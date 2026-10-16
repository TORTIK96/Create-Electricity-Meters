package tor_tik96.create_em.process;

import net.minecraft.util.StringRepresentable;
import tor_tik96.create_em.Config;

/** Что катализатор делает прямо сейчас (хранится в блокстейте). */
public enum CatalystProcess implements StringRepresentable {
    NONE("none", 0),
    SPLASHING("splashing", 0),
    SMOKING("smoking", 12),
    BLASTING("blasting", 15),
    HAUNTING("haunting", 10),
    /** Сам выбирает обработку для каждого предмета ({@link UniversalFanType}). */
    UNIVERSAL("universal", 13);

    private final String name;
    public final int light;

    CatalystProcess(String name, int light) {
        this.name = name;
        this.light = light;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Расход, FE/t. */
    public int cost() {
        return switch (this) {
            case NONE -> 0;
            case SPLASHING -> Config.CATALYST_SPLASHING.get();
            case SMOKING -> Config.CATALYST_SMOKING.get();
            case BLASTING -> Config.CATALYST_BLASTING.get();
            case HAUNTING -> Config.CATALYST_HAUNTING.get();
            case UNIVERSAL -> Config.CATALYST_UNIVERSAL.get();
        };
    }

    public String translationKey() {
        return "create_em.catalyst.process." + name;
    }

    /** Сила сигнала → режим: 0 выкл, 1–4 промывка, 5–8 копчение, 9–12 плавка, 13–15 призрачный огонь. */
    public static CatalystProcess fromSignal(int signal) {
        if (signal <= 0) return NONE;
        if (signal <= 4) return SPLASHING;
        if (signal <= 8) return SMOKING;
        if (signal <= 12) return BLASTING;
        return HAUNTING;
    }
}
