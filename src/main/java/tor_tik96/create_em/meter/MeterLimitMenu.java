package tor_tik96.create_em.meter;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.registry.EMRegistry;

/**
 * Меню лимита счётчика. Без слотов: только числа через ContainerData и кнопки через
 * clickMenuButton (ванильный пакет нажатия кнопки, свой пакет не нужен).
 * <p>
 * Данные идут по сети как short, поэтому числа больше 32767 передаются двумя половинами.
 */
public class MeterLimitMenu extends AbstractContainerMenu implements tor_tik96.create_em.panel.ValuePayload.Receiver {
    public static final int F_LIMIT = 1;
    public static final int BTN_MINUS_LARGE = 0, BTN_MINUS_SMALL = 1, BTN_PLUS_SMALL = 2, BTN_PLUS_LARGE = 3,
            BTN_MIN = 4, BTN_MAX = 5, BTN_REARM = 6;

    // слоты данных
    private static final int LIMIT_LO = 0, LIMIT_HI = 1, MIN_LO = 2, MIN_HI = 3, MAX_LO = 4, MAX_HI = 5,
            CAN_EDIT = 6, TIER = 7, SEALED = 8, TRIPPED = 9, CAN_REARM = 10, COUNT = 11;

    @Nullable private final EnergyMeterBlockEntity meter; // только на сервере
    private final ContainerData data;

    /** Клиент. */
    public MeterLimitMenu(int id, Inventory inv) {
        super(EMRegistry.METER_LIMIT_MENU.get(), id);
        this.meter = null;
        this.data = new SimpleContainerData(COUNT);
        addDataSlots(data);
    }

    /** Сервер. */
    public MeterLimitMenu(int id, Inventory inv, EnergyMeterBlockEntity meter) {
        super(EMRegistry.METER_LIMIT_MENU.get(), id);
        this.meter = meter;
        Player player = inv.player;
        this.data = new ContainerData() {
            @Override public int get(int i) {
                MeterTier t = meter.getTier();
                return switch (i) {
                    case LIMIT_LO -> meter.getLimit() & 0xFFFF;
                    case LIMIT_HI -> meter.getLimit() >>> 16;
                    case MIN_LO -> t.minLimit() & 0xFFFF;
                    case MIN_HI -> t.minLimit() >>> 16;
                    case MAX_LO -> t.maxLimit() & 0xFFFF;
                    case MAX_HI -> t.maxLimit() >>> 16;
                    case CAN_EDIT -> meter.canEditLimit(player) ? 1 : 0;
                    case TIER -> t.ordinal();
                    case SEALED -> meter.isSealed() ? 1 : 0;
                    case TRIPPED -> meter.isTripped() ? 1 : 0;
                    case CAN_REARM -> meter.canRearm(player) ? 1 : 0;
                    default -> 0;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return COUNT; }
        };
        addDataSlots(data);
    }

    private int joined(int lo, int hi) { return (data.get(lo) & 0xFFFF) | ((data.get(hi) & 0xFFFF) << 16); }

    public int getLimit() { return joined(LIMIT_LO, LIMIT_HI); }
    public int getMin() { return joined(MIN_LO, MIN_HI); }
    public int getMax() { return joined(MAX_LO, MAX_HI); }
    public boolean canEdit() { return data.get(CAN_EDIT) == 1; }
    public boolean isSealed() { return data.get(SEALED) == 1; }
    public boolean isTripped() { return data.get(TRIPPED) == 1; }
    public boolean canRearm() { return data.get(CAN_REARM) == 1; }
    public MeterTier getTier() { return MeterTier.values()[Math.max(0, Math.min(MeterTier.values().length - 1, data.get(TIER)))]; }

    /** Нажатие кнопки приходит на сервер; права проверяются здесь, а не на клиенте. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (meter == null) return false;
        if (id == BTN_REARM) return meter.rearm(player);
        if (!meter.canEditLimit(player)) return false;
        MeterTier t = meter.getTier();
        int cur = meter.getLimit();
        int next = switch (id) {
            case BTN_MINUS_LARGE -> cur - t.largeStep;
            case BTN_MINUS_SMALL -> cur - t.smallStep;
            case BTN_PLUS_SMALL -> cur + t.smallStep;
            case BTN_PLUS_LARGE -> cur + t.largeStep;
            case BTN_MIN -> t.minLimit();
            case BTN_MAX -> t.maxLimit();
            default -> cur;
        };
        meter.setLimit(next);
        return true;
    }

    /** Лимит из поля ввода; права те же, что у кнопок. */
    @Override
    public void receiveValue(net.minecraft.server.level.ServerPlayer player, int field, java.util.UUID target, long value) {
        if (field == F_LIMIT && meter != null && meter.canEditLimit(player))
            meter.setLimit((int) Math.max(0, Math.min(Integer.MAX_VALUE, value)));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        if (meter == null) return true;
        return !meter.isRemoved() && player.distanceToSqr(meter.getBlockPos().getCenter()) <= 64;
    }
}
