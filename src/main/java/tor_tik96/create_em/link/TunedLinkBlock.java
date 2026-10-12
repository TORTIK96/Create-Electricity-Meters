package tor_tik96.create_em.link;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.EnumMap;
import java.util.Map;

/**
 * Настраиваемый передатчик: работает в сети редстоун-связи Create, но передаёт
 * не входной сигнал, а выставленную силу. Ставится на грань блока, как связь Create.
 * Гаечный ключ (или Shift-ПКМ пустой рукой) переключает режим «обычный / селектор».
 */
public class TunedLinkBlock extends Block implements IBE<TunedLinkBlockEntity>, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** Сейчас передаёт ненулевой сигнал (лампа на панели). */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty SELECTOR = BooleanProperty.create("selector");

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.UP, box(1, 0, 1, 15, 3, 15));
        SHAPES.put(Direction.DOWN, box(1, 13, 1, 15, 16, 15));
        SHAPES.put(Direction.SOUTH, box(1, 1, 0, 15, 15, 3));
        SHAPES.put(Direction.NORTH, box(1, 1, 13, 15, 15, 16));
        SHAPES.put(Direction.EAST, box(0, 1, 1, 3, 15, 15));
        SHAPES.put(Direction.WEST, box(13, 1, 1, 16, 15, 15));
    }

    public TunedLinkBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP)
                .setValue(POWERED, false).setValue(SELECTOR, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, POWERED, SELECTOR);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = defaultBlockState().setValue(FACING, ctx.getClickedFace());
        return state.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return !level.getBlockState(support).getCollisionShape(level, support).isEmpty();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (dir == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos))
            return Blocks.AIR.defaultBlockState();
        return state;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ---------- режим ----------

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx) {
        toggleMode(state, ctx.getLevel(), ctx.getClickedPos(), ctx.getPlayer());
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        toggleMode(state, level, pos, player);
        return InteractionResult.SUCCESS;
    }

    private void toggleMode(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide) return;
        boolean selector = !state.getValue(SELECTOR);
        level.setBlock(pos, state.setValue(SELECTOR, selector), Block.UPDATE_ALL);
        withBlockEntityDo(level, pos, be -> be.onModeChanged(selector));
        level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.4f, selector ? 0.7f : 0.55f);
        if (player != null)
            player.displayClientMessage(Component.translatable(selector
                    ? "create_em.tuned_link.mode.selector" : "create_em.tuned_link.mode.normal"), true);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<TunedLinkBlockEntity> getBlockEntityClass() {
        return TunedLinkBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TunedLinkBlockEntity> getBlockEntityType() {
        return EMRegistry.TUNED_LINK_BE.get();
    }
}
