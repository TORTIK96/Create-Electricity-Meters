package tor_tik96.create_em.process;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.EMFormat;

import java.util.List;

public class ElectricCatalystBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    /** Сколько тиков работы должно накопиться в буфере, чтобы запуститься после простоя. */
    private static final int START_RESERVE_TICKS = 20;
    /** Как далеко искать вентилятор, чтобы сразу сообщить ему о смене режима. */
    private static final int FAN_SEARCH = 3;

    private ScrollOptionBehaviour<CatalystMode> mode;
    private final Buffer energy = new Buffer();
    /** Режим нужен, но энергии не хватает — показываем в очках. */
    private boolean starving;

    public ElectricCatalystBlockEntity(BlockPos pos, BlockState state) {
        super(tor_tik96.create_em.registry.EMRegistry.CATALYST_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        mode = new ScrollOptionBehaviour<>(CatalystMode.class,
                Component.translatable("create_em.catalyst.mode"), this, new CenteredSideValueBoxTransform()) {
            @Override
            public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
                return new ValueSettingsBoard(label, max, 1,
                        List.of(Component.translatable("create_em.catalyst.mode_row")),
                        new ValueSettingsFormatter.ScrollOptionSettingsFormatter(CatalystMode.values()));
            }
        };
        behaviours.add(mode);
    }

    public IEnergyStorage getEnergy() {
        return energy;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        CatalystProcess wanted = wanted();
        CatalystProcess current = getBlockState().getValue(ElectricCatalystBlock.PROCESS);
        int cost = wanted.cost();

        boolean run;
        if (wanted == CatalystProcess.NONE) run = false;
        else if (wanted == current) run = energy.stored() >= cost;
        else run = energy.stored() >= Math.min(energy.getMaxEnergyStored(), (long) cost * START_RESERVE_TICKS);

        if (run) energy.drain(cost);
        boolean nowStarving = wanted != CatalystProcess.NONE && !run;
        if (nowStarving != starving) {
            starving = nowStarving;
            sendData();
        }

        CatalystProcess next = run ? wanted : CatalystProcess.NONE;
        ambientSound(next);
        if (next != current) {
            level.setBlock(worldPosition, ElectricCatalystBlock.withProcess(getBlockState(), next), Block.UPDATE_ALL);
            notifyFans();
        }
    }

    /** Изредка — звук того, что внутри: вода, костёр, лава, призрачный огонь. */
    private void ambientSound(CatalystProcess p) {
        if (p == CatalystProcess.NONE || level.random.nextInt(40) != 0) return;
        var sound = switch (p) {
            case SPLASHING -> net.minecraft.sounds.SoundEvents.WATER_AMBIENT;
            case SMOKING -> net.minecraft.sounds.SoundEvents.CAMPFIRE_CRACKLE;
            case BLASTING -> net.minecraft.sounds.SoundEvents.LAVA_AMBIENT;
            default -> net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value();
        };
        level.playSound(null, worldPosition, sound, net.minecraft.sounds.SoundSource.BLOCKS, 0.35f,
                0.9f + level.random.nextFloat() * 0.2f);
    }

    /** Режим, который выбран переключателем или сигналом (без учёта энергии). */
    public CatalystProcess wanted() {
        CatalystMode m = mode.get();
        if (m.fixed != null) return m.fixed;
        return level == null ? CatalystProcess.NONE : CatalystProcess.fromSignal(level.getBestNeighborSignal(worldPosition));
    }

    /** Вентилятор и так перепроверяет поток раз в ~1.5 с; ближним сообщаем сразу. */
    private void notifyFans() {
        for (Direction d : Direction.values()) {
            for (int i = 1; i <= FAN_SEARCH; i++) {
                BlockEntity be = level.getBlockEntity(worldPosition.relative(d, i));
                if (be instanceof EncasedFanBlockEntity fan) {
                    fan.blockInFrontChanged();
                    break;
                }
            }
        }
    }

    // ---------- сохранение ----------

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", energy.stored());
        tag.putBoolean("Starving", starving);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        energy.set(tag.getInt("Energy"));
        starving = tag.getBoolean("Starving");
    }

    // ---------- очки инженера ----------

    private static final ChatFormatting LABEL = ChatFormatting.GRAY;

    private static MutableComponent line(int indent, Component c) {
        return Component.literal(" ".repeat(4 + indent * 2)).append(c); // отступы как у Create
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));

        CatalystMode m = mode.get();
        tooltip.add(line(1, Component.translatable("create_em.catalyst.tooltip.switch").withStyle(LABEL)
                .append(Component.translatable(m.getTranslationKey()).withStyle(ChatFormatting.AQUA))));

        CatalystProcess current = getBlockState().getValue(ElectricCatalystBlock.PROCESS);
        MutableComponent state;
        if (starving) state = Component.translatable("create_em.catalyst.tooltip.no_power").withStyle(ChatFormatting.RED);
        else if (current == CatalystProcess.NONE) state = Component.translatable(current.translationKey()).withStyle(ChatFormatting.DARK_GRAY);
        else state = Component.translatable(current.translationKey()).withStyle(ChatFormatting.GOLD);
        tooltip.add(line(1, Component.translatable("create_em.catalyst.tooltip.process").withStyle(LABEL).append(state)));

        int cost = current != CatalystProcess.NONE ? current.cost() : 0;
        tooltip.add(line(1, Component.translatable("create_em.catalyst.tooltip.usage").withStyle(LABEL)
                .append(Component.literal(EMFormat.fe(cost) + "/t").withStyle(ChatFormatting.AQUA))));
        tooltip.add(line(1, Component.translatable("create_em.catalyst.tooltip.stored").withStyle(LABEL)
                .append(Component.literal(EMFormat.fe(energy.stored()) + " / " + EMFormat.fe(energy.getMaxEnergyStored()))
                        .withStyle(ChatFormatting.AQUA))));
        return true;
    }

    // ---------- буфер ----------

    /** Принимает FE с любой стороны, отдать её нельзя. Ёмкость — из конфига. */
    private final class Buffer extends EnergyStorage {
        Buffer() {
            super(Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
        }

        int stored() {
            return energy;
        }

        void set(int v) {
            energy = Math.max(0, v);
        }

        void drain(int v) {
            energy = Math.max(0, energy - v);
            setChanged();
        }

        @Override
        public int getMaxEnergyStored() {
            return Config.CATALYST_CAPACITY.get();
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int accepted = Math.max(0, Math.min(toReceive, getMaxEnergyStored() - energy));
            if (!simulate && accepted > 0) {
                energy += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public boolean canExtract() {
            return false;
        }
    }
}
