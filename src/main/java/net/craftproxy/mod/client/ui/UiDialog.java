package net.craftproxy.mod.client.ui;

import net.craftproxy.mod.client.compat.Gfx;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Small centered confirmation dialog (title, wrapped message, cancel + confirm buttons).
 */
public class UiDialog {
    private static final int WIDTH = 220;

    private final Component title;
    private final Component message;
    private final UiButton cancel;
    private final UiButton confirm;
    private final Anim appear = new Anim();
    private boolean closed;
    private int x, y, h;
    private List<FormattedCharSequence> lines = List.of();

    public UiDialog(Component title, Component message, Component confirmLabel, boolean danger, Runnable onConfirm) {
        this.title = title;
        this.message = message;
        this.cancel = new UiButton(Component.translatable("gui.cancel"), null, UiButton.Style.SECONDARY, () -> closed = true);
        this.confirm = new UiButton(confirmLabel, null, danger ? UiButton.Style.DANGER : UiButton.Style.PRIMARY, () -> {
            closed = true;
            onConfirm.run();
        });
    }

    public boolean isClosed() {
        return closed;
    }

    public void close() {
        closed = true;
    }

    private void layout(Font font, int screenW, int screenH) {
        lines = font.split(message, WIDTH - 24);
        h = 30 + lines.size() * 10 + 34;
        x = (screenW - WIDTH) / 2;
        y = (screenH - h) / 2;
        int bw = (WIDTH - 24 - 6) / 2;
        cancel.bounds(x + 12, y + h - 30, bw, 18);
        confirm.bounds(x + 12 + bw + 6, y + h - 30, bw, 18);
    }

    public void draw(Gfx g, Font font, int mx, int my, int screenW, int screenH) {
        layout(font, screenW, screenH);
        float t = Anim.easeOutCubic(appear.update(1.0F, 14.0F));
        float savedAlpha = UiRender.alpha;
        UiRender.alpha = savedAlpha * t;

        UiRender.fill(g, 0, 0, screenW, screenH, 0x90000000);
        int top = y + Math.round((1.0F - t) * 6);
        UiRender.shadow(g, x, top, WIDTH, h, 5, 4);
        UiRender.card(g, x, top, WIDTH, h, 5, Theme.WINDOW, Theme.BORDER_STRONG);
        UiRender.text(g, font, title.copy().withStyle(s -> s.withBold(true)), x + 12, top + 12, Theme.TEXT);
        for (int i = 0; i < lines.size(); i++) {
            UiRender.text(g, font, lines.get(i), x + 12, top + 28 + i * 10, Theme.TEXT_SECONDARY);
        }
        cancel.draw(g, font, mx, my);
        confirm.draw(g, font, mx, my);
        UiRender.alpha = savedAlpha;
    }

    /** Always consumes the click; clicking outside cancels. */
    public void click(double mx, double my) {
        if (cancel.click(mx, my) || confirm.click(mx, my)) return;
        if (!UiRender.inside(mx, my, x, y, WIDTH, h)) closed = true;
    }
}
