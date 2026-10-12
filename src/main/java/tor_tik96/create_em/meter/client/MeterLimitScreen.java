package tor_tik96.create_em.meter.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import tor_tik96.create_em.client.EMButton;
import tor_tik96.create_em.client.EMGui;
import tor_tik96.create_em.client.EMScreen;
import tor_tik96.create_em.meter.MeterLimitMenu;
import tor_tik96.create_em.meter.MeterTier;

/**
 * Меню лимита счётчика: экран с текущим лимитом и шкалой коридора, поле ввода лимита
 * (с понятными сокращениями 2k, 8M), кнопки «Минимум» и «Максимум».
 */
public class MeterLimitScreen extends EMScreen<MeterLimitMenu> {
    private static final int W = 200, H = 144;

    private EMButton min, max, rearm;
    private NumberField limitField;

    public MeterLimitScreen(MeterLimitMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = H;
    }

    @Override
    protected EMGui.Theme theme() { return menu.getTier() == MeterTier.BRASS ? EMGui.Theme.BRASS : EMGui.Theme.ANDESITE; }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        limitField = field(x + 6, y + 99, W - 12, MeterLimitMenu.F_LIMIT, () -> menu.getLimit(), () -> null,
                menu::canEdit, Component.translatable("create_em.limit.hint"));
        min = btn(x + 6, y + 119, 93, MeterLimitMenu.BTN_MIN, Component.translatable("create_em.limit.min"));
        max = btn(x + 101, y + 119, 93, MeterLimitMenu.BTN_MAX, Component.translatable("create_em.limit.max"));
        rearm = btn(x + 6, y + 75, W - 12, MeterLimitMenu.BTN_REARM, Component.translatable("create_em.limit.rearm"));
        rearm.visible = false;
    }

    private EMButton btn(int x, int y, int w, int id, Component label) {
        return addRenderableWidget(new EMButton(x, y, w, 18, label, this::theme, () -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean edit = menu.canEdit();
        min.active = edit;
        max.active = edit;
        rearm.visible = menu.isTripped();
        rearm.active = menu.canRearm();
        limitField.box.setTextColor(theme().screenText);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        EMGui.Theme t = theme();
        EMGui.panel(g, t, leftPos, topPos, W, H);
        EMGui.inset(g, t, leftPos + 6, topPos + 17, W - 12, 54);
        int lo = menu.getMin(), hi = Math.max(lo + 1, menu.getMax());
        float k = Math.max(0, Math.min(1, (menu.getLimit() - lo) / (float) (hi - lo)));
        int bx0 = leftPos + 14, bx1 = leftPos + W - 14, by = topPos + 58;
        g.fill(bx0, by, bx1, by + 4, 0xFF0B0907);
        g.fill(bx0, by, bx0 + Math.round((bx1 - bx0) * k), by + 4, t == EMGui.Theme.BRASS ? 0xFFF39A2F : 0xFF58B244);
        renderFieldBacks(g);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        EMGui.Theme t = theme();
        EMGui.text(g, font, Component.translatable("create_em.limit.title"), 6, 6, W - 12, t.labelColor(), false, 1);

        String value = String.format("%,d FE/t", menu.getLimit());
        g.pose().pushPose();
        g.pose().translate(W / 2f, 23, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, value, -font.width(value) / 2, 0, 0xFFFFB35C, false);
        g.pose().popPose();

        EMGui.text(g, font, Component.literal(String.format("%,d — %,d FE/t", menu.getMin(), menu.getMax())),
                6, 44, W - 12, 0xFF9AA49D, false, 1);

        if (!menu.isTripped()) {
            Component status = !menu.canEdit()
                    ? Component.translatable(menu.isSealed() ? "create_em.limit.locked_sealed" : "create_em.limit.locked_owner").withStyle(ChatFormatting.DARK_RED)
                    : Component.translatable(menu.isSealed() ? "create_em.limit.sealed" : "create_em.limit.unsealed");
            EMGui.text(g, font, status, 6, 80, W - 12, t.labelColor(), false, 1);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
