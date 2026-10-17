package tor_tik96.create_em.chunk;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import tor_tik96.create_em.registry.EMRegistry;

/** Загрузчик чанков: FE с любой стороны, радиус — колёсиком на боковой грани. */
public class ChunkLoaderBlock extends Block implements IBE<ChunkLoaderBlockEntity>, IWrenchable {
    public ChunkLoaderBlock(Properties props) {
        super(props);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ChunkLoaderBlockEntity be) be.onRemoved();
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<ChunkLoaderBlockEntity> getBlockEntityClass() {
        return ChunkLoaderBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ChunkLoaderBlockEntity> getBlockEntityType() {
        return EMRegistry.CHUNK_LOADER_BE.get();
    }
}
