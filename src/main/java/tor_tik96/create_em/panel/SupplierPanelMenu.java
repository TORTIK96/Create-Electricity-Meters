package tor_tik96.create_em.panel;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.billing.BillingData;
import tor_tik96.create_em.line.TransformerBlockEntity;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static tor_tik96.create_em.billing.BillingData.CURRENCY_SLOTS;

/**
 * Панель поставщика в умном (латунном) трансформаторе: абоненты, валюта, касса.
 * <p>
 * Слоты валюты — «призрачные», как фильтры Create: клик предметом ставит его копию,
 * сам предмет остаётся у игрока; клик пустой рукой очищает слот.
 * Числа (баланс, потолок, тариф, цены) приходят из полей ввода пакетом {@link ValuePayload},
 * действия — кнопками меню, списки и текущие значения — пакетом {@link PanelSyncPayload}.
 */
public class SupplierPanelMenu extends AbstractContainerMenu implements ValuePayload.Receiver {
    // кнопки
    public static final int BTN_TOGGLE = 1, BTN_BILL_ONE = 6, BTN_BILL_ALL = 7,
            BTN_PAYMODE = 8, BTN_OWN_RESET = 9, BTN_PREPAID_DEFAULT = 10,
            SELECT_BASE = 1000, VAULT_BASE = 2000;
    // поля ввода
    public static final int F_BALANCE = 1, F_CAP = 2, F_PRICE = 3, F_PER_FE = 4,
            F_PERIOD = 5, F_LIMIT_AFTER = 6, F_LIMIT_PERCENT = 7, F_CUT_AFTER = 8, F_VALUE0 = 10,
            F_OWN_PRICE = 20, F_OWN_PER_FE = 21;

    // раскладка (общая с экраном)
    public static final int W = 256, H = 256;
    public static final int CUR_X0 = 8, CUR_COL = 124, CUR_Y0 = 78, CUR_ROW = 20;
    public static final int INV_X = 47, INV_Y = 150, HOTBAR_Y = 208;

    @Nullable private final UUID supplier;                // сервер
    @Nullable private final TransformerBlockEntity transformer;
    private final Player player;
    private final Container ghosts;
    private int syncTimer;
    @Nullable private UUID selected;                      // сервер: выбранный абонент

    // клиент
    public int tab = 1;                                    // 0 — абоненты, 1 — валюта, 2 — условия, 3 — касса
    @Nullable private PanelSyncPayload clientData;

    /** Клиент. */
    public SupplierPanelMenu(int id, Inventory inv) {
        this(id, inv, null, null, new SimpleContainer(CURRENCY_SLOTS));
    }

    /** Сервер. */
    public static SupplierPanelMenu server(int id, Inventory inv, TransformerBlockEntity be) {
        UUID owner = be.getOwner();
        BillingData bd = BillingData.get(inv.player.getServer());
        bd.setSupplierName(owner, inv.player.getGameProfile().getName());
        BillingData.Supplier sup = bd.supplier(owner);
        SimpleContainer ghosts = new SimpleContainer(CURRENCY_SLOTS);
        for (int i = 0; i < CURRENCY_SLOTS; i++) ghosts.setItem(i, sup.currency[i].copy());
        return new SupplierPanelMenu(id, inv, owner, be, ghosts);
    }

    private SupplierPanelMenu(int id, Inventory inv, @Nullable UUID supplier, @Nullable TransformerBlockEntity be, Container ghosts) {
        super(EMRegistry.SUPPLIER_PANEL_MENU.get(), id);
        this.supplier = supplier;
        this.transformer = be;
        this.player = inv.player;
        this.ghosts = ghosts;
        for (int i = 0; i < CURRENCY_SLOTS; i++)
            addSlot(new GhostSlot(ghosts, i, CUR_X0 + 2 + (i % 2) * CUR_COL, CUR_Y0 + (i / 2) * CUR_ROW));
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new TabSlot(inv, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new TabSlot(inv, c, INV_X + c * 18, HOTBAR_Y));
    }

    // ---------- слоты ----------
    /** Призрачный слот валюты: предмет в него не кладётся по-настоящему. */
    public class GhostSlot extends Slot {
        GhostSlot(Container c, int i, int x, int y) { super(c, i, x, y); }
        @Override public boolean mayPlace(ItemStack s) { return false; }
        @Override public boolean mayPickup(Player p) { return false; }
        @Override public boolean isActive() { return tab == 1; }
    }

    class TabSlot extends Slot {
        TabSlot(Container c, int i, int x, int y) { super(c, i, x, y); }
        @Override public boolean isActive() { return tab == 1; }
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player p) {
        if (slotId >= 0 && slotId < CURRENCY_SLOTS) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                ItemStack carried = getCarried();
                setGhost(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return; // сам предмет никуда не перемещается
        }
        if (type == ClickType.QUICK_MOVE && slotId >= CURRENCY_SLOTS && slotId < slots.size()) {
            // Shift-клик по предмету в инвентаре — копия в первый свободный слот валюты
            ItemStack st = slots.get(slotId).getItem();
            if (st.isEmpty()) return;
            for (int i = 0; i < CURRENCY_SLOTS; i++)
                if (ItemStack.isSameItemSameComponents(ghosts.getItem(i), st)) return;
            for (int i = 0; i < CURRENCY_SLOTS; i++)
                if (ghosts.getItem(i).isEmpty()) { setGhost(i, st.copyWithCount(1)); return; }
            return;
        }
        super.clicked(slotId, button, type, p);
    }

    private void setGhost(int i, ItemStack stack) {
        ghosts.setItem(i, stack);
        if (supplier != null && player instanceof ServerPlayer sp)
            BillingData.get(sp.getServer()).setCurrency(supplier, i, stack);
    }

    @Override public boolean canDragTo(Slot slot) { return !(slot instanceof GhostSlot); }
    @Override public boolean canTakeItemForPickAll(ItemStack s, Slot slot) { return !(slot instanceof GhostSlot); }
    @Override public ItemStack quickMoveStack(Player p, int index) { return ItemStack.EMPTY; }

    private boolean isSupplier(Player p) { return supplier != null && supplier.equals(p.getUUID()); }

    // ---------- кнопки ----------
    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (!(p instanceof ServerPlayer sp) || !isSupplier(p)) return false;
        MinecraftServer server = sp.getServer();
        BillingData bd = BillingData.get(server);
        syncTimer = 0;

        if (id >= VAULT_BASE) {
            ItemStack got = bd.takeFromVault(supplier, id - VAULT_BASE);
            if (!got.isEmpty() && !sp.getInventory().add(got)) sp.drop(got, false);
            return true;
        }
        if (id >= SELECT_BASE) {
            List<BillingData.Account> list = bd.accountsOf(supplier);
            int i = id - SELECT_BASE;
            selected = i < list.size() ? list.get(i).consumer : null;
            return true;
        }
        if (id == BTN_PREPAID_DEFAULT) {
            bd.setPrepaidDefault(supplier, !bd.supplier(supplier).prepaidDefault, server.overworld().getGameTime());
            return true;
        }
        if (id == BTN_BILL_ALL) {
            sp.displayClientMessage(Component.translatable("create_em.bill.manual", bd.billNow(server, supplier)), true);
            return true;
        }
        BillingData.Account a = selected == null ? null : bd.findAccount(supplier, selected);
        if (a == null) return false;
        switch (id) {
            case BTN_TOGGLE -> {
                a.disabled = !a.disabled;
                bd.changed();
                ServerPlayer c = server.getPlayerList().getPlayer(a.consumer);
                if (c != null) c.sendSystemMessage(Component.translatable(a.disabled ? "create_em.panel.notice_off" : "create_em.panel.notice_on")
                        .withStyle(a.disabled ? ChatFormatting.RED : ChatFormatting.GREEN));
            }
            case BTN_BILL_ONE -> sp.displayClientMessage(Component.translatable("create_em.bill.manual", bd.billAccount(server, a)), true);
            case BTN_PAYMODE -> {
                bd.setPayMode(a, (a.payMode + 1) % 3, server.overworld().getGameTime());
                ServerPlayer c = server.getPlayerList().getPlayer(a.consumer);
                if (c != null) c.sendSystemMessage(Component.translatable(bd.isPrepaid(a) ? "create_em.panel.notice_prepaid" : "create_em.panel.notice_postpaid")
                        .withStyle(ChatFormatting.GRAY));
            }
            case BTN_OWN_RESET -> bd.setOwnTariff(a, -1, a.ownPerFe);
            default -> { return false; }
        }
        return true;
    }

    // ---------- поля ввода ----------
    @Override
    public void receiveValue(ServerPlayer sp, int field, UUID target, long value) {
        if (!isSupplier(sp)) return;
        BillingData bd = BillingData.get(sp.getServer());
        long now = sp.getServer().overworld().getGameTime();
        syncTimer = 0;
        if (field >= F_VALUE0 && field < F_VALUE0 + CURRENCY_SLOTS) {
            bd.setCurrencyValue(supplier, field - F_VALUE0, (int) Math.max(1, Math.min(Integer.MAX_VALUE, value)));
            return;
        }
        BillingData.Tariff t = bd.tariff(supplier);
        BillingData.Supplier sup = bd.supplier(supplier);
        int iv = (int) Math.max(-1, Math.min(Integer.MAX_VALUE, value));
        switch (field) {
            case F_PERIOD -> bd.setTerms(supplier, iv, sup.limitAfterDays, sup.limitPercent, sup.cutAfterDays, now);
            case F_LIMIT_AFTER -> bd.setTerms(supplier, sup.periodDays, iv, sup.limitPercent, sup.cutAfterDays, now);
            case F_LIMIT_PERCENT -> bd.setTerms(supplier, sup.periodDays, sup.limitAfterDays, iv, sup.cutAfterDays, now);
            case F_CUT_AFTER -> bd.setTerms(supplier, sup.periodDays, sup.limitAfterDays, sup.limitPercent, iv, now);
            case F_PRICE -> bd.setTariff(supplier, value, t.perFe);
            case F_PER_FE -> bd.setTariff(supplier, t.price, value);
            case F_OWN_PRICE, F_OWN_PER_FE -> {
                BillingData.Account a = bd.findAccount(supplier, target);
                if (a == null) return;
                // первое изменение берёт недостающую половину из общего тарифа
                long price = bd.hasOwnTariff(a) ? a.ownPrice : t.price;
                long per = bd.hasOwnTariff(a) ? a.ownPerFe : t.perFe;
                if (field == F_OWN_PRICE) price = Math.max(0, value);
                else per = Math.max(1, value);
                bd.setOwnTariff(a, price, per);
                ServerPlayer c = sp.getServer().getPlayerList().getPlayer(a.consumer);
                if (c != null) c.sendSystemMessage(Component.translatable("create_em.panel.notice_tariff", price,
                        tor_tik96.create_em.EMFormat.fe(per)).withStyle(ChatFormatting.GRAY));
            }
            case F_BALANCE, F_CAP -> {
                BillingData.Account a = bd.findAccount(supplier, target);
                if (a == null) return;
                if (field == F_BALANCE) {
                    bd.setBalance(a, value, now);
                    ServerPlayer c = sp.getServer().getPlayerList().getPlayer(a.consumer);
                    if (c != null) c.sendSystemMessage(Component.translatable("create_em.panel.notice_balance", value).withStyle(ChatFormatting.GRAY));
                } else {
                    a.powerCap = (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
                    bd.changed();
                }
            }
            default -> {}
        }
    }

    // ---------- синхронизация ----------
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer sp) || supplier == null || --syncTimer > 0) return;
        syncTimer = 20;
        BillingData bd = BillingData.get(sp.getServer());
        long now = sp.getServer().overworld().getGameTime();
        List<PanelSyncPayload.AccountView> acc = new ArrayList<>();
        for (BillingData.Account a : bd.accountsOf(supplier))
            acc.add(new PanelSyncPayload.AccountView(a.consumer, a.consumerName, a.balance, bd.stage(a, now).ordinal(),
                    a.disabled, a.powerCap, a.feSincePayment, a.unbilledFe,
                    bd.hasOwnTariff(a) ? a.ownPrice : -1, a.ownPerFe, a.payMode, bd.isPrepaid(a)));
        BillingData.Supplier sup = bd.supplier(supplier);
        List<PanelSyncPayload.VaultView> v = new ArrayList<>();
        for (BillingData.VaultEntry e : sup.vault) v.add(new PanelSyncPayload.VaultView(e.sample, e.count));
        BillingData.Tariff t = bd.tariff(supplier);
        PacketDistributor.sendToPlayer(sp, new PanelSyncPayload(containerId, acc, v, t.price, t.perFe, sup.value.clone(),
                bd.secondsToBill(supplier, now), sup.periodDays, sup.limitAfterDays, sup.limitPercent, sup.cutAfterDays,
                sup.prepaidDefault));
    }

    // ---------- клиент ----------
    public void setClientData(PanelSyncPayload p) { clientData = p; }
    public List<PanelSyncPayload.AccountView> getAccounts() { return clientData == null ? List.of() : clientData.accounts(); }
    public List<PanelSyncPayload.VaultView> getVault() { return clientData == null ? List.of() : clientData.vault(); }
    public int getValue(int slot) { return clientData == null || slot >= clientData.values().length ? 1 : clientData.values()[slot]; }
    public long getPrice() { return clientData == null ? 0 : clientData.price(); }
    public long getPerFe() { return clientData == null ? 1 : clientData.perFe(); }
    public int getNextBillSeconds() { return clientData == null ? 0 : clientData.nextBillSec(); }
    public boolean hasData() { return clientData != null; }
    public int getPeriodDays() { return clientData == null ? 1 : clientData.periodDays(); }
    public int getLimitAfterDays() { return clientData == null ? -1 : clientData.limitAfterDays(); }
    public int getLimitPercent() { return clientData == null ? 0 : clientData.limitPercent(); }
    public int getCutAfterDays() { return clientData == null ? -1 : clientData.cutAfterDays(); }
    public boolean isPrepaidDefault() { return clientData != null && clientData.prepaidDefault(); }

    @Override
    public boolean stillValid(Player p) {
        return transformer == null || (!transformer.isRemoved() && p.distanceToSqr(transformer.getBlockPos().getCenter()) <= 64);
    }
}
