package dev.settledlands.npc;

/** Allocation-free policy shared by the server controller and the client editor. */
public final class EscortRules {
    public static final int MAX_CODE = 64;
    public static final double MAX_DISTANCE_SQ = 8.0 * 8.0;
    // Stop before the hard limit. Hysteresis prevents stop/start pathfinding spam.
    public static final double STOP_DISTANCE_SQ = 7.5 * 7.5;
    public static final double RESUME_DISTANCE_SQ = 6.5 * 6.5;
    public static final double CATCH_UP_END_DISTANCE_SQ = 3.0 * 3.0;
    public static final double CATCH_UP_TARGET_MOVED_SQ = 2.0 * 2.0;
    // Mob.setSpeed sets both LivingEntity.speed and forward input: ground acceleration is speed^2.
    // Match default player walking (MOVEMENT_SPEED=0.1), not the player's sprint modifier.
    public static final double WALK_AI_SPEED = Math.sqrt(0.1);
    public static final double CATCH_UP_AI_SPEED = Math.sqrt(0.1 * 1.6);
    public static final double ARRIVAL_PLAYER_DISTANCE_SQ = 3.0 * 3.0;
    public static final int FINISH_WAIT_TICKS = 5 * 20;
    public static final int REPATH_TICKS = 20;
    public static final int MAX_RETRY_TICKS = 160;
    public static final double WAYPOINT_DISTANCE = 16.0;
    private EscortRules() {}

    public static boolean validCode(String code) {
        if (code == null || code.isEmpty() || code.length() > MAX_CODE) return false;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
                || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.')) return false;
        }
        return true;
    }

    public static boolean shouldWait(double distanceSq, boolean waiting) {
        return waiting ? distanceSq > RESUME_DISTANCE_SQ : distanceSq >= STOP_DISTANCE_SQ;
    }

    public static boolean shouldCatchUp(double playerDistanceSq, double playerGoalDistanceSq,
                                        double npcGoalDistanceSq, boolean catchingUp) {
        return playerGoalDistanceSq < npcGoalDistanceSq
            && playerDistanceSq > (catchingUp ? CATCH_UP_END_DISTANCE_SQ : MAX_DISTANCE_SQ);
    }

    public static double navigationSpeed(double movementAttribute, boolean catchingUp) {
        if (!Double.isFinite(movementAttribute) || movementAttribute <= 0.0) return 0.0;
        return (catchingUp ? CATCH_UP_AI_SPEED : WALK_AI_SPEED) / movementAttribute;
    }

    public static boolean atDestination(double x, double y, double z, int tx, int ty, int tz) {
        return atDestination(x, y, z, tx, ty, tz, 0.6F);
    }

    public static boolean atDestination(double x, double y, double z, int tx, int ty, int tz, float entityWidth) {
        // Match Path.getEntityPosAtNode. Large/scaled NPCs use a different node-centre offset.
        double offset = (int)(entityWidth + 1.0F) * 0.5;
        double dx = x - (tx + offset), dz = z - (tz + offset);
        // Vanilla accepts each horizontal axis independently; cover that square for wide models.
        double waypointRadius = entityWidth > 0.75F ? entityWidth * 0.5 : 0.75 - entityWidth * 0.5;
        double toleranceSq = Math.max(0.75 * 0.75, 2.0 * waypointRadius * waypointRadius);
        return dx * dx + dz * dz <= toleranceSq && Math.abs(y - ty) <= 1.0;
    }

    public static int retryTicks(int failures) {
        return REPATH_TICKS << Math.min(Math.max(failures, 0), 3);
    }
}
