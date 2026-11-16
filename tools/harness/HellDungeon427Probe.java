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
            writeRoomBp(axiomDir.resolve("а Тупик.bp"), "а Тупик",
                    List.of("dungeon", "infernal"), 0, 10, false, true, false, false, false);

            DungeonBlueprints427.Library autoLib = DungeonBlueprints427.load(cfg);
            check(autoLib.sourceDir.equals(tmp.resolve("config/axiom/blueprints")),
                    "auto-discovered config/axiom/blueprints when settledlands/blueprints is empty");
            check(autoLib.skippedNoMarkers == 1, "house.bp without lapis_block skipped cleanly");
            check(autoLib.failedParse == 0, "0 parse failures");
            check("auto->infernal".equals(autoLib.appliedFilter), "auto selected infernal among mixed themes");
            check(autoLib.pieces.size() == 2 && autoLib.deadEnds.size() == 1,
                    "filtered infernal pieces + deadEnds");

            // Now drop a custom room directly in config/settledlands/blueprints -> takes priority, auto->all
            Path customDir = tmp.resolve(DungeonConfig427.DEFAULT_BLUEPRINTS_REL);
            writeRoomBp(customDir.resolve("my_room.bp"), "my_room",
                    List.of(), 1, 10, false, true, true, false, false);
            DungeonBlueprints427.Library customLib = DungeonBlueprints427.load(cfg);
            check(customLib.sourceDir.equals(customDir), "config/settledlands/blueprints takes priority");
            check("auto->all".equals(customLib.appliedFilter) && customLib.pieces.size() == 1,
                    "single-theme custom folder loads all pieces");
            check(customLib.startPiece != null && customLib.startPiece.embeddedFallback,
                    "embedded start.slbp used when custom folder lacks старт.bp");

            // Tag filter check
            DungeonConfig427 cryptCfg = new DungeonConfig427(
                    tmp, axiomDir.toString(), "tag:crypt", "старт",
                    60, 80, 230, -52, 240, true, "minecraft:nether_bricks", 8, 4096, true);
            DungeonBlueprints427.Library cryptLib = DungeonBlueprints427.load(cryptCfg);
            check(cryptLib.pieces.size() == 1 && "г Коридор".equals(cryptLib.pieces.get(0).key),
                    "tag:crypt filter selects only crypt pieces");
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
                    60, 80, 230, -52, 240, true, "minecraft:nether_bricks", 8, 8192, true);
            DungeonBlueprints427.Library lib = DungeonBlueprints427.load(cfg);
            check(lib.totalBpFiles == 232 && lib.failedParse == 0, "all 232 real .bp parsed");
            check("auto->infernal".equals(lib.appliedFilter) && lib.pieces.size() == 48,
                    "infernal theme selected (48 pieces)");

            MockServer srv = new MockServer();
            MockLevel ow = new MockLevel(OVERWORLD_KEY, srv);
            MockLevel hd = new MockLevel(DungeonManager425.HELL_DUNGEON_LEVEL, srv);
            srv.overworld = ow;
            srv.levels.put(OVERWORLD_KEY.toString(), ow);
            srv.levels.put(DungeonManager425.HELL_DUNGEON_LEVEL.toString(), hd);

            DungeonData425 data = DungeonData425.get(srv);
            DungeonData425.Instance inst = data.getOrCreateInstance("real", 7777L, 1L);
            DungeonManager425.ensureInstanceInitialized(hd, data, inst, lib);
            check(inst.pieces.size() >= 60 && inst.pieces.size() <= 80,
                    "real infernal plan has " + inst.pieces.size() + " rooms");

            int ticks = 0;
            while (!inst.placed && ticks++ < 400) {
                DungeonManager425.tickPendingGeneration(srv, lib);
            }
            check(inst.placed, "real infernal dungeon finished in " + ticks + " ticks");
            check(hd.placedStates.size() > 150000,
                    "real infernal dungeon placed " + hd.placedStates.size() + " unique blocks");
            for (BlockState bs : hd.placedStates.values()) {
                if (bs instanceof MockBlockState mbs) {
                    check(!"minecraft:lapis_block".equals(mbs.blockId)
                            && !"minecraft:redstone_block".equals(mbs.blockId),
                            "zero raw marker blocks in real infernal build");
                }
            }
            // Verify rear arch of старт is sealed (no void behind spawn)
            check("minecraft:nether_bricks".equals(hd.placedBlockId(12, -8, 0)),
                    "real старт rear arch (12,-8,0) sealed");
        } finally {
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
        final ServerLevel curLevel;
        final double px, py, pz;

        MockPlayer(MinecraftServer srv, ServerLevel level, double x, double y, double z) {
            this.srv = srv;
            this.curLevel = level;
            this.px = x; this.py = y; this.pz = z;
        }

        @Override public MinecraftServer getServer() { return srv; }
        @Override public ServerLevel serverLevel() { return curLevel; }
        @Override public double getX() { return px; }
        @Override public double getY() { return py; }
        @Override public double getZ() { return pz; }
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
