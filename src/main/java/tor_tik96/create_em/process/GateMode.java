package tor_tik96.create_em.process;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/** Роль шлюза обработки: откуда брать и чем обрабатывать — или куда складывать готовое. */
public enum GateMode implements StringRepresentable {
    BLASTING("blasting", CatalystProcess.BLASTING),
    SMOKING("smoking", CatalystProcess.SMOKING),
    SPLASHING("splashing", CatalystProcess.SPLASHING),
    HAUNTING("haunting", CatalystProcess.HAUNTING),
    UNIVERSAL("universal", CatalystProcess.UNIVERSAL),
    OUTPUT("output", null);

    private final String name;
    /** null — выходной шлюз. */
    @Nullable public final CatalystProcess process;

    GateMode(String name, @Nullable CatalystProcess process) {
        this.name = name;
        this.process = process;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public GateMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public String translationKey() {
        return "create_em.gate.mode." + name;
    }
}
