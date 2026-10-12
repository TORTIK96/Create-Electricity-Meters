package tor_tik96.create_em.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import tor_tik96.create_em.Create_em;

/**
 * Отрисовка интерфейсов в стиле Create: латунные и андезитовые панели, утопленные «экраны»,
 * рамки слотов и текст, который ужимается, если не влезает. Всё берётся из одной текстуры
 * textures/gui/widgets.png (128×128).
 */
public final class EMGui {
    public static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(Create_em.MODID, "textures/gui/widgets.png");
    private static final int TS = 128;

    public enum Theme {
        BRASS(0, 64, 0, 56, 0xFF3A2411, 0xFFFFD9A0, 0xFF6B5A44, false),
        ANDESITE(32, 96, 18, 72, 0xFFFFFFFF, 0xFFD6E4DA, 0xFF9A9D9B, true);

        final int panelU, insetU, slotU, buttonV;
        /** Текст на кнопке, текст на тёмном экране, текст неактивной кнопки. */
        public final int buttonText, screenText, disabledText;
        public final boolean buttonShadow;

        Theme(int panelU, int insetU, int slotU, int buttonV, int buttonText, int screenText, int disabledText, boolean buttonShadow) {
            this.panelU = panelU; this.insetU = insetU; this.slotU = slotU; this.buttonV = buttonV;
            this.buttonText = buttonText; this.screenText = screenText; this.disabledText = disabledText; this.buttonShadow = buttonShadow;
        }

        /** Цвет подписей прямо на панели. */
        public int labelColor() { return this == BRASS ? 0xFF3A2411 : 0xFF1B1D1C; }
    }

    /** Основная панель окна (рамка 4 px, середина тайлится, чтобы фактура не растягивалась). */
    public static void panel(GuiGraphics g, Theme t, int x, int y, int w, int h) {
        nine(g, x, y, w, h, t.panelU, 0, 32, 32, 4, true);
    }

    /** Утопленный тёмный экран. */
    public static void inset(GuiGraphics g, Theme t, int x, int y, int w, int h) {
        nine(g, x, y, w, h, t.insetU, 0, 32, 32, 1, false);
    }

    /** Рамка слота: x, y — позиция предмета в слоте. */
    public static void slot(GuiGraphics g, Theme t, int x, int y) {
        g.blit(TEX, x - 1, y - 1, t.slotU, 32, 18, 18, TS, TS);
    }

    /** Кнопка: state 0 — обычная, 1 — под курсором, 2 — неактивна. */
    public static void button(GuiGraphics g, Theme t, int x, int y, int w, int h, int state) {
        nine(g, x, y, w, h, state * 32, t.buttonV, 32, 16, 3, false);
    }

    /** Текст в рамке ширины maxW: если не влезает — ужимается. align: 0 — влево, 1 — по центру, 2 — вправо. */
    public static void text(GuiGraphics g, Font font, Component c, int x, int y, int maxW, int color, boolean shadow, int align) {
        int w = font.width(c);
        if (w * 0.85f > maxW) {
            // сильно не влезает — обрезаем с многоточием, а не сжимаем до нечитаемого
            String cut = font.plainSubstrByWidth(c.getString(), maxW - font.width("…")) + "…";
            int col = c.getStyle().getColor() != null ? (0xFF000000 | c.getStyle().getColor().getValue()) : color;
            int dx = align == 1 ? x + (maxW - font.width(cut)) / 2 : align == 2 ? x + maxW - font.width(cut) : x;
            g.drawString(font, cut, dx, y, col, shadow);
            return;
        }
        float k = w > maxW ? maxW / (float) w : 1f;
        float drawW = w * k;
        float dx = align == 1 ? x + (maxW - drawW) / 2f : align == 2 ? x + maxW - drawW : x;
        g.pose().pushPose();
        g.pose().translate(dx, y + (1 - k) * 4f, 0); // держим строку по центру по вертикали
        g.pose().scale(k, k, 1f);
        g.drawString(font, c, 0, 0, color, shadow);
        g.pose().popPose();
    }

    private static void nine(GuiGraphics g, int x, int y, int w, int h, int u, int v, int sw, int sh, int b, boolean tile) {
        int cw = sw - 2 * b, ch = sh - 2 * b;
        // углы
        g.blit(TEX, x, y, u, v, b, b, TS, TS);
        g.blit(TEX, x + w - b, y, u + sw - b, v, b, b, TS, TS);
        g.blit(TEX, x, y + h - b, u, v + sh - b, b, b, TS, TS);
        g.blit(TEX, x + w - b, y + h - b, u + sw - b, v + sh - b, b, b, TS, TS);
        if (!tile) {
            stretch(g, x + b, y, w - 2 * b, b, u + b, v, cw, b);
            stretch(g, x + b, y + h - b, w - 2 * b, b, u + b, v + sh - b, cw, b);
            stretch(g, x, y + b, b, h - 2 * b, u, v + b, b, ch);
            stretch(g, x + w - b, y + b, b, h - 2 * b, u + sw - b, v + b, b, ch);
            stretch(g, x + b, y + b, w - 2 * b, h - 2 * b, u + b, v + b, cw, ch);
            return;
        }
        for (int px = x + b; px < x + w - b; px += cw) {
            int pw = Math.min(cw, x + w - b - px);
            g.blit(TEX, px, y, u + b, v, pw, b, TS, TS);
            g.blit(TEX, px, y + h - b, u + b, v + sh - b, pw, b, TS, TS);
        }
        for (int py = y + b; py < y + h - b; py += ch) {
            int ph = Math.min(ch, y + h - b - py);
            g.blit(TEX, x, py, u, v + b, b, ph, TS, TS);
            g.blit(TEX, x + w - b, py, u + sw - b, v + b, b, ph, TS, TS);
            for (int px = x + b; px < x + w - b; px += cw) {
                int pw = Math.min(cw, x + w - b - px);
                g.blit(TEX, px, py, u + b, v + b, pw, ph, TS, TS);
            }
        }
    }

    private static void stretch(GuiGraphics g, int x, int y, int w, int h, int u, int v, int uw, int vh) {
        if (w <= 0 || h <= 0) return;
        g.blit(TEX, x, y, w, h, u, v, uw, vh, TS, TS);
    }

    private EMGui() {}
}
