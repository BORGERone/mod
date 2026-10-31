package dev.settledlands.npc;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;

/**
 * Server-safe configuration & two-way synchronization for autonomous NPC wandering
 * and hostile-mob melee combat across NpcProfile (NpcConfigScreen) and
 * NpcDialogueGraph (NpcDialogueGraphScreen).
 *
 * NOTE: Must NEVER reference net.minecraft.client.* or dev.settledlands.npc.client.*.
 */
public final class NpcAutoAi255 {
    public static final int DEFAULT_WANDER_RADIUS = 10;
    public static final int DEFAULT_AGGRO_RADIUS = 12;
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 64;

    public static final int CARD_W = 300;
    public static final int CARD_H = 170;
    public static final int CARD_GAP_Y = 16;
    public static final int DEFAULT_CARD_X = -360;
    public static final int DEFAULT_CARD_Y = 186;
    public static final int UNSET_COORD = Integer.MIN_VALUE;

    public static final String TAG_AUTO_WANDER = "AutoWander";
    public static final String TAG_WANDER_RADIUS = "WanderRadius";
    public static final String TAG_AUTO_ATTACK = "AutoAttackHostiles";
    public static final String TAG_AGGRO_RADIUS = "AggroRadius";
    public static final String TAG_GRAPH_SYNC = "AutoAiSync";
    public static final String TAG_CARD_X = "AutoAiX";
    public static final String TAG_CARD_Y = "AutoAiY";

    public static final class ProfileState {
        public boolean autoWander = false;
        public int wanderRadius = DEFAULT_WANDER_RADIUS;
        public boolean autoAttackHostiles = false;
        public int aggroRadius = DEFAULT_AGGRO_RADIUS;

        public ProfileState copy() {
            ProfileState c = new ProfileState();
            c.autoWander = this.autoWander;
            c.wanderRadius = clampRadiusOrDefault(this.wanderRadius, DEFAULT_WANDER_RADIUS);
            c.autoAttackHostiles = this.autoAttackHostiles;
            c.aggroRadius = clampRadiusOrDefault(this.aggroRadius, DEFAULT_AGGRO_RADIUS);
            return c;
        }
    }

    public static final class GraphState {
        public boolean synced = false;
        public boolean autoWander = false;
        public int wanderRadius = DEFAULT_WANDER_RADIUS;
        public boolean autoAttackHostiles = false;
        public int aggroRadius = DEFAULT_AGGRO_RADIUS;
        public int cardX = UNSET_COORD;
        public int cardY = UNSET_COORD;

        public GraphState copy() {
            GraphState c = new GraphState();
            c.synced = this.synced;
            c.autoWander = this.autoWander;
            c.wanderRadius = clampRadiusOrDefault(this.wanderRadius, DEFAULT_WANDER_RADIUS);
            c.autoAttackHostiles = this.autoAttackHostiles;
            c.aggroRadius = clampRadiusOrDefault(this.aggroRadius, DEFAULT_AGGRO_RADIUS);
            c.cardX = this.cardX;
            c.cardY = this.cardY;
            return c;
        }
    }

    private static final Map<NpcProfile, ProfileState> PROFILES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<NpcDialogueGraph, GraphState> GRAPHS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NpcAutoAi255() {}

    public static int clampRadius(int value) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, value));
    }

    public static int clampRadiusOrDefault(int value, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return clampRadius(value);
    }

    public static int clampCoord(int value) {
        return Math.max(-32768, Math.min(32767, value));
    }

    public static ProfileState profileState(NpcProfile profile) {
        if (profile == null) {
            return new ProfileState();
        }
        synchronized (PROFILES) {
            ProfileState st = PROFILES.get(profile);
            if (st == null) {
                st = new ProfileState();
                PROFILES.put(profile, st);
            }
            return st;
        }
    }

    public static GraphState graphState(NpcDialogueGraph graph) {
        if (graph == null) {
            return new GraphState();
        }
        synchronized (GRAPHS) {
            GraphState st = GRAPHS.get(graph);
            if (st == null) {
                st = new GraphState();
                GRAPHS.put(graph, st);
            }
            return st;
        }
    }

    public static boolean isAutoWander(NpcProfile profile) {
        return profile != null && profileState(profile).autoWander;
    }

    public static int wanderRadius(NpcProfile profile) {
        return profile == null ? DEFAULT_WANDER_RADIUS
                : clampRadiusOrDefault(profileState(profile).wanderRadius, DEFAULT_WANDER_RADIUS);
    }

    public static boolean isAutoAttackHostiles(NpcProfile profile) {
        return profile != null && profileState(profile).autoAttackHostiles;
    }

    public static int aggroRadius(NpcProfile profile) {
        return profile == null ? DEFAULT_AGGRO_RADIUS
                : clampRadiusOrDefault(profileState(profile).aggroRadius, DEFAULT_AGGRO_RADIUS);
    }

    public static void setAutoWander(NpcProfile profile, boolean enabled) {
        if (profile == null) return;
        ProfileState ps = profileState(profile);
        ps.autoWander = enabled;
        mirrorProfileToGraph(profile);
    }

    public static void setWanderRadius(NpcProfile profile, int radius) {
        if (profile == null) return;
        ProfileState ps = profileState(profile);
        ps.wanderRadius = clampRadius(radius);
        mirrorProfileToGraph(profile);
    }

    public static void setAutoAttackHostiles(NpcProfile profile, boolean enabled) {
        if (profile == null) return;
        ProfileState ps = profileState(profile);
        ps.autoAttackHostiles = enabled;
        mirrorProfileToGraph(profile);
    }

    public static void setAggroRadius(NpcProfile profile, int radius) {
        if (profile == null) return;
        ProfileState ps = profileState(profile);
        ps.aggroRadius = clampRadius(radius);
        mirrorProfileToGraph(profile);
    }

    public static float damageMultiplier(NpcProfile profile) {
        NpcDialogueGraph g = profile == null ? null : profile.dialogueGraph;
        return NpcStatsGraph.damage(g) / 10.0F;
    }

    public static double speedMultiplier(NpcProfile profile) {
        NpcDialogueGraph g = profile == null ? null : profile.dialogueGraph;
        return NpcStatsGraph.speed(g) / 10.0D;
    }

    // ---- NpcProfile bytecode hooks ----

    public static void initProfile(NpcProfile profile) {
        if (profile != null) {
            PROFILES.put(profile, new ProfileState());
        }
    }

    public static void copyProfile(NpcProfile src, NpcProfile dst) {
        if (src == null || dst == null) return;
        ProfileState copied = profileState(src).copy();
        PROFILES.put(dst, copied);
        if (dst.dialogueGraph != null) {
            GraphState gs = graphState(dst.dialogueGraph);
            if (!gs.synced) {
                mirrorStateToGraph(copied, gs);
            }
        }
    }

    public static void sanitizeProfile(NpcProfile src, NpcProfile dst) {
        if (dst == null) return;
        ProfileState copied = src == null ? new ProfileState() : profileState(src).copy();
        PROFILES.put(dst, copied);
        if (dst.dialogueGraph != null) {
            GraphState gs = graphState(dst.dialogueGraph);
            if (gs.synced) {
                // Keep graph and profile mutually consistent; profile wins if explicitly set on src
                gs.autoWander = copied.autoWander;
                gs.wanderRadius = copied.wanderRadius;
                gs.autoAttackHostiles = copied.autoAttackHostiles;
                gs.aggroRadius = copied.aggroRadius;
            } else {
                mirrorStateToGraph(copied, gs);
            }
        }
    }

    public static void saveProfile(NpcProfile profile, CompoundTag tag) {
        if (profile == null || tag == null) return;
        ProfileState ps = profileState(profile);
        tag.putBoolean(TAG_AUTO_WANDER, ps.autoWander);
        tag.putInt(TAG_WANDER_RADIUS, clampRadiusOrDefault(ps.wanderRadius, DEFAULT_WANDER_RADIUS));
        tag.putBoolean(TAG_AUTO_ATTACK, ps.autoAttackHostiles);
        tag.putInt(TAG_AGGRO_RADIUS, clampRadiusOrDefault(ps.aggroRadius, DEFAULT_AGGRO_RADIUS));
    }

    public static void loadProfile(CompoundTag tag, NpcProfile profile) {
        if (profile == null) return;
        ProfileState ps = profileState(profile);
        boolean hasProfileTags = tag != null && (
                tag.contains(TAG_AUTO_WANDER, 1)
                || tag.contains(TAG_WANDER_RADIUS, 3)
                || tag.contains(TAG_AUTO_ATTACK, 1)
                || tag.contains(TAG_AGGRO_RADIUS, 3)
        );
        if (hasProfileTags) {
            ps.autoWander = tag.getBoolean(TAG_AUTO_WANDER);
            ps.wanderRadius = tag.contains(TAG_WANDER_RADIUS, 3)
                    ? clampRadius(tag.getInt(TAG_WANDER_RADIUS))
                    : DEFAULT_WANDER_RADIUS;
            ps.autoAttackHostiles = tag.getBoolean(TAG_AUTO_ATTACK);
            ps.aggroRadius = tag.contains(TAG_AGGRO_RADIUS, 3)
                    ? clampRadius(tag.getInt(TAG_AGGRO_RADIUS))
                    : DEFAULT_AGGRO_RADIUS;
            mirrorProfileToGraph(profile);
        } else if (profile.dialogueGraph != null && graphState(profile.dialogueGraph).synced) {
            GraphState gs = graphState(profile.dialogueGraph);
            ps.autoWander = gs.autoWander;
            ps.wanderRadius = clampRadiusOrDefault(gs.wanderRadius, DEFAULT_WANDER_RADIUS);
            ps.autoAttackHostiles = gs.autoAttackHostiles;
            ps.aggroRadius = clampRadiusOrDefault(gs.aggroRadius, DEFAULT_AGGRO_RADIUS);
        } else {
            ps.autoWander = false;
            ps.wanderRadius = DEFAULT_WANDER_RADIUS;
            ps.autoAttackHostiles = false;
            ps.aggroRadius = DEFAULT_AGGRO_RADIUS;
            mirrorProfileToGraph(profile);
        }
    }

    // ---- NpcDialogueGraph bytecode hooks ----

    public static void initGraph(NpcDialogueGraph graph) {
        if (graph != null) {
            GRAPHS.put(graph, new GraphState());
        }
    }

    public static void copyGraph(NpcDialogueGraph src, NpcDialogueGraph dst) {
        if (src == null || dst == null) return;
        GRAPHS.put(dst, graphState(src).copy());
    }

    public static void saveGraph(NpcDialogueGraph graph, CompoundTag tag) {
        if (graph == null || tag == null) return;
        GraphState gs = graphState(graph);
        if (!gs.synced) return;
        tag.putBoolean(TAG_GRAPH_SYNC, true);
        tag.putBoolean(TAG_AUTO_WANDER, gs.autoWander);
        tag.putInt(TAG_WANDER_RADIUS, clampRadiusOrDefault(gs.wanderRadius, DEFAULT_WANDER_RADIUS));
        tag.putBoolean(TAG_AUTO_ATTACK, gs.autoAttackHostiles);
        tag.putInt(TAG_AGGRO_RADIUS, clampRadiusOrDefault(gs.aggroRadius, DEFAULT_AGGRO_RADIUS));
        if (gs.cardX != UNSET_COORD && gs.cardY != UNSET_COORD) {
            tag.putInt(TAG_CARD_X, clampCoord(gs.cardX));
            tag.putInt(TAG_CARD_Y, clampCoord(gs.cardY));
        }
    }

    public static NpcDialogueGraph loadGraph(NpcDialogueGraph loaded, CompoundTag tag) {
        if (loaded == null || tag == null) return loaded;
        if (tag.getBoolean(TAG_GRAPH_SYNC)) {
            GraphState gs = graphState(loaded);
            gs.synced = true;
            gs.autoWander = tag.getBoolean(TAG_AUTO_WANDER);
            gs.wanderRadius = tag.contains(TAG_WANDER_RADIUS, 3)
                    ? clampRadius(tag.getInt(TAG_WANDER_RADIUS))
                    : DEFAULT_WANDER_RADIUS;
            gs.autoAttackHostiles = tag.getBoolean(TAG_AUTO_ATTACK);
            gs.aggroRadius = tag.contains(TAG_AGGRO_RADIUS, 3)
                    ? clampRadius(tag.getInt(TAG_AGGRO_RADIUS))
                    : DEFAULT_AGGRO_RADIUS;
            if (tag.contains(TAG_CARD_X, 3) && tag.contains(TAG_CARD_Y, 3)) {
                gs.cardX = clampCoord(tag.getInt(TAG_CARD_X));
                gs.cardY = clampCoord(tag.getInt(TAG_CARD_Y));
            }
        }
        return loaded;
    }

    // ---- Two-way UI / Network Sync ----

    public static boolean applyFieldEdit(NpcProfile profile, String key, String value) {
        if (profile == null || key == null) return false;
        ProfileState ps = profileState(profile);
        switch (key) {
            case "autoWander" -> ps.autoWander = parseBool(value);
            case "wanderRadius" -> ps.wanderRadius = clampRadius(parseInt(value, ps.wanderRadius));
            case "autoAttackHostiles" -> ps.autoAttackHostiles = parseBool(value);
            case "aggroRadius" -> ps.aggroRadius = clampRadius(parseInt(value, ps.aggroRadius));
            default -> {
                return false;
            }
        }
        mirrorProfileToGraph(profile);
        return true;
    }

    public static void syncGraphSaveToProfile(NpcDialogueGraph savedMainGraph, NpcProfile dstProfile) {
        if (savedMainGraph == null || dstProfile == null) return;
        GraphState gs = graphState(savedMainGraph);
        if (!gs.synced) return;
        ProfileState ps = profileState(dstProfile);
        ps.autoWander = gs.autoWander;
        ps.wanderRadius = clampRadiusOrDefault(gs.wanderRadius, DEFAULT_WANDER_RADIUS);
        ps.autoAttackHostiles = gs.autoAttackHostiles;
        ps.aggroRadius = clampRadiusOrDefault(gs.aggroRadius, DEFAULT_AGGRO_RADIUS);
    }

    public static void ensureCardOnMainGraph(NpcDialogueGraph mainGraph, NpcProfile profile) {
        if (mainGraph == null) return;
        GraphState gs = graphState(mainGraph);
        if (profile != null) {
            ProfileState ps = profileState(profile);
            gs.autoWander = ps.autoWander;
            gs.wanderRadius = clampRadiusOrDefault(ps.wanderRadius, DEFAULT_WANDER_RADIUS);
            gs.autoAttackHostiles = ps.autoAttackHostiles;
            gs.aggroRadius = clampRadiusOrDefault(ps.aggroRadius, DEFAULT_AGGRO_RADIUS);
        }
        gs.synced = true;
        if (gs.cardX == UNSET_COORD || gs.cardY == UNSET_COORD) {
            NpcDialogueGraph.Node stats = NpcStatsGraph.find(mainGraph);
            int baseX = stats != null ? stats.x : DEFAULT_CARD_X;
            int baseY = stats != null ? stats.y + 170 + CARD_GAP_Y : DEFAULT_CARD_Y;
            gs.cardX = clampCoord(baseX);
            gs.cardY = clampCoord(baseY);
        }
    }

    public static void mirrorGraphToProfile(NpcDialogueGraph mainGraph, NpcProfile profile) {
        if (mainGraph == null || profile == null) return;
        GraphState gs = graphState(mainGraph);
        gs.synced = true;
        ProfileState ps = profileState(profile);
        ps.autoWander = gs.autoWander;
        ps.wanderRadius = clampRadiusOrDefault(gs.wanderRadius, DEFAULT_WANDER_RADIUS);
        ps.autoAttackHostiles = gs.autoAttackHostiles;
        ps.aggroRadius = clampRadiusOrDefault(gs.aggroRadius, DEFAULT_AGGRO_RADIUS);
    }

    public static void mirrorProfileToGraph(NpcProfile profile) {
        if (profile == null || profile.dialogueGraph == null) return;
        ProfileState ps = profileState(profile);
        GraphState gs = graphState(profile.dialogueGraph);
        mirrorStateToGraph(ps, gs);
    }

    private static void mirrorStateToGraph(ProfileState ps, GraphState gs) {
        gs.synced = true;
        gs.autoWander = ps.autoWander;
        gs.wanderRadius = clampRadiusOrDefault(ps.wanderRadius, DEFAULT_WANDER_RADIUS);
        gs.autoAttackHostiles = ps.autoAttackHostiles;
        gs.aggroRadius = clampRadiusOrDefault(ps.aggroRadius, DEFAULT_AGGRO_RADIUS);
    }

    private static boolean parseBool(String value) {
        if (value == null) return false;
        String t = value.trim();
        return "1".equals(t) || "true".equalsIgnoreCase(t);
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
