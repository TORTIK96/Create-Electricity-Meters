package tor_tik96.create_em.power;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Силовой кабель: толстый провод-блок, соединяется с соседними кабелями и любыми FE-блоками.
 * Энергию не хранит — что в него толкнули, сразу раздаёт концам сети. Поток не ограничен
 * (по умолчанию), поэтому выдерживает разряд конденсатора.
 */
public class PowerCableBlock extends Block implements IBE<PowerCableBlockEntity>, IWrenchable {
    public static final Map<Direction, BooleanProperty> PROPS = new EnumMap<>(Direction.class);

    static {
        PROPS.put(Direction.NORTH, BlockStateProperties.NORTH);
        PROPS.put(Direction.SOUTH, BlockStateProperties.SOUTH);
        PROPS.put(Direction.EAST, BlockStateProperties.EAST);
        PROPS.put(Direction.WEST, BlockStateProperties.WEST);
        PROPS.put(Direction.UP, BlockStateProperties.UP);
        PROPS.put(Direction.DOWN, BlockStateProperties.DOWN);
    }

    private static final VoxelShape CORE = box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);

    static {
        ARMS.put(Direction.NORTH, box(5, 5, 0, 11, 11, 5));
        ARMS.put(Direction.SOUTH, box(5, 5, 11, 11, 11, 16));
        ARMS.put(Direction.WEST, box(0, 5, 5, 5, 11, 11));
        ARMS.put(Direction.EAST, box(11, 5, 5, 16, 11, 11));
        ARMS.put(Direction.DOWN, box(5, 0, 5, 11, 5, 11));
        ARMS.put(Direction.UP, box(5, 11, 5, 11, 16, 11));
    }

    private final Map<BlockState, VoxelShape> shapeCache = new HashMap<>();

    public PowerCableBlock(Properties properties) {
        super(properties);
        BlockState s = defaultBlockState();
        for (BooleanProperty p : PROPS.values()) s = s.setValue(p, false);
        registerDefaultState(s);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        PROPS.values().forEach(b::add);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapeCache.computeIfAbsent(state, s -> {
            VoxelShape shape = CORE;
            for (Direction d : Direction.values())
                if (s.getValue(PROPS.get(d))) shape = Shapes.or(shape, ARMS.get(d));
            return shape;
        });
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return connections(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        PowerCableBlockEntity.invalidateNetworks();
        return state.setValue(PROPS.get(dir), connects(level, pos, dir));
    }

    private static BlockState connections(BlockState state, LevelAccessor level, BlockPos pos) {
        for (Direction d : Direction.values()) state = state.setValue(PROPS.get(d), connects(level, pos, d));
        return state;
    }

    /** Соседний кабель или любой блок с FE на этой грани. */
    static boolean connects(LevelAccessor level, BlockPos pos, Direction dir) {
        BlockPos np = pos.relative(dir);
        if (level.getBlockState(np).getBlock() instanceof PowerCableBlock) return true;
        if (level instanceof Level l)
            return l.getCapability(Capabilities.EnergyStorage.BLOCK, np, dir.getOpposite()) != null;
        return false;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        PowerCableBlockEntity.invalidateNetworks();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        PowerCableBlockEntity.invalidateNetworks();
        IBE.onRemove(state, level, pos, newState);
    }

    /** Сосед мог получить или потерять FE без смены блока (например, сменил режим) — перепроверяем. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        BlockState updated = connections(state, level, pos);
        if (updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        PowerCableBlockEntity.invalidateNetworks();
    }

    @Override
    public Class<PowerCableBlockEntity> getBlockEntityClass() {
        return PowerCableBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PowerCableBlockEntity> getBlockEntityType() {
        return EMRegistry.POWER_CABLE_BE.get();
    }
}
