import com.mojang.brigadier.CommandDispatcher;
import dev.settledlands.dungeon.AxiomBlueprint427;
import dev.settledlands.dungeon.CreateCompat431;
import dev.settledlands.dungeon.CreateSchematic431;
import dev.settledlands.dungeon.DungeonBlueprints427;
import dev.settledlands.dungeon.DungeonCommand425;
import dev.settledlands.dungeon.DungeonConfig427;
import dev.settledlands.dungeon.DungeonData425;
import dev.settledlands.dungeon.DungeonEvents425;
import dev.settledlands.dungeon.DungeonGenerator427;
import dev.settledlands.dungeon.DungeonManager425;
import dev.settledlands.dungeon.DungeonNbt431;
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
import net.minecraft.world.level.block.entity.BlockEntity;
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
        "valhelsia_structures:dungeon_door",
        "minecraft:oak_stairs",
        "minecraft:deepslate_bricks",
        "minecraft:nether_wart_block",
        "minecraft:deepslate_tiles",
        "minecraft:cracked_deepslate_bricks",
        "minecraft:crimson_hyphae",
        "minecraft:red_nether_bricks",
        "minecraft:cobbled_deepslate",
        "minecraft:netherrack",
        "minecraft:polished_deepslate",
        "minecraft:stripped_crimson_hyphae",
        "minecraft:tuff_bricks",
        "minecraft:deepslate_brick_stairs",
        "minecraft:cracked_deepslate_tiles",
        "create:belt",
        "create:encased_cogwheel",
        "create:chain_drive",
        "create:radial_chassis",
        "create:creative_motor",
        "create:fluid_tank",
        "create:item_vault",
        "create:redstone_link",
        "create:mechanical_bearing",
        "create:windmill_bearing",
        "create:mechanical_arm",
        "create:chain_conveyor",
        "create:display_link",
        "create:mechanical_crafter",
        "create:copycat_step",
        "create:powered_shaft",
    };

    public static void main(String[] args) throws Exception {
        registerBlocks();
        testRotationMathAndProperties();
        testConfigCreationAutoDiscoveryAndFilters();
        testStreamingPlacementSaveLoadAndProximity();
        testMultiBlockDoorSafetyBuild430();
        testCreateNbtAndSuperGlueBuild431();
        testBuild433ContraptionAndNbtSpawning();
        testRealArchiveIfPresent();
        testBiomeProgressionBuild429();
        testZeroSealDeadEndInvariantBuild432();
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
            check(cfg.minPieces == 85 && cfg.maxPieces == 110 && cfg.maxRadius == 240,
                    "Build 430 default minPieces=85, maxPieces=110, maxRadius=240");

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
            check(inst.seals.size() == 1,
                    "flat plan has ZERO corridor seal walls (only start entrance), got " + inst.seals.size());
            boolean hasDeadEnd = false;
            for (var pp : inst.pieces) {
                if (pp.pieceKey.contains("Тупик")) { hasDeadEnd = true; break; }
            }
            check(hasDeadEnd, "flat plan terminates branches in dead-end rooms");
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
                    85, 110, 240, -52, 240, true, "minecraft:nether_bricks", 8, 8192, true);
            DungeonBlueprints427.Library lib = DungeonBlueprints427.load(cfg);
            check(lib.totalBpFiles == 232 && lib.failedParse == 0, "all 232 real .bp parsed");
            check("auto->biomes(к->а+п)".equals(lib.appliedFilter) && lib.biomeProgression
                    && lib.pieces.size() == 142 && lib.cryptCount == 51
                    && lib.infernalCount == 48 && lib.fleshCount == 43,
                    "biome pool к+а+п selected (142 pieces: 51 crypt, 48 infernal, 43 flesh)");

            // Check 5 distinct seeds for large crypt core, far deep biomes, ZERO overlaps, and ZERO corridor seals
            for (int s = 0; s < 5; s++) {
                DungeonGenerator427.Plan p = DungeonGenerator427.generate(lib, 0, 0, 0, 1000L + s * 7919L);
                check(p.pieces.size() >= 85 && p.pieces.size() <= 110,
                        "real biome plan #" + s + " size=" + p.pieces.size());
                check(p.seals.size() == 1,
                        "real biome plan #" + s + " has ZERO corridor seal walls (seals=" + p.seals.size() + ")");
                check(p.cryptCount >= 32 && p.infernalCount >= 12 && p.fleshCount >= 12,
                        "real biome plan #" + s + " large crypt + deep biomes [к=" + p.cryptCount
                                + ", а=" + p.infernalCount + ", п=" + p.fleshCount + "]");
                double minDeepDist = Double.MAX_VALUE;
                for (int i = 0; i < p.pieces.size(); i++) {
                    var a = p.pieces.get(i);
                    for (int j = i + 1; j < p.pieces.size(); j++) {
                        var b = p.pieces.get(j);
                        check(!DungeonGenerator427.overlaps(
                                new int[] { a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ },
                                new int[] { b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ }),
                                "no overlap in real biome plan #" + s + ": " + a.pieceKey + " vs " + b.pieceKey);
                    }
                }
                for (int i = 1; i < p.pieces.size(); i++) {
                    var pp = p.pieces.get(i);
                    if (pp.depth <= 7) {
                        check(DungeonBlueprints427.THEME_CRYPT.equals(pp.biome),
                                "real shallow room (depth=" + pp.depth + ") must be crypt, got " + pp.pieceKey);
                    }
                    if (!DungeonBlueprints427.THEME_CRYPT.equals(pp.biome)) {
                        double cx = (pp.minX + pp.maxX) * 0.5;
                        double cz = (pp.minZ + pp.maxZ) * 0.5;
                        double d = Math.hypot(cx, cz);
                        if (d < minDeepDist) minDeepDist = d;
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
                check(minDeepDist >= 95.0D,
                        "real biome plan #" + s + " deep biomes far from spawn (minDeepDist=" + minDeepDist + ")");
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
            check(inst.pieces.size() >= 85 && inst.pieces.size() <= 110,
                    "real biome plan has " + inst.pieces.size() + " rooms");
            check(inst.seals.size() == 1,
                    "real streamed instance has ZERO corridor seal walls (seals=" + inst.seals.size() + ")");

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

    private static void testMultiBlockDoorSafetyBuild430() throws Exception {
        Path tmp = Files.createTempDirectory("hd430_doors");
        try {
            Path bpPath = tmp.resolve("старт_с_дверью.bp");
            writeOutOfOrderDoorBp(bpPath);
            AxiomBlueprint427.PieceTemplate piece = AxiomBlueprint427.loadFile(bpPath, "старт_с_дверью");
            int[] raw = piece.content.blocks;
            var pal = piece.content.palette;
            int totalBlocks = piece.content.blockCount();

            // Verify 3-phase ordering in blocksArr:
            // 1) All structural blocks (stone/chain) come before wall/hanging decor (soul_lantern),
            // 2) All decor comes before any valhelsia_structures:dungeon_door,
            // 3) Every door column has lower immediately before upper (bottom-up wy).
            int maxPhaseSeen = -1;
            int doorCount = 0;
            for (int bi = 0; bi < totalBlocks; bi++) {
                int off = bi * 4;
                int bx = raw[off], by = raw[off + 1], bz = raw[off + 2], pi = raw[off + 3];
                String id = pal.get(pi).id;
                int phase = id.contains("dungeon_door") ? 2 : (id.contains("lantern") ? 1 : 0);
                check(phase >= maxPhaseSeen, "block order phase never goes backwards (idx=" + bi + ", id=" + id + ")");
                maxPhaseSeen = phase;
                if (phase == 2) {
                    String half = pal.get(pi).properties.get("half");
                    if ("lower".equals(half)) {
                        check(bi + 1 < totalBlocks, "door upper immediately follows lower");
                        int nOff = (bi + 1) * 4;
                        int nx = raw[nOff], ny = raw[nOff + 1], nz = raw[nOff + 2], npi = raw[nOff + 3];
                        String nextId = pal.get(npi).id;
                        String nextHalf = pal.get(npi).properties.get("half");
                        check("valhelsia_structures:dungeon_door".equals(nextId)
                                && "upper".equals(nextHalf)
                                && nx == bx && nz == bz && ny == by + 1,
                                "upper half (" + nx + "," + ny + "," + nz
                                        + ") grouped immediately after lower (" + bx + "," + by + "," + bz + ")");
                    }
                    doorCount++;
                }
            }
            check(doorCount == 6, "3 double-height dungeon_door columns (6 blocks) parsed, got " + doorCount);

            // Place in MockLevel with strict vanilla DoorBlock & updateShape physics simulation:
            // - Any setBlock without UPDATE_KNOWN_SHAPE|UPDATE_SUPPRESS_DROPS (16|32) triggers neighbor drops
            // - Placing door lower without solid below drops it
            // - Placing door upper without door lower below drops it
            // - Placing structural block adjacent to an already-placed door simulates shape update drop
            for (int rot = 0; rot < 4; rot++) {
                MockServer srv = new MockServer();
                MockLevel hd = new MockLevel(DungeonManager425.HELL_DUNGEON_LEVEL, srv);
                hd.strictDoorPhysics = true;
                hd.blocksPerTickTestMode = true;

                // Stream with tiny slice (7 blocks/tick) to verify tick boundaries NEVER split a door!
                int cursor = 0;
                int guard = 0;
                while (cursor < totalBlocks && guard++ < 200) {
                    int nextCursor = DungeonTemplate425.placeSlice(
                            hd, piece.content, 100, 64, 100, rot, "minecraft:nether_bricks", cursor, 7, null);
                    check(nextCursor > cursor, "placeSlice advances forward");
                    // After EVERY SINGLE TICK, every placed lower half MUST already have its upper half!
                    for (var entry : hd.placedStates.entrySet()) {
                        if (entry.getValue() instanceof MockBlockState mbs
                                && "valhelsia_structures:dungeon_door".equals(mbs.blockId)
                                && "lower".equals(mbs.appliedProps.get("half"))) {
                            String[] xyz = entry.getKey().split(",");
                            int ux = Integer.parseInt(xyz[0]);
                            int uy = Integer.parseInt(xyz[1]) + 1;
                            int uz = Integer.parseInt(xyz[2]);
                            BlockState up = hd.placedStates.get(ux + "," + uy + "," + uz);
                            check(up instanceof MockBlockState umbs
                                    && "valhelsia_structures:dungeon_door".equals(umbs.blockId)
                                    && "upper".equals(umbs.appliedProps.get("half")),
                                    "tick boundary never splits lower/upper door at rot=" + rot);
                        }
                    }
                    cursor = nextCursor;
                }
                check(cursor == totalBlocks, "all blocks streamed cleanly at rot=" + rot);
                check(hd.droppedDoors == 0, "zero dungeon_door drops at rot=" + rot);
                check(hd.requiredFlagsSeen, "PLACE_FLAGS includes UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS (50)");

                // Count final surviving door blocks = 6 (all 3 two-block doors intact!)
                int intactDoors = 0;
                for (BlockState bs : hd.placedStates.values()) {
                    if (bs instanceof MockBlockState mbs
                            && "valhelsia_structures:dungeon_door".equals(mbs.blockId)) {
                        intactDoors++;
                    }
                }
                check(intactDoors == 6, "all 6 dungeon_door halves intact at rot=" + rot + ", got " + intactDoors);
            }
        } finally {
            deleteTree(tmp);
        }
    }

    /**
     * Writes an Axiom .bp where section sy=0 appears BEFORE sy=-1 (worst-case vanilla section order),
     * containing:
     *  - a dungeon_door straddling sy=-1 (lower at y=-1) and sy=0 (upper at y=0),
     *  - a double dungeon_door inside sy=0 (lower at y=0, upper at y=1) whose floor rests in sy=-1 (y=-1),
     *  - a hanging soul_lantern under a chain.
     */
    private static void writeOutOfOrderDoorBp(Path path) throws IOException {
        ByteArrayOutputStream hBaos = new ByteArrayOutputStream();
        try (DataOutputStream ho = new DataOutputStream(hBaos)) {
            ho.writeByte(10); ho.writeUTF("");
            ho.writeByte(8); ho.writeUTF("Name"); ho.writeUTF("старт_с_дверью");
            ho.writeByte(8); ho.writeUTF("Author"); ho.writeUTF("probe");
            ho.writeByte(9); ho.writeUTF("Tags"); ho.writeByte(8); ho.writeInt(0);
            ho.writeByte(3); ho.writeUTF("BlockCount"); ho.writeInt(30);
            ho.writeByte(1); ho.writeUTF("Locked"); ho.writeByte(0);
            ho.writeByte(0);
        }
        byte[] hdrBytes = hBaos.toByteArray();

        // Write 2 chunk sections in WORST ORDER: sy=0 first, sy=-1 second!
        class Sec {
            final int sy;
            final List<String> pal = new ArrayList<>();
            final int[] idx = new int[4096];
            Sec(int sy) {
                this.sy = sy;
                pal.add("minecraft:air");
            }
            void set(int lx, int ly, int lz, String state) {
                int pi = pal.indexOf(state);
                if (pi < 0) { pi = pal.size(); pal.add(state); }
                idx[(ly << 8) | (lz << 4) | lx] = pi;
            }
        }
        Sec sec0 = new Sec(0);
        Sec secM1 = new Sec(-1);

        // Door 1 & 2 (double door on floor at y=-1, doors at y=0..1)
        secM1.set(4, 15, 4, "minecraft:blackstone");
        secM1.set(5, 15, 4, "minecraft:blackstone");
        sec0.set(4, 0, 4, "valhelsia_structures:dungeon_door[facing=north,half=lower,hinge=left,open=false,powered=false]");
        sec0.set(4, 1, 4, "valhelsia_structures:dungeon_door[facing=north,half=upper,hinge=left,open=false,powered=false]");
        sec0.set(5, 0, 4, "valhelsia_structures:dungeon_door[facing=north,half=lower,hinge=right,open=false,powered=false]");
        sec0.set(5, 1, 4, "valhelsia_structures:dungeon_door[facing=north,half=upper,hinge=right,open=false,powered=false]");
        // Jambs around double door at y=0..2
        secM1.set(3, 15, 4, "minecraft:blackstone");
        secM1.set(6, 15, 4, "minecraft:blackstone");
        sec0.set(3, 0, 4, "minecraft:blackstone");
        sec0.set(3, 1, 4, "minecraft:blackstone");
        sec0.set(3, 2, 4, "minecraft:blackstone");
        sec0.set(4, 2, 4, "minecraft:blackstone");
        sec0.set(5, 2, 4, "minecraft:blackstone");
        sec0.set(6, 0, 4, "minecraft:blackstone");
        sec0.set(6, 1, 4, "minecraft:blackstone");
        sec0.set(6, 2, 4, "minecraft:blackstone");

        // Door 3 straddling sy=-1 (lower at y=-1) and sy=0 (upper at y=0), floor at y=-2
        secM1.set(8, 14, 4, "minecraft:blackstone");
        secM1.set(8, 15, 4, "valhelsia_structures:dungeon_door[facing=east,half=lower,hinge=left,open=false,powered=false]");
        sec0.set(8, 0, 4, "valhelsia_structures:dungeon_door[facing=east,half=upper,hinge=left,open=false,powered=false]");
        sec0.set(8, 1, 4, "minecraft:blackstone");

        // Hanging lantern under chain in sy=0
        sec0.set(2, 2, 2, "minecraft:chain[axis=y]");
        sec0.set(2, 1, 2, "minecraft:soul_lantern[hanging=true]");

        // Entrance lapis marker so AxiomBlueprint427 treats it as a valid piece
        secM1.set(0, 14, 0, "minecraft:lapis_block");
        secM1.set(0, 15, 0, "minecraft:blackstone");

        ByteArrayOutputStream gzBaos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(gzBaos); DataOutputStream bo = new DataOutputStream(gz)) {
            bo.writeByte(10); bo.writeUTF("");
            bo.writeByte(9); bo.writeUTF("BlockRegion"); bo.writeByte(10); bo.writeInt(2);
            for (Sec sec : new Sec[] { sec0, secM1 }) { // deliberately WRONG Y order!
                bo.writeByte(3); bo.writeUTF("X"); bo.writeInt(0);
                bo.writeByte(3); bo.writeUTF("Y"); bo.writeInt(sec.sy);
                bo.writeByte(3); bo.writeUTF("Z"); bo.writeInt(0);
                bo.writeByte(10); bo.writeUTF("BlockStates");
                bo.writeByte(9); bo.writeUTF("palette"); bo.writeByte(10); bo.writeInt(sec.pal.size());
                for (String ps : sec.pal) {
                    int br = ps.indexOf('[');
                    String bid = br < 0 ? ps : ps.substring(0, br);
                    bo.writeByte(8); bo.writeUTF("Name"); bo.writeUTF(bid);
                    if (br > 0) {
                        bo.writeByte(10); bo.writeUTF("Properties");
                        for (String kv : ps.substring(br + 1, ps.length() - 1).split(",")) {
                            String[] p = kv.split("=");
                            bo.writeByte(8); bo.writeUTF(p[0]); bo.writeUTF(p[1]);
                        }
                        bo.writeByte(0);
                    }
                    bo.writeByte(0);
                }
                int bpe = Math.max(4, 32 - Integer.numberOfLeadingZeros(sec.pal.size() - 1));
                int perLong = 64 / bpe;
                int longsLen = (4096 + perLong - 1) / perLong;
                long[] packed = new long[longsLen];
                for (int i = 0; i < 4096; i++) {
                    int li = i / perLong;
                    int bit = (i % perLong) * bpe;
                    packed[li] |= ((long) sec.idx[i]) << bit;
                }
                bo.writeByte(12); bo.writeUTF("data"); bo.writeInt(longsLen);
                for (long v : packed) bo.writeLong(v);
                bo.writeByte(0);
                bo.writeByte(0);
            }
            bo.writeByte(9); bo.writeUTF("BlockEntities"); bo.writeByte(10); bo.writeInt(0);
            bo.writeByte(9); bo.writeUTF("Entities"); bo.writeByte(10); bo.writeInt(0);
            bo.writeByte(0);
        }
        byte[] gzBytes = gzBaos.toByteArray();
        ByteArrayOutputStream full = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(full)) {
            out.writeInt(0x0AE5BB36);
            out.writeInt(hdrBytes.length);
            out.write(hdrBytes);
            out.writeInt(0);
            out.writeInt(gzBytes.length);
            out.write(gzBytes);
        }
        Files.write(path, full.toByteArray());
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
                check(plan.seals.size() == 1,
                        "synthetic biome plan #" + s + " has ZERO corridor seal walls (seals=" + plan.seals.size() + ")");
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

    private static void testZeroSealDeadEndInvariantBuild432() throws Exception {
        // 1. Tight radius in flat mode (branches rapidly hit maxRadius=36 boundary & siblings)
        Path tmpTight = Files.createTempDirectory("hd432_tight");
        try {
            Path bpDir = tmpTight.resolve(DungeonConfig427.DEFAULT_BLUEPRINTS_REL);
            Files.createDirectories(bpDir);
            writeRoomBp(bpDir.resolve("старт.bp"), "старт", List.of(), 0, 8, true, true, false, false, false);
            writeRoomBp(bpDir.resolve("Коридор.bp"), "Коридор", List.of(), 0, 8, false, true, true, false, false);
            writeRoomBp(bpDir.resolve("Развилка.bp"), "Развилка", List.of(), 0, 8, false, true, true, true, false);
            writeRoomBp(bpDir.resolve("Тупик.bp"), "Тупик", List.of(), 0, 6, false, true, false, false, false);

            DungeonConfig427 cfg = new DungeonConfig427(
                    tmpTight, bpDir.toString(), "auto", "старт",
                    12, 22, 36, -52, 240, true, "minecraft:nether_bricks", 8, 4096, true);
            DungeonBlueprints427.Library lib = DungeonBlueprints427.load(cfg);
            for (int s = 0; s < 8; s++) {
                DungeonGenerator427.Plan p = DungeonGenerator427.generate(lib, 0, 0, 0, 91000L + s * 4099L);
                check(p.seals.size() == 1,
                        "tight maxRadius=36 seed #" + s + " has ZERO non-start seals (seals=" + p.seals.size() + ")");
                check(p.pieces.size() >= 6, "tight plan #" + s + " built " + p.pieces.size() + " rooms");
                for (var pp : p.pieces) {
                    check(pp.minX >= -36 && pp.maxX <= 36 && pp.minZ >= -36 && pp.maxZ <= 36,
                            "tight plan piece inside [-36..36]: " + pp.pieceKey);
                }
            }
        } finally {
            deleteTree(tmpTight);
        }

        // 2. Tight 3-way crossroad (9x8x9) whose wide 15x8x15 dead-ends conflict unless bridged by a corridor
        Path tmpCross = Files.createTempDirectory("hd432_cross");
        try {
            Path bpDir = tmpCross.resolve(DungeonConfig427.DEFAULT_BLUEPRINTS_REL);
            Files.createDirectories(bpDir);
            writeRoomBp(bpDir.resolve("старт.bp"), "старт", List.of(), 0, 8, true, true, false, false, false);
            writeCustomBoxRoomBp(bpDir.resolve("Коридор.bp"), "Коридор", 8, 8, true, true, false, false);
            writeCustomBoxRoomBp(bpDir.resolve("Перекрёсток 1.bp"), "Перекрёсток 1", 8, 8, true, true, true, true);
            writeCustomBoxRoomBp(bpDir.resolve("Тупик широкий.bp"), "Тупик широкий", 14, 14, true, false, false, false);

            DungeonConfig427 cfg = new DungeonConfig427(
                    tmpCross, bpDir.toString(), "auto", "старт",
                    18, 28, 180, -52, 240, true, "minecraft:nether_bricks", 8, 4096, true);
            DungeonBlueprints427.Library lib = DungeonBlueprints427.load(cfg);
            boolean sawCrossroad = false;
            for (int s = 0; s < 6; s++) {
                DungeonGenerator427.Plan p = DungeonGenerator427.generate(lib, 0, 0, 0, 50500L + s * 7919L);
                check(p.seals.size() == 1,
                        "tight crossroad plan #" + s + " has ZERO non-start seals (seals=" + p.seals.size() + ")");
                check(p.pieces.size() >= 18 && p.pieces.size() <= 28,
                        "tight crossroad plan #" + s + " size=" + p.pieces.size());
                for (int i = 0; i < p.pieces.size(); i++) {
                    var a = p.pieces.get(i);
                    if ("Перекрёсток 1".equals(a.pieceKey)) sawCrossroad = true;
                    for (int j = i + 1; j < p.pieces.size(); j++) {
                        var b = p.pieces.get(j);
                        check(!DungeonGenerator427.overlaps(
                                new int[] { a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ },
                                new int[] { b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ }),
                                "no overlap with bridged crossroad");
                    }
                }
            }
            check(sawCrossroad, "tight 3-way Перекрёсток 1 placed via automatic bridge corridor");
        } finally {
            deleteTree(tmpCross);
        }

        // 3. Fallback when blueprint library has ZERO dead-ends at all
        Path tmpNoCap = Files.createTempDirectory("hd432_nocap");
        try {
            Path bpDir = tmpNoCap.resolve(DungeonConfig427.DEFAULT_BLUEPRINTS_REL);
            Files.createDirectories(bpDir);
            writeRoomBp(bpDir.resolve("старт.bp"), "старт", List.of(), 0, 8, true, true, false, false, false);
            writeRoomBp(bpDir.resolve("Коридор.bp"), "Коридор", List.of(), 0, 8, false, true, true, false, false);
            DungeonConfig427 cfg = new DungeonConfig427(
                    tmpNoCap, bpDir.toString(), "auto", "старт",
                    5, 8, 120, -52, 240, true, "minecraft:nether_bricks", 8, 4096, true);
            DungeonBlueprints427.Library lib = DungeonBlueprints427.load(cfg);
            DungeonGenerator427.Plan p = DungeonGenerator427.generate(lib, 0, 0, 0, 12345L);
            check(p.pieces.size() == 8, "library without dead-ends still generates full fallback plan");
        } finally {
            deleteTree(tmpNoCap);
        }

        // 4. Real archive stress check (biome [75..95] and flat "all") when /tmp/dungeonz/Данж is present
        Path realDir = Paths.get("/tmp/dungeonz/Данж");
        if (Files.isDirectory(realDir)) {
            Path tmpReal = Files.createTempDirectory("hd432_real");
            try {
                DungeonConfig427 cfgBiome = new DungeonConfig427(
                        tmpReal, realDir.toString(), "auto", "старт",
                        75, 95, 240, -52, 240, true, "minecraft:nether_bricks", 8, 4096, true);
                DungeonBlueprints427.Library libBiome = DungeonBlueprints427.load(cfgBiome);
                for (int s = 0; s < 10; s++) {
                    var p = DungeonGenerator427.generate(libBiome, 0, 120, 0, 300000L + s * 1234567L);
                    check(p.seals.size() == 1, "real [75..95] #" + s + " has ZERO non-start seals");
                    check(p.pieces.size() >= 75 && p.pieces.size() <= 95,
                            "real [75..95] #" + s + " size=" + p.pieces.size());
                }
                DungeonConfig427 cfgFlat = new DungeonConfig427(
                        tmpReal, realDir.toString(), "all", "старт",
                        40, 60, 220, -52, 240, true, "minecraft:nether_bricks", 8, 4096, true);
                DungeonBlueprints427.Library libFlat = DungeonBlueprints427.load(cfgFlat);
                for (int s = 0; s < 10; s++) {
                    var p = DungeonGenerator427.generate(libFlat, 512, 120, 512, 400000L + s * 99991L);
                    check(p.seals.size() == 1, "real flat all #" + s + " has ZERO non-start seals");
                    check(p.pieces.size() >= 40 && p.pieces.size() <= 60,
                            "real flat all #" + s + " size=" + p.pieces.size());
                }
            } finally {
                deleteTree(tmpReal);
            }
        }
    }

    private static void writeCustomBoxRoomBp(Path path, String name, int maxX, int maxZ,
            boolean hasLapis, boolean hasForwardRed, boolean hasEastRed, boolean hasWestRed) throws IOException {
        Map<String, Integer> palIdx = new LinkedHashMap<>();
        List<String> palNames = new ArrayList<>();
        palIdx.put("minecraft:air", 0);
        palNames.add("minecraft:air");
        List<int[]> voxels = new ArrayList<>();
        java.util.function.BiConsumer<int[], String> put = (xyz, id) -> {
            Integer pi = palIdx.computeIfAbsent(id, k -> {
                palNames.add(k);
                return palNames.size() - 1;
            });
            voxels.add(new int[] { xyz[0], xyz[1], xyz[2], pi });
        };
        int maxY = 7;
        for (int x = 0; x <= maxX; x++) {
            for (int z = 0; z <= maxZ; z++) {
                put.accept(new int[] { x, 0, z }, "minecraft:nether_bricks");
                put.accept(new int[] { x, 1, z }, "minecraft:polished_blackstone");
                put.accept(new int[] { x, maxY, z }, "minecraft:nether_bricks");
                if (x == 0 || x == maxX || z == 0 || z == maxZ) {
                    for (int y = 2; y < maxY; y++) put.accept(new int[] { x, y, z }, "minecraft:blackstone");
                }
            }
        }
        int midX = maxX / 2;
        int midZ = maxZ / 2;
        if (hasLapis) {
            put.accept(new int[] { midX, 0, 0 }, "minecraft:lapis_block");
            for (int dx = -1; dx <= 1; dx++)
                for (int y = 2; y <= 6; y++) put.accept(new int[] { midX + dx, y, 0 }, "minecraft:air");
        }
        if (hasForwardRed) {
            put.accept(new int[] { midX, 0, maxZ }, "minecraft:redstone_block");
            for (int dx = -1; dx <= 1; dx++)
                for (int y = 2; y <= 6; y++) put.accept(new int[] { midX + dx, y, maxZ }, "minecraft:air");
        }
        if (hasEastRed) {
            put.accept(new int[] { maxX, 0, midZ }, "minecraft:redstone_block");
            for (int dz = -1; dz <= 1; dz++)
                for (int y = 2; y <= 6; y++) put.accept(new int[] { maxX, y, midZ + dz }, "minecraft:air");
        }
        if (hasWestRed) {
            put.accept(new int[] { 0, 0, midZ }, "minecraft:redstone_block");
            for (int dz = -1; dz <= 1; dz++)
                for (int y = 2; y <= 6; y++) put.accept(new int[] { 0, y, midZ + dz }, "minecraft:air");
        }
        ByteArrayOutputStream hdrBaos = new ByteArrayOutputStream();
        try (DataOutputStream ho = new DataOutputStream(hdrBaos)) {
            ho.writeByte(10); ho.writeUTF("");
            ho.writeByte(3); ho.writeUTF("Version"); ho.writeInt(2);
            ho.writeByte(8); ho.writeUTF("Name"); ho.writeUTF(name);
            ho.writeByte(9); ho.writeUTF("Tags"); ho.writeByte(8); ho.writeInt(0);
            ho.writeByte(0);
        }
        byte[] hdrBytes = hdrBaos.toByteArray();
        Map<String, int[]> secBlocks = new LinkedHashMap<>();
        for (int[] v : voxels) {
            int sx = Math.floorDiv(v[0], 16), sy = Math.floorDiv(v[1], 16), sz = Math.floorDiv(v[2], 16);
            String sk = sx + "," + sy + "," + sz;
            int[] arr = secBlocks.computeIfAbsent(sk, k -> new int[4096]);
            int lx = Math.floorMod(v[0], 16), ly = Math.floorMod(v[1], 16), lz = Math.floorMod(v[2], 16);
            arr[lx + (lz << 4) + (ly << 8)] = v[3];
        }
        ByteArrayOutputStream gzBaos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(gzBaos); DataOutputStream bo = new DataOutputStream(gz)) {
            bo.writeByte(10); bo.writeUTF("");
            bo.writeByte(9); bo.writeUTF("BlockRegion"); bo.writeByte(10); bo.writeInt(secBlocks.size());
            for (Map.Entry<String, int[]> se : secBlocks.entrySet()) {
                String[] c = se.getKey().split(",");
                bo.writeByte(3); bo.writeUTF("X"); bo.writeInt(Integer.parseInt(c[0]));
                bo.writeByte(3); bo.writeUTF("Y"); bo.writeInt(Integer.parseInt(c[1]));
                bo.writeByte(3); bo.writeUTF("Z"); bo.writeInt(Integer.parseInt(c[2]));
                bo.writeByte(10); bo.writeUTF("BlockStates");
                bo.writeByte(9); bo.writeUTF("palette"); bo.writeByte(10); bo.writeInt(palNames.size());
                for (String pn : palNames) {
                    bo.writeByte(8); bo.writeUTF("Name"); bo.writeUTF(pn);
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
                bo.writeByte(0);
                bo.writeByte(0);
            }
            bo.writeByte(9); bo.writeUTF("BlockEntities"); bo.writeByte(10); bo.writeInt(0);
            bo.writeByte(9); bo.writeUTF("Entities"); bo.writeByte(10); bo.writeInt(0);
            bo.writeByte(0);
        }
        byte[] gzBytes = gzBaos.toByteArray();
        ByteArrayOutputStream full = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(full)) {
            out.writeInt(0x0AE5BB36);
            out.writeInt(hdrBytes.length);
            out.write(hdrBytes);
            out.writeInt(0);
            out.writeInt(gzBytes.length);
            out.write(gzBytes);
        }
        Files.write(path, full.toByteArray());
    }

    private static void testCreateNbtAndSuperGlueBuild431() throws Exception {
        // 1. Create blockstate property rotation coverage (no Create jar needed)
        Map<String, String> vBelt = Map.of("facing", "north", "slope", "vertical", "part", "start", "casing", "true");
        Map<String, String> rBelt1 = DungeonTemplate425.rotateProps(vBelt, 1, "create:belt");
        check("east".equals(rBelt1.get("facing")) && "end".equals(rBelt1.get("part")),
                "vertical belt NORTH->EAST swaps part start->end");
        Map<String, String> rBelt3 = DungeonTemplate425.rotateProps(vBelt, 3, "create:belt");
        check("west".equals(rBelt3.get("facing")) && "start".equals(rBelt3.get("part")),
                "vertical belt NORTH->WEST preserves part start");

        Map<String, String> cogX = Map.of("axis", "x", "top_shaft", "true", "bottom_shaft", "false");
        Map<String, String> cogX1 = DungeonTemplate425.rotateProps(cogX, 1, "create:encased_cogwheel");
        check("z".equals(cogX1.get("axis")) && "true".equals(cogX1.get("top_shaft")),
                "encased_cogwheel axis=x CW90 keeps top_shaft=true");
        Map<String, String> cogX3 = DungeonTemplate425.rotateProps(cogX, 3, "create:encased_cogwheel");
        check("z".equals(cogX3.get("axis")) && "false".equals(cogX3.get("top_shaft"))
                && "true".equals(cogX3.get("bottom_shaft")),
                "encased_cogwheel axis=x CCW90 swaps top_shaft<->bottom_shaft");

        Map<String, String> chain = Map.of("axis", "z", "connected_along_first_coordinate", "true", "part", "start");
        Map<String, String> chain1 = DungeonTemplate425.rotateProps(chain, 1, "create:chain_drive");
        check("x".equals(chain1.get("axis")) && "false".equals(chain1.get("connected_along_first_coordinate"))
                && "start".equals(chain1.get("part")),
                "chain_drive Z->X CW90 flips connected_along_first_coordinate & keeps +X->+Z part");
        Map<String, String> chain3 = DungeonTemplate425.rotateProps(chain, 3, "create:chain_drive");
        check("x".equals(chain3.get("axis")) && "false".equals(chain3.get("connected_along_first_coordinate"))
                && "end".equals(chain3.get("part")),
                "chain_drive Z->X CCW90 flips connected_along_first_coordinate & swaps +X->-Z part");
        Map<String, String> chainVert = Map.of("axis", "x", "connected_along_first_coordinate", "true", "part", "start");
        Map<String, String> chainVert1 = DungeonTemplate425.rotateProps(chainVert, 1, "create:chain_drive");
        check("z".equals(chainVert1.get("axis")) && "false".equals(chainVert1.get("connected_along_first_coordinate"))
                && "start".equals(chainVert1.get("part")),
                "vertical chain_drive X->Z CW90 flips connected_along_first_coordinate & preserves vertical part");

        Map<String, String> chassis = Map.of("axis", "y", "glueable_north", "true", "glueable_east", "false");
        Map<String, String> chassis1 = DungeonTemplate425.rotateProps(chassis, 1, "create:radial_chassis");
        check("true".equals(chassis1.get("glueable_east")) && "false".equals(chassis1.get("glueable_south")),
                "chassis glueable_* rotates clockwise");

        Map<String, String> gearshift = Map.of("axis", "y", "axis_along_first", "true");
        check("false".equals(DungeonTemplate425.rotateProps(gearshift, 1).get("axis_along_first")),
                "directional axis_along_first toggles on odd turns");

        Map<String, String> deployer = Map.of("facing", "north", "target", "east");
        Map<String, String> dep1 = DungeonTemplate425.rotateProps(deployer, 1);
        check("east".equals(dep1.get("facing")) && "south".equals(dep1.get("target")),
                "directed directional target rotates alongside facing");

        // 2. Write a real gzipped Create .nbt schematic and load via AxiomBlueprint427 + CreateSchematic431
        Path tmp = Files.createTempDirectory("hd431_probe");
        try {
            Path schemDir = tmp.resolve("schematics");
            Files.createDirectories(schemDir);
            Path roomNbt = schemDir.resolve("к комната_create.nbt");
            writeTestCreateSchematicNbt(roomNbt);

            AxiomBlueprint427.PieceTemplate piece = AxiomBlueprint427.loadFile(roomNbt, "к комната_create");
            check(piece != null && piece.entrance != null && piece.exits.size() == 1,
                    "Create .nbt loaded with 1 lapis entrance and 1 redstone exit");
            for (DungeonTemplate425.PaletteEntry pe : piece.content.palette) {
                check(!"minecraft:structure_void".equals(pe.id) && !"minecraft:air".equals(pe.id),
                        "structure_void and air filtered from Create .nbt palette: " + pe.id);
            }
            check(piece.content.oldSchemaOrigin != null
                    && piece.content.oldSchemaOrigin[0] == 999
                    && piece.content.oldSchemaOrigin[1] == 69
                    && piece.content.oldSchemaOrigin[2] == 1998,
                    "oldSchemaOrigin inferred accurately from Create IsController+Controller");

            // Check 4-phase placement order & MockBlockEntity + sendBlockUpdated
            MockServer srv = new MockServer();
            MockLevel lvl = new MockLevel(DungeonManager425.HELL_DUNGEON_LEVEL, srv);
            lvl.createMockBlockEntities = true;
            DungeonTemplate425.PlacementStats stats = new DungeonTemplate425.PlacementStats();
            DungeonTemplate425.placeSlice(lvl, piece.content, 100, 50, 200, 1,
                    DungeonTemplate425.DOORWAY_REPLACEMENT, 0, piece.blockCount(), stats);
            check(stats.blocksPlaced == piece.blockCount(),
                    "all Create .nbt blocks placed: " + stats.blocksPlaced + "/" + piece.blockCount());
            check(stats.blockEntitiesLoaded == piece.content.blockEntities.size(),
                    "all Create block entities loaded: " + stats.blockEntitiesLoaded);
            check(lvl.sendBlockUpdatedCalls > 0 && lvl.beSetChangedCalls > 0,
                    "post-room CreateCompat431 synced BEs via setChanged & sendBlockUpdated");
            check(!CreateCompat431.isCreateAvailable(), "CreateCompat431 safely detects Create absent in unit env");

            int idxStone = lvl.firstOrderIndex("minecraft:blackstone");
            int idxBelt = lvl.firstOrderIndex("create:belt");
            int idxLink = lvl.firstOrderIndex("create:redstone_link");
            int idxDoor = lvl.firstOrderIndex("valhelsia_structures:dungeon_door");
            check(idxStone >= 0 && idxBelt > idxStone && idxLink > idxBelt && idxDoor > idxLink,
                    "4-phase order enforced: structural(" + idxStone + ") < create(" + idxBelt
                            + ") < fragile(" + idxLink + ") < door(" + idxDoor + ")");

            // Verify DungeonNbt431 BlockEntity rotation & kinetic reset directly
            for (DungeonTemplate425.BlockEntitySpec beSpec : piece.content.blockEntities) {
                int rbx = DungeonGenerator427.rotX(beSpec.x, beSpec.z, 1);
                int rbz = DungeonGenerator427.rotZ(beSpec.x, beSpec.z, 1);
                DungeonNbt431.NbtCompound tb = DungeonNbt431.transformBlockEntityNbt(
                        beSpec.id, beSpec.rawNbt, 100, 50, 200,
                        100 + rbx, 50 + beSpec.y, 200 + rbz, 1, piece.content.oldSchemaOrigin);
                check(!tb.contains("Source") && !tb.contains("Network"),
                        "stale kinetic Source/Network stripped on " + beSpec.id);
                check(tb.getInt("NeedsSpeedUpdate") == 1,
                        "NeedsSpeedUpdate=1 set on " + beSpec.id);
                if ("create:windmill_bearing".equals(beSpec.id)) {
                    check(tb.getInt("Running") == 0 && tb.getInt("QueueAssembly") == 1,
                            "windmill bearing resets Running=0 and sets QueueAssembly=1");
                }
                if ("create:belt".equals(beSpec.id)) {
                    check(!tb.contains("Controller") && tb.getInt("IsController") == 0 && tb.getInt("Length") == 0,
                            "rotated belt clears Controller/Length for BeltBlock.initBelt");
                }
                if ("create:fluid_tank".equals(beSpec.id)) {
                    check(!tb.contains("Controller") && tb.getInt("Uninitialized") == 1,
                            "rotated fluid_tank resets Controller & sets Uninitialized=1");
                }
                if ("create:display_link".equals(beSpec.id)) {
                    int[] toff = tb.getIntArray("TargetOffset");
                    check(toff != null && toff[0] == 3 && toff[1] == 0 && toff[2] == 1,
                            "DisplayLink TargetOffset (1,0,-3) CW90 -> (3,0,1)");
                }
                if ("create:chain_conveyor".equals(beSpec.id)) {
                    int[] conn0 = tb.getList("Connections").get(0).val instanceof int[] ca ? ca : null;
                    check(conn0 != null && conn0[0] == 0 && conn0[1] == 0 && conn0[2] == 2,
                            "ChainConveyor Connection (2,0,0) CW90 -> (0,0,2)");
                }
                if ("create:mechanical_arm".equals(beSpec.id)) {
                    DungeonNbt431.NbtCompound pt0 = tb.getList("InteractionPoints").get(0).val instanceof DungeonNbt431.NbtCompound c ? c : null;
                    int[] ip = pt0 != null ? pt0.getIntArray("Pos") : null;
                    check(ip != null && ip[0] == 2 && ip[1] == 1 && ip[2] == 0,
                            "MechanicalArm InteractionPoint (0,1,-2) CW90 -> (2,1,0)");
                }
                if ("create:mechanical_crafter".equals(beSpec.id)) {
                    DungeonNbt431.NbtCompound d0 = tb.getCompound("Input").getList("Data").get(0).val instanceof DungeonNbt431.NbtCompound c ? c : null;
                    check(d0 != null && d0.getInt("X") == 0 && d0.getInt("Y") == 0 && d0.getInt("Z") == 1,
                            "MechanicalCrafter Input.Data (1,0,0) CW90 -> (0,0,1)");
                    DungeonNbt431.NbtCompound brProps = tb.getCompound("Bracket").getCompound("Properties");
                    check("east".equals(brProps.getString("facing")), "Bracket facing north -> east");
                }
                if ("create:copycat_step".equals(beSpec.id)) {
                    DungeonNbt431.NbtCompound matProps = tb.getCompound("Material").getCompound("Properties");
                    check("south".equals(matProps.getString("facing")), "Copycat Material facing east -> south");
                }
                if ("create:powered_shaft".equals(beSpec.id)) {
                    int[] ep = tb.getIntArray("EnginePos");
                    // raw (1,1,2) relative to oldSchemaOrigin (999,69,1998) at CW90 -> rot(1,2,1)=(-2,1) -> (98, 51, 201)
                    check(ep != null && ep[0] == 98 && ep[1] == 51 && ep[2] == 201,
                            "PoweredShaft EnginePos translated & rotated accurately");
                }
            }

            // Verify SuperGlue AABB transform across all 4 rotations + ItemFrame facing/block_pos
            DungeonTemplate425.EntitySpec glueSpec = null;
            DungeonTemplate425.EntitySpec frameSpec = null;
            for (DungeonTemplate425.EntitySpec es : piece.content.entities) {
                if ("create:super_glue".equals(es.id)) glueSpec = es;
                if ("minecraft:item_frame".equals(es.id)) frameSpec = es;
            }
            check(glueSpec != null && frameSpec != null, "both super_glue and item_frame parsed from .nbt");

            for (int t = 0; t < 4; t++) {
                DungeonNbt431.TransformedEntity te = DungeonNbt431.transformEntityPlacement(
                        glueSpec, 100, 50, 200, t);
                check(te.moveBeforeLoad, "super_glue always sets moveBeforeLoad=true");
                check(!te.mcNbt.contains("UUID") && !te.mcNbt.contains("Pos"),
                        "super_glue strips UUID & Pos");
                // Original box in schematic: x=[1..3], y=[2..4], z=[1..2]
                // Compute rotated AABB corners and compare to transformed pos + From/To
                double rx0 = DungeonGenerator427.rotEntityX(1.0, 1.0, t);
                double rz0 = DungeonGenerator427.rotEntityZ(1.0, 1.0, t);
                double rx1 = DungeonGenerator427.rotEntityX(3.0, 2.0, t);
                double rz1 = DungeonGenerator427.rotEntityZ(3.0, 2.0, t);
                double expCx = 100.0 + (Math.min(rx0, rx1) + Math.max(rx0, rx1)) * 0.5;
                double expCz = 200.0 + (Math.min(rz0, rz1) + Math.max(rz0, rz1)) * 0.5;
                check(Math.abs(te.wx - expCx) < 1e-6 && Math.abs(te.wy - 52.0) < 1e-6
                        && Math.abs(te.wz - expCz) < 1e-6,
                        "super_glue position matches rotated AABB center at turns=" + t);
            }

            DungeonNbt431.TransformedEntity frameTe = DungeonNbt431.transformEntityPlacement(
                    frameSpec, 100, 50, 200, 1);
            check(frameTe.mcNbt.getInt("facing") == 5, "item_frame facing 2(NORTH) CW90 -> 5(EAST)");
            int[] fbp = frameTe.mcNbt.getIntArray("block_pos");
            check(fbp.length == 3 && fbp[0] == (int) Math.floor(frameTe.wx)
                    && fbp[1] == (int) Math.floor(frameTe.wy)
                    && fbp[2] == (int) Math.floor(frameTe.wz),
                    "item_frame block_pos updated to rotated world block coords");

            // 3. Verify auto-discovery of schematics/ directory with .nbt start (0 lapis) + .nbt room
            writeStartNbtWithoutLapis(schemDir.resolve("старт.nbt"));
            DungeonConfig427 autoCfg = new DungeonConfig427(
                    tmp, "auto", "auto", "старт", 4, 8, 128, 0, 128, true,
                    DungeonTemplate425.DOORWAY_REPLACEMENT, 4, 4096, true, true);
            DungeonBlueprints427.Library nbtLib = DungeonBlueprints427.load(autoCfg);
            check(nbtLib.nbtCount == 2 && nbtLib.pieces.size() == 1 && nbtLib.startPiece != null
                    && !nbtLib.startPiece.embeddedFallback,
                    "auto-discovered schematics/ with старт.nbt (0 lapis) + room .nbt");
            DungeonGenerator427.Plan nbtPlan = DungeonGenerator427.generate(nbtLib, 0, 64, 0, 777L);
            check(nbtPlan != null && nbtPlan.pieces.size() >= 2,
                    "dungeon generated seamlessly from .nbt start & rooms: " + nbtPlan.pieces.size());

            // 4. Verify uncompressed vanilla NBT using `palettes` list-of-lists
            DungeonNbt431.NbtCompound pRoot = new DungeonNbt431.NbtCompound();
            List<DungeonNbt431.NbtValue> firstPal = new ArrayList<>();
            DungeonNbt431.NbtCompound ps0 = new DungeonNbt431.NbtCompound();
            ps0.putString("Name", "minecraft:blackstone");
            firstPal.add(new DungeonNbt431.NbtValue(10, 0, ps0));
            List<DungeonNbt431.NbtValue> outerPal = new ArrayList<>();
            outerPal.add(new DungeonNbt431.NbtValue(9, 10, firstPal));
            pRoot.values.put("palettes", new DungeonNbt431.NbtValue(9, 9, outerPal));
            List<DungeonNbt431.NbtValue> pBlocks = new ArrayList<>();
            pBlocks.add(makeNbtBlock(0, 0, 0, 0, null));
            pRoot.values.put("blocks", new DungeonNbt431.NbtValue(9, 10, pBlocks));
            ByteArrayOutputStream rawBaos = new ByteArrayOutputStream();
            try (DataOutputStream rawOut = new DataOutputStream(rawBaos)) {
                rawOut.writeByte(10);
                DungeonNbt431.writeString(rawOut, "");
                DungeonNbt431.writeCompound(rawOut, pRoot);
            }
            AxiomBlueprint427.PieceTemplate rawPalPiece = AxiomBlueprint427.parseBytes(
                    rawBaos.toByteArray(), "raw_palettes");
            check(rawPalPiece != null && rawPalPiece.blockCount() == 1
                    && "minecraft:blackstone".equals(rawPalPiece.content.palette.get(0).id),
                    "uncompressed palettes[0] .nbt decoded via AxiomBlueprint427.parseBytes");
        } finally {
            deleteTree(tmp);
        }
    }

    private static void writeTestCreateSchematicNbt(Path path) throws IOException {
        DungeonNbt431.NbtCompound root = new DungeonNbt431.NbtCompound();

        // Palette: structure_void(0), blackstone(1), lapis(2), redstone(3), belt(4), fluid_tank(5),
        // windmill(6), display_link(7), chain_conveyor(8), arm(9), crafter(10), copycat(11),
        // powered_shaft(12), redstone_link(13), door_lower(14), door_upper(15)
        String[] names = {
            "minecraft:structure_void",
            "minecraft:blackstone",
            "minecraft:lapis_block",
            "minecraft:redstone_block",
            "create:belt",
            "create:fluid_tank",
            "create:windmill_bearing",
            "create:display_link",
            "create:chain_conveyor",
            "create:mechanical_arm",
            "create:mechanical_crafter",
            "create:copycat_step",
            "create:powered_shaft",
            "create:redstone_link",
            "valhelsia_structures:dungeon_door",
            "valhelsia_structures:dungeon_door",
        };
        List<DungeonNbt431.NbtValue> palList = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            DungeonNbt431.NbtCompound st = new DungeonNbt431.NbtCompound();
            st.putString("Name", names[i]);
            if (i == 4) {
                DungeonNbt431.NbtCompound pr = new DungeonNbt431.NbtCompound();
                pr.putString("facing", "north");
                pr.putString("slope", "vertical");
                pr.putString("part", "start");
                st.putCompound("Properties", pr);
            } else if (i == 14 || i == 15) {
                DungeonNbt431.NbtCompound pr = new DungeonNbt431.NbtCompound();
                pr.putString("facing", "north");
                pr.putString("half", i == 14 ? "lower" : "upper");
                st.putCompound("Properties", pr);
            }
            palList.add(new DungeonNbt431.NbtValue(10, 0, st));
        }
        root.values.put("palette", new DungeonNbt431.NbtValue(9, 10, palList));

        // Blocks listed deliberately with doors & redstone_link FIRST to prove 4-phase re-ordering
        List<DungeonNbt431.NbtValue> blocks = new ArrayList<>();
        blocks.add(makeNbtBlock(4, 2, 1, 15, null)); // door upper
        blocks.add(makeNbtBlock(4, 1, 1, 14, null)); // door lower
        blocks.add(makeNbtBlock(2, 2, 1, 13, null)); // redstone_link (fragile phase 2)

        // Create BEs (phase 1)
        DungeonNbt431.NbtCompound beltNbt = new DungeonNbt431.NbtCompound();
        beltNbt.putString("id", "create:belt");
        beltNbt.putByte("IsController", (byte) 1);
        beltNbt.putIntArray("Controller", new int[] { 1000, 70, 2000 }); // local (1,1,2) -> origin (999,69,1998)
        beltNbt.putInt("Length", 3);
        beltNbt.putFloat("Speed", 32.0F);
        beltNbt.putIntArray("Source", new int[] { 999, 70, 2000 });
        beltNbt.putByte("Network", (byte) 1);
        blocks.add(makeNbtBlock(1, 1, 2, 4, beltNbt));

        DungeonNbt431.NbtCompound tankNbt = new DungeonNbt431.NbtCompound();
        tankNbt.putString("id", "create:fluid_tank");
        tankNbt.putIntArray("Controller", new int[] { 999, 69, 1999 });
        blocks.add(makeNbtBlock(2, 1, 2, 5, tankNbt));

        DungeonNbt431.NbtCompound windNbt = new DungeonNbt431.NbtCompound();
        windNbt.putString("id", "create:windmill_bearing");
        windNbt.putByte("Running", (byte) 1);
        blocks.add(makeNbtBlock(1, 2, 2, 6, windNbt));

        DungeonNbt431.NbtCompound dlNbt = new DungeonNbt431.NbtCompound();
        dlNbt.putString("id", "create:display_link");
        dlNbt.putIntArray("TargetOffset", new int[] { 1, 0, -3 });
        blocks.add(makeNbtBlock(3, 1, 2, 7, dlNbt));

        DungeonNbt431.NbtCompound ccNbt = new DungeonNbt431.NbtCompound();
        ccNbt.putString("id", "create:chain_conveyor");
        List<DungeonNbt431.NbtValue> conns = new ArrayList<>();
        conns.add(new DungeonNbt431.NbtValue(11, 0, new int[] { 2, 0, 0 }));
        ccNbt.values.put("Connections", new DungeonNbt431.NbtValue(9, 11, conns));
        blocks.add(makeNbtBlock(3, 2, 2, 8, ccNbt));

        DungeonNbt431.NbtCompound armNbt = new DungeonNbt431.NbtCompound();
        armNbt.putString("id", "create:mechanical_arm");
        List<DungeonNbt431.NbtValue> ips = new ArrayList<>();
        DungeonNbt431.NbtCompound ip0 = new DungeonNbt431.NbtCompound();
        ip0.putIntArray("Pos", new int[] { 0, 1, -2 });
        ips.add(new DungeonNbt431.NbtValue(10, 0, ip0));
        armNbt.values.put("InteractionPoints", new DungeonNbt431.NbtValue(9, 10, ips));
        blocks.add(makeNbtBlock(1, 3, 2, 9, armNbt));

        DungeonNbt431.NbtCompound crNbt = new DungeonNbt431.NbtCompound();
        crNbt.putString("id", "create:mechanical_crafter");
        DungeonNbt431.NbtCompound inputTag = new DungeonNbt431.NbtCompound();
        List<DungeonNbt431.NbtValue> dataList = new ArrayList<>();
        DungeonNbt431.NbtCompound d0 = new DungeonNbt431.NbtCompound();
        d0.putInt("X", 1); d0.putInt("Y", 0); d0.putInt("Z", 0);
        dataList.add(new DungeonNbt431.NbtValue(10, 0, d0));
        inputTag.values.put("Data", new DungeonNbt431.NbtValue(9, 10, dataList));
        crNbt.putCompound("Input", inputTag);
        DungeonNbt431.NbtCompound brTag = new DungeonNbt431.NbtCompound();
        brTag.putString("Name", "create:andesite_bracket");
        DungeonNbt431.NbtCompound brProps = new DungeonNbt431.NbtCompound();
        brProps.putString("facing", "north");
        brTag.putCompound("Properties", brProps);
        crNbt.putCompound("Bracket", brTag);
        blocks.add(makeNbtBlock(2, 3, 2, 10, crNbt));

        DungeonNbt431.NbtCompound catNbt = new DungeonNbt431.NbtCompound();
        catNbt.putString("id", "create:copycat_step");
        DungeonNbt431.NbtCompound matTag = new DungeonNbt431.NbtCompound();
        matTag.putString("Name", "minecraft:oak_stairs");
        DungeonNbt431.NbtCompound matProps = new DungeonNbt431.NbtCompound();
        matProps.putString("facing", "east");
        matTag.putCompound("Properties", matProps);
        catNbt.putCompound("Material", matTag);
        blocks.add(makeNbtBlock(3, 3, 2, 11, catNbt));

        DungeonNbt431.NbtCompound psNbt = new DungeonNbt431.NbtCompound();
        psNbt.putString("id", "create:powered_shaft");
        psNbt.putIntArray("EnginePos", new int[] { 1000, 70, 2000 });
        blocks.add(makeNbtBlock(3, 3, 1, 12, psNbt));

        // Structural floor + markers + structure_void
        blocks.add(makeNbtBlock(1, 0, 1, 0, null)); // structure_void (filtered out)
        blocks.add(makeNbtBlock(0, 1, 2, 2, null)); // lapis on West edge (x=0)
        blocks.add(makeNbtBlock(4, 1, 2, 3, null)); // redstone on East edge (x=4)
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 3; z++) {
                blocks.add(makeNbtBlock(x, 0, z, 1, null));
            }
        }
        root.values.put("blocks", new DungeonNbt431.NbtValue(9, 10, blocks));

        // Entities: super_glue spanning [1..3, 2..4, 1..2] + item_frame facing NORTH (2)
        List<DungeonNbt431.NbtValue> ents = new ArrayList<>();
        DungeonNbt431.NbtCompound glueWrap = new DungeonNbt431.NbtCompound();
        glueWrap.putDoubleList("pos", 2.0D, 2.0D, 1.5D);
        DungeonNbt431.NbtCompound glueNbt = new DungeonNbt431.NbtCompound();
        glueNbt.putString("id", "create:super_glue");
        glueNbt.putDoubleList("From", -1.0D, 0.0D, -0.5D);
        glueNbt.putDoubleList("To", 1.0D, 2.0D, 0.5D);
        glueNbt.putIntArray("UUID", new int[] { 1, 2, 3, 4 });
        glueNbt.putDoubleList("Pos", 1000.0D, 70.0D, 2000.0D);
        glueWrap.putCompound("nbt", glueNbt);
        ents.add(new DungeonNbt431.NbtValue(10, 0, glueWrap));

        DungeonNbt431.NbtCompound frameWrap = new DungeonNbt431.NbtCompound();
        frameWrap.putDoubleList("pos", 1.5D, 2.5D, 1.03125D);
        DungeonNbt431.NbtCompound frameNbt = new DungeonNbt431.NbtCompound();
        frameNbt.putString("id", "minecraft:item_frame");
        frameNbt.putByte("facing", (byte) 2);
        frameNbt.putIntArray("block_pos", new int[] { 999, 71, 1999 });
        frameWrap.putCompound("nbt", frameNbt);
        ents.add(new DungeonNbt431.NbtValue(10, 0, frameWrap));
        root.values.put("entities", new DungeonNbt431.NbtValue(9, 10, ents));

        try (GZIPOutputStream gz = new GZIPOutputStream(Files.newOutputStream(path));
             DataOutputStream out = new DataOutputStream(gz)) {
            out.writeByte(10);
            DungeonNbt431.writeString(out, "");
            DungeonNbt431.writeCompound(out, root);
        }
    }

    private static DungeonNbt431.NbtValue makeNbtBlock(int x, int y, int z, int state,
            DungeonNbt431.NbtCompound beNbt) {
        DungeonNbt431.NbtCompound b = new DungeonNbt431.NbtCompound();
        List<DungeonNbt431.NbtValue> pos = new ArrayList<>(3);
        pos.add(new DungeonNbt431.NbtValue(3, 0, x));
        pos.add(new DungeonNbt431.NbtValue(3, 0, y));
        pos.add(new DungeonNbt431.NbtValue(3, 0, z));
        b.values.put("pos", new DungeonNbt431.NbtValue(9, 3, pos));
        b.putInt("state", state);
        if (beNbt != null) {
            b.putCompound("nbt", beNbt);
        }
        return new DungeonNbt431.NbtValue(10, 0, b);
    }

    private static void writeStartNbtWithoutLapis(Path path) throws IOException {
        DungeonNbt431.NbtCompound root = new DungeonNbt431.NbtCompound();
        List<DungeonNbt431.NbtValue> palList = new ArrayList<>();
        for (String name : new String[] { "minecraft:blackstone", "minecraft:redstone_block" }) {
            DungeonNbt431.NbtCompound st = new DungeonNbt431.NbtCompound();
            st.putString("Name", name);
            palList.add(new DungeonNbt431.NbtValue(10, 0, st));
        }
        root.values.put("palette", new DungeonNbt431.NbtValue(9, 10, palList));
        List<DungeonNbt431.NbtValue> blocks = new ArrayList<>();
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                blocks.add(makeNbtBlock(x, 0, z, 0, null));
            }
        }
        blocks.add(makeNbtBlock(4, 1, 2, 1, null)); // 1 redstone exit, 0 lapis markers
        root.values.put("blocks", new DungeonNbt431.NbtValue(9, 10, blocks));
        try (GZIPOutputStream gz = new GZIPOutputStream(Files.newOutputStream(path));
             DataOutputStream out = new DataOutputStream(gz)) {
            out.writeByte(10);
            DungeonNbt431.writeString(out, "");
            DungeonNbt431.writeCompound(out, root);
        }
    }

    private static long packContraptionPos(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
    }

    private static int[] unpackContraptionPos(long pos) {
        return new int[] { (int) (pos >> 38), (int) ((pos << 52) >> 52), (int) ((pos << 26) >> 38) };
    }

    private static void writeTestContraptionRoomNbt(Path path) throws IOException {
        DungeonNbt431.NbtCompound root = new DungeonNbt431.NbtCompound();
        String[] names = {
            "minecraft:blackstone",
            "minecraft:lapis_block",
            "minecraft:redstone_block",
            "create:mechanical_bearing",
            "create:creative_motor"
        };
        List<DungeonNbt431.NbtValue> palList = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            DungeonNbt431.NbtCompound st = new DungeonNbt431.NbtCompound();
            st.putString("Name", names[i]);
            if (i >= 3) {
                DungeonNbt431.NbtCompound pr = new DungeonNbt431.NbtCompound();
                pr.putString("facing", "west");
                st.putCompound("Properties", pr);
            }
            palList.add(new DungeonNbt431.NbtValue(10, 0, st));
        }
        root.values.put("palette", new DungeonNbt431.NbtValue(9, 10, palList));

        List<DungeonNbt431.NbtValue> blocks = new ArrayList<>();
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                int s = (x == 3 && z == 0) ? 1 : (x == 3 && z == 6) ? 2 : 0;
                blocks.add(makeNbtBlock(x, 0, z, s, null));
            }
        }
        DungeonNbt431.NbtCompound bearingNbt = new DungeonNbt431.NbtCompound();
        bearingNbt.putString("id", "create:mechanical_bearing");
        bearingNbt.putByte("Running", (byte) 1);
        bearingNbt.putFloat("Angle", -94.197F);
        bearingNbt.putFloat("Speed", -1.0F);
        bearingNbt.putByte("NeedsSpeedUpdate", (byte) 1);
        bearingNbt.putIntArray("Source", new int[] { -10, -40, 20 });
        bearingNbt.putCompound("Network", new DungeonNbt431.NbtCompound());
        blocks.add(makeNbtBlock(5, 3, 3, 3, bearingNbt));

        DungeonNbt431.NbtCompound motorNbt = new DungeonNbt431.NbtCompound();
        motorNbt.putString("id", "create:motor");
        motorNbt.putInt("ScrollValue", 1);
        motorNbt.putFloat("Speed", -1.0F);
        motorNbt.putCompound("Network", new DungeonNbt431.NbtCompound());
        blocks.add(makeNbtBlock(6, 3, 3, 4, motorNbt));
        root.values.put("blocks", new DungeonNbt431.NbtValue(9, 10, blocks));

        // create:stationary_contraption at exact integer corner [4.0, 3.0, 3.0]
        DungeonNbt431.NbtCompound scWrap = new DungeonNbt431.NbtCompound();
        scWrap.putDoubleList("pos", 4.0D, 3.0D, 3.0D);
        DungeonNbt431.NbtCompound scNbt = new DungeonNbt431.NbtCompound();
        scNbt.putString("id", "create:stationary_contraption");
        scNbt.putIntArray("ControllerRelative", new int[] { 1, 0, 0 });
        scNbt.putString("Axis", "X");
        scNbt.putFloat("Angle", -94.197F);
        scNbt.putByte("Initialized", (byte) 1);

        DungeonNbt431.NbtCompound contr = new DungeonNbt431.NbtCompound();
        contr.putString("Type", "create:bearing");
        contr.putIntArray("Anchor", new int[] { -12, -40, 20 });
        contr.putInt("Facing", 4); // WEST
        List<DungeonNbt431.NbtValue> bf = new ArrayList<>();
        for (float f : new float[] { -3.0F, -2.0F, -2.0F, 1.0F, 3.0F, 3.0F }) {
            bf.add(new DungeonNbt431.NbtValue(5, 0, f));
        }
        contr.values.put("BoundsFront", new DungeonNbt431.NbtValue(9, 5, bf));

        DungeonNbt431.NbtCompound cBlocks = new DungeonNbt431.NbtCompound();
        List<DungeonNbt431.NbtValue> cPal = new ArrayList<>();
        DungeonNbt431.NbtCompound chainSt = new DungeonNbt431.NbtCompound();
        chainSt.putString("Name", "minecraft:chain");
        DungeonNbt431.NbtCompound chainProps = new DungeonNbt431.NbtCompound();
        chainProps.putString("axis", "x");
        chainProps.putString("waterlogged", "false");
        chainSt.putCompound("Properties", chainProps);
        cPal.add(new DungeonNbt431.NbtValue(10, 0, chainSt));
        cBlocks.values.put("Palette", new DungeonNbt431.NbtValue(9, 10, cPal));

        List<DungeonNbt431.NbtValue> cList = new ArrayList<>();
        for (int lx : new int[] { 0, -1, -2, -3 }) {
            DungeonNbt431.NbtCompound be = new DungeonNbt431.NbtCompound();
            be.putInt("State", 0);
            be.putLong("Pos", packContraptionPos(lx, 0, 0));
            cList.add(new DungeonNbt431.NbtValue(10, 0, be));
        }
        cBlocks.values.put("BlockList", new DungeonNbt431.NbtValue(9, 10, cList));
        contr.putCompound("Blocks", cBlocks);

        DungeonNbt431.NbtCompound actor0 = new DungeonNbt431.NbtCompound();
        actor0.putIntArray("Pos", new int[] { -2, 0, 0 });
        actor0.putDoubleList("Position", -13.5D, -39.5D, 20.5D);
        actor0.putDoubleList("Motion", 0.01D, 0.0D, 0.0D);
        actor0.putDoubleList("RelativeMotion", 0.01D, 0.0D, 0.0D);
        List<DungeonNbt431.NbtValue> actors = new ArrayList<>();
        actors.add(new DungeonNbt431.NbtValue(10, 0, actor0));
        contr.values.put("Actors", new DungeonNbt431.NbtValue(9, 10, actors));

        scNbt.putCompound("Contraption", contr);
        scWrap.putCompound("nbt", scNbt);
        List<DungeonNbt431.NbtValue> ents = new ArrayList<>();
        ents.add(new DungeonNbt431.NbtValue(10, 0, scWrap));
        root.values.put("entities", new DungeonNbt431.NbtValue(9, 10, ents));

        try (GZIPOutputStream gz = new GZIPOutputStream(Files.newOutputStream(path));
             DataOutputStream out = new DataOutputStream(gz)) {
            out.writeByte(10);
            DungeonNbt431.writeString(out, "");
            DungeonNbt431.writeCompound(out, root);
        }
    }

    private static void testBuild433ContraptionAndNbtSpawning() throws Exception {
        Path tmp = Files.createTempDirectory("hd433_probe");
        try {
            Path nbtFile = tmp.resolve("а АстролябияТест.nbt");
            writeTestContraptionRoomNbt(nbtFile);
            AxiomBlueprint427.PieceTemplate piece = AxiomBlueprint427.loadFile(nbtFile, "а АстролябияТест");
            check(piece.isNbt, "Build 433 marks .nbt PieceTemplate with isNbt=true");
            check(piece.content.entities.size() == 1, "contraption entity loaded");

            DungeonTemplate425.BlockEntitySpec bearingBe = null;
            for (DungeonTemplate425.BlockEntitySpec be : piece.content.blockEntities) {
                if ("create:mechanical_bearing".equals(be.id)) bearingBe = be;
            }
            check(bearingBe != null, "mechanical_bearing BlockEntitySpec parsed");
            DungeonTemplate425.EntitySpec scSpec = piece.content.entities.get(0);

            int ox = 500, oy = 40, oz = -300;
            String[] expAxis = { "X", "Z", "X", "Z" };
            int[] expFacing = { 4, 2, 5, 3 };
            String[] expChainAxis = { "x", "z", "x", "z" };
            for (int t = 0; t < 4; t++) {
                int bx = ox + DungeonGenerator427.rotX(bearingBe.x, bearingBe.z, t);
                int by = oy + bearingBe.y;
                int bz = oz + DungeonGenerator427.rotZ(bearingBe.x, bearingBe.z, t);
                DungeonNbt431.NbtCompound tb = DungeonNbt431.transformBlockEntityNbt(
                        bearingBe.id, bearingBe.rawNbt, ox, oy, oz, bx, by, bz, t, null);
                check(tb.getBoolean("Running"), "mechanical_bearing Running=1 preserved at t=" + t);
                check(!tb.contains("QueueAssembly"), "mechanical_bearing does not inject QueueAssembly at t=" + t);

                DungeonNbt431.TransformedEntity te = DungeonNbt431.transformEntityPlacement(scSpec, ox, oy, oz, t);
                check(te.moveBeforeLoad && te.yaw == 0.0F && te.pitch == 0.0F,
                        "contraption sets moveBeforeLoad=true and zero yaw/pitch at t=" + t);
                check(te.wx == ox + DungeonGenerator427.rotX(4, 3, t)
                        && te.wy == oy + 3.0D
                        && te.wz == oz + DungeonGenerator427.rotZ(4, 3, t),
                        "integer-corner contraption placed without +1 block shift at t=" + t);

                DungeonNbt431.NbtCompound raw = DungeonNbt431.prepareContraptionNbt(scSpec, ox, oy, oz, t);
                int[] cr = raw.getIntArray("ControllerRelative");
                check(cr != null && cr.length == 3
                        && ((int) Math.floor(te.wx)) + cr[0] == bx
                        && ((int) Math.floor(te.wy)) + cr[1] == by
                        && ((int) Math.floor(te.wz)) + cr[2] == bz,
                        "ControllerRelative points directly at rotated bearing at t=" + t);
                check(expAxis[t].equals(raw.getString("Axis")), "Axis rotated at t=" + t);

                DungeonNbt431.NbtCompound contr = raw.getCompound("Contraption");
                int[] anc = contr.getIntArray("Anchor");
                check(anc != null && anc[0] == (int) Math.floor(te.wx) && anc[2] == (int) Math.floor(te.wz),
                        "Contraption.Anchor updated to world coords at t=" + t);
                check(contr.getInt("Facing") == expFacing[t], "Contraption.Facing rotated at t=" + t);
                DungeonNbt431.NbtValue bfVal = contr.values.get("BoundsFront");
                check(bfVal != null && bfVal.type == 9 && bfVal.listElemType == 5,
                        "Contraption.BoundsFront keeps TAG_Float listElemType=5 at t=" + t);

                DungeonNbt431.NbtCompound cBlocks = contr.getCompound("Blocks");
                DungeonNbt431.NbtCompound chainState = (DungeonNbt431.NbtCompound) cBlocks.getList("Palette").get(0).val;
                check(expChainAxis[t].equals(chainState.getCompound("Properties").getString("axis")),
                        "Contraption Palette chain axis rotated at t=" + t);
                for (DungeonNbt431.NbtValue bItem : cBlocks.getList("BlockList")) {
                    DungeonNbt431.NbtCompound entry = (DungeonNbt431.NbtCompound) bItem.val;
                    int[] xyz = unpackContraptionPos(((Number) entry.values.get("Pos").val).longValue());
                    if (t == 1) check(xyz[0] == 0 && xyz[2] <= 0 && xyz[2] >= -3, "t=1 BlockList along -Z");
                    if (t == 2) check(xyz[2] == 0 && xyz[0] >= 0 && xyz[0] <= 3, "t=2 BlockList along +X");
                    if (t == 3) check(xyz[0] == 0 && xyz[2] >= 0 && xyz[2] <= 3, "t=3 BlockList along +Z");
                }
                DungeonNbt431.NbtCompound act = (DungeonNbt431.NbtCompound) contr.getList("Actors").get(0).val;
                int[] ap = act.getIntArray("Pos");
                check(ap != null && ap[0] == DungeonGenerator427.rotX(-2, 0, t)
                        && ap[2] == DungeonGenerator427.rotZ(-2, 0, t), "Actor.Pos rotated at t=" + t);
                List<DungeonNbt431.NbtValue> apos = act.getList("Position");
                double ax = ((Number) apos.get(0).val).doubleValue() - anc[0];
                double az = ((Number) apos.get(2).val).doubleValue() - anc[2];
                check(Math.abs(ax - (ap[0] + 0.5D)) < 1e-6 && Math.abs(az - (ap[2] + 0.5D)) < 1e-6,
                        "Actor.Position matches rotated Pos relative to Anchor at t=" + t);
            }

            // Test .nbt overriding same-key .bp + unprefixed .nbt in auto biome mode + unused schematics/ hint
            Path worldDir = tmp.resolve("world_test");
            Path axiomDir = worldDir.resolve("config/axiom/blueprints/Данж");
            Path schemDir = worldDir.resolve("schematics");
            Files.createDirectories(axiomDir);
            Files.createDirectories(schemDir);
            writeRoomBp(axiomDir.resolve("старт.bp"), "старт", List.of(), 0, 8, true, true, false, false, true);
            writeRoomBp(axiomDir.resolve("к Коридор.bp"), "к Коридор", List.of("dungeon", "crypt"),
                    0, 10, false, true, true, false, false);
            writeRoomBp(axiomDir.resolve("к Развилка.bp"), "к Развилка", List.of("dungeon", "crypt"),
                    0, 8, false, true, true, true, false);
            writeRoomBp(axiomDir.resolve("к Тупик.bp"), "к Тупик", List.of("dungeon", "crypt"),
                    0, 6, false, true, false, false, false);
            for (int i = 1; i <= 15; i++) {
                writeRoomBp(axiomDir.resolve("а Комната_" + i + ".bp"), "а Комната_" + i,
                        List.of("dungeon", "infernal"), 0, 10, false, true, true, false, false);
            }
            writeRoomBp(axiomDir.resolve("а Развилка.bp"), "а Развилка", List.of("dungeon", "infernal"),
                    0, 8, false, true, true, true, false);
            writeRoomBp(axiomDir.resolve("а АстролябияТест.bp"), "а АстролябияТест",
                    List.of("dungeon", "infernal"), 0, 8, false, true, true, false, false);
            writeRoomBp(axiomDir.resolve("а Тупик.bp"), "а Тупик", List.of("dungeon", "infernal"),
                    0, 6, false, true, false, false, false);
            writeRoomBp(axiomDir.resolve("п Коридор.bp"), "п Коридор", List.of("dungeon", "flesh"),
                    0, 10, false, true, true, false, false);
            writeRoomBp(axiomDir.resolve("п Развилка.bp"), "п Развилка", List.of("dungeon", "flesh"),
                    0, 8, false, true, true, true, false);
            writeRoomBp(axiomDir.resolve("п Тупик.bp"), "п Тупик", List.of("dungeon", "flesh"),
                    0, 6, false, true, false, false, false);
            Files.copy(nbtFile, axiomDir.resolve("а АстролябияТест.nbt"));
            writeTestContraptionRoomNbt(axiomDir.resolve("СвояНБТ.nbt"));
            writeTestContraptionRoomNbt(schemDir.resolve("Забытая.nbt"));

            DungeonConfig427 autoCfg = new DungeonConfig427(
                    worldDir, "auto", "auto", "старт", 24, 34, 160, -52, 240, true,
                    DungeonTemplate425.DOORWAY_REPLACEMENT, 8, 4096, true, true);
            DungeonBlueprints427.Library lib = DungeonBlueprints427.load(autoCfg);
            int astroCount = 0;
            boolean astroNbt = false;
            boolean foundUnprefixedNbt = false;
            for (AxiomBlueprint427.PieceTemplate p : lib.pieces) {
                if (p.key.contains("АстролябияТест")) {
                    astroCount++;
                    astroNbt = p.isNbt;
                }
                if (p.key.contains("СвояНБТ")) {
                    foundUnprefixedNbt = p.isNbt;
                }
            }
            check(astroCount == 1 && astroNbt, ".nbt replaces same-key .bp cleanly without #nbt duplicate");
            check(foundUnprefixedNbt, "unprefixed .nbt retained in auto->biomes mode");
            check(lib.nbtCount == 2 && lib.nbtInPoolCount == 2 && lib.nbtFilteredCount == 0,
                    "active vs total .nbt counts tracked accurately");
            check(lib.unusedNbtInOtherDirs == 1 && lib.unusedNbtOtherDirHint.endsWith("schematics"),
                    "detects unused .nbt sitting in schematics/ when axiom folder is active");
            check(!lib.connectorsForTheme(DungeonBlueprints427.THEME_INFERNAL).isEmpty()
                    && lib.connectorsForTheme(DungeonBlueprints427.THEME_INFERNAL)
                            .stream().anyMatch(p -> p.key.contains("СвояНБТ")),
                    "unprefixed .nbt routed into deep infernal biome");

            int astroSpawns = 0;
            int customSpawns = 0;
            for (int s = 1; s <= 12; s++) {
                DungeonGenerator427.Plan plan = DungeonGenerator427.generate(lib, 0, 64, 0, s * 997L + 17L);
                for (DungeonGenerator427.PlannedPiece pp : plan.pieces) {
                    if (pp.pieceKey.contains("АстролябияТест")) astroSpawns++;
                    if (pp.pieceKey.contains("СвояНБТ")) customSpawns++;
                }
            }
            check(astroSpawns >= 8 && customSpawns >= 8,
                    "first-placement .nbt priority ensures high spawn rate: astro=" + astroSpawns
                    + "/12, custom=" + customSpawns + "/12");
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
            "dev.settledlands.dungeon.DungeonGenerator427$PlacementAttempt",
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

    private static final class MockBlockEntity extends BlockEntity {
        final MockLevel lvl;
        MockBlockEntity(MockLevel lvl) { this.lvl = lvl; }
        @Override public void setChanged() { lvl.beSetChangedCalls++; }
    }

    private static final class MockLevel extends ServerLevel {
        final ResourceKey<Level> dim;
        final MinecraftServer srv;
        final DimensionDataStorage storage = new MockStorage();
        final Map<String, BlockState> placedStates = new HashMap<>();
        final List<String> placementOrder = new ArrayList<>();
        final List<ServerPlayer> playersList = new ArrayList<>();
        boolean strictDoorPhysics = false;
        boolean blocksPerTickTestMode = false;
        boolean createMockBlockEntities = false;
        boolean requiredFlagsSeen = false;
        int droppedDoors = 0;
        int beSetChangedCalls = 0;
        int sendBlockUpdatedCalls = 0;

        MockLevel(ResourceKey<Level> dim, MinecraftServer srv) {
            this.dim = dim;
            this.srv = srv;
        }

        int firstOrderIndex(String blockId) {
            for (int i = 0; i < placementOrder.size(); i++) {
                if (blockId.equals(placementOrder.get(i))) return i;
            }
            return -1;
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

        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            if (!createMockBlockEntities || pos == null) return null;
            BlockState bs = getBlockState(pos);
            if (bs instanceof MockBlockState mbs && (mbs.blockId.startsWith("create:") || "minecraft:chest".equals(mbs.blockId))) {
                return new MockBlockEntity(this);
            }
            return null;
        }

        @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
            sendBlockUpdatedCalls++;
        }

        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) {
            if ((flags & 16) != 0 && (flags & 32) != 0) {
                requiredFlagsSeen = true;
            }
            if (state instanceof MockBlockState mbs) {
                placementOrder.add(mbs.blockId);
            }
            if (strictDoorPhysics) {
                if ((flags & 16) == 0 || (flags & 32) == 0) {
                    droppedDoors++;
                }
                if (state instanceof MockBlockState mbs) {
                    if ("valhelsia_structures:dungeon_door".equals(mbs.blockId)) {
                        String half = mbs.appliedProps.get("half");
                        BlockState below = placedStates.get(pos.getX() + "," + (pos.getY() - 1) + "," + pos.getZ());
                        if ("lower".equals(half)) {
                            if (!(below instanceof MockBlockState bbs) || bbs.isAir()
                                    || bbs.blockId.contains("door")) {
                                droppedDoors++;
                                return false;
                            }
                        } else if ("upper".equals(half)) {
                            if (!(below instanceof MockBlockState bbs)
                                    || !"valhelsia_structures:dungeon_door".equals(bbs.blockId)
                                    || !"lower".equals(bbs.appliedProps.get("half"))) {
                                droppedDoors++;
                                return false;
                            }
                        }
                    } else if (!mbs.blockId.contains("lantern") && !mbs.isAir()) {
                        // Structural block must never be placed adjacent to an already-placed door
                        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
                        for (int[] d : dirs) {
                            BlockState nb = placedStates.get((pos.getX()+d[0]) + "," + (pos.getY()+d[1]) + "," + (pos.getZ()+d[2]));
                            if (nb instanceof MockBlockState nmbs && nmbs.blockId.contains("dungeon_door")) {
                                droppedDoors++;
                            }
                        }
                    }
                }
            }
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
