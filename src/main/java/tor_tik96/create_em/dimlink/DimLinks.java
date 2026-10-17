package tor_tik96.create_em.dimlink;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Все межпространственные узлы сервера и их пары. Хранится в верхнем мире, потому что пара
 * обычно живёт в двух разных измерениях, и второй конец может быть не загружен.
 */
public class DimLinks extends SavedData {
    private static final String NAME = "create_em_dim_links";
    private static final SavedData.Factory<DimLinks> FACTORY = new SavedData.Factory<>(DimLinks::new, DimLinks::load);

    private final Map<GlobalPos, UUID> owners = new HashMap<>();
    private final Map<GlobalPos, GlobalPos> pairs = new HashMap<>();

    public static DimLinks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public void register(GlobalPos node, UUID owner) {
        owners.put(node, owner);
        setDirty();
    }

    /** Узел убрали: пропадает и его пара. */
    public void remove(GlobalPos node) {
        owners.remove(node);
        unpair(node);
        setDirty();
    }

    @Nullable
    public UUID owner(GlobalPos node) {
        return owners.get(node);
    }

    @Nullable
    public GlobalPos partner(GlobalPos node) {
        return pairs.get(node);
    }

    /** Связать два узла; прежние пары обоих разрываются. */
    public void pair(GlobalPos a, GlobalPos b) {
        unpair(a);
        unpair(b);
        pairs.put(a, b);
        pairs.put(b, a);
        setDirty();
    }

    private void unpair(GlobalPos node) {
        GlobalPos old = pairs.remove(node);
        if (old != null && node.equals(pairs.get(old))) pairs.remove(old);
        setDirty();
    }

    // ---------- NBT ----------
    private static CompoundTag write(GlobalPos p) {
        CompoundTag c = new CompoundTag();
        c.putString("Dim", p.dimension().location().toString());
        c.putLong("Pos", p.pos().asLong());
        return c;
    }

    @Nullable
    private static GlobalPos read(CompoundTag c) {
        ResourceLocation dim = ResourceLocation.tryParse(c.getString("Dim"));
        if (dim == null) return null;
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(c.getLong("Pos")));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag l = new ListTag();
        owners.forEach((node, owner) -> {
            CompoundTag c = write(node);
            c.putUUID("Owner", owner);
            GlobalPos p = pairs.get(node);
            if (p != null) c.put("Partner", write(p));
            l.add(c);
        });
        tag.put("Nodes", l);
        return tag;
    }

    private static DimLinks load(CompoundTag tag, HolderLookup.Provider reg) {
        DimLinks d = new DimLinks();
        for (Tag t : tag.getList("Nodes", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            GlobalPos node = read(c);
            if (node == null || !c.hasUUID("Owner")) continue;
            d.owners.put(node, c.getUUID("Owner"));
            if (c.contains("Partner")) {
                GlobalPos p = read(c.getCompound("Partner"));
                if (p != null) d.pairs.put(node, p);
            }
        }
        return d;
    }
}
