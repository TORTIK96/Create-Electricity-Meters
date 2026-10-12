package tor_tik96.create_em.meter.client;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.client.Minecraft;

/**
 * Обращения к клиентским классам из общего кода. Вызывается только на клиенте
 * (подсказки Create рисуются клиентом), поэтому на сервере класс не загружается.
 */
public final class ClientAccess {
    public static boolean isWearingGoggles() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && GogglesItem.isWearingGoggles(mc.player);
    }

    private ClientAccess() {}
}
