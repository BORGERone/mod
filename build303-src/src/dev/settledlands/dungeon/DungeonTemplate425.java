package dev.settledlands.dungeon;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Build 425: loader and deterministic placer for the packed {@code start.slbp} blueprint
 * (converted from Axiom {@code старт.bp}).
 *
 * <p>Hides schematic seam markers ({@code minecraft:lapis_block} and {@code minecraft:redstone_block})
 * by swapping them to {@code minecraft:nether_bricks}, gracefully skips optional external mod blocks
 * or entities that are not installed in the running pack, and offsets block entities and entities
 * relative to the dungeon instance grid origin.</p>
 */
public final class DungeonTemplate425 {

    private static final Logger LOG = LoggerFactory.getLogger("settledlands");

    public static final String START_RESOURCE = "/data/settledlands/dungeon/start.slbp";
    public static final String DOORWAY_REPLACEMENT = "minecraft:nether_bricks";

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

        public BlockEntitySpec(String id, int x, int y, int z, CompoundTag nbt) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.nbt = nbt;
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

        public EntitySpec(String id, double dx, double dy, double dz, float yaw, float pitch, CompoundTag nbt) {
            this.id = id;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.yaw = yaw;
            this.pitch = pitch;
            this.nbt = nbt;
        }
    }

    public static final class StartTemplate {
        public final List<PaletteEntry> palette;
        public final int[] blocks;
        public final List<BlockEntitySpec> blockEntities;
        public final List<EntitySpec> entities;

        public StartTemplate(List<PaletteEntry> palette, int[] blocks,
                List<BlockEntitySpec> blockEntities, List<EntitySpec> entities) {
            this.palette = Collections.unmodifiableList(palette);
            this.blocks = blocks == null ? new int[0] : blocks;
            this.blockEntities = Collections.unmodifiableList(blockEntities);
            this.entities = Collections.unmodifiableList(entities);
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
        if ("minecraft:lapis_block".equals(rawId) || "minecraft:redstone_block".equals(rawId)) {
            return DOORWAY_REPLACEMENT;
        }
        return rawId;
    }

    public static BlockState resolveBlockState(PaletteEntry entry) {
        if (entry == null || entry.id == null || entry.id.isEmpty()) return null;
        String mappedId = remapBlockId(entry.id);
        ResourceLocation rl = ResourceLocation.tryParse(mappedId);
        if (rl == null) return null;

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

        for (Map.Entry<String, String> propEntry : entry.properties.entrySet()) {
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

        BlockState[] resolved = new BlockState[tpl.palette.size()];
        for (int i = 0; i < tpl.palette.size(); i++) {
            resolved[i] = resolveBlockState(tpl.palette.get(i));
        }

        int[] raw = tpl.blocks;
        for (int i = 0; i + 3 < raw.length; i += 4) {
            int palIdx = raw[i + 3];
            BlockState state = (palIdx >= 0 && palIdx < resolved.length) ? resolved[palIdx] : null;
            if (state == null) {
                stats.blocksSkipped++;
                continue;
            }
            BlockPos pos = new BlockPos(originX + raw[i], originY + raw[i + 1], originZ + raw[i + 2]);
            ((Level) level).setBlock(pos, state, 2);
            stats.blocksPlaced++;
        }

        for (BlockEntitySpec beSpec : tpl.blockEntities) {
            BlockPos pos = new BlockPos(originX + beSpec.x, originY + beSpec.y, originZ + beSpec.z);
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                stats.blockEntitiesSkipped++;
                continue;
            }
            try {
                CompoundTag nbt = beSpec.nbt == null ? new CompoundTag() : beSpec.nbt.copy();
                if (beSpec.id != null && !beSpec.id.isEmpty()) {
                    nbt.putString("id", beSpec.id);
                }
                nbt.putInt("x", pos.getX());
                nbt.putInt("y", pos.getY());
                nbt.putInt("z", pos.getZ());
                be.loadWithComponents(nbt, ((Level) level).registryAccess());
                be.setChanged();
                stats.blockEntitiesLoaded++;
            } catch (Throwable ex) {
                stats.blockEntitiesSkipped++;
                LOG.warn("settledlands: skipped block entity " + beSpec.id + " at " + pos + ": " + ex.getMessage());
            }
        }

        for (EntitySpec eSpec : tpl.entities) {
            ResourceLocation rl = eSpec.id == null ? null : ResourceLocation.tryParse(eSpec.id);
            EntityType type = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.get(rl);
            ResourceLocation actualKey = type == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (type == null || actualKey == null || !rl.equals(actualKey)) {
                stats.entitiesSkipped++;
                continue;
            }
            try {
                Entity entity = type.create((Level) level);
                if (entity == null) {
                    stats.entitiesSkipped++;
                    continue;
                }
                CompoundTag nbt = eSpec.nbt == null ? new CompoundTag() : eSpec.nbt.copy();
                nbt.remove("UUID");
                nbt.remove("Pos");
                nbt.remove("neoforge:attachments");
                if (!nbt.isEmpty()) {
                    entity.load(nbt);
                }
                entity.moveTo(originX + eSpec.dx, originY + eSpec.dy, originZ + eSpec.dz);
                entity.setYRot(eSpec.yaw);
                entity.setXRot(eSpec.pitch);
                level.addFreshEntity(entity);
                stats.entitiesSpawned++;
            } catch (Throwable ex) {
                stats.entitiesSkipped++;
                LOG.warn("settledlands: skipped entity " + eSpec.id + ": " + ex.getMessage());
            }
        }

        return stats;
    }
}
