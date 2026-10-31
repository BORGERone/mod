package dev.settledlands.npc.client;

/** Pure popup/gesture policy; no rendering, world or packet work. Right click is distinct from right drag. */
public final class NpcGraphContextMenu {
    public static final int ROW_H = 22, HEADER_H = 20;
    public static final int TOTAL_ROWS = 15;
    public static final int VISIBLE_ROWS = 5;
    public static final int MENU_W = 172;
    public static final int MENU_H = HEADER_H + VISIBLE_ROWS * ROW_H + 4;
    public static final int SCROLLBAR_W = 6;
    public static final double DRAG_THRESHOLD_SQ = 4.0 * 4.0;

    public boolean open;
    public int left, top, width = MENU_W, height = MENU_H;
    public double worldX, worldY, deltaX, deltaY;
    public int pendingWireFrom = -1;
    public int pendingWirePort = 0;
    public int targetNodeId = -1;
    public int scrollRow = 0;
    private boolean pressed, dragged;
    private double startX, startY, lastX, lastY;

    public void press(double x, double y) {
        open = false;
        pressed = true;
        dragged = false;
        startX = lastX = x;
        startY = lastY = y;
        deltaX = deltaY = 0;
        pendingWireFrom = -1;
        pendingWirePort = 0;
        targetNodeId = -1;
        scrollRow = 0;
    }

    public boolean isPressed() {
        return pressed;
    }

    public boolean drag(double x, double y) {
        deltaX = deltaY = 0;
        if (!pressed) return false;
        double dx = x - startX, dy = y - startY;
        if (!dragged && dx * dx + dy * dy < DRAG_THRESHOLD_SQ) return false;
        dragged = true;
        deltaX = x - lastX;
        deltaY = y - lastY;
        lastX = x;
        lastY = y;
        return true;
    }

    public boolean release(double x, double y, NpcGraphCanvas canvas) {
        if (!pressed) return false;
        pressed = false;
        double dx = x - startX, dy = y - startY;
        if (dragged || dx * dx + dy * dy >= DRAG_THRESHOLD_SQ || !canvas.contains(startX, startY)) {
            dragged = false;
            return false;
        }
        if (targetNodeId >= 0) {
            showForNode(startX, startY, canvas, targetNodeId);
        } else {
            show(startX, startY, canvas);
        }
        return true;
    }

    public void showForWire(double x, double y, NpcGraphCanvas canvas, int wireFrom) {
        showForWire(x, y, canvas, wireFrom, 0);
    }

    public void showForWire(double x, double y, NpcGraphCanvas canvas, int wireFrom, int wirePort) {
        show(x, y, canvas);
        this.pendingWireFrom = wireFrom;
        this.pendingWirePort = wirePort;
    }

    public void showForNode(double x, double y, NpcGraphCanvas canvas, int nodeId) {
        this.targetNodeId = nodeId;
        this.pendingWireFrom = -1;
        this.pendingWirePort = 0;
        this.scrollRow = 0;
        worldX = canvas.worldX(x);
        worldY = canvas.worldY(y);
        width = Math.min(MENU_W, Math.max(32, canvas.right - canvas.left));
        int nodeMenuH = HEADER_H + 2 * ROW_H + 4;
        height = Math.min(nodeMenuH, Math.max(1, canvas.bottom - canvas.top));
        left = (int) Math.max(canvas.left, Math.min(canvas.right - width, x));
        top = (int) Math.max(canvas.top, Math.min(canvas.bottom - height, y));
        open = true;
    }

    public void show(double x, double y, NpcGraphCanvas canvas) {
        this.targetNodeId = -1;
        this.scrollRow = 0;
        worldX = canvas.worldX(x);
        worldY = canvas.worldY(y);
        width = Math.min(MENU_W, Math.max(32, canvas.right - canvas.left));
        height = Math.min(MENU_H, Math.max(1, canvas.bottom - canvas.top));
        left = (int) Math.max(canvas.left, Math.min(canvas.right - width, x));
        top = (int) Math.max(canvas.top, Math.min(canvas.bottom - height, y));
        open = true;
    }

    public void scroll(int amount) {
        if (targetNodeId >= 0) return;
        int maxScroll = Math.max(0, TOTAL_ROWS - VISIBLE_ROWS);
        scrollRow = Math.max(0, Math.min(maxScroll, scrollRow + amount));
    }

    public boolean isScrollbar(double x, double y) {
        if (!open || targetNodeId >= 0) return false;
        return x >= left + width - SCROLLBAR_W - 4 && x <= left + width && y >= top + HEADER_H && y <= top + height - 2;
    }

    public void scrollbarDrag(double y) {
        if (targetNodeId >= 0) return;
        int trackH = height - HEADER_H - 4;
        if (trackH <= 0) return;
        double relY = y - (top + HEADER_H + 2);
        double ratio = relY / trackH;
        int maxScroll = Math.max(0, TOTAL_ROWS - VISIBLE_ROWS);
        scrollRow = (int) Math.max(0, Math.min(maxScroll, Math.round(ratio * maxScroll)));
    }

    public boolean contains(double x, double y) {
        return open && x >= left && x < left + width && y >= top && y < top + height;
    }

    public int rowAt(double x, double y) {
        if (!contains(x, y) || y < top + HEADER_H || y >= top + height - 2) return -1;
        if (targetNodeId < 0 && x >= left + width - SCROLLBAR_W - 3) return -1;
        int visualRow = (int) ((y - top - HEADER_H) / ROW_H);
        if (targetNodeId >= 0) {
            return visualRow >= 0 && visualRow < 2 ? visualRow : -1;
        }
        if (visualRow < 0 || visualRow >= VISIBLE_ROWS) return -1;
        int actualRow = scrollRow + visualRow;
        return actualRow >= 0 && actualRow < TOTAL_ROWS ? actualRow : -1;
    }

    public void close() {
        open = false;
        pendingWireFrom = -1;
        pendingWirePort = 0;
        targetNodeId = -1;
        scrollRow = 0;
    }

    public void cancel() {
        open = false;
        pressed = false;
        dragged = false;
        deltaX = deltaY = 0;
        pendingWireFrom = -1;
        pendingWirePort = 0;
        targetNodeId = -1;
        scrollRow = 0;
    }
}
