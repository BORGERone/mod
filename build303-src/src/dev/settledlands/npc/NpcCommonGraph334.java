package dev.settledlands.npc;

import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * Build 334: one "Общее" graph for all NPCs - the lookups, in one place.
 *
 * <p>The graph itself lives in {@link NpcCommonGraphStorage} (world data). This class is the only
 * way the rest of the mod touches it, so the rules are written once:</p>
 *
 * <ul>
 *   <li>no common graph yet - {@link #graph} answers null and every NPC keeps ticking its own tab,
 *       exactly as before this build;</li>
 *   <li>a common graph exists - every NPC ticks it instead of its own, and the editor of every NPC
 *       shows it;</li>
 *   <li>saving the tab from any NPC stores the graph here once, so what the author edited applies
 *       to every NPC at once, including NPCs created later.</li>
 * </ul>
 *
 * <p>The server lookup is cached: {@code tickSubGraph} runs for every NPC on every tick, and a
 * {@code SavedData} lookup per NPC per tick would allocate a factory each time. One reference
 * comparison per call, and one lookup per server.</p>
 */
public final class NpcCommonGraph334 {

    /** The server the cached store belongs to; a new server means the cache is stale. */
    private static MinecraftServer cachedServer;

    private static NpcCommonGraphStorage cachedStore;

    /** True while the common graph is running the damage chain, so the hook does not recurse. */
    private static boolean inDamage;

    private NpcCommonGraph334() {
    }

    /** The shared graph, or null while the tab has never been saved. */
    public static NpcDialogueGraph graph(MinecraftServer server) {
        NpcCommonGraphStorage store = store(server);
        return store == null ? null : store.graph();
    }

    /** Stores the graph of the tab; returns false when there is no server to store it in. */
    public static boolean save(MinecraftServer server, NpcDialogueGraph graph) {
        NpcCommonGraphStorage store = store(server);
        if (store == null || graph == null) return false;
        store.setGraph(graph);
        return true;
    }

    /**
     * Runs the damage chain of the common graph for one NPC. Returns false when there is nothing
     * common to run (the caller then lets the stock body run its own graph) and true when the chain
     * was handled here.
     */
    public static boolean damage(GuideNpc npc, DamageSource source, float amount,
            Map<Integer, Long> cooldowns) {
        if (npc == null || inDamage) return false;
        // Asked through Entity: the same call the base jar already makes, so nothing new is invoked.
        net.minecraft.world.level.Level level = ((Entity) npc).level();
        NpcDialogueGraph common = level == null ? null : graph(level.getServer());
        if (common == null) return false;
        inDamage = true;
        try {
            GuideDialogue.handleNpcDamaged(npc, source, amount, cooldowns, common);
        } finally {
            inDamage = false;
        }
        return true;
    }

    private static NpcCommonGraphStorage store(MinecraftServer server) {
        if (server == null) return null;
        if (server != cachedServer) {
            cachedServer = server;
            cachedStore = NpcCommonGraphStorage.get(server);
        }
        return cachedStore;
    }
}
