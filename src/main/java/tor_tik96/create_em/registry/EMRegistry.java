package tor_tik96.create_em.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
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
import tor_tik96.create_em.line.*;
import tor_tik96.create_em.meter.EnergyMeterBlock;
import tor_tik96.create_em.meter.EnergyMeterBlockEntity;
import tor_tik96.create_em.meter.MeterLimitMenu;
import tor_tik96.create_em.meter.MeterTier;
import tor_tik96.create_em.seal.SealItem;

public final class EMRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Create_em.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Create_em.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BE_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Create_em.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Create_em.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Create_em.MODID);

    // ---------- компоненты ----------
    /** Узел, к которому привязан провод в руке. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> BOUND_TO =
            COMPONENTS.registerComponentType("bound_to",
                    b -> b.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));

    // ---------- счётчики ----------
    public static final DeferredBlock<EnergyMeterBlock> ANDESITE_METER = BLOCKS.register("andesite_energy_meter",
            () -> new EnergyMeterBlock(MeterTier.ANDESITE, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(2.5f).sound(SoundType.WOOD).requiresCorrectToolForDrops().noOcclusion()));

    public static final DeferredBlock<EnergyMeterBlock> BRASS_METER = BLOCKS.register("brass_energy_meter",
            () -> new EnergyMeterBlock(MeterTier.BRASS, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_YELLOW).strength(3f).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()));

    // ---------- сервисная линия ----------
    public static final DeferredBlock<TransformerBlock> TRANSFORMER = BLOCKS.register("line_transformer",
            () -> new TransformerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(3f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()));

    public static final DeferredBlock<LineConnectorBlock> LINE_CONNECTOR = BLOCKS.register("line_connector",
            () -> new LineConnectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE).strength(1f).sound(SoundType.COPPER).noOcclusion()));

    public static final DeferredItem<BlockItem> ANDESITE_METER_ITEM =
            ITEMS.registerSimpleBlockItem("andesite_energy_meter", ANDESITE_METER, new Item.Properties());
    public static final DeferredItem<BlockItem> BRASS_METER_ITEM =
            ITEMS.registerSimpleBlockItem("brass_energy_meter", BRASS_METER, new Item.Properties());
    public static final DeferredItem<BlockItem> TRANSFORMER_ITEM =
            ITEMS.registerSimpleBlockItem("line_transformer", TRANSFORMER, new Item.Properties());
    public static final DeferredItem<BlockItem> LINE_CONNECTOR_ITEM =
            ITEMS.registerSimpleBlockItem("line_connector", LINE_CONNECTOR, new Item.Properties());
    public static final DeferredItem<LineWireItem> LINE_WIRE =
            ITEMS.register("line_wire", () -> new LineWireItem(new Item.Properties()));
    public static final DeferredItem<SealItem> SEAL =
            ITEMS.register("seal", () -> new SealItem(new Item.Properties()));

    // ---------- меню ----------
    public static final DeferredHolder<MenuType<?>, MenuType<MeterLimitMenu>> METER_LIMIT_MENU =
            MENUS.register("meter_limit", () -> new MenuType<>(MeterLimitMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // ---------- BlockEntity ----------
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyMeterBlockEntity>> METER_BE =
            BE_TYPES.register("energy_meter", () -> BlockEntityType.Builder
                    .of(EnergyMeterBlockEntity::new, ANDESITE_METER.get(), BRASS_METER.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransformerBlockEntity>> TRANSFORMER_BE =
            BE_TYPES.register("line_transformer", () -> BlockEntityType.Builder
                    .of(TransformerBlockEntity::new, TRANSFORMER.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LineConnectorBlockEntity>> LINE_CONNECTOR_BE =
            BE_TYPES.register("line_connector", () -> BlockEntityType.Builder
                    .of(LineConnectorBlockEntity::new, LINE_CONNECTOR.get()).build(null));

    public static void init(IEventBus modBus) {
        COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BE_TYPES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(EMRegistry::registerCaps);
    }

    /**
     * Обычная FE заходит только в трансформатор (с боков и снизу; сверху — изолятор линии).
     * Счётчик FE не принимает: он получает энергию только по линии.
     */
    private static void registerCaps(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TRANSFORMER_BE.get(),
                (be, side) -> side == Direction.UP ? null : be.getInput());
    }

    private EMRegistry() {}
}
