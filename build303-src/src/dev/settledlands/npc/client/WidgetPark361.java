package dev.settledlands.npc.client;

import java.lang.reflect.Method;

/**
 * Build 361: taking a stock widget off the screen of the graph editor for good.
 *
 * <p>Build 360 asked a tab button to stop being drawn by clearing {@code visible} and {@code active},
 * and the game came back with all three tabs still on the top bar of a block's graph. A flag that some
 * other code can set back, or a flag this screen never looks at, is not an answer, so build 361 asks a
 * harder question: the widget is moved out of the window altogether. A box a thousand rows above the
 * top edge has no pixels on the screen, no matter who writes {@code visible} afterwards.</p>
 *
 * <p>The move is done through whatever the widget class offers, in the order the API has had them:
 * {@code setPosition(int, int)}, then {@code setY(int)}, then {@code setHeight(0)} - the first one that
 * exists does the job. They are looked up at runtime so that a class that has none of them (another
 * game version) is a widget that stays where it is, not a crash in the middle of a screen the author is
 * using. The lookup happens once per widget parked, which is a handful of times per screen.</p>
 */
public final class WidgetPark361 {

    /** Where a parked widget goes: far above any window, so it is never inside the viewport. */
    public static final int PARK_Y = -20000;

    private static final String[] MOVERS = {"setPosition", "setY", "setHeight"};

    private WidgetPark361() {}

    /**
     * Hide a widget and move it out of the window. Never throws: a widget is something a screen can
     * live without, an exception while it is built is not.
     */
    public static void park(Object widget) {
        if (widget == null) return;
        try {
            widget.getClass().getField("visible").setBoolean(widget, false);
        } catch (Throwable ignored) {
            // No such field, or not writable: the move below is what really matters.
        }
        try {
            widget.getClass().getField("active").setBoolean(widget, false);
        } catch (Throwable ignored) {
            // A widget without the flag simply keeps it.
        }
        settledlands$move(widget);
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
