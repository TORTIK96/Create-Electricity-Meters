package tor_tik96.create_em.process.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import tor_tik96.create_em.process.ProcessGateBlock;
import tor_tik96.create_em.process.ProcessGateBlockEntity;

/** Шлюз: заслонки латунной воронки Create (модель воронки берём у самого Create). */
public class ProcessGateRenderer implements BlockEntityRenderer<ProcessGateBlockEntity> {
    public static final ModelResourceLocation FLAP = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("create", "block/funnel/flap"));

    public ProcessGateRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ProcessGateBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        Direction toInventory = be.getBlockState().getValue(ProcessGateBlock.FACING);
        if (toInventory.getAxis().isVertical()) return; // у вертикальных воронок Create заслонок тоже нет
        var model = Minecraft.getInstance().getModelManager().getModel(FLAP);
        // воронка Create смотрит от инвентаря; угол — от направления «к инвентарю», как у Create
        FlapRenderer.render(ms, buffers, be.getBlockState(), model, 10 / 16f, 9.5f / 16f,
                FlapRenderer.horizontalAngle(toInventory), 1 / 16f, be.flaps.get(0, pt), light, overlay);
    }
}
