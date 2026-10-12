package tor_tik96.create_em.line.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import tor_tik96.create_em.line.LineNode;

/** Рендер трансформатора и коннектора: сами блоки — обычные модели, здесь только провода. */
public class LineNodeRenderer<T extends BlockEntity & LineNode> implements BlockEntityRenderer<T> {
    public LineNodeRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        LineWireRenderer.render(be, be, pose, buffers);
    }

    /** Провод длиннее блока: не отсекаем по рамке блока. */
    @Override public boolean shouldRenderOffScreen(T be) { return !be.getLinks().isEmpty(); }
    @Override public AABB getRenderBoundingBox(T be) { return be.getLinks().isEmpty() ? new AABB(be.getBlockPos()) : AABB.INFINITE; }
    @Override public int getViewDistance() { return 128; }
}
