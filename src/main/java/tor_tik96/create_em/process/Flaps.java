package tor_tik96.create_em.process;

import net.minecraft.util.Mth;

/**
 * Заслонки как у воронок и туннелей Create: толчок до ±1 и плавное затухание к 0
 * (то же экспоненциальное «догоняние», что LerpedFloat.chase(0, .05, EXP) у Create).
 */
public final class Flaps {
    private final float[] value, prev;

    public Flaps(int count) {
        value = new float[count];
        prev = new float[count];
    }

    /** inward — предмет заходит внутрь (заслонки отклоняются меньше, как у Create). */
    public void bump(int i, boolean inward) {
        if (i < 0 || i >= value.length) return;
        value[i] = inward ? -1 : 1;
    }

    public void tick() {
        for (int i = 0; i < value.length; i++) {
            prev[i] = value[i];
            value[i] += (0 - value[i]) * .05f;
            if (Math.abs(value[i]) < 1e-3f) value[i] = 0;
        }
    }

    public float get(int i, float partialTick) {
        return Mth.lerp(partialTick, prev[i], value[i]);
    }

    /** Угол сегмента заслонки — формула Create (FlapStuffs.flapAngle). */
    public static float angle(float flapness, int segment) {
        float intensity = segment == 3 ? 1.5f : segment + 1;
        float abs = Math.abs(flapness);
        float a = Mth.sin((float) ((1 - abs) * Math.PI * intensity)) * 30 * flapness;
        return flapness < 0 ? a * .5f : a;
    }
}
