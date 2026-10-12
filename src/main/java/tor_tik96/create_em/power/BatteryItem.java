package tor_tik96.create_em.power;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import tor_tik96.create_em.EMFormat;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;

/** Аккумулятор в инвентаре помнит заряд: полоска как у прочности и строчка в подсказке. */
public class BatteryItem extends BlockItem {
    public BatteryItem(Block block, Properties properties) {
        super(block, properties);
    }

    private static int stored(ItemStack stack) {
        return stack.getOrDefault(EMRegistry.STORED_ENERGY.get(), 0);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stored(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(13f * stored(stack) / BatteryBlockEntity.capacity()), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x5BD45B;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tooltip, flag);
        tooltip.add(Component.translatable("create_em.battery.tooltip.charge").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(EMFormat.fe(stored(stack)) + " / " + EMFormat.fe(BatteryBlockEntity.capacity()))
                        .withStyle(ChatFormatting.AQUA)));
    }
}
