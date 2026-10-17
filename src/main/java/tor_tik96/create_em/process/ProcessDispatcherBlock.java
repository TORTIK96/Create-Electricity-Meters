package tor_tik96.create_em.process;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.registry.EMRegistry;

/**
 * Диспетчер обработки. FACING — куда уходит лента (по взгляду игрока при установке).
 * Сзади лента возвращается, снизу — инвентарь для готового, слева/справа/сверху — бочки.
 */
public class ProcessDispatcherBlock extends Block implements IBE<ProcessDispatcherBlockEntity>, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ProcessDispatcherBlock(Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    /** Привязка к катализатору переходит из предмета в блок. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockPos cat = stack.get(EMRegistry.BOUND_TO.get());
        if (!level.isClientSide && cat != null) withBlockEntityDo(level, pos, be -> be.setCatalyst(cat));
    }

    /** ПКМ по грани с бочкой — сменить её обработку. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ProcessDispatcherBlockEntity be)) return InteractionResult.PASS;
        int slot = be.slotOf(hit.getDirection());
        if (slot < 0) {
            player.displayClientMessage(Component.translatable("create_em.dispatcher.hint").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        CatalystProcess m = be.cycle(slot);
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.2f);
        String[] names = {"create_em.dispatcher.left", "create_em.dispatcher.right", "create_em.dispatcher.top"};
        player.displayClientMessage(Component.translatable(names[slot]).append(Component.translatable(
                m == CatalystProcess.NONE ? "create_em.dispatcher.unused" : m.translationKey()).withStyle(ChatFormatting.AQUA)), true);
        return InteractionResult.CONSUME;
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
    public Class<ProcessDispatcherBlockEntity> getBlockEntityClass() {
        return ProcessDispatcherBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ProcessDispatcherBlockEntity> getBlockEntityType() {
        return EMRegistry.PROCESS_DISPATCHER_BE.get();
    }
}
