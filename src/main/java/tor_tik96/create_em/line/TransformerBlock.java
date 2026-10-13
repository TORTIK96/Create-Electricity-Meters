package tor_tik96.create_em.line;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.registry.EMRegistry;

/**
 * Трансформатор: принимает обычную FE с боков и снизу и отдаёт её в сервисную линию.
 * Провод линии крепится к изолятору сверху.
 */
public class TransformerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** Умный (латунный) трансформатор — ещё и панель поставщика. */
    private final boolean smart;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 13, 15),     // корпус
            Block.box(5.5, 13, 5.5, 10.5, 16, 10.5)); // изолятор

    public TransformerBlock(Properties props, boolean smart) {
        super(props);
        this.smart = smart;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public boolean isSmart() { return smart; }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return simpleCodec(p -> new TransformerBlock(p, smart)); }

    /** ПКМ по умному трансформатору — панель поставщика (только владельцу). */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos,
                                                                   net.minecraft.world.entity.player.Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (!smart || !(level.getBlockEntity(pos) instanceof TransformerBlockEntity be)) return net.minecraft.world.InteractionResult.PASS;
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        if (be.getOwner() == null || !be.getOwner().equals(player.getUUID())) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("create_em.panel.not_owner")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> tor_tik96.create_em.panel.SupplierPanelMenu.server(id, inv, be),
                net.minecraft.network.chat.Component.translatable("create_em.panel.title")));
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TransformerBlockEntity(pos, state); }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> type) {
        if (type != EMRegistry.TRANSFORMER_BE.get()) return null;
        return level.isClientSide
                ? (l, p, st, be) -> ((TransformerBlockEntity) be).clientTick()
                : (l, p, st, be) -> ((TransformerBlockEntity) be).serverTick();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        LineNetwork.onPlaced(level, pos, placer);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) LineNetwork.onRemoved(level, pos);
        super.onRemove(state, level, pos, newState, moved);
    }
}
