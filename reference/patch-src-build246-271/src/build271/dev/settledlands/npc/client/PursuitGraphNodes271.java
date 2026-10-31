package dev.settledlands.npc.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Dedicated graph-menu entries that create ordinary, connected Trigger actions. */
public final class PursuitGraphNodes271 {
    public static final int START_NPC_ROW = 16;
    public static final int START_PLAYER_ROW = 17;
    public static final int STOP_ROW = 18;
    public static final int MENU_ITEM_COUNT = 19;

    private PursuitGraphNodes271() {}

    public static String actionForMenuRow(int row) {
        return switch (row) {
            case START_NPC_ROW -> "trigger:pursuit:start:npc:";
            case START_PLAYER_ROW -> "trigger:pursuit:start:player:";
            case STOP_ROW -> "trigger:pursuit:stop";
            default -> null;
        };
    }

    public static String labelKeyForAction(String action) {
        if (action == null) return null;
        if (action.startsWith("trigger:pursuit:start:npc:"))
            return "gui.settledlands.graph.pursuit_node_start_npc";
        if (action.startsWith("trigger:pursuit:start:player:"))
            return "gui.settledlands.graph.pursuit_node_start_player";
        if (action.equals("trigger:pursuit:stop"))
            return "gui.settledlands.graph.pursuit_node_stop";
        return null;
    }

    /** Package-peer access is intentional: menu state is package-private in the target mod class. */
    public static boolean isOpenForCreation(NpcGraphContextMenu menu) {
        return menu != null && menu.open && menu.targetNodeId < 0;
    }

    public static int rowAt(NpcGraphContextMenu menu, double x, double y) {
        return menu == null ? -1 : menu.rowAt(x, y);
    }

    public record MenuPoint(int row, double x, double y, int wireFrom, int wirePort) {}

    public static MenuPoint capture(NpcGraphContextMenu menu, int row) {
        if (menu == null) return null;
        return new MenuPoint(row, menu.worldX, menu.worldY, menu.pendingWireFrom, menu.pendingWirePort);
    }

    public static void drawExtraRows(NpcGraphContextMenu menu, GuiGraphics graphics, Font font,
            int mouseX, int mouseY) {
        if (!isOpenForCreation(menu) || graphics == null || font == null) return;
        int hovered = menu.rowAt(mouseX, mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0, 0.0, 600.0);
        for (int visible = 0; visible < 5; visible++) {
            int row = menu.scrollRow + visible;
            if (row < START_NPC_ROW || row > STOP_ROW) continue;
            int y = menu.top + 20 + visible * 22;
            graphics.fill(menu.left + 2, y, menu.left + menu.width - 2, y + 22,
                    hovered == row ? 0xff33485a : 0xff1a2027);
            String key = row == START_NPC_ROW
                    ? "gui.settledlands.graph.pursuit_menu_start_npc"
                    : row == START_PLAYER_ROW
                            ? "gui.settledlands.graph.pursuit_menu_start_player"
                            : "gui.settledlands.graph.pursuit_menu_stop";
            String text = Component.translatable(key).getString();
            while (!text.isEmpty() && font.width(text) > menu.width - 18)
                text = text.substring(0, text.length() - 1);
            graphics.drawString(font, text, menu.left + 8, y + 6, 0xfff1d68a, false);
        }
        graphics.pose().popPose();
    }

    public static void drawNodeTitle(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            dev.settledlands.npc.NpcDialogueGraph.Node node) {
        if (screen == null || graphics == null || node == null
                || node.type != dev.settledlands.npc.NpcDialogueGraph.TRIGGER) return;
        String key = labelKeyForAction(node.action);
        if (key == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;
        graphics.pose().pushPose();
        graphics.pose().translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0);
        graphics.pose().scale((float) canvas.zoom, (float) canvas.zoom, 1.0f);
        graphics.fill(1, 1, NpcGraphCanvas.NODE_W - 1, 23, 0xff285444);
        String title = Component.translatable(key).getString();
        while (!title.isEmpty() && font.width(title) > NpcGraphCanvas.NODE_W - 18)
            title = title.substring(0, title.length() - 1);
        graphics.drawString(font, title, 8, 8, 0xfff4f5f8, false);
        graphics.pose().popPose();
    }
}
