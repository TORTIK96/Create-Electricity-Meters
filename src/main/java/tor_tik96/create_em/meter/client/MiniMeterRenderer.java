package tor_tik96.create_em.meter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import tor_tik96.create_em.meter.MiniMeterBlock;
import tor_tik96.create_em.meter.MiniMeterBlockEntity;

/**
 * Цифры FE/t на четырёх никси-лампах мини-счётчика. Больше 9999 — мигает «9999».
 * Модель нарисована лицом на север (-Z), координаты в пикселях модели.
 */
public class MiniMeterRenderer implements BlockEntityRenderer<MiniMeterBlockEntity> {
    // слева направо для зрителя — от большего x к меньшему (как у латунного счётчика)
    private static final float[] TUBE_X = {10.5f, 8.25f, 6.0f, 3.75f};
    private static final float TUBE_W = 1.75f, TUBE_CY = 8f, TUBE_Z = 4.5f;
    private static final float DIGIT_SCALE = 2.6f / 16f / 7f;
    private static final int NIXIE_COLOR = 0xFFFFB35C;
    /** Период мигания при переполнении, тиков. */
    private static final int BLINK = 10;

    private final Font font;

    public MiniMeterRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(MiniMeterBlockEntity be, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int value = Math.round(be.getFlow());
        if (value > 9999) {
            long t = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
            if ((t / BLINK) % 2 == 1) return; // фаза «погасло»
            value = 9999;
        }

        float facingDeg = (be.getBlockState().getValue(MiniMeterBlock.FACING).toYRot() + 180f) % 360f;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facingDeg));
        pose.translate(-0.5, 0, -0.5);

        String s = String.format("%4d", value); // ведущие пробелы = погасшие лампы
        for (int i = 0; i < 4; i++) {
            char c = s.charAt(i);
            if (c == ' ') continue;
            String d = String.valueOf(c);
            pose.pushPose();
            pose.translate((TUBE_X[i] + TUBE_W / 2f) / 16f, TUBE_CY / 16f, (TUBE_Z - 0.02f) / 16f);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.scale(DIGIT_SCALE, -DIGIT_SCALE, DIGIT_SCALE);
            font.drawInBatch(d, -font.width(d) / 2f, -3.5f, NIXIE_COLOR, false, pose.last().pose(), buffers,
                    Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        pose.popPose();
    }
}
