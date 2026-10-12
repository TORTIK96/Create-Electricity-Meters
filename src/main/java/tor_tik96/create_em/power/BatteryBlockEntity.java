package tor_tik96.create_em.power;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.EMFormat;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;

public class BatteryBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final int WINDOW = 20;

    private int energy;
    private int inWindow, outWindow, windowTicks;
    private float inRate, outRate;
    private int lastComparator = -1;
    @Nullable private BlockCapabilityCache<IEnergyStorage, Direction> upCache;

    private final EnergyPort input = EnergyPort.input(this::receive, () -> energy, BatteryBlockEntity::capacity);
    private final EnergyPort output = EnergyPort.output(this::extract, () -> energy, BatteryBlockEntity::capacity);

    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.BATTERY_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public static int capacity() {
        return Config.safe(Config.BATTERY_CAPACITY, 2_000_000);
    }

    private static int maxIo() {
        return Config.safe(Config.BATTERY_MAX_IO, 4096);
    }

    /** Сверху — выход, лицо — шкала (ничего), остальное — вход. */
    @Nullable
    public IEnergyStorage port(@Nullable Direction side) {
        if (side == Direction.UP) return output;
        if (side != null && side == getBlockState().getValue(BatteryBlock.FACING)) return null;
        return input;
    }

    private int receive(int amount, boolean simulate) {
        int accepted = Math.max(0, Math.min(Math.min(amount, maxIo()), capacity() - energy));
        if (!simulate && accepted > 0) {
            energy += accepted;
            inWindow += accepted;
            setChanged();
        }
        return accepted;
    }

    private int extract(int amount, boolean simulate) {
        int given = Math.max(0, Math.min(Math.min(amount, maxIo()), energy));
        if (!simulate && given > 0) {
            energy -= given;
            outWindow += given;
            setChanged();
        }
        return given;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel sl)) return;
        if (upCache == null)
            upCache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, sl, worldPosition.above(), Direction.DOWN);

        // сам толкаем энергию вверх
        IEnergyStorage target = upCache.getCapability();
        if (target != null && target.canReceive() && energy > 0) {
            int moved = target.receiveEnergy(Math.min(energy, maxIo()), false);
            if (moved > 0) extract(moved, false);
        }

        if (++windowTicks >= WINDOW) {
            float newIn = inWindow / (float) WINDOW, newOut = outWindow / (float) WINDOW;
            boolean changed = newIn != inRate || newOut != outRate || inWindow > 0 || outWindow > 0;
            inRate = newIn;
            outRate = newOut;
            inWindow = outWindow = windowTicks = 0;
            if (changed) sendData();
        }

        int charge = chargeLevel();
        BlockState state = getBlockState();
        if (state.getValue(BatteryBlock.CHARGE) != charge)
            level.setBlock(worldPosition, state.setValue(BatteryBlock.CHARGE, charge), Block.UPDATE_CLIENTS);

        int cmp = comparatorSignal();
        if (cmp != lastComparator) {
            lastComparator = cmp;
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        }
    }

    private int chargeLevel() {
        if (energy <= 0) return 0;
        return Math.min(4, 1 + (int) ((long) energy * 4 / capacity()));
    }

    public int comparatorSignal() {
        if (energy <= 0) return 0;
        return Math.min(15, 1 + (int) ((long) energy * 14 / capacity()));
    }

    // ---------- сохранение, в том числе в предмет ----------

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", energy);
        if (clientPacket) {
            tag.putFloat("In", inRate);
            tag.putFloat("Out", outRate);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        energy = Math.max(0, tag.getInt("Energy"));
        if (clientPacket) {
            inRate = tag.getFloat("In");
            outRate = tag.getFloat("Out");
        }
    }

    /** Заряд уходит в предмет при ломании (loot: copy_components) и возвращается при установке. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (energy > 0) components.set(EMRegistry.STORED_ENERGY.get(), energy);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        energy = Math.max(0, input.getOrDefault(EMRegistry.STORED_ENERGY.get(), 0));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Energy");
    }

    // ---------- очки ----------

    private static MutableComponent line(int indent, Component c) {
        return Component.literal(" ".repeat(4 + indent * 2)).append(c);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        int cap = capacity();
        int pct = (int) ((long) energy * 100 / cap);
        tooltip.add(line(1, Component.translatable("create_em.battery.tooltip.charge").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(EMFormat.fe(energy) + " / " + EMFormat.fe(cap) + " (" + pct + "%)").withStyle(ChatFormatting.AQUA))));
        tooltip.add(line(1, Component.translatable("create_em.battery.tooltip.in").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(EMFormat.fe(Math.round(inRate)) + "/t").withStyle(ChatFormatting.GREEN))));
        tooltip.add(line(1, Component.translatable("create_em.battery.tooltip.out").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(EMFormat.fe(Math.round(outRate)) + "/t").withStyle(ChatFormatting.GOLD))));
        return true;
    }
}
