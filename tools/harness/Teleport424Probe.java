import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.TeleportActions424;
import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.npc.client.BlockMenu363;
import dev.settledlands.npc.client.TeleportCard424;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Build 424 JVM probe: exercises {@link TeleportNode424}, {@link TeleportActions424},
 * {@link TeleportCard424} and {@link BlockMenu363} directly against the shipped jar and compile
 * stubs.
 */
public final class Teleport424Probe {

    private static int passed = 0;

    public static void main(String[] args) {
        testNodeParsingAndFormatting();
        testCoordInputFilter();
        testWorldsAndCustomDimension();
        testBlockMenuPreservesStockRows();
        testTeleportActionsExecution();
        testTeleportCardDropdown();
        System.out.println("Teleport424Probe: " + passed + " checks passed");
    }

    private static void check(boolean cond, String label) {
        if (!cond) {
            throw new AssertionError("FAIL: " + label);
        }
        passed++;
    }

    private static void testNodeParsingAndFormatting() {
        NpcDialogueGraph g = new NpcDialogueGraph();
        NpcDialogueGraph.Node tp = g.add(NpcDialogueGraph.TRIGGER, 10, 20);
        check(!TeleportNode424.isTeleportNode(tp), "plain trigger is not teleport");
        TeleportNode424.applyDefaults(tp);
        check(TeleportNode424.isTeleportNode(tp), "applyDefaults makes teleport node");
        check("minecraft:overworld".equals(TeleportNode424.dimensionOf(tp)), "default dimension");
        check("".equals(TeleportNode424.rawX(tp)), "default rawX blank");
        check("".equals(TeleportNode424.rawY(tp)), "default rawY blank");
        check("".equals(TeleportNode424.rawZ(tp)), "default rawZ blank");

        TeleportNode424.Parsed p0 = TeleportNode424.parse(tp.action);
        check(p0 != null && p0.x == 0.0 && p0.y == 0.0 && p0.z == 0.0, "blank coords parse as 0");

        TeleportNode424.setWorld(tp, 3);
        check("settledlands:purgatory".equals(TeleportNode424.dimensionOf(tp)), "world 3 is purgatory");
        TeleportNode424.setX(tp, "-123,5");
        TeleportNode424.setY(tp, "64");
        TeleportNode424.setZ(tp, "");
        TeleportNode424.Parsed p1 = TeleportNode424.parse(tp.action);
        check(p1 != null, "parsed non-null");
        check("settledlands:purgatory".equals(p1.dimension), "parsed purgatory");
        check(Math.abs(p1.x - (-123.5)) < 1e-9, "comma decimal x");
        check(Math.abs(p1.y - 64.0) < 1e-9, "integer y");
        check(p1.z == 0.0, "empty z = 0");
        check("-123.5".equals(TeleportNode424.formatCoord(p1.x)), "format decimal");
        check("64".equals(TeleportNode424.formatCoord(64.0)), "format int");
        check("0".equals(TeleportNode424.formatCoord(-0.0)), "format negative zero");

        // Clamping & legacy space-separated + stripped forms
        TeleportNode424.Parsed pClamp = TeleportNode424.parse("teleport nether 99999999 -99999999 42");
        check(pClamp != null && "minecraft:the_nether".equals(pClamp.dimension), "short nether alias");
        check(pClamp.x == 30_000_000.0 && pClamp.y == -30_000_000.0 && pClamp.z == 42.0, "clamped coords");

        // Non-teleport triggers must never parse as teleport
        check(TeleportNode424.parse("give:minecraft:apple") == null, "other trigger ignored");
        check(TeleportNode424.parse("trigger:command:/say hi") == null, "command trigger ignored");
        check(TeleportNode424.parse("") == null, "empty ignored");

        // Card special guard
        NpcDialogueGraph.Node travel = g.add(NpcDialogueGraph.TRIGGER, 0, 0);
        TeleportNode424.applyDefaults(travel);
        travel.special = 1;
        check(!TeleportNode424.isTeleportNode(travel), "special != 0 excluded");
        NpcDialogueGraph.Node stats = g.add(16, 0, 0);
        stats.action = TeleportNode424.DEFAULT_ACTION;
        check(!TeleportNode424.isTeleportNode(stats), "stats node (type 16) excluded");
    }

    private static void testCoordInputFilter() {
        check(TeleportNode424.isCoordInput(""), "empty allowed");
        check(TeleportNode424.isCoordInput("-"), "leading minus allowed while typing");
        check(TeleportNode424.isCoordInput("-12.5"), "negative decimal allowed");
        check(TeleportNode424.isCoordInput("10,25"), "comma decimal allowed");
        check(!TeleportNode424.isCoordInput("12-3"), "mid-minus rejected");
        check(!TeleportNode424.isCoordInput("1.2.3"), "double dot rejected");
        check(!TeleportNode424.isCoordInput("12a"), "alpha rejected");
        check(TeleportNode424.parseCoord("-") == 0.0, "dangling minus = 0");
        check(TeleportNode424.parseCoord("-.") == 0.0, "dangling minus-dot = 0");
    }

    private static void testWorldsAndCustomDimension() {
        check(TeleportNode424.worldCount() == 5, "5 built-in dimensions");
        check(TeleportNode424.worldIndexForId("minecraft:overworld") == 0, "overworld index 0");
        check(TeleportNode424.worldIndexForId("minecraft:the_nether") == 1, "nether index 1");
        check(TeleportNode424.worldIndexForId("minecraft:the_end") == 2, "end index 2");
        check(TeleportNode424.worldIndexForId("settledlands:purgatory") == 3, "purgatory index 3");
        check(TeleportNode424.worldIndexForId("settledlands:hell_dungeon") == 4, "hell_dungeon index 4");
        check(TeleportNode424.worldIndexForId("twilightforest:twilight_forest") == -1, "custom dim = -1");

        TeleportNode424.Parsed hdAlias = TeleportNode424.parse("trigger:teleport:hell_dungeon|||");
        check(hdAlias != null && "settledlands:hell_dungeon".equals(hdAlias.dimension),
                "hell_dungeon alias normalized");
        TeleportNode424.Parsed dgAlias = TeleportNode424.parse("teleport dungeon 1 2 3");
        check(dgAlias != null && "settledlands:hell_dungeon".equals(dgAlias.dimension),
                "dungeon alias normalized");

        TeleportNode424.Parsed custom = TeleportNode424.parse(
                "trigger:teleport:twilightforest:twilight_forest|10|80|-20");
        check(custom != null && "twilightforest:twilight_forest".equals(custom.dimension),
                "custom mod dim preserved");
    }

    private static void testBlockMenuPreservesStockRows() {
        int[] rows = BlockMenu363.rows();
        check(rows.length > 0, "stock block rows present");
        check(BlockMenu363.count() == rows.length + 1, "block menu count = rows + 1");
        check(BlockMenu363.isTeleportRow(rows.length), "last row is Teleport");
        check(!BlockMenu363.isTeleportRow(0), "row 0 is not Teleport");
        check(BlockMenu363.stockRow(rows.length) == -1, "Teleport has no stockRow");
        check(BlockMenu363.doorType(rows.length) == -1, "Teleport has no stock doorType");
        for (int i = 0; i < rows.length; i++) {
            check(BlockMenu363.stockRow(i) == rows[i], "stockRow preserved at " + i);
            check(BlockMenu363.doorType(i) >= 0, "doorType valid at " + i);
        }
        check(BlockMenu363.colour(rows.length) == TeleportNode424.ACCENT_COLOR, "Teleport menu color");
    }

    private static final class MockServer extends MinecraftServer {
        final Map<String, ServerLevel> levels = new HashMap<>();

        @Override
        public ServerLevel getLevel(ResourceKey<Level> key) {
            return key == null ? null : levels.get(key.toString());
        }
    }

    private static final class MockPlayer extends ServerPlayer {
        final MinecraftServer srv;
        boolean clearedFire = false;
        Vec3 lastDelta = null;
        ServerLevel arrivedLevel = null;
        double arrivedX, arrivedY, arrivedZ;
        float yRot = 135.0F;
        float xRot = -15.0F;
        float arrivedYRot, arrivedXRot;
        int teleportCalls = 0;

        MockPlayer(MinecraftServer srv) {
            this.srv = srv;
            this.fallDistance = 17.5F;
        }

        @Override public MinecraftServer getServer() { return srv; }
        @Override public void clearFire() { clearedFire = true; }
        @Override public void setDeltaMovement(Vec3 motion) { lastDelta = motion; }
        @Override public float getYRot() { return yRot; }
        @Override public float getXRot() { return xRot; }

        @Override
        public boolean teleportTo(ServerLevel level, double x, double y, double z,
                java.util.Set<?> relatives, float yaw, float pitch) {
            teleportCalls++;
            arrivedLevel = level;
            arrivedX = x;
            arrivedY = y;
            arrivedZ = z;
            arrivedYRot = yaw;
            arrivedXRot = pitch;
            return true;
        }
    }

    private static void testTeleportActionsExecution() {
        MockServer server = new MockServer();
        ServerLevel overworld = new ServerLevel();
        ServerLevel purgatory = new ServerLevel();
        server.levels.put("ResourceKey[minecraft:dimension / minecraft:overworld]", overworld);
        server.levels.put("ResourceKey[minecraft:dimension / settledlands:purgatory]", purgatory);

        MockPlayer player = new MockPlayer(server);
        boolean ok = TeleportActions424.teleport(player, "trigger:teleport:settledlands:purgatory|12.5||-40");
        check(ok, "teleport to purgatory succeeded");
        check(player.teleportCalls == 1, "teleportTo called once");
        check(player.arrivedLevel == purgatory, "arrived in purgatory ServerLevel");
        check(player.arrivedX == 12.5 && player.arrivedY == 0.0 && player.arrivedZ == -40.0,
                "arrived coords with blank y = 0");
        check(player.fallDistance == 0.0F, "fallDistance cleared before teleport");
        check(player.clearedFire, "fire cleared before teleport");
        check(player.lastDelta == Vec3.ZERO, "velocity reset to Vec3.ZERO");
        check(player.arrivedYRot == 135.0F && player.arrivedXRot == -15.0F, "yaw/pitch preserved");

        // Unloaded / missing dimension returns false gracefully without throwing
        boolean missing = TeleportActions424.teleport(player, "trigger:teleport:minecraft:the_end|0|64|0");
        check(!missing && player.teleportCalls == 1, "missing dimension skipped safely");

        // Non-player entities are never teleported
        Entity nonPlayer = new Entity();
        check(!TeleportActions424.executeForEntity(nonPlayer, "trigger:teleport:minecraft:overworld|0|64|0"),
                "non-player entity ignored");
        check(TeleportActions424.executeForEntity(player, "trigger:teleport:minecraft:overworld|||"),
                "executeForEntity forwards ServerPlayer");
        check(player.arrivedLevel == overworld && player.arrivedX == 0.0 && player.arrivedY == 0.0
                && player.arrivedZ == 0.0, "blank coords teleport to 0, 0, 0 in overworld");
    }

    private static void testTeleportCardDropdown() {
        List<Component> std = TeleportCard424.worldDropdownEntries("minecraft:the_end");
        check(std.size() == 5, "standard dropdown has 5 entries");
        check(TeleportCard424.worldDropdownIndex("minecraft:the_end") == 2, "the_end index 2");
        check(TeleportCard424.worldDropdownIndex("settledlands:hell_dungeon") == 4, "hell_dungeon index 4");

        List<Component> cust = TeleportCard424.worldDropdownEntries("modid:dungeon_arena");
        check(cust.size() == 6, "custom dim appends 6th entry");
        check(TeleportCard424.worldDropdownIndex("modid:dungeon_arena") == 5, "custom dim selects index 5");
    }
}
