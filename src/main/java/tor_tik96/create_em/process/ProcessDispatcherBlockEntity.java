package tor_tik96.create_em.process;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static tor_tik96.create_em.meter.EnergyMeterBlockEntity.line;

/**
 * Диспетчер обработки — начало и конец петли из лент.
 * <ul>
 *   <li>Слева, справа и сверху — бочки (любые инвентари), у каждой своя обработка (ПКМ по грани).</li>
 *   <li>Спереди уходит лента через поток вентилятора, сзади она возвращается обратно в диспетчер.</li>
 *   <li>Вернувшееся готовое (пометка Create на ленте) — в инвентарь снизу, сырое — снова на ленту.</li>
 * </ul>
 * Берёт из активной бочки по 16 предметов, которые её обработка действительно умеет обрабатывать,
 * и держит привязанный катализатор в нужном режиме. Следующая бочка — только когда в активной
 * не осталось подходящего и <b>на лентах петли не осталось ни одного предмета</b>; без таймеров.
 */
public class ProcessDispatcherBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int SLOTS = 3; // 0 — слева, 1 — справа, 2 — сверху
    private static final int BATCH = 16;
    private static final int MAX_BELTS = 64;

    private final CatalystProcess[] modes = {CatalystProcess.NONE, CatalystProcess.NONE, CatalystProcess.NONE};
    /** Какая бочка сейчас в работе; -1 — простаивает. */
    private int active = -1;
    @Nullable private BlockPos catalyst;

    // синхронизируется для очков
    private final int[] pending = new int[SLOTS];
    private boolean loopBusy;
    private boolean catalystFound;

    public ProcessDispatcherBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.PROCESS_DISPATCHER_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this)
                .onlyInsertWhen(movement -> movement == facing()) // лента возвращается сзади
                .setInsertionHandler(this::returned));
    }

    private Direction facing() {
        return getBlockState().getValue(ProcessDispatcherBlock.FACING);
    }

    /** Сторона бочки: 0 — слева, 1 — справа (если смотреть по ходу ленты), 2 — сверху. */
    public Direction slotSide(int slot) {
        return switch (slot) {
            case 0 -> facing().getCounterClockWise();
            case 1 -> facing().getClockWise();
            default -> Direction.UP;
        };
    }

    public int slotOf(Direction side) {
        for (int i = 0; i < SLOTS; i++) if (slotSide(i) == side) return i;
        return -1;
    }

    /** ПКМ по грани бочки: следующий режим. */
    public CatalystProcess cycle(int slot) {
        CatalystProcess[] order = {CatalystProcess.NONE, CatalystProcess.BLASTING, CatalystProcess.SMOKING,
                CatalystProcess.SPLASHING, CatalystProcess.HAUNTING, CatalystProcess.UNIVERSAL};
        int i = 0;
        while (order[i] != modes[slot]) i++;
        modes[slot] = order[(i + 1) % order.length];
        if (active == slot) active = -1; // сменили режим активной бочки — выберем заново, когда петля опустеет
        setChanged();
        sendData();
        return modes[slot];
    }

    public void setCatalyst(@Nullable BlockPos pos) {
        catalyst = pos;
        setChanged();
    }

    @Nullable
    private ElectricCatalystBlockEntity catalystBE() {
        if (catalyst == null || level == null || !level.isLoaded(catalyst)) return null;
        return level.getBlockEntity(catalyst) instanceof ElectricCatalystBlockEntity c ? c : null;
    }

    @Nullable
    private IItemHandler bin(int slot) {
        if (level == null) return null;
        Direction d = slotSide(slot);
        return level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(d), d.getOpposite());
    }

    /** Сколько в бочке предметов, которые её режим умеет обработать. */
    private int processable(int slot) {
        IItemHandler h = bin(slot);
        CatalystProcess m = modes[slot];
        if (h == null || m == CatalystProcess.NONE) return 0;
        int n = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty() && m.canProcess(s, level)) n += s.getCount();
        }
        return n;
    }

    /** Есть ли что-нибудь на лентах петли: от ленты спереди по цепочке до возврата в диспетчер. */
    private boolean loopHasItems() {
        if (level == null) return false;
        BlockPos pos = worldPosition.relative(facing());
        Set<BlockPos> seen = new HashSet<>();
        for (int i = 0; i < MAX_BELTS; i++) {
            BeltBlockEntity belt = BeltHelper.getControllerBE(level, pos);
            if (belt == null || !seen.add(belt.getBlockPos())) return false;
            if (!belt.getInventory().getTransportedItems().isEmpty()) return true;
            float speed = belt.getDirectionAwareBeltMovementSpeed();
            if (speed == 0) return false;
            pos = BeltHelper.getPositionForOffset(belt, speed > 0 ? belt.beltLength : -1);
            if (pos.equals(worldPosition)) return false;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        boolean busy = loopHasItems();
        if (active >= 0 && (modes[active] == CatalystProcess.NONE || processable(active) == 0) && !busy)
            active = -1; // бочка опустела и всё доехало — свободны
        if (active < 0 && !busy) active = pickNext();

        ElectricCatalystBlockEntity cat = catalystBE();
        if (cat != null) cat.dispatch(active >= 0 ? modes[active] : CatalystProcess.NONE);

        // одна партия за тик, если лента спереди готова принять (катализатор успел включиться)
        if (active >= 0 && cat != null && cat.getBlockState().getValue(ElectricCatalystBlock.PROCESS) == modes[active])
            feed(active);

        if (level.getGameTime() % 10 == 0) {
            boolean changed = busy != loopBusy || (cat != null) != catalystFound;
            for (int i = 0; i < SLOTS; i++) {
                int n = processable(i);
                if (n != pending[i]) changed = true;
                pending[i] = n;
            }
            loopBusy = busy;
            catalystFound = cat != null;
            if (changed) sendData();
        }
    }

    /** Следующая по кругу бочка, в которой есть что обработать. */
    private int pickNext() {
        for (int k = 1; k <= SLOTS; k++) {
            int s = (lastActive + k) % SLOTS;
            if (modes[s] != CatalystProcess.NONE && processable(s) > 0) {
                lastActive = s;
                return s;
            }
        }
        return -1;
    }

    private int lastActive = SLOTS - 1;

    private void feed(int slot) {
        IItemHandler h = bin(slot);
        if (h == null) return;
        CatalystProcess m = modes[slot];
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty() || !m.canProcess(s, level)) continue;
            ItemStack batch = h.extractItem(i, BATCH, true);
            if (batch.isEmpty()) continue;
            ItemStack rest = toBelt(new TransportedItemStack(batch), true);
            int fits = batch.getCount() - rest.getCount();
            if (fits <= 0) return; // лента занята — подождём
            toBelt(new TransportedItemStack(h.extractItem(i, fits, false)), false);
            return;
        }
    }

    /** На ленту спереди (с пометкой обработки, если она есть). */
    private ItemStack toBelt(TransportedItemStack t, boolean simulate) {
        if (level == null) return t.stack;
        Direction out = facing();
        DirectBeltInputBehaviour belt = BlockEntityBehaviour.get(level, worldPosition.relative(out), DirectBeltInputBehaviour.TYPE);
        if (belt == null || !belt.canInsertFromSide(out)) return t.stack;
        return belt.handleInsertion(t.copy(), out, simulate);
    }

    /** Лента вернулась сзади: готовое — вниз, сырое — снова в петлю. */
    private ItemStack returned(TransportedItemStack t, Direction movement, boolean simulate) {
        if (level == null) return t.stack;
        if (!ProcessSorterBlockEntity.isDone(t)) return toBelt(t, simulate);
        IItemHandler out = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.below(), Direction.UP);
        if (out == null) return t.stack;
        return ItemHandlerHelper.insertItemStacked(out, t.stack.copy(), simulate);
    }

    // ---------- очки ----------
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        String[] names = {"create_em.dispatcher.left", "create_em.dispatcher.right", "create_em.dispatcher.top"};
        for (int i = 0; i < SLOTS; i++) {
            var c = Component.translatable(names[i]).withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable(modes[i] == CatalystProcess.NONE ? "create_em.dispatcher.unused"
                            : modes[i].translationKey()).withStyle(i == active ? ChatFormatting.GOLD : ChatFormatting.AQUA));
            if (modes[i] != CatalystProcess.NONE)
                c.append(Component.literal(" · " + pending[i]).withStyle(ChatFormatting.GRAY));
            tooltip.add(line(1, c));
        }
        String status = !catalystFound ? "create_em.dispatcher.no_catalyst"
                : active >= 0 ? (loopBusy ? "create_em.dispatcher.working" : "create_em.dispatcher.starting")
                : loopBusy ? "create_em.dispatcher.draining" : "create_em.dispatcher.idle";
        tooltip.add(line(1, Component.translatable(status).withStyle(catalystFound ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
        return true;
    }

    // ---------- NBT ----------
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.write(tag, reg, clientPacket);
        for (int i = 0; i < SLOTS; i++) tag.putInt("Mode" + i, modes[i].ordinal());
        tag.putInt("Active", active);
        if (catalyst != null) tag.putLong("Catalyst", catalyst.asLong());
        if (clientPacket) {
            tag.putIntArray("Pending", pending);
            tag.putBoolean("Busy", loopBusy);
            tag.putBoolean("CatOk", catalystFound);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.read(tag, reg, clientPacket);
        CatalystProcess[] all = CatalystProcess.values();
        for (int i = 0; i < SLOTS; i++) modes[i] = all[Math.min(all.length - 1, Math.max(0, tag.getInt("Mode" + i)))];
        active = tag.contains("Active") ? tag.getInt("Active") : -1;
        catalyst = tag.contains("Catalyst") ? BlockPos.of(tag.getLong("Catalyst")) : null;
        if (clientPacket) {
            int[] p = tag.getIntArray("Pending");
            for (int i = 0; i < SLOTS && i < p.length; i++) pending[i] = p[i];
            loopBusy = tag.getBoolean("Busy");
            catalystFound = tag.getBoolean("CatOk");
        }
    }
}
