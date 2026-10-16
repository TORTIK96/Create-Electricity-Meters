package tor_tik96.create_em;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.gui.AllIcons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Свои иконки для опций Create (колёсико настройки на блоке).
 * Create принимает только {@link AllIcons}, поэтому наследуемся и подменяем атлас.
 * Атлас — одна строка иконок 16×16: {@code textures/gui/icons.png}.
 */
public class EMIcons extends AllIcons {
    public static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(Create_em.MODID, "textures/gui/icons.png");
    private static final int ATLAS_W = 512, ATLAS_H = 16;

    public static final EMIcons REDSTONE = new EMIcons(0);
    public static final EMIcons OFF = new EMIcons(1);
    public static final EMIcons SPLASHING = new EMIcons(2);
    public static final EMIcons SMOKING = new EMIcons(3);
    public static final EMIcons BLASTING = new EMIcons(4);
    public static final EMIcons HAUNTING = new EMIcons(5);
    public static final EMIcons AUTO = new EMIcons(6);
    public static final EMIcons INPUT_MAIN = new EMIcons(7);
    public static final EMIcons INPUT_RESERVE = new EMIcons(8);
    public static final EMIcons DISCHARGE_FULL = new EMIcons(9);
    public static final EMIcons DISCHARGE_SIGNAL = new EMIcons(10);
    public static final EMIcons HOLD = new EMIcons(11);
    public static final EMIcons TARGET_ALL = new EMIcons(12);
    public static final EMIcons TARGET_NO_PLAYERS = new EMIcons(13);
    public static final EMIcons TARGET_HOSTILE = new EMIcons(14);
    public static final EMIcons TARGET_ANIMALS = new EMIcons(15);
    public static final EMIcons UNIVERSAL = new EMIcons(16);

    private final int u;

    private EMIcons(int index) {
        super(0, 0);
        this.u = index * 16;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void bind() {
        RenderSystem.setShaderTexture(0, ATLAS);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(ATLAS, x, y, 0, u, 0, 16, 16, ATLAS_W, ATLAS_H);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void render(PoseStack ms, MultiBufferSource buffer, int color) {
        VertexConsumer b = buffer.getBuffer(RenderType.text(ATLAS));
        Matrix4f m = ms.last().pose();
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, bl = color & 0xFF;
        int light = LightTexture.FULL_BRIGHT;
        float u1 = u / (float) ATLAS_W, u2 = (u + 16) / (float) ATLAS_W;
        b.addVertex(m, 0, 0, 0).setColor(r, g, bl, 255).setUv(u1, 0).setLight(light);
        b.addVertex(m, 0, 1, 0).setColor(r, g, bl, 255).setUv(u1, 1).setLight(light);
        b.addVertex(m, 1, 1, 0).setColor(r, g, bl, 255).setUv(u2, 1).setLight(light);
        b.addVertex(m, 1, 0, 0).setColor(r, g, bl, 255).setUv(u2, 0).setLight(light);
    }
}
