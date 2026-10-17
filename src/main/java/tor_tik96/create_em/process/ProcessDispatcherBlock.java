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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.registry.EMRegistry;

/**
 * Диспетчер обработки. Граней «под что-то» нет: выход — туда, откуда уходит лента,
 * вход — любая лента, которая в него упирается. Бочки подключаются шлюзами.
 */
public class ProcessDispatcherBlock extends Block implements IBE<ProcessDispatcherBlockEntity>, IWrenchable {
    public ProcessDispatcherBlock(Properties props) {
        super(props);
    }

    /** Привязка к катализатору переходит из предмета в блок. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BlockPos cat = stack.get(EMRegistry.BOUND_TO.get());
        if (!level.isClientSide && cat != null) withBlockEntityDo(level, pos, be -> be.setCatalyst(cat));
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        var be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
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
