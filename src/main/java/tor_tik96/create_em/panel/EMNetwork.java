package tor_tik96.create_em.panel;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import tor_tik96.create_em.Create_em;

/** Свои пакеты: синхронизация списков панели (S2C) и значения из полей ввода (C2S). Кнопки — ванильным пакетом. */
@EventBusSubscriber(modid = Create_em.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class EMNetwork {
    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("3")
                .playToClient(PanelSyncPayload.TYPE, PanelSyncPayload.CODEC, PanelSyncPayload::handle)
                .playToClient(tor_tik96.create_em.billing.TerminalSyncPayload.TYPE, tor_tik96.create_em.billing.TerminalSyncPayload.CODEC,
                        tor_tik96.create_em.billing.TerminalSyncPayload::handle)
                .playToServer(ValuePayload.TYPE, ValuePayload.CODEC, ValuePayload::handle);
    }

    private EMNetwork() {}
}
