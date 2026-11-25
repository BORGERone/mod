package dev.settledlands.dungeon;

import dev.settledlands.chest.LootPoolManager436;
import dev.settledlands.npc.NpcGraphAnchor420;
import dev.settledlands.npc.NpcGraphAnchorCore420;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 425 / 427: loader and deterministic rotated/chunked placer for both the bundled
 * {@code start.slbp} fallback and externally loaded Axiom {@code .bp} templates.
 *
 * <p>Hides schematic seam markers ({@code minecraft:lapis_block} and {@code minecraft:redstone_block})
 * by swapping them to {@code doorwayBlock} (default {@code minecraft:nether_bricks}), rotates block
 * coordinates, blockstate properties ({@code facing}, {@code axis}, {@code rotation},
 * {@code north/east/south/west}, rail {@code shape}), block entities, and entities cleanly across
 * 0/90/180/270-degree turns, and supports sliced tick-budgeted placement.</p>
 */
public final class DungeonTemplate425 {

    private static final Logger LOG = LoggerFactory.getLogger("settledlands");

    public static final String START_RESOURCE = "/data/settledlands/dungeon/start.slbp";
    public static final String DOORWAY_REPLACEMENT = "minecraft:nether_bricks";
    /** UPDATE_CLIENTS (2) | UPDATE_KNOWN_SHAPE (16) | UPDATE_SUPPRESS_DROPS (32) = 50. */
    public static final int PLACE_FLAGS = 2 | 16 | 32;

    /** Safe entrance inside {@code старт.bp} facing West (-X) into the chamber from the lapis sill. */
    public static final double SPAWN_OFFSET_X = 10.5D;
    public static final double SPAWN_OFFSET_Y = -8.0D;
    public static final double SPAWN_OFFSET_Z = 0.5D;
    public static final float SPAWN_YAW = 90.0F;
    public static final float SPAWN_PITCH = 0.0F;

    private static volatile StartTemplate cachedStart;

    private DungeonTemplate425() {}

    public static final class PaletteEntry {
        public final String id;
        public final Map<String, String> properties;

        public PaletteEntry(String id, Map<String, String> properties) {
            this.id = id;
            this.properties = properties == null ? Collections.emptyMap() : properties;
        }
    }

    public static final class BlockEntitySpec {
        public final String id;
        public final int x;
        public final int y;
        public final int z;
        public final CompoundTag nbt;
        public final DungeonNbt431.NbtCompound rawNbt;

        public BlockEntitySpec(String id, int x, int y, int z, CompoundTag nbt) {
            this(id, x, y, z, nbt, null);
        }

        public BlockEntitySpec(String id, int x, int y, int z, CompoundTag nbt,
                DungeonNbt431.NbtCompound rawNbt) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.nbt = nbt;
            this.rawNbt = rawNbt;
        }
    }

    public static final class EntitySpec {
        public final String id;
        public final double dx;
        public final double dy;
        public final double dz;
        public final float yaw;
        public final float pitch;
        public final CompoundTag nbt;
        public final DungeonNbt431.NbtCompound rawNbt;

        public EntitySpec(String id, double dx, double dy, double dz, float yaw, float pitch, CompoundTag nbt) {
            this(id, dx, dy, dz, yaw, pitch, nbt, null);
        }

        public EntitySpec(String id, double dx, double dy, double dz, float yaw, float pitch,
                CompoundTag nbt, DungeonNbt431.NbtCompound rawNbt) {
            this.id = id;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.yaw = yaw;
            this.pitch = pitch;
            this.nbt = nbt;
            this.rawNbt = rawNbt;
        }
    }

    public static final class StartTemplate {
        public final List<PaletteEntry> palette;
        public final int[] blocks;
        public final List<BlockEntitySpec> blockEntities;
        public final List<EntitySpec> entities;
        public final int[] oldSchemaOrigin;

        public StartTemplate(List<PaletteEntry> palette, int[] blocks,
                List<BlockEntitySpec> blockEntities, List<EntitySpec> entities) {
            this(palette, blocks, blockEntities, entities, null);
        }

        public StartTemplate(List<PaletteEntry> palette, int[] blocks,
                List<BlockEntitySpec> blockEntities, List<EntitySpec> entities, int[] oldSchemaOrigin) {
            this.palette = Collections.unmodifiableList(palette);
            this.blocks = blocks == null ? new int[0] : blocks;
            this.blockEntities = Collections.unmodifiableList(blockEntities);
            this.entities = Collections.unmodifiableList(entities);
            if (oldSchemaOrigin == null) {
                this.oldSchemaOrigin = null;
            } else {
                this.oldSchemaOrigin = new int[oldSchemaOrigin.length];
                System.arraycopy(oldSchemaOrigin, 0, this.oldSchemaOrigin, 0, oldSchemaOrigin.length);
            }
        }

        public int blockCount() {
            return blocks.length / 4;
        }
    }

    public static final class PlacementStats {
        public int blocksPlaced;
        public int blocksSkipped;
        public int blockEntitiesLoaded;
        public int blockEntitiesSkipped;
        public int entitiesSpawned;
        public int entitiesSkipped;
    }

    public static StartTemplate loadStart() {
        StartTemplate tpl = cachedStart;
        if (tpl != null) return tpl;
        synchronized (DungeonTemplate425.class) {
            if (cachedStart != null) return cachedStart;
            try (InputStream in = DungeonTemplate425.class.getResourceAsStream(START_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("Missing dungeon template resource: " + START_RESOURCE);
                }
                CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
                cachedStart = parseTemplate(root);
                return cachedStart;
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to load " + START_RESOURCE + ": " + ex.getMessage(), ex);
            }
        }
    }

    public static StartTemplate parseTemplate(CompoundTag root) {
        if (root == null) {
            return new StartTemplate(Collections.emptyList(), new int[0],
                    Collections.emptyList(), Collections.emptyList());
        }

        List<PaletteEntry> palette = new ArrayList<>();
        ListTag palList = root.getList("Palette", 10);
        for (int i = 0; i < palList.size(); i++) {
            CompoundTag item = palList.getCompound(i);
            String name = item.getString("Name");
            CompoundTag propsTag = item.getCompound("Props");
            Map<String, String> props = new LinkedHashMap<>();
            for (String key : propsTag.getAllKeys()) {
                props.put(key, propsTag.getString(key));
            }
            palette.add(new PaletteEntry(name, props));
        }

        int[] blocks = root.getIntArray("Blocks");

        List<BlockEntitySpec> blockEntities = new ArrayList<>();
        ListTag beList = root.getList("BlockEntities", 10);
        for (int i = 0; i < beList.size(); i++) {
            CompoundTag be = beList.getCompound(i);
            String id = be.getString("id");
            int x = be.getInt("x");
            int y = be.getInt("y");
            int z = be.getInt("z");
            CompoundTag nbt = be.contains("nbt", 10) ? be.getCompound("nbt").copy() : new CompoundTag();
            blockEntities.add(new BlockEntitySpec(id, x, y, z, nbt));
        }

        List<EntitySpec> entities = new ArrayList<>();
        ListTag entList = root.getList("Entities", 10);
        for (int i = 0; i < entList.size(); i++) {
            CompoundTag ent = entList.getCompound(i);
            String id = ent.getString("id");
            double dx = ent.getDouble("dx");
            double dy = ent.getDouble("dy");
            double dz = ent.getDouble("dz");
            float yaw = ent.getFloat("yaw");
            float pitch = ent.getFloat("pitch");
            CompoundTag nbt = ent.contains("nbt", 10) ? ent.getCompound("nbt").copy() : new CompoundTag();
            entities.add(new EntitySpec(id, dx, dy, dz, yaw, pitch, nbt));
        }

        return new StartTemplate(palette, blocks, blockEntities, entities);
    }

    public static String remapBlockId(String rawId) {
        return remapBlockId(rawId, DOORWAY_REPLACEMENT);
    }

    public static String remapBlockId(String rawId, String doorwayReplacement) {
        if ("minecraft:lapis_block".equals(rawId) || "minecraft:redstone_block".equals(rawId)) {
            return (doorwayReplacement == null || doorwayReplacement.isEmpty())
                    ? DOORWAY_REPLACEMENT : doorwayReplacement;
        }
        return rawId;
    }

    public static Map<String, String> rotateProps(Map<String, String> props, int turns) {
        return rotateProps(props, turns, null);
    }

    public static Map<String, String> rotateProps(Map<String, String> props, int turns, String blockId) {
        int t = turns & 3;
        if (props == null || props.isEmpty() || t == 0) {
            return props == null ? Collections.emptyMap() : props;
        }
        Map<String, String> out = new LinkedHashMap<>();
        String cN = props.get("north");
        String cE = props.get("east");
        String cS = props.get("south");
        String cW = props.get("west");
        String gN = props.get("glueable_north");
        String gE = props.get("glueable_east");
        String gS = props.get("glueable_south");
        String gW = props.get("glueable_west");

        for (Map.Entry<String, String> e : props.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if ("facing".equals(k) || "target".equals(k) || "direction".equals(k)) {
                out.put(k, rotateFacing(v, t));
            } else if ("axis".equals(k)) {
                out.put(k, rotateAxis(v, t));
            } else if ("rotation".equals(k)) {
                out.put(k, rotateInt16(v, t));
            } else if ("shape".equals(k)) {
                out.put(k, rotateShape(v, t));
            } else if ("axis_along_first".equals(k) || "connected_along_first_coordinate".equals(k)) {
                out.put(k, (t & 1) == 1 ? flipBoolString(v) : v);
            } else if ("north".equals(k) || "east".equals(k) || "south".equals(k) || "west".equals(k)
                    || "glueable_north".equals(k) || "glueable_east".equals(k)
                    || "glueable_south".equals(k) || "glueable_west".equals(k)) {
                // Reassigned below so cardinal connections map cleanly without losing order
                out.put(k, v);
            } else {
                out.put(k, v);
            }
        }
        if (cN != null || cE != null || cS != null || cW != null) {
            String[] ring = new String[] { cN, cE, cS, cW };
            String[] names = new String[] { "north", "east", "south", "west" };
            for (int i = 0; i < 4; i++) {
                String val = ring[i];
                if (val != null) {
                    out.put(names[(i + t) & 3], val);
                }
            }
        }
        if (gN != null || gE != null || gS != null || gW != null) {
            String[] ring = new String[] { gN, gE, gS, gW };
            String[] names = new String[] { "glueable_north", "glueable_east", "glueable_south", "glueable_west" };
            for (int i = 0; i < 4; i++) {
                String val = ring[i];
                if (val != null) {
                    out.put(names[(i + t) & 3], val);
                }
            }
        }

        // Create EncasedCogwheel: top_shaft <-> bottom_shaft swap when horizontal axis flips sign
        if (props.containsKey("top_shaft") && props.containsKey("bottom_shaft")) {
            String ax = props.get("axis");
            boolean swapShafts = ("x".equals(ax) && (t == 2 || t == 3))
                    || ("z".equals(ax) && (t == 1 || t == 2));
            if (swapShafts) {
                out.put("top_shaft", props.get("bottom_shaft"));
                out.put("bottom_shaft", props.get("top_shaft"));
            }
        }

        // Create Belt (slope=vertical) and ChainDrive: swap part start <-> end when axis sign flips
        String part = props.get("part");
        if ("start".equals(part) || "end".equals(part)) {
            boolean flipPart = false;
            if ("vertical".equals(props.get("slope"))) {
                String oldFacing = props.get("facing");
                String newFacing = out.get("facing");
                flipPart = isPositiveHorizontalDir(oldFacing) != isPositiveHorizontalDir(newFacing);
            } else if (props.containsKey("connected_along_first_coordinate")
                    || (blockId != null && blockId.contains("chain"))) {
                String ax = props.get("axis");
                boolean connFirst = "true".equals(props.get("connected_along_first_coordinate"));
                String connAxis = connFirst
                        ? ("x".equals(ax) ? "y" : "x")
                        : ("z".equals(ax) ? "y" : "z");
                flipPart = ("x".equals(connAxis) && (t == 2 || t == 3))
                        || ("z".equals(connAxis) && (t == 1 || t == 2));
            }
            if (flipPart) {
                out.put("part", "start".equals(part) ? "end" : "start");
            }
        }

        return out;
    }

    private static String flipBoolString(String v) {
        if ("true".equals(v)) return "false";
        if ("false".equals(v)) return "true";
        return v;
    }

    private static boolean isPositiveHorizontalDir(String dir) {
        return "east".equals(dir) || "south".equals(dir);
    }

    private static String rotateFacing(String v, int turns) {
        if (v == null) return null;
        int dir = switch (v.toLowerCase(Locale.ROOT)) {
            case "north" -> 0;
            case "east" -> 1;
            case "south" -> 2;
            case "west" -> 3;
            default -> -1;
        };
        if (dir < 0) return v;
        return switch ((dir + turns) & 3) {
            case 0 -> "north";
            case 1 -> "east";
            case 2 -> "south";
            default -> "west";
        };
    }

    private static String rotateAxis(String v, int turns) {
        if (v == null || (turns & 1) == 0) return v;
        if ("x".equals(v)) return "z";
        if ("z".equals(v)) return "x";
        return v;
    }

    private static String rotateInt16(String v, int turns) {
        try {
            int r = Integer.parseInt(v);
            return Integer.toString((r + 4 * turns) & 15);
        } catch (NumberFormatException ignored) {
            return v;
        }
    }

    private static String rotateShape(String v, int turns) {
        if (v == null) return null;
        if ("north_south".equals(v)) return (turns & 1) == 1 ? "east_west" : "north_south";
        if ("east_west".equals(v)) return (turns & 1) == 1 ? "north_south" : "east_west";
        if (v.startsWith("ascending_")) {
            String dir = rotateFacing(v.substring("ascending_".length()), turns);
            return "ascending_" + dir;
        }
        int corner = switch (v) {
            case "north_east" -> 0;
            case "south_east" -> 1;
            case "south_west" -> 2;
            case "north_west" -> 3;
            default -> -1;
        };
        if (corner >= 0) {
            return switch ((corner + turns) & 3) {
                case 0 -> "north_east";
                case 1 -> "south_east";
                case 2 -> "south_west";
                default -> "north_west";
            };
        }
        return v;
    }

    public static BlockState resolveBlockState(PaletteEntry entry) {
        return resolveBlockState(entry, 0, DOORWAY_REPLACEMENT);
    }

    public static BlockState resolveBlockState(PaletteEntry entry, int turns, String doorwayReplacement) {
        if (entry == null || entry.id == null || entry.id.isEmpty()) return null;
        String mappedId = remapBlockId(entry.id, doorwayReplacement);
        ResourceLocation rl = ResourceLocation.tryParse(mappedId);
        if (rl == null || BuiltInRegistries.BLOCK == null) return null;

        Block block = BuiltInRegistries.BLOCK.get(rl);
        if (block == null) return null;
        ResourceLocation actualKey = BuiltInRegistries.BLOCK.getKey(block);
        if (actualKey == null || !rl.equals(actualKey)) {
            return null;
        }

        BlockState state = block.defaultBlockState();
        if (state == null || entry.properties.isEmpty() || !mappedId.equals(entry.id)) {
            return state;
        }

        StateDefinition<Block, BlockState> def = block.getStateDefinition();
        if (def == null) return state;

        int t = turns & 3;
        if (t != 0 && (mappedId.startsWith("create:") || !mappedId.startsWith("minecraft:"))
                && CreateCompat431.isCreateAvailable()) {
            BlockState unrotated = state;
            for (Map.Entry<String, String> propEntry : entry.properties.entrySet()) {
                Property<?> prop = def.getProperty(propEntry.getKey());
                if (prop != null) {
                    unrotated = applyProperty(unrotated, prop, propEntry.getValue());
                }
            }
            BlockState transformed = CreateCompat431.transformBlockState(unrotated, t);
            if (transformed != null) {
                return transformed;
            }
        }

        Map<String, String> rotatedProps = rotateProps(entry.properties, t, mappedId);
        for (Map.Entry<String, String> propEntry : rotatedProps.entrySet()) {
            Property<?> prop = def.getProperty(propEntry.getKey());
            if (prop != null) {
                state = applyProperty(state, prop, propEntry.getValue());
            }
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperty(BlockState state, Property prop, String rawValue) {
        try {
            Optional<?> parsed = prop.getValue(rawValue);
            if (parsed != null && parsed.isPresent() && parsed.get() instanceof Comparable<?> value) {
                Object updated = state.setValue(prop, value);
                if (updated instanceof BlockState bs) {
                    return bs;
                }
            }
        } catch (Throwable ignored) {
        }
        return state;
    }

    public static PlacementStats place(ServerLevel level, StartTemplate tpl, int originX, int originY, int originZ) {
        PlacementStats stats = new PlacementStats();
        if (level == null || tpl == null) return stats;
        placeSlice(level, tpl, originX, originY, originZ, 0, DOORWAY_REPLACEMENT, 0, tpl.blockCount(), stats);
        return stats;
    }

    public static int placeSlice(
            ServerLevel level,
            StartTemplate tpl,
            int originX,
            int originY,
            int originZ,
            int turns,
            String doorwayReplacement,
            int fromBlock,
            int maxBlocks,
            PlacementStats stats) {
        if (level == null || tpl == null) return fromBlock;
        int total = tpl.blockCount();
        int start = Math.max(0, fromBlock);
        int end = Math.min(total, start + Math.max(0, maxBlocks));
        int t = turns & 3;

        BlockState[] resolved = new BlockState[tpl.palette.size()];
        for (int i = 0; i < tpl.palette.size(); i++) {
            resolved[i] = resolveBlockState(tpl.palette.get(i), t, doorwayReplacement);
        }

        int[] raw = tpl.blocks;
        // Never split multi-block doors (e.g. valhelsia_structures:dungeon_door) across server ticks.
        if (end > start && end < total) {
            int lastPal = raw[(end - 1) * 4 + 3];
            if (lastPal >= 0 && lastPal < tpl.palette.size()
                    && AxiomBlueprint427.isDoorLike(tpl.palette.get(lastPal).id)) {
                int extra = 0;
                int prevX = raw[(end - 1) * 4];
                int prevZ = raw[(end - 1) * 4 + 2];
                while (end < total && extra < 16) {
                    int nextPal = raw[end * 4 + 3];
                    if (nextPal < 0 || nextPal >= tpl.palette.size()
                            || !AxiomBlueprint427.isDoorLike(tpl.palette.get(nextPal).id)) {
                        break;
                    }
                    int nx = raw[end * 4];
                    int nz = raw[end * 4 + 2];
                    if (Math.abs(nx - prevX) > 4 || Math.abs(nz - prevZ) > 4) {
                        break;
                    }
                    prevX = nx;
                    prevZ = nz;
                    end++;
                    extra++;
                }
            }
        }

        for (int bi = start; bi < end; bi++) {
            int i = bi * 4;
            int palIdx = raw[i + 3];
            BlockState state = (palIdx >= 0 && palIdx < resolved.length) ? resolved[palIdx] : null;
            if (state == null) {
                if (stats != null) stats.blocksSkipped++;
                continue;
            }
            int rx = DungeonGenerator427.rotX(raw[i], raw[i + 2], t);
            int rz = DungeonGenerator427.rotZ(raw[i], raw[i + 2], t);
            BlockPos pos = new BlockPos(originX + rx, originY + raw[i + 1], originZ + rz);
            ((Level) level).setBlock(pos, state, PLACE_FLAGS);
            if (stats != null) stats.blocksPlaced++;
        }

        if (end >= total && start < total) {
            placeBlockEntitiesAndEntities(level, tpl, originX, originY, originZ, t, stats);
        } else if (total == 0 && start == 0) {
            placeBlockEntitiesAndEntities(level, tpl, originX, originY, originZ, t, stats);
        }
        return end;
    }

    private static void placeBlockEntitiesAndEntities(
            ServerLevel level,
            StartTemplate tpl,
            int originX,
            int originY,
            int originZ,
            int turns,
            PlacementStats stats) {
        List<BlockPos> createPositions = new ArrayList<>();
        List<Entity> spawnedAnchors = new ArrayList<>();
        for (BlockEntitySpec beSpec : tpl.blockEntities) {
            int rx = DungeonGenerator427.rotX(beSpec.x, beSpec.z, turns);
            int rz = DungeonGenerator427.rotZ(beSpec.x, beSpec.z, turns);
            BlockPos pos = new BlockPos(originX + rx, originY + beSpec.y, originZ + rz);
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                if (stats != null) stats.blockEntitiesSkipped++;
                continue;
            }
            try {
                CompoundTag nbt;
                if (beSpec.rawNbt != null) {
                    DungeonNbt431.NbtCompound transformed = DungeonNbt431.transformBlockEntityNbt(
                            beSpec.id, beSpec.rawNbt, originX, originY, originZ,
                            pos.getX(), pos.getY(), pos.getZ(), turns, tpl.oldSchemaOrigin);
                    nbt = DungeonNbt431.toMinecraftTag(transformed);
                } else {
                    nbt = beSpec.nbt == null ? new CompoundTag() : beSpec.nbt.copy();
                    if (beSpec.id != null && !beSpec.id.isEmpty()) {
                        nbt.putString("id", beSpec.id);
                    }
                    nbt.putInt("x", pos.getX());
                    nbt.putInt("y", pos.getY());
                    nbt.putInt("z", pos.getZ());
                }
                be.loadWithComponents(nbt, ((Level) level).registryAccess());
                be.setChanged();
                if (CreateCompat431.isCreateBlockEntity(be, beSpec.id)) {
                    createPositions.add(pos);
                }
                if (stats != null) stats.blockEntitiesLoaded++;
            } catch (Throwable ex) {
                if (stats != null) stats.blockEntitiesSkipped++;
                LOG.warn("settledlands: skipped block entity " + beSpec.id + " at " + pos + ": " + ex.getMessage());
            }
        }

        for (EntitySpec eSpec : tpl.entities) {
            ResourceLocation rl = eSpec.id == null ? null : ResourceLocation.tryParse(eSpec.id);
            EntityType type = (rl == null || BuiltInRegistries.ENTITY_TYPE == null)
                    ? null : BuiltInRegistries.ENTITY_TYPE.get(rl);
            ResourceLocation actualKey = type == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (type == null || actualKey == null || !rl.equals(actualKey)) {
                if (stats != null) stats.entitiesSkipped++;
                LOG.warn("settledlands: skipped unknown/unregistered entity type '" + eSpec.id
                        + "' (resolved=" + actualKey + ")");
                continue;
            }
            try {
                Entity entity = type.create((Level) level);
                if (entity == null) {
                    if (stats != null) stats.entitiesSkipped++;
                    LOG.warn("settledlands: EntityType.create returned null for '" + eSpec.id + "'");
                    continue;
                }
                DungeonNbt431.TransformedEntity te = DungeonNbt431.transformEntityPlacement(
                        eSpec, originX, originY, originZ, turns);
                if (te.moveBeforeLoad) {
                    // SuperGlueEntity.readAdditionalSaveData shifts From/To relative to position(),
                    // and ControlledContraptionEntity.readAdditional adds ControllerRelative to blockPosition();
                    // moveTo after load refreshes contraption AABB from Contraption.bounds.
                    entity.moveTo(te.wx, te.wy, te.wz);
                    if (te.mcNbt != null && !te.mcNbt.isEmpty()) {
                        entity.load(te.mcNbt);
                    }
                    entity.moveTo(te.wx, te.wy, te.wz);
                } else {
                    if (te.mcNbt != null && !te.mcNbt.isEmpty()) {
                        entity.load(te.mcNbt);
                    }
                    entity.moveTo(te.wx, te.wy, te.wz);
                }
                entity.setYRot(te.yaw);
                entity.setXRot(te.pitch);
                boolean added = level.addFreshEntity(entity);
                CreateCompat431.attachContraption(level, entity);
                BlockPos ePos = new BlockPos((int) Math.floor(te.wx), (int) Math.floor(te.wy), (int) Math.floor(te.wz));
                boolean ticking = level.isPositionEntityTicking(ePos);
                if (!added || entity.isRemoved()) {
                    if (stats != null) stats.entitiesSkipped++;
                    if ("settledlands:guide_trigger".equals(eSpec.id)
                            && NpcGraphAnchorCore420.hasGraphs(NpcGraphAnchor420.data(entity))) {
                        spawnedAnchors.add(entity);
                    }
                    LOG.warn("settledlands: entity " + eSpec.id + " at " + ePos
                            + " was rejected/removed on spawn (added=" + added
                            + ", removed=" + entity.isRemoved()
                            + ", ticking=" + ticking + ")");
                } else {
                    if (stats != null) stats.entitiesSpawned++;
                    if (NpcGraphAnchorCore420.hasGraphs(NpcGraphAnchor420.data(entity))) {
                        spawnedAnchors.add(entity);
                    }
                    if (eSpec.id != null && eSpec.id.contains("contraption")) {
                        LOG.info("settledlands: spawned " + eSpec.id + " at " + ePos
                                + " (exact=" + te.wx + "," + te.wy + "," + te.wz
                                + ") turns=" + turns + " ticking=" + ticking
                                + " createCompat=" + CreateCompat431.isCreateAvailable());
                    }
                }
            } catch (Throwable ex) {
                if (stats != null) stats.entitiesSkipped++;
                LOG.warn("settledlands: skipped entity " + eSpec.id + ": " + ex.getMessage());
            }
        }

        if (!createPositions.isEmpty()) {
            CreateCompat431.activateCreateRoom(level, createPositions);
        }
        if (!spawnedAnchors.isEmpty() && DungeonManager425.isInDungeon(level)) {
            LootPoolManager436.onDungeonRoomPlaced(level, spawnedAnchors, turns);
        }
    }

    public static int placeSeal(ServerLevel level, int wx, int wy, int wz, int outDir, String doorwayBlock) {
        if (level == null) return 0;
        String blockId = (doorwayBlock == null || doorwayBlock.isEmpty()) ? DOORWAY_REPLACEMENT : doorwayBlock;
        BlockState state = resolveBlockState(new PaletteEntry(blockId, Collections.emptyMap()), 0, blockId);
        if (state == null) return 0;
        int d = outDir & 3;
        int tx = -AxiomBlueprint427.DZ[d];
        int tz = AxiomBlueprint427.DX[d];
        int placed = 0;
        for (int s = -1; s <= 1; s++) {
            for (int dy = 2; dy <= 6; dy++) {
                BlockPos pos = new BlockPos(wx + tx * s, wy + dy, wz + tz * s);
                BlockState existing = ((Level) level).getBlockState(pos);
                if (existing == null || existing.isAir()) {
                    ((Level) level).setBlock(pos, state, PLACE_FLAGS);
                    placed++;
                }
            }
        }
        return placed;
    }
}
