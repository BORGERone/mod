package dev.settledlands.npc;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * Build 290: who last hurt this NPC - the value of the blue output of an "Урон" node.
 *
 * <p>The damage node already knows when it fires: {@code GuideDialogue#handleNpcDamaged}
 * is the one place every damage event goes through, and it carries the {@link DamageSource}
 * with the attacker in it. This class remembers that attacker per NPC, and the pin on the
 * damage card hands it to whatever reads that node.</p>
 *
 * <p>The record is deliberately short lived: a damage node says "кто меня ударил", not
 * "кто меня ударил когда-то". After {@link #WINDOW_TICKS} without damage the output is
 * empty and the nodes reading it behave as if nothing was found. A body that left or died
 * is dropped immediately.</p>
 *
 * <p>The node's own filter still applies: {@code Фильтр} (any / player / monster / non-player)
 * and {@code Мин. урон} decide whether this damage counts for that node, so two damage nodes
 * on one NPC can carry different attackers.</p>
 */
public final class NpcDamageValue290 {

    /** How long a damage event stays readable, in ticks (10 s). */
    public static final int WINDOW_TICKS = 200;

    private static final Map<GuideNpc, Last> LAST =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NpcDamageValue290() {}

    /** Records the attacker of one damage event. Called from the damage hook. */
    public static void record(GuideNpc npc, DamageSource source, float amount) {
        if (npc == null || source == null) return;
        if (npc.level() == null || npc.level().isClientSide) return;
        Entity attacker = source.getEntity();
        if (attacker == null) return;
        LAST.put(npc, new Last(attacker, source, amount, npc.level().getGameTime()));
    }

    /** True when this node id is an "Урон" node of this NPC. */
    public static boolean isDamageSource(GuideNpc npc, int nodeId) {
        return damageNode(npc, nodeId) != null;
    }

    /** The attacker the node would hand out right now, or null. */
    public static Entity damager(GuideNpc npc, int nodeId) {
        NpcDialogueGraph.Node node = damageNode(npc, nodeId);
        if (node == null || npc.level() == null) return null;
        Last last = LAST.get(npc);
        if (last == null) return null;
        long now = npc.level().getGameTime();
        if (now - last.tick > WINDOW_TICKS || now < last.tick) {
            LAST.remove(npc);
            return null;
        }
        if (last.amount + 1.0e-4F < node.minDamage) return null;
        if (!GuideDialogue.matchesDamageFilter(node.damageFilter, last.source)) return null;
        Entity attacker = last.attacker;
        if (attacker == null || !attacker.isAlive() || attacker.isRemoved()) return null;
        if (attacker.level() != npc.level()) return null;
        return attacker;
    }

    /** The "Урон" node with this id in any of the NPC's three graphs, or null. */
    private static NpcDialogueGraph.Node damageNode(GuideNpc npc, int nodeId) {
        if (npc == null || nodeId < 0) return null;
        NpcProfile profile;
        try {
            profile = npc.profile();
        } catch (Throwable ignored) {
            return null;
        }
        if (profile == null) return null;
        NpcDialogueGraph.Node node = NpcValueSource290.node(profile.dialogueGraph, nodeId);
        if (node == null) node = NpcValueSource290.node(profile.walkGraph, nodeId);
        if (node == null) node = NpcValueSource290.node(profile.animGraph, nodeId);
        if (node == null || node.type != NpcDialogueGraph.DAMAGE) return null;
        return node;
    }

    private static final class Last {
        final Entity attacker;
        final DamageSource source;
        final float amount;
        final long tick;

        Last(Entity attacker, DamageSource source, float amount, long tick) {
            this.attacker = attacker;
            this.source = source;
            this.amount = amount;
            this.tick = tick;
        }
    }
}
