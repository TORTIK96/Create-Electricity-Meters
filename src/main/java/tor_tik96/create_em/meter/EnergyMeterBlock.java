package tor_tik96.create_em.meter;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.line.LineNetwork;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.EnumMap;
import java.util.Map;

/**
 * Счётчик электроэнергии. Вход — провод сервисной линии к клемме сверху,
 * выходы FE — сзади (против циферблата) и снизу. Циферблат смотрит на игрока, который его поставил.
 */
public class EnergyMeterBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private final MeterTier tier;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public EnergyMeterBlock(MeterTier tier, Properties props) {
        super(props);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        for (Direction d : Direction.Plane.HORIZONTAL) shapes.put(d, buildShape(tier, d));
    }

    public MeterTier getTier() { return tier; }

    // Кодек нужен только для датагена/регистров; уровень берём тот же.
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new EnergyMeterBlock(tier, p));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return shapes.get(s.getValue(FACING));
    }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EnergyMeterBlockEntity(pos, state); }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> type) {
        if (type != EMRegistry.METER_BE.get()) return null;
        return level.isClientSide
                ? (l, p, st, be) -> ((EnergyMeterBlockEntity) be).clientTick()
                : (l, p, st, be) -> ((EnergyMeterBlockEntity) be).serverTick();
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

    /** Временно, пока нет расчётной системы: в креативе Shift-ПКМ пустой рукой отмечает оплату. */
    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isCreative() || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EnergyMeterBlockEntity be) {
            be.markPaid();
            player.displayClientMessage(Component.translatable("create_em.meter.marked_paid"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---------- форма ----------
    private static VoxelShape buildShape(MeterTier tier, Direction d) {
        VoxelShape body = Shapes.or(
                box(d, 1, 1, 2, 15, 15, 14),                       // корпус
                box(d, 1.5, 0, 2.5, 14.5, 1, 13.5));               // ножки
        return switch (tier) {
            case ANDESITE -> Shapes.or(body,
                    box(d, 3, 5, 0.5, 13, 13, 2),                  // рамка окна со стрелкой
                    box(d, 4.5, 2, 0.75, 11.5, 4.5, 2));           // счётный механизм
            case BRASS -> Shapes.or(body,
                    box(d, 2.5, 7.5, 0.75, 13.5, 13.5, 2),         // дисплей с никси-лампами
                    box(d, 3.5, 2.25, 0.75, 13.5, 6.25, 2),        // нижняя панель
                    box(d, 0.5, 4, 4, 15.5, 11, 12));              // рёбра охлаждения
        };
    }

    /** Бокс задан для фасада на север; поворачиваем вокруг центра блока. */
    private static VoxelShape box(Direction d, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (d) {
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }
}
