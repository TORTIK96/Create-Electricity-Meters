package tor_tik96.create_em.billing;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.UUID;

import static tor_tik96.create_em.billing.TerminalBlockEntity.*;

/**
 * Меню терминала. Одно меню на двоих: поставщик видит тариф и кассу,
 * абонент — свой лицевой счёт и кнопку оплаты. Кто есть кто — решает сервер.
 */
public class TerminalMenu extends AbstractContainerMenu {
    public static final int BTN_PAY = 0, BTN_PRICE_DOWN = 1, BTN_PRICE_UP = 2, BTN_PER_DOWN = 3, BTN_PER_UP = 4,
            BTN_X10 = 10; // id + 10 — шаг 10 (Shift)

    // данные
    private static final int OWNER = 0, PRICE = 1, PER = 2, BAL_LO = 3, BAL_HI = 4, SINCE_LO = 5, SINCE_HI = 6,
            UNBILLED_LO = 7, UNBILLED_HI = 8, STAGE = 9, ACCOUNTS = 10, DEBT_LO = 11, DEBT_HI = 12, COUNT = 13;

    // координаты (общие с экраном)
    public static final int W = 216, H = 182;
    public static final int SAMPLE_X = 8, SAMPLE_Y = 20, PAY_X = 8, PAY_Y = 58, CASH_X = 156, CASH_Y = 18,
            INV_X = 27, INV_Y = 100, HOTBAR_Y = 158;

    @Nullable private final TerminalBlockEntity terminal;
    private final Container container;
    private final ContainerData data;

    /** Клиент. */
    public TerminalMenu(int id, Inventory inv) {
        this(id, inv, new SimpleContainer(SIZE), new SimpleContainerData(COUNT), null);
    }

    /** Сервер. */
    public TerminalMenu(int id, Inventory inv, TerminalBlockEntity be) {
        this(id, inv, be.getContainer(), serverData(be, inv.player), be);
    }

    private TerminalMenu(int id, Inventory inv, Container container, ContainerData data, @Nullable TerminalBlockEntity be) {
        super(EMRegistry.TERMINAL_MENU.get(), id);
        this.terminal = be;
        this.container = container;
        this.data = data;
        addDataSlots(data);

        addSlot(new Slot(container, SLOT_SAMPLE, SAMPLE_X, SAMPLE_Y) {
            @Override public boolean mayPlace(ItemStack s) { return isOwner(); }
            @Override public boolean mayPickup(Player p) { return isOwner(); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(container, SLOT_PAY, PAY_X, PAY_Y));
        for (int i = 0; i < CASH_COUNT; i++) {
            addSlot(new Slot(container, CASH_FIRST + i, CASH_X + (i % 3) * 18, CASH_Y + (i / 3) * 18) {
                @Override public boolean mayPlace(ItemStack s) { return false; }
                @Override public boolean mayPickup(Player p) { return isOwner(); }
                @Override public boolean isActive() { return isOwner(); }
            });
        }
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, INV_X + c * 18, HOTBAR_Y));
    }

    private static ContainerData serverData(TerminalBlockEntity be, Player player) {
        return new ContainerData() {
            @Override public int get(int i) {
                UUID owner = be.getOwner();
                if (owner == null || be.getLevel() == null || be.getLevel().getServer() == null) return 0;
                BillingData bd = BillingData.get(be.getLevel().getServer());
                BillingData.Tariff t = bd.tariff(owner);
                BillingData.Account a = bd.findAccount(owner, player.getUUID());
                long now = be.getLevel().getGameTime();
                long fePerKwh = Config.FE_PER_KWH.get();
                long bal = a == null ? 0 : a.balance;
                long since = a == null ? 0 : a.feSincePayment * 100 / fePerKwh;     // сотые кВт·ч
                long unbilled = a == null ? 0 : a.unbilledFe * 100 / fePerKwh;
                long debt = 0;
                int count = 0;
                if (i == ACCOUNTS || i == DEBT_LO || i == DEBT_HI)
                    for (BillingData.Account x : bd.accountsOf(owner)) { count++; if (x.balance < 0) debt -= x.balance; }
                return switch (i) {
                    case OWNER -> owner.equals(player.getUUID()) ? 1 : 0;
                    case PRICE -> t.price;
                    case PER -> t.perKwh;
                    case BAL_LO -> lo(bal);
                    case BAL_HI -> hi(bal);
                    case SINCE_LO -> lo(since);
                    case SINCE_HI -> hi(since);
                    case UNBILLED_LO -> lo(unbilled);
                    case UNBILLED_HI -> hi(unbilled);
                    case STAGE -> a == null ? 0 : a.stage(now).ordinal();
                    case ACCOUNTS -> count;
                    case DEBT_LO -> lo(debt);
                    case DEBT_HI -> hi(debt);
                    default -> 0;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return COUNT; }
        };
    }

    // числа идут по сети как short — шлём int двумя половинами
    private static int lo(long v) { int x = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v)); return x & 0xFFFF; }
    private static int hi(long v) { int x = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v)); return (x >>> 16) & 0xFFFF; }
    private int joined(int l, int h) { return (data.get(l) & 0xFFFF) | ((data.get(h) & 0xFFFF) << 16); }

    public boolean isOwner() { return data.get(OWNER) == 1; }
    public int getPrice() { return data.get(PRICE); }
    public int getPerKwh() { return Math.max(1, data.get(PER)); }
    public int getBalance() { return joined(BAL_LO, BAL_HI); }
    public double getSinceKwh() { return joined(SINCE_LO, SINCE_HI) / 100.0; }
    public double getUnbilledKwh() { return joined(UNBILLED_LO, UNBILLED_HI) / 100.0; }
    public BillingStage getStage() { return BillingStage.values()[Math.max(0, Math.min(3, data.get(STAGE)))]; }
    public int getAccounts() { return data.get(ACCOUNTS); }
    public int getTotalDebt() { return joined(DEBT_LO, DEBT_HI); }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (terminal == null || terminal.getOwner() == null || terminal.getLevel() == null) return false;
        var server = terminal.getLevel().getServer();
        if (server == null) return false;
        BillingData bd = BillingData.get(server);

        if (id == BTN_PAY) return pay(p, bd);

        if (!terminal.getOwner().equals(p.getUUID())) return false; // тариф меняет только поставщик
        int step = id >= BTN_X10 ? 10 : 1;
        int base = id % BTN_X10;
        BillingData.Tariff t = bd.tariff(terminal.getOwner());
        switch (base) {
            case BTN_PRICE_DOWN -> bd.setTariff(terminal.getOwner(), t.price - step, t.perKwh);
            case BTN_PRICE_UP -> bd.setTariff(terminal.getOwner(), t.price + step, t.perKwh);
            case BTN_PER_DOWN -> bd.setTariff(terminal.getOwner(), t.price, t.perKwh - step);
            case BTN_PER_UP -> bd.setTariff(terminal.getOwner(), t.price, t.perKwh + step);
            default -> { return false; }
        }
        return true;
    }

    /** Переложить валюту из слота оплаты в кассу и зачислить на лицевой счёт игрока. */
    private boolean pay(Player p, BillingData bd) {
        ItemStack pay = container.getItem(SLOT_PAY);
        ItemStack sample = container.getItem(SLOT_SAMPLE);
        if (pay.isEmpty()) return false;
        if (sample.isEmpty()) { msg(p, "create_em.terminal.no_currency"); return false; }
        if (!ItemStack.isSameItemSameComponents(pay, sample)) { msg(p, "create_em.terminal.wrong_currency"); return false; }

        int moved = 0;
        for (int i = CASH_FIRST; i < CASH_FIRST + CASH_COUNT && !pay.isEmpty(); i++) {
            ItemStack cash = container.getItem(i);
            if (cash.isEmpty()) {
                int n = Math.min(pay.getCount(), pay.getMaxStackSize());
                container.setItem(i, pay.copyWithCount(n));
                pay.shrink(n);
                moved += n;
            } else if (ItemStack.isSameItemSameComponents(cash, pay)) {
                int n = Math.min(pay.getCount(), cash.getMaxStackSize() - cash.getCount());
                if (n <= 0) continue;
                cash.grow(n);
                pay.shrink(n);
                moved += n;
            }
        }
        container.setItem(SLOT_PAY, pay);
        container.setChanged();
        if (moved == 0) { msg(p, "create_em.terminal.cash_full"); return false; }

        BillingData.Account a = bd.account(terminal.getOwner(), p.getUUID());
        a.consumerName = p.getGameProfile().getName();
        bd.pay(a, moved, terminal.getLevel().getGameTime());
        p.displayClientMessage(Component.translatable("create_em.terminal.paid", moved, a.balance), true);
        return true;
    }

    private static void msg(Player p, String key) {
        p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    /** Shift-клик: из инвентаря — в слот оплаты, из терминала — в инвентарь. */
    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(p)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int invStart = 2 + CASH_COUNT, end = invStart + 36;
        if (index < invStart) {
            if (!moveItemStackTo(stack, invStart, end, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, SLOT_PAY, SLOT_PAY + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        if (terminal == null) return true;
        return !terminal.isRemoved() && p.distanceToSqr(terminal.getBlockPos().getCenter()) <= 64;
    }

    /** Закрыли меню — всё, что лежит в слоте оплаты, возвращается игроку. */
    @Override
    public void removed(Player p) {
        super.removed(p);
        if (terminal != null && !p.level().isClientSide) {
            ItemStack left = container.removeItemNoUpdate(SLOT_PAY);
            if (!left.isEmpty() && !p.getInventory().add(left)) p.drop(left, false);
        }
    }
}
