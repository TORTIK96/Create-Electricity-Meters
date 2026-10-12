package tor_tik96.create_em.power;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.EMFormat;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;

/**
 * Зарядка — ограничена, разряд — без ограничения: за один тик выходу предлагается весь заряд.
 * Что выход не принял, предлагается в следующие тики; если выход молчит секунду, разряд
 * прерывается и конденсатор снова заряжается (энергия не теряется).
 */
public class CapacitorBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final int STALL_TICKS = 20;

    private ScrollOptionBehaviour<CapacitorMode> mode;
    private int energy;
    private boolean discharging;
    private int stall;
    private boolean lastTrigger;
    /** Сколько отдал последний разряд — для очков. */
    private int lastShot;
    @Nullable private BlockCapabilityCache<IEnergyStorage, Direction> outCache;

    private final EnergyPort input = EnergyPort.input(this::receive, () -> energy, CapacitorBlockEntity::capacity);
    private final EnergyPort output = EnergyPort.output(this::extract, () -> energy, CapacitorBlockEntity::capacity);

    public CapacitorBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.CAPACITOR_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        mode = new ScrollOptionBehaviour<>(CapacitorMode.class, Component.translatable("create_em.capacitor.mode"), this,
                new CenteredSideValueBoxTransform((s, d) -> d.getAxis().isHorizontal() && d.getAxis() != s.getValue(CapacitorBlock.FACING).getAxis())) {
            @Override
            public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
                return new ValueSettingsBoard(label, max, 1, List.of(Component.translatable("create_em.capacitor.mode_row")),
                        new ValueSettingsFormatter.ScrollOptionSettingsFormatter(CapacitorMode.values()));
            }
        };
        behaviours.add(mode);
    }

    public static int capacity() {
        return Config.safe(Config.CAPACITOR_CAPACITY, 500_000);
    }

    /** Сзади — зарядка, спереди — разряд, остальное — ничего. */
    @Nullable
    public IEnergyStorage port(@Nullable Direction side) {
        Direction front = getBlockState().getValue(CapacitorBlock.FACING);
        if (side == front) return output;
        if (side == null || side == front.getOpposite()) return input;
        return null;
    }

    private int receive(int amount, boolean simulate) {
        if (discharging) return 0;
        int rate = Config.safe(Config.CAPACITOR_CHARGE_RATE, 4096);
        int accepted = Math.max(0, Math.min(Math.min(amount, rate), capacity() - energy));
        if (!simulate && accepted > 0) {
            energy += accepted;
            setChanged();
        }
        return accepted;
    }

    /** Забрать из выхода можно только во время разряда. */
    private int extract(int amount, boolean simulate) {
        if (!discharging) return 0;
        int given = Math.max(0, Math.min(amount, energy));
        if (!simulate && given > 0) {
            energy -= given;
            lastShot += given;
            setChanged();
        }
        return given;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel sl)) return;
        if (outCache == null) {
            Direction front = getBlockState().getValue(CapacitorBlock.FACING);
            outCache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, sl,
                    worldPosition.relative(front), front.getOpposite());
        }

        boolean trigger = level.getSignal(worldPosition.above(), Direction.UP) > 0;
        boolean rising = trigger && !lastTrigger;
        lastTrigger = trigger;

        if (!discharging && energy > 0) {
            boolean start = switch (mode.get()) {
                case ON_FULL -> energy >= capacity();
                case ON_SIGNAL -> rising;
                case HOLD -> false;
            };
            if (start) startDischarge(sl);
        }

        if (discharging) {
            IEnergyStorage target = outCache.getCapability();
            int moved = 0;
            if (target != null && target.canReceive() && energy > 0)
                moved = target.receiveEnergy(energy, false); // весь заряд разом
            if (moved > 0) extract(moved, false);
            stall = moved > 0 ? 0 : stall + 1;
            if (energy <= 0 || stall >= STALL_TICKS) {
                discharging = false;
                stall = 0;
                setChanged();
                sendData();
            }
        }

        updateState();
    }

    private void startDischarge(ServerLevel sl) {
        discharging = true;
        stall = 0;
        lastShot = 0;
        setChanged();
        sendData();
        // щелчок и искры на выходе
        Direction front = getBlockState().getValue(CapacitorBlock.FACING);
        double x = worldPosition.getX() + .5 + front.getStepX() * .55;
        double y = worldPosition.getY() + .5;
        double z = worldPosition.getZ() + .5 + front.getStepZ() * .55;
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 12, .15, .15, .15, .3);
        sl.playSound(null, worldPosition, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, .8f, 1.6f);
    }

    private void updateState() {
        int cap = capacity();
        int charge = energy <= 0 ? 0 : Math.min(4, 1 + (int) ((long) energy * 4 / cap));
        int power = energy <= 0 ? 0 : Math.min(15, 1 + (int) ((long) energy * 14 / cap));
        if (energy >= cap) power = 15;
        BlockState state = getBlockState();
        if (state.getValue(CapacitorBlock.CHARGE) != charge || state.getValue(CapacitorBlock.POWER) != power
                || state.getValue(CapacitorBlock.DISCHARGING) != discharging) {
            level.setBlock(worldPosition, state.setValue(CapacitorBlock.CHARGE, charge)
                    .setValue(CapacitorBlock.POWER, power).setValue(CapacitorBlock.DISCHARGING, discharging), Block.UPDATE_ALL);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        boolean turned = state.getValue(CapacitorBlock.FACING) != getBlockState().getValue(CapacitorBlock.FACING);
        super.setBlockState(state);
        if (turned) {
            outCache = null;
            if (level != null) level.invalidateCapabilities(worldPosition);
        }
    }

    // ---------- сохранение ----------

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", energy);
        tag.putBoolean("Discharging", discharging);
        tag.putBoolean("Trigger", lastTrigger);
        tag.putInt("LastShot", lastShot);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        energy = Math.max(0, tag.getInt("Energy"));
        discharging = tag.getBoolean("Discharging");
        lastTrigger = tag.getBoolean("Trigger");
        lastShot = tag.getInt("LastShot");
    }

    // ---------- очки ----------

    private static MutableComponent line(int indent, Component c) {
        return Component.literal(" ".repeat(4 + indent * 2)).append(c);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(1, Component.translatable("create_em.capacitor.tooltip.mode").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(mode.get().getTranslationKey()).withStyle(ChatFormatting.AQUA))));
        int cap = capacity();
        tooltip.add(line(1, Component.translatable("create_em.battery.tooltip.charge").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(EMFormat.fe(energy) + " / " + EMFormat.fe(cap) + " (" + (int) ((long) energy * 100 / cap) + "%)")
                        .withStyle(ChatFormatting.AQUA))));
        if (discharging)
            tooltip.add(line(1, Component.translatable("create_em.capacitor.tooltip.discharging").withStyle(ChatFormatting.GOLD)));
        if (lastShot > 0)
            tooltip.add(line(1, Component.translatable("create_em.capacitor.tooltip.last_shot").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(EMFormat.fe(lastShot)).withStyle(ChatFormatting.GOLD))));
        return true;
    }
}
