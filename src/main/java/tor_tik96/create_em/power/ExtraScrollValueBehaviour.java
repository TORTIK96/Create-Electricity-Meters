package tor_tik96.create_em.power;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Вторая рамка настройки на одном блоке.
 *
 * <p>В Create поведения блока хранятся по типу, а значение рамки пишется под ключом {@code ScrollValue}.
 * У {@link ScrollValueBehaviour} и {@code ScrollOptionBehaviour} тип общий, поэтому две рамки на одном
 * блоке вытесняют друг друга и затирают сохранение. Здесь — свой тип, свой ключ в NBT и свой номер
 * для сетевого пакета настройки.
 */
public class ExtraScrollValueBehaviour extends ScrollValueBehaviour {
    public static final BehaviourType<ExtraScrollValueBehaviour> TYPE = new BehaviourType<>("create_em_extra_scroll");

    private final String key;

    public ExtraScrollValueBehaviour(Component label, SmartBlockEntity be, ValueBoxTransform slot, String key) {
        super(label, be, slot);
        this.key = key;
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    /** Номер для пакета настройки: у основной рамки 0, у фильтров Create 1, у заводских панелей 2+. */
    @Override
    public int netId() {
        return 100;
    }

    @Override
    public String getClipboardKey() {
        return key;
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        nbt.putInt(key, value); // без super: он писал бы в общий ключ ScrollValue и затёр основную рамку
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        if (nbt.contains(key)) value = nbt.getInt(key);
    }
}
