package tor_tik96.create_em.gov;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Список игроков, которым разрешено госэлектричество (data/create_em_gov.dat). */
public class GovData extends SavedData {
    private static final String NAME = "create_em_gov";
    /** UUID → имя (для списка). */
    private final Map<UUID, String> allowed = new TreeMap<>();

    public static GovData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(GovData::new, GovData::load), NAME);
    }

    public boolean isAllowed(UUID player) {
        return allowed.containsKey(player);
    }

    public boolean allow(UUID player, String name) {
        boolean added = allowed.put(player, name) == null;
        setDirty();
        return added;
    }

    public boolean deny(UUID player) {
        boolean removed = allowed.remove(player) != null;
        setDirty();
        return removed;
    }

    public Map<UUID, String> list() {
        return allowed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag l = new ListTag();
        allowed.forEach((id, name) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", id);
            c.putString("Name", name);
            l.add(c);
        });
        tag.put("Allowed", l);
        return tag;
    }

    private static GovData load(CompoundTag tag, HolderLookup.Provider reg) {
        GovData d = new GovData();
        for (Tag t : tag.getList("Allowed", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.allowed.put(c.getUUID("Id"), c.getString("Name"));
        }
        return d;
    }
}
