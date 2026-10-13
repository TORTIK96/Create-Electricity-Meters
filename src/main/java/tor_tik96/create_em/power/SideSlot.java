package tor_tik96.create_em.power;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiPredicate;

/** Рамка настройки Create на грани блока, но не обязательно по центру: можно поставить две на одну грань. */
public class SideSlot extends ValueBoxTransform.Sided {
    private final float x, y;
    private final BiPredicate<BlockState, Direction> sides;

    /** x, y — в пикселях на грани (как на южной грани блока). */
    public SideSlot(float x, float y, BiPredicate<BlockState, Direction> sides) {
        this.x = x;
        this.y = y;
        this.sides = sides;
    }

    @Override
    protected Vec3 getSouthLocation() {
        return VecHelper.voxelSpace(x, y, 15.5f);
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        return sides.test(state, direction);
    }
}
