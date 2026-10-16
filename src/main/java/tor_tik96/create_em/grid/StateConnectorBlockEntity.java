package tor_tik96.create_em.grid;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.line.LineLinks;
import tor_tik96.create_em.line.LineNode;
import tor_tik96.create_em.power.EnergyPort;
import tor_tik96.create_em.power.Relay;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.*;

/**
 * Госконнектор. Спереди (синяя пластина) — устройство. Дальше два варианта:
 * <ul>
 *   <li><b>без госпровода</b> — сзади другой госблок хозяина, энергия идёт между ними в одну сторону;</li>
 *   <li><b>с госпроводом</b> — задняя клемма отключается, коннекторы, соединённые госпроводом, образуют
 *       госсеть: «из устройства» забирают энергию у своих устройств и делят её поровну между
 *       коннекторами «в устройство» этой сети.</li>
 * </ul>
 * Работает только с госблоками своего хозяина. Госпровод подключает только хозяин.
 */
public class StateConnectorBlockEntity extends SmartBlockEntity implements LineNode,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    private static final int MAX = Integer.MAX_VALUE / 2;
    private static final int MAX_NET = 256;

    private final Relay relay = new Relay();
    private final LineLinks links = new LineLinks(this);
    @Nullable private UUID owner;
    private String ownerName = "";

    /** Сзади, режим «в устройство»: принимаем и передаём вперёд. */
    private final EnergyPort backIn = EnergyPort.input(
            (a, sim) -> !isOut() && allowed() ? relay.accept(a, sim, front(), MAX) : 0, relay::buffer, () -> MAX);
    /** Сзади, режим «из устройства»: отдаём. */
    private final EnergyPort backOut = EnergyPort.output(
            (a, sim) -> isOut() && allowed() ? relay.take(a, sim) : 0, relay::buffer, () -> MAX);
    /** Спереди, режим «из устройства»: устройство само толкает в нас. */
    private final EnergyPort frontIn = EnergyPort.input(
            (a, sim) -> isOut() && allowed() ? relay.accept(a, sim, back(), MAX) : 0, relay::buffer, () -> MAX);

    public StateConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.STATE_CONNECTOR_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public String getOwnerName() { return ownerName; }

    void setOwner(UUID id, String name) {
        owner = id;
        ownerName = name;
        setChanged();
    }

    private Direction facing() {
        return getBlockState().getValue(StateConnectorBlock.FACING);
    }

    boolean isOut() {
        return getBlockState().getValue(StateConnectorBlock.OUT);
    }

    /** Есть госпровод — задняя клемма не используется. */
    private boolean wired() {
        return !links.isEmpty();
    }

    @Nullable
    public IEnergyStorage port(@Nullable Direction side) {
        if (side == null) return null;
        if (side == facing()) return isOut() ? frontIn : null;
        if (side == facing().getOpposite() && !wired()) return isOut() ? backOut : backIn;
        return null;
    }

    /** Работаем, только если с обеих сторон — госблоки своего хозяина (с госпроводом — только спереди). */
    boolean allowed() {
        if (!(level instanceof ServerLevel sl) || owner == null) return false;
        GridMarks marks = GridMarks.get(sl);
        if (!ok(marks, sl, worldPosition.relative(facing()))) return false;
        return wired() || ok(marks, sl, worldPosition.relative(facing().getOpposite()));
    }

    private boolean ok(GridMarks marks, ServerLevel sl, BlockPos pos) {
        if (!sl.isLoaded(pos)) return false;
        if (sl.getBlockEntity(pos) instanceof StateConnectorBlockEntity other) return owner.equals(other.owner);
        GridMarks.Mark m = marks.at(sl, pos);
        return m != null && m.owner().equals(owner);
    }

    @Nullable IEnergyStorage front() { return neighbor(facing()); }

    /** Куда отдавать в режиме «из устройства»: по госпроводу в сеть или в госблок сзади. */
    @Nullable
    private IEnergyStorage back() {
        return wired() ? network() : neighbor(facing().getOpposite());
    }

    @Nullable
    private IEnergyStorage neighbor(Direction d) {
        if (!(level instanceof ServerLevel sl)) return null;
        BlockPos p = worldPosition.relative(d);
        if (!sl.isLoaded(p)) return null;
        return GridIsolation.bypass(() -> sl.getCapability(Capabilities.EnergyStorage.BLOCK, p, d.getOpposite()));
    }

    // ---------- госсеть по проводам ----------

    /** Все устройства «в устройство», до которых дотягивается госпровод (кроме нас самих). */
    @Nullable
    private IEnergyStorage network() {
        if (!(level instanceof ServerLevel sl)) return null;
        List<IEnergyStorage> sinks = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>(Set.of(worldPosition));
        Deque<StateConnectorBlockEntity> queue = new ArrayDeque<>(List.of(this));
        while (!queue.isEmpty() && seen.size() < MAX_NET) {
            StateConnectorBlockEntity c = queue.poll();
            if (c != this && !c.isOut() && c.allowed()) {
                IEnergyStorage t = c.front();
                if (t != null && t.canReceive()) sinks.add(new Sink(c, t));
            }
            for (BlockPos p : c.links.get()) {
                if (!seen.add(p) || !sl.isLoaded(p)) continue;
                if (sl.getBlockEntity(p) instanceof StateConnectorBlockEntity n && Objects.equals(owner, n.owner)) queue.add(n);
            }
        }
        return sinks.isEmpty() ? null : new Split(sinks);
    }

    /** Приёмник в сети: его устройство и сам коннектор (для счёта в очках). */
    private record Sink(StateConnectorBlockEntity conn, IEnergyStorage device) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            int got = device.receiveEnergy(amount, simulate);
            if (!simulate) conn.receivedFe += got;
            return got;
        }
        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return device.getEnergyStored(); }
        @Override public int getMaxEnergyStored() { return device.getMaxEnergyStored(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return device.canReceive(); }
    }

    /** Несколько потребителей как один: поровну, недобор одних — остальным. */
    private record Split(List<IEnergyStorage> targets) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            int left = amount;
            boolean[] hungry = new boolean[targets.size()];
            for (int i = 0; i < targets.size() && left > 0; i++) {
                int share = Math.min(left, Math.max(1, left / (targets.size() - i)));
                int got = targets.get(i).receiveEnergy(share, simulate);
                hungry[i] = got >= share;
                left -= got;
            }
            for (int i = 0; i < targets.size() && left > 0; i++)
                if (hungry[i]) left -= targets.get(i).receiveEnergy(left, simulate);
            return amount - left;
        }

        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }

        @Override
        public int getEnergyStored() {
            long s = 0;
            for (IEnergyStorage t : targets) s += t.getEnergyStored();
            return (int) Math.min(Integer.MAX_VALUE, s);
        }

        @Override
        public int getMaxEnergyStored() {
            long s = 0;
            for (IEnergyStorage t : targets) s += t.getMaxEnergyStored();
            return (int) Math.min(Integer.MAX_VALUE, s);
        }

        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }

    void onModeChanged() {
        relay.clear();
        if (level != null) GridIsolation.invalidate(level, worldPosition);
    }

    // ---------- диагностика для очков ----------
    /** Что мешает работать (синхронизируется). */
    public enum Status { OK, NO_OWNER, FRONT_NONE, FRONT_UNMARKED, FRONT_FOREIGN, BACK_BAD, NO_RECEIVERS, NO_SUPPLY }

    private Status status = Status.OK;
    private int receivers;     // сколько приёмников в сети (для «из устройства» с проводом)
    private long windowFe;
    private int windowTicks;
    private float flow;        // FE/t через этот коннектор
    /** Сколько отдали в устройство «в устройство» по проводу за окно (засчитывают источники). */
    private long receivedFe;

    private Status diagnose(ServerLevel sl) {
        if (owner == null) return Status.NO_OWNER;
        GridMarks marks = GridMarks.get(sl);
        BlockPos fp = worldPosition.relative(facing());
        if (front() == null) return Status.FRONT_NONE;
        GridMarks.Mark m = marks.at(sl, fp);
        if (!(sl.getBlockEntity(fp) instanceof StateConnectorBlockEntity)) {
            if (m == null) return Status.FRONT_UNMARKED;
            if (!m.owner().equals(owner)) return Status.FRONT_FOREIGN;
        }
        if (!wired() && !ok(marks, sl, worldPosition.relative(facing().getOpposite()))) return Status.BACK_BAD;
        if (wired() && isOut() && receivers == 0) return Status.NO_RECEIVERS;
        return Status.OK;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel sl)) return;
        int moved = 0;
        if (allowed()) {
            if (!(wired() && !isOut())) { // «в устройство» с проводом кормят коннекторы «из устройства»
                IEnergyStorage src = isOut() ? front() : back(), dst = isOut() ? back() : front();
                relay.pull(src, dst, MAX);
                moved = relay.push(dst, MAX);
            }
        }
        windowFe += moved;
        if (++windowTicks >= 20) {
            if (wired() && isOut()) {
                IEnergyStorage net = network();
                receivers = net instanceof Split s ? s.targets().size() : 0;
            }
            long fe = wired() && !isOut() ? receivedFe : windowFe;
            Status st = diagnose(sl);
            if (st == Status.OK && wired() && !isOut() && fe == 0) st = Status.NO_SUPPLY;
            float f = fe / 20f;
            boolean changed = st != status || f != flow;
            status = st;
            flow = f;
            windowFe = 0;
            receivedFe = 0;
            windowTicks = 0;
            if (changed) sendData();
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<net.minecraft.network.chat.Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(tor_tik96.create_em.meter.EnergyMeterBlockEntity.line(0, net.minecraft.network.chat.Component
                .translatable(getBlockState().getBlock().getDescriptionId()).withStyle(net.minecraft.ChatFormatting.WHITE)));
        tooltip.add(tor_tik96.create_em.meter.EnergyMeterBlockEntity.line(1, net.minecraft.network.chat.Component
                .translatable(isOut() ? "create_em.connector.mode_out" : "create_em.connector.mode_in").withStyle(net.minecraft.ChatFormatting.GRAY)));
        tooltip.add(tor_tik96.create_em.meter.EnergyMeterBlockEntity.line(1, net.minecraft.network.chat.Component
                .translatable("create_em.connector.wires", links.size(), ownerName.isEmpty() ? "?" : ownerName).withStyle(net.minecraft.ChatFormatting.GRAY)));
        if (wired() && isOut())
            tooltip.add(tor_tik96.create_em.meter.EnergyMeterBlockEntity.line(1, net.minecraft.network.chat.Component
                    .translatable("create_em.connector.receivers", receivers).withStyle(net.minecraft.ChatFormatting.GRAY)));
        tooltip.add(tor_tik96.create_em.meter.EnergyMeterBlockEntity.line(1, net.minecraft.network.chat.Component
                .literal(String.format("%,.0f FE/t", flow)).withStyle(net.minecraft.ChatFormatting.AQUA)));
        tooltip.add(tor_tik96.create_em.meter.EnergyMeterBlockEntity.line(1, net.minecraft.network.chat.Component
                .translatable("create_em.connector.status." + status.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(status == Status.OK ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GOLD)));
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        boolean turned = state.getValue(StateConnectorBlock.FACING) != getBlockState().getValue(StateConnectorBlock.FACING);
        super.setBlockState(state);
        if (turned && level != null) {
            relay.clear();
            GridIsolation.invalidate(level, worldPosition);
        }
    }

    // ---------- LineNode: госпровод ----------
    @Override public LineLinks getLinks() { return links; }
    @Override public int getMaxLinks() { return 4; }
    @Override public boolean isStateGrid() { return true; }
    @Override public @Nullable UUID getOwner() { return owner; }
    @Override public void setOwner(@Nullable UUID owner) { this.owner = owner; setChanged(); }

    /** Провод крепится к клемме на конце изолятора. */
    @Override
    public Vec3 getWireAnchor() {
        Direction back = facing().getOpposite();
        double k = 1.5 / 16; // клемма чуть дальше центра блока от устройства
        return new Vec3(0.5 + back.getStepX() * k, 0.5 + back.getStepY() * k, 0.5 + back.getStepZ() * k);
    }

    // ---------- NBT ----------
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.write(tag, reg, clientPacket);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        links.save(tag);
        if (clientPacket) {
            tag.putInt("Status", status.ordinal());
            tag.putFloat("Flow", flow);
            tag.putInt("Receivers", receivers);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.read(tag, reg, clientPacket);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        links.load(tag);
        if (clientPacket) {
            status = Status.values()[Math.min(Status.values().length - 1, Math.max(0, tag.getInt("Status")))];
            flow = tag.getFloat("Flow");
            receivers = tag.getInt("Receivers");
        }
    }
}
