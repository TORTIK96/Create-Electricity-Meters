package tor_tik96.create_em.power;

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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import tor_tik96.create_em.registry.EMRegistry;

/**
 * АВР — автоматический ввод резерва.
 * <ul>
 *   <li>лицо — панель с лампами и переключателем режима;</li>
 *   <li>сзади — ввод 1 (основной: счётчик, линия);</li>
 *   <li>снизу — ввод 2 (резерв: аккумулятор, свой генератор);</li>
 *   <li>сверху и по бокам — выход к нагрузке.</li>
 * </ul>
 * Пока основной ввод пропал, блок выдаёт сигнал редстоуна 15 во все стороны — например, чтобы
 * сцеплением запустить резервный двигатель.
 */
public class AtsBlock extends Block implements IBE<AtsBlockEntity>, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<AtsSource> SOURCE = EnumProperty.create("source", AtsSource.class);
    /** Основной ввод пропал. */
    public static final BooleanProperty ALARM = BooleanProperty.create("alarm");

    public AtsBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(SOURCE, AtsSource.NONE).setValue(ALARM, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, SOURCE, ALARM);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ---------- сигнал аварии ----------

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return state.getValue(ALARM) ? 15 : 0;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return dir != null;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<AtsBlockEntity> getBlockEntityClass() {
        return AtsBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AtsBlockEntity> getBlockEntityType() {
        return EMRegistry.ATS_BE.get();
    }
}
