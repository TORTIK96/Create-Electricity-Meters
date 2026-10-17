package tor_tik96.create_em.process.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import tor_tik96.create_em.process.Flaps;

/** Рисует 4 сегмента заслонки так же, как Create (FlapStuffs.renderFlaps). */
final class FlapRenderer {
    static final float X_OFFSET = 0.075f / 16f;
    static final float SEGMENT_STEP = -3.05f / 16f;

    /** Угол поворота модели, как AngleHelper.horizontalAngle у Create. */
    static float horizontalAngle(Direction d) {
        if (d.getAxis().isVertical()) return 0;
        float a = d.toYRot();
        return d.getAxis() == Direction.Axis.X ? -a : a;
    }

    static void render(PoseStack ms, MultiBufferSource buffers, BlockState state, BakedModel model,
                       float pivotY, float pivotZ, float angleY, float zOffset, float flapness, int light, int overlay) {
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        var vc = buffers.getBuffer(RenderType.cutout());
        ms.pushPose();
        ms.translate(.5, .5, .5);
        ms.mulPose(Axis.YP.rotationDegrees(angleY));
        ms.translate(-.5, -.5, -.5);
        ms.translate(X_OFFSET, 0, zOffset);
        for (int seg = 0; seg < 4; seg++) {
            ms.pushPose();
            ms.translate(0, pivotY, pivotZ);
            ms.mulPose(Axis.XP.rotationDegrees(Flaps.angle(flapness, seg)));
            ms.translate(0, -pivotY, -pivotZ);
            renderer.renderModel(ms.last(), vc, state, model, 1f, 1f, 1f, light, overlay, ModelData.EMPTY, RenderType.cutout());
            ms.popPose();
            ms.translate(SEGMENT_STEP, 0, 0);
        }
        ms.popPose();
    }

    private FlapRenderer() {}
}
