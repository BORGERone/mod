package dev.settledlands.npc;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server bridge from graph-node options to the player's cutscene fade overlay. */
public final class CutsceneFadeControl263 {
    private record SceneFade(String name, boolean fadeOut, boolean fadeIn) {}

    private static final Map<UUID, ArrayDeque<SceneFade>> CHAIN_FADES = new ConcurrentHashMap<>();

    private CutsceneFadeControl263() {}

    /** Called immediately before GuideDialogue starts the first cutscene in a graph chain. */
    public static void prepareInitial(ServerPlayer player, NpcDialogueGraph graph,
                                      NpcDialogueGraph.Node first) {
        if (player == null || graph == null || first == null || first.type != NpcDialogueGraph.CUTSCENE
                || first.cutsceneName == null || first.cutsceneName.isBlank()
                || !hasScene(player, first.cutsceneName)) {
            if (player != null) CHAIN_FADES.remove(player.getUUID());
            return;
        }
        PacketDistributor.sendToPlayer(player, new CutsceneFadePayload263(
                CutsceneFadeGraphData263.fadeOut(first), CutsceneFadeGraphData263.fadeIn(first)));

        ArrayDeque<SceneFade> queued = new ArrayDeque<>();
        HashSet<Integer> visited = new HashSet<>();
        visited.add(first.id);
        NpcDialogueGraph.Node next = graph.get(first.next);
        while (next != null && next.type == NpcDialogueGraph.CUTSCENE && visited.add(next.id)) {
            if (next.cutsceneName != null && !next.cutsceneName.isBlank()) {
                queued.addLast(new SceneFade(next.cutsceneName,
                        CutsceneFadeGraphData263.fadeOut(next), CutsceneFadeGraphData263.fadeIn(next)));
            }
            next = graph.get(next.next);
        }
        if (queued.isEmpty()) CHAIN_FADES.remove(player.getUUID());
        else CHAIN_FADES.put(player.getUUID(), queued);
    }

    /** Called at HEAD of CutsceneCamera.playChained, matching GuideDialogue's queued scene order. */
    public static void prepareChained(ServerPlayer player, String sceneName) {
        if (player == null) return;
        ArrayDeque<SceneFade> queued = CHAIN_FADES.get(player.getUUID());
        if (queued == null) return;
        SceneFade next = queued.pollFirst();
        if (queued.isEmpty()) CHAIN_FADES.remove(player.getUUID(), queued);
        if (next == null) return;
        if (!next.name().equals(sceneName) || !hasScene(player, sceneName)) {
            CHAIN_FADES.remove(player.getUUID());
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new CutsceneFadePayload263(next.fadeOut(), next.fadeIn()));
    }

    public static void clearChain(ServerPlayer player) {
        if (player != null) CHAIN_FADES.remove(player.getUUID());
    }

    private static boolean hasScene(ServerPlayer player, String name) {
        var keys = CutsceneStorage.get(player.server).scene(name);
        return keys != null && keys.size() >= 2;
    }
}
