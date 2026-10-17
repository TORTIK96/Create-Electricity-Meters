package tor_tik96.create_em.process;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.*;

import static tor_tik96.create_em.meter.EnergyMeterBlockEntity.line;

/**
 * Диспетчер обработки — только управление, предметы он не носит.
 * <ul>
 *   <li>Шлюзы-источники (воронки над лентой на сундуках) выкладывают партию на ленту под собой,
 *       когда диспетчер даёт им очередь и катализатор уже работает в их режиме. Каждый выпущенный
 *       предмет помечен «сырое» ({@link EMRegistry#RAW_BATCH}).</li>
 *   <li>Шлюз «выход» снимает с ленты под собой только предметы без метки — Create при обработке
 *       создаёт новый предмет, метка с ним не переходит. Сырое катается по кругу.</li>
 *   <li>Очередь переходит дальше, только когда в активном сундуке не осталось подходящего и на лентах
 *       вокруг шлюзов нет ни одного предмета (обход в обе стороны, в том числе через блоки между лентами).</li>
 * </ul>
 */
public class ProcessDispatcherBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final int MAX_BELTS = 256;

    /** Шлюзы, которые отметились (раз в секунду). */
    private final Set<BlockPos> gates = new LinkedHashSet<>();
    @Nullable private BlockPos active;
    @Nullable private BlockPos lastActive;
    @Nullable private BlockPos catalyst;
    private boolean busy;
    private boolean ready;

    // ---------- питание: диспетчер — считай компьютер ----------
    private int energy;
    private boolean powered = true; // синхронизируется

    private static int idleCost() { return tor_tik96.create_em.Config.safe(tor_tik96.create_em.Config.DISPATCHER_IDLE_FE, 2); }
    private static int workCost() { return tor_tik96.create_em.Config.safe(tor_tik96.create_em.Config.DISPATCHER_WORK_FE, 16); }

    /** Запас — на секунду работы, как у катализатора: впрок оплаченное не копится. */
    private static int capacity() { return workCost() * 20; }

    private final net.neoforged.neoforge.energy.IEnergyStorage input = new net.neoforged.neoforge.energy.IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) {
            int got = Math.max(0, Math.min(max, capacity() - energy));
            if (!simulate && got > 0) { energy += got; setChanged(); }
            return got;
        }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return capacity(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    public net.neoforged.neoforge.energy.IEnergyStorage getEnergy() {
        return input;
    }

    // синхронизируется для очков
    private int sourceGates, outputGates, pending, rawOnBelts;
    private boolean catalystFound, loopBusy;
    private int activeMode = -1;

    public ProcessDispatcherBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.PROCESS_DISPATCHER_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    void register(BlockPos gate) {
        if (gates.add(gate.immutable())) setChanged();
    }

    public void setCatalyst(@Nullable BlockPos pos) {
        catalyst = pos;
        setChanged();
    }

    /** Может ли этот шлюз сейчас выкладывать партию. */
    boolean mayRelease(ProcessGateBlockEntity g) {
        return ready && g.getBlockPos().equals(active);
    }

    @Nullable
    CatalystProcess activeProcess() {
        ProcessGateBlockEntity g = active == null ? null : gate(active);
        return g == null ? null : g.mode().process;
    }

    @Nullable
    private ProcessGateBlockEntity gate(BlockPos p) {
        if (level == null || !level.isLoaded(p)) return null;
        if (level.getBlockEntity(p) instanceof ProcessGateBlockEntity g && worldPosition.equals(g.getDispatcher())
                && p.closerThan(worldPosition, ProcessGateBlockEntity.range())) return g;
        return null;
    }

    private List<ProcessGateBlockEntity> liveGates() {
        List<ProcessGateBlockEntity> out = new ArrayList<>();
        for (Iterator<BlockPos> it = gates.iterator(); it.hasNext(); ) {
            BlockPos p = it.next();
            if (level != null && !level.isLoaded(p)) continue;
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

    static int processable(ProcessGateBlockEntity g, Level level) {
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

    /**
     * Есть ли предметы на лентах вокруг шлюзов: обходим ленты в обе стороны, а если между лентами
     * стоит другой блок (сортировщик, депо…), смотрим ленты вокруг него. Возвращает {всего, сырых}.
     */
    private int[] itemsOnBelts(List<ProcessGateBlockEntity> live) {
        int total = 0, raw = 0;
        if (level == null) return new int[]{0, 0};
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> visitedJoints = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (ProcessGateBlockEntity g : live) queue.add(g.getBlockPos().below());
        while (!queue.isEmpty() && seen.size() < MAX_BELTS) {
            BlockPos p = queue.poll();
            BeltBlockEntity belt = BeltHelper.getControllerBE(level, p);
            if (belt == null || !seen.add(belt.getBlockPos())) continue;
            for (TransportedItemStack t : belt.getInventory().getTransportedItems()) {
                total++;
                if (t.stack.has(EMRegistry.RAW_BATCH.get())) raw++;
            }
            for (int off : new int[]{belt.beltLength, -1}) {
                BlockPos n = BeltHelper.getPositionForOffset(belt, off);
                if (BeltHelper.getControllerBE(level, n) != null) { queue.add(n); continue; }
                // между лентами — другой блок: ленты вокруг него тоже часть петли
                if (level.isLoaded(n) && level.getBlockEntity(n) != null && visitedJoints.add(n))
                    for (Direction d : Direction.values()) queue.add(n.relative(d));
            }
        }
        return new int[]{total, raw};
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        List<ProcessGateBlockEntity> live = liveGates();
        if (level.getGameTime() % 5 == 0) {
            int[] items = itemsOnBelts(live);
            busy = items[0] > 0;
            rawOnBelts = items[1];
        }

        ProcessGateBlockEntity act = active == null ? null : gate(active);
        if (act != null && act.mode().process == null) act = null; // шлюз переключили в «выход»
        if ((act == null || processable(act, level) == 0) && !busy) act = pickNext(live);
        active = act == null ? null : act.getBlockPos();

        // без питания диспетчер «завис»: катализатор гаснет, шлюзы ничего не выкладывают
        int cost = act != null || busy ? workCost() : idleCost();
        boolean nowPowered = energy >= cost;
        if (nowPowered) { energy -= cost; setChanged(); }
        if (nowPowered != powered) { powered = nowPowered; sendData(); }
        CatalystProcess mode = act == null || !powered ? CatalystProcess.NONE : act.mode().process;

        ElectricCatalystBlockEntity cat = catalystBE();
        if (cat != null) cat.dispatch(mode);
        ready = powered && act != null && cat != null && cat.getBlockState().getValue(ElectricCatalystBlock.PROCESS) == mode;

        if (level.getGameTime() % 10 == 0) {
            int src = 0, outs = 0;
            for (ProcessGateBlockEntity g : live) if (g.mode() == GateMode.OUTPUT) outs++; else src++;
            int pend = act == null ? 0 : processable(act, level);
            int am = act == null ? -1 : act.mode().ordinal();
            boolean changed = src != sourceGates || outs != outputGates || pend != pending || busy != loopBusy
                    || (cat != null) != catalystFound || am != activeMode;
            sourceGates = src;
            outputGates = outs;
            pending = pend;
            loopBusy = busy;
            catalystFound = cat != null;
            activeMode = am;
            if (changed) sendData();
        }
    }

    @Nullable
    private ProcessGateBlockEntity pickNext(List<ProcessGateBlockEntity> live) {
        List<ProcessGateBlockEntity> src = new ArrayList<>();
        for (ProcessGateBlockEntity g : live) if (g.mode().process != null && g.hasBelt()) src.add(g);
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

    // ---------- очки ----------
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(1, Component.translatable("create_em.dispatcher.gates", sourceGates, outputGates).withStyle(ChatFormatting.GRAY)));
        if (activeMode >= 0)
            tooltip.add(line(1, Component.translatable("create_em.dispatcher.active")
                    .append(Component.translatable(GateMode.values()[activeMode].translationKey()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" · " + pending)).withStyle(ChatFormatting.GRAY)));
        if (rawOnBelts > 0)
            tooltip.add(line(1, Component.translatable("create_em.dispatcher.raw", rawOnBelts).withStyle(ChatFormatting.GRAY)));
        tooltip.add(line(1, Component.translatable("create_em.dispatcher.power")
                .append(Component.literal(tor_tik96.create_em.EMFormat.fe(activeMode >= 0 || loopBusy ? workCost() : idleCost()) + "/t"))
                .withStyle(ChatFormatting.GRAY)));
        String status = !powered ? "create_em.dispatcher.no_power"
                : !catalystFound ? "create_em.dispatcher.no_catalyst"
                : outputGates == 0 ? "create_em.dispatcher.no_output"
                : activeMode >= 0 ? (loopBusy ? "create_em.dispatcher.working" : "create_em.dispatcher.starting")
                : loopBusy ? "create_em.dispatcher.draining" : "create_em.dispatcher.idle";
        boolean good = powered && catalystFound && outputGates > 0;
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
        tag.putInt("Energy", energy);
        if (clientPacket) {
            tag.putBoolean("Powered", powered);
            tag.putInt("Src", sourceGates);
            tag.putInt("Outs", outputGates);
            tag.putInt("Pending", pending);
            tag.putInt("Raw", rawOnBelts);
            tag.putBoolean("Busy", loopBusy);
            tag.putBoolean("CatOk", catalystFound);
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
        energy = Math.min(tag.getInt("Energy"), capacity());
        if (clientPacket) {
            powered = tag.getBoolean("Powered");
            sourceGates = tag.getInt("Src");
            outputGates = tag.getInt("Outs");
            pending = tag.getInt("Pending");
            rawOnBelts = tag.getInt("Raw");
            loopBusy = tag.getBoolean("Busy");
            catalystFound = tag.getBoolean("CatOk");
            activeMode = tag.contains("ActiveMode") ? tag.getInt("ActiveMode") : -1;
        }
    }
}
