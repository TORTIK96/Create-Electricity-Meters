package tor_tik96.create_em.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;
import tor_tik96.create_em.EMFormat;
import tor_tik96.create_em.panel.ValuePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Экран с числовыми полями ввода. Поле отправляет значение на сервер по Enter, кнопке «OK»
 * или когда с него уходит фокус. Понимает «1500», «2.5k», «3M», «1G».
 * Пока поле в фокусе, клавиши (в том числе E) идут в поле, а не закрывают окно.
 */
public abstract class EMScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    protected final List<NumberField> fields = new ArrayList<>();

    protected EMScreen(M menu, Inventory inv, Component title) { super(menu, inv, title); }

    protected abstract EMGui.Theme theme();

    /** Поле ввода: x, y, w — вся ширина вместе с кнопкой OK (20 px); высота 14. */
    protected NumberField field(int x, int y, int w, int fieldId, LongSupplier current, Supplier<UUID> target,
                                BooleanSupplier visible, Component hint) {
        NumberField f = new NumberField(x, y, w, fieldId, current, target, visible);
        f.box.setHint(hint);
        fields.add(f);
        addRenderableWidget(f.box);
        addRenderableWidget(f.ok);
        return f;
    }

    @Override
    protected void init() {
        super.init();
        fields.clear();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        for (NumberField f : fields) f.tick();
    }

    /** Фон полей — вызывать из renderBg. */
    protected void renderFieldBacks(GuiGraphics g) {
        for (NumberField f : fields)
            if (f.visible.getAsBoolean()) EMGui.inset(g, theme(), f.x, f.y, f.w - 21, 14);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) return super.keyPressed(key, scan, mods); // Esc закрывает как обычно
        for (NumberField f : fields) {
            if (!f.box.isFocused() || !f.box.visible) continue;
            if (key == 257 || key == 335) { f.submit(); f.box.setFocused(false); return true; } // Enter
            return f.box.keyPressed(key, scan, mods) || f.box.canConsumeInput() || super.keyPressed(key, scan, mods);
        }
        return super.keyPressed(key, scan, mods);
    }

    public class NumberField {
        final int x, y, w, fieldId;
        final LongSupplier current;
        final Supplier<UUID> target;
        final BooleanSupplier visible;
        public final EditBox box;
        final EMButton ok;
        private long shown = Long.MIN_VALUE;
        private boolean wasFocused;

        NumberField(int x, int y, int w, int fieldId, LongSupplier current, Supplier<UUID> target, BooleanSupplier visible) {
            this.x = x; this.y = y; this.w = w; this.fieldId = fieldId;
            this.current = current; this.target = target; this.visible = visible;
            box = new EditBox(font, x + 4, y + 3, w - 21 - 6, 10, Component.empty());
            box.setBordered(false);
            box.setMaxLength(18);
            box.setTextColor(theme().screenText);
            box.setFilter(EMFormat::allowed);
            ok = new EMButton(x + w - 20, y, 20, 14, Component.literal("OK"), EMScreen.this::theme, this::submit);
        }

        void tick() {
            boolean vis = visible.getAsBoolean();
            box.visible = vis;
            ok.visible = vis;
            if (!vis) { box.setFocused(false); return; }
            // ушли с поля — применить, если изменили
            if (wasFocused && !box.isFocused() && EMFormat.parse(box.getValue()) != null
                    && EMFormat.parse(box.getValue()) != current.getAsLong()) submit();
            wasFocused = box.isFocused();
            long now = current.getAsLong();
            if (!box.isFocused() && now != shown) {
                shown = now;
                box.setValue(EMFormat.plain(now));
            }
        }

        void submit() {
            Long v = EMFormat.parse(box.getValue());
            if (v == null) { box.setValue(EMFormat.plain(current.getAsLong())); return; }
            UUID t = target.get();
            PacketDistributor.sendToServer(new ValuePayload(menu.containerId, fieldId, t == null ? ValuePayload.NONE : t, v));
            shown = v;          // не затираем ввод старым значением, пока сервер не ответит
            box.setValue(EMFormat.plain(v));
        }
    }
}
