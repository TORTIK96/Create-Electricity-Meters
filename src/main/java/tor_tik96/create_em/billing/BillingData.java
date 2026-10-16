package tor_tik96.create_em.billing;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
 * поставщик — тот, кто его опломбировал. Баланс считается в цифровых кредитах поставщика:
 * положительный — предоплата, отрицательный — долг.
 * <p>
 * Валюта поставщика — список предметов с ценой в кредитах. Оплаченные предметы
 * не копятся в терминалах, а уходят в общее хранилище (vault) поставщика.
 */
public class BillingData extends SavedData {
    private static final String NAME = "create_em_billing";
    private static final long DAY = 24000L;

    public static final int CURRENCY_SLOTS = 6;

    /** Схема оплаты абонента. */
    public static final int PAY_DEFAULT = 0, PAY_POSTPAID = 1, PAY_PREPAID = 2;

    /** Тариф поставщика: price кредитов за perFe FE. price = 0 — энергия бесплатна. */
    public static final class Tariff {
        public long price = 0;
        public long perFe = 1_000_000;
    }

    /** Настройки поставщика: имя, условия оплаты и санкции, валюта, хранилище оплаченных предметов. */
    public static final class Supplier {
        public String name = "";
        /** Расчётный период, игровых дней. */
        public int periodDays = Config.BILLING_PERIOD_DAYS.get();
        /** Через сколько дней долга урезать мощность (-1 — никогда, 0 — сразу). */
        public int limitAfterDays = Config.LIMIT_AFTER_DAYS.get();
        /** Сколько процентов лимита остаётся при урезании. */
        public int limitPercent = Config.LIMITED_PERCENT.get();
        /** Через сколько дней долга отключать (-1 — никогда, 0 — сразу). */
        public int cutAfterDays = Config.CUT_AFTER_DAYS.get();
        /** Когда следующая плановая квитанция этого поставщика; -1 — ещё не назначена. */
        public long nextBillingAt = -1;
        /** Образцы валюты (count = 1) и их цена в кредитах; пустой образец — слот не используется. */
        public final ItemStack[] currency = new ItemStack[CURRENCY_SLOTS];
        public final int[] value = new int[CURRENCY_SLOTS];
        /** Хранилище: образец (count = 1) и сколько штук лежит. */
        public final List<VaultEntry> vault = new ArrayList<>();
        /** Схема оплаты для абонентов, у которых не выбрана своя: true — предоплата. */
        public boolean prepaidDefault;
        Supplier() { Arrays.fill(currency, ItemStack.EMPTY); Arrays.fill(value, 1); }
    }

    public static final class VaultEntry {
        public final ItemStack sample;
        public long count;
        VaultEntry(ItemStack sample, long count) { this.sample = sample; this.count = count; }
    }

    public static final class Account {
        public final UUID supplier, consumer;
        public String consumerName = "";
        public long balance;            // штук валюты; < 0 — долг
        public long unbilledFe;         // потреблено, но ещё не попало в квитанцию
        public long feSincePayment;     // потреблено с последней оплаты
        public long paidAt = -1;        // игровое время последней оплаты
        public long debtSince = -1;     // с какого момента долг; -1 — долга нет
        public double lastBillKwh;      // последняя квитанция (FE; имя поля историческое)
        public long lastBillCost;
        public boolean disabled;        // отключён поставщиком удалённо
        public int powerCap;            // потолок мощности от поставщика, FE/t; 0 — без ограничения
        /** Личный тариф: ownPrice кредитов за ownPerFe FE; ownPrice < 0 — действует общий тариф поставщика. */
        public long ownPrice = -1, ownPerFe = 1_000_000;
        /** Схема оплаты: {@link #PAY_DEFAULT} — как у поставщика по умолчанию, иначе своя. */
        public int payMode = PAY_DEFAULT;
        int notifiedStage;

        Account(UUID supplier, UUID consumer) { this.supplier = supplier; this.consumer = consumer; }

    }

    /**
     * Стадия счёта по условиям его поставщика: сколько дней висит долг и когда
     * поставщик урезает мощность или отключает (-1 — никогда, 0 — сразу).
     */
    public BillingStage stage(Account a, long now) {
        // предоплата: деньги кончились — сразу отключение, долгов и отсрочек нет
        if (isPrepaid(a)) return price(a) > 0 && a.balance <= 0 ? BillingStage.CUT : BillingStage.NORMAL;
        if (a.debtSince < 0 || a.balance >= 0) return BillingStage.NORMAL;
        Supplier s = supplier(a.supplier);
        long days = (now - a.debtSince) / DAY;
        if (s.cutAfterDays >= 0 && days >= s.cutAfterDays) return BillingStage.CUT;
        if (s.limitAfterDays >= 0 && days >= s.limitAfterDays) return BillingStage.LIMITED;
        return BillingStage.WARNING;
    }

    private final Map<UUID, Tariff> tariffs = new HashMap<>();
    private final Map<String, Account> accounts = new HashMap<>();
    private final Map<UUID, Supplier> suppliers = new HashMap<>();

    public static BillingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BillingData::new, BillingData::load), NAME);
    }

    private static String key(UUID supplier, UUID consumer) { return supplier + "|" + consumer; }

    public Tariff tariff(UUID supplier) { return tariffs.computeIfAbsent(supplier, k -> new Tariff()); }

    public void setTariff(UUID supplier, long price, long perFe) {
        Tariff t = tariff(supplier);
        t.price = Math.max(0, price);
        t.perFe = Math.max(1, perFe);
        setDirty();
    }

    /** Действующий тариф абонента: личный, если задан, иначе общий. */
    public long price(Account a) { return a.ownPrice >= 0 ? a.ownPrice : tariff(a.supplier).price; }
    public long perFe(Account a) { return a.ownPrice >= 0 ? Math.max(1, a.ownPerFe) : Math.max(1, tariff(a.supplier).perFe); }
    public boolean hasOwnTariff(Account a) { return a.ownPrice >= 0; }

    /** Личный тариф; price < 0 — вернуть общий. */
    public void setOwnTariff(Account a, long price, long perFe) {
        a.ownPrice = price < 0 ? -1 : price;
        a.ownPerFe = Math.max(1, perFe);
        setDirty();
    }

    public boolean isPrepaid(Account a) {
        return a.payMode == PAY_DEFAULT ? supplier(a.supplier).prepaidDefault : a.payMode == PAY_PREPAID;
    }

    /** Сменить схему оплаты абонента. При переходе на постоплату с минусом — это долг с этого момента. */
    public void setPayMode(Account a, int mode, long now) {
        a.payMode = Math.max(PAY_DEFAULT, Math.min(PAY_PREPAID, mode));
        if (!isPrepaid(a) && a.balance < 0 && a.debtSince < 0) a.debtSince = now;
        if (isPrepaid(a)) chargePrepaid(a);
        setDirty();
    }

    public void setPrepaidDefault(UUID supplierId, boolean prepaid, long now) {
        supplier(supplierId).prepaidDefault = prepaid;
        for (Account a : accounts.values())
            if (a.supplier.equals(supplierId) && a.payMode == PAY_DEFAULT) {
                if (!prepaid && a.balance < 0 && a.debtSince < 0) a.debtSince = now;
                if (prepaid) chargePrepaid(a);
            }
        setDirty();
    }

    /**
     * Предоплата: списываем за потреблённое сразу (вызывается раз в секунду из отчёта счётчика).
     * Списывается только то, что набрало целый кредит; остаток ждёт следующей секунды.
     */
    private void chargePrepaid(Account a) {
        long price = price(a), per = perFe(a);
        if (price <= 0) { a.unbilledFe = 0; return; }
        long cost = (long) Math.floor(a.unbilledFe / (double) per * price);
        if (cost <= 0) return;
        long fe = Math.min(a.unbilledFe, (long) Math.floor(cost * (double) per / price));
        a.unbilledFe -= fe;
        a.balance -= cost;
        a.lastBillKwh = fe;
        a.lastBillCost = cost;
    }

    /** Поставщик вручную задаёт баланс абонента. Долг отсчитывается заново от этого момента. */
    public void setBalance(Account a, long balance, long now) {
        a.balance = balance;
        if (balance >= 0) a.debtSince = -1;
        else if (a.debtSince < 0) a.debtSince = now;
        setDirty();
    }

    public Supplier supplier(UUID id) { return suppliers.computeIfAbsent(id, k -> new Supplier()); }

    /** Задать образец валюты в слоте (count = 1) или очистить его пустым стеком. */
    public void setCurrency(UUID supplierId, int slot, ItemStack sample) {
        Supplier s = supplier(supplierId);
        s.currency[slot] = sample.isEmpty() ? ItemStack.EMPTY : sample.copyWithCount(1);
        if (s.value[slot] <= 0) s.value[slot] = 1;
        setDirty();
    }

    public void setCurrencyValue(UUID supplierId, int slot, int value) {
        supplier(supplierId).value[slot] = Math.max(1, value);
        setDirty();
    }

    /** Цена одного предмета в кредитах или 0, если поставщик такой валюты не принимает. */
    public int valueOf(UUID supplierId, ItemStack stack) {
        Supplier s = suppliers.get(supplierId);
        if (s == null || stack.isEmpty()) return 0;
        for (int i = 0; i < CURRENCY_SLOTS; i++)
            if (!s.currency[i].isEmpty() && ItemStack.isSameItemSameComponents(s.currency[i], stack)) return s.value[i];
        return 0;
    }

    public void addToVault(UUID supplierId, ItemStack stack) {
        if (stack.isEmpty()) return;
        Supplier s = supplier(supplierId);
        for (VaultEntry e : s.vault)
            if (ItemStack.isSameItemSameComponents(e.sample, stack)) { e.count += stack.getCount(); setDirty(); return; }
        s.vault.add(new VaultEntry(stack.copyWithCount(1), stack.getCount()));
        setDirty();
    }

    /** Достать из хранилища до одного стака; пустой стек — нечего доставать. */
    public ItemStack takeFromVault(UUID supplierId, int index) {
        Supplier s = suppliers.get(supplierId);
        if (s == null || index < 0 || index >= s.vault.size()) return ItemStack.EMPTY;
        VaultEntry e = s.vault.get(index);
        int n = (int) Math.min(e.count, e.sample.getMaxStackSize());
        e.count -= n;
        if (e.count <= 0) s.vault.remove(index);
        setDirty();
        return e.sample.copyWithCount(n);
    }

    public Account account(UUID supplier, UUID consumer) {
        return accounts.computeIfAbsent(key(supplier, consumer), k -> { setDirty(); return new Account(supplier, consumer); });
    }

    @Nullable public Account findAccount(UUID supplier, UUID consumer) { return accounts.get(key(supplier, consumer)); }

    /** Счета поставщика, по имени абонента — порядок стабилен, по нему выбирают в панели. */
    public List<Account> accountsOf(UUID supplier) {
        return accounts.values().stream().filter(a -> a.supplier.equals(supplier))
                .sorted(Comparator.comparing((Account a) -> a.consumerName.toLowerCase()).thenComparing(a -> a.consumer))
                .toList();
    }

    public void changed() { setDirty(); }

    /** Квитанция одному абоненту прямо сейчас. */
    public int billAccount(MinecraftServer server, Account account) {
        long now = server.overworld().getGameTime();
        return billOne(server, now, account) ? 1 : 0;
    }

    /** Счётчик сообщает о потреблении (раз в секунду). */
    public void report(UUID supplier, UUID consumer, String consumerName, long fe) {
        if (fe <= 0) return;
        Account a = account(supplier, consumer);
        if (!consumerName.isEmpty()) a.consumerName = consumerName;
        a.unbilledFe += fe;
        a.feSincePayment += fe;
        if (isPrepaid(a)) chargePrepaid(a);
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

    /** Вызывается раз в секунду из серверного тика. У каждого поставщика свой период. */
    void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        Set<UUID> active = new HashSet<>();
        for (Account a : accounts.values()) active.add(a.supplier);
        for (UUID id : active) {
            Supplier s = supplier(id);
            long period = Math.max(1, s.periodDays) * DAY;
            if (s.nextBillingAt < 0) { s.nextBillingAt = now + period; setDirty(); }
            if (now >= s.nextBillingAt) {
                bill(server, now, id);
                s.nextBillingAt = now + period;
                setDirty();
            }
        }
        notifyStages(server, now);
    }

    /** Когда следующая плановая квитанция поставщика (игровое время), -1 — ещё не назначена. */
    public long getNextBillingAt(UUID supplier) { return supplier(supplier).nextBillingAt; }

    /** Секунд реального времени до плановой квитанции поставщика. */
    public int secondsToBill(UUID supplier, long now) {
        long next = getNextBillingAt(supplier);
        return next < 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.max(0, (next - now) / 20));
    }

    /** Поставщик меняет условия. Новый период начинает отсчёт заново. */
    public void setTerms(UUID supplierId, int periodDays, int limitAfterDays, int limitPercent, int cutAfterDays, long now) {
        Supplier s = supplier(supplierId);
        int newPeriod = Math.max(1, Math.min(365, periodDays));
        if (newPeriod != s.periodDays) s.nextBillingAt = now + newPeriod * DAY;
        s.periodDays = newPeriod;
        s.limitAfterDays = Math.max(-1, Math.min(365, limitAfterDays));
        s.limitPercent = Math.max(0, Math.min(100, limitPercent));
        s.cutAfterDays = Math.max(-1, Math.min(365, cutAfterDays));
        setDirty();
    }

    /** Запомнить имя поставщика для вкладок терминала. */
    public void setSupplierName(UUID supplierId, String name) {
        Supplier s = supplier(supplierId);
        if (!name.equals(s.name)) { s.name = name; setDirty(); }
    }

    /** Поставщики, у которых есть счёт у этого абонента. */
    public List<UUID> suppliersOf(UUID consumer) {
        List<UUID> out = new ArrayList<>();
        for (Account a : accounts.values()) if (a.consumer.equals(consumer) && !out.contains(a.supplier)) out.add(a.supplier);
        out.sort(Comparator.comparing((UUID id) -> supplier(id).name.toLowerCase()));
        return out;
    }

    /**
     * Выставить квитанции вручную: всем абонентам поставщика (supplier != null) или вообще всем.
     * Плановый период при этом не сдвигается. Возвращает число выставленных квитанций.
     */
    public int billNow(MinecraftServer server, @Nullable UUID supplier) {
        return bill(server, server.overworld().getGameTime(), supplier);
    }

    /** Выставить квитанции; supplier == null — всем счетам. */
    private int bill(MinecraftServer server, long now, @Nullable UUID onlySupplier) {
        int issued = 0;
        for (Account a : accounts.values()) {
            if (onlySupplier != null && !onlySupplier.equals(a.supplier)) continue;
            if (billOne(server, now, a)) issued++;
        }
        setDirty();
        return issued;
    }

    private boolean billOne(MinecraftServer server, long now, Account a) {
        if (isPrepaid(a)) { chargePrepaid(a); return false; } // при предоплате списывается сразу, квитанций нет
        Tariff t = new Tariff();
        t.price = price(a);
        t.perFe = perFe(a);
        long billedFe;
        long cost;
        if (t.price <= 0) {                 // бесплатно — просто обнуляем
            cost = 0;
            billedFe = a.unbilledFe;
            a.unbilledFe = 0;
        } else {
            cost = (long) Math.floor(a.unbilledFe / (double) t.perFe * t.price);
            // в квитанцию идёт только то, что набрало целый кредит; остаток ждёт следующего периода
            billedFe = Math.min(a.unbilledFe, (long) Math.floor(cost * (double) t.perFe / t.price));
            a.unbilledFe -= billedFe;
        }
        if (billedFe <= 0 && cost <= 0) return false;
        double kwh = billedFe;
        a.lastBillKwh = kwh;
        a.lastBillCost = cost;
        a.balance -= cost;
        if (a.balance < 0 && a.debtSince < 0) a.debtSince = now;
        setDirty();

        ServerPlayer p = server.getPlayerList().getPlayer(a.consumer);
        if (p != null)
            p.sendSystemMessage(Component.translatable("create_em.bill.notice",
                    tor_tik96.create_em.EMFormat.fe(billedFe), cost, a.balance).withStyle(a.balance < 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        return true;
    }

    /** Сообщить абоненту и поставщику, когда стадия долга меняется. */
    private void notifyStages(MinecraftServer server, long now) {
        for (Account a : accounts.values()) {
            BillingStage st = stage(a, now);
            if (st.ordinal() == a.notifiedStage) continue;
            a.notifiedStage = st.ordinal();
            setDirty();
            if (st == BillingStage.NORMAL) continue;
            String key = isPrepaid(a) ? "create_em.stage.notice.prepaid_empty" : "create_em.stage.notice." + st.name().toLowerCase();
            Component msg = Component.translatable(key, -a.balance)
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
            c.putLong("Price", t.price);
            c.putLong("PerFe", t.perFe);
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
            c.putBoolean("Disabled", a.disabled);
            c.putInt("Cap", a.powerCap);
            c.putLong("OwnPrice", a.ownPrice);
            c.putLong("OwnPerFe", a.ownPerFe);
            c.putInt("PayMode", a.payMode);
            as.add(c);
        }
        tag.put("Accounts", as);

        ListTag ss = new ListTag();
        suppliers.forEach((id, sup) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("Supplier", id);
            ListTag cur = new ListTag();
            for (int i = 0; i < CURRENCY_SLOTS; i++) {
                CompoundTag e = new CompoundTag();
                e.put("Item", sup.currency[i].saveOptional(reg));
                e.putInt("Value", sup.value[i]);
                cur.add(e);
            }
            c.put("Currency", cur);
            ListTag vault = new ListTag();
            for (VaultEntry v : sup.vault) {
                CompoundTag e = new CompoundTag();
                e.put("Item", v.sample.saveOptional(reg));
                e.putLong("Count", v.count);
                vault.add(e);
            }
            c.put("Vault", vault);
            c.putString("Name", sup.name);
            c.putInt("Period", sup.periodDays);
            c.putInt("LimitAfter", sup.limitAfterDays);
            c.putInt("LimitPercent", sup.limitPercent);
            c.putInt("CutAfter", sup.cutAfterDays);
            c.putLong("NextBilling", sup.nextBillingAt);
            c.putBoolean("PrepaidDefault", sup.prepaidDefault);
            ss.add(c);
        });
        tag.put("Suppliers", ss);

        return tag;
    }

    private static BillingData load(CompoundTag tag, HolderLookup.Provider reg) {
        BillingData d = new BillingData();
        for (Tag t0 : tag.getList("Tariffs", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t0;
            Tariff t = new Tariff();
            t.price = c.getLong("Price");
            // старые сохранения: тариф был за кВт·ч
            t.perFe = c.contains("PerFe") ? Math.max(1, c.getLong("PerFe"))
                    : Math.max(1, c.getInt("PerKwh")) * Config.FE_PER_KWH.get();
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
            a.disabled = c.getBoolean("Disabled");
            a.powerCap = c.getInt("Cap");
            a.ownPrice = c.contains("OwnPrice") ? c.getLong("OwnPrice") : -1;
            a.ownPerFe = c.contains("OwnPerFe") ? Math.max(1, c.getLong("OwnPerFe")) : 1_000_000;
            a.payMode = c.getInt("PayMode");
            d.accounts.put(key(a.supplier, a.consumer), a);
        }
        for (Tag t0 : tag.getList("Suppliers", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t0;
            Supplier sup = d.supplier(c.getUUID("Supplier"));
            sup.name = c.getString("Name");
            if (c.contains("Period")) {
                sup.periodDays = Math.max(1, c.getInt("Period"));
                sup.limitAfterDays = c.getInt("LimitAfter");
                sup.limitPercent = c.getInt("LimitPercent");
                sup.cutAfterDays = c.getInt("CutAfter");
            }
            sup.nextBillingAt = c.contains("NextBilling") ? c.getLong("NextBilling") : -1;
            sup.prepaidDefault = c.getBoolean("PrepaidDefault");
            ListTag cur = c.getList("Currency", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(CURRENCY_SLOTS, cur.size()); i++) {
                CompoundTag e = cur.getCompound(i);
                sup.currency[i] = ItemStack.parseOptional(reg, e.getCompound("Item"));
                sup.value[i] = Math.max(1, e.getInt("Value"));
            }
            for (Tag v0 : c.getList("Vault", Tag.TAG_COMPOUND)) {
                CompoundTag e = (CompoundTag) v0;
                ItemStack st = ItemStack.parseOptional(reg, e.getCompound("Item"));
                if (!st.isEmpty()) sup.vault.add(new VaultEntry(st, e.getLong("Count")));
            }
        }

        return d;
    }
}
