package net.craftproxy.mod.client.gui;

import net.craftproxy.mod.client.compat.Gfx;
import net.craftproxy.mod.client.ui.Icon;
import net.craftproxy.mod.client.ui.Theme;
import net.craftproxy.mod.client.ui.UiRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if >=1.21.2 {
import net.minecraft.client.gui.components.toasts.ToastManager;
//?} else {
/*import net.minecraft.client.gui.components.toasts.ToastComponent;
*///?}
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Base for CraftProxy toasts: handles the per-version toast API, a display timer that can be
 * restarted (e.g. when another message from the same sender arrives) and the shared card layout
 * (avatar with type badge, bold title, one line of body text, optional counter).
 */
public abstract class CraftToast implements Toast {

    protected static final long DISPLAY_TIME_MS = 5000L;
    private static final int WIDTH = 200;
    private static final int HEIGHT = 32;

    private long startMs;
    private boolean restart;
    private boolean hidden;
    private boolean shown;
    /** Wall clock of creation or the last restart, to detect toasts the manager dropped. */
    private long touchedAt = System.currentTimeMillis();
    /** Screen-space bounds of the fully visible toast, updated while it renders. */
    private int hitX = Integer.MIN_VALUE;
    private int hitY = Integer.MIN_VALUE;

    /** Shows the toast for the full display time again, starting now. */
    protected void restartTimer() {
        restart = true;
        touchedAt = System.currentTimeMillis();
    }

    /** True while the toast is queued or visible and can still be updated in place. */
    protected boolean isLive() {
        return !hidden && System.currentTimeMillis() - touchedAt < DISPLAY_TIME_MS + 1000L;
    }

    /** Hides the toast early (e.g. after it was clicked). */
    public void dismiss() {
        hidden = true;
    }

    /**
     * True when the mouse is over the fully visible toast. The toast manager reports the slot
     * position on 1.21.2+; older versions fall back to the usual top-right position.
     */
    public boolean isClickableAt(double mouseX, double mouseY) {
        // Queued toasts have no slot yet and must not claim clicks meant for visible ones.
        if (!shown || !isLive()) {
            return false;
        }
        if (hitX == Integer.MIN_VALUE) {
            hitX = Minecraft.getInstance().getWindow().getGuiScaledWidth() - width();
            hitY = 0;
        }
        return mouseX >= hitX && mouseX < hitX + width()
                && mouseY >= hitY && mouseY < hitY + height();
    }

    //? if >=1.21.2 {
    @Override
    public float xPos(int screenWidth, float visiblePortion) {
        // Remember where the toast is once fully visible, independent of the slide animation.
        this.hitX = screenWidth - width();
        return screenWidth - width() * visiblePortion;
    }

    @Override
    public float yPos(int slotIndex) {
        this.hitY = slotIndex * height();
        return slotIndex * height();
    }
    //?}

    /** Called from update/render, which the toast manager only runs for toasts on screen. */
    private long elapsed(long visibleMs) {
        shown = true;
        if (restart) {
            startMs = visibleMs;
            restart = false;
        }
        return Math.max(0L, visibleMs - startMs);
    }

    //? if >=1.21.2 {
    @Override
    public Visibility getWantedVisibility() {
        return hidden ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        if (elapsed(fullyVisibleForMs) >= DISPLAY_TIME_MS) {
            hidden = true;
        }
    }
    //?}

    //? if >=26.1 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        draw(new Gfx(graphics), font, elapsed(fullyVisibleForMs));
    }
    //?} else if >=1.21.2 {
    /*@Override
    public void render(GuiGraphics graphics, Font font, long fullyVisibleForMs) {
        draw(new Gfx(graphics), font, elapsed(fullyVisibleForMs));
    }
    *///?} else {
    /*@Override
    public Visibility render(GuiGraphics graphics, ToastComponent toasts, long fullyVisibleForMs) {
        long elapsed = elapsed(fullyVisibleForMs);
        draw(new Gfx(graphics), toasts.getMinecraft().font, elapsed);
        hidden = elapsed >= DISPLAY_TIME_MS;
        return hidden ? Visibility.HIDE : Visibility.SHOW;
    }
    *///?}

    protected abstract void draw(Gfx graphics, Font font, long elapsedMs);

    /**
     * Draws the shared card: avatar with a small accent badge, bold title (with an optional
     * counter pill) and a single line of body text, plus a thin bar for the remaining time.
     */
    protected void drawCard(Gfx g, Font font, Identifier skin, Icon badge, String title, String body,
                            int count, long elapsedMs) {
        int w = width();
        int h = height();

        UiRender.shadow(g, 0, 0, w, h, 5, 2);
        UiRender.card(g, 0, 0, w, h, 5, Theme.WINDOW, Theme.BORDER_STRONG);

        // Avatar with type badge in the bottom-right corner.
        UiRender.head(g, skin, 7, 6, 20, Theme.WINDOW);
        UiRender.roundRect(g, 20, 19, 11, 11, 3, Theme.WINDOW);
        UiRender.roundRect(g, 21, 20, 9, 9, 2, Theme.ACCENT);
        badge.drawCentered(g, 25, 24, 1, Theme.TEXT_ON_ACCENT);

        int textX = 37;
        int right = w - 8;

        int countW = 0;
        if (count > 1) {
            String label = count > 99 ? "99+" : Integer.toString(count);
            countW = font.width(label) + 6;
            UiRender.roundRect(g, right - countW, 5, countW, 10, 3, Theme.ACCENT_DIM);
            UiRender.text(g, font, label, right - countW + 3, 6, Theme.ACCENT_HOVER);
            countW += 4;
        }

        Component titleText = bold(font, title, right - textX - countW);
        UiRender.text(g, font, titleText, textX, 6, Theme.TEXT);
        UiRender.text(g, font, UiRender.ellipsize(font, body, right - textX), textX, 18, Theme.TEXT_SECONDARY);

        float remaining = 1.0F - Math.min(1.0F, elapsedMs / (float) DISPLAY_TIME_MS);
        int barW = Math.round((w - 12) * remaining);
        if (barW > 0) {
            UiRender.fill(g, 6, h - 2, 6 + barW, h - 1, Theme.withAlpha(Theme.ACCENT, 0x70));
        }
    }

    /** Bold component shortened with an ellipsis so it fits into {@code maxWidth}. */
    private static Component bold(Font font, String text, int maxWidth) {
        String shown = text;
        Component component = Component.literal(shown).withStyle(s -> s.withBold(true));
        while (font.width(component) > maxWidth && shown.length() > 1) {
            shown = shown.substring(0, shown.length() - 1);
            String candidate = shown.strip() + "…";
            component = Component.literal(candidate).withStyle(s -> s.withBold(true));
        }
        return component;
    }

    /** Collapses line breaks so a preview fits into one line. */
    protected static String oneLine(String text) {
        return text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').strip();
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }
}
