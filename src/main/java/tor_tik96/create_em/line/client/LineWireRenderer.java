package tor_tik96.create_em.line.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import tor_tik96.create_em.Create_em;
import tor_tik96.create_em.line.LineNode;

/**
 * Рисует провода узла: провисающий кабель из двух перекрещенных лент.
 * Каждый провод рисует только узел с меньшим BlockPos#asLong, чтобы не было дублей.
 */
public final class LineWireRenderer {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Create_em.MODID, "textures/entity/line_wire.png");
    /** Госпровод — синяя изоляция. */
    private static final ResourceLocation STATE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Create_em.MODID, "textures/entity/state_wire.png");
    private static final float HALF_WIDTH = 0.04f; // ~1.3 px толщины
    private static final int SEGMENTS = 20;
    /** Цвет текущего провода (рисуется только в потоке рендера). */
    private static int color = 0xFFFFFF;

    public static void render(BlockEntity be, LineNode node, PoseStack pose, MultiBufferSource buffers) {
        Level level = be.getLevel();
        if (level == null || node.getLinks().isEmpty()) return;
        BlockPos origin = be.getBlockPos();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(node.isStateGrid() ? STATE_TEXTURE : TEXTURE));
        color = node.wireColor();

        Vec3 a = node.getWireAnchor();
        for (BlockPos other : node.getLinks().get()) {
            if (origin.asLong() > other.asLong()) continue;
            if (!(level.getBlockEntity(other) instanceof LineNode on)) continue;
            Vec3 b = on.getWireAnchor().add(other.getX() - origin.getX(), other.getY() - origin.getY(), other.getZ() - origin.getZ());
            drawCable(vc, pose, level, origin, a, b);
        }
    }

    private static void drawCable(VertexConsumer vc, PoseStack pose, Level level, BlockPos origin, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-3) return;
        double sag = Math.min(1.5, 0.05 * len + 0.05);

        // две ленты: «горизонтальная» поперёк провода и перпендикулярная ей
        Vec3 dir = d.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(HALF_WIDTH);
        Vec3 up = dir.cross(side).normalize().scale(HALF_WIDTH);

        Vec3 prev = point(a, d, sag, 0);
        int prevLight = light(level, origin, prev);
        for (int i = 1; i <= SEGMENTS; i++) {
            Vec3 cur = point(a, d, sag, i / (float) SEGMENTS);
            int curLight = light(level, origin, cur);
            float v0 = 0, v1 = (float) Math.min(1, prev.distanceTo(cur));
            quad(vc, pose, prev, cur, side, prevLight, curLight, v0, v1);
            quad(vc, pose, prev, cur, up, prevLight, curLight, v0, v1);
            prev = cur;
            prevLight = curLight;
        }
    }

    /** Точка провода: прямая a→b с параболическим провисом. */
    private static Vec3 point(Vec3 a, Vec3 d, double sag, float t) {
        return new Vec3(a.x + d.x * t, a.y + d.y * t - sag * 4 * t * (1 - t), a.z + d.z * t);
    }

    private static int light(Level level, BlockPos origin, Vec3 local) {
        return LevelRenderer.getLightColor(level, BlockPos.containing(
                origin.getX() + local.x, origin.getY() + local.y, origin.getZ() + local.z));
    }

    private static void quad(VertexConsumer vc, PoseStack pose, Vec3 p0, Vec3 p1, Vec3 off,
                             int l0, int l1, float v0, float v1) {
        PoseStack.Pose last = pose.last();
        vertex(vc, last, p0.subtract(off), 0, v0, l0);
        vertex(vc, last, p0.add(off), 1, v0, l0);
        vertex(vc, last, p1.add(off), 1, v1, l1);
        vertex(vc, last, p1.subtract(off), 0, v1, l1);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, float u, float v, int light) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0, 1, 0);
    }

    private LineWireRenderer() {}
}
