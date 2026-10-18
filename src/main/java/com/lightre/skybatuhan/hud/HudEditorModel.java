package com.lightre.skybatuhan.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The logic of the position editor: which box is picked, dragging, snapping, nudging and resetting.
 */
public class HudEditorModel {
    private static final int SNAP_DISTANCE = 4;
    private static final int EDGE_MARGIN = 4;

    /**
     * One box in the editor. It works on a copy of the layout, nothing is saved until applyChanges().
     */
    public static final class Box {
        private final HudElement element;
        private final HudLayout layout;
        private final List<String> lines;
        private int width;
        private int height;

        private Box(HudElement element) {
            this.element = element;
            this.layout = HudManager.getLayout(element).copy();
            this.lines = element.previewLines();
        }

        public List<String> lines() {
            return lines;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int x(int screenWidth) {
            return layout.resolveX(screenWidth, width);
        }

        public int y(int screenHeight) {
            return layout.resolveY(screenHeight, height);
        }
    }

    private final List<Box> boxes = new ArrayList<>();
    private boolean measured = false;
    private Box selected = null;
    private Box dragged = null;
    private int grabX;
    private int grabY;

    public HudEditorModel(List<HudElement> elements) {
        for (HudElement element : elements) {
            if (element.isEnabled()) boxes.add(new Box(element));
        }
    }

    public List<Box> boxes() {
        return Collections.unmodifiableList(boxes);
    }

    public Box selected() {
        return selected;
    }

    public void measure(HudCanvas canvas) {
        if (measured) return;
        for (Box box : boxes) {
            box.width = HudStyle.width(canvas, box.lines);
            box.height = HudStyle.height(canvas, box.lines);
        }
        measured = true;
    }

    /**
     * The topmost box under the point, or null.
     */
    public Box boxAt(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        for (int i = boxes.size() - 1; i >= 0; i--) {
            Box box = boxes.get(i);
            int x = box.x(screenWidth);
            int y = box.y(screenHeight);
            if (mouseX >= x && mouseX < x + box.width && mouseY >= y && mouseY < y + box.height) return box;
        }
        return null;
    }

    public void clearSelection() {
        selected = null;
        dragged = null;
    }

    public void beginDrag(Box box, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        selected = box;
        dragged = box;
        grabX = mouseX - box.x(screenWidth);
        grabY = mouseY - box.y(screenHeight);
    }

    public boolean isDragging() {
        return dragged != null;
    }

    public void dragTo(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (dragged == null) return;
        moveTo(dragged, mouseX - grabX, mouseY - grabY, screenWidth, screenHeight, true);
    }

    public void endDrag() {
        dragged = null;
    }

    /**
     * Fine movement with the arrow keys. No snapping.
     */
    public void nudge(int dx, int dy, int screenWidth, int screenHeight) {
        if (selected == null) return;
        moveTo(selected, selected.x(screenWidth) + dx, selected.y(screenHeight) + dy, screenWidth, screenHeight, false);
    }

    public void resetSelected() {
        if (selected != null) selected.layout.copyFrom(selected.element.createDefaultLayout());
    }

    public void resetAll() {
        for (Box box : boxes) {
            box.layout.copyFrom(box.element.createDefaultLayout());
        }
    }

    /**
     * Hands the positions to the HUD manager. Boxes left at their default stay unsaved, so a new default can still reach them.
     */
    public void applyChanges() {
        for (Box box : boxes) {
            String id = box.element.getId();
            if (box.layout.sameAs(box.element.createDefaultLayout())) {
                HudManager.resetLayout(id);
            } else {
                HudManager.setLayout(id, box.layout.copy());
            }
        }
    }

    private void moveTo(Box box, int x, int y, int screenWidth, int screenHeight, boolean snap) {
        int newX = snap ? snapX(box, x, screenWidth) : x;
        int newY = snap ? snapY(box, y, screenHeight) : y;

        newX = Math.clamp(newX, 0, Math.max(0, screenWidth - box.width));
        newY = Math.clamp(newY, 0, Math.max(0, screenHeight - box.height));

        box.layout.moveTo(newX, newY, screenWidth, screenHeight, box.width, box.height);
    }

    private int snapX(Box moving, int x, int screenWidth) {
        List<Integer> targets = new ArrayList<>();
        targets.add(EDGE_MARGIN);                                   // left margin
        targets.add((screenWidth - moving.width) / 2);              // centered
        targets.add(screenWidth - moving.width - EDGE_MARGIN);      // right margin

        for (Box other : boxes) {
            if (other == moving) continue;
            int otherX = other.x(screenWidth);
            targets.add(otherX);                                    // left edges line up
            targets.add(otherX + other.width - moving.width);       // right edges line up
            targets.add(otherX + other.width);                      // right next to it
            targets.add(otherX - moving.width);                     // left next to it
        }
        return nearest(x, targets);
    }

    private int snapY(Box moving, int y, int screenHeight) {
        List<Integer> targets = new ArrayList<>();
        targets.add(EDGE_MARGIN);
        targets.add((screenHeight - moving.height) / 2);
        targets.add(screenHeight - moving.height - EDGE_MARGIN);

        for (Box other : boxes) {
            if (other == moving) continue;
            int otherY = other.y(screenHeight);
            targets.add(otherY);
            targets.add(otherY + other.height - moving.height);
            targets.add(otherY + other.height);
            targets.add(otherY - moving.height);
        }
        return nearest(y, targets);
    }

    private static int nearest(int value, List<Integer> targets) {
        int best = value;
        int bestDistance = SNAP_DISTANCE + 1;
        for (int target : targets) {
            int distance = Math.abs(value - target);
            if (distance <= SNAP_DISTANCE && distance < bestDistance) {
                best = target;
                bestDistance = distance;
            }
        }
        return best;
    }
}