package tor_tik96.create_em.power;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;

/** Включённый рубильник — проходной узел ({@link Relay}), выключенный — разрыв. */
public class KnifeSwitchBlockEntity extends SmartBlockEntity {
    private static final int MAX = Integer.MAX_VALUE / 2;

    private final Relay relay = new Relay();
    @Nullable private BlockCapabilityCache<IEnergyStorage, Direction> inCache, outCache;

    private final EnergyPort input = EnergyPort.input(
            (a, sim) -> isOn() ? relay.accept(a, sim, neighbor(true), MAX) : 0, relay::buffer, () -> MAX);
    private final EnergyPort output = EnergyPort.output(
            (a, sim) -> isOn() ? relay.take(a, sim) : 0, relay::buffer, () -> MAX);

    public KnifeSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.KNIFE_SWITCH_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    private boolean isOn() {
        return getBlockState().getValue(KnifeSwitchBlock.ON);
    }

    @Nullable
    public IEnergyStorage port(@Nullable Direction side) {
        BlockState state = getBlockState();
        if (side == null || side == KnifeSwitchBlock.input(state)) return input;
        if (side == KnifeSwitchBlock.output(state)) return output;
        return null;
    }

    @Nullable
    private IEnergyStorage neighbor(boolean out) {
        if (!(level instanceof ServerLevel sl)) return null;
        if (outCache == null || inCache == null) {
            Direction o = KnifeSwitchBlock.output(getBlockState()), i = KnifeSwitchBlock.input(getBlockState());
            outCache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, sl, worldPosition.relative(o), o.getOpposite());
            inCache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, sl, worldPosition.relative(i), i.getOpposite());
        }
        return (out ? outCache : inCache).getCapability();
    }

    void onToggled(boolean on) {
        if (!on) relay.clear(); // разомкнули — то, что было «в проводе», пропадает
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel) || !isOn()) return;
        IEnergyStorage target = neighbor(true);
        relay.pull(neighbor(false), target, MAX);
        relay.push(target, MAX);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        boolean turned = state.getValue(KnifeSwitchBlock.FACING) != getBlockState().getValue(KnifeSwitchBlock.FACING);
        super.setBlockState(state);
        if (turned) {
            outCache = null;
            inCache = null;
            if (level != null) level.invalidateCapabilities(worldPosition);
        }
    }
}
