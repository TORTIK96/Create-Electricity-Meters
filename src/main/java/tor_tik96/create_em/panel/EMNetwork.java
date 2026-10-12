package tor_tik96.create_em.panel;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import tor_tik96.create_em.Create_em;

/** Свои пакеты. Действия клиента идут через кнопки меню (ванильный пакет), свой — только синхронизация списков. */
@EventBusSubscriber(modid = Create_em.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class EMNetwork {
    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("1").playToClient(PanelSyncPayload.TYPE, PanelSyncPayload.CODEC, PanelSyncPayload::handle);
    }

    private EMNetwork() {}
}
