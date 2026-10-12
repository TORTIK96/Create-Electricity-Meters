package tor_tik96.create_em.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** Кнопка в стиле Create: текстура из EMGui, тема меняется на лету (латунь / андезит). */
public class EMButton extends AbstractButton {
    private final Runnable action;
    private final Supplier<EMGui.Theme> theme;

    public EMButton(int x, int y, int w, int h, Component label, Supplier<EMGui.Theme> theme, Runnable action) {
        super(x, y, w, h, label);
        this.theme = theme;
        this.action = action;
    }

    @Override public void onPress() { action.run(); }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float pt) {
        EMGui.Theme t = theme.get();
        int state = !active ? 2 : isHoveredOrFocused() ? 1 : 0;
        EMGui.button(g, t, getX(), getY(), width, height, state);
        var font = Minecraft.getInstance().font;
        int color = active ? t.buttonText : t.disabledText;
        EMGui.text(g, font, getMessage(), getX() + 3, getY() + (height - 8) / 2 + 1, width - 6, color, active && t.buttonShadow, 1);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
}
