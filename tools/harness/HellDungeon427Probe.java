import com.mojang.brigadier.CommandDispatcher;
import dev.settledlands.dungeon.AxiomBlueprint427;
import dev.settledlands.dungeon.DungeonBlueprints427;
import dev.settledlands.dungeon.DungeonCommand425;
import dev.settledlands.dungeon.DungeonConfig427;
import dev.settledlands.dungeon.DungeonData425;
import dev.settledlands.dungeon.DungeonEvents425;
import dev.settledlands.dungeon.DungeonGenerator427;
import dev.settledlands.dungeon.DungeonManager425;
import dev.settledlands.dungeon.DungeonTemplate425;
import dev.settledlands.npc.TeleportActions424;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.ProbeRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Build 427 JVM probe: tests native Axiom {@code .bp} reading, external blueprint config and
 * folder auto-discovery, theme/tag/prefix filtering, rotated blockstates/block-entities/entities,
 * non-overlapping stitching, void-leak sealing, mid-build SavedData persistence, player-proximity
 * streaming via {@code ServerTickEvent.Post}, {@code /settledlands dungeon reload}, and full
 * real-schematic verification when {@code /tmp/dungeonz/Данж} is present.
 */
public final class HellDungeon427Probe {

    private static int passed = 0;

    private static final String[] TEST_BLOCKS = {
        "minecraft:air",
        "minecraft:blackstone",
        "minecraft:cracked_polished_blackstone_bricks",
        "minecraft:lapis_block",
        "minecraft:redstone_block",
        "minecraft:nether_bricks",
        "minecraft:polished_blackstone",
        "minecraft:polished_blackstone_brick_stairs",
        "minecraft:polished_blackstone_wall",
        "minecraft:polished_basalt",
        "minecraft:rail",
        "minecraft:soul_lantern",
        "minecraft:chest",
        "minecraft:skeleton_skull",
        "trialtuner:trial_spawner",
        "minecraft:obsidian",
        "minecraft:cracked_nether_bricks",
        "minecraft:chiseled_nether_bricks",
        "minecraft:chiseled_polished_blackstone",
        "minecraft:polished_blackstone_bricks",
        "minecraft:polished_blackstone_brick_slab",
        "minecraft:magma_block",
        "minecraft:gilded_blackstone",
        "minecraft:bone_block",
        "minecraft:chain",
        "minecraft:shroomlight",
        "minecraft:soul_fire",
        "minecraft:soul_soil",
        "minecraft:wither_skeleton_skull",
    };

    public static void main(String[] args) throws Exception {
        registerBlocks();
        testRotationMathAndProperties();
        testConfigCreationAutoDiscoveryAndFilters();
        testStreamingPlacementSaveLoadAndProximity();
        testRealArchiveIfPresent();
        testBiomeProgressionBuild429();
        testUniqueDungeonPerPlayerBuild428();
        testReflectionAndCommands();
        System.out.println("HellDungeon427Probe: " + passed + " checks passed");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError("FAIL: " + msg);
        }
        passed++;
    }

    private static final ResourceKey<Level> OVERWORLD_KEY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"));

    private static void registerBlocks() {
        ProbeRegistries.clear();
        for (String id : TEST_BLOCKS) {
            ProbeRegistries.putBlock(ResourceLocation.tryParse(id), new MockBlock(id));
        }
    }

    // ---- tests -------------------------------------------------------------

    private static void testRotationMathAndProperties() {
        Map<String, String> stairs = new LinkedHashMap<>();
        stairs.put("facing", "north");
        stairs.put("half", "bottom");
        check("east".equals(DungeonTemplate425.rotateProps(stairs, 1).get("facing")), "facing CW 90");
        check("south".equals(DungeonTemplate425.rotateProps(stairs, 2).get("facing")), "facing CW 180");
        check("west".equals(DungeonTemplate425.rotateProps(stairs, 3).get("facing")), "facing CW 270");

        Map<String, String> log = Map.of("axis", "x");
        check("z".equals(DungeonTemplate425.rotateProps(log, 1).get("axis")), "axis x->z");
        check("x".equals(DungeonTemplate425.rotateProps(log, 2).get("axis")), "axis x->x at 180");

        Map<String, String> skull = Map.of("rotation", "14");
        check("2".equals(DungeonTemplate425.rotateProps(skull, 1).get("rotation")), "rotation 14+4 mod 16 = 2");

        Map<String, String> wall = Map.of("north", "low", "east", "none", "south", "tall", "west", "none");
        Map<String, String> rWall = DungeonTemplate425.rotateProps(wall, 1);
        check("low".equals(rWall.get("east")) && "tall".equals(rWall.get("west"))
                && "none".equals(rWall.get("north")) && "none".equals(rWall.get("south")),
                "cardinal connections rotated CW");

        Map<String, String> rail = Map.of("shape", "north_east");
        check("south_east".equals(DungeonTemplate425.rotateProps(rail, 1).get("shape")), "rail corner CW");
        Map<String, String> asc = Map.of("shape", "ascending_west");
        check("ascending_north".equals(DungeonTemplate425.rotateProps(asc, 1).get("shape")), "ascending rail CW");

        // Block-center (bx + 0.5, bz + 0.5) must match entity rotation for all 4 turns
        for (int turns = 0; turns < 4; turns++) {
            double bx = 4, bz = -7;
            double rx = DungeonGenerator427.rotX((int) bx, (int) bz, turns) + 0.5;
            double rz = DungeonGenerator427.rotZ((int) bx, (int) bz, turns) + 0.5;
            double ex = DungeonGenerator427.rotEntityX(bx + 0.5, bz + 0.5, turns);
            double ez = DungeonGenerator427.rotEntityZ(bx + 0.5, bz + 0.5, turns);
            check(Math.abs(rx - ex) < 1e-9 && Math.abs(rz - ez) < 1e-9,
                    "entity center matches block rotation at turns=" + turns);
        }
    }

    private static void testConfigCreationAutoDiscoveryAndFilters() throws Exception {
        Path tmp = Files.createTempDirectory("sl_probe_cfg");
        try {
            DungeonConfig427 cfg = DungeonConfig427.loadOrCreate(tmp);
            check(Files.isRegularFile(tmp.resolve(DungeonConfig427.CONFIG_REL)),
                    "config/settledlands/dungeon.properties created");
            check(Files.isDirectory(tmp.resolve(DungeonConfig427.DEFAULT_BLUEPRINTS_REL)),
                    "config/settledlands/blueprints dir created");
            check(Files.isRegularFile(tmp.resolve(DungeonConfig427.DEFAULT_BLUEPRINTS_REL)
                    .resolve("README-BLUEPRINTS-RU.txt")), "Russian blueprint README created");
            check("auto".equals(cfg.blueprintsDir) && "auto".equals(cfg.filterMode), "default auto modes");
            check(cfg.minPieces == 75 && cfg.maxPieces == 95, "Build 429 default minPieces=75, maxPieces=95");

            // Empty folders -> embedded start.slbp fallback
            DungeonBlueprints427.Library emptyLib = DungeonBlueprints427.load(cfg);
            check(!emptyLib.hasExternalPieces() && emptyLib.startPiece != null
                    && emptyLib.startPiece.embeddedFallback, "empty dir falls back to embedded start.slbp");

            // Put blueprints into config/axiom/blueprints/Данж + an unrelated house.bp without lapis_block
            Path axiomDir = tmp.resolve("config/axiom/blueprints/Данж");
            Files.createDirectories(axiomDir);
            writeRoomBp(tmp.resolve("config/axiom/blueprints/house.bp"), "house",
                    List.of("decoration"), 0, 8, false, false, false, false, false);
            writeRoomBp(axiomDir.resolve("старт.bp"), "старт",
                    List.of(), 1, 10, true, false, false, true, true);
            writeRoomBp(axiomDir.resolve("а Коридор.bp"), "а Коридор",
                    List.of("dungeon", "infernal"), 2, 10, false, true, true, false, false);
            writeRoomBp(axiomDir.resolve("г Коридор.bp"), "г Коридор",
                    List.of("dungeon", "crypt"), 2, 10, false, true, true, false, false);
            writeRoomBp(axiomDir.resolve("к КоридорБезТега.bp"), "к КоридорБезТега",
                    List.of(), 2, 10, false, true, true, false, false);
            writeRoomBp(axiomDir.resolve("а Тупик.bp"), "а Тупик",
                    List.of("dungeon", "infernal"), 0, 10, false, true, false, false, false);
            writeRoomBp(axiomDir.resolve("п Коридор.bp"), "п Коридор",
                    List.of("dungeon", "flesh"), 2, 10, false, true, true, false, false);
            writeRoomBp(axiomDir.resolve("п Тупик.bp"), "п Тупик",
                    List.of("dungeon", "flesh"), 0, 10, false, true, false, false, false);
            writeRoomBp(axiomDir.resolve("с Канал.bp"), "с Канал",
                    List.of("dungeon", "sewer"), 2, 10, false, true, true, false, false);

            DungeonBlueprints427.Library autoLib = DungeonBlueprints427.load(cfg);
            check(autoLib.sourceDir.equals(tmp.resolve("config/axiom/blueprints")),
                    "auto-discovered config/axiom/blueprints when settledlands/blueprints is empty");
            check(autoLib.skippedNoMarkers == 1, "house.bp without lapis_block skipped cleanly");
            check(autoLib.failedParse == 0, "0 parse failures");
            check("auto->biomes(к->а+п)".equals(autoLib.appliedFilter) && autoLib.biomeProgression,
                    "auto selected biome progression к->а+п among mixed themes");
            check(autoLib.pieces.size() == 6 && autoLib.cryptCount == 2 && autoLib.infernalCount == 2
                    && autoLib.fleshCount == 2 && autoLib.deadEnds.size() == 2,
                    "filtered к+а+п pieces (including prefix-only к) and excluded sewer");

            // Now drop a custom room directly in config/settledlands/blueprints -> takes priority, auto->all
            Path customDir = tmp.resolve(DungeonConfig427.DEFAULT_BLUEPRINTS_REL);
            writeRoomBp(customDir.resolve("my_room.bp"), "my_room",
                    List.of(), 1, 10, false, true, true, false, false);
            DungeonBlueprints427.Library customLib = DungeonBlueprints427.load(cfg);
            check(customLib.sourceDir.equals(customDir), "config/settledlands/blueprints takes priority");
            check("auto->all".equals(customLib.appliedFilter) && !customLib.biomeProgression
                    && customLib.pieces.size() == 1,
                    "single-theme custom folder loads all pieces without biome split");
            check(customLib.startPiece != null && customLib.startPiece.embeddedFallback,
                    "embedded start.slbp used when custom folder lacks старт.bp");

            // Tag filter check
            DungeonConfig427 cryptCfg = new DungeonConfig427(
                    tmp, axiomDir.toString(), "tag:crypt", "старт",
                    60, 80, 230, -52, 240, true, "minecraft:nether_bricks", 8, 4096, true);
            DungeonBlueprints427.Library cryptLib = DungeonBlueprints427.load(cryptCfg);
            check(cryptLib.pieces.size() == 1 && "г Коридор".equals(cryptLib.pieces.get(0).key)
                    && !cryptLib.biomeProgression,
                    "tag:crypt filter selects only tag:crypt pieces");
        } finally {
            deleteTree(tmp);
        }
    }

    private static void testStreamingPlacementSaveLoadAndProximity() throws Exception {
        Path tmp = Files.createTempDirectory("sl_probe_stream");
        try {
            Path bpDir = tmp.resolve(DungeonConfig427.DEFAULT_BLUEPRINTS_REL);
            Files.createDirectories(bpDir);
            writeRoomBp(bpDir.resolve("старт.bp"), "старт",
                    List.of(), 1, 10, true, false, false, true, true);
            writeRoomBp(bpDir.resolve("а Прямой.bp"), "а Прямой",
                    List.of("dungeon", "infernal"), 1, 10, false, true, true, false, false);
            writeRoomBp(bpDir.resolve("а Развилка.bp"), "а Развилка",
                    List.of("dungeon", "infernal"), 2, 10, false, true, true, true, false);
            writeRoomBp(bpDir.resolve("а Тупик.bp"), "а Тупик",
                    List.of("dungeon", "infernal"), 0, 10, false, true, false, false, false);

            DungeonConfig427 cfg = new DungeonConfig427(
                    tmp, bpDir.toString(), "auto", "старт",
                    14, 18, 200, -52, 240, true, "minecraft:nether_bricks", 3, 256, true);
            DungeonBlueprints427.Library lib = DungeonBlueprints427.load(cfg);
            check(lib.pieces.size() == 3 && lib.connectors.size() == 2 && lib.deadEnds.size() == 1,
                    "synthetic room pool loaded");

            MockServer srv = new MockServer();
            MockLevel ow = new MockLevel(OVERWORLD_KEY, srv);
            MockLevel hd = new MockLevel(DungeonManager425.HELL_DUNGEON_LEVEL, srv);
            srv.overworld = ow;
            srv.levels.put(OVERWORLD_KEY.toString(), ow);
            srv.levels.put(DungeonManager425.HELL_DUNGEON_LEVEL.toString(), hd);

            DungeonData425 data = DungeonData425.get(srv);
            DungeonData425.Instance inst = data.getOrCreateInstance("default", 4242L, 10L);
            boolean init = DungeonManager425.ensureInstanceInitialized(hd, data, inst, lib);
            check(init && inst.planGenerated && inst.pieces.size() >= 8,
                    "plan generated (" + inst.pieces.size() + " pieces)");
            check(!inst.placed && inst.donePieceCount() == 3,
                    "only immediatePieces (3) placed synchronously, rest queued for ticks");
            check(lib.startPiece.content.entities.size() == 1, "start entity parsed from synthetic .bp");
            check(lib.startPiece.content.blockEntities.size() == 1, "start blockEntity parsed from synthetic .bp");
            // Verify start entrance rear seal was placed synchronously
            check("minecraft:nether_bricks".equals(hd.placedBlockId(
                    inst.originX + lib.startPiece.entrance.x,
                    inst.originY + lib.startPiece.entrance.y + 3,
                    inst.originZ + lib.startPiece.entrance.z)),
                    "start entrance rear arch sealed immediately");

            // Place a player near the LAST pending piece to verify proximity prioritization
            DungeonData425.PieceProgress lastPiece = inst.pieces.get(inst.pieces.size() - 1);
            MockPlayer player = new MockPlayer(srv, hd, lastPiece.minX + 1.0, lastPiece.minY + 2.0, lastPiece.minZ + 1.0);
            hd.playersList.add(player);

            int placedBeforeTick = lastPiece.blockCursor;
            DungeonManager425.tickPendingGeneration(srv, lib);
            check(lastPiece.done || lastPiece.blockCursor > placedBeforeTick,
                    "tickPendingGeneration prioritized pending room closest to player");

            // Round-trip SavedData mid-build
            CompoundTag nbt = data.save(new CompoundTag(), null);
            DungeonData425 restored = DungeonData425.load(nbt, null);
            DungeonData425.Instance rInst = restored.getInstance("default");
            check(rInst != null && rInst.pieces.size() == inst.pieces.size()
                    && rInst.donePieceCount() == inst.donePieceCount()
                    && rInst.seals.size() == inst.seals.size(),
                    "mid-build SavedData round-trip preserved exact piece & seal progress");

            // Run remaining ticks until completed
            int guard = 0;
            while (!inst.placed && guard++ < 500) {
                DungeonManager425.tickPendingGeneration(srv, lib);
            }
            check(inst.placed, "instance finished streaming within tick budget (" + guard + " ticks)");
            check(inst.donePieceCount() == inst.pieces.size(), "all planned pieces finished");

            // Zero raw lapis/redstone blocks left anywhere in placed blocks
            for (BlockState bs : hd.placedStates.values()) {
                if (bs instanceof MockBlockState mbs) {
                    check(!"minecraft:lapis_block".equals(mbs.blockId)
                            && !"minecraft:redstone_block".equals(mbs.blockId),
                            "no doorway marker leaked: " + mbs.blockId);
                }
            }
        } finally {
            deleteTree(tmp);
        }
    }

    private static void testRealArchiveIfPresent() throws Exception {
        Path realDir = Paths.get("/tmp/dungeonz/Данж");
        if (!Files.isDirectory(realDir)) {
            return;
        }
        Path tmp = Files.createTempDirectory("sl_probe_real");
        try {
            DungeonConfig427 cfg = new DungeonConfig427(
                    tmp, realDir.toString(), "auto", "старт",
                    75, 95, 230, -52, 240, true, "minecraft:nether_bricks", 8, 8192, true);
            DungeonBlueprints427.Library lib = DungeonBlueprints427.load(cfg);
            check(lib.totalBpFiles == 232 && lib.failedParse == 0, "all 232 real .bp parsed");
            check("auto->biomes(к->а+п)".equals(lib.appliedFilter) && lib.biomeProgression
                    && lib.pieces.size() == 142 && lib.cryptCount == 51
                    && lib.infernalCount == 48 && lib.fleshCount == 43,
                    "biome pool к+а+п selected (142 pieces: 51 crypt, 48 infernal, 43 flesh)");

            // Check 5 distinct seeds for strict crypt-near-start and both deep biomes present
            for (int s = 0; s < 5; s++) {
                DungeonGenerator427.Plan p = DungeonGenerator427.generate(lib, 0, 0, 0, 1000L + s * 7919L);
                check(p.pieces.size() >= 75 && p.pieces.size() <= 95,
                        "real biome plan #" + s + " size=" + p.pieces.size());
                check(p.cryptCount >= 15 && p.infernalCount >= 12 && p.fleshCount >= 12,
                        "real biome plan #" + s + " balanced [к=" + p.cryptCount
                                + ", а=" + p.infernalCount + ", п=" + p.fleshCount + "]");
                for (int i = 1; i < p.pieces.size(); i++) {
                    var pp = p.pieces.get(i);
                    if (pp.depth <= 3) {
                        check(DungeonBlueprints427.THEME_CRYPT.equals(pp.biome),
                                "real shallow room (depth=" + pp.depth + ") must be crypt, got " + pp.pieceKey);
                    }
                    // Dead-ends must belong to their branch's biome
                    if (pp.pieceKey.contains("Тупик")) {
                        if (DungeonBlueprints427.THEME_CRYPT.equals(pp.biome)) {
                            check(pp.pieceKey.startsWith("к "), "crypt dead-end in crypt branch: " + pp.pieceKey);
                        } else if (DungeonBlueprints427.THEME_INFERNAL.equals(pp.biome)) {
                            check(pp.pieceKey.startsWith("а "), "infernal dead-end in infernal branch: " + pp.pieceKey);
                        } else if (DungeonBlueprints427.THEME_FLESH.equals(pp.biome)) {
                            check(pp.pieceKey.startsWith("п "), "flesh dead-end in flesh branch: " + pp.pieceKey);
                        }
                    }
                }
            }

            MockServer srv = new MockServer();
            MockLevel ow = new MockLevel(OVERWORLD_KEY, srv);
            MockLevel hd = new MockLevel(DungeonManager425.HELL_DUNGEON_LEVEL, srv);
            srv.overworld = ow;
            srv.levels.put(OVERWORLD_KEY.toString(), ow);
            srv.levels.put(DungeonManager425.HELL_DUNGEON_LEVEL.toString(), hd);

            DungeonData425 data = DungeonData425.get(srv);
            DungeonData425.Instance inst = data.getOrCreateInstance("real", 7777L, 1L);
            DungeonManager425.ensureInstanceInitialized(hd, data, inst, lib);
            check(inst.pieces.size() >= 75 && inst.pieces.size() <= 95,
                    "real biome plan has " + inst.pieces.size() + " rooms");

            int ticks = 0;
            while (!inst.placed && ticks++ < 500) {
                DungeonManager425.tickPendingGeneration(srv, lib);
            }
            check(inst.placed, "real biome dungeon finished in " + ticks + " ticks");
            check(hd.placedStates.size() > 150000,
                    "real biome dungeon placed " + hd.placedStates.size() + " unique blocks");
            for (BlockState bs : hd.placedStates.values()) {
                if (bs instanceof MockBlockState mbs) {
                    check(!"minecraft:lapis_block".equals(mbs.blockId)
                            && !"minecraft:redstone_block".equals(mbs.blockId),
                            "zero raw marker blocks in real biome build");
                }
            }
            // Verify rear arch of старт is sealed (no void behind spawn)
            check("minecraft:nether_bricks".equals(hd.placedBlockId(12, -8, 0)),
                    "real старт rear arch (12,-8,0) sealed");
        } finally {
            deleteTree(tmp);
        }
    }

    private static void testBiomeProgressionBuild429() throws Exception {
        Path tmp = Files.createTempDirectory("hd429_biomes");
        try {
            Path bpDir = tmp.resolve("config/axiom/blueprints/Данж");
            Files.createDirectories(bpDir);
            writeRoomBp(bpDir.resolve("старт.bp"), "старт", List.of(),
                    0, 8, true, true, false, false, true);
            writeRoomBp(bpDir.resolve("к Коридор.bp"), "к Коридор", List.of("dungeon", "crypt"),
                    0, 10, false, true, true, false, false);
            writeRoomBp(bpDir.resolve("к Развилка.bp"), "к Развилка", List.of("dungeon", "crypt"),
                    0, 8, false, true, true, true, false);
            writeRoomBp(bpDir.resolve("к Тупик.bp"), "к Тупик", List.of("dungeon", "crypt"),
                    0, 6, false, true, false, false, false);
            writeRoomBp(bpDir.resolve("а Коридор.bp"), "а Коридор", List.of("dungeon", "infernal"),
                    0, 10, false, true, true, false, false);
            writeRoomBp(bpDir.resolve("а Развилка.bp"), "а Развилка", List.of("dungeon", "infernal"),
                    0, 8, false, true, true, true, false);
            writeRoomBp(bpDir.resolve("а Тупик.bp"), "а Тупик", List.of("dungeon", "infernal"),
                    0, 6, false, true, false, false, false);
            writeRoomBp(bpDir.resolve("п Коридор.bp"), "п Коридор", List.of("dungeon", "flesh"),
                    0, 10, false, true, true, false, false);
            writeRoomBp(bpDir.resolve("п Развилка.bp"), "п Развилка", List.of("dungeon", "flesh"),
                    0, 8, false, true, true, true, false);
            writeRoomBp(bpDir.resolve("п Тупик.bp"), "п Тупик", List.of("dungeon", "flesh"),
                    0, 6, false, true, false, false, false);

            DungeonConfig427 cfg = new DungeonConfig427(
                    tmp, bpDir.toString(), "auto", "старт",
                    28, 38, 220, -52, 240, true, "minecraft:nether_bricks", 8, 4096, true);
            DungeonBlueprints427.Library lib = DungeonBlueprints427.load(cfg);
            check(lib.biomeProgression && "auto->biomes(к->а+п)".equals(lib.appliedFilter),
                    "synthetic folder activated biome progression к->а+п");

            for (int s = 0; s < 5; s++) {
                DungeonGenerator427.Plan plan = DungeonGenerator427.generate(lib, 0, 0, 0, 4242L + s * 31337L);
                check(plan.pieces.size() >= 28 && plan.pieces.size() <= 38,
                        "synthetic biome plan #" + s + " size=" + plan.pieces.size());
                check(plan.cryptCount >= 5 && plan.infernalCount >= 4 && plan.fleshCount >= 4,
                        "synthetic biome plan #" + s + " has crypt+infernal+flesh ("
                                + plan.cryptCount + "/" + plan.infernalCount + "/" + plan.fleshCount + ")");
                for (int i = 1; i < plan.pieces.size(); i++) {
                    var pp = plan.pieces.get(i);
                    if (pp.depth < 3) {
                        check(DungeonBlueprints427.THEME_CRYPT.equals(pp.biome) && pp.pieceKey.startsWith("к "),
                                "depth " + pp.depth + " near start must be crypt: " + pp.pieceKey);
                    }
                    if (DungeonBlueprints427.THEME_INFERNAL.equals(pp.biome)) {
                        check(pp.pieceKey.startsWith("а "), "infernal piece must have prefix а: " + pp.pieceKey);
                    } else if (DungeonBlueprints427.THEME_FLESH.equals(pp.biome)) {
                        check(pp.pieceKey.startsWith("п "), "flesh piece must have prefix п: " + pp.pieceKey);
                    } else if (DungeonBlueprints427.THEME_CRYPT.equals(pp.biome)) {
                        check(pp.pieceKey.startsWith("к "), "crypt piece must have prefix к: " + pp.pieceKey);
                    }
                }
            }
        } finally {
            deleteTree(tmp);
        }
    }

    private static void testUniqueDungeonPerPlayerBuild428() throws Exception {
        Path tmp = Files.createTempDirectory("hd428_probe");
        try {
            Path bpDir = tmp.resolve("config/settledlands/blueprints");
            Files.createDirectories(bpDir);
            writeRoomBp(bpDir.resolve("старт.bp"), "старт", List.of("dungeon", "infernal"),
                    0, 8, true, true, false, false, false);
            for (int i = 1; i <= 6; i++) {
                writeRoomBp(bpDir.resolve("corridor_" + i + ".bp"), "corridor_" + i,
                        List.of("dungeon", "infernal"), 0, 6 + (i % 3) * 2, false, true, true, i % 2 == 0, false);
            }
            writeRoomBp(bpDir.resolve("тупик_1.bp"), "тупик_1", List.of("dungeon", "infernal"),
                    0, 6, false, true, false, false, false);
            writeRoomBp(bpDir.resolve("тупик_2.bp"), "тупик_2", List.of("dungeon", "infernal"),
                    0, 8, false, true, false, false, false);

            DungeonConfig427 defaultCfg = DungeonConfig427.loadOrCreate(tmp);
            check(defaultCfg.uniqueDungeonPerEntry,
                    "unique_dungeon_per_entry defaults to true in Build 428");
            String propText = Files.readString(tmp.resolve("config/settledlands/dungeon.properties"),
                    StandardCharsets.UTF_8);
            check(propText.contains("unique_dungeon_per_entry=true"),
                    "generated dungeon.properties documents unique_dungeon_per_entry=true");

            // Point live DungeonBlueprints427 cache at our temporary blueprint directory
            DungeonBlueprints427.Library lib = DungeonBlueprints427.reload(tmp);
            check(lib.config.uniqueDungeonPerEntry && lib.hasExternalPieces(),
                    "reloaded temp library has unique mode and external pieces");

            MockServer srv = new MockServer();
            MockLevel ow = new MockLevel(OVERWORLD_KEY, srv);
            MockLevel hd = new MockLevel(DungeonManager425.HELL_DUNGEON_LEVEL, srv);
            srv.overworld = ow;
            srv.levels.put(OVERWORLD_KEY.toString(), ow);
            srv.levels.put(DungeonManager425.HELL_DUNGEON_LEVEL.toString(), hd);

            MockPlayer playerA = new MockPlayer(srv, ow, 10.0, 64.0, 20.0, new UUID(0x1111L, 0xAAAAL));
            MockPlayer playerB = new MockPlayer(srv, ow, -30.0, 70.0, 40.0, new UUID(0x2222L, 0xBBBBL));

            // 1. Player A enters via Teleport with blank coords -> unique cell 0
            check(TeleportActions424.teleport(playerA, "trigger:teleport:settledlands:hell_dungeon|||"),
                    "player A enters via blank Teleport coords");
            DungeonData425 data = DungeonData425.get(srv);
            check(data.instanceCount() == 1, "1 instance created after player A enters");
            DungeonData425.Instance instA = data.allInstances().iterator().next();
            check(instA.index == 0 && instA.originX == 0 && instA.originZ == 0,
                    "player A lands in cell 0");
            check(playerA.curLevel == hd && Math.abs(playerA.px - instA.spawnX) < 1e-6,
                    "player A teleported to start chamber spawn, not (0,0,0)");
            check(instA.pieces.size() > 1, "player A has procedural room plan (" + instA.pieces.size() + " rooms)");

            // 2. Player B enters via Teleport with literal 0|0|0 -> unique cell 1 (512, 0, 0), NOT player A's corridor
            check(TeleportActions424.teleport(playerB, "trigger:teleport:settledlands:hell_dungeon|0|0|0"),
                    "player B enters via literal 0,0,0 Teleport coords");
            check(data.instanceCount() == 2, "player B allocates a separate instance");
            DungeonData425.Instance instB = null;
            for (DungeonData425.Instance cand : data.allInstances()) {
                if (!cand.id.equals(instA.id)) {
                    instB = cand;
                }
            }
            check(instB != null && instB.index == 1 && instB.originX == 512 && instB.originZ == 0,
                    "player B gets separate grid cell (512, 0, 0)");
            check(instB.seed != instA.seed, "player B receives distinct seed from player A");
            check(Math.abs(playerB.px - instB.spawnX) < 1e-6 && Math.abs(playerB.pz - instB.spawnZ) < 1e-6,
                    "player B placed at cell 1 entrance (" + instB.spawnX + "," + instB.spawnZ + ")");
            check(playerB.px >= 500.0, "player B not placed in shared (0,0,0) corridor");

            // Verify relative layouts actually differ (different piece sequence/rotations/offsets, not just cell origin)
            List<String> layoutA = new ArrayList<>();
            for (var pp : instA.pieces) {
                layoutA.add(pp.pieceKey + "@" + pp.turns + ":"
                        + (pp.ox - instA.originX) + "," + (pp.oz - instA.originZ));
            }
            List<String> layoutB = new ArrayList<>();
            for (var pp : instB.pieces) {
                layoutB.add(pp.pieceKey + "@" + pp.turns + ":"
                        + (pp.ox - instB.originX) + "," + (pp.oz - instB.originZ));
            }
            check(!layoutA.equals(layoutB), "player A and player B received distinct dungeon layouts");

            // 3. Player A leaves and enters again -> gets a 3rd unique instance
            check(DungeonManager425.leaveDungeon(playerA) && playerA.curLevel == ow,
                    "player A returned to overworld");
            check(TeleportActions424.teleport(playerA, "trigger:teleport:settledlands:hell_dungeon|||"),
                    "player A re-enters via Teleport");
            check(data.instanceCount() == 3, "player A re-entry creates a fresh 3rd instance");
            DungeonData425.Instance instA2 = null;
            for (DungeonData425.Instance cand : data.allInstances()) {
                if (cand.index == 2) instA2 = cand;
            }
            check(instA2 != null && instA2.seed != instA.seed && playerA.pz >= 500.0,
                    "player A re-entry lands in cell 2 with fresh seed");

            // 4. /settledlands dungeon enter (no id) creates 4th unique instance via DungeonManager425.enterUniqueOrDefault
            DungeonData425.Instance instCmd = DungeonManager425.enterUniqueOrDefault(playerB);
            check(instCmd != null && data.instanceCount() == 4 && instCmd.index == 3,
                    "enterUniqueOrDefault creates 4th unique instance");

            // 5. Explicit instance join allows co-op into Player A's first instance without creating a 5th instance
            check(DungeonManager425.enterDungeon(playerB, instA.id) && data.instanceCount() == 4,
                    "explicit instance id joins existing dungeon without creating new cell");
            check(Math.abs(playerB.px - instA.spawnX) < 1e-6 && Math.abs(playerB.pz - instA.spawnZ) < 1e-6,
                    "player B landed at player A's instance entrance");

            // 6. Explicit non-zero Teleport coords target exact coordinate
            check(TeleportActions424.teleport(playerB, "trigger:teleport:settledlands:hell_dungeon|516|-8|4")
                    && Math.abs(playerB.px - 516.0) < 1e-6
                    && Math.abs(playerB.py - (-8.0)) < 1e-6
                    && Math.abs(playerB.pz - 4.0) < 1e-6,
                    "explicit non-zero Teleport coords target exact coordinate");

            // 7. Disabling unique_dungeon_per_entry reverts blank/command entry to shared default
            Files.writeString(tmp.resolve("config/settledlands/dungeon.properties"),
                    "blueprints_dir=auto\nunique_dungeon_per_entry=false\n", StandardCharsets.UTF_8);
            DungeonBlueprints427.Library sharedLib = DungeonBlueprints427.reload(tmp);
            check(!sharedLib.config.uniqueDungeonPerEntry, "unique_dungeon_per_entry=false parsed from config");
            MockServer srvShared = new MockServer();
            MockLevel owShared = new MockLevel(OVERWORLD_KEY, srvShared);
            MockLevel hdShared = new MockLevel(DungeonManager425.HELL_DUNGEON_LEVEL, srvShared);
            srvShared.overworld = owShared;
            srvShared.levels.put(OVERWORLD_KEY.toString(), owShared);
            srvShared.levels.put(DungeonManager425.HELL_DUNGEON_LEVEL.toString(), hdShared);
            MockPlayer pSharedA = new MockPlayer(srvShared, owShared, 0, 64, 0, new UUID(1L, 1L));
            MockPlayer pSharedB = new MockPlayer(srvShared, owShared, 0, 64, 0, new UUID(2L, 2L));
            DungeonManager425.enterUniqueOrDefault(pSharedA);
            DungeonData425 sharedData = DungeonData425.get(srvShared);
            check(sharedData.getInstance(DungeonData425.DEFAULT_INSTANCE_ID) != null,
                    "shared mode uses default instance");
            check(sharedData.instanceCount() == 1, "created shared default instance once");
            DungeonManager425.enterUniqueOrDefault(pSharedB);
            check(sharedData.instanceCount() == 1,
                    "shared mode reuses existing default instance for second player");
        } finally {
            DungeonBlueprints427.reload();
            deleteTree(tmp);
        }
    }

    private static void testReflectionAndCommands() throws Exception {
        String[] classes = {
            "dev.settledlands.dungeon.AxiomBlueprint427",
            "dev.settledlands.dungeon.AxiomBlueprint427$Marker",
            "dev.settledlands.dungeon.AxiomBlueprint427$PieceTemplate",
            "dev.settledlands.dungeon.DungeonConfig427",
            "dev.settledlands.dungeon.DungeonBlueprints427",
            "dev.settledlands.dungeon.DungeonBlueprints427$Library",
            "dev.settledlands.dungeon.DungeonGenerator427",
            "dev.settledlands.dungeon.DungeonGenerator427$Plan",
            "dev.settledlands.dungeon.DungeonGenerator427$PlannedPiece",
            "dev.settledlands.dungeon.DungeonGenerator427$Seal",
            "dev.settledlands.dungeon.DungeonTemplate425",
            "dev.settledlands.dungeon.DungeonData425",
            "dev.settledlands.dungeon.DungeonData425$Instance",
            "dev.settledlands.dungeon.DungeonData425$PieceProgress",
            "dev.settledlands.dungeon.DungeonData425$SealProgress",
            "dev.settledlands.dungeon.DungeonManager425",
            "dev.settledlands.dungeon.DungeonEvents425",
            "dev.settledlands.dungeon.DungeonCommand425",
        };
        for (String name : classes) {
            Class<?> c = Class.forName(name);
            c.getDeclaredMethods();
            c.getDeclaredConstructors();
            c.getDeclaredFields();
            passed++;
        }

        // Verify ServerTickEvent.Post handler runs cleanly when nothing is pending
        MockServer srv = new MockServer();
        MockLevel ow = new MockLevel(OVERWORLD_KEY, srv);
        srv.overworld = ow;
        DungeonEvents425.onServerTick(new MockServerTickPost(srv));
        passed++;

        // Verify /settledlands dungeon reload command registration & invocation
        CommandDispatcher<CommandSourceStack> disp = new CommandDispatcher<>();
        DungeonCommand425.onRegisterCommands(new MockRegisterCommandsEvent(disp));
        List<String> msgs = new ArrayList<>();
        MockCommandSource src = new MockCommandSource(srv, msgs);
        var mReload = DungeonCommand425.class.getDeclaredMethod(
                "reload", com.mojang.brigadier.context.CommandContext.class);
        mReload.setAccessible(true);
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx =
                new com.mojang.brigadier.context.CommandContext<>() {
                    @Override public CommandSourceStack getSource() { return src; }
                };
        int rc = (Integer) mReload.invoke(null, ctx);
        check(rc == 1 && msgs.size() == 1 && msgs.get(0).contains("блупринты перечитаны"),
                "/settledlands dungeon reload returned feedback");
    }

    // ---- synthetic Axiom .bp generator ------------------------------------

    private static void writeRoomBp(Path path, String name, List<String> tags, int sideExitMode,
            int lenZ, boolean isStart, boolean hasLapis, boolean hasForwardRed,
            boolean hasSideRed, boolean hasEntity) throws IOException {
        // Build blocks inside [0..8, 0..7, 0..lenZ] (or shifted for start).
        int minX = 0, maxX = 8, maxY = 7;
        Map<String, Integer> palIdx = new LinkedHashMap<>();
        List<String> palNames = new ArrayList<>();
        List<Map<String, String>> palProps = new ArrayList<>();
        palIdx.put("minecraft:air", 0);
        palNames.add("minecraft:air");
        palProps.add(Map.of());

        List<int[]> voxels = new ArrayList<>();
        java.util.function.BiConsumer<int[], String> put = (xyz, spec) -> {
            String block = spec;
            Map<String, String> props = Map.of();
            int br = spec.indexOf('[');
            if (br > 0) {
                block = spec.substring(0, br);
                props = new LinkedHashMap<>();
                for (String kv : spec.substring(br + 1, spec.length() - 1).split(",")) {
                    String[] p = kv.split("=");
                    props.put(p[0], p[1]);
                }
            }
            Integer id = palIdx.get(spec);
            if (id == null) {
                id = palNames.size();
                palIdx.put(spec, id);
                palNames.add(block);
                palProps.add(props);
            }
            voxels.add(new int[] { xyz[0], xyz[1], xyz[2], id });
        };

        for (int x = minX; x <= maxX; x++) {
            for (int z = 0; z <= lenZ; z++) {
                put.accept(new int[] { x, 0, z }, "minecraft:nether_bricks");
                put.accept(new int[] { x, 1, z }, "minecraft:polished_blackstone");
                put.accept(new int[] { x, maxY, z }, "minecraft:nether_bricks");
                if (x == minX || x == maxX || z == 0 || z == lenZ) {
                    for (int y = 2; y < maxY; y++) {
                        put.accept(new int[] { x, y, z }, "minecraft:blackstone");
                    }
                }
            }
        }
        // Decorative directional block inside room
        put.accept(new int[] { 2, 2, 2 }, "minecraft:polished_blackstone_brick_stairs[facing=north,half=bottom]");
        put.accept(new int[] { 3, 2, 2 }, "minecraft:chest[facing=north]");

        if (isStart) {
            // Start: lapis at (8,0,4), redstone at (0,0,4)
            put.accept(new int[] { 8, 0, 4 }, "minecraft:lapis_block");
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 2; y <= 6; y++) put.accept(new int[] { 8, y, 4 + dz }, "minecraft:air");
            }
            put.accept(new int[] { 0, 0, 4 }, "minecraft:redstone_block");
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 2; y <= 6; y++) put.accept(new int[] { 0, y, 4 + dz }, "minecraft:air");
            }
        } else {
            if (hasLapis) {
                put.accept(new int[] { 4, 0, 0 }, "minecraft:lapis_block");
                for (int dx = -1; dx <= 1; dx++) {
                    for (int y = 2; y <= 6; y++) put.accept(new int[] { 4 + dx, y, 0 }, "minecraft:air");
                }
            }
            if (hasForwardRed) {
                put.accept(new int[] { 4, 0, lenZ }, "minecraft:redstone_block");
                for (int dx = -1; dx <= 1; dx++) {
                    for (int y = 2; y <= 6; y++) put.accept(new int[] { 4 + dx, y, lenZ }, "minecraft:air");
                }
            }
            if (hasSideRed) {
                put.accept(new int[] { maxX, 0, lenZ / 2 }, "minecraft:redstone_block");
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = 2; y <= 6; y++) put.accept(new int[] { maxX, y, lenZ / 2 + dz }, "minecraft:air");
                }
            }
        }

        // Write Axiom header NBT
        ByteArrayOutputStream hdrBaos = new ByteArrayOutputStream();
        try (DataOutputStream ho = new DataOutputStream(hdrBaos)) {
            ho.writeByte(10); ho.writeUTF("");
            ho.writeByte(3); ho.writeUTF("Version"); ho.writeInt(2);
            ho.writeByte(8); ho.writeUTF("Name"); ho.writeUTF(name);
            ho.writeByte(9); ho.writeUTF("Tags"); ho.writeByte(8); ho.writeInt(tags.size());
            for (String tg : tags) ho.writeUTF(tg);
            ho.writeByte(0);
        }
        byte[] hdrBytes = hdrBaos.toByteArray();

        // Group voxels into 16x16x16 sections
        Map<String, int[]> secBlocks = new LinkedHashMap<>();
        for (int[] v : voxels) {
            int sx = Math.floorDiv(v[0], 16), sy = Math.floorDiv(v[1], 16), sz = Math.floorDiv(v[2], 16);
            String sk = sx + "," + sy + "," + sz;
            int[] arr = secBlocks.computeIfAbsent(sk, k -> new int[4096]);
            int lx = Math.floorMod(v[0], 16), ly = Math.floorMod(v[1], 16), lz = Math.floorMod(v[2], 16);
            arr[lx + (lz << 4) + (ly << 8)] = v[3];
        }

        ByteArrayOutputStream gzBaos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(gzBaos);
             DataOutputStream bo = new DataOutputStream(gz)) {
            bo.writeByte(10); bo.writeUTF("");
            bo.writeByte(9); bo.writeUTF("BlockRegion"); bo.writeByte(10); bo.writeInt(secBlocks.size());
            for (Map.Entry<String, int[]> se : secBlocks.entrySet()) {
                String[] c = se.getKey().split(",");
                bo.writeByte(3); bo.writeUTF("X"); bo.writeInt(Integer.parseInt(c[0]));
                bo.writeByte(3); bo.writeUTF("Y"); bo.writeInt(Integer.parseInt(c[1]));
                bo.writeByte(3); bo.writeUTF("Z"); bo.writeInt(Integer.parseInt(c[2]));
                bo.writeByte(10); bo.writeUTF("BlockStates");
                bo.writeByte(9); bo.writeUTF("palette"); bo.writeByte(10); bo.writeInt(palNames.size());
                for (int p = 0; p < palNames.size(); p++) {
                    bo.writeByte(8); bo.writeUTF("Name"); bo.writeUTF(palNames.get(p));
                    Map<String, String> pm = palProps.get(p);
                    if (!pm.isEmpty()) {
                        bo.writeByte(10); bo.writeUTF("Properties");
                        for (Map.Entry<String, String> pe : pm.entrySet()) {
                            bo.writeByte(8); bo.writeUTF(pe.getKey()); bo.writeUTF(pe.getValue());
                        }
                        bo.writeByte(0);
                    }
                    bo.writeByte(0);
                }
                int bpe = Math.max(4, Integer.SIZE - Integer.numberOfLeadingZeros(palNames.size() - 1));
                int perLong = 64 / bpe;
                int nLongs = (4096 + perLong - 1) / perLong;
                bo.writeByte(12); bo.writeUTF("data"); bo.writeInt(nLongs);
                int[] arr = se.getValue();
                for (int li = 0; li < nLongs; li++) {
                    long word = 0L;
                    for (int k = 0; k < perLong; k++) {
                        int idx = li * perLong + k;
                        if (idx >= 4096) break;
                        word |= ((long) arr[idx]) << (k * bpe);
                    }
                    bo.writeLong(word);
                }
                bo.writeByte(0); // end BlockStates
                bo.writeByte(0); // end BlockRegion entry
            }
            // BlockEntities
            bo.writeByte(9); bo.writeUTF("BlockEntities"); bo.writeByte(10); bo.writeInt(1);
            bo.writeByte(8); bo.writeUTF("id"); bo.writeUTF("minecraft:chest");
            bo.writeByte(3); bo.writeUTF("x"); bo.writeInt(3);
            bo.writeByte(3); bo.writeUTF("y"); bo.writeInt(2);
            bo.writeByte(3); bo.writeUTF("z"); bo.writeInt(2);
            bo.writeByte(0);

            // Entities
            bo.writeByte(9); bo.writeUTF("Entities"); bo.writeByte(10); bo.writeInt(hasEntity ? 1 : 0);
            if (hasEntity) {
                bo.writeByte(8); bo.writeUTF("id"); bo.writeUTF("settledlands:guide_trigger");
                bo.writeByte(9); bo.writeUTF("Pos"); bo.writeByte(6); bo.writeInt(3);
                bo.writeDouble(4.5); bo.writeDouble(2.0); bo.writeDouble(4.5);
                bo.writeByte(9); bo.writeUTF("Rotation"); bo.writeByte(5); bo.writeInt(2);
                bo.writeFloat(90.0f); bo.writeFloat(0.0f);
                bo.writeByte(0);
            }
            bo.writeByte(0);
        }
        byte[] gzBytes = gzBaos.toByteArray();

        ByteArrayOutputStream full = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(full)) {
            out.writeInt(0x0AE5BB36);
            out.writeInt(hdrBytes.length);
            out.write(hdrBytes);
            out.writeInt(0); // empty png
            out.writeInt(gzBytes.length);
            out.write(gzBytes);
        }
        Files.write(path, full.toByteArray());
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }

    // ---- lightweight mocks -------------------------------------------------

    private static final class MockProperty extends Property<String> {
        private final String propName;
        MockProperty(String propName) { this.propName = propName; }
        @Override public String getName() { return propName; }
        @Override public java.util.Optional<String> getValue(String value) {
            return java.util.Optional.ofNullable(value);
        }
    }

    private static final class MockStateDef extends StateDefinition<Block, BlockState> {
        private final Map<String, Property<?>> props = new HashMap<>();
        @Override public Property<?> getProperty(String name) {
            return props.computeIfAbsent(name, MockProperty::new);
        }
    }

    private static final class MockBlockState extends BlockState {
        final String blockId;
        final Block owner;
        final Map<String, String> appliedProps = new HashMap<>();

        MockBlockState(String blockId, Block owner) {
            this.blockId = blockId;
            this.owner = owner;
        }

        @Override public Block getBlock() { return owner; }
        @Override public boolean isAir() { return "minecraft:air".equals(blockId); }

        @Override
        @SuppressWarnings("rawtypes")
        public Object setValue(Property property, Comparable value) {
            MockBlockState copy = new MockBlockState(blockId, owner);
            copy.appliedProps.putAll(this.appliedProps);
            copy.appliedProps.put(property.getName(), String.valueOf(value));
            return copy;
        }
    }

    private static final class MockBlock extends Block {
        final String id;
        final MockStateDef def = new MockStateDef();
        MockBlock(String id) { this.id = id; }
        @Override public BlockState defaultBlockState() { return new MockBlockState(id, this); }
        @Override public StateDefinition<Block, BlockState> getStateDefinition() { return def; }
    }

    private static final class MockStorage extends DimensionDataStorage {
        private final Map<String, SavedData> store = new HashMap<>();
        @Override @SuppressWarnings("unchecked")
        public <T extends SavedData> T computeIfAbsent(SavedData.Factory<T> factory, String name) {
            return (T) store.computeIfAbsent(name, k -> new DungeonData425());
        }
    }

    private static final class MockLevel extends ServerLevel {
        final ResourceKey<Level> dim;
        final MinecraftServer srv;
        final DimensionDataStorage storage = new MockStorage();
        final Map<String, BlockState> placedStates = new HashMap<>();
        final List<ServerPlayer> playersList = new ArrayList<>();

        MockLevel(ResourceKey<Level> dim, MinecraftServer srv) {
            this.dim = dim;
            this.srv = srv;
        }

        String placedBlockId(int x, int y, int z) {
            BlockState bs = placedStates.get(x + "," + y + "," + z);
            return bs instanceof MockBlockState mbs ? mbs.blockId : null;
        }

        @Override public ResourceKey<Level> dimension() { return dim; }
        @Override public MinecraftServer getServer() { return srv; }
        @Override public long getSeed() { return 4242L; }
        @Override public long getGameTime() { return 100L; }
        @Override public DimensionDataStorage getDataStorage() { return storage; }
        @Override public List<ServerPlayer> players() { return playersList; }
        @Override public BlockPos getSharedSpawnPos() { return new BlockPos(0, 64, 0); }

        @Override public BlockState getBlockState(BlockPos pos) {
            return placedStates.get(pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }

        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) {
            placedStates.put(pos.getX() + "," + pos.getY() + "," + pos.getZ(), state);
            return true;
        }
    }

    private static final class MockServer extends MinecraftServer {
        final Map<String, ServerLevel> levels = new HashMap<>();
        ServerLevel overworld;
        @Override public ServerLevel overworld() { return overworld; }
        @Override public ServerLevel getLevel(ResourceKey<Level> key) {
            return key == null ? null : levels.get(key.toString());
        }
    }

    private static final class MockPlayer extends ServerPlayer {
        final MinecraftServer srv;
        final UUID id;
        ServerLevel curLevel;
        double px, py, pz;
        float yaw = 0.0F, pitch = 0.0F;

        MockPlayer(MinecraftServer srv, ServerLevel level, double x, double y, double z) {
            this(srv, level, x, y, z, UUID.randomUUID());
        }

        MockPlayer(MinecraftServer srv, ServerLevel level, double x, double y, double z, UUID id) {
            this.srv = srv;
            this.curLevel = level;
            this.px = x; this.py = y; this.pz = z;
            this.id = id;
        }

        @Override public MinecraftServer getServer() { return srv; }
        @Override public UUID getUUID() { return id; }
        @Override public ServerLevel serverLevel() { return curLevel; }
        @Override public double getX() { return px; }
        @Override public double getY() { return py; }
        @Override public double getZ() { return pz; }
        @Override public float getYRot() { return yaw; }
        @Override public float getXRot() { return pitch; }
        @Override public void clearFire() {}
        @Override public void setDeltaMovement(Vec3 m) {}
        @Override public void displayClientMessage(Component msg, boolean actionBar) {}
        @Override public boolean teleportTo(ServerLevel level, double tx, double ty, double tz,
                Set<?> relatives, float tyaw, float tpitch) {
            this.curLevel = level;
            this.px = tx; this.py = ty; this.pz = tz;
            this.yaw = tyaw; this.pitch = tpitch;
            return true;
        }
    }

    private static final class MockServerTickPost extends ServerTickEvent.Post {
        private final MinecraftServer srv;
        MockServerTickPost(MinecraftServer srv) { this.srv = srv; }
        @Override public MinecraftServer getServer() { return srv; }
    }

    private static final class MockRegisterCommandsEvent extends RegisterCommandsEvent {
        private final CommandDispatcher<CommandSourceStack> disp;
        MockRegisterCommandsEvent(CommandDispatcher<CommandSourceStack> d) { this.disp = d; }
        @Override public CommandDispatcher<CommandSourceStack> getDispatcher() { return disp; }
    }

    private static final class MockCommandSource extends CommandSourceStack {
        private final MinecraftServer srv;
        private final List<String> msgs;
        MockCommandSource(MinecraftServer srv, List<String> msgs) { this.srv = srv; this.msgs = msgs; }
        @Override public boolean hasPermission(int level) { return true; }
        @Override public MinecraftServer getServer() { return srv; }
        @Override public void sendSuccess(java.util.function.Supplier<Component> msg, boolean allowLogging) {
            msgs.add(msg.get().getString());
        }
    }
}
