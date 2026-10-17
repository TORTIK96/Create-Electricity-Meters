package tor_tik96.create_em.dimlink;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.grid.GridIsolation;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.UUID;

/**
 * Межпространственный узел. OUT = «отдача» (раздаёт соседям), иначе «приём» (берёт с кабелей и шлёт паре).
 * Узел, поставленный привязанным предметом, сразу связывается с тем узлом и встаёт на «отдачу».
 */
public class DimLinkBlock extends Block implements IBE<DimLinkBlockEntity>, IWrenchable {
    public static final BooleanProperty OUT = BooleanProperty.create("out");

    public DimLinkBlock(Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(OUT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(OUT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(OUT, ctx.getItemInHand().has(EMRegistry.LINK_TARGET.get()));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel sl) || !(placer instanceof Player p)) return;
        withBlockEntityDo(level, pos, be -> be.setOwner(p.getUUID(), p.getGameProfile().getName()));
        DimLinks reg = DimLinks.get(sl.getServer());
        GlobalPos self = GlobalPos.of(level.dimension(), pos);
        reg.register(self, p.getUUID());

        GlobalPos target = stack.get(EMRegistry.LINK_TARGET.get());
        if (target == null) return;
        UUID targetOwner = reg.owner(target);
        if (targetOwner == null || target.equals(self)) {
            p.displayClientMessage(Component.translatable("create_em.dim_link.target_gone").withStyle(ChatFormatting.RED), true);
        } else if (!GridIsolation.canManage(p, targetOwner, null)) {
            p.displayClientMessage(Component.translatable("create_em.dim_link.target_foreign").withStyle(ChatFormatting.RED), true);
        } else {
            reg.pair(self, target);
            p.displayClientMessage(Component.translatable("create_em.dim_link.paired",
                    target.dimension().location().toString(), target.pos().toShortString()).withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** ПКМ пустой рукой — сменить режим (только хозяин). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof DimLinkBlockEntity be)) return InteractionResult.PASS;
        if (!GridIsolation.canManage(player, be.getOwner(), null)) {
            player.displayClientMessage(Component.translatable("create_em.dim_link.not_yours", be.getOwnerName())
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        boolean out = !state.getValue(OUT);
        level.setBlock(pos, state.setValue(OUT, out), Block.UPDATE_ALL);
        be.onModeChanged();
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, out ? 0.7f : 0.9f);
        player.displayClientMessage(Component.translatable(out ? "create_em.dim_link.mode_out" : "create_em.dim_link.mode_in")
                .withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.CONSUME;
    }

    /** Крутить ключом нечего; снимать (Shift + ключ) может только хозяин. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx) {
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl)
            DimLinks.get(sl.getServer()).remove(GlobalPos.of(level.dimension(), pos));
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<DimLinkBlockEntity> getBlockEntityClass() {
        return DimLinkBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DimLinkBlockEntity> getBlockEntityType() {
        return EMRegistry.DIM_LINK_BE.get();
    }
}
