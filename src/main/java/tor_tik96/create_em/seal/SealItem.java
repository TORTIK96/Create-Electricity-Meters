package tor_tik96.create_em.seal;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import tor_tik96.create_em.meter.EnergyMeterBlockEntity;

import java.util.List;

/**
 * Пломба. ПКМ по неопломбированному счётчику — опломбировать: ты становишься его поставщиком,
 * и твои трансформаторы начинают отдавать в него энергию. Пломба расходуется.
 * Shift-ПКМ пломбой по своему опломбированному счётчику — снять пломбу (она пропадает).
 */
public class SealItem extends Item {
    public SealItem(Properties props) { super(props); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null || !(level.getBlockEntity(ctx.getClickedPos()) instanceof EnergyMeterBlockEntity meter))
            return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (meter.isSealed()) {
            if (player.isShiftKeyDown() && meter.isSupplier(player)) {
                meter.revokeSeal(player.getGameProfile().getName());
                level.playSound(null, ctx.getClickedPos(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1f, 1.2f);
                player.displayClientMessage(Component.translatable("create_em.seal.revoked"), true);
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.translatable("create_em.seal.already",
                    meter.getSupplierName()).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        meter.installSeal(player);
        if (!player.isCreative()) ctx.getItemInHand().shrink(1);
        level.playSound(null, ctx.getClickedPos(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 0.8f);
        player.displayClientMessage(Component.translatable("create_em.seal.installed"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("create_em.seal.hint1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("create_em.seal.hint2").withStyle(ChatFormatting.GRAY));
    }
}
