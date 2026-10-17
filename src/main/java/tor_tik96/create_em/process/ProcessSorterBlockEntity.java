package tor_tik96.create_em.process;

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;

/**
 * Сортировщик обработки. Лента Create отдаёт предмет вместе с пометкой: каким вентилятором
 * его обрабатывали и закончена ли обработка. По ней и делим — без фильтров по предметам:
 * <ul>
 *   <li>готово (обработан, или этим потоком не обрабатывается) — вперёд;</li>
 *   <li>сырое (в потоке не был или не успел) — вбок, обратно в петлю; недоделанная обработка
 *       продолжится с того же места, пометка переходит на следующую ленту.</li>
 * </ul>
 * Так цепочки вроде «булыжник → камень → гладкий камень» останавливаются на первом шаге.
 */
public class ProcessSorterBlockEntity extends SmartBlockEntity {
    public ProcessSorterBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.PROCESS_SORTER_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this)
                .onlyInsertWhen(this::canAccept)
                .setInsertionHandler(this::sort));
    }

    /** Не принимаем «со стороны выходов», иначе предмет вернётся туда, откуда пришёл. */
    private boolean canAccept(Direction movement) {
        Direction from = movement.getOpposite();
        return from != ProcessSorterBlock.doneSide(getBlockState()) && from != ProcessSorterBlock.rawSide(getBlockState());
    }

    /**
     * Готово: Create пометил, что вентилятор с ним закончил, и это не сырая партия диспетчера
     * (метку «сырое» Create при обработке не переносит, а вот «этим потоком не обрабатывается» выглядит как готово).
     */
    public static boolean isDone(TransportedItemStack t) {
        return t.processedBy != null && t.processingTime == -1
                && !t.stack.has(tor_tik96.create_em.registry.EMRegistry.RAW_BATCH.get());
    }

    private ItemStack sort(TransportedItemStack t, Direction movement, boolean simulate) {
        if (level == null) return t.stack;
        Direction out = isDone(t) ? ProcessSorterBlock.doneSide(getBlockState()) : ProcessSorterBlock.rawSide(getBlockState());
        return pass(t, out, simulate);
    }

    /** Отдать соседу: на ленту/в депо/в бассейн — с пометкой обработки, в инвентарь — просто предмет. */
    private ItemStack pass(TransportedItemStack t, Direction out, boolean simulate) {
        BlockPos target = worldPosition.relative(out);
        DirectBeltInputBehaviour next = BlockEntityBehaviour.get(level, target, DirectBeltInputBehaviour.TYPE);
        if (next != null) {
            if (!next.canInsertFromSide(out)) return t.stack;
            return next.handleInsertion(t.copy(), out, simulate);
        }
        IItemHandler inv = level.getCapability(Capabilities.ItemHandler.BLOCK, target, out.getOpposite());
        if (inv == null) return t.stack;
        return ItemHandlerHelper.insertItemStacked(inv, t.stack.copy(), simulate);
    }
}
