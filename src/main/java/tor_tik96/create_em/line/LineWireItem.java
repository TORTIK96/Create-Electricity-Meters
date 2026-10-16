package tor_tik96.create_em.line;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;
import java.util.UUID;

/**
 * Провод сервисной линии. ПКМ по первому узлу — запомнить, ПКМ по второму — соединить.
 * Shift-ПКМ в воздух — сбросить. Правила:
 * <ul>
 *   <li>трансформатор и коннектор можно подключать, только если они твои;</li>
 *   <li>счётчик подключается к любой твоей линии, но только одним проводом;</li>
 *   <li>два счётчика друг с другом не соединяются;</li>
 *   <li>длина провода ограничена конфигом.</li>
 * </ul>
 */
public class LineWireItem extends Item {
    /** true — госпровод: соединяет только госконнекторы; false — провод сервисной линии. */
    private final boolean state;

    public LineWireItem(Properties props) { this(props, false); }

    public LineWireItem(Properties props, boolean state) {
        super(props);
        this.state = state;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        BlockPos clicked = ctx.getClickedPos();
        if (player == null || !(level.getBlockEntity(clicked) instanceof LineNode node)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ItemStack stack = ctx.getItemInHand();
        if (node.isStateGrid() != state)
            return fail(player, stack, state ? "create_em.wire.state_only" : "create_em.wire.no_state", false);
        BlockPos bound = stack.get(EMRegistry.BOUND_TO.get());

        String problem = checkNode(node, player);
        if (problem != null) return fail(player, stack, problem, false);

        if (bound == null) {
            stack.set(EMRegistry.BOUND_TO.get(), clicked.immutable());
            player.playSound(SoundEvents.LEASH_KNOT_PLACE, 1f, 1f);
            player.displayClientMessage(Component.translatable("create_em.wire.bound"), true);
            return InteractionResult.CONSUME;
        }

        if (bound.equals(clicked)) return fail(player, stack, "create_em.wire.self", true);
        int maxLen = Config.MAX_WIRE_LENGTH.get();
        if (bound.distSqr(clicked) > (double) maxLen * maxLen) return fail(player, stack, "create_em.wire.too_far", false, maxLen);

        BlockEntity boundBe = level.getBlockEntity(bound);
        if (!(boundBe instanceof LineNode first)) return fail(player, stack, "create_em.wire.lost", true);
        if (first.getLinks().contains(clicked)) return fail(player, stack, "create_em.wire.already", true);
        if (first.isTerminal() && node.isTerminal()) return fail(player, stack, "create_em.wire.two_meters", true);
        String firstProblem = checkNode(first, player);
        if (firstProblem != null) return fail(player, stack, firstProblem, true);

        LineNetwork.link(first, bound, node, clicked);
        stack.remove(EMRegistry.BOUND_TO.get());
        if (!player.isCreative()) stack.shrink(1);
        player.playSound(SoundEvents.LEASH_KNOT_PLACE, 1f, 1.2f);
        player.displayClientMessage(Component.translatable("create_em.wire.connected"), true);
        return InteractionResult.CONSUME;
    }

    /** null — к узлу можно подключиться, иначе ключ сообщения. */
    private static String checkNode(LineNode node, Player player) {
        if (node.getLinks().size() >= node.getMaxLinks()) return "create_em.wire.full";
        UUID owner = node.getOwner();
        if (!node.isTerminal() && owner != null && !owner.equals(player.getUUID()) && !player.isCreative()
                && !(tor_tik96.create_em.gov.Gov.SUPPLIER.equals(owner) && tor_tik96.create_em.gov.Gov.isOperator(player)))
            return "create_em.wire.not_owner";
        return null;
    }

    private static InteractionResult fail(Player player, ItemStack stack, String key, boolean unbind, Object... args) {
        if (unbind) stack.remove(EMRegistry.BOUND_TO.get());
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.has(EMRegistry.BOUND_TO.get())) {
            stack.remove(EMRegistry.BOUND_TO.get());
            player.playSound(SoundEvents.LEASH_KNOT_BREAK, 1f, 1f);
            player.displayClientMessage(Component.translatable("create_em.wire.unbound"), true);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    /** Отошёл слишком далеко с привязанным проводом — привязка сбрасывается. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !selected) return;
        BlockPos bound = stack.get(EMRegistry.BOUND_TO.get());
        if (bound == null) return;
        int maxLen = Config.MAX_WIRE_LENGTH.get();
        if (entity.distanceToSqr(bound.getCenter()) > (double) maxLen * maxLen * 2) {
            stack.remove(EMRegistry.BOUND_TO.get());
            if (entity instanceof Player p)
                p.displayClientMessage(Component.translatable("create_em.wire.too_far", maxLen).withStyle(ChatFormatting.RED), true);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) { return stack.has(EMRegistry.BOUND_TO.get()); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("create_em.wire.hint").withStyle(ChatFormatting.GRAY));
        BlockPos bound = stack.get(EMRegistry.BOUND_TO.get());
        if (bound != null)
            tooltip.add(Component.translatable("create_em.wire.bound_to", bound.getX(), bound.getY(), bound.getZ()).withStyle(ChatFormatting.AQUA));
    }
}
