package tor_tik96.create_em.line;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** Коннектор (изолятор) сервисной линии: ставится на любую грань блока, держит до 8 проводов. */
public class LineConnectorBlock extends DirectionalBlock implements EntityBlock {
    public static final MapCodec<LineConnectorBlock> CODEC = simpleCodec(LineConnectorBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // фасад вверх: основание 6x6, изолятор до 8 px
        for (Direction d : Direction.values()) SHAPES.put(d, rotate(d, 5, 0, 5, 11, 8, 11));
    }

    public LineConnectorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPES.get(s.getValue(FACING)); }

    /** Держится, только пока есть опора. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos))
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LineConnectorBlockEntity(pos, state); }

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

    /** Бокс задан для фасада вверх; поворачиваем под нужную грань. */
    private static VoxelShape rotate(Direction d, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (d) {
            case UP -> Block.box(x1, y1, z1, x2, y2, z2);
            case DOWN -> Block.box(x1, 16 - y2, z1, x2, 16 - y1, z2);
            case NORTH -> Block.box(x1, z1, 16 - y2, x2, z2, 16 - y1);
            case SOUTH -> Block.box(x1, z1, y1, x2, z2, y2);
            case WEST -> Block.box(16 - y2, z1, x1, 16 - y1, z2, x2);
            case EAST -> Block.box(y1, z1, x1, y2, z2, x2);
        };
    }
}
