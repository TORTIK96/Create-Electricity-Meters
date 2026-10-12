package tor_tik96.create_em.link.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import tor_tik96.create_em.link.TunedLinkBlock;
import tor_tik96.create_em.link.TunedLinkBlockEntity;

/**
 * Предметы частоты рисует Create, а мы — выставленную силу в окошке:
 * две цифры друг над другом, как никси-лампы (окошко вытянуто вдоль корпуса).
 */
public class TunedLinkRenderer extends SmartBlockEntityRenderer<TunedLinkBlockEntity> {
    /** Высота цифры — 2.4 px, шрифт Minecraft — 7 единиц. */
    private static final float DIGIT = 2.4f / 16f / 7f;
    private static final int NORMAL_LIT = 0xFFFF7A1E, NORMAL_DIM = 0xFF6A3010;
    private static final int SELECT_LIT = 0xFF6BF0FF, SELECT_DIM = 0xFF1E5560;

    private final Font font;

    public TunedLinkRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
        this.font = ctx.getFont();
    }

    @Override
    protected void renderSafe(TunedLinkBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        super.renderSafe(be, pt, ms, buffers, light, overlay);
        if (be.getStrengthBehaviour() == null) return;

        boolean on = be.getBlockState().getValue(TunedLinkBlock.POWERED);
        int color = be.selector() ? (on ? SELECT_LIT : SELECT_DIM) : (on ? NORMAL_LIT : NORMAL_DIM);
        String s = String.format("%02d", be.getStrength());

        ValueBoxTransform slot = be.getStrengthBehaviour().getSlotPositioning();
        ms.pushPose();
        // тот же переход в систему окошка, что у рамки значения Create
        slot.transform(be.getLevel(), be.getBlockPos(), be.getBlockState(), ms);
        float inv = 1f / slot.getScale();
        ms.scale(inv, inv, inv);
        ms.scale(-DIGIT, -DIGIT, -DIGIT);
        ms.translate(0, 0, 0.5f); // чуть над стеклом (после отрицательного масштаба +z смотрит на игрока, как у Create)
        for (int i = 0; i < 2; i++) {
            String d = s.substring(i, i + 1);
            float y = i == 0 ? -8.2f : 0.6f; // десятки сверху, единицы снизу
            font.drawInBatch(d, -font.width(d) / 2f + 0.5f, y, color, false, ms.last().pose(), buffers,
                    Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        }
        ms.popPose();
    }
}
