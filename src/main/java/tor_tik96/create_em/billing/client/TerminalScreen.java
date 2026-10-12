package tor_tik96.create_em.billing.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import tor_tik96.create_em.billing.BillingStage;
import tor_tik96.create_em.billing.TerminalMenu;
import tor_tik96.create_em.client.EMButton;
import tor_tik96.create_em.client.EMGui;

import java.util.List;

import static tor_tik96.create_em.billing.TerminalMenu.*;

/**
 * Терминал в латунной теме. Слева — валюта и оплата, справа — экран.
 * Поставщик: тариф с кнопками прямо на экране, «Выставить счета», касса в ряд.
 * Абонент: экран на всю высоту с лицевым счётом.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    private static final EMGui.Theme T = EMGui.Theme.BRASS;
    private static final int SCREEN_X = 84, SCREEN_Y = 16, SCREEN_W = W - 6 - 84;
    private static final int OWNER_SCREEN_H = 58, USER_SCREEN_H = 97;
    private static final int TEXT_X = SCREEN_X + 5, TEXT_W = SCREEN_W - 10;

    private EMButton pay, bill;
    private List<EMButton> tariffButtons;

    public TerminalScreen(TerminalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = H;
        inventoryLabelX = INV_X;
        inventoryLabelY = INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        pay = add(x + 8, y + PAY_Y + 20, 72, 16, Component.translatable("create_em.terminal.pay"), BTN_PAY, false);
        bill = add(x + SCREEN_X, y + 78, SCREEN_W, 16, Component.translatable("create_em.terminal.bill_now"), BTN_BILL, false);
        int bx = x + SCREEN_X + 86;
        tariffButtons = List.of(
                add(bx, y + SCREEN_Y + 4, 12, 12, Component.literal("−"), BTN_PRICE_DOWN, true),
                add(bx + 62, y + SCREEN_Y + 4, 12, 12, Component.literal("+"), BTN_PRICE_UP, true),
                add(bx, y + SCREEN_Y + 18, 12, 12, Component.literal("−"), BTN_PER_DOWN, true),
                add(bx + 62, y + SCREEN_Y + 18, 12, 12, Component.literal("+"), BTN_PER_UP, true));
        refresh();
    }

    /** shiftStep — с Shift шаг 10. */
    private EMButton add(int x, int y, int w, int h, Component label, int id, boolean shiftStep) {
        return addRenderableWidget(new EMButton(x, y, w, h, label, () -> T, () -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        shiftStep && Screen.hasShiftDown() ? id + BTN_X10 : id);
        }));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    private void refresh() {
        boolean owner = menu.isOwner();
        bill.visible = owner;
        for (EMButton b : tariffButtons) b.visible = owner;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        EMGui.panel(g, T, x, y, W, H);
        EMGui.inset(g, T, x + SCREEN_X, y + SCREEN_Y, SCREEN_W, menu.isOwner() ? OWNER_SCREEN_H : USER_SCREEN_H);
        for (Slot s : menu.slots)
            if (s.isActive()) EMGui.slot(g, T, x + s.x, y + s.y);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        int label = T.labelColor(), scr = T.screenText;
        EMGui.text(g, font, title, 8, 6, W - 16, label, false, 0);
        EMGui.text(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 162, label, false, 0);
        EMGui.text(g, font, Component.translatable("create_em.terminal.currency"), 8, SAMPLE_Y - 10, 72, label, false, 0);
        EMGui.text(g, font, Component.translatable("create_em.terminal.payment"), 8, PAY_Y - 10, 72, label, false, 0);

        int ty = SCREEN_Y + 6;
        if (menu.isOwner()) {
            EMGui.text(g, font, Component.translatable("create_em.terminal.price_label"), TEXT_X, ty, 80, scr, false, 0);
            EMGui.text(g, font, Component.translatable("create_em.unit.items", menu.getPrice()), SCREEN_X + 99, ty, 48, scr, false, 1);
            EMGui.text(g, font, Component.translatable("create_em.terminal.per_label"), TEXT_X, ty + 14, 80, scr, false, 0);
            EMGui.text(g, font, Component.translatable("create_em.unit.kwh", menu.getPerKwh()), SCREEN_X + 99, ty + 14, 48, scr, false, 1);
            EMGui.text(g, font, Component.translatable("create_em.terminal.owner_summary", menu.getAccounts(), menu.getTotalDebt()),
                    TEXT_X, ty + 28, TEXT_W, scr, false, 0);
            EMGui.text(g, font, nextBill(), TEXT_X, ty + 40, TEXT_W, scr, false, 0);
            EMGui.text(g, font, Component.translatable("create_em.terminal.cashbox"), 8, CASH_Y + 5, 72, label, false, 0);
        } else {
            int bal = menu.getBalance();
            BillingStage st = menu.getStage();
            ChatFormatting sc = switch (st) { case NORMAL -> ChatFormatting.GREEN; case WARNING, LIMITED -> ChatFormatting.GOLD; case CUT -> ChatFormatting.RED; };
            Component[] lines = {
                    Component.translatable("create_em.terminal.tariff", menu.getPrice(), menu.getPerKwh()),
                    Component.translatable("create_em.terminal.balance", bal).withStyle(bal < 0 ? ChatFormatting.RED : ChatFormatting.RESET),
                    Component.translatable("create_em.terminal.since", String.format("%.2f", menu.getSinceKwh())),
                    Component.translatable("create_em.terminal.unbilled", String.format("%.2f", menu.getUnbilledKwh())),
                    Component.translatable("create_em.terminal.stage").append(Component.translatable(st.key()).withStyle(sc)),
                    nextBill()};
            for (int i = 0; i < lines.length; i++)
                EMGui.text(g, font, lines[i], TEXT_X, ty + i * 14, TEXT_W, scr, false, 0);
        }
    }

    private Component nextBill() {
        int s = menu.getNextBillSeconds();
        return Component.translatable("create_em.terminal.next_bill", String.format("%d:%02d", s / 60, s % 60));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
