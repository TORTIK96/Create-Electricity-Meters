package tor_tik96.create_em.grid;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.gov.Gov;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Госизоляция: блок под госмаркировкой не принимает и не отдаёт FE никому, кроме госконнекторов
 * того же хозяина (и соседних блоков под его же маркировкой).
 *
 * <p>Как это работает с чужими модами: мы первыми (приоритет HIGHEST) регистрируем поставщика
 * энергии для всех блоков. Для промаркированного блока он отдаёт «мёртвое» хранилище, и до настоящего
 * дело не доходит. Для соседа промаркированного блока — тоже «мёртвое», но только со стороны, смотрящей
 * на маркированный блок: так маркированный блок не может сам «вытолкнуть» энергию в чужой провод.
 * Госконнектор обходит изоляцию через {@link #bypass}.
 */
public final class GridIsolation {
    private static final ThreadLocal<int[]> BYPASS = ThreadLocal.withInitial(() -> new int[1]);
    /** Ничего не принимает и не отдаёт. */
    private static final IEnergyStorage DEAD = new EnergyStorage(0, 0, 0);

    public static void init(IEventBus modBus) {
        modBus.addListener(EventPriority.HIGHEST, GridIsolation::registerCaps);
        modBus.addListener(GridIsolation::setup);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, GridIsolation::onBreak);
        NeoForge.EVENT_BUS.addListener(GridIsolation::onPlace);
        NeoForge.EVENT_BUS.addListener(GridIsolation::onExplosion);
    }

    private static void registerCaps(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, GridIsolation::provide,
                BuiltInRegistries.BLOCK.stream().toArray(Block[]::new));
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BlockMovementChecks.registerMovementAllowedCheck((state, level, pos) ->
                level instanceof ServerLevel sl && isProtected(sl, pos, state)
                        ? BlockMovementChecks.CheckResult.FAIL : BlockMovementChecks.CheckResult.PASS));
    }

    @Nullable
    private static IEnergyStorage provide(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be, @Nullable Direction side) {
        if (!(level instanceof ServerLevel sl) || BYPASS.get()[0] > 0) return null;
        GridMarks marks = GridMarks.get(sl);
        if (marks.isEmpty()) return null;
        GridMarks.Mark own = marks.at(state, pos);
        GridMarks.Mark near = side == null ? null : marks.at(sl, pos.relative(side));
        if (own != null) return near != null && near.owner().equals(own.owner()) ? null : DEAD;
        if (near != null) return state.is(EMRegistry.STATE_CONNECTOR.get()) ? null : DEAD;
        return null;
    }

    /** Выполнить в обход изоляции (только для госконнектора и проверок). */
    public static <T> T bypass(Supplier<T> action) {
        int[] depth = BYPASS.get();
        depth[0]++;
        try {
            return action.get();
        } finally {
            depth[0]--;
        }
    }

    /** Есть ли у блока хоть какая-то энергия (с любой стороны). */
    public static boolean hasEnergy(Level level, BlockPos pos) {
        return bypass(() -> {
            if (level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null) != null) return true;
            for (Direction d : Direction.values())
                if (level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, d) != null) return true;
            return false;
        });
    }

    /** Блок сменил статус — чужие провода должны заново спросить и его, и соседей. */
    public static void invalidate(Level level, BlockPos pos) {
        level.invalidateCapabilities(pos);
        for (Direction d : Direction.values()) level.invalidateCapabilities(pos.relative(d));
    }

    private static boolean isProtected(ServerLevel level, BlockPos pos, BlockState state) {
        return state.is(EMRegistry.STATE_CONNECTOR.get()) || GridMarks.get(level).at(state, pos) != null;
    }

    public static boolean canManage(Player player, @Nullable UUID owner, @Nullable UUID placer) {
        UUID id = player.getUUID();
        return id.equals(owner) || id.equals(placer) || Gov.isOperator(player);
    }

    // ---------- защита ----------

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Player player = event.getPlayer();
        GridMarks marks = GridMarks.get(level);

        if (state.is(EMRegistry.STATE_CONNECTOR.get())
                && level.getBlockEntity(pos) instanceof StateConnectorBlockEntity be
                && !canManage(player, be.getOwner(), null)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("create_em.connector.protected", be.getOwnerName())
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        GridMarks.Mark mark = marks.at(state, pos);
        if (mark != null) {
            if (!canManage(player, mark.owner(), marks.placer(pos))) {
                event.setCanceled(true);
                player.displayClientMessage(Component.translatable("create_em.mark.protected", mark.name())
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            marks.unmark(pos);
            if (!player.isCreative())
                Block.popResource(level, pos, new ItemStack(EMRegistry.STATE_MARK.get()));
            invalidate(level, pos);
        }
        marks.setPlacer(pos, null);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        GridMarks marks = GridMarks.get(level);
        marks.unmark(pos); // на этом месте новый блок — старая маркировка не в счёт
        boolean energy = event.getPlacedBlock().hasBlockEntity() && hasEnergy(level, pos);
        marks.setPlacer(pos, energy && event.getEntity() instanceof Player p ? p.getUUID() : null);
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        event.getAffectedBlocks().removeIf(pos -> isProtected(level, pos, level.getBlockState(pos)));
    }

    private GridIsolation() {}
}
