package tor_tik96.create_em.grid;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
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
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.registry.EMRegistry;

/**
 * Госконнектор: единственный путь энергии в промаркированное устройство и из него.
 * Лицо (с синей пластиной) прикладывается к устройству, сзади — обычная клемма FE.
 * ПКМ хозяином — сменить направление: «в устройство» / «из устройства».
 */
public class StateConnectorBlock extends Block implements IBE<StateConnectorBlockEntity>, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** true — энергия идёт из устройства наружу. */
    public static final BooleanProperty OUT = BooleanProperty.create("out");

    /** Маленький: пластина на грани устройства и изолятор с клеммой (как коннекторы New Age). */
    private static final java.util.Map<Direction, net.minecraft.world.phys.shapes.VoxelShape> SHAPES = new java.util.EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.values())
            SHAPES.put(d, net.minecraft.world.phys.shapes.Shapes.or(
                    box(d, 3, 3, 0, 13, 13, 1.5),     // пластина
                    box(d, 6, 6, 1.5, 10, 10, 10)));  // изолятор с клеммой
    }

    /** Бокс задан для коннектора, прижатого к устройству на севере; поворачиваем. */
    private static net.minecraft.world.phys.shapes.VoxelShape box(Direction d, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (d) {
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            case UP -> Block.box(x1, 16 - z2, y1, x2, 16 - z1, y2);
            case DOWN -> Block.box(x1, z1, 16 - y2, x2, z2, 16 - y1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                                                  BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    public StateConnectorBlock(Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(OUT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, OUT);
    }

    /** Лицом к блоку, по которому кликнули. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player p)
            withBlockEntityDo(level, pos, be -> be.setOwner(p.getUUID(), p.getGameProfile().getName()));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof StateConnectorBlockEntity be)) return InteractionResult.PASS;
        if (!GridIsolation.canManage(player, be.getOwner(), null)) {
            player.displayClientMessage(Component.translatable("create_em.connector.not_yours", be.getOwnerName())
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        boolean out = !state.getValue(OUT);
        level.setBlock(pos, state.setValue(OUT, out), Block.UPDATE_ALL);
        be.onModeChanged();
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, out ? 0.7f : 0.9f);
        player.displayClientMessage(Component.translatable(out ? "create_em.connector.mode_out" : "create_em.connector.mode_in")
                .withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.CONSUME;
    }

    /** Поворачивать ключом может только хозяин. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p != null && ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof StateConnectorBlockEntity be
                && !GridIsolation.canManage(p, be.getOwner(), null))
            return InteractionResult.FAIL;
        return IWrenchable.super.onWrenched(state, ctx);
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
        if (!state.is(newState.getBlock())) tor_tik96.create_em.line.LineNetwork.onRemoved(level, pos); // госпровода — на землю
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<StateConnectorBlockEntity> getBlockEntityClass() {
        return StateConnectorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StateConnectorBlockEntity> getBlockEntityType() {
        return EMRegistry.STATE_CONNECTOR_BE.get();
    }
}
