package dev.settledlands.chest;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;

/**
 * Build 436: optional soft-compat bridge to {@code diegesis} and {@code handson_furniture} via
 * cached reflection. Safe with zero runtime classes from either mod present.
 */
public final class ChestDiegesisCompat436 {

    private static volatile boolean diegesisProbed;
    private static Method activeStationMethod;
    private static Method stationPlayersMethod;

    private static volatile boolean roomyProbed;
    private static Class<?> roomyClass;
    private static Method ownSizeMethod;

    private ChestDiegesisCompat436() {}

    /**
     * Returns true when {@code player} currently has an open 3D {@code diegesis} station at {@code pos}.
     */
    public static boolean isStationOpen(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (level == null || pos == null || player == null) return false;
        ensureDiegesis();
        if (activeStationMethod == null || stationPlayersMethod == null) return false;
        try {
            Object optObj = activeStationMethod.invoke(null, level, pos);
            if (!(optObj instanceof Optional<?> opt) || opt.isEmpty()) return false;
            Object playersObj = stationPlayersMethod.invoke(opt.get());
            return playersObj instanceof Collection<?> col && col.contains(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * When {@code handson_furniture} is loaded, {@code BasicLootBlockEntityMixin} wraps furniture
     * containers in {@code RoomyContainer} and expands {@link Container#getContainerSize()} to 160+
     * 3D scene slots. Returning {@code handson$ownSize()} ensures dungeon loot lands inside the
     * furniture's native inventory slots (visible both in 3D stations and standard Refurbished GUIs).
     */
    public static int usableSlotCount(Object blockEntity, Container container) {
        if (container == null) return 0;
        int total = container.getContainerSize();
        if (total <= 0) return 0;
        if (blockEntity == null) return total;
        ensureRoomy();
        if (roomyClass != null && ownSizeMethod != null && roomyClass.isInstance(blockEntity)) {
            try {
                Object val = ownSizeMethod.invoke(blockEntity);
                if (val instanceof Integer own && own > 0 && own <= total) {
                    return own;
                }
            } catch (Throwable ignored) {
            }
        }
        return total;
    }

    private static void ensureDiegesis() {
        if (diegesisProbed) return;
        synchronized (ChestDiegesisCompat436.class) {
            if (diegesisProbed) return;
            try {
                Class<?> api = Class.forName("com.pockyl.diegesis.api.Diegesis");
                Class<?> ctx = Class.forName("com.pockyl.diegesis.api.StationContext");
                activeStationMethod = api.getMethod("activeStation", ServerLevel.class, BlockPos.class);
                stationPlayersMethod = ctx.getMethod("players");
            } catch (Throwable ignored) {
                activeStationMethod = null;
                stationPlayersMethod = null;
            }
            diegesisProbed = true;
        }
    }

    private static void ensureRoomy() {
        if (roomyProbed) return;
        synchronized (ChestDiegesisCompat436.class) {
            if (roomyProbed) return;
            try {
                Class<?> iface = Class.forName("com.pockyl.handson_furniture.station.RoomyContainer");
                Method own = iface.getMethod("handson$ownSize");
                roomyClass = iface;
                ownSizeMethod = own;
            } catch (Throwable ignored) {
                roomyClass = null;
                ownSizeMethod = null;
            }
            roomyProbed = true;
        }
    }
}
