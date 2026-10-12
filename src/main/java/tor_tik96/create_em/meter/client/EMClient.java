package tor_tik96.create_em.meter.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import tor_tik96.create_em.Create_em;
import tor_tik96.create_em.line.client.LineNodeRenderer;
import tor_tik96.create_em.registry.EMRegistry;

@EventBusSubscriber(modid = Create_em.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EMClient {
    /** Стрелка не привязана к блокстейту — запекаем её отдельно. */
    @SubscribeEvent
    static void registerModels(ModelEvent.RegisterAdditional e) {
        e.register(EnergyMeterRenderer.NEEDLE);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(EMRegistry.METER_BE.get(), EnergyMeterRenderer::new);
        e.registerBlockEntityRenderer(EMRegistry.TRANSFORMER_BE.get(), LineNodeRenderer::new);
        e.registerBlockEntityRenderer(EMRegistry.LINE_CONNECTOR_BE.get(), LineNodeRenderer::new);
    }

    private EMClient() {}
}
