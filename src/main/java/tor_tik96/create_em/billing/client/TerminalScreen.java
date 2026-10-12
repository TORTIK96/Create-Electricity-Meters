package tor_tik96.create_em.billing.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import tor_tik96.create_em.EMFormat;
import tor_tik96.create_em.billing.BillingStage;
import tor_tik96.create_em.billing.TerminalMenu;
import tor_tik96.create_em.client.EMButton;
import tor_tik96.create_em.client.EMGui;

import static tor_tik96.create_em.billing.BillingData.CURRENCY_SLOTS;
import static tor_tik96.create_em.billing.TerminalMenu.*;

/** Терминал абонента в латунной теме: оплата слева, лицевой счёт на экране, ниже — принимаемая валюта. */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    private static final EMGui.Theme T = EMGui.Theme.BRASS;
    private static final int SX = 86, SY = 16, SW = W - 6 - 86, SH = 78;

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
        addRenderableWidget(new EMButton(leftPos + 8, topPos + PAY_Y + 22, 72, 16, Component.translatable("create_em.terminal.pay"), () -> T, () -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BTN_PAY);
        }));
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        EMGui.panel(g, T, leftPos, topPos, W, H);
        EMGui.inset(g, T, leftPos + SX, topPos + SY, SW, SH);
        for (Slot s : menu.slots) {
            boolean acceptedSlot = s.index >= 1 && s.index <= CURRENCY_SLOTS;
            if (acceptedSlot && !s.hasItem()) continue; // пустые ячейки валюты не рисуем
            EMGui.slot(g, T, leftPos + s.x, topPos + s.y);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        int label = T.labelColor(), scr = T.screenText;
        EMGui.text(g, font, title, 8, 6, W - 16, label, false, 0);
        EMGui.text(g, font, Component.translatable("create_em.terminal.payment"), 8, PAY_Y - 11, 72, label, false, 0);
        EMGui.text(g, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 162, label, false, 0);

        long bal = menu.getBalance();
        BillingStage st = menu.getStage();
        ChatFormatting sc = switch (st) { case NORMAL -> ChatFormatting.GREEN; case WARNING, LIMITED -> ChatFormatting.GOLD; case CUT -> ChatFormatting.RED; };
        int ns = menu.getNextBillSeconds();
        Component[] lines = {
                menu.getPrice() <= 0 ? Component.translatable("create_em.terminal.free")
                        : Component.translatable("create_em.terminal.tariff", menu.getPrice(), EMFormat.fe(menu.getPerFe())),
                Component.translatable("create_em.terminal.balance", bal).withStyle(bal < 0 ? ChatFormatting.RED : ChatFormatting.RESET),
                Component.translatable("create_em.terminal.since", EMFormat.fe(menu.getSinceFe())),
                Component.translatable("create_em.terminal.unbilled", EMFormat.fe(menu.getUnbilledFe())),
                Component.translatable("create_em.terminal.stage").append(Component.translatable(st.key()).withStyle(sc)),
                Component.translatable("create_em.terminal.next_bill", String.format("%d:%02d", ns / 60, ns % 60))};
        for (int i = 0; i < lines.length; i++)
            EMGui.text(g, font, lines[i], SX + 5, SY + 5 + i * 12, SW - 10, scr, false, 0);

        boolean any = false;
        for (int i = 0; i < CURRENCY_SLOTS; i++) {
            Slot s = menu.slots.get(1 + i);
            if (!s.hasItem()) continue;
            any = true;
            EMGui.text(g, font, Component.translatable("create_em.unit.credits", menu.getValue(i)),
                    s.x - 9, s.y + 20, 34, label, false, 1);
        }
        EMGui.text(g, font, Component.translatable(any ? "create_em.terminal.accepts" : "create_em.terminal.no_currency"),
                8, CUR_Y - 11, W - 16, label, false, 0);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
