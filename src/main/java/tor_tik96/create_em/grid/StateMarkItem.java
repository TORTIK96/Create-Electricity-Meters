package tor_tik96.create_em.grid;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import tor_tik96.create_em.gov.Gov;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.*;

/**
 * Госмаркировка. ПКМ по своему энергоблоку — изолировать: он будет принимать и отдавать FE
 * только через ваши госконнекторы. Shift-ПКМ — снять (хозяин маркировки, тот, кто поставил блок, или оператор).
 */
public class StateMarkItem extends Item {
    public StateMarkItem(Properties props) {
        super(props);
    }

    /**
     * Для силового кабеля — все соединённые кабели с той же маркировкой ({@code owner}, null — без маркировки),
     * которыми игрок может распоряжаться. Для остального — только сам блок.
     */
    private static List<BlockPos> cableRun(ServerLevel level, GridMarks marks, BlockPos start, UUID owner, Player player) {
        if (!(level.getBlockState(start).getBlock() instanceof tor_tik96.create_em.power.PowerCableBlock)) return List.of(start);
        List<BlockPos> run = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>(Set.of(start));
        Deque<BlockPos> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty() && run.size() < 4096) {
            BlockPos p = queue.poll();
            run.add(p);
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos np = p.relative(d);
                if (!seen.add(np) || !level.isLoaded(np)
                        || !(level.getBlockState(np).getBlock() instanceof tor_tik96.create_em.power.PowerCableBlock)) continue;
                GridMarks.Mark m = marks.at(level, np);
                if (!Objects.equals(owner, m == null ? null : m.owner())) continue;
                UUID placer = marks.placer(np);
                boolean mine = owner == null ? player.getUUID().equals(placer) || Gov.isOperator(player)
                        : GridIsolation.canManage(player, owner, placer);
                if (mine) queue.add(np);
            }
        }
        return run;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        GridMarks marks = GridMarks.get(level);
        GridMarks.Mark mark = marks.at(state, pos);
        UUID placer = marks.placer(pos);

        if (mark != null) {
            if (player.isShiftKeyDown() && GridIsolation.canManage(player, mark.owner(), placer)) {
                for (BlockPos p : cableRun(level, marks, pos, mark.owner(), player)) {
                    marks.unmark(p);
                    GridIsolation.invalidate(level, p);
                }
                if (!player.isCreative()) player.getInventory().placeItemBackInInventory(new ItemStack(this));
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1f, 0.8f);
                player.displayClientMessage(Component.translatable("create_em.mark.removed"), true);
            } else {
                player.displayClientMessage(Component.translatable("create_em.mark.info", mark.name()).withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResult.CONSUME;
        }

        if (state.is(EMRegistry.STATE_CONNECTOR.get())) {
            player.displayClientMessage(Component.translatable("create_em.mark.connector").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (!GridIsolation.hasEnergy(level, pos)) {
            player.displayClientMessage(Component.translatable("create_em.mark.no_energy").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        // чужой энергоблок промаркировать нельзя — иначе можно «присвоить» и сломать чужой аккумулятор
        if (!player.getUUID().equals(placer) && !Gov.isOperator(player)) {
            player.displayClientMessage(Component.translatable("create_em.mark.not_yours").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        // силовой кабель маркируется целиком — весь соединённый пролёт ваших кабелей одной маркировкой
        for (BlockPos p : cableRun(level, marks, pos, null, player)) {
            marks.mark(p, level.getBlockState(p), player.getUUID(), player.getGameProfile().getName());
            GridIsolation.invalidate(level, p);
        }
        if (!player.isCreative()) ctx.getItemInHand().shrink(1);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1f, 0.7f);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, 1.3f);
        player.displayClientMessage(Component.translatable("create_em.mark.done").withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.CONSUME;
    }
}
