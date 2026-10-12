package tor_tik96.create_em.power;

import net.minecraft.util.StringRepresentable;

/** От какого ввода сейчас питается нагрузка. */
public enum AtsSource implements StringRepresentable {
    NONE("none"), MAIN("main"), RESERVE("reserve");

    private final String name;

    AtsSource(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String translationKey() {
        return "create_em.ats.source." + name;
    }
}
