package dev.settledlands.npc.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Build 361/362: taking a stock widget off the screen of the graph editor for good.
 *
 * <p>Two builds in a row asked a tab button to stop being drawn and two builds in a row came back with
 * the tab still on the top bar: 360 cleared {@code visible}, 361 cleared it and moved the button out of
 * the window. Both are requests to a widget that some other part of the screen may simply ignore, so
 * build 362 stops asking and takes the widget out of the screen instead: the button is removed from the
 * two lists a {@code Screen} keeps - the children it hands its clicks to and the renderables it draws -
 * and only then hidden and moved for good measure. A widget in neither list is not drawn and not
 * clicked; there is nothing left that could bring it back.</p>
 *
 * <p>Everything is done through reflection on purpose. A graph screen has to open on a client whose
 * {@code Screen} is whatever the game ships; a member this build does not find is a step skipped, never
 * a crash in the middle of a window the author is using. Nothing here runs per frame: it is called once
 * per rebuild of the editor's widgets.</p>
 */
public final class WidgetPark361 {

    /** Where a parked widget goes: far above any window, so it is never inside the viewport. */
    public static final int PARK_Y = -20000;

    /** The list a {@code Screen} renders, and the list it dispatches clicks through. */
    private static final String[] LISTS = {"renderables", "children"};

    private static final String[] MOVERS = {"setPosition", "setY", "setHeight"};

    private WidgetPark361() {}

    /**
     * Hide a widget and move it out of the window, without touching the screen. Used where only the
     * widget is at hand (an inspector row, a button just built by the stock code).
     */
    public static void park(Object widget) {
        park(null, widget);
    }

    /**
     * Take a widget off a screen: remove it from the lists the screen draws and clicks, hide it, and
     * move it out of the window. Any of the steps that this game's {@code Screen} does not offer is
     * skipped; the others still happen. Never throws.
     */
    public static void park(Object screen, Object widget) {
        if (widget == null) return;
        if (screen != null) {
            for (String list : LISTS) {
                settledlands$drop(screen, list, widget);
            }
        }
        settledlands$flag(widget, "visible", false);
        settledlands$flag(widget, "active", false);
        settledlands$move(widget);
    }

    /** Remove one widget from a list the screen keeps it in, by the field or by the accessor. */
    private static void settledlands$drop(Object screen, String list, Object widget) {
        Object value = settledlands$read(screen, list);
        if (!(value instanceof List)) return;
        try {
            ((List) value).remove(widget);
        } catch (Throwable ignored) {
            // An immutable list: the flags and the move below still apply.
        }
    }

    /** The children of a screen, as the click dispatcher sees them, or an empty list. */
    public static List children(Object screen) {
        Object value = settledlands$read(screen, "children");
        if (value instanceof List) return (List) value;
        try {
            Method method = screen.getClass().getMethod("children");
            Object list = method.invoke(screen);
            if (list instanceof List) return (List) list;
        } catch (Throwable ignored) {
            // No accessor either: an empty list, the caller parks nothing.
        }
        return java.util.Collections.emptyList();
    }

    /**
     * True for a button of the tab strip of the graph editor: the stock strip is built at {@code y = 10}
     * with a height of 20, starting at {@code x = 66} - undo and redo sit at 10 and 38 with the same box,
     * which is why the left edge is part of the test. Read through reflection, so a widget that answers
     * none of the getters is simply not a tab.
     */
    public static boolean isTabStrip(Object widget, int y, int height, int minX) {
        if (widget == null) return false;
        Object top = settledlands$call(widget, "getY");
        Object tall = settledlands$call(widget, "getHeight");
        Object left = settledlands$call(widget, "getX");
        if (!(top instanceof Integer) || !(tall instanceof Integer) || !(left instanceof Integer)) {
            return false;
        }
        return ((Integer) top).intValue() == y
                && ((Integer) tall).intValue() == height
                && ((Integer) left).intValue() >= minX;
    }

    /** Invoke a no-argument method of a widget, or null when it has none. */
    private static Object settledlands$call(Object widget, String name) {
        try {
            Method method = widget.getClass().getMethod(name);
            return method.invoke(widget);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** The text a widget shows, or an empty string when it shows none. */
    public static String label(Object widget) {
        if (widget == null) return "";
        try {
            Method method = widget.getClass().getMethod("getMessage");
            Object message = method.invoke(widget);
            if (message instanceof net.minecraft.network.chat.Component) {
                return ((net.minecraft.network.chat.Component) message).getString();
            }
        } catch (Throwable ignored) {
            // A widget without a message simply has no label.
        }
        return "";
    }

    private static Object settledlands$read(Object target, String field) {
        if (target == null) return null;
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field found = type.getDeclaredField(field);
                found.setAccessible(true);
                return found.get(target);
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static void settledlands$flag(Object widget, String field, boolean value) {
        Class<?> type = widget.getClass();
        while (type != null) {
            try {
                Field found = type.getDeclaredField(field);
                found.setAccessible(true);
                found.setBoolean(widget, value);
                return;
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
    }

    private static void settledlands$move(Object widget) {
        Class<?> type = widget.getClass();
        for (String name : MOVERS) {
            try {
                Method method = "setPosition".equals(name)
                        ? type.getMethod(name, int.class, int.class)
                        : type.getMethod(name, int.class);
                if ("setPosition".equals(name)) {
                    method.invoke(widget, PARK_Y, PARK_Y);
                } else if ("setY".equals(name)) {
                    method.invoke(widget, PARK_Y);
                } else {
                    method.invoke(widget, 0);
                }
                return;
            } catch (Throwable ignored) {
                // Try the next one: an API that has none of them leaves the widget in place.
            }
        }
    }
}
