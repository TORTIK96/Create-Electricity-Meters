package tor_tik96.create_em.gov;

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
 * Госпломба: ставит на счётчик пломбу поставщика «Госсеть». Рецепта нет — выдают операторы.
 * Снять госпломбу (Shift-ПКМ) может только оператор.
 */
public class StateSealItem extends Item {
    public StateSealItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null || !(level.getBlockEntity(ctx.getClickedPos()) instanceof EnergyMeterBlockEntity meter))
            return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (meter.isSealed()) {
            if (player.isShiftKeyDown() && Gov.SUPPLIER.equals(meter.getSealSupplier()) && Gov.isOperator(player)) {
                meter.revokeSeal(player.getGameProfile().getName());
                level.playSound(null, ctx.getClickedPos(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1f, 1.2f);
                player.displayClientMessage(Component.translatable("create_em.seal.revoked"), true);
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.translatable("create_em.seal.already", meter.getSupplierName()).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        meter.installSeal(Gov.SUPPLIER, Gov.NAME);
        if (!player.isCreative()) ctx.getItemInHand().shrink(1);
        level.playSound(null, ctx.getClickedPos(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 0.6f);
        player.displayClientMessage(Component.translatable("create_em.gov.sealed").withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("create_em.gov.seal_hint").withStyle(ChatFormatting.GRAY));
    }
}
