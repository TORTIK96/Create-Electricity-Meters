package tor_tik96.create_em.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import tor_tik96.create_em.Create_em;

/** Звуки мода. Файлы — assets/create_em/sounds/*.ogg, описание — sounds.json. */
public final class EMSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Create_em.MODID);

    /** Гул трансформатора под нагрузкой (петля 2 с). */
    public static final DeferredHolder<SoundEvent, SoundEvent> TRANSFORMER_HUM = register("transformer_hum");
    /** Андезитовый счётчик: щелчок диска. */
    public static final DeferredHolder<SoundEvent, SoundEvent> METER_TICK = register("meter_tick");
    /** Латунный и мини-счётчик: тик реле. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BRASS_METER_TICK = register("brass_meter_tick");
    /** Переключение АВР — контактор. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ATS_SWITCH = register("ats_switch");
    /** Расцепитель андезитового счётчика. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAKER_TRIP = register("breaker_trip");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Create_em.MODID, name)));
    }

    public static void init(IEventBus bus) {
        SOUNDS.register(bus);
    }

    private EMSounds() {}
}
