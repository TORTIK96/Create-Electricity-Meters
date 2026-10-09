package tor_tik96.create_em.meter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelData;
import tor_tik96.create_em.Create_em;
import tor_tik96.create_em.meter.EnergyMeterBlockEntity;
import tor_tik96.create_em.meter.MeterTier;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

/**
 * Андезитовый: стрелка-брусок (отдельная модель) крутится вокруг (8, 9) на передней грани.
 * Латунный: цифры FE/t на четырёх никси-лампах, полная яркость.
 * Координаты в пикселях модели (1/16 блока), фасад смотрит на север (-Z).
 */
public class EnergyMeterRenderer implements BlockEntityRenderer<EnergyMeterBlockEntity> {
    public static final ModelResourceLocation NEEDLE = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(Create_em.MODID, "block/energy_meter_needle"));

    private static final float PIVOT_X = 8f, PIVOT_Y = 9f;

    // никси-лампы: x начала каждой лампы, ширина 1.75; слева направо для зрителя — от большего x к меньшему
    private static final float[] TUBE_X = {10.5f, 8.25f, 6.0f, 3.75f};
    private static final float TUBE_W = 1.75f, TUBE_CY = 10.35f, TUBE_Z = 1.1f;
    private static final float DIGIT_SCALE = 2.6f / 16f / 7f; // высота цифры ≈ 2.6 px модели
    private static final int NIXIE_COLOR = 0xFFFFB35C;

    private final Font font;

    public EnergyMeterRenderer(BlockEntityRendererProvider.Context ctx) { this.font = ctx.getFont(); }

    @Override
    public void render(EnergyMeterBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        float facingDeg = (be.getBlockState().getValue(FACING).toYRot() + 180f) % 360f; // как "y" в blockstate
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facingDeg));
        pose.translate(-0.5, 0, -0.5);

        if (be.getTier() == MeterTier.ANDESITE) renderNeedle(be, partialTick, pose, buffers, light, overlay);
        else renderNixie(be, pose, buffers);

        pose.popPose();
    }

    private void renderNeedle(EnergyMeterBlockEntity be, float pt, PoseStack pose,
                              MultiBufferSource buffers, int light, int overlay) {
        BakedModel needle = Minecraft.getInstance().getModelManager().getModel(NEEDLE);
        pose.pushPose();
        pose.translate(PIVOT_X / 16f, PIVOT_Y / 16f, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(be.getNeedleAngle(pt))); // по часовой, если смотреть на счётчик
        pose.translate(-PIVOT_X / 16f, -PIVOT_Y / 16f, 0);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                pose.last(), buffers.getBuffer(RenderType.cutout()), be.getBlockState(), needle,
                1f, 1f, 1f, light, overlay, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }

    private void renderNixie(EnergyMeterBlockEntity be, PoseStack pose, MultiBufferSource buffers) {
        int value = Math.min(9999, Math.round(be.getFlowPerTick()));
        String s = String.format("%4d", value); // ведущие пробелы = погасшие лампы
        for (int i = 0; i < 4; i++) {
            char c = s.charAt(i);
            if (c == ' ') continue;
            String d = String.valueOf(c);
            pose.pushPose();
            pose.translate((TUBE_X[i] + TUBE_W / 2f) / 16f, TUBE_CY / 16f, (TUBE_Z - 0.02f) / 16f);
            pose.mulPose(Axis.YP.rotationDegrees(180)); // текст смотрит на -Z, к игроку перед счётчиком
            pose.scale(DIGIT_SCALE, -DIGIT_SCALE, DIGIT_SCALE);
            font.drawInBatch(d, -font.width(d) / 2f, -3.5f, NIXIE_COLOR, false, pose.last().pose(), buffers,
                    Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }
}
