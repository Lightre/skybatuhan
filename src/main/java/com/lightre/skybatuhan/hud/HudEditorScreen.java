package com.lightre.skybatuhan.hud;

import com.lightre.skybatuhan.manager.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The screen where the HUD boxes are dragged into place. Done saves, Esc or Cancel throws the changes away.
 */
public class HudEditorScreen extends Screen {
    private static final int DIM_COLOR = 0x50000000;
    private static final int HOVER_COLOR = 0xFFFFFFFF;
    private static final int SELECTED_COLOR = 0xFFFFAA00;
    private static final int BUTTON_COLOR = 0xC0202020;
    private static final int BUTTON_HOVER_COLOR = 0xC0454545;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 6;
    private static final int BUTTON_PADDING = 8;

    private record EditorButton(String label, int x, int y, int width, int height, Runnable action) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final Screen parent;
    private final HudEditorModel model = new HudEditorModel(HudManager.getElements());

    private HudEditorScreen(Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    /**
     * Opens the editor on the next tick (so it also works from a command), and returns to the current screen when it closes.
     */
    public static void open() {
        Minecraft client = Minecraft.getInstance();
        client.schedule(() -> client.gui.setScreen(new HudEditorScreen(client.gui.screen())));
    }

    private void finish(boolean save) {
        if (save) {
            model.applyChanges();
            ConfigManager.save();
        }
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void onClose() {
        finish(false);
    }

    // ================= DRAWING =================

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        HudCanvas canvas = new GuiHudCanvas(graphics, client.font);
        model.measure(canvas);

        graphics.fill(0, 0, screenWidth, screenHeight, DIM_COLOR);

        HudEditorModel.Box hovered = model.boxAt(mouseX, mouseY, screenWidth, screenHeight);
        for (HudEditorModel.Box box : model.boxes()) {
            int x = box.x(screenWidth);
            int y = box.y(screenHeight);
            HudStyle.draw(canvas, box.lines(), x, y);

            if (box == model.selected()) {
                outline(graphics, x, y, box.width(), box.height(), SELECTED_COLOR);
            } else if (box == hovered) {
                outline(graphics, x, y, box.width(), box.height(), HOVER_COLOR);
            }
        }

        drawCentered(graphics, client, model.boxes().isEmpty() ? "No boxes to place. Turn some on in the HUD tab." : "Drag a box to move it. Arrow keys: 1 px. R: reset the box.", screenWidth, 8);
        drawCentered(graphics, client, "Enter: save. Esc: cancel.", screenWidth, 8 + client.font.lineHeight + 3);

        for (EditorButton button : buttons(client, screenWidth, screenHeight)) {
            boolean over = button.contains(mouseX, mouseY);
            graphics.fill(button.x(), button.y(), button.x() + button.width(), button.y() + button.height(), over ? BUTTON_HOVER_COLOR : BUTTON_COLOR);
            graphics.text(client.font, button.label(), button.x() + BUTTON_PADDING, button.y() + (button.height() - client.font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);
        }
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x - 1, y - 1, x + width + 1, y, color);               // top
        graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, color); // bottom
        graphics.fill(x - 1, y, x, y + height, color);                      // left
        graphics.fill(x + width, y, x + width + 1, y + height, color);      // right
    }

    private static void drawCentered(GuiGraphicsExtractor graphics, Minecraft client, String text, int screenWidth, int y) {
        graphics.text(client.font, text, (screenWidth - client.font.width(text)) / 2, y, 0xFFFFFFFF, true);
    }

    private List<EditorButton> buttons(Minecraft client, int screenWidth, int screenHeight) {
        String[] labels = {"Done", "Reset All", "Cancel"};
        Runnable[] actions = {() -> finish(true), model::resetAll, () -> finish(false)};

        int total = BUTTON_GAP * (labels.length - 1);
        for (String label : labels) {
            total += client.font.width(label) + BUTTON_PADDING * 2;
        }

        List<EditorButton> result = new ArrayList<>();
        int x = (screenWidth - total) / 2;
        int y = screenHeight - BUTTON_HEIGHT - 10;
        for (int i = 0; i < labels.length; i++) {
            int width = client.font.width(labels[i]) + BUTTON_PADDING * 2;
            result.add(new EditorButton(labels[i], x, y, width, BUTTON_HEIGHT, actions[i]));
            x += width + BUTTON_GAP;
        }
        return result;
    }

    // ================= INPUT =================

    private boolean press(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int x = (int) mouseX;
        int y = (int) mouseY;

        for (EditorButton editorButton : buttons(client, screenWidth, screenHeight)) {
            if (editorButton.contains(x, y)) {
                editorButton.action().run();
                return true;
            }
        }

        HudEditorModel.Box box = model.boxAt(x, y, screenWidth, screenHeight);
        if (box == null) {
            model.clearSelection();
        } else {
            model.beginDrag(box, x, y, screenWidth, screenHeight);
        }
        return true;
    }

    private boolean drag(double mouseX, double mouseY) {
        if (!model.isDragging()) return false;

        Minecraft client = Minecraft.getInstance();
        model.dragTo((int) mouseX, (int) mouseY, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
        return true;
    }

    private boolean release() {
        if (!model.isDragging()) return false;
        model.endDrag();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return press(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return drag(event.x(), event.y()) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        return release() || super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int key = event.key();

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            finish(true);
        } else if (key == GLFW.GLFW_KEY_LEFT) {
            model.nudge(-1, 0, screenWidth, screenHeight);
        } else if (key == GLFW.GLFW_KEY_RIGHT) {
            model.nudge(1, 0, screenWidth, screenHeight);
        } else if (key == GLFW.GLFW_KEY_UP) {
            model.nudge(0, -1, screenWidth, screenHeight);
        } else if (key == GLFW.GLFW_KEY_DOWN) {
            model.nudge(0, 1, screenWidth, screenHeight);
        } else if (key == GLFW.GLFW_KEY_R) {
            model.resetSelected();
        } else {
            return super.keyPressed(event); // Esc closes through onClose()
        }
        return true;
    }
}