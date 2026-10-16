package tor_tik96.create_em;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import tor_tik96.create_em.registry.EMRegistry;

@Mod(Create_em.MODID)
public class Create_em {
    public static final String MODID = "create_em";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_em"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> EMRegistry.BRASS_METER_ITEM.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(EMRegistry.ANDESITE_METER_ITEM.get());
                        output.accept(EMRegistry.BRASS_METER_ITEM.get());
                        output.accept(EMRegistry.PUBLIC_METER_ITEM.get());
                        output.accept(EMRegistry.MINI_METER_ITEM.get());
                        output.accept(EMRegistry.TRANSFORMER_ITEM.get());
                        output.accept(EMRegistry.SMART_TRANSFORMER_ITEM.get());
                        output.accept(EMRegistry.LINE_CONVERTER_ITEM.get());
                        output.accept(EMRegistry.LINE_CONNECTOR_ITEM.get());
                        output.accept(EMRegistry.LINE_WIRE.get());
                        output.accept(EMRegistry.SEAL.get());
                        output.accept(EMRegistry.TERMINAL_ITEM.get());
                        output.accept(EMRegistry.STATE_SUBSTATION_ITEM.get());
                        output.accept(EMRegistry.STATE_SEAL.get());
                        output.accept(EMRegistry.STATE_MARK.get());
                        output.accept(EMRegistry.STATE_CONNECTOR_ITEM.get());
                        output.accept(EMRegistry.BATTERY_ITEM.get());
                        output.accept(EMRegistry.ATS_ITEM.get());
                        output.accept(EMRegistry.KNIFE_SWITCH_ITEM.get());
                        output.accept(EMRegistry.CAPACITOR_ITEM.get());
                        output.accept(EMRegistry.POWER_CABLE_ITEM.get());
                        output.accept(EMRegistry.ELECTRIFIER_ITEM.get());
                        output.accept(EMRegistry.CATALYST_ITEM.get());
                        output.accept(EMRegistry.TUNED_LINK_ITEM.get());
                    })
                    .build());

    public Create_em(IEventBus modEventBus, ModContainer modContainer) {
        EMRegistry.init(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }
}
