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
 * Слоты на лицевой панели передатчика. Повторяют раскладку редстоун-связи Create
 * (та же глубина и поворот), но сдвинуты: частота — левая колонка, сила сигнала — справа.
 */
public final class TunedLinkSlots {
    /** Центр колонки частоты и окошка силы по горизонтали, в пикселях. */
    static final float FREQ_X = 5.5f, STRENGTH_X = 11f;

    /** Положение точки на лицевой панели: x, y — по панели (как при FACING=SOUTH), в пикселях. */
    static Vec3 onFace(BlockState state, float x, float y, RotateHorizontally rot) {
        Direction facing = state.getValue(TunedLinkBlock.FACING);
        if (facing.getAxis().isHorizontal())
            return rot.apply(state, VecHelper.voxelSpace(x, y, 3.01f));
        // на полу/потолке ось «вверх по панели» уходит в z, как у Create
        Vec3 loc = VecHelper.voxelSpace(x, 3.01f, y);
        return VecHelper.rotateCentered(loc, facing == Direction.DOWN ? 180 : 0, Axis.X);
    }

    interface RotateHorizontally {
        Vec3 apply(BlockState state, Vec3 v);
    }

    /** Частота: два слота друг над другом. */
    public static class Frequency extends RedstoneLinkFrequencySlot {
        public Frequency(boolean first) {
            super(first);
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return onFace(state, FREQ_X, first ? 10.5f : 5.5f, this::rotateHorizontally);
        }
    }

    /** Сила сигнала: одно окошко справа по центру. */
    public static class Strength extends RedstoneLinkFrequencySlot {
        public Strength() {
            super(true);
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return onFace(state, STRENGTH_X, 8f, this::rotateHorizontally);
        }
    }

    private TunedLinkSlots() {}
}
