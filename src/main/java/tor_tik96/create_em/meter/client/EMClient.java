package tor_tik96.create_em.meter.client;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import tor_tik96.create_em.Create_em;
import tor_tik96.create_em.billing.client.TerminalScreen;
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
        e.registerBlockEntityRenderer(EMRegistry.MINI_METER_BE.get(), MiniMeterRenderer::new);
        e.registerBlockEntityRenderer(EMRegistry.TRANSFORMER_BE.get(), LineNodeRenderer::new);
        e.registerBlockEntityRenderer(EMRegistry.LINE_CONNECTOR_BE.get(), LineNodeRenderer::new);
        e.registerBlockEntityRenderer(EMRegistry.LINE_CONVERTER_BE.get(), LineNodeRenderer::new);
        e.registerBlockEntityRenderer(EMRegistry.STATE_CONNECTOR_BE.get(), LineNodeRenderer::new);
        // предметы частоты на передатчике рисует рендерер Create
        e.registerBlockEntityRenderer(EMRegistry.TUNED_LINK_BE.get(), tor_tik96.create_em.link.client.TunedLinkRenderer::new);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent e) {
        e.register(EMRegistry.METER_LIMIT_MENU.get(), MeterLimitScreen::new);
        e.register(EMRegistry.TERMINAL_MENU.get(), TerminalScreen::new);
        e.register(EMRegistry.SUPPLIER_PANEL_MENU.get(), tor_tik96.create_em.panel.client.SupplierPanelScreen::new);
    }

    /** Описания предметов в стиле Create: «Удерживайте [Shift] для описания». */
    @SubscribeEvent
    static void clientSetup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> EMRegistry.ITEMS.getEntries().forEach(holder -> {
            Item item = holder.get();
            TooltipModifier.REGISTRY.register(item, new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));
        }));
    }

    private EMClient() {}
}
