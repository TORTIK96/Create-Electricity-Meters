package tor_tik96.create_em.power;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import tor_tik96.create_em.registry.EMRegistry;

/** Электризатор: принимает FE с любой стороны и тут же разряжает её в воздух вокруг. */
public class ElectrifierBlock extends Block implements IBE<ElectrifierBlockEntity>, IWrenchable {
    /** Вспышка разряда (несколько тиков). */
    public static final BooleanProperty ZAP = BooleanProperty.create("zap");

    public ElectrifierBlock(Properties properties) {
        super(properties.lightLevel(s -> s.getValue(ZAP) ? 9 : 0));
        registerDefaultState(defaultBlockState().setValue(ZAP, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(ZAP);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<ElectrifierBlockEntity> getBlockEntityClass() {
        return ElectrifierBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ElectrifierBlockEntity> getBlockEntityType() {
        return EMRegistry.ELECTRIFIER_BE.get();
    }
}
