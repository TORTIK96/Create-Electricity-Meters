package tor_tik96.create_em.link;

import com.simibubi.create.content.redstone.link.RedstoneLinkFrequencySlot;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Окошко силы сигнала на корпусе. Частота — стандартные слоты Create
 * ({@link RedstoneLinkFrequencySlot}), потому что модель — модель связи Create.
 * Окошко стоит на свободной стороне корпуса, напротив антенны:
 * <ul>
 *   <li>на стене — справа от слотов частоты (x = 11.5);</li>
 *   <li>на полу/потолке — по другую сторону от антенны (x = 4).</li>
 * </ul>
 * Поворот наследуем у слота Create, поэтому цифры и рамка смотрят так же, как у частоты.
 */
public class TunedLinkSlots {
    /** Высота поверхности окошка над корпусом связи (корпус — 3 px, окошко — ещё 0.25). */
    static final float SURFACE = 3.27f;

    public static class Strength extends RedstoneLinkFrequencySlot {
        public Strength() {
            super(true);
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            Direction facing = state.getValue(TunedLinkBlock.FACING);
            if (facing.getAxis().isHorizontal())
                return rotateHorizontally(state, VecHelper.voxelSpace(11.5f, 8f, SURFACE));
            Vec3 loc = VecHelper.voxelSpace(4f, SURFACE, 8f);
            return VecHelper.rotateCentered(loc, facing == Direction.DOWN ? 180 : 0, Axis.X);
        }

        @Override
        public float getScale() {
            return .35f;
        }
    }

    private TunedLinkSlots() {}
}
