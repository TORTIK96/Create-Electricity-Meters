package tor_tik96.create_em.meter;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.EnumMap;
import java.util.Map;

/**
 * Мини-счётчик: проходной ваттметр. Ни к кому не привязан, ничего не считает для оплаты —
 * просто показывает на никси-лампах, сколько FE/t сейчас уходит потребителю.
 * Если смотреть на дисплей: вход слева, выход справа.
 */
public class MiniMeterBlock extends Block implements IBE<MiniMeterBlockEntity>, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // модель нарисована лицом на север: корпус вдоль оси X, дисплей на -Z
        SHAPES.put(Direction.NORTH, box(0, 3, 4, 16, 13, 13));
        SHAPES.put(Direction.SOUTH, box(0, 3, 3, 16, 13, 12));
        SHAPES.put(Direction.EAST, box(3, 3, 0, 12, 13, 16));
        SHAPES.put(Direction.WEST, box(4, 3, 0, 13, 13, 16));
    }

    public MiniMeterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Если смотреть на дисплей, вход — слева. */
    public static Direction input(BlockState state) {
        return state.getValue(FACING).getClockWise();
    }

    /** …а выход — справа. */
    public static Direction output(BlockState state) {
        return state.getValue(FACING).getCounterClockWise();
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<MiniMeterBlockEntity> getBlockEntityClass() {
        return MiniMeterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MiniMeterBlockEntity> getBlockEntityType() {
        return EMRegistry.MINI_METER_BE.get();
    }
}
