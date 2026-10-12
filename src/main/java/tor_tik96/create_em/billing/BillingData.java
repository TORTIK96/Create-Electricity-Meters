package tor_tik96.create_em.billing;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.Config;

import java.util.*;

/**
 * Расчёты за энергию: тарифы поставщиков и лицевые счета абонентов.
 * Живёт в мире (data/create_em_billing.dat), поэтому начисления и долги не зависят
 * от того, загружены ли чанки со счётчиками.
 * <p>
 * Лицевой счёт — пара (поставщик, абонент). Абонент — тот, кто поставил счётчик,
 * поставщик — тот, кто его опломбировал. Баланс считается в штуках валюты поставщика:
 * положительный — предоплата, отрицательный — долг.
 */
public class BillingData extends SavedData {
    private static final String NAME = "create_em_billing";
    private static final long DAY = 24000L;

    /** Тариф поставщика: price штук валюты за perKwh кВт·ч. price = 0 — энергия бесплатна. */
    public static final class Tariff {
        public int price = 0;
        public int perKwh = 1;
    }

    public static final class Account {
        public final UUID supplier, consumer;
        public String consumerName = "";
        public long balance;            // штук валюты; < 0 — долг
        public long unbilledFe;         // потреблено, но ещё не попало в квитанцию
        public long feSincePayment;     // потреблено с последней оплаты
        public long paidAt = -1;        // игровое время последней оплаты
        public long debtSince = -1;     // с какого момента долг; -1 — долга нет
        public double lastBillKwh;      // последняя квитанция
        public long lastBillCost;
        int notifiedStage;

        Account(UUID supplier, UUID consumer) { this.supplier = supplier; this.consumer = consumer; }

        public BillingStage stage(long now) {
            if (debtSince < 0 || balance >= 0) return BillingStage.NORMAL;
            long days = (now - debtSince) / DAY;
            if (days >= Config.CUT_AFTER_DAYS.get()) return BillingStage.CUT;
            if (days >= Config.LIMIT_AFTER_DAYS.get()) return BillingStage.LIMITED;
            return BillingStage.WARNING;
        }
    }

    private final Map<UUID, Tariff> tariffs = new HashMap<>();
    private final Map<String, Account> accounts = new HashMap<>();
    private long nextBillingAt = -1;

    public static BillingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BillingData::new, BillingData::load), NAME);
    }

    private static String key(UUID supplier, UUID consumer) { return supplier + "|" + consumer; }

    public Tariff tariff(UUID supplier) { return tariffs.computeIfAbsent(supplier, k -> new Tariff()); }

    public void setTariff(UUID supplier, int price, int perKwh) {
        Tariff t = tariff(supplier);
        t.price = Math.max(0, price);
        t.perKwh = Math.max(1, perKwh);
        setDirty();
    }

    public Account account(UUID supplier, UUID consumer) {
        return accounts.computeIfAbsent(key(supplier, consumer), k -> { setDirty(); return new Account(supplier, consumer); });
    }

    @Nullable public Account findAccount(UUID supplier, UUID consumer) { return accounts.get(key(supplier, consumer)); }

    public List<Account> accountsOf(UUID supplier) {
        return accounts.values().stream().filter(a -> a.supplier.equals(supplier)).toList();
    }

    /** Счётчик сообщает о потреблении (раз в секунду). */
    public void report(UUID supplier, UUID consumer, String consumerName, long fe) {
        if (fe <= 0) return;
        Account a = account(supplier, consumer);
        if (!consumerName.isEmpty()) a.consumerName = consumerName;
        a.unbilledFe += fe;
        a.feSincePayment += fe;
        setDirty();
    }

    /** Оплата: пополнение баланса на amount штук валюты. */
    public void pay(Account a, long amount, long now) {
        a.balance += amount;
        a.feSincePayment = 0;
        a.paidAt = now;
        if (a.balance >= 0) a.debtSince = -1;
        setDirty();
    }

    /** Отметить оплату без денег (креатив / админ). */
    public void markPaid(Account a, long now) {
        a.feSincePayment = 0;
        a.paidAt = now;
        setDirty();
    }

    // ---------- расчётный период ----------

    /** Вызывается раз в секунду из серверного тика. */
    void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        long period = Config.BILLING_PERIOD_DAYS.get() * DAY;
        if (nextBillingAt < 0) { nextBillingAt = now + period; setDirty(); }
        if (now >= nextBillingAt) {
            bill(server, now, null);
            nextBillingAt = now + period;
            setDirty();
        }
        notifyStages(server, now);
    }

    /** Когда будет следующая плановая квитанция (игровое время), -1 — ещё не назначена. */
    public long getNextBillingAt() { return nextBillingAt; }

    /**
     * Выставить квитанции вручную: всем абонентам поставщика (supplier != null) или вообще всем.
     * Плановый период при этом не сдвигается. Возвращает число выставленных квитанций.
     */
    public int billNow(MinecraftServer server, @Nullable UUID supplier) {
        return bill(server, server.overworld().getGameTime(), supplier);
    }

    /** Выставить квитанции; supplier == null — всем счетам. */
    private int bill(MinecraftServer server, long now, @Nullable UUID onlySupplier) {
        long fePerKwh = Config.FE_PER_KWH.get();
        int issued = 0;
        for (Account a : accounts.values()) {
            if (onlySupplier != null && !onlySupplier.equals(a.supplier)) continue;
            Tariff t = tariff(a.supplier);
            double kwh = a.unbilledFe / (double) fePerKwh;
            long cost;
            if (t.price <= 0) {                 // бесплатно — просто обнуляем
                cost = 0;
                a.unbilledFe = 0;
            } else {
                cost = (long) Math.floor(kwh * t.price / t.perKwh);
                // в квитанцию идёт только то, что оплачено целыми штуками; остаток ждёт следующего периода
                long billedFe = (long) Math.floor(cost * (double) t.perKwh * fePerKwh / t.price);
                a.unbilledFe = Math.max(0, a.unbilledFe - billedFe);
                kwh = billedFe / (double) fePerKwh;
            }
            if (kwh <= 0 && cost <= 0) continue;
            issued++;
            a.lastBillKwh = kwh;
            a.lastBillCost = cost;
            a.balance -= cost;
            if (a.balance < 0 && a.debtSince < 0) a.debtSince = now;

            ServerPlayer p = server.getPlayerList().getPlayer(a.consumer);
            if (p != null)
                p.sendSystemMessage(Component.translatable("create_em.bill.notice",
                        String.format("%.2f", kwh), cost, a.balance).withStyle(a.balance < 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
        setDirty();
        return issued;
    }

    /** Сообщить абоненту и поставщику, когда стадия долга меняется. */
    private void notifyStages(MinecraftServer server, long now) {
        for (Account a : accounts.values()) {
            BillingStage st = a.stage(now);
            if (st.ordinal() == a.notifiedStage) continue;
            a.notifiedStage = st.ordinal();
            setDirty();
            if (st == BillingStage.NORMAL) continue;
            Component msg = Component.translatable("create_em.stage.notice." + st.name().toLowerCase(), -a.balance)
                    .withStyle(st == BillingStage.CUT ? ChatFormatting.RED : ChatFormatting.GOLD);
            ServerPlayer c = server.getPlayerList().getPlayer(a.consumer);
            if (c != null) c.sendSystemMessage(msg);
            ServerPlayer s = server.getPlayerList().getPlayer(a.supplier);
            if (s != null && st == BillingStage.CUT)
                s.sendSystemMessage(Component.translatable("create_em.stage.notice.supplier_cut", a.consumerName).withStyle(ChatFormatting.GRAY));
        }
    }

    // ---------- NBT ----------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag ts = new ListTag();
        tariffs.forEach((s, t) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("Supplier", s);
            c.putInt("Price", t.price);
            c.putInt("PerKwh", t.perKwh);
            ts.add(c);
        });
        tag.put("Tariffs", ts);

        ListTag as = new ListTag();
        for (Account a : accounts.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Supplier", a.supplier);
            c.putUUID("Consumer", a.consumer);
            c.putString("Name", a.consumerName);
            c.putLong("Balance", a.balance);
            c.putLong("Unbilled", a.unbilledFe);
            c.putLong("SincePay", a.feSincePayment);
            c.putLong("PaidAt", a.paidAt);
            c.putLong("DebtSince", a.debtSince);
            c.putDouble("LastKwh", a.lastBillKwh);
            c.putLong("LastCost", a.lastBillCost);
            c.putInt("Notified", a.notifiedStage);
            as.add(c);
        }
        tag.put("Accounts", as);
        tag.putLong("NextBilling", nextBillingAt);
        return tag;
    }

    private static BillingData load(CompoundTag tag, HolderLookup.Provider reg) {
        BillingData d = new BillingData();
        for (Tag t0 : tag.getList("Tariffs", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t0;
            Tariff t = new Tariff();
            t.price = c.getInt("Price");
            t.perKwh = Math.max(1, c.getInt("PerKwh"));
            d.tariffs.put(c.getUUID("Supplier"), t);
        }
        for (Tag t0 : tag.getList("Accounts", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t0;
            Account a = new Account(c.getUUID("Supplier"), c.getUUID("Consumer"));
            a.consumerName = c.getString("Name");
            a.balance = c.getLong("Balance");
            a.unbilledFe = c.getLong("Unbilled");
            a.feSincePayment = c.getLong("SincePay");
            a.paidAt = c.getLong("PaidAt");
            a.debtSince = c.getLong("DebtSince");
            a.lastBillKwh = c.getDouble("LastKwh");
            a.lastBillCost = c.getLong("LastCost");
            a.notifiedStage = c.getInt("Notified");
            d.accounts.put(key(a.supplier, a.consumer), a);
        }
        d.nextBillingAt = tag.contains("NextBilling") ? tag.getLong("NextBilling") : -1;
        return d;
    }
}
