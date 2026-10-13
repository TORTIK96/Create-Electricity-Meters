package tor_tik96.create_em.power;

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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
import tor_tik96.create_em.registry.EMSounds;

import java.util.EnumMap;
import java.util.Map;

/**
 * Рубильник: разрывает цепь FE. Если смотреть на рукоять — вход слева, выход справа (как у мини-счётчика).
 * ПКМ — включить/выключить. Выключенный не пропускает ничего.
 */
public class KnifeSwitchBlock extends Block implements IBE<KnifeSwitchBlockEntity>, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ON = BooleanProperty.create("on");

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // как мини-счётчик: брусок вдоль оси вход—выход, рукоять на лицевой стороне
        SHAPES.put(Direction.NORTH, box(0, 3, 2, 16, 13, 13));
        SHAPES.put(Direction.SOUTH, box(0, 3, 3, 16, 13, 14));
        SHAPES.put(Direction.EAST, box(3, 3, 0, 14, 13, 16));
        SHAPES.put(Direction.WEST, box(2, 3, 0, 13, 13, 16));
    }

    public KnifeSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ON, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ON);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    public static Direction input(BlockState state) {
        return state.getValue(FACING).getClockWise();
    }

    public static Direction output(BlockState state) {
        return state.getValue(FACING).getCounterClockWise();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        boolean on = !state.getValue(ON);
        level.setBlock(pos, state.setValue(ON, on), Block.UPDATE_ALL);
        withBlockEntityDo(level, pos, be -> be.onToggled(on));
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, on ? 0.6f : 0.5f);
        level.playSound(null, pos, EMSounds.ATS_SWITCH.get(), SoundSource.BLOCKS, 0.7f, on ? 1.05f : 0.9f);
        player.displayClientMessage(Component.translatable(on ? "create_em.knife_switch.on" : "create_em.knife_switch.off"), true);
        return InteractionResult.SUCCESS;
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
    public Class<KnifeSwitchBlockEntity> getBlockEntityClass() {
        return KnifeSwitchBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends KnifeSwitchBlockEntity> getBlockEntityType() {
        return EMRegistry.KNIFE_SWITCH_BE.get();
    }
}
