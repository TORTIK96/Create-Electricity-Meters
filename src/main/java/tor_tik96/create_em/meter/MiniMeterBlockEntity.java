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

    private int windowFe, windowTicks;
    /** Потребление (что реально потрачено) и проход (что принято) за окно, FE/t. */
    private float flow, passed;
    /** Запас потребителя в начале окна; -1 — ещё не замеряли. */
    private long targetStoredAtStart = -1;
    /** Защита от замыкания, если вход и выход оказались в одной сети проводов. */
    private boolean busy;
    @Nullable private BlockCapabilityCache<IEnergyStorage, Direction> inCache, outCache;

    /**
     * Счётчик ничего не хранит: что пришло на вход, тут же передаётся на выход в том же вызове,
     * а потребитель, который сам забирает с выхода, на самом деле забирает со входа.
     * Поэтому без питания на входе через счётчик не проходит ничего, и показания честные.
     */
    private final EnergyPort input = EnergyPort.input(this::passForward, () -> 0, MiniMeterBlockEntity::capacity);
    private final EnergyPort output = EnergyPort.output(this::passBackward, () -> 0, MiniMeterBlockEntity::capacity);

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

    @Nullable
    private IEnergyStorage neighbor(boolean out) {
        if (!(level instanceof ServerLevel sl)) return null;
        if (outCache == null || inCache == null) {
            Direction o = MiniMeterBlock.output(getBlockState()), i = MiniMeterBlock.input(getBlockState());
            outCache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, sl, worldPosition.relative(o), o.getOpposite());
            inCache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, sl, worldPosition.relative(i), i.getOpposite());
        }
        return (out ? outCache : inCache).getCapability();
    }

    /** Источник толкает во вход — сразу толкаем потребителю на выходе. */
    private int passForward(int amount, boolean simulate) {
        if (busy) return 0;
        IEnergyStorage target = neighbor(true);
        if (target == null || !target.canReceive()) return 0;
        busy = true;
        try {
            int moved = target.receiveEnergy(Math.min(amount, capacity()), simulate);
            if (!simulate) windowFe += moved;
            return moved;
        } finally {
            busy = false;
        }
    }

    /** Потребитель забирает с выхода — забираем у источника на входе. */
    private int passBackward(int amount, boolean simulate) {
        if (busy) return 0;
        IEnergyStorage source = neighbor(false);
        if (source == null || !source.canExtract()) return 0;
        busy = true;
        try {
            int moved = source.extractEnergy(Math.min(amount, capacity()), simulate);
            if (!simulate) windowFe += moved;
            return moved;
        } finally {
            busy = false;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel)) return;

        // источник, который сам не толкает (например, аккумулятор другого мода): тянем со входа сами
        IEnergyStorage source = neighbor(false), target = neighbor(true);
        if (source != null && target != null && source.canExtract() && target.canReceive() && !busy) {
            int can = source.extractEnergy(capacity(), true);
            if (can > 0) {
                int fits = target.receiveEnergy(can, true);
                if (fits > 0) {
                    int taken = source.extractEnergy(fits, false);
                    windowFe += target.receiveEnergy(taken, false);
                }
            }
        }

        if (++windowTicks >= WINDOW) {
            // потребление = принято − насколько вырос запас самого потребителя
            // (двигатель или машина сначала заполняют свой буфер — это ещё не расход)
            long storedNow = target == null ? -1 : target.getEnergyStored();
            // убыль запаса не прибавляем: что потребитель тратит из своего буфера, через счётчик не проходило
            long grew = (storedNow >= 0 && targetStoredAtStart >= 0) ? Math.max(0, storedNow - targetStoredAtStart) : 0;
            float used = Math.max(0, windowFe - grew) / (float) WINDOW;
            float pass = windowFe / (float) WINDOW;
            boolean changed = used != flow || pass != passed;
            flow = used;
            passed = pass;
            windowFe = windowTicks = 0;
            targetStoredAtStart = storedNow;
            if (changed) sendData();
        }
    }

    /** Повернули ключом — вход и выход поменялись местами. */
    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        boolean turned = state.getValue(MiniMeterBlock.FACING) != getBlockState().getValue(MiniMeterBlock.FACING);
        super.setBlockState(state);
        if (turned) {
            outCache = null;
            inCache = null;
            if (level != null) level.invalidateCapabilities(worldPosition);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Flow", flow);
        tag.putFloat("Passed", passed);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        flow = tag.getFloat("Flow");
        passed = tag.getFloat("Passed");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(1, Component.translatable("create_em.mini_meter.tooltip.flow").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(EMFormat.fe(Math.round(flow)) + "/t").withStyle(ChatFormatting.AQUA))));
        if (Math.round(passed) != Math.round(flow))
            tooltip.add(line(1, Component.translatable("create_em.mini_meter.tooltip.passed").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(EMFormat.fe(Math.round(passed)) + "/t").withStyle(ChatFormatting.DARK_AQUA))));
        return true;
    }

    private static MutableComponent line(int indent, Component c) {
        return Component.literal(" ".repeat(4 + indent * 2)).append(c);
    }
}
