package dev.settledlands.npc;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Build 345: "Источник урона" matches what the dropdown says.
 *
 * <p>The editor offers four sources in one order and stores the chosen row as the node's number:
 * 0 - любое ("Any"), 1 - от игрока ("Player"), 2 - от монстра ("Monster"), 3 - не от игрока. The card
 * of the node draws the label back with that same order, so a node that says "От игрока (Player)" holds
 * 1. The damage chain, however, read the number in another order - 0 игрок, 1 монстр, 2 не игрок,
 * 3 любое - so "От игрока" was tested as "от монстра" and a node with the source set to the player
 * never fired when a player hit the NPC. Nothing could ever reach the action: no heal, no line, no
 * command. "Любое" had the mirror-image problem and only answered player damage.</p>
 *
 * <p>This class holds the reading the editor and the card already use, and the hook in
 * {@code GuideDialogue.matchesDamageFilter} hands every call to it - the damage trigger and the
 * damage value node both ask that one method, so both now agree with the screen.</p>
 *
 * <p>The filter is a plain function of the source: the entity that caused the damage is
 * {@code getEntity()}, falling back to {@code getDirectEntity()} for a source that only names the
 * projectile. That is the same resolution the stock method made; only the numbering changes.</p>
 */
public final class NpcDamageFilter345 {

    /** "Любой урон (Any)": every hit fires. */
    public static final int ANY = 0;

    /** "От игрока (Player)". */
    public static final int PLAYER = 1;

    /** "От монстра (Monster)". */
    public static final int MONSTER = 2;

    /** "Не от игрока": monsters, the world, a fall - everything but a player. */
    public static final int NON_PLAYER = 3;

    private NpcDamageFilter345() {
    }

    /** True when a node set to {@code filter} has to fire for this damage. */
    public static boolean matches(int filter, DamageSource source) {
        Entity entity = damager(source);
        return switch (filter) {
            case PLAYER -> entity instanceof Player;
            case MONSTER -> entity instanceof Enemy;
            case NON_PLAYER -> !(entity instanceof Player);
            default -> true;
        };
    }

    /** The entity behind a damage source, or null: what the stock method resolved as well. */
    public static Entity damager(DamageSource source) {
        if (source == null) return null;
        Entity entity = source.getEntity();
        return entity != null ? entity : source.getDirectEntity();
    }
}
