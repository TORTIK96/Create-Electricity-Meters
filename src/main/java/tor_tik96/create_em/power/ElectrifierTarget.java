package tor_tik96.create_em.power;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import tor_tik96.create_em.EMIcons;

import java.util.Locale;

/** Кого бьёт электризатор. */
public enum ElectrifierTarget implements INamedIconOptions {
    NO_PLAYERS(EMIcons.TARGET_NO_PLAYERS),
    ANIMALS(EMIcons.TARGET_ANIMALS),
    HOSTILE(EMIcons.TARGET_HOSTILE),
    ALL(EMIcons.TARGET_ALL);

    private final AllIcons icon;

    ElectrifierTarget(AllIcons icon) {
        this.icon = icon;
    }

    public boolean matches(LivingEntity e) {
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return switch (this) {
            case NO_PLAYERS -> !(e instanceof Player);
            case ANIMALS -> e instanceof Animal;
            case HOSTILE -> e instanceof Enemy;
            case ALL -> true;
        };
    }

    @Override public AllIcons getIcon() { return icon; }

    @Override
    public String getTranslationKey() {
        return "create_em.electrifier.target." + name().toLowerCase(Locale.ROOT);
    }
}
