package tor_tik96.create_em.line;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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

/** Обратный преобразователь: линия сверху (изолятор), FE с боков и снизу. Подключить провод может только хозяин. */
public class LineConverterBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 13, 15),
            Block.box(5.5, 13, 5.5, 10.5, 16, 10.5));

    public LineConverterBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return simpleCodec(LineConverterBlock::new); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LineConverterBlockEntity(pos, state); }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> type) {
        if (level.isClientSide || type != EMRegistry.LINE_CONVERTER_BE.get()) return null;
        return (l, p, st, be) -> ((LineConverterBlockEntity) be).serverTick();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        LineNetwork.onPlaced(level, pos, placer);
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof LineConverterBlockEntity be)
            be.setOwnerName(p.getGameProfile().getName());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) LineNetwork.onRemoved(level, pos);
        super.onRemove(state, level, pos, newState, moved);
    }
}
