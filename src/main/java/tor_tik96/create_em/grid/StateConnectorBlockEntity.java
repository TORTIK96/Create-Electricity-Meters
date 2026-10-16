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
public class StateConnectorBlockEntity extends SmartBlockEntity implements LineNode {
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
                if (t != null && t.canReceive()) sinks.add(t);
            }
            for (BlockPos p : c.links.get()) {
                if (!seen.add(p) || !sl.isLoaded(p)) continue;
                if (sl.getBlockEntity(p) instanceof StateConnectorBlockEntity n && Objects.equals(owner, n.owner)) queue.add(n);
            }
        }
        return sinks.isEmpty() ? null : new Split(sinks);
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

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel) || !allowed()) return;
        // с госпроводом коннектор «в устройство» ничего не делает сам — его кормят коннекторы «из устройства»
        if (wired() && !isOut()) return;
        IEnergyStorage src = isOut() ? front() : back(), dst = isOut() ? back() : front();
        relay.pull(src, dst, MAX);
        relay.push(dst, MAX);
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

    /** Провод крепится к середине задней грани. */
    @Override
    public Vec3 getWireAnchor() {
        Direction back = facing().getOpposite();
        return new Vec3(0.5 + back.getStepX() * 0.5, 0.5 + back.getStepY() * 0.5, 0.5 + back.getStepZ() * 0.5);
    }

    // ---------- NBT ----------
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.write(tag, reg, clientPacket);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        links.save(tag);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.read(tag, reg, clientPacket);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        links.load(tag);
    }
}
