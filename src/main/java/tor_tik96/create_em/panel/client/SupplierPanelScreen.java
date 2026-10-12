package tor_tik96.create_em.panel.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.billing.BillingStage;
import tor_tik96.create_em.client.EMButton;
import tor_tik96.create_em.client.EMGui;
import tor_tik96.create_em.panel.PanelSyncPayload.AccountView;
import tor_tik96.create_em.panel.PanelSyncPayload.VaultView;
import tor_tik96.create_em.panel.SupplierPanelMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static tor_tik96.create_em.billing.BillingData.CURRENCY_SLOTS;
import static tor_tik96.create_em.panel.SupplierPanelMenu.*;

/**
 * Панель поставщика: вкладки «Абоненты», «Валюта», «Касса».
 * Латунная тема; все размеры — в пикселях интерфейса относительно левого верхнего угла окна.
 */
public class SupplierPanelScreen extends AbstractContainerScreen<SupplierPanelMenu> {
    private static final EMGui.Theme T = EMGui.Theme.BRASS;
    private static final int ROW = 18, LIST_X = 6, LIST_Y = 26, LIST_W = 112, LIST_H = 200, LIST_ROWS = (LIST_H - 4) / ROW;
    private static final int DET_X = 122, DET_W = W - 6 - 146;
    private static final int CELL = 20, VAULT_COLS = 12, VAULT_X = 8, VAULT_Y = 28;

    private final List<EMButton> tabs = new ArrayList<>();
    private final List<EMButton> accountButtons = new ArrayList<>(), currencyButtons = new ArrayList<>();
    private EMButton toggle;
    private int scroll;
    private UUID selected;

    public SupplierPanelScreen(SupplierPanelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = H;
        inventoryLabelX = INV_X;
        inventoryLabelY = INV_Y - 11;
    }

    // ---------- кнопки ----------
    @Override
    protected void init() {
        super.init();
        tabs.clear(); accountButtons.clear(); currencyButtons.clear();
        String[] names = {"create_em.panel.tab_accounts", "create_em.panel.tab_currency", "create_em.panel.tab_vault"};
        for (int i = 0; i < 3; i++) {
            int tab = i;
            tabs.add(addRenderableWidget(new EMButton(leftPos + 6 + i * 82, topPos + 5, 80, 16, Component.translatable(names[i]), () -> T,
                    () -> { menu.tab = tab; refresh(); })));
        }
        // абоненты: действия над выбранным
        int x = leftPos + DET_X, y = topPos + 124;
        toggle = acc(new EMButton(x, y, DET_W, 16, Component.empty(), () -> T, () -> click(BTN_TOGGLE)));
        acc(new EMButton(x, y + 30, DET_W / 2 - 1, 16, Component.literal("−"), () -> T,
                () -> click(Screen.hasShiftDown() ? BTN_CAP_DOWN_BIG : BTN_CAP_DOWN)));
        acc(new EMButton(x + DET_W / 2 + 1, y + 30, DET_W / 2 - 1, 16, Component.literal("+"), () -> T,
                () -> click(Screen.hasShiftDown() ? BTN_CAP_UP_BIG : BTN_CAP_UP)));
        acc(new EMButton(x, y + 50, DET_W, 16, Component.translatable("create_em.panel.bill_one"), () -> T, () -> click(BTN_BILL_ONE)));
        acc(new EMButton(x, y + 84, DET_W, 16, Component.translatable("create_em.panel.bill_all"), () -> T, () -> click(BTN_BILL_ALL)));

        // валюта: тариф и цены
        int ty = topPos + 30;
        cur(new EMButton(leftPos + 52, ty, 12, 12, Component.literal("−"), () -> T, () -> click(Screen.hasShiftDown() ? BTN_PRICE_DOWN_BIG : BTN_PRICE_DOWN)));
        cur(new EMButton(leftPos + 112, ty, 12, 12, Component.literal("+"), () -> T, () -> click(Screen.hasShiftDown() ? BTN_PRICE_UP_BIG : BTN_PRICE_UP)));
        cur(new EMButton(leftPos + 168, ty, 12, 12, Component.literal("−"), () -> T, () -> click(Screen.hasShiftDown() ? BTN_PER_DOWN_BIG : BTN_PER_DOWN)));
        cur(new EMButton(leftPos + 236, ty, 12, 12, Component.literal("+"), () -> T, () -> click(Screen.hasShiftDown() ? BTN_PER_UP_BIG : BTN_PER_UP)));
        for (int i = 0; i < CURRENCY_SLOTS; i++) {
            int slot = i;
            int cx = leftPos + CUR_X0 + (i % 2) * CUR_COL, cy = topPos + CUR_Y0 + (i / 2) * CUR_ROW + 3;
            cur(new EMButton(cx + 86, cy, 12, 12, Component.literal("−"), () -> T,
                    () -> click(VALUE_BASE + slot * 4 + (Screen.hasShiftDown() ? 2 : 0))));
            cur(new EMButton(cx + 100, cy, 12, 12, Component.literal("+"), () -> T,
                    () -> click(VALUE_BASE + slot * 4 + (Screen.hasShiftDown() ? 3 : 1))));
        }
        refresh();
    }

    private EMButton acc(EMButton b) { accountButtons.add(addRenderableWidget(b)); return b; }
    private EMButton cur(EMButton b) { currencyButtons.add(addRenderableWidget(b)); return b; }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).active = menu.tab != i;
        AccountView sel = selectedView();
        for (EMButton b : accountButtons) { b.visible = menu.tab == 0; b.active = sel != null; }
        accountButtons.get(accountButtons.size() - 1).active = true; // «Выставить всем» не требует выбора
        if (sel != null) toggle.setMessage(Component.translatable(sel.disabled() ? "create_em.panel.turn_on" : "create_em.panel.turn_off"));
        else toggle.setMessage(Component.translatable("create_em.panel.turn_off"));
        for (EMButton b : currencyButtons) b.visible = menu.tab == 1;
    }

    private AccountView selectedView() {
        for (AccountView a : menu.getAccounts()) if (a.consumer().equals(selected)) return a;
        return null;
    }

    // ---------- мышь ----------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int rx = (int) mx - leftPos, ry = (int) my - topPos;
        if (menu.tab == 0 && rx >= LIST_X && rx < LIST_X + LIST_W && ry >= LIST_Y + 2 && ry < LIST_Y + LIST_H - 2) {
            int i = scroll + (ry - LIST_Y - 2) / ROW;
            List<AccountView> list = menu.getAccounts();
            if (i < list.size()) {
                selected = list.get(i).consumer();
                click(SELECT_BASE + i);
                refresh();
            }
            return true;
        }
        if (menu.tab == 2) {
            int col = (rx - VAULT_X) / CELL, row = (ry - VAULT_Y) / CELL;
            if (rx >= VAULT_X && col < VAULT_COLS && ry >= VAULT_Y && row >= 0) {
                int i = row * VAULT_COLS + col;
                if (i < menu.getVault().size()) { click(VAULT_BASE + i); return true; }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (menu.tab == 0) {
            int max = Math.max(0, menu.getAccounts().size() - LIST_ROWS);
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    // ---------- отрисовка ----------
    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        EMGui.panel(g, T, x, y, W, H);
        // подчёркивание активной вкладки
        g.fill(x + 8 + menu.tab * 82, y + 22, x + 84 + menu.tab * 82, y + 23, 0xFF3A2411);

        switch (menu.tab) {
            case 0 -> {
                EMGui.inset(g, T, x + LIST_X, y + LIST_Y, LIST_W, LIST_H);
                EMGui.inset(g, T, x + DET_X, y + LIST_Y, DET_W, 94);
                List<AccountView> list = menu.getAccounts();
                for (int r = 0; r < LIST_ROWS && scroll + r < list.size(); r++)
                    if (list.get(scroll + r).consumer().equals(selected))
                        g.fill(x + LIST_X + 1, y + LIST_Y + 2 + r * ROW, x + LIST_X + LIST_W - 1, y + LIST_Y + 2 + (r + 1) * ROW, 0xFF4A2E14);
            }
            case 1 -> {
                EMGui.inset(g, T, x + 6, y + 26, W - 12, 34);
                for (Slot s : menu.slots) if (s.isActive()) EMGui.slot(g, T, x + s.x, y + s.y);
            }
            case 2 -> {
                EMGui.inset(g, T, x + 6, y + 26, W - 12, H - 32);
                List<VaultView> v = menu.getVault();
                for (int i = 0; i < v.size(); i++) {
                    int cx = x + VAULT_X + (i % VAULT_COLS) * CELL, cy = y + VAULT_Y + (i / VAULT_COLS) * CELL;
                    if (cy + 18 > y + H - 8) break;
                    EMGui.slot(g, T, cx + 1, cy + 1);
                    g.renderItem(v.get(i).sample(), cx + 1, cy + 1);
                    g.renderItemDecorations(font, v.get(i).sample(), cx + 1, cy + 1, shortCount(v.get(i).count()));
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        int scr = T.screenText, label = T.labelColor();
        switch (menu.tab) {
            case 0 -> labelsAccounts(g, scr);
            case 1 -> {
                EMGui.text(g, font, Component.translatable("create_em.terminal.price_label"), 12, 32, 38, scr, false, 0);
                EMGui.text(g, font, Component.translatable("create_em.unit.credits", menu.getPrice()), 66, 32, 44, scr, false, 1);
                EMGui.text(g, font, Component.translatable("create_em.terminal.per_label"), 132, 32, 34, scr, false, 0);
                EMGui.text(g, font, Component.translatable("create_em.unit.kwh", menu.getPerKwh()), 182, 32, 52, scr, false, 1);
                EMGui.text(g, font, nextBill(), 12, 47, W - 24, scr, false, 0);
                EMGui.text(g, font, Component.translatable("create_em.panel.currency_hint"), 8, 64, W - 16, label, false, 0);
                for (int i = 0; i < CURRENCY_SLOTS; i++) {
                    int cx = CUR_X0 + (i % 2) * CUR_COL, cy = CUR_Y0 + (i / 2) * CUR_ROW;
                    boolean has = !menu.slots.get(i).getItem().isEmpty();
                    EMGui.text(g, font, has ? Component.translatable("create_em.panel.value", menu.getValue(i))
                            : Component.translatable("create_em.panel.empty_slot"), cx + 22, cy + 5, 62, has ? label : 0xFF7A5A34, false, 0);
                }
                EMGui.text(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 162, label, false, 0);
            }
            case 2 -> {
                if (menu.getVault().isEmpty())
                    EMGui.text(g, font, Component.translatable("create_em.panel.vault_empty"), 12, 32, W - 24, scr, false, 0);
                EMGui.text(g, font, Component.translatable("create_em.panel.vault_hint"), 8, H - 16, W - 16, scr, false, 2);
            }
        }
    }

    private void labelsAccounts(GuiGraphics g, int scr) {
        List<AccountView> list = menu.getAccounts();
        if (list.isEmpty()) EMGui.text(g, font, Component.translatable("create_em.panel.no_accounts"), LIST_X + 4, LIST_Y + 6, LIST_W - 8, scr, false, 0);
        for (int r = 0; r < LIST_ROWS && scroll + r < list.size(); r++) {
            AccountView a = list.get(scroll + r);
            int ry = LIST_Y + 2 + r * ROW + 5;
            int nameColor = a.disabled() ? 0xFF9A9D9B : stageColor(a.stage());
            EMGui.text(g, font, Component.literal(a.name().isEmpty() ? "?" : a.name()), LIST_X + 4, ry, 66, nameColor, false, 0);
            EMGui.text(g, font, Component.literal(String.valueOf(a.balance())), LIST_X + 72, ry, LIST_W - 76,
                    a.balance() < 0 ? 0xFFFF6B5A : scr, false, 2);
        }
        AccountView s = selectedView();
        int dx = DET_X + 4, dy = LIST_Y + 5, dw = DET_W - 8;
        if (s == null) {
            EMGui.text(g, font, Component.translatable("create_em.panel.select_hint"), dx, dy, dw, scr, false, 0);
            return;
        }
        long fePerKwh = 1_000_000L;
        try { fePerKwh = Config.FE_PER_KWH.get(); } catch (Exception ignored) {} // на клиенте серверного конфига может не быть
        BillingStage st = BillingStage.values()[Math.max(0, Math.min(3, s.stage()))];
        Component[] lines = {
                Component.literal(s.name()).withStyle(ChatFormatting.WHITE),
                Component.translatable("create_em.terminal.balance", s.balance()),
                Component.translatable("create_em.terminal.since", String.format("%.2f", s.sinceFe() / (double) fePerKwh)),
                Component.translatable("create_em.panel.unbilled", String.format("%.2f", s.unbilledFe() / (double) fePerKwh)),
                s.disabled() ? Component.translatable("create_em.panel.state_off").withStyle(ChatFormatting.RED)
                        : Component.translatable(st.key()).withStyle(stageFmt(st)),
                s.cap() > 0 ? Component.translatable("create_em.panel.cap", String.format("%,d FE/t", s.cap()))
                        : Component.translatable("create_em.panel.no_cap")};
        for (int i = 0; i < lines.length; i++) EMGui.text(g, font, lines[i], dx, dy + i * 14, dw, scr, false, 0);
        EMGui.text(g, font, Component.translatable("create_em.panel.cap_label"), DET_X, 144, DET_W, T.labelColor(), false, 1);
    }

    private static ChatFormatting stageFmt(BillingStage st) {
        return switch (st) { case NORMAL -> ChatFormatting.GREEN; case WARNING, LIMITED -> ChatFormatting.GOLD; case CUT -> ChatFormatting.RED; };
    }

    private static int stageColor(int stage) {
        return switch (stage) { case 0 -> 0xFFB8F0A0; case 1, 2 -> 0xFFFFC266; default -> 0xFFFF6B5A; };
    }

    private Component nextBill() {
        int s = menu.getNextBillSeconds();
        return Component.translatable("create_em.terminal.next_bill", String.format("%d:%02d", s / 60, s % 60));
    }

    private static String shortCount(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return (n / 1000) + "k";
        return (n / 1_000_000) + "M";
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
        // подсказка по предмету в кассе
        if (menu.tab == 2) {
            int rx = mx - leftPos - VAULT_X, ry = my - topPos - VAULT_Y;
            if (rx >= 0 && ry >= 0 && rx / CELL < VAULT_COLS) {
                int i = (ry / CELL) * VAULT_COLS + rx / CELL;
                if (i < menu.getVault().size()) {
                    VaultView v = menu.getVault().get(i);
                    g.renderTooltip(font, List.of(v.sample().getHoverName(), Component.literal(String.format("× %,d", v.count()))),
                            java.util.Optional.empty(), mx, my);
                }
            }
        }
    }
}
