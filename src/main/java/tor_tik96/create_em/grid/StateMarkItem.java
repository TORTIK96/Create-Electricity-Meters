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

import java.util.UUID;

/**
 * Госмаркировка. ПКМ по своему энергоблоку — изолировать: он будет принимать и отдавать FE
 * только через ваши госконнекторы. Shift-ПКМ — снять (хозяин маркировки, тот, кто поставил блок, или оператор).
 */
public class StateMarkItem extends Item {
    public StateMarkItem(Properties props) {
        super(props);
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
                marks.unmark(pos);
                GridIsolation.invalidate(level, pos);
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

        marks.mark(pos, state, player.getUUID(), player.getGameProfile().getName());
        GridIsolation.invalidate(level, pos);
        if (!player.isCreative()) ctx.getItemInHand().shrink(1);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1f, 0.7f);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, 1.3f);
        player.displayClientMessage(Component.translatable("create_em.mark.done").withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.CONSUME;
    }
}
