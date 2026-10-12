package tor_tik96.create_em.process;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import tor_tik96.create_em.EMIcons;

/** Переключатель на корпусе (колёсико Create): по сигналу или вручную. */
public enum CatalystMode implements INamedIconOptions {
    REDSTONE(EMIcons.REDSTONE, null),
    OFF(EMIcons.OFF, CatalystProcess.NONE),
    SPLASHING(EMIcons.SPLASHING, CatalystProcess.SPLASHING),
    SMOKING(EMIcons.SMOKING, CatalystProcess.SMOKING),
    BLASTING(EMIcons.BLASTING, CatalystProcess.BLASTING),
    HAUNTING(EMIcons.HAUNTING, CatalystProcess.HAUNTING);

    private final AllIcons icon;
    /** null — режим берётся из силы сигнала. */
    public final CatalystProcess fixed;

    CatalystMode(AllIcons icon, CatalystProcess fixed) {
        this.icon = icon;
        this.fixed = fixed;
    }

    @Override
    public AllIcons getIcon() {
        return icon;
    }

    @Override
    public String getTranslationKey() {
        return "create_em.catalyst.mode." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
