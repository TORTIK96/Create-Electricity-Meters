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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import tor_tik96.create_em.registry.EMRegistry;

/**
 * Конденсатор: копит энергию и отдаёт её разом.
 * <ul>
 *   <li>сзади — зарядка (вход), спереди — разряд (выход);</li>
 *   <li>сверху — вход сигнала «разрядить» (кнопка прямо на крышке);</li>
 *   <li>по бокам и снизу — сигнал редстоуна 0–15 по заряду. Наверх сигнал не идёт,
 *       чтобы не замкнуться на собственный вход.</li>
 * </ul>
 */
public class CapacitorBlock extends Block implements IBE<CapacitorBlockEntity>, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);
    public static final BooleanProperty DISCHARGING = BooleanProperty.create("discharging");
    /** Сила сигнала по заряду — в блокстейте, чтобы соседи обновлялись сами. */
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public CapacitorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(CHARGE, 0)
                .setValue(DISCHARGING, false).setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, CHARGE, DISCHARGING, POWER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // лицо (выход разряда) — от игрока, вход — к игроку
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ---------- редстоун ----------

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** {@code dir} — сторона соседа, смотрящая на нас; наверх (dir == DOWN) не отдаём. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return dir == Direction.DOWN ? 0 : state.getValue(POWER);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return dir != null;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(POWER);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<CapacitorBlockEntity> getBlockEntityClass() {
        return CapacitorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CapacitorBlockEntity> getBlockEntityType() {
        return EMRegistry.CAPACITOR_BE.get();
    }
}
