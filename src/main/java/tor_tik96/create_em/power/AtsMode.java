package tor_tik96.create_em.power;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import tor_tik96.create_em.EMIcons;

import java.util.Locale;

/** Переключатель режима АВР на лицевой панели. */
public enum AtsMode implements INamedIconOptions {
    AUTO(EMIcons.AUTO),
    MAIN(EMIcons.INPUT_MAIN),
    RESERVE(EMIcons.INPUT_RESERVE),
    OFF(EMIcons.OFF);

    private final AllIcons icon;

    AtsMode(AllIcons icon) {
        this.icon = icon;
    }

    @Override
    public AllIcons getIcon() {
        return icon;
    }

    @Override
    public String getTranslationKey() {
        return "create_em.ats.mode." + name().toLowerCase(Locale.ROOT);
    }
}
