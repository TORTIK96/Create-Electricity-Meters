package tor_tik96.create_em.power;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import tor_tik96.create_em.EMIcons;

import java.util.Locale;

/** Когда конденсатор разряжается. */
public enum CapacitorMode implements INamedIconOptions {
    ON_FULL(EMIcons.DISCHARGE_FULL),
    ON_SIGNAL(EMIcons.DISCHARGE_SIGNAL),
    HOLD(EMIcons.HOLD);

    private final AllIcons icon;

    CapacitorMode(AllIcons icon) {
        this.icon = icon;
    }

    @Override public AllIcons getIcon() { return icon; }

    @Override
    public String getTranslationKey() {
        return "create_em.capacitor.mode." + name().toLowerCase(Locale.ROOT);
    }
}
