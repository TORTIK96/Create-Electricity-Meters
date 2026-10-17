package tor_tik96.create_em.compat;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity.FuelType;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.Create_em;

import java.lang.reflect.Field;

/**
 * Горелка вспыхача Create принимает лаву по трубам как обычное топливо.
 * Лава превращается в тики горения ({@link Config#BURNER_LAVA_TICKS_PER_MB} на 1 мВ),
 * запас не больше родного потолка горелки (10000 тиков), так что насос просто держит её «долитой».
 * Перегрев лава не даёт; горелку на торте вспыхача лава не трогает.
 */
public final class BurnerLava {
    private static final ResourceLocation HEATER_ID = ResourceLocation.fromNamespaceAndPath("create", "blaze_heater");

    private static Field activeFuel;
    private static Field remainingBurnTime;
    private static boolean broken;

    public static void register(RegisterCapabilitiesEvent event) {
        BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(HEATER_ID);
        if (type == null || !resolveFields())
            return;
        @SuppressWarnings("unchecked")
        BlockEntityType<BlockEntity> t = (BlockEntityType<BlockEntity>) type;
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, t, BurnerLava::handler);
    }

    private static IFluidHandler handler(BlockEntity be, Direction side) {
        if (broken || side == Direction.UP || !(be instanceof BlazeBurnerBlockEntity burner))
            return null;
        if (Config.BURNER_LAVA_TICKS_PER_MB.get() <= 0)
            return null;
        return new Tank(burner);
    }

    private static boolean resolveFields() {
        try {
            activeFuel = BlazeBurnerBlockEntity.class.getDeclaredField("activeFuel");
            remainingBurnTime = BlazeBurnerBlockEntity.class.getDeclaredField("remainingBurnTime");
            activeFuel.setAccessible(true);
            remainingBurnTime.setAccessible(true);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Create_em.LOGGER.warn("Горелка вспыхача: не удалось подключить приём лавы ({}), функция выключена", e.toString());
            broken = true;
            return false;
        }
    }

    /** Свободное место в горелке, мВ лавы. 0 — не принимает. */
    private static int room(BlazeBurnerBlockEntity b, int ticksPerMb) {
        if (b.isCreative() || b.getLevel() == null || b.getLevel().isClientSide)
            return 0;
        BlazeBurnerBlock.HeatLevel heat = b.getHeatLevelFromBlock();
        if (heat == null || heat == BlazeBurnerBlock.HeatLevel.NONE) // пустая горелка, без вспыхача
            return 0;
        if (b.getActiveFuel() == FuelType.SPECIAL) // не гасим перегрев
            return 0;
        int free = BlazeBurnerBlockEntity.MAX_HEAT_CAPACITY - b.getRemainingBurnTime();
        return Math.max(0, free / ticksPerMb);
    }

    private record Tank(BlazeBurnerBlockEntity burner) implements IFluidHandler {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }

        @Override
        public int getTankCapacity(int tank) {
            int k = Config.BURNER_LAVA_TICKS_PER_MB.get();
            return k <= 0 ? 0 : BlazeBurnerBlockEntity.MAX_HEAT_CAPACITY / k;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.getFluid().is(FluidTags.LAVA);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int k = Config.BURNER_LAVA_TICKS_PER_MB.get();
            if (k <= 0 || resource.isEmpty() || !isFluidValid(0, resource))
                return 0;
            int amount = Math.min(resource.getAmount(), room(burner, k));
            if (amount <= 0 || action.simulate())
                return amount;
            try {
                FuelType before = burner.getActiveFuel();
                remainingBurnTime.setInt(burner, burner.getRemainingBurnTime() + amount * k);
                activeFuel.set(burner, FuelType.NORMAL);
                burner.updateBlockState();
                burner.setChanged();
                if (before != FuelType.NORMAL)
                    burner.notifyUpdate();
            } catch (ReflectiveOperationException | RuntimeException e) {
                Create_em.LOGGER.warn("Горелка вспыхача: ошибка приёма лавы ({}), функция выключена", e.toString());
                broken = true;
                return 0;
            }
            return amount;
        }

        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    }

    private BurnerLava() {}
}
