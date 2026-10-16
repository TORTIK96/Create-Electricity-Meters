package tor_tik96.create_em.panel.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import tor_tik96.create_em.EMFormat;
import tor_tik96.create_em.billing.BillingStage;
import tor_tik96.create_em.client.EMButton;
import tor_tik96.create_em.client.EMGui;
import tor_tik96.create_em.client.EMScreen;
import tor_tik96.create_em.panel.PanelSyncPayload.AccountView;
import tor_tik96.create_em.panel.PanelSyncPayload.VaultView;
import tor_tik96.create_em.panel.SupplierPanelMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static tor_tik96.create_em.billing.BillingData.CURRENCY_SLOTS;
import static tor_tik96.create_em.panel.SupplierPanelMenu.*;

/**
 * Панель поставщика: вкладки «Абоненты», «Валюта», «Касса».
 * Числа вводятся в поля (понимают 1500, 2.5k, 3M), кнопки — только для действий.
 */
public class SupplierPanelScreen extends EMScreen<SupplierPanelMenu> {
    private static final EMGui.Theme T = EMGui.Theme.BRASS;
    private static final int ROW = 18, LIST_X = 6, LIST_Y = 26, LIST_W = 112, LIST_H = 224, LIST_ROWS = (LIST_H - 4) / ROW;
    private static final int DET_X = 122, DET_W = W - 6 - 122;
    private static final int CELL = 20, VAULT_COLS = 12, VAULT_X = 8, VAULT_Y = 28;
    private static final int TAB_STEP = 61, TAB_VAULT = 3, TAB_TERMS = 2;
    private static final int TERMS_FIELD_X = 172, TERMS_Y0 = 30, TERMS_ROW = 20;

    private final List<EMButton> tabs = new ArrayList<>();
    private final List<EMButton> accountButtons = new ArrayList<>();
    private EMButton toggle, billOne, payMode, ownReset, prepaidDefault;
    private int scroll;
    private UUID selected;

    public SupplierPanelScreen(SupplierPanelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = H;
        inventoryLabelX = INV_X;
        inventoryLabelY = INV_Y - 11;
    }

    @Override protected EMGui.Theme theme() { return T; }

    @Override
    protected void init() {
        super.init();
        tabs.clear(); accountButtons.clear();
        String[] names = {"create_em.panel.tab_accounts", "create_em.panel.tab_currency", "create_em.panel.tab_terms", "create_em.panel.tab_vault"};
        for (int i = 0; i < names.length; i++) {
            int tab = i;
            tabs.add(addRenderableWidget(new EMButton(leftPos + 6 + i * TAB_STEP, topPos + 5, TAB_STEP - 2, 16, Component.translatable(names[i]), () -> T,
                    () -> { menu.tab = tab; refresh(); })));
        }
        int x = leftPos + DET_X, y = topPos;
        // абоненты
        toggle = acc(new EMButton(x, y + 124, DET_W, 16, Component.empty(), () -> T, () -> click(BTN_TOGGLE)));
        field(x, y + 151, DET_W, F_BALANCE, () -> sel() == null ? 0 : sel().balance(), () -> selected,
                () -> menu.tab == 0 && sel() != null, Component.translatable("create_em.panel.hint_balance"));
        field(x, y + 176, DET_W, F_CAP, () -> sel() == null ? 0 : sel().cap(), () -> selected,
                () -> menu.tab == 0 && sel() != null, Component.translatable("create_em.panel.hint_cap"));
        billOne = acc(new EMButton(x, y + 237, DET_W / 2 - 1, 16, Component.translatable("create_em.panel.bill_one"), () -> T, () -> click(BTN_BILL_ONE)));
        acc(new EMButton(x + DET_W / 2 + 1, y + 237, DET_W / 2 - 1, 16, Component.translatable("create_em.panel.bill_all"), () -> T, () -> click(BTN_BILL_ALL)));
        // личный тариф и схема оплаты
        field(x, y + 201, 40, F_OWN_PRICE, () -> sel() == null ? 0 : sel().ownPrice() >= 0 ? sel().ownPrice() : menu.getPrice(),
                () -> selected, () -> menu.tab == 0 && sel() != null, Component.literal("0"));
        field(x + 62, y + 201, DET_W - 62, F_OWN_PER_FE, () -> sel() == null ? 0 : sel().ownPrice() >= 0 ? sel().ownPerFe() : menu.getPerFe(),
                () -> selected, () -> menu.tab == 0 && sel() != null, Component.literal("1M"));
        payMode = acc(new EMButton(x, y + 219, DET_W / 2 - 1, 16, Component.empty(), () -> T, () -> click(BTN_PAYMODE)));
        ownReset = acc(new EMButton(x + DET_W / 2 + 1, y + 219, DET_W / 2 - 1, 16, Component.translatable("create_em.panel.own_reset"), () -> T, () -> click(BTN_OWN_RESET)));
        // условия: схема оплаты по умолчанию
        prepaidDefault = addRenderableWidget(new EMButton(leftPos + 6, y + H - 26, W - 12, 16, Component.empty(), () -> T, () -> click(BTN_PREPAID_DEFAULT)));
        // валюта: тариф и цены
        field(leftPos + 38, y + 27, 66, F_PRICE, menu::getPrice, () -> null, () -> menu.tab == 1, Component.literal("0"));
        field(leftPos + 144, y + 27, 88, F_PER_FE, menu::getPerFe, () -> null, () -> menu.tab == 1, Component.literal("1M"));
        for (int i = 0; i < CURRENCY_SLOTS; i++) {
            int slot = i;
            int cx = leftPos + CUR_X0 + (i % 2) * CUR_COL, cy = y + CUR_Y0 + (i / 2) * CUR_ROW + 1;
            field(cx + 30, cy, 68, F_VALUE0 + i, () -> menu.getValue(slot), () -> null,
                    () -> menu.tab == 1 && menu.slots.get(slot).hasItem(), Component.literal("1"));
        }
        // условия: период и санкции
        int[] termFields = {F_PERIOD, F_LIMIT_AFTER, F_LIMIT_PERCENT, F_CUT_AFTER};
        java.util.function.LongSupplier[] termValues = {menu::getPeriodDays, menu::getLimitAfterDays, menu::getLimitPercent, menu::getCutAfterDays};
        for (int i = 0; i < termFields.length; i++)
            field(leftPos + TERMS_FIELD_X, y + TERMS_Y0 + i * TERMS_ROW - 3, W - 6 - TERMS_FIELD_X, termFields[i], termValues[i], () -> null,
                    () -> menu.tab == TAB_TERMS, Component.empty());
        refresh();
    }

    private EMButton acc(EMButton b) { accountButtons.add(addRenderableWidget(b)); return b; }

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
        AccountView s = sel();
        for (EMButton b : accountButtons) b.visible = menu.tab == 0;
        toggle.active = s != null;
        billOne.active = s != null;
        toggle.setMessage(Component.translatable(s != null && s.disabled() ? "create_em.panel.turn_on" : "create_em.panel.turn_off"));
        payMode.active = s != null;
        ownReset.active = s != null && s.ownPrice() >= 0;
        payMode.setMessage(Component.translatable(s == null ? "create_em.panel.pay_default"
                : switch (s.payMode()) { case 1 -> "create_em.panel.pay_post"; case 2 -> "create_em.panel.pay_pre"; default -> "create_em.panel.pay_default"; }));
        prepaidDefault.visible = menu.tab == TAB_TERMS;
        prepaidDefault.setMessage(Component.translatable(menu.isPrepaidDefault() ? "create_em.panel.default_pre" : "create_em.panel.default_post"));
    }

    private AccountView sel() {
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
        if (menu.tab == TAB_VAULT && rx >= VAULT_X && ry >= VAULT_Y) {
            int col = (rx - VAULT_X) / CELL, row = (ry - VAULT_Y) / CELL;
            int i = row * VAULT_COLS + col;
            if (col < VAULT_COLS && i < menu.getVault().size()) { click(VAULT_BASE + i); return true; }
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
        g.fill(x + 8 + menu.tab * TAB_STEP, y + 22, x + 2 + (menu.tab + 1) * TAB_STEP, y + 23, 0xFF3A2411);
        switch (menu.tab) {
            case 0 -> {
                EMGui.inset(g, T, x + LIST_X, y + LIST_Y, LIST_W, LIST_H);
                EMGui.inset(g, T, x + DET_X, y + LIST_Y, DET_W, 94);
                List<AccountView> list = menu.getAccounts();
                for (int r = 0; r < LIST_ROWS && scroll + r < list.size(); r++)
                    if (list.get(scroll + r).consumer().equals(selected))
                        g.fill(x + LIST_X + 1, y + LIST_Y + 2 + r * ROW, x + LIST_X + LIST_W - 1, y + LIST_Y + 2 + (r + 1) * ROW, 0xFF4A2E14);
            }
            case 1 -> { for (Slot s : menu.slots) if (s.isActive()) EMGui.slot(g, T, x + s.x, y + s.y); }
            case TAB_TERMS -> EMGui.inset(g, T, x + 6, y + 120, W - 12, H - 154);
            case TAB_VAULT -> {
                EMGui.inset(g, T, x + 6, y + 26, W - 12, H - 32);
                List<VaultView> v = menu.getVault();
                for (int i = 0; i < v.size(); i++) {
                    int cx = x + VAULT_X + (i % VAULT_COLS) * CELL, cy = y + VAULT_Y + (i / VAULT_COLS) * CELL;
                    if (cy + 18 > y + H - 18) break;
                    EMGui.slot(g, T, cx + 1, cy + 1);
                    g.renderItem(v.get(i).sample(), cx + 1, cy + 1);
                    g.renderItemDecorations(font, v.get(i).sample(), cx + 1, cy + 1, shortCount(v.get(i).count()));
                }
            }
        }
        renderFieldBacks(g);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        int scr = T.screenText, label = T.labelColor();
        switch (menu.tab) {
            case 0 -> labelsAccounts(g, scr, label);
            case 1 -> {
                EMGui.text(g, font, Component.translatable("create_em.terminal.price_label"), 8, 30, 30, label, false, 0);
                EMGui.text(g, font, Component.translatable("create_em.panel.credits_per"), 106, 30, 38, label, false, 1);
                EMGui.text(g, font, Component.literal("FE"), 235, 30, 16, label, false, 0);
                EMGui.text(g, font, tariffLine(), 8, 46, W - 16, label, false, 0);
                EMGui.text(g, font, Component.translatable("create_em.panel.currency_hint"), 8, 62, W - 16, label, false, 0);
                for (int i = 0; i < CURRENCY_SLOTS; i++) {
                    int cx = CUR_X0 + (i % 2) * CUR_COL, cy = CUR_Y0 + (i / 2) * CUR_ROW;
                    boolean has = menu.slots.get(i).hasItem();
                    if (has) {
                        EMGui.text(g, font, Component.literal("="), cx + 22, cy + 5, 8, label, false, 0);
                        EMGui.text(g, font, Component.translatable("create_em.panel.cr"), cx + 100, cy + 5, 22, label, false, 0);
                    } else {
                        EMGui.text(g, font, Component.translatable("create_em.panel.empty_slot"), cx + 22, cy + 5, 96, 0xFF7A5A34, false, 0);
                    }
                }
                EMGui.text(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 162, label, false, 0);
            }
            case TAB_TERMS -> labelsTerms(g, scr, label);
            case TAB_VAULT -> {
                if (menu.getVault().isEmpty())
                    EMGui.text(g, font, Component.translatable("create_em.panel.vault_empty"), 12, 32, W - 24, scr, false, 0);
                EMGui.text(g, font, Component.translatable("create_em.panel.vault_hint"), 8, H - 16, W - 16, scr, false, 2);
            }
        }
    }

    /** Условия: подписи к полям и итог человеческим языком. */
    private void labelsTerms(GuiGraphics g, int scr, int label) {
        String[] keys = {"create_em.panel.terms_period", "create_em.panel.terms_limit_after", "create_em.panel.terms_percent", "create_em.panel.terms_cut_after"};
        for (int i = 0; i < keys.length; i++)
            EMGui.text(g, font, Component.translatable(keys[i]), 8, TERMS_Y0 + i * TERMS_ROW, TERMS_FIELD_X - 12, label, false, 0);
        EMGui.text(g, font, Component.translatable("create_em.panel.terms_hint"), 8, 108, W - 16, 0xFF6B4A24, false, 0);

        int p = menu.getPeriodDays(), la = menu.getLimitAfterDays(), pct = menu.getLimitPercent(), cut = menu.getCutAfterDays();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("create_em.panel.terms_every", p, p * 20));
        if (cut == 0) lines.add(Component.translatable("create_em.panel.terms_cut_now"));
        else {
            if (la >= 0 && (cut < 0 || la < cut))
                lines.add(la == 0 ? Component.translatable("create_em.panel.terms_limit_now", pct)
                        : Component.translatable("create_em.panel.terms_limit_in", la, pct));
            lines.add(cut < 0 ? Component.translatable("create_em.panel.terms_never_cut")
                    : Component.translatable("create_em.panel.terms_cut_in", cut));
        }
        lines.add(tariffLine());
        for (int i = 0; i < lines.size(); i++) EMGui.text(g, font, lines.get(i), 12, 125 + i * 13, W - 24, scr, false, 0);
    }

    /** «1 кр. = 1M FE · квитанция через 47:12». */
    private Component tariffLine() {
        int s = menu.getNextBillSeconds();
        String next = String.format("%d:%02d", s / 60, s % 60);
        if (menu.getPrice() <= 0) return Component.translatable("create_em.panel.tariff_free", next);
        return Component.translatable("create_em.panel.tariff_line", menu.getPrice(), EMFormat.fe(menu.getPerFe()), next);
    }

    private void labelsAccounts(GuiGraphics g, int scr, int label) {
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
        AccountView s = sel();
        int dx = DET_X + 4, dy = LIST_Y + 5, dw = DET_W - 8;
        if (s == null) {
            EMGui.text(g, font, Component.translatable("create_em.panel.select_hint"), dx, dy, dw, scr, false, 0);
            return;
        }
        BillingStage st = BillingStage.values()[Math.max(0, Math.min(3, s.stage()))];
        Component[] lines = {
                Component.literal(s.name()).withStyle(ChatFormatting.WHITE),
                Component.translatable("create_em.terminal.balance", s.balance()),
                Component.translatable("create_em.terminal.since", EMFormat.fe(s.sinceFe())),
                Component.translatable("create_em.panel.unbilled", EMFormat.fe(s.unbilledFe())),
                s.disabled() ? Component.translatable("create_em.panel.state_off").withStyle(ChatFormatting.RED)
                        : Component.translatable(st.key()).withStyle(stageFmt(st)),
                s.cap() > 0 ? Component.translatable("create_em.panel.cap", String.format("%,d FE/t", s.cap()))
                        : Component.translatable("create_em.panel.no_cap"),
                Component.translatable(s.prepaid() ? "create_em.panel.mode_pre" : "create_em.panel.mode_post"),
                s.ownPrice() >= 0 ? Component.translatable("create_em.panel.own_tariff", s.ownPrice(), EMFormat.fe(s.ownPerFe()))
                        : Component.translatable("create_em.panel.common_tariff")};
        for (int i = 0; i < lines.length; i++) EMGui.text(g, font, lines[i], dx, dy - 2 + i * 11, dw, scr, false, 0);
        EMGui.text(g, font, Component.translatable("create_em.panel.balance_label"), DET_X, 142, DET_W, label, false, 0);
        EMGui.text(g, font, Component.translatable("create_em.panel.cap_label"), DET_X, 167, DET_W, label, false, 0);
        EMGui.text(g, font, Component.translatable(s.ownPrice() >= 0 ? "create_em.panel.own_label" : "create_em.panel.own_label_common"),
                DET_X, 192, DET_W, label, false, 0);
        EMGui.text(g, font, Component.translatable("create_em.panel.per_short"), DET_X + 41, 204, 20, label, false, 1);
    }

    private static ChatFormatting stageFmt(BillingStage st) {
        return switch (st) { case NORMAL -> ChatFormatting.GREEN; case WARNING, LIMITED -> ChatFormatting.GOLD; case CUT -> ChatFormatting.RED; };
    }

    private static int stageColor(int stage) {
        return switch (stage) { case 0 -> 0xFFB8F0A0; case 1, 2 -> 0xFFFFC266; default -> 0xFFFF6B5A; };
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
        if (menu.tab == TAB_VAULT) {
            int rx = mx - leftPos - VAULT_X, ry = my - topPos - VAULT_Y;
            if (rx >= 0 && ry >= 0 && rx / CELL < VAULT_COLS) {
                int i = (ry / CELL) * VAULT_COLS + rx / CELL;
                if (i < menu.getVault().size()) {
                    VaultView v = menu.getVault().get(i);
                    g.renderTooltip(font, List.of(v.sample().getHoverName(), Component.literal(String.format("× %,d", v.count()))),
                            Optional.empty(), mx, my);
                }
            }
        }
    }
}
