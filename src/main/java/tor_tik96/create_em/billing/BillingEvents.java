package tor_tik96.create_em.billing;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import tor_tik96.create_em.Create_em;

/** Расчётные периоды и стадии долга идут по серверному тику, независимо от чанков. */
@EventBusSubscriber(modid = Create_em.MODID)
public final class BillingEvents {
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 20 != 0) return;
        BillingData.get(e.getServer()).tick(e.getServer());
    }

    private BillingEvents() {}
}
