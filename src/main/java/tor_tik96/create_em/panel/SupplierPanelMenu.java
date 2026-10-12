package tor_tik96.create_em.panel;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
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
 * Панель поставщика в умном (латунном) трансформаторе: абоненты, валюта, хранилище.
 * <p>
 * Слоты валюты — «призрачные», как фильтры Create: клик предметом ставит его копию,
 * сам предмет остаётся у игрока; клик пустой рукой очищает слот.
 * Действия — через clickMenuButton, списки абонентов и хранилища — через {@link PanelSyncPayload}.
 */
public class SupplierPanelMenu extends AbstractContainerMenu {
    // кнопки
    public static final int BTN_TOGGLE = 1, BTN_CAP_DOWN = 2, BTN_CAP_UP = 3, BTN_CAP_DOWN_BIG = 4, BTN_CAP_UP_BIG = 5,
            BTN_BILL_ONE = 6, BTN_BILL_ALL = 7,
            BTN_PRICE_DOWN = 8, BTN_PRICE_UP = 9, BTN_PRICE_DOWN_BIG = 10, BTN_PRICE_UP_BIG = 11,
            BTN_PER_DOWN = 12, BTN_PER_UP = 13, BTN_PER_DOWN_BIG = 14, BTN_PER_UP_BIG = 15,
            SELECT_BASE = 1000, VAULT_BASE = 2000, VALUE_BASE = 3000;
    public static final int CAP_STEP = 100, CAP_STEP_BIG = 1000;

    // данные: цены валюты (lo/hi), тариф, до квитанции
    private static final int PRICE = CURRENCY_SLOTS * 2, PER = PRICE + 1, NEXT_BILL = PER + 1, COUNT = NEXT_BILL + 1;

    // раскладка (общая с экраном)
    public static final int W = 256, H = 232;
    public static final int CUR_X0 = 8, CUR_COL = 124, CUR_Y0 = 78, CUR_ROW = 20;
    public static final int INV_X = 47, INV_Y = 150, HOTBAR_Y = 208;

    @Nullable private final UUID supplier;      // сервер
    @Nullable private final TransformerBlockEntity transformer; // сервер
    private final Player player;
    private final Container ghosts;
    private final ContainerData data;
    private int syncTimer;
    @Nullable private UUID selected;            // сервер: выбранный абонент

    // клиент
    public int tab = 1;                          // 0 — абоненты, 1 — валюта, 2 — касса; на сервере все слоты активны
    private List<PanelSyncPayload.AccountView> accounts = List.of();
    private List<PanelSyncPayload.VaultView> vault = List.of();

    /** Клиент. */
    public SupplierPanelMenu(int id, Inventory inv) {
        this(id, inv, null, null, new SimpleContainer(CURRENCY_SLOTS), new SimpleContainerData(COUNT));
    }

    /** Сервер. */
    public static SupplierPanelMenu server(int id, Inventory inv, TransformerBlockEntity be) {
        UUID owner = be.getOwner();
        MinecraftServer server = inv.player.getServer();
        BillingData bd = BillingData.get(server);
        SimpleContainer ghosts = new SimpleContainer(CURRENCY_SLOTS);
        BillingData.Supplier sup = bd.supplier(owner);
        for (int i = 0; i < CURRENCY_SLOTS; i++) ghosts.setItem(i, sup.currency[i].copy());
        ContainerData data = new ContainerData() {
            @Override public int get(int i) {
                BillingData.Supplier s = bd.supplier(owner);
                if (i < CURRENCY_SLOTS * 2) {
                    int v = s.value[i / 2];
                    return i % 2 == 0 ? v & 0xFFFF : (v >>> 16) & 0xFFFF;
                }
                return switch (i) {
                    case PRICE -> bd.tariff(owner).price;
                    case PER -> bd.tariff(owner).perKwh;
                    case NEXT_BILL -> bd.getNextBillingAt() < 0 ? 0
                            : (int) Math.min(32767, Math.max(0, (bd.getNextBillingAt() - server.overworld().getGameTime()) / 20));
                    default -> 0;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return COUNT; }
        };
        return new SupplierPanelMenu(id, inv, owner, be, ghosts, data);
    }

    private SupplierPanelMenu(int id, Inventory inv, @Nullable UUID supplier, @Nullable TransformerBlockEntity be,
                              Container ghosts, ContainerData data) {
        super(EMRegistry.SUPPLIER_PANEL_MENU.get(), id);
        this.supplier = supplier;
        this.transformer = be;
        this.player = inv.player;
        this.ghosts = ghosts;
        this.data = data;
        addDataSlots(data);
        for (int i = 0; i < CURRENCY_SLOTS; i++)
            addSlot(new GhostSlot(ghosts, i, CUR_X0 + 2 + (i % 2) * CUR_COL, CUR_Y0 + (i / 2) * CUR_ROW));
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new TabSlot(inv, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new TabSlot(inv, c, INV_X + c * 18, HOTBAR_Y));
    }

    // ---------- слоты ----------
    /** Призрачный слот валюты: живёт только на вкладке «Валюта», предмет в него не кладётся по-настоящему. */
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
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < CURRENCY_SLOTS) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                ItemStack carried = getCarried();
                setGhost(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return; // сам предмет никуда не перемещается
        }
        if (type == ClickType.QUICK_MOVE && slotId >= CURRENCY_SLOTS && slotId < slots.size()) {
            // Shift-клик по предмету в инвентаре — поставить его копию в первый свободный слот валюты
            ItemStack st = slots.get(slotId).getItem();
            if (st.isEmpty()) return;
            for (int i = 0; i < CURRENCY_SLOTS; i++)
                if (ItemStack.isSameItemSameComponents(ghosts.getItem(i), st)) return;
            for (int i = 0; i < CURRENCY_SLOTS; i++)
                if (ghosts.getItem(i).isEmpty()) { setGhost(i, st.copyWithCount(1)); return; }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    private void setGhost(int i, ItemStack stack) {
        ghosts.setItem(i, stack);
        if (supplier != null && serverPlayer() != null)
            BillingData.get(serverPlayer().getServer()).setCurrency(supplier, i, stack);
    }

    @Override public boolean canDragTo(Slot slot) { return !(slot instanceof GhostSlot); }
    @Override public boolean canTakeItemForPickAll(ItemStack s, Slot slot) { return !(slot instanceof GhostSlot); }
    @Override public ItemStack quickMoveStack(Player p, int index) { return ItemStack.EMPTY; }

    // ---------- кнопки ----------
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (supplier == null || !(player instanceof ServerPlayer sp) || !supplier.equals(player.getUUID())) return false;
        MinecraftServer server = sp.getServer();
        BillingData bd = BillingData.get(server);

        if (id >= VALUE_BASE && id < VALUE_BASE + CURRENCY_SLOTS * 4) {
            int slot = (id - VALUE_BASE) / 4, k = (id - VALUE_BASE) % 4;
            int delta = switch (k) { case 0 -> -1; case 1 -> 1; case 2 -> -10; default -> 10; };
            bd.setCurrencyValue(supplier, slot, bd.supplier(supplier).value[slot] + delta);
            return true;
        }
        if (id >= VAULT_BASE && id < VALUE_BASE) {
            ItemStack got = bd.takeFromVault(supplier, id - VAULT_BASE);
            if (!got.isEmpty() && !sp.getInventory().add(got)) sp.drop(got, false);
            syncTimer = 0;
            return true;
        }
        if (id >= SELECT_BASE && id < VAULT_BASE) {
            List<BillingData.Account> list = bd.accountsOf(supplier);
            int i = id - SELECT_BASE;
            selected = i < list.size() ? list.get(i).consumer : null;
            return true;
        }

        BillingData.Tariff t = bd.tariff(supplier);
        switch (id) {
            case BTN_PRICE_DOWN -> bd.setTariff(supplier, t.price - 1, t.perKwh);
            case BTN_PRICE_UP -> bd.setTariff(supplier, t.price + 1, t.perKwh);
            case BTN_PRICE_DOWN_BIG -> bd.setTariff(supplier, t.price - 10, t.perKwh);
            case BTN_PRICE_UP_BIG -> bd.setTariff(supplier, t.price + 10, t.perKwh);
            case BTN_PER_DOWN -> bd.setTariff(supplier, t.price, t.perKwh - 1);
            case BTN_PER_UP -> bd.setTariff(supplier, t.price, t.perKwh + 1);
            case BTN_PER_DOWN_BIG -> bd.setTariff(supplier, t.price, t.perKwh - 10);
            case BTN_PER_UP_BIG -> bd.setTariff(supplier, t.price, t.perKwh + 10);
            case BTN_BILL_ALL -> sp.displayClientMessage(Component.translatable("create_em.bill.manual", bd.billNow(server, supplier)), true);
            default -> { return accountAction(sp, bd, id); }
        }
        syncTimer = 0;
        return true;
    }

    private boolean accountAction(ServerPlayer sp, BillingData bd, int id) {
        if (selected == null) return false;
        BillingData.Account a = bd.findAccount(supplier, selected);
        if (a == null) return false;
        switch (id) {
            case BTN_TOGGLE -> {
                a.disabled = !a.disabled;
                ServerPlayer c = sp.getServer().getPlayerList().getPlayer(a.consumer);
                if (c != null) c.sendSystemMessage(Component.translatable(a.disabled ? "create_em.panel.notice_off" : "create_em.panel.notice_on")
                        .withStyle(a.disabled ? ChatFormatting.RED : ChatFormatting.GREEN));
            }
            case BTN_CAP_DOWN -> a.powerCap = Math.max(0, a.powerCap - CAP_STEP);
            case BTN_CAP_UP -> a.powerCap += CAP_STEP;
            case BTN_CAP_DOWN_BIG -> a.powerCap = Math.max(0, a.powerCap - CAP_STEP_BIG);
            case BTN_CAP_UP_BIG -> a.powerCap += CAP_STEP_BIG;
            case BTN_BILL_ONE -> sp.displayClientMessage(Component.translatable("create_em.bill.manual", bd.billAccount(sp.getServer(), a)), true);
            default -> { return false; }
        }
        bd.changed();
        syncTimer = 0;
        return true;
    }

    // ---------- синхронизация ----------
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        ServerPlayer sp = serverPlayer();
        if (sp == null || supplier == null || --syncTimer > 0) return;
        syncTimer = 20;
        BillingData bd = BillingData.get(sp.getServer());
        long now = sp.getServer().overworld().getGameTime();
        List<PanelSyncPayload.AccountView> acc = new ArrayList<>();
        for (BillingData.Account a : bd.accountsOf(supplier))
            acc.add(new PanelSyncPayload.AccountView(a.consumer, a.consumerName, a.balance, a.stage(now).ordinal(),
                    a.disabled, a.powerCap, a.feSincePayment, a.unbilledFe));
        List<PanelSyncPayload.VaultView> v = new ArrayList<>();
        for (BillingData.VaultEntry e : bd.supplier(supplier).vault) v.add(new PanelSyncPayload.VaultView(e.sample, e.count));
        PacketDistributor.sendToPlayer(sp, new PanelSyncPayload(containerId, acc, v));
    }

    @Nullable private ServerPlayer serverPlayer() { return player instanceof ServerPlayer sp ? sp : null; }

    // ---------- клиент ----------
    public void setClientData(List<PanelSyncPayload.AccountView> a, List<PanelSyncPayload.VaultView> v) { accounts = a; vault = v; }
    public List<PanelSyncPayload.AccountView> getAccounts() { return accounts; }
    public List<PanelSyncPayload.VaultView> getVault() { return vault; }
    public int getValue(int slot) { return (data.get(slot * 2) & 0xFFFF) | ((data.get(slot * 2 + 1) & 0xFFFF) << 16); }
    public int getPrice() { return data.get(PRICE); }
    public int getPerKwh() { return Math.max(1, data.get(PER)); }
    public int getNextBillSeconds() { return data.get(NEXT_BILL) & 0xFFFF; }

    @Override
    public boolean stillValid(Player p) {
        return transformer == null || (!transformer.isRemoved() && p.distanceToSqr(transformer.getBlockPos().getCenter()) <= 64);
    }
}
