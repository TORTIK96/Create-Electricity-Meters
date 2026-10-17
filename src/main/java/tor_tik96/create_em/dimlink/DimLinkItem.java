package tor_tik96.create_em.dimlink;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import tor_tik96.create_em.grid.GridIsolation;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;

/** ПКМ по своему поставленному узлу — запомнить его; поставленный потом узел свяжется с ним. Shift+ПКМ в воздух — сбросить. */
public class DimLinkItem extends BlockItem {
    public DimLinkItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof DimLinkBlockEntity be) {
            Player p = ctx.getPlayer();
            if (!ctx.getLevel().isClientSide && p != null) {
                if (!GridIsolation.canManage(p, be.getOwner(), null)) {
                    p.displayClientMessage(Component.translatable("create_em.dim_link.not_yours", be.getOwnerName())
                            .withStyle(ChatFormatting.RED), true);
                } else {
                    ctx.getItemInHand().set(EMRegistry.LINK_TARGET.get(), GlobalPos.of(ctx.getLevel().dimension(), ctx.getClickedPos().immutable()));
                    p.displayClientMessage(Component.translatable("create_em.dim_link.bound").withStyle(ChatFormatting.AQUA), true);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.useOn(ctx);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.has(EMRegistry.LINK_TARGET.get())) {
            if (!level.isClientSide) {
                stack.remove(EMRegistry.LINK_TARGET.get());
                player.displayClientMessage(Component.translatable("create_em.dim_link.unbound").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(EMRegistry.LINK_TARGET.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos p = stack.get(EMRegistry.LINK_TARGET.get());
        tooltip.add((p == null ? Component.translatable("create_em.dim_link.item_free")
                : Component.translatable("create_em.dim_link.item_bound", p.dimension().location().toString(), p.pos().toShortString()))
                .withStyle(ChatFormatting.GRAY));
    }
}
