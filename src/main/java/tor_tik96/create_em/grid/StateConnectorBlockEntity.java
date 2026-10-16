package tor_tik96.create_em.grid;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.power.EnergyPort;
import tor_tik96.create_em.power.Relay;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;
import java.util.UUID;

/**
 * Проходной узел ({@link Relay}) между промаркированным устройством (спереди) и обычной клеммой (сзади).
 * Работает, только если устройство спереди без маркировки или под маркировкой хозяина коннектора.
 */
public class StateConnectorBlockEntity extends SmartBlockEntity {
    private static final int MAX = Integer.MAX_VALUE / 2;

    private final Relay relay = new Relay();
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

    @Nullable public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }

    void setOwner(UUID id, String name) {
        owner = id;
        ownerName = name;
        setChanged();
    }

    private Direction facing() {
        return getBlockState().getValue(StateConnectorBlock.FACING);
    }

    private boolean isOut() {
        return getBlockState().getValue(StateConnectorBlock.OUT);
    }

    @Nullable
    public IEnergyStorage port(@Nullable Direction side) {
        if (side == null) return null;
        if (side == facing()) return isOut() ? frontIn : null;
        if (side == facing().getOpposite()) return isOut() ? backOut : backIn;
        return null;
    }

    /** Чужое устройство (под чужой маркировкой) — не работаем ни в какую сторону. */
    private boolean allowed() {
        if (!(level instanceof ServerLevel sl) || owner == null) return false;
        GridMarks marks = GridMarks.get(sl);
        return ok(marks, sl, worldPosition.relative(facing())) && ok(marks, sl, worldPosition.relative(facing().getOpposite()));
    }

    private boolean ok(GridMarks marks, ServerLevel sl, BlockPos pos) {
        GridMarks.Mark m = marks.at(sl, pos);
        return m == null || m.owner().equals(owner);
    }

    @Nullable private IEnergyStorage front() { return neighbor(facing()); }
    @Nullable private IEnergyStorage back() { return neighbor(facing().getOpposite()); }

    @Nullable
    private IEnergyStorage neighbor(Direction d) {
        if (!(level instanceof ServerLevel sl)) return null;
        BlockPos p = worldPosition.relative(d);
        if (!sl.isLoaded(p)) return null;
        return GridIsolation.bypass(() -> sl.getCapability(Capabilities.EnergyStorage.BLOCK, p, d.getOpposite()));
    }

    void onModeChanged() {
        relay.clear();
        if (level != null) GridIsolation.invalidate(level, worldPosition);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel) || !allowed()) return;
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

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.write(tag, reg, clientPacket);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.read(tag, reg, clientPacket);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
    }
}
