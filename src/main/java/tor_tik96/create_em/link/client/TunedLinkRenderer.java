package tor_tik96.create_em.link.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import tor_tik96.create_em.link.TunedLinkBlock;
import tor_tik96.create_em.link.TunedLinkBlockEntity;

/** Предметы частоты рисует Create, а мы — выставленную силу в окошке справа. */
public class TunedLinkRenderer extends SmartBlockEntityRenderer<TunedLinkBlockEntity> {
    private static final float DIGIT_SCALE = 3f / 16f / 7f;
    private static final int LIT = 0xFFFF7A1E, DIM = 0xFF7A3A12;

    private final Font font;

    public TunedLinkRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
        this.font = ctx.getFont();
    }

    @Override
    protected void renderSafe(TunedLinkBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        super.renderSafe(be, pt, ms, buffers, light, overlay);

        Direction facing = be.getBlockState().getValue(TunedLinkBlock.FACING);
        ms.pushPose();
        // тот же поворот, что у модели в блокстейте (модель нарисована для facing=south)
        ms.translate(.5, .5, .5);
        switch (facing) {
            case WEST -> ms.mulPose(Axis.YP.rotationDegrees(-90));
            case NORTH -> ms.mulPose(Axis.YP.rotationDegrees(-180));
            case EAST -> ms.mulPose(Axis.YP.rotationDegrees(-270));
            case UP -> ms.mulPose(Axis.XP.rotationDegrees(-90));
            case DOWN -> ms.mulPose(Axis.XP.rotationDegrees(-270));
            default -> {}
        }
        ms.translate(-.5, -.5, -.5);

        String s = String.valueOf(be.getStrength());
        ms.translate(11f / 16f, 8f / 16f, 3.05f / 16f);
        ms.scale(DIGIT_SCALE, -DIGIT_SCALE, DIGIT_SCALE);
        font.drawInBatch(s, -font.width(s) / 2f + 0.5f, -3.5f, be.getBlockState().getValue(TunedLinkBlock.POWERED) ? LIT : DIM, false,
                ms.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        ms.popPose();
    }
}
