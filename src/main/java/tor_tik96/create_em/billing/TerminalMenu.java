package tor_tik96.create_em.billing;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.UUID;

import static tor_tik96.create_em.billing.BillingData.CURRENCY_SLOTS;
import static tor_tik96.create_em.billing.TerminalBlockEntity.SLOT_PAY;

/**
 * Терминал абонента: кладёшь любую валюту поставщика, жмёшь «Оплатить» —
 * предметы пересчитываются в кредиты по его курсу и уходят в общее хранилище поставщика
 * (в самом терминале ничего не копится). Настройки валюты и тарифа — в умном трансформаторе.
 */
public class TerminalMenu extends AbstractContainerMenu {
    public static final int BTN_PAY = 0;

    private static final int BAL_LO = 0, BAL_HI = 1, SINCE_LO = 2, SINCE_HI = 3, UNB_LO = 4, UNB_HI = 5, STAGE = 6,
            PRICE = 7, PER = 8, NEXT = 9, OWNER = 10, VAL0 = 11, COUNT = VAL0 + CURRENCY_SLOTS * 2;

    // раскладка (общая с экраном)
    public static final int W = 256, H = 234;
    public static final int PAY_X = 10, PAY_Y = 30, CUR_X = 10, CUR_STEP = 37, CUR_Y = 110,
            INV_X = 47, INV_Y = 152, HOTBAR_Y = 210;

    @Nullable private final TerminalBlockEntity terminal;
    private final Container pay;
    private final Container accepted;   // показ принимаемой валюты (только для глаз)
    private final ContainerData data;

    /** Клиент. */
    public TerminalMenu(int id, Inventory inv) {
        this(id, inv, null, new SimpleContainer(TerminalBlockEntity.SIZE), new SimpleContainer(CURRENCY_SLOTS), new SimpleContainerData(COUNT));
    }

    /** Сервер. */
    public TerminalMenu(int id, Inventory inv, TerminalBlockEntity be) {
        this(id, inv, be, be.getContainer(), acceptedOf(be), serverData(be, inv.player));
    }

    private TerminalMenu(int id, Inventory inv, @Nullable TerminalBlockEntity be, Container pay, Container accepted, ContainerData data) {
        super(EMRegistry.TERMINAL_MENU.get(), id);
        this.terminal = be;
        this.pay = pay;
        this.accepted = accepted;
        this.data = data;
        addDataSlots(data);
        addSlot(new Slot(pay, SLOT_PAY, PAY_X, PAY_Y));
        for (int i = 0; i < CURRENCY_SLOTS; i++)
            addSlot(new Slot(accepted, i, CUR_X + i * CUR_STEP, CUR_Y) {
                @Override public boolean mayPlace(ItemStack s) { return false; }
                @Override public boolean mayPickup(Player p) { return false; }
            });
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, INV_X + c * 18, HOTBAR_Y));
    }

    private static Container acceptedOf(TerminalBlockEntity be) {
        SimpleContainer c = new SimpleContainer(CURRENCY_SLOTS);
        if (be.getOwner() != null && be.getLevel() != null && be.getLevel().getServer() != null) {
            BillingData.Supplier s = BillingData.get(be.getLevel().getServer()).supplier(be.getOwner());
            for (int i = 0; i < CURRENCY_SLOTS; i++) c.setItem(i, s.currency[i].copy());
        }
        return c;
    }

    private static ContainerData serverData(TerminalBlockEntity be, Player player) {
        return new ContainerData() {
            @Override public int get(int i) {
                UUID owner = be.getOwner();
                if (owner == null || be.getLevel() == null || be.getLevel().getServer() == null) return 0;
                BillingData bd = BillingData.get(be.getLevel().getServer());
                if (i >= VAL0) {
                    int v = bd.supplier(owner).currency[(i - VAL0) / 2].isEmpty() ? 0 : bd.supplier(owner).value[(i - VAL0) / 2];
                    return (i - VAL0) % 2 == 0 ? v & 0xFFFF : (v >>> 16) & 0xFFFF;
                }
                BillingData.Account a = bd.findAccount(owner, player.getUUID());
                long fePerKwh = Config.FE_PER_KWH.get();
                long now = be.getLevel().getGameTime();
                long bal = a == null ? 0 : a.balance;
                long since = a == null ? 0 : a.feSincePayment * 100 / fePerKwh;
                long unb = a == null ? 0 : a.unbilledFe * 100 / fePerKwh;
                return switch (i) {
                    case BAL_LO -> lo(bal);
                    case BAL_HI -> hi(bal);
                    case SINCE_LO -> lo(since);
                    case SINCE_HI -> hi(since);
                    case UNB_LO -> lo(unb);
                    case UNB_HI -> hi(unb);
                    case STAGE -> a == null ? 0 : a.stage(now).ordinal();
                    case PRICE -> bd.tariff(owner).price;
                    case PER -> bd.tariff(owner).perKwh;
                    case NEXT -> bd.getNextBillingAt() < 0 ? 0 : (int) Math.min(32767, Math.max(0, (bd.getNextBillingAt() - now) / 20));
                    case OWNER -> owner.equals(player.getUUID()) ? 1 : 0;
                    default -> 0;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return COUNT; }
        };
    }

    private static int clampInt(long v) { return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v)); }
    private static int lo(long v) { return clampInt(v) & 0xFFFF; }
    private static int hi(long v) { return (clampInt(v) >>> 16) & 0xFFFF; }
    private int joined(int l, int h) { return (data.get(l) & 0xFFFF) | ((data.get(h) & 0xFFFF) << 16); }

    public int getBalance() { return joined(BAL_LO, BAL_HI); }
    public double getSinceKwh() { return joined(SINCE_LO, SINCE_HI) / 100.0; }
    public double getUnbilledKwh() { return joined(UNB_LO, UNB_HI) / 100.0; }
    public BillingStage getStage() { return BillingStage.values()[Math.max(0, Math.min(3, data.get(STAGE)))]; }
    public int getPrice() { return data.get(PRICE); }
    public int getPerKwh() { return Math.max(1, data.get(PER)); }
    public int getNextBillSeconds() { return data.get(NEXT) & 0xFFFF; }
    public boolean isOwner() { return data.get(OWNER) == 1; }
    public int getValue(int slot) { return joined(VAL0 + slot * 2, VAL0 + slot * 2 + 1); }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (id != BTN_PAY || terminal == null || terminal.getOwner() == null || terminal.getLevel() == null) return false;
        var server = terminal.getLevel().getServer();
        if (server == null) return false;
        BillingData bd = BillingData.get(server);
        UUID owner = terminal.getOwner();

        ItemStack stack = pay.getItem(SLOT_PAY);
        if (stack.isEmpty()) return false;
        int value = bd.valueOf(owner, stack);
        if (value <= 0) {
            boolean any = false;
            for (ItemStack c : bd.supplier(owner).currency) any |= !c.isEmpty();
            msg(p, any ? "create_em.terminal.wrong_currency" : "create_em.terminal.no_currency");
            return false;
        }
        long credits = (long) value * stack.getCount();
        bd.addToVault(owner, stack.copy());
        pay.setItem(SLOT_PAY, ItemStack.EMPTY);
        pay.setChanged();

        BillingData.Account a = bd.account(owner, p.getUUID());
        a.consumerName = p.getGameProfile().getName();
        bd.pay(a, credits, terminal.getLevel().getGameTime());
        p.displayClientMessage(Component.translatable("create_em.terminal.paid", credits, a.balance), true);
        return true;
    }

    private static void msg(Player p, String key) {
        p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    /** Shift-клик: из инвентаря — в слот оплаты, из слота оплаты — в инвентарь. */
    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(p)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int invStart = 1 + CURRENCY_SLOTS, end = invStart + 36;
        if (index == 0) {
            if (!moveItemStackTo(stack, invStart, end, true)) return ItemStack.EMPTY;
        } else if (index >= invStart) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
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
            ItemStack left = pay.removeItemNoUpdate(SLOT_PAY);
            if (!left.isEmpty() && !p.getInventory().add(left)) p.drop(left, false);
        }
    }
}
