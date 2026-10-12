package tor_tik96.create_em.billing.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import tor_tik96.create_em.billing.BillingStage;
import tor_tik96.create_em.billing.TerminalMenu;

import java.util.ArrayList;
import java.util.List;

import static tor_tik96.create_em.billing.TerminalMenu.*;

/** Экран терминала в цветах латунного корпуса, без отдельной текстуры. */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    private static final int EDGE = 0xFF724731, FRAME = 0xFFCEA05A, LIGHT = 0xFFFFEB8C, PANEL = 0xFF3A2411,
            SLOT_BG = 0xFF1F140A, TEXT = 0xFF2A1A0B, SCREEN_TEXT = 0xFFFFD9A0;

    private Button payButton;
    private final List<Button> ownerButtons = new ArrayList<>();

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
        ownerButtons.clear();
        payButton = addRenderableWidget(Button.builder(Component.translatable("create_em.terminal.pay"),
                b -> click(BTN_PAY)).bounds(leftPos + 28, topPos + 56, 46, 20).build());
        // тариф: цена и объём, Shift — шаг 10
        ownerButtons.add(small(84, 28, "−", BTN_PRICE_DOWN));
        ownerButtons.add(small(98, 28, "+", BTN_PRICE_UP));
        ownerButtons.add(small(84, 56, "−", BTN_PER_DOWN));
        ownerButtons.add(small(98, 56, "+", BTN_PER_UP));
    }

    private Button small(int x, int y, String label, int id) {
        return addRenderableWidget(Button.builder(Component.literal(label),
                b -> click(Screen.hasShiftDown() ? id + BTN_X10 : id)).bounds(leftPos + x, topPos + y, 13, 13).build());
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean owner = menu.isOwner();
        for (Button b : ownerButtons) b.visible = owner;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x0 = leftPos, y0 = topPos, x1 = leftPos + imageWidth, y1 = topPos + imageHeight;
        g.fill(x0, y0, x1, y1, EDGE);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, FRAME);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, LIGHT);
        // «экран» с данными
        int sx1 = menu.isOwner() ? x0 + CASH_X - 6 : x1 - 6;
        g.fill(x0 + 80, y0 + 15, sx1, y0 + 92, PANEL);
        // фон под слотами
        for (Slot s : menu.slots)
            if (s.isActive()) g.fill(x0 + s.x - 1, y0 + s.y - 1, x0 + s.x + 17, y0 + s.y + 17, SLOT_BG);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 5, TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        g.drawString(font, Component.translatable("create_em.terminal.currency"), 28, SAMPLE_Y + 4, TEXT, false);
        g.drawString(font, Component.translatable("create_em.terminal.payment"), PAY_X, PAY_Y - 10, TEXT, false);

        int x = 84, y = 18;
        if (menu.isOwner()) {
            line(g, Component.translatable("create_em.terminal.price", menu.getPrice()), x + 30, y + 13);
            line(g, Component.translatable("create_em.terminal.tariff_title"), x, y);
            line(g, Component.translatable("create_em.terminal.per", menu.getPerKwh()), x + 30, y + 41);
            line(g, Component.translatable("create_em.terminal.accounts", menu.getAccounts()), x, y + 56);
            line(g, Component.translatable("create_em.terminal.debt", menu.getTotalDebt()), x, y + 66);
            g.drawString(font, Component.translatable("create_em.terminal.cashbox"), CASH_X, 8, TEXT, false);
        } else {
            line(g, Component.translatable("create_em.terminal.tariff", menu.getPrice(), menu.getPerKwh()), x, y);
            int bal = menu.getBalance();
            line(g, Component.translatable("create_em.terminal.balance", bal).withStyle(bal < 0 ? ChatFormatting.RED : ChatFormatting.RESET), x, y + 13);
            line(g, Component.translatable("create_em.terminal.since", String.format("%.2f", menu.getSinceKwh())), x, y + 26);
            line(g, Component.translatable("create_em.terminal.unbilled", String.format("%.2f", menu.getUnbilledKwh())), x, y + 39);
            BillingStage st = menu.getStage();
            ChatFormatting c = switch (st) { case NORMAL -> ChatFormatting.GREEN; case WARNING, LIMITED -> ChatFormatting.GOLD; case CUT -> ChatFormatting.RED; };
            line(g, Component.translatable("create_em.terminal.stage").append(Component.translatable(st.key()).withStyle(c)), x, y + 52);
        }
    }

    private void line(GuiGraphics g, Component c, int x, int y) {
        g.drawString(font, c, x, y, SCREEN_TEXT, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
