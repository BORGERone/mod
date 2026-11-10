import dev.settledlands.npc.GuideCorpseNpc;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.GuideTriggerNpc;
import dev.settledlands.npc.NpcHnsStealth416;
import java.util.Arrays;
import java.util.UUID;
import net.dndats.hackersandslashers.HackersAndSlashers;
import net.dndats.hackersandslashers.utils.helper.DatapackHelper;
import net.dndats.hackersandslashers.utils.helper.EntityHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Build 416 runtime JVM probe for {@link NpcHnsStealth416}.
 *
 * Executes the real compiled {@code NpcHnsStealth416.class} from the target jar against
 * faithful Hackers 'n Slashers public API doubles and lightweight entity doubles.
 */
public final class HnsStealth416Probe {

    public static void main(String[] args) {
        Level level = new Level();
        GuideNpc guard = new GuideNpc(level, 0.0D, 64.0D, 0.0D);
        guard.yHeadRot = 0.0F; // facing +Z

        Player sneaky = new Player(level, UUID.fromString("00000000-0000-0000-0000-000000000001"),
                0.0D, 64.0D, -3.0D); // behind guard (-Z)
        Player visible = new Player(level, UUID.fromString("00000000-0000-0000-0000-000000000002"),
                6.0D, 64.0D, 0.0D);  // to the side (+X), farther away (6m > 3m)
        level.setPlayers(Arrays.asList(sneaky, visible));

        HackersAndSlashers.serverConfig.enable_stealth_system = true;
        guard.setLineOfSight(sneaky, true);
        guard.setLineOfSight(visible, true);

        // 1) Sneaking behind NPC (sightLevel = 0): head MUST NOT rotate toward sneaky player.
        EntityHelper.setSightLevel(sneaky, guard, 0.0D);
        EntityHelper.setSightLevel(visible, guard, 0.0D);
        Player p1 = NpcHnsStealth416.pickVisibleLookPlayer(level, guard, 12.0D);
        boolean look1 = NpcHnsStealth416.lookHeadAtVisiblePlayer(guard, sneaky);
        System.out.println("HIDDEN_BEHIND pickNull=" + (p1 == null)
                + " look=" + look1 + " headUnchanged=" + (Math.abs(guard.yHeadRot) < 0.001F));

        // 2) Partial suspicion (< 90, e.g. 55): still not spotted -> must NOT acquire head lock.
        EntityHelper.setSightLevel(sneaky, guard, 55.0D);
        Player p2 = NpcHnsStealth416.pickVisibleLookPlayer(level, guard, 12.0D);
        System.out.println("SUSPICIOUS_55 pickNull=" + (p2 == null)
                + " canAcquire=" + NpcHnsStealth416.canLookAtPlayer(guard, sneaky, false));

        // 3) Closer hidden player (3m, sight=0) MUST NOT mask farther spotted player (6m, sight=95).
        EntityHelper.setSightLevel(sneaky, guard, 0.0D);
        EntityHelper.setSightLevel(visible, guard, 95.0D);
        Player p3 = NpcHnsStealth416.pickVisibleLookPlayer(level, guard, 12.0D);
        boolean look3 = NpcHnsStealth416.lookHeadAtVisiblePlayer(guard, p3);
        System.out.println("MASKING_BYPASS pickedVisible=" + (p3 == visible)
                + " look=" + look3 + " headMoved=" + (Math.abs(guard.yHeadRot) > 0.1F));

        // 4) Hysteresis: once spotted & tracked, sight dipping to 50 (>= 40) retains smooth gaze,
        //    whereas dropping below 40 (e.g. 20) releases gaze immediately.
        EntityHelper.setSightLevel(visible, guard, 50.0D);
        boolean retain50 = NpcHnsStealth416.lookHeadAtVisiblePlayer(guard, visible);
        Player p4a = NpcHnsStealth416.pickVisibleLookPlayer(level, guard, 12.0D);
        EntityHelper.setSightLevel(visible, guard, 20.0D);
        boolean drop20 = NpcHnsStealth416.lookHeadAtVisiblePlayer(guard, visible);
        Player p4b = NpcHnsStealth416.pickVisibleLookPlayer(level, guard, 12.0D);
        System.out.println("HYSTERESIS retain50=" + retain50 + " pick50=" + (p4a == visible)
                + " drop20=" + (!drop20) + " pick20Null=" + (p4b == null));

        // 5) Wall occlusion: even with sight=100, losing line of sight stops head tracking through walls.
        EntityHelper.setSightLevel(visible, guard, 100.0D);
        guard.setLineOfSight(visible, false);
        boolean wallBlocked = !NpcHnsStealth416.lookHeadAtVisiblePlayer(guard, visible)
                && NpcHnsStealth416.pickVisibleLookPlayer(level, guard, 12.0D) == null;
        guard.setLineOfSight(visible, true);
        System.out.println("WALL_OCCLUSION blocked=" + wallBlocked);

        // 6) Invisibility potion vs True Vision.
        guard.setLineOfSight(sneaky, false);
        visible.setInvisible(true);
        boolean invisHidden = (NpcHnsStealth416.pickVisibleLookPlayer(level, guard, 12.0D) == null);
        DatapackHelper.setMobTrueVision(guard, true);
        boolean trueVisionSees = (NpcHnsStealth416.pickVisibleLookPlayer(level, guard, 12.0D) == visible);
        DatapackHelper.setMobTrueVision(guard, false);
        visible.setInvisible(false);
        guard.setLineOfSight(sneaky, true);
        System.out.println("INVISIBILITY hidden=" + invisHidden + " trueVision=" + trueVisionSees);

        // 7) Active dialogue overrides stealth & walls so talking NPCs face their interlocutor.
        EntityHelper.setSightLevel(sneaky, guard, 0.0D);
        guard.setLineOfSight(sneaky, false);
        guard.setTalking(true);
        boolean talkLooks = NpcHnsStealth416.canLookAtPlayer(guard, sneaky, false);
        guard.setTalking(false);
        guard.setLineOfSight(sneaky, true);
        System.out.println("DIALOGUE_OVERRIDE looks=" + talkLooks);

        // 8) Blind technical entities (GuideTriggerNpc, GuideCorpseNpc) never look and scrub HnS sight/suspicion.
        GuideTriggerNpc trigger = new GuideTriggerNpc(level, 0.0D, 64.0D, 0.0D);
        GuideCorpseNpc corpse = new GuideCorpseNpc(level, 1.0D, 64.0D, 0.0D);
        EntityHelper.setSightLevel(sneaky, trigger, 100.0D);
        EntityHelper.addDummySuspicion(trigger);
        EntityHelper.setSightLevel(sneaky, corpse, 100.0D);
        EntityHelper.addDummySuspicion(corpse);

        boolean blindNoLook = !NpcHnsStealth416.shouldLookAtPlayer(trigger)
                && !NpcHnsStealth416.shouldLookAtPlayer(corpse)
                && NpcHnsStealth416.pickVisibleLookPlayer(level, trigger, 12.0D) == null
                && NpcHnsStealth416.pickVisibleLookPlayer(level, corpse, 12.0D) == null;
        NpcHnsStealth416.clearBlindEntitySight(trigger);
        NpcHnsStealth416.clearBlindEntitySight(corpse);
        boolean scrubbed = EntityHelper.getSightLevel(sneaky, trigger) == 0.0D
                && !EntityHelper.hasSuspicionEntries(trigger)
                && EntityHelper.getSightLevel(sneaky, corpse) == 0.0D
                && !EntityHelper.hasSuspicionEntries(corpse);
        System.out.println("BLIND_ENTITIES noLook=" + blindNoLook + " scrubbed=" + scrubbed);

        // 9) Fallback when Hackers 'n Slashers stealth system is disabled in server config.
        HackersAndSlashers.serverConfig.enable_stealth_system = false;
        EntityHelper.setSightLevel(sneaky, guard, 0.0D);
        guard.setLineOfSight(sneaky, true);
        boolean disabledLosLooks = NpcHnsStealth416.canLookAtPlayer(guard, sneaky, false);
        guard.setLineOfSight(sneaky, false);
        boolean disabledWallBlocks = !NpcHnsStealth416.canLookAtPlayer(guard, sneaky, false);
        System.out.println("STEALTH_DISABLED losLooks=" + disabledLosLooks
                + " wallBlocks=" + disabledWallBlocks);
    }
}
