package tor_tik96.create_em.line;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Список соединений узла. Хранится в NBT и синхронизируется на клиент для отрисовки проводов. */
public final class LineLinks {
    private final BlockEntity holder;
    private final Set<BlockPos> links = new LinkedHashSet<>();

    public LineLinks(BlockEntity holder) { this.holder = holder; }

    public Set<BlockPos> get() { return Collections.unmodifiableSet(links); }
    public int size() { return links.size(); }
    public boolean contains(BlockPos pos) { return links.contains(pos); }
    public boolean isEmpty() { return links.isEmpty(); }

    boolean add(BlockPos pos) {
        if (!links.add(pos.immutable())) return false;
        changed();
        return true;
    }

    boolean remove(BlockPos pos) {
        if (!links.remove(pos)) return false;
        changed();
        return true;
    }

    void clear() {
        if (links.isEmpty()) return;
        links.clear();
        changed();
    }

    private void changed() {
        holder.setChanged();
        var level = holder.getLevel();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(holder.getBlockPos(), holder.getBlockState(), holder.getBlockState(), Block.UPDATE_CLIENTS);
            LineNetwork.bump();
        }
    }

    public void save(CompoundTag tag) {
        long[] arr = new long[links.size()];
        int i = 0;
        for (BlockPos p : links) arr[i++] = p.asLong();
        tag.putLongArray("Links", arr);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("Links")) return;
        links.clear();
        for (long l : tag.getLongArray("Links")) links.add(BlockPos.of(l));
    }
}
