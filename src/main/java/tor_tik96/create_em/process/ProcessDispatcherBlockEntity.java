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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.*;

import static tor_tik96.create_em.meter.EnergyMeterBlockEntity.line;

/**
 * Диспетчер обработки. Работает со <b>шлюзами</b>: шлюзы на сундуках/бочках привязаны к диспетчеру,
 * у каждого своя роль — обработка (откуда брать) или выход (куда класть готовое).
 * <ul>
 *   <li>Выход на ленту — грань, от которой уходит лента (определяется сама).</li>
 *   <li>С любой другой грани принимает ленту: готовое (пометка Create) — в выходной шлюз,
 *       сырое — снова на ленту.</li>
 *   <li>Следующий шлюз — когда в активном не осталось того, что его режим умеет обработать,
 *       и на лентах от выхода нет ни одного предмета. Без таймеров.</li>
 * </ul>
 */
public class ProcessDispatcherBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final int BATCH = 16;
    private static final int MAX_BELTS = 64;

    /** Только клиент: шторки на гранях с лентами, как у туннеля Create (индекс — Direction.ordinal). */
    public final Flaps flaps = new Flaps(6);

    private void flap(Direction side, boolean inward) {
        if (level != null && !level.isClientSide)
            level.blockEvent(worldPosition, getBlockState().getBlock(), side.ordinal(), inward ? 1 : 0);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id >= 0 && id < 6) {
            if (level != null && level.isClientSide) flaps.bump(id, param == 1);
            return true;
        }
        return super.triggerEvent(id, param);
    }

    /** Шлюзы, которые отметились (раз в секунду). Проверяются при каждом обращении. */
    private final Set<BlockPos> gates = new LinkedHashSet<>();
    @Nullable private BlockPos active;
    @Nullable private BlockPos lastActive;
    @Nullable private BlockPos catalyst;

    // синхронизируется для очков
    private int sourceGates, outputGates, pending;
    private boolean loopBusy, catalystFound, hasBelt;
    private int activeMode = -1;

    public ProcessDispatcherBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.PROCESS_DISPATCHER_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this)
                .onlyInsertWhen(movement -> movement.getOpposite() != outputSide()) // с выхода не принимаем
                .setInsertionHandler(this::returned));
    }

    void register(BlockPos gate) {
        if (gates.add(gate.immutable())) setChanged();
    }

    public void setCatalyst(@Nullable BlockPos pos) {
        catalyst = pos;
        setChanged();
    }

    @Nullable
    private ProcessGateBlockEntity gate(BlockPos p) {
        if (level == null || !level.isLoaded(p)) return null;
        if (level.getBlockEntity(p) instanceof ProcessGateBlockEntity g && worldPosition.equals(g.getDispatcher())
                && p.closerThan(worldPosition, ProcessGateBlockEntity.range())) return g;
        return null;
    }

    /** Живые шлюзы; пропавшие и перепривязанные выбрасываем. */
    private List<ProcessGateBlockEntity> liveGates() {
        List<ProcessGateBlockEntity> out = new ArrayList<>();
        for (Iterator<BlockPos> it = gates.iterator(); it.hasNext(); ) {
            BlockPos p = it.next();
            if (level != null && !level.isLoaded(p)) continue; // чанк выгружен — пусть останется в списке
            ProcessGateBlockEntity g = gate(p);
            if (g == null) { it.remove(); setChanged(); continue; }
            out.add(g);
        }
        return out;
    }

    @Nullable
    private ElectricCatalystBlockEntity catalystBE() {
        if (catalyst == null || level == null || !level.isLoaded(catalyst)) return null;
        return level.getBlockEntity(catalyst) instanceof ElectricCatalystBlockEntity c ? c : null;
    }

    /** Грань, от которой уходит лента: сосед — лента, которая едет от диспетчера. */
    @Nullable
    private Direction outputSide() {
        if (level == null) return null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof BeltBlockEntity b
                    && b.getDirectionAwareBeltMovementSpeed() != 0 && b.getMovementFacing() == d) return d;
        }
        return null;
    }

    private static int processable(ProcessGateBlockEntity g, net.minecraft.world.level.Level level) {
        CatalystProcess m = g.mode().process;
        IItemHandler h = g.inventory();
        if (m == null || h == null) return 0;
        int n = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty() && m.canProcess(s, level)) n += s.getCount();
        }
        return n;
    }

    /** Есть ли что-нибудь на лентах: от выхода по цепочке, пока ленты не кончатся или не вернутся в диспетчер. */
    private boolean loopHasItems(@Nullable Direction out) {
        if (level == null || out == null) return false;
        BlockPos pos = worldPosition.relative(out);
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
        if (level != null && level.isClientSide) {
            flaps.tick();
            return;
        }
        if (level == null) return;

        Direction out = outputSide();
        boolean busy = loopHasItems(out);
        ProcessGateBlockEntity act = active == null ? null : gate(active);
        if (act != null && act.mode().process == null) act = null; // шлюз переключили в «выход»
        if (act == null || (processable(act, level) == 0 && !busy)) {
            if (!busy) act = pickNext();
        }
        active = act == null ? null : act.getBlockPos();
        CatalystProcess mode = act == null ? CatalystProcess.NONE : act.mode().process;

        ElectricCatalystBlockEntity cat = catalystBE();
        if (cat != null) cat.dispatch(mode);

        // партия — только когда катализатор уже работает в нужном режиме
        if (act != null && out != null && cat != null && cat.getBlockState().getValue(ElectricCatalystBlock.PROCESS) == mode)
            feed(act, out);

        if (level.getGameTime() % 10 == 0) {
            List<ProcessGateBlockEntity> live = liveGates();
            int src = 0, outs = 0;
            for (ProcessGateBlockEntity g : live) if (g.mode() == GateMode.OUTPUT) outs++; else src++;
            int pend = act == null ? 0 : processable(act, level);
            int am = act == null ? -1 : act.mode().ordinal();
            boolean changed = src != sourceGates || outs != outputGates || pend != pending || busy != loopBusy
                    || (cat != null) != catalystFound || (out != null) != hasBelt || am != activeMode;
            sourceGates = src;
            outputGates = outs;
            pending = pend;
            loopBusy = busy;
            catalystFound = cat != null;
            hasBelt = out != null;
            activeMode = am;
            if (changed) sendData();
        }
    }

    /** Следующий по кругу шлюз-источник, в котором есть что обработать. */
    @Nullable
    private ProcessGateBlockEntity pickNext() {
        List<ProcessGateBlockEntity> src = new ArrayList<>();
        for (ProcessGateBlockEntity g : liveGates()) if (g.mode().process != null) src.add(g);
        if (src.isEmpty()) return null;
        src.sort(Comparator.comparingLong(g -> g.getBlockPos().asLong()));
        int start = 0;
        if (lastActive != null)
            for (int i = 0; i < src.size(); i++) if (src.get(i).getBlockPos().equals(lastActive)) start = i + 1;
        for (int k = 0; k < src.size(); k++) {
            ProcessGateBlockEntity g = src.get((start + k) % src.size());
            if (processable(g, level) > 0) {
                lastActive = g.getBlockPos();
                return g;
            }
        }
        return null;
    }

    private void feed(ProcessGateBlockEntity g, Direction out) {
        IItemHandler h = g.inventory();
        CatalystProcess m = g.mode().process;
        if (h == null || m == null) return;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty() || !m.canProcess(s, level)) continue;
            ItemStack batch = h.extractItem(i, BATCH, true);
            if (batch.isEmpty()) continue;
            ItemStack rest = toBelt(new TransportedItemStack(batch), out, true);
            int fits = batch.getCount() - rest.getCount();
            if (fits <= 0) return; // лента занята
            toBelt(new TransportedItemStack(h.extractItem(i, fits, false)), out, false);
            g.flap(false);   // выезжает из сундука
            flap(out, false); // и из диспетчера на ленту
            return;
        }
    }

    private ItemStack toBelt(TransportedItemStack t, @Nullable Direction out, boolean simulate) {
        if (level == null || out == null) return t.stack;
        DirectBeltInputBehaviour belt = BlockEntityBehaviour.get(level, worldPosition.relative(out), DirectBeltInputBehaviour.TYPE);
        if (belt == null || !belt.canInsertFromSide(out)) return t.stack;
        return belt.handleInsertion(t.copy(), out, simulate);
    }

    /** Лента пришла в диспетчер: готовое — в выходные шлюзы, сырое — снова на ленту. */
    private ItemStack returned(TransportedItemStack t, Direction movement, boolean simulate) {
        if (level == null) return t.stack;
        Direction out = outputSide();
        ItemStack rest;
        if (!ProcessSorterBlockEntity.isDone(t)) {
            rest = toBelt(t, out, simulate);
            if (!simulate && rest.getCount() < t.stack.getCount() && out != null) flap(out, false);
        } else {
            rest = t.stack.copy();
            for (ProcessGateBlockEntity g : liveGates()) {
                if (g.mode() != GateMode.OUTPUT) continue;
                IItemHandler inv = g.inventory();
                if (inv == null) continue;
                int before = rest.getCount();
                rest = ItemHandlerHelper.insertItemStacked(inv, rest, simulate);
                if (!simulate && rest.getCount() < before) g.flap(true); // заезжает в сундук
                if (rest.isEmpty()) break;
            }
        }
        if (!simulate && rest.getCount() < t.stack.getCount()) flap(movement.getOpposite(), true); // въехало в диспетчер
        return rest;
    }

    // ---------- очки ----------
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(1, Component.translatable("create_em.dispatcher.gates", sourceGates, outputGates).withStyle(ChatFormatting.GRAY)));
        if (activeMode >= 0)
            tooltip.add(line(1, Component.translatable("create_em.dispatcher.active")
                    .append(Component.translatable(GateMode.values()[activeMode].translationKey()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" · " + pending)).withStyle(ChatFormatting.GRAY)));
        String status = !catalystFound ? "create_em.dispatcher.no_catalyst"
                : !hasBelt ? "create_em.dispatcher.no_belt"
                : outputGates == 0 ? "create_em.dispatcher.no_output"
                : activeMode >= 0 ? (loopBusy ? "create_em.dispatcher.working" : "create_em.dispatcher.starting")
                : loopBusy ? "create_em.dispatcher.draining" : "create_em.dispatcher.idle";
        boolean good = catalystFound && hasBelt && outputGates > 0;
        tooltip.add(line(1, Component.translatable(status).withStyle(good ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
        return true;
    }

    // ---------- NBT ----------
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.write(tag, reg, clientPacket);
        if (catalyst != null) tag.putLong("Catalyst", catalyst.asLong());
        if (active != null) tag.putLong("Active", active.asLong());
        if (lastActive != null) tag.putLong("LastActive", lastActive.asLong());
        long[] g = new long[gates.size()];
        int i = 0;
        for (BlockPos p : gates) g[i++] = p.asLong();
        tag.putLongArray("Gates", g);
        if (clientPacket) {
            tag.putInt("Src", sourceGates);
            tag.putInt("Outs", outputGates);
            tag.putInt("Pending", pending);
            tag.putBoolean("Busy", loopBusy);
            tag.putBoolean("CatOk", catalystFound);
            tag.putBoolean("Belt", hasBelt);
            tag.putInt("ActiveMode", activeMode);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.read(tag, reg, clientPacket);
        catalyst = tag.contains("Catalyst") ? BlockPos.of(tag.getLong("Catalyst")) : null;
        active = tag.contains("Active") ? BlockPos.of(tag.getLong("Active")) : null;
        lastActive = tag.contains("LastActive") ? BlockPos.of(tag.getLong("LastActive")) : null;
        gates.clear();
        for (long l : tag.getLongArray("Gates")) gates.add(BlockPos.of(l));
        if (clientPacket) {
            sourceGates = tag.getInt("Src");
            outputGates = tag.getInt("Outs");
            pending = tag.getInt("Pending");
            loopBusy = tag.getBoolean("Busy");
            catalystFound = tag.getBoolean("CatOk");
            hasBelt = tag.getBoolean("Belt");
            activeMode = tag.contains("ActiveMode") ? tag.getInt("ActiveMode") : -1;
        }
    }
}
