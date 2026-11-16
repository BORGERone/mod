package dev.settledlands.npc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Build 335: the buttons of the «Общее» graph are offered in the conversation.
 *
 * <p>The common graph was only ticked (checks, time, timers) and used by the damage chain: the talk
 * itself builds its answers from {@code profile.dialogueGraph} only - {@code sendGraph} lists the
 * {@code choices} of the node the player is on, {@code pickGraph} looks the picked id up in that same
 * graph - so a button placed in «Общее» could never be seen.</p>
 *
 * <p>This class gives the talk a graph in which both live. At the moment a conversation is opened it
 * copies the NPC's own graph, copies the whole common graph into it under fresh ids and hangs the
 * buttons that nothing else points at (the roots of the common graph) onto the line the talk starts
 * with. From there the stock dialogue code does the rest: the buttons are filtered by the same
 * visibility rules, a pick runs the same action and triggers, and the chain of the common graph is
 * walked with the stock resolver - IF nodes, variables, random nodes and cutscenes all behave as
 * they do in the NPC's own graph, because for the length of the talk that merged graph <em>is</em>
 * the NPC's graph.</p>
 *
 * <p>Every common node that ends a chain is pointed back at the starting line instead of being left
 * open, so when the common chain is over the player is back in front of the NPC rather than looking
 * at a finished conversation.</p>
 *
 * <p>The swap is per NPC and lasts only while somebody is talking to it. It is never written to disk:
 * {@code NpcProfile.save} - which is also what the config editor sends - puts the NPC's own graph
 * back for the length of that call, so a chunk save, a copy of the NPC or an edit of another player
 * cannot pick the merged graph up.</p>
 *
 * <p>Cost: one identity lookup per conversation start, the copy of two graphs when the talk opens
 * (cached: a second player talking to the same NPC reuses the merged graph), and nothing at all per
 * tick.</p>
 */
public final class NpcCommonDialogue335 {

    /** One entry per NPC whose profile currently carries a merged graph. */
    private static final Map<NpcProfile, State> STATES = new IdentityHashMap<>();

    /** Which NPC each talking player is attached to, so closing the talk releases the right one. */
    private static final Map<UUID, NpcProfile> OPEN = new HashMap<>();

    private NpcCommonDialogue335() {
    }

    /**
     * Gives this conversation a graph that holds the NPC's own one plus the common buttons. Called
     * right after the previous conversation was closed and before the new session is built, so
     * everything the talk does from here on sees the merged graph.
     */
    public static void attach(Player player, GuideNpc npc) {
        if (!(player instanceof ServerPlayer) || npc == null) return;
        NpcProfile profile = npc.profile();
        if (profile == null) return;
        UUID uuid = player.getUUID();
        if (OPEN.get(uuid) == profile) return;                  // already in this very conversation
        detach(uuid);                                          // the player left somebody else
        NpcDialogueGraph own = profile.dialogueGraph;
        State known = STATES.get(profile);
        if (known != null && known.merged == own) {             // another player is already inside
            known.sessions++;
            OPEN.put(uuid, profile);
            return;
        }
        if (known != null) STATES.remove(profile);              // the profile was replaced meanwhile
        NpcDialogueGraph common = NpcCommonGraph334.graph(server(npc));
        if (common == null) return;
        List<Integer> roots = roots(common);
        if (roots.isEmpty()) return;
        if (!GuideDialogue.graphActive(profile)) return;         // still an old-style profile talk
        NpcDialogueGraph merged = merge(own, common, roots);
        if (merged == null) return;
        STATES.put(profile, new State(own, merged));
        profile.dialogueGraph = merged;
        OPEN.put(uuid, profile);
    }

    /** The conversation is over: hand the NPC its own graph back once nobody is talking to it. */
    public static void detach(ServerPlayer player) {
        if (player != null) detach(player.getUUID());
    }

    /**
     * Puts the NPC's own graph back so that a save - which is what the entity NBT, the copy tool and
     * the config editor all go through - never sees the merged one. Returns the graph to put back
     * afterwards, or {@code null} when this profile was not carrying one.
     */
    public static NpcDialogueGraph strip(NpcProfile profile) {
        if (profile == null) return null;
        State state = STATES.get(profile);
        if (state == null || profile.dialogueGraph != state.merged) return null;
        profile.dialogueGraph = state.original;
        return state.merged;
    }

    public static void unstrip(NpcProfile profile, NpcDialogueGraph merged) {
        if (profile == null || merged == null) return;
        State state = STATES.get(profile);
        if (state != null && state.merged == merged && profile.dialogueGraph == state.original) {
            profile.dialogueGraph = merged;
        }
    }

    private static void detach(UUID uuid) {
        NpcProfile profile = OPEN.remove(uuid);
        if (profile != null) release(profile);
    }

    private static void release(NpcProfile profile) {
        State state = STATES.get(profile);
        if (state == null) return;
        if (--state.sessions > 0) return;
        if (profile.dialogueGraph == state.merged) profile.dialogueGraph = state.original;
        STATES.remove(profile);
    }

    /**
     * The buttons of the common graph that nothing points at. Those are its entry points - what the
     * author drops on the canvas and connects forwards; buttons living inside a chain are reached
     * through that chain and must not be offered a second time at the start of every talk.
     */
    private static List<Integer> roots(NpcDialogueGraph graph) {
        Set<Integer> referenced = new HashSet<>();
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (node == null) continue;
            addId(referenced, node.next);
            addId(referenced, node.falseNext);
            addId(referenced, node.varInputNode);
            addIds(referenced, node.choices);
            addIds(referenced, node.triggers);
            addIds(referenced, node.randomBranches);
        }
        List<Integer> roots = new ArrayList<>();
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (node == null || node.type != NpcDialogueGraph.BUTTON) continue;
            if (node.text == null || node.text.isBlank()) continue;
            if (referenced.contains(node.id)) continue;
            roots.add(node.id);
        }
        return roots;
    }

    /**
     * Everything reachable from the roots inside the common graph - the chains behind the buttons,
     * the triggers hanging off them and the buttons of those chains.
     */
    private static Set<Integer> reachable(NpcDialogueGraph graph, List<Integer> roots) {
        Set<Integer> keep = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty() && keep.size() < NpcDialogueGraph.MAX_NODES) {
            Integer id = queue.poll();
            if (id == null || id < 0 || !keep.add(id)) continue;
            NpcDialogueGraph.Node node = graph.get(id);
            if (node == null) continue;
            if (node.next >= 0) queue.add(node.next);
            if (node.falseNext >= 0) queue.add(node.falseNext);
            if (node.varInputNode >= 0) queue.add(node.varInputNode);
            enqueue(queue, node.choices);
            enqueue(queue, node.triggers);
            enqueue(queue, node.randomBranches);
        }
        return keep;
    }

    private static void enqueue(Deque<Integer> queue, List<Integer> ids) {
        if (ids == null) return;
        for (Integer value : ids) {
            if (value != null && value >= 0) queue.add(value);
        }
    }

    /** The NPC's graph plus the common one under fresh ids, with the common roots on the first line. */
    private static NpcDialogueGraph merge(NpcDialogueGraph base, NpcDialogueGraph common, List<Integer> roots) {
        if (base == null || common.nodes == null || common.nodes.isEmpty()) return null;
        NpcDialogueGraph merged = base.copy();
        if (merged == null || merged.nodes == null) return null;
        int entry = merged.entry;
        if (merged.get(entry) == null) return null;

        int nextId = 0;
        for (Integer key : merged.nodes.keySet()) {
            if (key != null && key >= nextId) nextId = key + 1;
        }
        // Above the ids of both graphs, so a copied node can never take an id the common graph uses.
        for (Integer key : common.nodes.keySet()) {
            if (key != null && key >= nextId) nextId = key + 1;
        }
        // Only what a root button can lead to is carried over: the checks, timers and quest nodes of
        // the common graph run from the tick and from the quest list, they have no place in a talk.
        Set<Integer> keep = reachable(common, roots);
        if (keep.isEmpty()) return null;
        Map<Integer, Integer> remap = new HashMap<>(Math.max(16, keep.size() * 2));
        for (Integer key : common.nodes.keySet()) {
            if (key != null && keep.contains(key)) remap.put(key, nextId++);
        }

        List<Integer> added = new ArrayList<>(roots.size());
        for (NpcDialogueGraph.Node node : common.nodes.values()) {
            if (node == null || !keep.contains(node.id)) continue;
            Integer fresh = remap.get(node.id);
            if (fresh == null) {
                fresh = nextId++;
                remap.put(node.id, fresh);
            }
            NpcDialogueGraph.Node copy = node.copy();
            if (copy == null) continue;
            copy.id = fresh;
            copy.next = mapped(remap, copy.next);
            copy.falseNext = mapped(remap, copy.falseNext);
            copy.varInputNode = mapped(remap, copy.varInputNode);
            remapAll(remap, copy.choices);
            remapAll(remap, copy.triggers);
            remapAll(remap, copy.randomBranches);
            // A chain of the common graph that ends ends the conversation by default; inside a talk it
            // has to lead back to the NPC instead, or the player is left with a closed dialogue.
            if (copy.next < 0 && copy.builtinTopic < 0) copy.next = entry;
            if (roots.contains(node.id)) added.add(fresh);
            merged.nodes.put(fresh, copy);
        }
        if (added.isEmpty()) return null;
        startLines(merged, entry, added);
        return merged;
    }

    /**
     * Hangs the common buttons onto the line the talk opens with. The entry of a graph is usually the
     * line itself but it may also be an IF or a variable that hands over to one, so this walks those
     * forward nodes and stops at the first real lines - the buttons appear where the conversation
     * starts and nowhere deeper.
     */
    private static void startLines(NpcDialogueGraph merged, int entry, List<Integer> roots) {
        Set<Integer> seen = new HashSet<>();
        Set<Integer> lines = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(entry);
        while (!queue.isEmpty() && seen.size() < 64) {
            Integer id = queue.poll();
            if (id == null || !seen.add(id)) continue;
            NpcDialogueGraph.Node node = merged.get(id);
            if (node == null) continue;
            if (node.type == NpcDialogueGraph.LINE && node.special == NpcDialogueGraph.NORMAL) {
                lines.add(id);
                continue;
            }
            if (node.next >= 0) queue.add(node.next);
            if (node.falseNext >= 0) queue.add(node.falseNext);
            for (Integer branch : node.randomBranches) {
                if (branch != null && branch >= 0) queue.add(branch);
            }
        }
        if (lines.isEmpty()) lines.add(entry);
        for (Integer id : lines) {
            NpcDialogueGraph.Node node = merged.get(id);
            if (node == null) continue;
            for (Integer root : roots) {
                if (!node.choices.contains(root)) node.choices.add(root);
            }
        }
    }

    private static int mapped(Map<Integer, Integer> remap, int id) {
        if (id < 0) return id;
        Integer fresh = remap.get(id);
        return fresh == null ? id : fresh;
    }

    private static void remapAll(Map<Integer, Integer> remap, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (int i = 0; i < ids.size(); i++) {
            Integer value = ids.get(i);
            if (value == null || value < 0) continue;
            Integer fresh = remap.get(value);
            if (fresh != null) ids.set(i, fresh);
        }
    }

    private static void addId(Set<Integer> ids, int id) {
        if (id >= 0) ids.add(id);
    }

    private static void addIds(Set<Integer> ids, List<Integer> list) {
        if (list == null) return;
        for (Integer value : list) {
            if (value != null && value >= 0) ids.add(value);
        }
    }

    private static MinecraftServer server(GuideNpc npc) {
        Level level = ((Entity) npc).level();
        return level == null ? null : level.getServer();
    }

    /** What the profile pointed at before the merge, and what is in its place now. */
    private static final class State {

        final NpcDialogueGraph original;
        final NpcDialogueGraph merged;
        int sessions = 1;

        State(NpcDialogueGraph original, NpcDialogueGraph merged) {
            this.original = original;
            this.merged = merged;
        }
    }
}
