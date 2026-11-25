import com.pockyl.diegesis.api.OpenSide;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.scene.PlaceSurface;
import com.pockyl.diegesis.api.scene.Placing;
import com.pockyl.diegesis.api.scene.PlacingOptions;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationHost;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.scene.NodeInstance;
import com.pockyl.diegesis.impl.client.scene.PlacedItem;
import com.pockyl.diegesis.impl.client.scene.PlacedShape;
import com.pockyl.diegesis.impl.client.scene.SceneInstance;
import com.pockyl.handson_furniture.station.RoomyContainer;
import dev.settledlands.chest.ChestDiegesisCompat436;
import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.ChestOpenWatch401;
import dev.settledlands.chest.DiegesisScatterClient439;
import dev.settledlands.chest.LootPool436;
import dev.settledlands.chest.LootPoolManager436;
import dev.settledlands.dungeon.DungeonManager425;
import dev.settledlands.npc.NpcDialogueGraph;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * End-to-end integration probe for Build 439:
 *   1. Dungeon LootPoolManager436 population stamps SettledLandsScatterSeed & clears stale poses;
 *   2. DiegesisScatterClient439 produces natural 3D organic placement across all surfaces of
 *      storage_cabinet, fridge (including narrow door pockets), and drawer (flatLies), with zero
 *      AABB intersections, zero wall/open-edge/ceiling violations, varied yaw/scale/lying/stacking;
 *   3. Manual furniture (seed == 0) is untouched (scatterFreeSpot returns null);
 *   4. Client -> ChestLootNet436.ScatterPoses -> Server commit validates distance, seed, poses,
 *      non-cyclic support, clears seed, saves via setChanged(), and rejects replay attacks;
 *   5. Late seed arrival re-scatters cached default poses automatically via behavior & tickWatch();
 *   6. Vanilla BlockEntity containers without Diegesis suffer no errors.
 */
public final class Build439Probe {

    private static final class MockLevel extends ServerLevel {
        final Map<BlockPos, BlockEntity> bes = new HashMap<>();
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final BlockState fallbackAir = new BlockState(null, true);

        @Override public ResourceKey<Level> dimension() { return DungeonManager425.HELL_DUNGEON_LEVEL; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return bes.get(pos); }
        @Override public BlockState getBlockState(BlockPos pos) {
            return states.getOrDefault(pos, fallbackAir);
        }
        @Override public boolean isLoaded(BlockPos pos) { return true; }
    }

    private static final class MockPlayer extends ServerPlayer {
        private final ServerLevel lvl;

        MockPlayer(ServerLevel lvl, double x, double y, double z) {
            this.lvl = lvl;
            moveTo(x, y, z);
        }

        @Override public ServerLevel serverLevel() { return lvl; }
        @Override public Level level() { return lvl; }

        @Override public double distanceToSqr(double ox, double oy, double oz) {
            double dx = getX() - ox;
            double dy = getY() - oy;
            double dz = getZ() - oz;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    static final class MockFurnitureBE extends BlockEntity
            implements Container, RoomyContainer, StationHost {
        private final BlockPos pos;
        private final ServerLevel level;
        private final ItemStack[] items;
        private final int ownSize;
        private StationState state = new StationState();
        int changedCount;

        MockFurnitureBE(BlockPos pos, ServerLevel level, int totalSize, int ownSize) {
            this.pos = pos;
            this.level = level;
            this.items = new ItemStack[totalSize];
            for (int i = 0; i < totalSize; i++) this.items[i] = ItemStack.EMPTY;
            this.ownSize = ownSize;
        }

        @Override public void setChanged() { changedCount++; }
        @Override public int handson$ownSize() { return ownSize; }
        @Override public int getContainerSize() { return items.length; }
        @Override public ItemStack getItem(int slot) { return items[slot]; }
        @Override public void setItem(int slot, ItemStack stack) {
            items[slot] = stack == null ? ItemStack.EMPTY : stack;
            if (slot >= 0 && slot < ownSize) {
                state.setItem(SlotRef.of("items", slot), items[slot]);
            }
        }
        @Override public int getMaxStackSize() { return 64; }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return true; }
        @Override public StationState stationState() { return state; }
    }

    static final class VanillaChestBE extends BlockEntity implements Container {
        private final ItemStack[] items = new ItemStack[27];

        VanillaChestBE() {
            for (int i = 0; i < items.length; i++) items[i] = ItemStack.EMPTY;
        }

        @Override public int getContainerSize() { return items.length; }
        @Override public ItemStack getItem(int slot) { return items[slot]; }
        @Override public void setItem(int slot, ItemStack stack) {
            items[slot] = stack == null ? ItemStack.EMPTY : stack;
        }
        @Override public int getMaxStackSize() { return 64; }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return true; }
    }

    private static <T> DefaultedRegistry<T> mapRegistry(Map<ResourceLocation, T> backing) {
        return new DefaultedRegistry<T>() {
            @Override public T get(ResourceLocation id) { return backing.get(id); }
            @Override public ResourceLocation getKey(T value) {
                for (Map.Entry<ResourceLocation, T> e : backing.entrySet()) {
                    if (e.getValue() == value) return e.getKey();
                }
                return null;
            }
            @Override public boolean containsKey(ResourceLocation id) { return backing.containsKey(id); }
        };
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static boolean boxesOverlap(float[] a, float[] b) {
        return a[1] > b[0] + 1e-4F && a[0] < b[1] - 1e-4F
                && a[3] > b[2] + 0.02F && a[2] < b[3] - 0.02F
                && a[5] > b[4] + 1e-4F && a[4] < b[5] - 1e-4F;
    }

    private static void verifySceneGeometry(String label, List<PlacedItem> items,
            PlacingOptions opts, boolean expectMultiSurface, boolean expectLyingPossible) {
        List<PlaceSurface> surfaces = opts.surfaces();
        float itemSize = opts.itemSize();
        List<float[]> boxes = new ArrayList<>();
        Set<Integer> usedSurfaces = new HashSet<>();
        Set<Integer> yaws = new HashSet<>();
        Set<Integer> scales = new HashSet<>();
        int lyingCount = 0;
        int standingCount = 0;
        int stackedCount = 0;
        Map<Integer, PlacedItem> bySlot = new HashMap<>();
        for (PlacedItem it : items) bySlot.put(Integer.valueOf(it.slot().index()), it);

        for (PlacedItem it : items) {
            ItemPose p = it.pose();
            assertTrue(p != null, label + ": missing pose for slot " + it.slot().index());
            assertTrue(p.scale() >= 0.45F - 1e-3F && p.scale() <= 1.35F + 1e-3F,
                    label + ": out-of-range scale " + p.scale());
            if (p.on() >= 0) {
                assertTrue(bySlot.containsKey(Integer.valueOf(p.on())),
                        label + ": invalid support slot " + p.on());
                assertTrue(p.on() != it.slot().index(), label + ": self-support cycle");
                stackedCount++;
            }

            Vector3f ext = it.shape().extents(p.rotation(), itemSize * p.scale());
            float least = Placing.leastHalf(opts, it.shape());
            float hx = Math.max(ext.x, it.shape().isFlat() ? least : 0.0F);
            float hy = Math.max(ext.y, least);
            float hz = Math.max(ext.z, it.shape().isFlat() ? least : 0.0F);
            float minX = p.x() - hx;
            float maxX = p.x() + hx;
            float minY = p.y();
            float maxY = p.y() + hy * 2.0F;
            float minZ = p.z() - hz;
            float maxZ = p.z() + hz;

            int sMatch = -1;
            for (int si = 0; si < surfaces.size(); si++) {
                PlaceSurface s = surfaces.get(si);
                float sx0 = Math.min(s.from().x(), s.to().x());
                float sx1 = Math.max(s.from().x(), s.to().x());
                float sz0 = Math.min(s.from().y(), s.to().y());
                float sz1 = Math.max(s.from().y(), s.to().y());
                if (p.x() >= sx0 - 1e-3F && p.x() <= sx1 + 1e-3F
                        && p.z() >= sz0 - 1e-3F && p.z() <= sz1 + 1e-3F
                        && p.y() >= s.height() - 1e-3F && p.y() <= s.height() + s.clearance()) {
                    sMatch = si;
                    boolean narrow = (sx1 - sx0 <= 2.6F) || (sz1 - sz0 <= 2.6F);
                    float wPad = narrow ? 0.039F : 0.099F;
                    float oPad = narrow ? 0.249F : 0.449F;
                    assertTrue(minX >= sx0 + (s.open().contains(OpenSide.RIGHT) ? oPad : wPad) - 1e-3F,
                            label + ": clipped right wall/edge x=" + minX + " vs " + sx0);
                    assertTrue(maxX <= sx1 - (s.open().contains(OpenSide.LEFT) ? oPad : wPad) + 1e-3F,
                            label + ": clipped left wall/edge x=" + maxX + " vs " + sx1);
                    assertTrue(minZ >= sz0 + (s.open().contains(OpenSide.FRONT) ? oPad : wPad) - 1e-3F,
                            label + ": clipped front wall/edge z=" + minZ + " vs " + sz0);
                    assertTrue(maxZ <= sz1 - (s.open().contains(OpenSide.BACK) ? oPad : wPad) + 1e-3F,
                            label + ": clipped back wall/edge z=" + maxZ + " vs " + sz1);
                    assertTrue(maxY <= s.height() + s.clearance() + 1e-3F,
                            label + ": clipped ceiling " + maxY + " vs " + (s.height() + s.clearance()));
                    usedSurfaces.add(Integer.valueOf(si));
                    break;
                }
            }
            assertTrue(sMatch >= 0, label + ": item placed outside all surfaces at "
                    + p.x() + "," + p.y() + "," + p.z());

            float[] box = new float[]{minX, maxX, minY, maxY, minZ, maxZ};
            for (int prev = 0; prev < boxes.size(); prev++) {
                float[] pb = boxes.get(prev);
                assertTrue(!boxesOverlap(pb, box),
                        label + ": 3D AABB collision detected between items " + prev + " and " + boxes.size()
                        + " prev=" + java.util.Arrays.toString(pb)
                        + " cur=" + java.util.Arrays.toString(box)
                        + " prevOn=" + items.get(prev).pose().on()
                        + " curOn=" + p.on());
            }
            boxes.add(box);

            Quaternionfc q = p.rotation();
            float pitch = Math.abs(q.x()) + Math.abs(q.z());
            if (pitch > 0.35F) lyingCount++;
            else standingCount++;
            yaws.add(Integer.valueOf(Math.round((float) Math.atan2(q.y(), q.w()) * 20.0F)));
            scales.add(Integer.valueOf(Math.round(p.scale() * 20.0F)));
        }

        if (expectMultiSurface) {
            assertTrue(usedSurfaces.size() >= 2,
                    label + ": expected items across >=2 surfaces, got " + usedSurfaces.size());
        }
        assertTrue(yaws.size() >= Math.min(8, items.size() / 2 + 2),
                label + ": insufficient yaw diversity (" + yaws.size() + ")");
        assertTrue(scales.size() >= 3,
                label + ": insufficient scale diversity (" + scales.size() + ")");
        if (expectLyingPossible) {
            assertTrue(lyingCount > 0, label + ": expected at least some lying flat items");
        }
        System.out.println("  scene [" + label + "]: items=" + items.size()
                + " surfaces=" + usedSurfaces.size()
                + " yaws=" + yaws.size()
                + " scales=" + scales.size()
                + " lying=" + lyingCount + " standing=" + standingCount
                + " stacked=" + stackedCount + " -> 0 collisions");
    }

    private static List<PlacedItem> buildSceneFromContainerAndScatter(ClientStation station,
            PlacingOptions opts, MockFurnitureBE be) {
        SceneInstance scene = new SceneInstance(station, opts);
        station.scene = scene;
        for (int i = 0; i < be.handson$ownSize(); i++) {
            ItemStack st = be.getItem(i);
            if (st == null || st.isEmpty()) continue;
            boolean flat = (i % 3 != 2);
            PlacedShape shape = flat
                    ? new PlacedShape(true, 0.5F, 0.5F, 0.04F)
                    : new PlacedShape(false, 0.45F, 0.48F, 0.45F);
            PlacedItem pItem = new PlacedItem(SlotRef.of("items", i), st.copy(), shape);
            scene.placed.add(new NodeInstance(pItem));
        }
        scene.update(station.state(), 0.0F);
        List<PlacedItem> result = new ArrayList<>();
        for (NodeInstance n : scene.placed) {
            result.add(n.placed());
        }
        return result;
    }

    private static List<PlacedItem> buildSceneAndScatter(ClientStation station, PlacingOptions opts,
            List<Item> testItems, int count) {
        SceneInstance scene = new SceneInstance(station, opts);
        station.scene = scene;
        for (int i = 0; i < count; i++) {
            Item item = testItems.get(i % testItems.size());
            boolean flat = (i % 3 != 2);
            PlacedShape shape = flat
                    ? new PlacedShape(true, 0.5F, 0.5F, 0.04F)
                    : new PlacedShape(false, 0.45F, 0.48F, 0.45F);
            PlacedItem pItem = new PlacedItem(SlotRef.of("items", i), new ItemStack(item, 1), shape);
            scene.placed.add(new NodeInstance(pItem));
        }
        scene.update(station.state(), 0.0F);
        List<PlacedItem> result = new ArrayList<>();
        for (NodeInstance n : scene.placed) {
            result.add(n.placed());
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        MockLevel level = new MockLevel();
        ServerPlayer playerNear = new MockPlayer(level, 122.0, 64.0, -38.0);
        ServerPlayer playerFar = new MockPlayer(level, 400.0, 64.0, 400.0);

        BlockPos cabinetPos = new BlockPos(120, 64, -40);
        BlockPos fridgePos = new BlockPos(125, 64, -40);
        BlockPos drawerPos = new BlockPos(130, 64, -40);
        BlockPos manualPos = new BlockPos(135, 64, -40);

        // Register test items + refurbished_furniture storage block
        Map<ResourceLocation, Block> blockMap = new HashMap<>();
        Block cabinetBlock = new Block();
        blockMap.put(ResourceLocation.tryParse("refurbished_furniture:oak_storage_cabinet"), cabinetBlock);
        BuiltInRegistries.BLOCK = mapRegistry(blockMap);

        Map<ResourceLocation, Item> itemMap = new HashMap<>();
        List<Item> lootItems = new ArrayList<>();
        String[] itemIds = {
                "minecraft:iron_sword", "minecraft:bread", "minecraft:book",
                "minecraft:potion", "minecraft:emerald", "minecraft:lantern"
        };
        StringBuilder poolText = new StringBuilder();
        for (int i = 0; i < itemIds.length; i++) {
            Item it = new Item();
            lootItems.add(it);
            itemMap.put(ResourceLocation.tryParse(itemIds[i]), it);
            poolText.append(itemIds[i]).append(" | 10 | 10 | 1-2\n");
        }
        BuiltInRegistries.ITEM = mapRegistry(itemMap);

        Path poolDir = LootPoolManager436.ensureDirectory();
        Files.writeString(poolDir.resolve("build439_test.txt"), poolText.toString(), StandardCharsets.UTF_8);

        MockFurnitureBE cabinetBe = new MockFurnitureBE(cabinetPos, level, 160, 27);
        MockFurnitureBE manualBe = new MockFurnitureBE(manualPos, level, 160, 27);
        BlockState cabinetState = new BlockState(cabinetBlock, false);
        level.bes.put(cabinetPos, cabinetBe);
        level.states.put(cabinetPos, cabinetState);
        level.bes.put(manualPos, manualBe);
        level.states.put(manualPos, cabinetState);

        // Seed stale manual poses in cabinetBe slots 0 and 15 to verify stale-pose clearing on population
        StationState cabState = cabinetBe.stationState();
        cabState.setPose(SlotRef.of("items", 0), new ItemPose(1F, 1F, 1F, 0.5F, new Quaternionf(0, 0, 0, 1), 1F, -1));
        cabState.setPose(SlotRef.of("items", 15), new ItemPose(2F, 2F, 2F, 0.5F, new Quaternionf(0, 0, 0, 1), 1F, -1));
        // Unrelated group slot ("extra") must NOT be wiped
        cabState.setPose(SlotRef.of("extra", 0), new ItemPose(3F, 3F, 3F, 0.5F, new Quaternionf(0, 0, 0, 1), 1F, -1));

        // 1. Server scatter mark check via real LootPoolManager436.populateContainer
        NpcDialogueGraph graph = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node card = ChestGraph396.chestNode(graph);
        NpcDialogueGraph.Node lp = graph.add(NpcDialogueGraph.TRIGGER, 200, 100);
        LootPool436.applyDefaults(lp);
        lp.action = LootPool436.ACTION_PREFIX + "build439_test.txt|||16-18";
        card.next = lp.id;

        int placedCount = LootPoolManager436.populateContainer(level, cabinetPos, graph);
        assertTrue(placedCount >= 14, "expected >= 14 items populated, got " + placedCount);
        long seed = cabState.data().getLong(ChestDiegesisCompat436.SCATTER_SEED_KEY);
        assertTrue(seed != 0L, "expected SettledLandsScatterSeed != 0 after populateContainer");
        assertTrue(cabinetBe.changedCount >= 1, "expected setChanged() called on populate");
        assertTrue(cabState.getPose(SlotRef.of("items", 0)) == null
                && cabState.getPose(SlotRef.of("items", 15)) == null,
                "expected stale poses in items group cleared");
        assertTrue(cabState.getPose(SlotRef.of("extra", 0)) != null,
                "expected non-items group pose preserved");

        // Manual furniture (no dungeon loot) has seed == 0 -> scatterFreeSpot MUST return null
        ClientStation manualClient = new ClientStation(manualPos, manualBe.stationState());
        List<PlaceSurface> cabSurfaces = List.of(
                new PlaceSurface(new Vector2f(1F, 2F), new Vector2f(15F, 15F), 8F, 7F, EnumSet.of(OpenSide.FRONT)),
                new PlaceSurface(new Vector2f(1F, 2F), new Vector2f(15F, 15F), 1F, 6F, EnumSet.of(OpenSide.FRONT))
        );
        PlacingOptions cabOpts = new PlacingOptions(4.0F, new float[]{0.5F, 1.6F}, cabSurfaces);
        SceneInstance manualScene = new SceneInstance(manualClient, cabOpts);
        manualClient.scene = manualScene;
        PlacedItem manualItem = new PlacedItem(SlotRef.of("items", 0), new ItemStack(lootItems.get(0), 1),
                new PlacedShape(true, 0.5F, 0.5F, 0.04F));
        assertTrue(DiegesisScatterClient439.scatterFreeSpot(manualScene, manualItem, manualItem.shape()) == null,
                "manual furniture (seed == 0) must let Diegesis place normally");

        // 2. Client scatter test: storage_cabinet (2 shelves, itemSize 4.0) using real server-populated slots
        DiegesisScatterClient439.registerBehavior();
        ClientStation cabClient = new ClientStation(cabinetPos, cabState.copy());
        List<PlacedItem> cabPlaced = buildSceneFromContainerAndScatter(cabClient, cabOpts, cabinetBe);
        verifySceneGeometry("storage_cabinet", cabPlaced, cabOpts, true, true);

        // 3. Client scatter test: fridge (3 shelves + 3 narrow door pockets, itemSize 2.0)
        StationState fridgeState = new StationState();
        CompoundTag fd = fridgeState.data();
        fd.putLong(ChestDiegesisCompat436.SCATTER_SEED_KEY, 0x13572468ACE0L);
        fridgeState.setData(fd);
        List<PlaceSurface> fridgeSurfaces = List.of(
                new PlaceSurface(new Vector2f(1F, 6F), new Vector2f(15F, 15F), 11F, 4F, EnumSet.of(OpenSide.FRONT)),
                new PlaceSurface(new Vector2f(1F, 6F), new Vector2f(15F, 15F), 6F, 4F, EnumSet.of(OpenSide.FRONT)),
                new PlaceSurface(new Vector2f(1F, 6F), new Vector2f(15F, 15F), 1F, 4F, EnumSet.of(OpenSide.FRONT)),
                new PlaceSurface(new Vector2f(1F, 3F), new Vector2f(15F, 5F), 12F, 4F, EnumSet.noneOf(OpenSide.class)),
                new PlaceSurface(new Vector2f(1F, 3F), new Vector2f(15F, 5F), 7F, 4F, EnumSet.noneOf(OpenSide.class)),
                new PlaceSurface(new Vector2f(1F, 3F), new Vector2f(15F, 5F), 2F, 4F, EnumSet.noneOf(OpenSide.class))
        );
        PlacingOptions fridgeOpts = new PlacingOptions(2.0F, new float[]{0.5F, 1.6F}, fridgeSurfaces);
        ClientStation fridgeClient = new ClientStation(fridgePos, fridgeState);
        List<PlacedItem> fridgePlaced = buildSceneAndScatter(fridgeClient, fridgeOpts, lootItems, 26);
        verifySceneGeometry("fridge", fridgePlaced, fridgeOpts, true, true);

        // 4. Client scatter test: drawer with flatLies=true (2 drawers, closed walls)
        StationState drawerState = new StationState();
        CompoundTag dd = drawerState.data();
        dd.putLong(ChestDiegesisCompat436.SCATTER_SEED_KEY, 0xCAFEBABE1029L);
        drawerState.setData(dd);
        List<PlaceSurface> drawerSurfaces = List.of(
                new PlaceSurface(new Vector2f(1F, 1F), new Vector2f(15F, 12F), 8F, 5F, EnumSet.noneOf(OpenSide.class), true),
                new PlaceSurface(new Vector2f(1F, 1F), new Vector2f(15F, 12F), 1F, 6F, EnumSet.noneOf(OpenSide.class), true)
        );
        PlacingOptions drawerOpts = new PlacingOptions(4.0F, new float[]{0.5F, 1.6F}, drawerSurfaces);
        ClientStation drawerClient = new ClientStation(drawerPos, drawerState);
        List<PlacedItem> drawerPlaced = buildSceneAndScatter(drawerClient, drawerOpts, lootItems, 14);
        verifySceneGeometry("drawer_flatLies", drawerPlaced, drawerOpts, true, true);

        // 5. Full network round-trip: client focus -> ScatterPoses C2S -> server validation & commit
        PacketDistributor.SENT_TO_SERVER.clear();
        cabClient.focused = true;
        StationBehavior registered = DiegesisClient.LAST_FACTORY == null
                ? null : DiegesisClient.LAST_FACTORY.create();
        assertTrue(registered != null, "DiegesisClient.addBehavior did not register ScatterBehavior");
        DiegesisScatterClient439.trySyncFocusedStation(cabClient);
        assertTrue(PacketDistributor.SENT_TO_SERVER.size() == 1,
                "expected 1 ScatterPoses packet sent on focus, got " + PacketDistributor.SENT_TO_SERVER.size());
        CustomPacketPayload sent = PacketDistributor.SENT_TO_SERVER.get(0);
        String payloadData = extractScatterWire(sent);
        String[] parts = payloadData.split("\\|", 3);
        long wireSeed = Long.parseLong(parts[1]);
        String wireB64 = parts[2];

        // 5a. Far player (> 64 blocks) must be rejected
        assertTrue(!ChestDiegesisCompat436.commitScatteredPoses(playerFar, cabinetPos, wireSeed, wireB64),
                "far player must be rejected");
        assertTrue(cabinetBe.stationState().data().contains(ChestDiegesisCompat436.SCATTER_SEED_KEY),
                "seed must survive rejected far-player commit");

        // 5b. Wrong seed must be rejected
        assertTrue(!ChestDiegesisCompat436.commitScatteredPoses(playerNear, cabinetPos, wireSeed ^ 1L, wireB64),
                "wrong seed must be rejected");

        // 5c. Valid player commits poses, clears seed, saves BE
        int prevChanges = cabinetBe.changedCount;
        assertTrue(ChestDiegesisCompat436.commitScatteredPoses(playerNear, cabinetPos, wireSeed, wireB64),
                "valid player commit must succeed");
        assertTrue(!cabinetBe.stationState().data().contains(ChestDiegesisCompat436.SCATTER_SEED_KEY),
                "SettledLandsScatterSeed must be removed after commit");
        assertTrue(cabinetBe.changedCount > prevChanges,
                "setChanged() must run after commitScatteredPoses");
        for (PlacedItem pItem : cabPlaced) {
            assertTrue(cabinetBe.stationState().getPose(pItem.slot()) != null,
                    "committed pose missing for slot " + pItem.slot().index());
        }

        // 5d. Replay by second player after seed cleared must be rejected
        assertTrue(!ChestDiegesisCompat436.commitScatteredPoses(playerNear, cabinetPos, wireSeed, wireB64),
                "replay commit after seed cleared must be rejected");

        // 6. Late seed arrival (seed == 0 first, then seed arrives later -> automatic re-scatter)
        StationState lateState = new StationState();
        ClientStation lateClient = new ClientStation(cabinetPos, lateState);
        List<PlacedItem> initialGrid = buildSceneAndScatter(lateClient, cabOpts, lootItems, 10);
        ItemPose firstInitial = initialGrid.get(0).pose();
        assertTrue(Math.abs(firstInitial.rotation().y()) < 1e-4F,
                "expected stock upright pose before seed arrives");

        CompoundTag ld = lateState.data();
        ld.putLong(ChestDiegesisCompat436.SCATTER_SEED_KEY, 0x998877665544L);
        lateState.setData(ld);
        lateClient.focused = true;
        PacketDistributor.SENT_TO_SERVER.clear();
        registered.onStateChanged(lateClient, new StationState.Changes(true));
        registered.onOpen(lateClient);
        assertTrue(!PacketDistributor.SENT_TO_SERVER.isEmpty(),
                "expected late seed arrival + onOpen to re-scatter and send ScatterPoses");
        boolean anyRotated = false;
        for (NodeInstance n : lateClient.scene.placed) {
            if (Math.abs(n.placed().pose().rotation().y()) > 0.05F
                    || Math.abs(n.placed().pose().rotation().x()) > 0.05F) {
                anyRotated = true;
                break;
            }
        }
        assertTrue(anyRotated, "expected late seed arrival to replace initial grid with scattered rotations");

        // 7. Tick-watch fallback check (ChestOpenWatch401 -> DiegesisScatterClient439.tickWatch)
        StationState watchState = new StationState();
        CompoundTag wd = watchState.data();
        wd.putLong(ChestDiegesisCompat436.SCATTER_SEED_KEY, 0x42424242L);
        watchState.setData(wd);
        ClientStation watchClient = new ClientStation(drawerPos, watchState);
        List<PlacedItem> watchPlaced = buildSceneAndScatter(watchClient, drawerOpts, lootItems, 10);
        verifySceneGeometry("tick_watch_sync", watchPlaced, drawerOpts, true, true);
        watchClient.focused = true;
        PacketDistributor.SENT_TO_SERVER.clear();
        for (int t = 0; t < 4; t++) {
            ChestOpenWatch401.onClientTick((ClientTickEvent.Post) null);
        }
        assertTrue(!PacketDistributor.SENT_TO_SERVER.isEmpty(),
                "expected ChestOpenWatch401.onClientTick -> DiegesisScatterClient439.tickWatch to sync focused station");

        // 8. Vanilla BlockEntity safety (no StationHost, no crash)
        VanillaChestBE vanilla = new VanillaChestBE();
        assertTrue(!ChestDiegesisCompat436.markLootScatter(vanilla, Collections.singletonList(0), 12345L),
                "vanilla BlockEntity must safely return false from markLootScatter");

        System.out.println("Build439Probe: ALL 8 integration & geometry checks passed!");
    }

    private static String extractScatterWire(CustomPacketPayload payload) throws Exception {
        if (payload instanceof dev.settledlands.chest.ChestLootNet436.ScatterPoses sp) {
            return sp.data();
        }
        Method m = payload.getClass().getMethod("data");
        return (String) m.invoke(payload);
    }
}
