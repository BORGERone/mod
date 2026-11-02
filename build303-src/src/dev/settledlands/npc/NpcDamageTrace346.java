package dev.settledlands.npc;

import java.util.Map;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 346: a bounded trace of the damage chain, to find out where a damage node is lost.
 *
 * <p>Nothing here changes behaviour - it reads the same graph the chain reads and writes what it
 * sees, once per hit, into {@code latest.log}:</p>
 *
 * <pre>
 * [dmg] hit npc='Проводник' amount=1.0 source=entity=ServerPlayer direct=null profile=true
 *       own=enabled=true entry=0 nodes=6 damage=1
 * [dmg] chain npc='Проводник' graph=… nodes=6 amount=1.0 gameTime=1234
 * [dmg]   node #4 type=6 min=1 filter=1(player) cooldown=1s if-in=0 -> MATCH
 * </pre>
 *
 * <p>The three lines answer the three questions the log cannot answer today: did the hit reach the
 * NPC at all, is the graph the chain looks at the graph the author edited (and is it enabled), and
 * which gate - minimum damage, source, cooldown, an incoming If - stops the node that should
 * have fired.</p>
 *
 * <p>Bounded on purpose: {@link #LIMIT} lines per session, then the trace switches itself off, so a
 * long fight cannot flood the log and the file stays small enough to read.</p>
 */
public final class NpcDamageTrace346 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DamageTrace346");

    /** Lines allowed for the whole session; a fight asks a handful, so this is plenty. */
    private static final int LIMIT = 80;

    private static int lines;

    private NpcDamageTrace346() {
    }

    /** Called at the head of {@code GuideNpc.hurt}: the hit arrived, whatever the chain does next. */
    public static void hurt(GuideNpc npc, DamageSource source, float amount) {
        if (npc == null) return;
        NpcProfile profile = npc.profile();
        NpcDialogueGraph own = profile == null ? null : profile.dialogueGraph;
        note("hit npc='" + name(npc) + "' amount=" + amount + " source=" + describe(source)
                + " profile=" + (profile != null) + " own=" + summary(own));
        list(own, null, amount, source, 0L);
    }

    /**
     * Called at the head of {@code GuideDialogue.handleNpcDamaged}: which graph the chain is about to
     * walk, and what each damage node of it would decide.
     */
    public static void chain(GuideNpc npc, DamageSource source, float amount,
            Map<Integer, Long> cooldowns, NpcDialogueGraph graph) {
        long now = now(npc);
        note("chain npc='" + name(npc) + "' graph=" + summary(graph) + " amount=" + amount
                + " gameTime=" + now);
        list(graph, cooldowns, amount, source, now);
    }

    /** Every damage node of the graph with the verdict of each gate, in the order the chain asks. */
    private static void list(NpcDialogueGraph graph, Map<Integer, Long> cooldowns, float amount,
            DamageSource source, long now) {
        if (graph == null) return;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (node == null || node.type != 6) continue;
            note("  node #" + node.id + " type=6 min=" + node.minDamage
                    + " filter=" + filter(node.damageFilter) + " cooldown=" + node.damageCooldown + "s"
                    + " if-in=" + incomingIf(graph, node.id) + " -> "
                    + verdict(node, cooldowns, amount, source, now, incomingIf(graph, node.id)));
        }
    }

    /** The same order the chain asks its questions, and the first one that answers no. */
    private static String verdict(NpcDialogueGraph.Node node, Map<Integer, Long> cooldowns,
            float amount, DamageSource source, long now, int ifIn) {
        int min = Math.max(0, node.minDamage);
        if (amount < min) return "skip: amount " + amount + " < min " + min;
        if (!NpcDamageFilter345.matches(node.damageFilter, source)) {
            return "skip: filter " + filter(node.damageFilter) + ", damager " + describe(source);
        }
        if (cooldowns != null) {
            Long last = cooldowns.get(node.id);
            long ticks = Math.max(0, node.damageCooldown) * 20L;
            if (last != null && now - last < ticks) {
                return "skip: cooldown " + (now - last) + "/" + ticks + " ticks";
            }
        } else {
            return "gate-ok (cooldown map not seen here)";
        }
        return ifIn == 0 ? "MATCH" : "MATCH if the " + ifIn + " If node(s) feeding it pass";
    }

    private static int incomingIf(NpcDialogueGraph graph, int id) {
        int count = 0;
        for (NpcDialogueGraph.Node other : graph.nodes.values()) {
            if (other == null || other.type != 3) continue;
            if (other.next == id || other.falseNext == id) count++;
        }
        return count;
    }

    private static String summary(NpcDialogueGraph graph) {
        if (graph == null) return "none";
        int damage = 0;
        for (NpcDialogueGraph.Node node : graph.nodes.values()) {
            if (node != null && node.type == 6) damage++;
        }
        return "enabled=" + graph.enabled + " entry=" + graph.entry + " nodes=" + graph.nodes.size()
                + " damage=" + damage;
    }

    private static String filter(int value) {
        return switch (value) {
            case 0 -> "0(any)";
            case 1 -> "1(player)";
            case 2 -> "2(monster)";
            case 3 -> "3(non-player)";
            default -> value + "(?)";
        };
    }

    private static String describe(DamageSource source) {
        if (source == null) return "none";
        return "entity=" + type(source.getEntity()) + " direct=" + type(source.getDirectEntity());
    }

    private static String type(Entity entity) {
        return entity == null ? "null" : entity.getClass().getSimpleName();
    }

    private static String name(GuideNpc npc) {
        return npc == null ? "?" : npc.getName().getString();
    }

    private static long now(GuideNpc npc) {
        if (npc == null) return 0L;
        net.minecraft.world.level.Level level = ((Entity) npc).level();
        return level == null ? 0L : level.getGameTime();
    }

    private static void note(String line) {
        if (lines > LIMIT) return;
        lines++;
        if (lines == LIMIT + 1) {
            LOG.warn("[dmg] trace limit reached - later hits are not logged");
            return;
        }
        LOG.warn("[dmg] {}", line);
    }
}
