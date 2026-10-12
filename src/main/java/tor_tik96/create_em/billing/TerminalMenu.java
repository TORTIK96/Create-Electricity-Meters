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

    // длинные числа идут по сети четырьмя short-слотами
    private static final int L_BAL = 0, L_SINCE = 4, L_UNB = 8, L_PRICE = 12, L_PER = 16,
            STAGE = 20, NEXT = 21, OWNER = 22, VAL0 = 23, COUNT = VAL0 + CURRENCY_SLOTS * 2;

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
                long now = be.getLevel().getGameTime();
                if (i < STAGE) {
                    long v = switch (i / 4) {
                        case 0 -> a == null ? 0 : a.balance;
                        case 1 -> a == null ? 0 : a.feSincePayment;
                        case 2 -> a == null ? 0 : a.unbilledFe;
                        case 3 -> bd.tariff(owner).price;
                        default -> bd.tariff(owner).perFe;
                    };
                    return (int) ((v >>> ((i % 4) * 16)) & 0xFFFF);
                }
                return switch (i) {
                    case STAGE -> a == null ? 0 : a.stage(now).ordinal();
                    case NEXT -> bd.getNextBillingAt() < 0 ? 0 : (int) Math.min(32767, Math.max(0, (bd.getNextBillingAt() - now) / 20));
                    case OWNER -> owner.equals(player.getUUID()) ? 1 : 0;
                    default -> 0;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return COUNT; }
        };
    }

    private long longAt(int base) {
        long v = 0;
        for (int k = 0; k < 4; k++) v |= (long) (data.get(base + k) & 0xFFFF) << (k * 16);
        return v;
    }
    private int joined(int l, int h) { return (data.get(l) & 0xFFFF) | ((data.get(h) & 0xFFFF) << 16); }

    public long getBalance() { return longAt(L_BAL); }
    public long getSinceFe() { return longAt(L_SINCE); }
    public long getUnbilledFe() { return longAt(L_UNB); }
    public BillingStage getStage() { return BillingStage.values()[Math.max(0, Math.min(3, data.get(STAGE)))]; }
    public long getPrice() { return longAt(L_PRICE); }
    public long getPerFe() { return Math.max(1, longAt(L_PER)); }
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
