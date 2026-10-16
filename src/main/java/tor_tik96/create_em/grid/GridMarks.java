package tor_tik96.create_em.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Госмаркировки одного измерения: какие блоки изолированы и чьи они,
 * плюс кто поставил энергоблоки (маркировать можно только свои).
 */
public class GridMarks extends SavedData {
    private static final String NAME = "create_em_grid_marks";

    public record Mark(UUID owner, String name, ResourceLocation block) {}

    private final Map<Long, Mark> marks = new HashMap<>();
    private final Map<Long, UUID> placers = new HashMap<>();

    private static final SavedData.Factory<GridMarks> FACTORY = new SavedData.Factory<>(GridMarks::new, GridMarks::load);
    /** Спрашивают при каждом запросе энергии в мире — держим под рукой, без поиска в хранилище. */
    private static final Map<ServerLevel, GridMarks> CACHE = new java.util.WeakHashMap<>();

    public static GridMarks get(ServerLevel level) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(level, l -> l.getDataStorage().computeIfAbsent(FACTORY, NAME));
        }
    }

    public boolean isEmpty() {
        return marks.isEmpty();
    }

    /** Маркировка на позиции; если блок там уже другой (сдвинули, заменили) — маркировки нет. */
    @Nullable
    public Mark at(ServerLevel level, BlockPos pos) {
        Mark m = marks.get(pos.asLong());
        if (m == null || !level.isLoaded(pos)) return null;
        return valid(m, level.getBlockState(pos), pos) ? m : null;
    }

    /** То же, когда состояние блока уже известно (без лишнего чтения мира). */
    @Nullable
    public Mark at(BlockState state, BlockPos pos) {
        Mark m = marks.get(pos.asLong());
        return m != null && valid(m, state, pos) ? m : null;
    }

    private boolean valid(Mark m, BlockState state, BlockPos pos) {
        if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(m.block())) return true;
        marks.remove(pos.asLong());
        setDirty();
        return false;
    }

    public void mark(BlockPos pos, BlockState state, UUID owner, String name) {
        marks.put(pos.asLong(), new Mark(owner, name, BuiltInRegistries.BLOCK.getKey(state.getBlock())));
        setDirty();
    }

    public void unmark(BlockPos pos) {
        if (marks.remove(pos.asLong()) != null) setDirty();
    }

    @Nullable
    public UUID placer(BlockPos pos) {
        return placers.get(pos.asLong());
    }

    public void setPlacer(BlockPos pos, @Nullable UUID player) {
        if (player == null ? placers.remove(pos.asLong()) != null : !player.equals(placers.put(pos.asLong(), player)))
            setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag l = new ListTag();
        marks.forEach((pos, m) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", pos);
            c.putUUID("Owner", m.owner());
            c.putString("Name", m.name());
            c.putString("Block", m.block().toString());
            l.add(c);
        });
        tag.put("Marks", l);
        ListTag p = new ListTag();
        placers.forEach((pos, id) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", pos);
            c.putUUID("Id", id);
            p.add(c);
        });
        tag.put("Placers", p);
        return tag;
    }

    private static GridMarks load(CompoundTag tag, HolderLookup.Provider reg) {
        GridMarks d = new GridMarks();
        for (Tag t : tag.getList("Marks", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            ResourceLocation block = ResourceLocation.tryParse(c.getString("Block"));
            if (block != null)
                d.marks.put(c.getLong("Pos"), new Mark(c.getUUID("Owner"), c.getString("Name"), block));
        }
        for (Tag t : tag.getList("Placers", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.placers.put(c.getLong("Pos"), c.getUUID("Id"));
        }
        return d;
    }
}
