package tor_tik96.create_em.seal;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Реестр пломб на сервере — единственный источник правды о том, действительна ли пломба.
 * Хранится в data/create_em_seals.dat мира (в overworld).
 * <p>
 * В счётчике лежит только id пломбы; подделать или скопировать пломбу нельзя:
 * id создаётся сервером при установке и привязан к измерению и координатам счётчика.
 */
public class SealRegistry extends SavedData {
    private static final String NAME = "create_em_seals";
    private static final int JOURNAL_LIMIT = 100;

    public enum State { INSTALLED, BROKEN, REVOKED }

    public record Seal(UUID id, UUID supplier, String supplierName,
                       ResourceKey<Level> dim, BlockPos pos, State state, long installedAt) {
        Seal withState(State s) { return new Seal(id, supplier, supplierName, dim, pos, s, installedAt); }
    }

    /** Запись журнала поставщика: что случилось с его пломбой. */
    public record Event(long gameTime, UUID sealId, ResourceKey<Level> dim, BlockPos pos, State state, String by) {}

    private final Map<UUID, Seal> seals = new HashMap<>();
    private final Map<UUID, Deque<Event>> journal = new HashMap<>(); // поставщик -> события

    public static SealRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SealRegistry::new, SealRegistry::load), NAME);
    }

    // ---------- операции ----------

    public Seal install(UUID supplier, String supplierName, Level level, BlockPos pos) {
        Seal seal = new Seal(UUID.randomUUID(), supplier, supplierName, level.dimension(), pos.immutable(),
                State.INSTALLED, level.getGameTime());
        seals.put(seal.id(), seal);
        setDirty();
        return seal;
    }

    /** Действующая пломба, установленная именно на этот счётчик, или null. */
    @Nullable
    public Seal getValid(@Nullable UUID id, Level level, BlockPos pos) {
        if (id == null) return null;
        Seal s = seals.get(id);
        if (s == null || s.state() != State.INSTALLED || !s.dim().equals(level.dimension()) || !s.pos().equals(pos)) return null;
        return s;
    }

    /** Пломба сорвана (счётчик сломан, сдвинут, вскрыт). Поставщик получает запись в журнал и сообщение. */
    public void breakSeal(UUID id, MinecraftServer server, String by) {
        finish(id, State.BROKEN, server, by);
    }

    /** Поставщик сам снял пломбу. */
    public void revoke(UUID id, MinecraftServer server, String by) {
        finish(id, State.REVOKED, server, by);
    }

    private void finish(UUID id, State state, MinecraftServer server, String by) {
        Seal s = seals.get(id);
        if (s == null || s.state() != State.INSTALLED) return;
        seals.put(id, s.withState(state));
        Deque<Event> log = journal.computeIfAbsent(s.supplier(), k -> new ArrayDeque<>());
        log.addFirst(new Event(server.overworld().getGameTime(), id, s.dim(), s.pos(), state, by));
        while (log.size() > JOURNAL_LIMIT) log.removeLast();
        setDirty();

        if (state == State.BROKEN) {
            ServerPlayer supplier = server.getPlayerList().getPlayer(s.supplier());
            if (supplier != null) {
                BlockPos p = s.pos();
                Object who = by.isEmpty() ? Component.translatable("create_em.seal.unknown") : by;
                supplier.sendSystemMessage(Component.translatable("create_em.seal.broken_notice",
                        p.getX(), p.getY(), p.getZ(), who).withStyle(ChatFormatting.RED));
            }
        }
    }

    public List<Event> getJournal(UUID supplier) {
        return List.copyOf(journal.getOrDefault(supplier, new ArrayDeque<>()));
    }

    // ---------- NBT ----------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag list = new ListTag();
        for (Seal s : seals.values()) {
            // снятые и сорванные пломбы больше не нужны для проверки — храним только действующие
            if (s.state() != State.INSTALLED) continue;
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", s.id());
            t.putUUID("Supplier", s.supplier());
            t.putString("SupplierName", s.supplierName());
            t.putString("Dim", s.dim().location().toString());
            t.putLong("Pos", s.pos().asLong());
            t.putLong("At", s.installedAt());
            list.add(t);
        }
        tag.put("Seals", list);

        ListTag j = new ListTag();
        journal.forEach((supplier, events) -> events.forEach(e -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Supplier", supplier);
            t.putLong("T", e.gameTime());
            t.putUUID("Seal", e.sealId());
            t.putString("Dim", e.dim().location().toString());
            t.putLong("Pos", e.pos().asLong());
            t.putString("State", e.state().name());
            t.putString("By", e.by());
            j.add(t);
        }));
        tag.put("Journal", j);
        return tag;
    }

    private static SealRegistry load(CompoundTag tag, HolderLookup.Provider reg) {
        SealRegistry r = new SealRegistry();
        for (Tag t0 : tag.getList("Seals", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) t0;
            Seal s = new Seal(t.getUUID("Id"), t.getUUID("Supplier"), t.getString("SupplierName"),
                    dim(t.getString("Dim")), BlockPos.of(t.getLong("Pos")), State.INSTALLED, t.getLong("At"));
            r.seals.put(s.id(), s);
        }
        for (Tag t0 : tag.getList("Journal", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) t0;
            r.journal.computeIfAbsent(t.getUUID("Supplier"), k -> new ArrayDeque<>()).addLast(new Event(
                    t.getLong("T"), t.getUUID("Seal"), dim(t.getString("Dim")), BlockPos.of(t.getLong("Pos")),
                    State.valueOf(t.getString("State")), t.getString("By")));
        }
        return r;
    }

    private static ResourceKey<Level> dim(String id) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
    }
}
