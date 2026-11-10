import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.ChestLock397;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.door.DoorSync375;
import dev.settledlands.npc.GuideCorpseNpc;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.GuideTriggerNpc;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcHnsStealth416;
import dev.settledlands.npc.NpcIllegalHack417;
import java.util.Arrays;
import java.util.UUID;
import net.dndats.hackersandslashers.HackersAndSlashers;
import net.dndats.hackersandslashers.utils.helper.EntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Build 417 runtime JVM probe for illegal lockpicking («Незаконно») on Door and Chest cards.
 *
 * Executes the real compiled {@code NpcIllegalHack417.class}, {@code DoorGraph382.class},
 * {@code ChestLock397.class}, {@code ChestGraph396.class}, {@code NpcDialogueGraph.class} and
 * {@code NpcHnsStealth416.class} from the target jar against Java 21 runtime doubles.
 */
public final class IllegalHack417Probe {

    public static void main(String[] args) {
        // 1) NBT round-trip of «Незаконно» (Node.varLocal / "VarLocal") on Door and Chest cards.
        NpcDialogueGraph doorGraph = DoorGraph382.defaultGraph(true);
        NpcDialogueGraph.Node doorNode = DoorGraph382.doorNode(doorGraph);
        boolean doorDefaultLegal = !DoorGraph382.illegal(doorNode);
        DoorGraph382.setIllegal(doorNode, true);
        int[] doorSettingsOn = DoorGraph382.settings(doorGraph);
        CompoundTag savedDoor = doorGraph.save();
        NpcDialogueGraph loadedDoor = NpcDialogueGraph.load(savedDoor);
        boolean doorSavedIllegal = DoorGraph382.illegal(DoorGraph382.doorNode(loadedDoor))
                && doorSettingsOn != null && doorSettingsOn.length >= 5 && doorSettingsOn[4] == 1;

        NpcDialogueGraph chestGraph = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node chestNode = ChestGraph396.chestNode(chestGraph);
        boolean chestDefaultLegal = !ChestLock397.illegal(chestNode);
        ChestLock397.setIllegal(chestNode, true);
        CompoundTag savedChest = chestGraph.save();
        NpcDialogueGraph loadedChest = NpcDialogueGraph.load(savedChest);
        boolean chestSavedIllegal = ChestLock397.illegal(ChestGraph396.chestNode(loadedChest));
        System.out.println("NBT_ROUNDTRIP doorDefaultLegal=" + doorDefaultLegal
                + " doorSavedIllegal=" + doorSavedIllegal
                + " chestDefaultLegal=" + chestDefaultLegal
                + " chestSavedIllegal=" + chestSavedIllegal);

        // 2) World + NPC + player setup with Hackers 'n Slashers active.
        MinecraftServer server = new MinecraftServer();
        ServerLevel level = new ServerLevel(server);
        ServerPlayer thief = new ServerPlayer(
                level, UUID.fromString("00000000-0000-0000-0000-000000000010"), 2.0D, 64.0D, 0.0D);
        server.getPlayerList().addPlayer(thief);

        GuideNpc guard = new GuideNpc(level, 0.0D, 64.0D, 0.0D);
        guard.yHeadRot = -90.0F; // facing +X toward thief at (2, 64, 0)
        GuideTriggerNpc trigger = new GuideTriggerNpc(level, 2.0D, 64.0D, 0.5D);
        GuideCorpseNpc corpse = new GuideCorpseNpc(level, 2.0D, 64.0D, -0.5D);
        level.setNpcs(Arrays.asList(guard, trigger, corpse));

        HackersAndSlashers.serverConfig.enable_stealth_system = true;
        NpcHnsStealth416.resetReflectionForTests();
        NpcIllegalHack417.clearSessionsForTests();

        BlockPos lockPos = new BlockPos(2, 64, 0);

        // 3) Already watched at click time (sightLevel = 95 >= 90, unobstructed LoS) -> block start!
        guard.setLineOfSight(thief, true);
        EntityHelper.setSightLevel(thief, guard, 95.0D);
        thief.clearMessages();
        PacketDistributor.clearSent();
        boolean startWhileWatched = NpcIllegalHack417.beginIllegalHack(
                thief, level, lockPos, false, "minecraft:overworld@2,64,0");
        boolean watchedMsgShown = NpcIllegalHack417.MSG_WATCHED.equals(thief.lastMessageKey())
                && thief.lastOverlay();
        boolean noSessionCreated = NpcIllegalHack417.activeSessionCount() == 0;
        System.out.println("START_BLOCKED refused=" + (!startWhileWatched)
                + " watchedMsg=" + watchedMsgShown
                + " noSession=" + noSessionCreated);

        // 4) Blind entities (GuideTriggerNpc, GuideCorpseNpc) and occluded guard behind wall DO NOT block.
        EntityHelper.setSightLevel(thief, trigger, 100.0D);
        EntityHelper.setSightLevel(thief, corpse, 100.0D);
        guard.setLineOfSight(thief, false); // wall between guard and thief
        thief.clearMessages();
        boolean startBehindWall = NpcIllegalHack417.beginIllegalHack(
                thief, level, lockPos, true, "minecraft:overworld@2,64,0");
        boolean wallAllowed = startBehindWall
                && thief.lastMessageKey() == null
                && NpcIllegalHack417.activeSessionCount() == 1;
        NpcIllegalHack417.endSession(thief);
        System.out.println("WALL_AND_BLIND_ALLOWED ok=" + wallAllowed);

        // 5) Hidden in stealth (LoS = true, but HnS sightLevel = 35 < 90) -> start succeeds,
        //    then guard spots thief mid-hack (sightLevel = 92) -> immediate abort + X| + watched msg!
        guard.setLineOfSight(thief, true);
        EntityHelper.setSightLevel(thief, guard, 35.0D);
        thief.clearMessages();
        PacketDistributor.clearSent();
        boolean startedHidden = NpcIllegalHack417.beginIllegalHack(
                thief, level, lockPos, true, "minecraft:overworld@2,64,0");
        NpcIllegalHack417.onNpcServerTick(guard); // still hidden at 35 -> must NOT abort
        boolean stillActiveAt35 = startedHidden
                && NpcIllegalHack417.activeSessionCount() == 1
                && thief.lastMessageKey() == null;

        EntityHelper.setSightLevel(thief, guard, 92.0D);
        NpcIllegalHack417.onNpcServerTick(guard); // spotted -> MUST abort immediately!
        Object lastPayload = PacketDistributor.lastPayload();
        boolean sentAbortPacket = (lastPayload instanceof DoorSync375 sync)
                && "X|".equals(sync.data());
        boolean midHackAborted = NpcIllegalHack417.activeSessionCount() == 0
                && sentAbortPacket
                && NpcIllegalHack417.MSG_WATCHED.equals(thief.lastMessageKey());
        System.out.println("MID_HACK_ABORT stillActiveAt35=" + stillActiveAt35
                + " abortedOn92=" + midHackAborted);

        // 6) ESC / manual close (`endSession`) clears session so walking into NPC view later does NOT
        //    falsely trigger «За вами наблюдают».
        EntityHelper.setSightLevel(thief, guard, 10.0D);
        thief.clearMessages();
        PacketDistributor.clearSent();
        boolean startedBeforeEsc = NpcIllegalHack417.beginIllegalHack(
                thief, level, lockPos, false, "minecraft:overworld@2,64,0");
        NpcIllegalHack417.endSession(thief); // simulates client sending !cancel on ESC
        EntityHelper.setSightLevel(thief, guard, 100.0D);
        NpcIllegalHack417.onNpcServerTick(guard);
        boolean noFalseWatchedAfterEsc = startedBeforeEsc
                && NpcIllegalHack417.activeSessionCount() == 0
                && thief.lastMessageKey() == null
                && PacketDistributor.lastPayload() == null;
        System.out.println("ESC_CLEANUP noFalsePositive=" + noFalseWatchedAfterEsc);

        // 7) Final solve guard (`checkBeforeComplete`): refuses illegal solve when watched, allows legal.
        thief.clearMessages();
        PacketDistributor.clearSent();
        EntityHelper.setSightLevel(thief, guard, 96.0D);
        boolean illegalSolveRefused = !NpcIllegalHack417.checkBeforeComplete(
                thief, level, lockPos, true)
                && NpcIllegalHack417.MSG_WATCHED.equals(thief.lastMessageKey())
                && (PacketDistributor.lastPayload() instanceof DoorSync375 s2)
                && "X|".equals(s2.data());
        thief.clearMessages();
        boolean legalSolveAllowed = NpcIllegalHack417.checkBeforeComplete(
                thief, level, lockPos, false)
                && thief.lastMessageKey() == null;
        System.out.println("SOLVE_GUARD illegalRefused=" + illegalSolveRefused
                + " legalAllowed=" + legalSolveAllowed);

        // 8) Fallback when Hackers 'n Slashers stealth is disabled: behind guard's back allowed,
        //    turning head onto thief aborts!
        HackersAndSlashers.serverConfig.enable_stealth_system = false;
        guard.yHeadRot = 90.0F; // facing -X, so thief at (+5, 64, 0) is behind guard's back!
        ServerPlayer backThief = new ServerPlayer(
                level, UUID.fromString("00000000-0000-0000-0000-000000000011"), 5.0D, 64.0D, 0.0D);
        server.getPlayerList().addPlayer(backThief);
        guard.setLineOfSight(backThief, true);
        BlockPos backPos = new BlockPos(5, 64, 0);
        boolean fallbackBehindAllowed = NpcIllegalHack417.beginIllegalHack(
                backThief, level, backPos, false, "minecraft:overworld@5,64,0");
        guard.yHeadRot = -90.0F; // guard turns around to face +X!
        NpcIllegalHack417.onNpcServerTick(guard);
        boolean fallbackTurnAborted = NpcIllegalHack417.activeSessionCount() == 0
                && NpcIllegalHack417.MSG_WATCHED.equals(backThief.lastMessageKey());
        System.out.println("FALLBACK_NO_HNS behindAllowed=" + fallbackBehindAllowed
                + " turnAborted=" + fallbackTurnAborted);
    }
}
