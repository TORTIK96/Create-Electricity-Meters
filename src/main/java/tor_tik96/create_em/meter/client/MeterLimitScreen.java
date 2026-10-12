package tor_tik96.create_em.meter.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import tor_tik96.create_em.meter.MeterLimitMenu;
import tor_tik96.create_em.meter.MeterTier;

import java.util.ArrayList;
import java.util.List;

/**
 * Меню лимита: крупное значение, шкала коридора, кнопки шага.
 * Рисуется заливками в цветах корпуса (андезит / латунь), без отдельной текстуры.
 */
public class MeterLimitScreen extends AbstractContainerScreen<MeterLimitMenu> {
    private final List<Button> buttons = new ArrayList<>();

    public MeterLimitScreen(MeterLimitMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 196;
        imageHeight = 130;
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        MeterTier t = menu.getTier();
        int y = topPos + 68, x = leftPos + 10, w = 42, gap = 2;
        add(x, y, w, "−" + t.largeStep, MeterLimitMenu.BTN_MINUS_LARGE);
        add(x + (w + gap), y, w, "−" + t.smallStep, MeterLimitMenu.BTN_MINUS_SMALL);
        add(x + (w + gap) * 2, y, w, "+" + t.smallStep, MeterLimitMenu.BTN_PLUS_SMALL);
        add(x + (w + gap) * 3, y, w, "+" + t.largeStep, MeterLimitMenu.BTN_PLUS_LARGE);
        y += 22;
        int half = (w * 4 + gap * 3 - gap) / 2;
        add(x, y, half, Component.translatable("create_em.limit.min").getString(), MeterLimitMenu.BTN_MIN);
        add(x + half + gap, y, half, Component.translatable("create_em.limit.max").getString(), MeterLimitMenu.BTN_MAX);
    }

    private void add(int x, int y, int w, String label, int id) {
        Button b = Button.builder(Component.literal(label), btn -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }).bounds(x, y, w, 18).build();
        buttons.add(addRenderableWidget(b));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean edit = menu.canEdit();
        for (Button b : buttons) b.active = edit;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        boolean brass = menu.getTier() == MeterTier.BRASS;
        int edge = brass ? 0xFF724731 : 0xFF353B39;
        int frame = brass ? 0xFFCEA05A : 0xFF828784;
        int light = brass ? 0xFFFFEB8C : 0xFFA8B3AB;
        int inner = brass ? 0xFF3A2411 : 0xFF2B2D33;

        int x0 = leftPos, y0 = topPos, x1 = leftPos + imageWidth, y1 = topPos + imageHeight;
        g.fill(x0, y0, x1, y1, edge);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, frame);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, light);
        g.fill(x0 + 4, y0 + 16, x1 - 4, y0 + 62, inner);

        // шкала коридора лимита
        int min = menu.getMin(), max = Math.max(menu.getMin() + 1, menu.getMax());
        float k = (menu.getLimit() - min) / (float) (max - min);
        int bx0 = x0 + 12, bx1 = x1 - 12, by = y0 + 52;
        g.fill(bx0, by, bx1, by + 4, 0xFF16171A);
        g.fill(bx0, by, bx0 + Math.round((bx1 - bx0) * Math.max(0, Math.min(1, k))), by + 4, brass ? 0xFFF39A2F : 0xFF58B244);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 5, 0xFF16171A, false);

        String value = String.format("%,d FE/t", menu.getLimit());
        g.pose().pushPose();
        g.pose().translate(imageWidth / 2f, 22, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, value, -font.width(value) / 2, 0, 0xFFFFB35C, false);
        g.pose().popPose();

        String range = String.format("%,d — %,d FE/t", menu.getMin(), menu.getMax());
        g.drawString(font, range, (imageWidth - font.width(range)) / 2, 41, 0xFF9AA49D, false);

        Component status = !menu.canEdit()
                ? Component.translatable(menu.isSealed() ? "create_em.limit.locked_sealed" : "create_em.limit.locked_owner").withStyle(ChatFormatting.DARK_RED)
                : Component.translatable(menu.isSealed() ? "create_em.limit.sealed" : "create_em.limit.unsealed");
        g.drawString(font, status, (imageWidth - font.width(status)) / 2, 115, 0xFF16171A, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
