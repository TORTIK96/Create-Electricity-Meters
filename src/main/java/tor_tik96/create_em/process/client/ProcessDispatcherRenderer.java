package tor_tik96.create_em.process.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import tor_tik96.create_em.Create_em;
import tor_tik96.create_em.process.ProcessDispatcherBlockEntity;

/** Диспетчер: шторки как у туннеля Create — на гранях, к которым подходит лента. */
public class ProcessDispatcherRenderer implements BlockEntityRenderer<ProcessDispatcherBlockEntity> {
    public static final ModelResourceLocation FLAP = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(Create_em.MODID, "block/dispatcher_flap"));

    /** Шторки, свисающие с нижнего края — когда диспетчер стоит над лентой. */
    public static final ModelResourceLocation FLAP_LOW = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(Create_em.MODID, "block/dispatcher_flap_low"));

    public ProcessDispatcherRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ProcessDispatcherBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        var model = Minecraft.getInstance().getModelManager().getModel(FLAP);
        if (be.getLevel().getBlockEntity(be.getBlockPos().below()) instanceof BeltBlockEntity below) {
            var low = Minecraft.getInstance().getModelManager().getModel(FLAP_LOW);
            Direction move = below.getMovementFacing();
            for (Direction d : new Direction[]{move, move.getOpposite()})
                FlapRenderer.render(ms, buffers, be.getBlockState(), low, 2 / 16f, -0.55f / 16f,
                        FlapRenderer.horizontalAngle(d.getOpposite()), 0, be.flaps.get(d.ordinal(), pt), light, overlay);
        }
        var lowModel = Minecraft.getInstance().getModelManager().getModel(FLAP_LOW);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            var pos = be.getBlockPos().relative(d);
            if (be.getLevel().getBlockEntity(pos) instanceof BeltBlockEntity) {
                FlapRenderer.render(ms, buffers, be.getBlockState(), model, 15 / 16f, -0.55f / 16f,
                        FlapRenderer.horizontalAngle(d.getOpposite()), 0, be.flaps.get(d.ordinal(), pt), light, overlay);
            } else if (be.getLevel().getBlockEntity(pos.below()) instanceof BeltBlockEntity) {
                // лента на блок ниже: шторка свисает с нижнего края грани к ленте, как у ленточной воронки
                FlapRenderer.render(ms, buffers, be.getBlockState(), lowModel, 2 / 16f, -0.55f / 16f,
                        FlapRenderer.horizontalAngle(d.getOpposite()), 0, be.flaps.get(d.ordinal(), pt), light, overlay);
            }
        }
    }
}
