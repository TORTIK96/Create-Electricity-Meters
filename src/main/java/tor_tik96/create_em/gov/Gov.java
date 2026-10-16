package tor_tik96.create_em.gov;

import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Госэлектричество: особый поставщик «Госсеть». Его подстанции ставят и настраивают только операторы
 * сервера, госпломбы выдают тоже они. Энергию получают только счётчики с госпломбой, хозяин которых
 * есть в списке доступа ({@link GovData}).
 */
public final class Gov {
    /** Постоянный UUID поставщика «Госсеть» — одинаковый на всех серверах и после перезапуска. */
    public static final UUID SUPPLIER = UUID.nameUUIDFromBytes("create_em:state_grid".getBytes(StandardCharsets.UTF_8));
    public static final String NAME = "Госсеть";

    public static boolean isOperator(Player p) {
        return p.hasPermissions(2);
    }

    private Gov() {}
}
