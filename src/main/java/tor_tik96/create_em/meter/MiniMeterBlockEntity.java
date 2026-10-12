package tor_tik96.create_em.meter;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.EMFormat;
import tor_tik96.create_em.power.EnergyPort;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;

public class MiniMeterBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    /** Усреднение показаний, тиков. Полсекунды — цифры живые, но не дрожат. */
    private static final int WINDOW = 10;

    private int buffer;
    private int windowFe, windowTicks;
    private float flow;
    @Nullable private BlockCapabilityCache<IEnergyStorage, Direction> outCache;

    private final EnergyPort input = EnergyPort.input(this::receive, () -> buffer, MiniMeterBlockEntity::capacity);
    private final EnergyPort output = EnergyPort.output(this::extract, () -> buffer, MiniMeterBlockEntity::capacity);

    public MiniMeterBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.MINI_METER_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    private static int capacity() {
        return Config.safe(Config.MINI_METER_MAX_TRANSFER, 262144);
    }

    @Nullable
    public IEnergyStorage port(@Nullable Direction side) {
        if (side == null) return input;
        BlockState state = getBlockState();
        if (side == MiniMeterBlock.input(state)) return input;
        if (side == MiniMeterBlock.output(state)) return output;
        return null;
    }

    public float getFlow() {
        return flow;
    }

    private int receive(int amount, boolean simulate) {
        int accepted = Math.max(0, Math.min(amount, capacity() - buffer));
        if (!simulate && accepted > 0) {
            buffer += accepted;
            setChanged();
        }
        return accepted;
    }

    /** Всё, что ушло через выход (толкнули мы или забрал потребитель), — это и есть потребление. */
    private int extract(int amount, boolean simulate) {
        int given = Math.max(0, Math.min(amount, buffer));
        if (!simulate && given > 0) {
            buffer -= given;
            windowFe += given;
            setChanged();
        }
        return given;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel sl)) return;
        if (outCache == null) {
            Direction out = MiniMeterBlock.output(getBlockState());
            outCache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, sl,
                    worldPosition.relative(out), out.getOpposite());
        }

        IEnergyStorage target = outCache.getCapability();
        if (target != null && target.canReceive() && buffer > 0) {
            int moved = target.receiveEnergy(buffer, false);
            if (moved > 0) extract(moved, false);
        }

        if (++windowTicks >= WINDOW) {
            float f = windowFe / (float) WINDOW;
            boolean changed = f != flow;
            flow = f;
            windowFe = windowTicks = 0;
            if (changed) sendData();
        }
    }

    /** Повернули ключом — выход теперь в другую сторону. */
    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        boolean turned = state.getValue(MiniMeterBlock.FACING) != getBlockState().getValue(MiniMeterBlock.FACING);
        super.setBlockState(state);
        if (turned) {
            outCache = null;
            if (level != null) level.invalidateCapabilities(worldPosition);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Buffer", buffer);
        tag.putFloat("Flow", flow);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        buffer = Math.max(0, tag.getInt("Buffer"));
        flow = tag.getFloat("Flow");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(1, Component.translatable("create_em.mini_meter.tooltip.flow").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(EMFormat.fe(Math.round(flow)) + "/t").withStyle(ChatFormatting.AQUA))));
        return true;
    }

    private static MutableComponent line(int indent, Component c) {
        return Component.literal(" ".repeat(4 + indent * 2)).append(c);
    }
}
