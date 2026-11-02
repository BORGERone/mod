import dev.settledlands.npc.NpcDamageFilter345;
import dev.settledlands.npc.NpcRandomLine345;
import dev.settledlands.npc.NpcRandomLine336;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Runs build 345's two guards: the damage source numbering the dropdown and the card use, and the dice
 * a random line throws for one NPC instead of for every NPC at once.
 */
public class GraphFixes345SmokeTest {

    static int failures = 0;

    static void check(String what, boolean ok) {
        if (ok) {
            System.out.println("ok    " + what);
        } else {
            failures++;
            System.out.println("FAIL  " + what);
        }
    }

    /** An entity that is a monster for the filter, without needing a level or a model. */
    static final class FakeMonster extends Entity implements Enemy {
    }

    /** A damage source that names the entity behind the hit. */
    static final class FakeSource extends DamageSource {
        private final Entity entity;

        FakeSource(Entity entity) {
            this.entity = entity;
        }

        @Override
        public Entity getEntity() {
            return entity;
        }

        @Override
        public Entity getDirectEntity() {
            return null;
        }
    }

    /** A source that only names the arrow, never the shooter. */
    static final class IndirectSource extends DamageSource {
        private final Entity direct;

        IndirectSource(Entity direct) {
            this.direct = direct;
        }

        @Override
        public Entity getEntity() {
            return null;
        }

        @Override
        public Entity getDirectEntity() {
            return direct;
        }
    }

    public static void main(String[] args) {
        DamageSource byPlayer = new FakeSource(new Player());
        DamageSource byMonster = new FakeSource(new FakeMonster());
        DamageSource byArrow = new IndirectSource(new Player());

        check("row 1 (player) fires for a player",
                NpcDamageFilter345.matches(NpcDamageFilter345.PLAYER, byPlayer));
        check("row 1 does not fire for a monster",
                !NpcDamageFilter345.matches(NpcDamageFilter345.PLAYER, byMonster));
        check("row 2 (monster) fires for a monster",
                NpcDamageFilter345.matches(NpcDamageFilter345.MONSTER, byMonster));
        check("row 2 does not fire for a player",
                !NpcDamageFilter345.matches(NpcDamageFilter345.MONSTER, byPlayer));
        check("row 3 (non-player) fires for a monster",
                NpcDamageFilter345.matches(NpcDamageFilter345.NON_PLAYER, byMonster));
        check("row 3 (non-player) does not fire for a player",
                !NpcDamageFilter345.matches(NpcDamageFilter345.NON_PLAYER, byPlayer));
        check("row 0 (any) fires for a player",
                NpcDamageFilter345.matches(NpcDamageFilter345.ANY, byPlayer));
        check("row 0 (any) fires for a monster",
                NpcDamageFilter345.matches(NpcDamageFilter345.ANY, byMonster));
        check("any row fires for a source with no entity",
                NpcDamageFilter345.matches(NpcDamageFilter345.ANY, new FakeSource(null)));
        check("row 1 refuses a source with no entity",
                !NpcDamageFilter345.matches(NpcDamageFilter345.PLAYER, new FakeSource(null)));
        check("row 3 (non-player) fires when nothing is behind the hit",
                NpcDamageFilter345.matches(NpcDamageFilter345.NON_PLAYER, new FakeSource(null)));
        check("an unknown row fires, as the stock switch did",
                NpcDamageFilter345.matches(99, byPlayer));
        check("a projectile still names its shooter through getEntity",
                NpcDamageFilter345.matches(NpcDamageFilter345.PLAYER, byArrow));
        check("no source is no crash", NpcDamageFilter345.damager(null) == null);

        // -- the dice belong to the NPC ----------------------------------------------------------
        Map<Integer, Integer> mine = new HashMap<>();
        int first = NpcRandomLine345.rollInto(mine, 7, 3);
        check("a roll lands inside the phrases", first >= 1 && first <= 3);
        check("the same NPC and node keep their phrase", NpcRandomLine345.rollInto(mine, 7, 3) == first);
        check("another node rolls on its own", NpcRandomLine345.rollInto(mine, 8, 3) >= 1);
        check("the phrases shrinking to one leaves no stale index",
                NpcRandomLine345.rollInto(mine, 7, 1) == 1);
        check("no phrases at all answers nothing", NpcRandomLine345.rollInto(mine, 9, 0) == 0);

        Set<Integer> seen = new HashSet<>();
        Map<Integer, Integer> room = new HashMap<>();
        for (int i = 0; i < 200; i++) {
            room.clear();
            seen.add(NpcRandomLine345.rollInto(room, 1, 4));
        }
        check("four phrases are all reachable", seen.size() == 4);
        check("no roll ever lands outside the phrases",
                seen.stream().allMatch(v -> v >= 1 && v <= 4));

        check("without an NPC the node's own example answers",
                NpcRandomLine345.pick(null) == null);
        check("the toggle stays the node's own flag", !NpcRandomLine336.random(null));
        check("entering and leaving a view leaves no NPC behind", speakingIsClear());

        if (failures == 0) {
            System.out.println("ALL OK");
        } else {
            System.out.println("FAILURES: " + failures);
            System.exit(1);
        }
    }

    static boolean speakingIsClear() {
        try {
            NpcRandomLine345.enter(null);
            NpcRandomLine345.exit();
            return NpcRandomLine345.speaking() == null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
