package tor_tik96.create_em.billing;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Расчётный терминал поставщика. Поставщик (кто поставил) задаёт тариф и забирает выручку,
 * абоненты смотрят свой лицевой счёт и платят валютой поставщика.
 */
public class TerminalBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<TerminalBlock> CODEC = simpleCodec(TerminalBlock::new);

    public TerminalBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TerminalBlockEntity(pos, state); }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof TerminalBlockEntity be)
            be.setOwner(p.getUUID(), p.getGameProfile().getName());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TerminalBlockEntity be)) return InteractionResult.PASS;
        if (!level.isClientSide)
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TerminalMenu(id, inv, be),
                    Component.translatable(getDescriptionId())));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TerminalBlockEntity be)
            Containers.dropContents(level, pos, be.getContainer());
        super.onRemove(state, level, pos, newState, moved);
    }
}
