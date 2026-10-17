package tor_tik96.create_em.process;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;

/** ПКМ по диспетчеру — запомнить его; потом ставить шлюзы на сундуки (привязка остаётся на всю стопку). */
public class ProcessGateItem extends BlockItem {
    public ProcessGateItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof ProcessDispatcherBlockEntity) {
            if (!ctx.getLevel().isClientSide && ctx.getPlayer() != null) {
                ctx.getItemInHand().set(EMRegistry.BOUND_TO.get(), ctx.getClickedPos().immutable());
                ctx.getPlayer().displayClientMessage(Component.translatable("create_em.gate.bound").withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResult.SUCCESS;
        }
        return super.useOn(ctx);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(EMRegistry.BOUND_TO.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        BlockPos p = stack.get(EMRegistry.BOUND_TO.get());
        tooltip.add((p == null ? Component.translatable("create_em.gate.item_unbound")
                : Component.translatable("create_em.gate.item_bound", p.getX(), p.getY(), p.getZ())).withStyle(ChatFormatting.GRAY));
    }
}
