package tor_tik96.create_em.registry;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import tor_tik96.create_em.Create_em;
import tor_tik96.create_em.meter.EnergyMeterBlock;
import tor_tik96.create_em.meter.EnergyMeterBlockEntity;
import tor_tik96.create_em.meter.MeterTier;

public final class EMRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Create_em.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Create_em.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BE_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Create_em.MODID);

    public static final DeferredBlock<EnergyMeterBlock> ANDESITE_METER = BLOCKS.register("andesite_energy_meter",
            () -> new EnergyMeterBlock(MeterTier.ANDESITE, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(2.5f).sound(SoundType.WOOD).requiresCorrectToolForDrops().noOcclusion()));

    public static final DeferredBlock<EnergyMeterBlock> BRASS_METER = BLOCKS.register("brass_energy_meter",
            () -> new EnergyMeterBlock(MeterTier.BRASS, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_YELLOW).strength(3f).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()));

    public static final DeferredItem<BlockItem> ANDESITE_METER_ITEM =
            ITEMS.registerSimpleBlockItem("andesite_energy_meter", ANDESITE_METER, new Item.Properties());
    public static final DeferredItem<BlockItem> BRASS_METER_ITEM =
            ITEMS.registerSimpleBlockItem("brass_energy_meter", BRASS_METER, new Item.Properties());

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyMeterBlockEntity>> METER_BE =
            BE_TYPES.register("energy_meter", () -> BlockEntityType.Builder
                    .of(EnergyMeterBlockEntity::new, ANDESITE_METER.get(), BRASS_METER.get()).build(null));

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BE_TYPES.register(modBus);
        modBus.addListener(EMRegistry::registerCaps);
    }

    /** Энергию можно залить только сверху; выходы сами толкают энергию в соседей. */
    private static void registerCaps(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, METER_BE.get(),
                (be, side) -> side == Direction.UP ? be.getInput() : null);
    }

    private EMRegistry() {}
}
