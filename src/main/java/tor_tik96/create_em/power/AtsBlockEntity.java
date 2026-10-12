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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Логика АВР.
 *
 * <p>Как понять, что основной ввод «жив»: источники FE сами толкают энергию, поэтому смотрим,
 * пытается ли кто-то отдать её во ввод 1. Даже когда мы на резерве и принимаем 0, попытка
 * засчитывается. Источники, которые не толкают, а ждут, что из них возьмут, проверяем
 * пробным {@code extractEnergy(simulate)}.
 *
 * <p>Резерв, пока работаем от основного, мы не трогаем: его попытки отклоняются, аккумулятор
 * не разряжается. Счётчик на основном вводе, пока мы на резерве, тоже ничего не насчитывает.
 */
public class AtsBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    /** Сколько тиков без попыток со стороны ввода считаем его пропавшим. */
    private static final int ALIVE_TICKS = 5;
    private static final int WINDOW = 20;

    private ScrollOptionBehaviour<AtsMode> mode;

    /** Крошечный буфер: см. {@link Relay}. */
    private final Relay relay = new Relay();
    private long lastMain = Long.MIN_VALUE / 2, lastReserve = Long.MIN_VALUE / 2;
    /** С какого тика основной ввод непрерывно есть; -1 — нет. */
    private long mainSince = -1;
    private AtsSource source = AtsSource.NONE;
    private int windowFe, windowTicks;
    private float flow;
    private boolean mainAlive, reserveAlive;

    @Nullable private BlockCapabilityCache<IEnergyStorage, Direction> mainCache, reserveCache;
    private final List<BlockCapabilityCache<IEnergyStorage, Direction>> outCaches = new ArrayList<>();

    private final EnergyPort mainPort = EnergyPort.input((a, sim) -> offer(true, a, sim), () -> 0, AtsBlockEntity::capacity);
    private final EnergyPort reservePort = EnergyPort.input((a, sim) -> offer(false, a, sim), () -> 0, AtsBlockEntity::capacity);
    private final EnergyPort outPort = EnergyPort.output(this::take, () -> 0, AtsBlockEntity::capacity);

    public AtsBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.ATS_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        mode = new ScrollOptionBehaviour<>(AtsMode.class, Component.translatable("create_em.ats.mode"), this,
                new CenteredSideValueBoxTransform((s, d) -> d == s.getValue(AtsBlock.FACING))) {
            @Override
            public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
                return new ValueSettingsBoard(label, max, 1, List.of(Component.translatable("create_em.ats.mode_row")),
                        new ValueSettingsFormatter.ScrollOptionSettingsFormatter(AtsMode.values()));
            }
        };
        behaviours.add(mode);
    }

    private static int maxTransfer() {
        return Config.safe(Config.ATS_MAX_TRANSFER, 16384);
    }

    private static int capacity() {
        return maxTransfer() * 2;
    }

    /** Если смотреть на панель: слева — ввод 1, справа — ввод 2, сверху — выход. */
    public static Direction mainSide(BlockState state) {
        return state.getValue(AtsBlock.FACING).getClockWise();
    }

    public static Direction reserveSide(BlockState state) {
        return state.getValue(AtsBlock.FACING).getCounterClockWise();
    }

    @Nullable
    public IEnergyStorage port(@Nullable Direction side) {
        if (side == null || side == Direction.UP) return outPort;
        BlockState state = getBlockState();
        if (side == mainSide(state)) return mainPort;
        if (side == reserveSide(state)) return reservePort;
        return null;
    }

    private long now() {
        return level == null ? 0 : level.getGameTime();
    }

    /** Ввод толкает в нас: отмечаем, что он жив, и принимаем, только если он сейчас активный. */
    private int offer(boolean main, int amount, boolean simulate) {
        if (main) lastMain = now();
        else lastReserve = now();
        if (source != (main ? AtsSource.MAIN : AtsSource.RESERVE)) return 0;
        return relay.accept(amount, simulate, outTarget(), maxTransfer());
    }

    /** Потребитель сам забирает с выхода. */
    private int take(int amount, boolean simulate) {
        int given = relay.take(amount, simulate);
        if (!simulate) windowFe += given;
        return given;
    }

    @Nullable
    private IEnergyStorage outTarget() {
        if (outCaches.isEmpty() && level instanceof ServerLevel sl) initCaches(sl);
        return outCaches.isEmpty() ? null : outCaches.get(0).getCapability();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel sl)) return;
        if (mainCache == null) initCaches(sl);
        long t = now();

        // источники, которые сами не толкают: пробуем взять
        probe(mainCache, true);
        probe(reserveCache, false);

        boolean wasMain = mainAlive, wasReserve = reserveAlive;
        mainAlive = t - lastMain <= ALIVE_TICKS;
        reserveAlive = t - lastReserve <= ALIVE_TICKS;
        if (wasMain != mainAlive || wasReserve != reserveAlive) sendData(); // иначе очки показывали старое
        if (!mainAlive) mainSince = -1;
        else if (mainSince < 0) mainSince = t;

        AtsSource next = switch (mode.get()) {
            case OFF -> AtsSource.NONE;
            case MAIN -> AtsSource.MAIN;
            case RESERVE -> AtsSource.RESERVE;
            case AUTO -> {
                if (source == AtsSource.MAIN) yield mainAlive ? AtsSource.MAIN : AtsSource.RESERVE;
                if (source == AtsSource.NONE && mainAlive) yield AtsSource.MAIN; // при загрузке — сразу
                boolean stable = mainAlive && t - mainSince >= Config.safe(Config.ATS_RETURN_DELAY, 60);
                yield stable ? AtsSource.MAIN : AtsSource.RESERVE;
            }
        };
        if (next != source) {
            source = next;
            setChanged();
        }

        windowFe += relay.push(outTarget()); // одно предложение нагрузке за тик

        if (++windowTicks >= WINDOW) {
            float f = windowFe / (float) WINDOW;
            boolean changed = f != flow || windowFe > 0;
            flow = f;
            windowFe = windowTicks = 0;
            if (changed) sendData();
        }

        boolean alarm = mode.get() != AtsMode.OFF && !mainAlive;
        BlockState state = getBlockState();
        if (state.getValue(AtsBlock.SOURCE) != source || state.getValue(AtsBlock.ALARM) != alarm) {
            level.setBlock(worldPosition, state.setValue(AtsBlock.SOURCE, source).setValue(AtsBlock.ALARM, alarm), Block.UPDATE_ALL);
            sendData();
        }
    }

    /** Источник, который сам не толкает: проверяем, есть ли в нём энергия, и, если ввод активен, берём сами. */
    private void probe(@Nullable BlockCapabilityCache<IEnergyStorage, Direction> cache, boolean main) {
        if (cache == null) return;
        IEnergyStorage src = cache.getCapability();
        if (src == null || !src.canExtract()) return;
        if (src.extractEnergy(maxTransfer(), true) <= 0) return;
        if (main) lastMain = now();
        else lastReserve = now();
        if (source == (main ? AtsSource.MAIN : AtsSource.RESERVE))
            relay.pull(src, outTarget(), maxTransfer());
    }

    private void initCaches(ServerLevel sl) {
        Direction main = mainSide(getBlockState()), reserve = reserveSide(getBlockState());
        mainCache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, sl,
                worldPosition.relative(main), main.getOpposite());
        reserveCache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, sl,
                worldPosition.relative(reserve), reserve.getOpposite());
        outCaches.clear();
        outCaches.add(BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, sl,
                worldPosition.above(), Direction.DOWN));
    }

    /** Повернули ключом — стороны поменялись, кэши соседей пересоздаём. */
    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        boolean turned = state.getValue(AtsBlock.FACING) != getBlockState().getValue(AtsBlock.FACING);
        super.setBlockState(state);
        if (turned) {
            mainCache = null;
            reserveCache = null;
            outCaches.clear();
            if (level != null) level.invalidateCapabilities(worldPosition);
        }
    }

    // ---------- сохранение ----------

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("Source", source.getSerializedName());
        if (clientPacket) {
            tag.putFloat("Flow", flow);
            tag.putBoolean("MainAlive", mainAlive);
            tag.putBoolean("ReserveAlive", reserveAlive);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        source = switch (tag.getString("Source")) {
            case "main" -> AtsSource.MAIN;
            case "reserve" -> AtsSource.RESERVE;
            default -> AtsSource.NONE;
        };
        if (clientPacket) {
            flow = tag.getFloat("Flow");
            mainAlive = tag.getBoolean("MainAlive");
            reserveAlive = tag.getBoolean("ReserveAlive");
        }
    }

    // ---------- очки ----------

    private static MutableComponent line(int indent, Component c) {
        return Component.literal(" ".repeat(4 + indent * 2)).append(c);
    }

    private static Component yesNo(boolean ok) {
        return Component.translatable(ok ? "create_em.ats.tooltip.present" : "create_em.ats.tooltip.absent")
                .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(1, Component.translatable("create_em.ats.tooltip.mode").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(mode.get().getTranslationKey()).withStyle(ChatFormatting.AQUA))));
        AtsSource s = getBlockState().getValue(AtsBlock.SOURCE);
        tooltip.add(line(1, Component.translatable("create_em.ats.tooltip.source").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(s.translationKey()).withStyle(
                        s == AtsSource.MAIN ? ChatFormatting.GREEN : s == AtsSource.RESERVE ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY))));
        tooltip.add(line(1, Component.translatable("create_em.ats.tooltip.main").withStyle(ChatFormatting.GRAY).append(yesNo(mainAlive))));
        tooltip.add(line(1, Component.translatable("create_em.ats.tooltip.reserve").withStyle(ChatFormatting.GRAY).append(yesNo(reserveAlive))));
        tooltip.add(line(1, Component.translatable("create_em.ats.tooltip.flow").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(EMFormat.fe(Math.round(flow)) + "/t").withStyle(ChatFormatting.AQUA))));
        return true;
    }
}
