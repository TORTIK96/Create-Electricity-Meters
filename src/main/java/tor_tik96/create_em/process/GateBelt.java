package tor_tik96.create_em.process;

import net.minecraft.util.StringRepresentable;

/** Как ленточная воронка Create: нет ленты под шлюзом, лента вдоль воронки или поперёк. */
public enum GateBelt implements StringRepresentable {
    NONE("none"), RETRACTED("retracted"), PERPENDICULAR("perpendicular");

    private final String name;

    GateBelt(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
