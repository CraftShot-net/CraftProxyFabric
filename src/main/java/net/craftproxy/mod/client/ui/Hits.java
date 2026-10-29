package net.craftproxy.mod.client.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Click targets collected while drawing (immediate-mode style): every frame the screen clears
 * the list and registers what it drew, clicks are then matched against the last frame.
 * Targets registered while a clip rectangle is set only react inside it.
 */
public class Hits {
    /** Receives the mouse position of a click. */
    public interface Action {
        void run(int mouseX, int mouseY);
    }

    private record Hit(int x0, int y0, int x1, int y1, Action left, Action right) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private int clipX0 = Integer.MIN_VALUE, clipY0 = Integer.MIN_VALUE, clipX1 = Integer.MAX_VALUE, clipY1 = Integer.MAX_VALUE;

    public void clear() {
        hits.clear();
        clearClip();
    }

    public void clip(int x, int y, int w, int h) {
        clipX0 = x;
        clipY0 = y;
        clipX1 = x + w;
        clipY1 = y + h;
    }

    public void clearClip() {
        clipX0 = clipY0 = Integer.MIN_VALUE;
        clipX1 = clipY1 = Integer.MAX_VALUE;
    }

    public void add(int x, int y, int w, int h, Runnable left) {
        add(x, y, w, h, (mx, my) -> left.run(), null);
    }

    public void add(int x, int y, int w, int h, Action left, Action right) {
        int x0 = Math.max(x, clipX0), y0 = Math.max(y, clipY0);
        int x1 = Math.min(x + w, clipX1), y1 = Math.min(y + h, clipY1);
        if (x1 > x0 && y1 > y0) hits.add(new Hit(x0, y0, x1, y1, left, right));
    }

    /** Runs the topmost (last registered) target under the mouse; button 0 = left, 1 = right. */
    public boolean click(double mx, double my, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (mx >= hit.x0 && mx < hit.x1 && my >= hit.y0 && my < hit.y1) {
                Action action = button == 1 ? hit.right : hit.left;
                if (action == null) {
                    if (button == 1) continue;
                    return true;
                }
                action.run((int) mx, (int) my);
                return true;
            }
        }
        return false;
    }
}
