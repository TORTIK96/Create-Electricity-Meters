package tor_tik96.create_em.process;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import tor_tik96.create_em.registry.EMRegistry;

/**
 * Сортировщик обработки. FACING — куда уходит готовое (зелёная стрелка).
 * Сырое — вправо по ходу (оранжевая стрелка); MIRRORED — влево (ПКМ). Принимает с остальных сторон и сверху.
 */
public class ProcessSorterBlock extends Block implements IBE<ProcessSorterBlockEntity>, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty MIRRORED = BooleanProperty.create("mirrored");

    public ProcessSorterBlock(Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(MIRRORED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, MIRRORED);
    }

    /** Готовое уходит туда, куда смотрел игрок при установке (по ходу ленты). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    public static Direction doneSide(BlockState s) {
        return s.getValue(FACING);
    }

    public static Direction rawSide(BlockState s) {
        return s.getValue(MIRRORED) ? s.getValue(FACING).getCounterClockWise() : s.getValue(FACING).getClockWise();
    }

    /** ПКМ пустой рукой — сырое на другую сторону. Поворот — ключом, как у Create. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                                   net.minecraft.world.entity.player.Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        BlockState next = state.cycle(MIRRORED);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.4f);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                next.getValue(MIRRORED) ? "create_em.sorter.raw_left" : "create_em.sorter.raw_right"), true);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING))).cycle(MIRRORED);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<ProcessSorterBlockEntity> getBlockEntityClass() {
        return ProcessSorterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ProcessSorterBlockEntity> getBlockEntityType() {
        return EMRegistry.PROCESS_SORTER_BE.get();
    }
}
