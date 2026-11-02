package dev.settledlands.npc;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Build 334: the one graph of the "Общее" tab, kept in the world instead of in an NPC.
 *
 * <p>Before this build the tab was a per-NPC sub-graph: every NPC carried its own copy in its
 * profile and ticked it on its own. The tab is now common - one graph, saved once for the whole
 * world, ticked by every NPC - so it lives here, next to the other world data, under the file name
 * {@code settledlands_common_graph}, and no NPC keeps a copy of it.</p>
 *
 * <p>The class is written the same way as {@code CutsceneStorage} and {@code NpcMobilityGroups}:
 * the same {@code SavedData} factory, the same {@code computeIfAbsent} call, the same world data
 * folder - nothing new is invented for storage. An empty store simply means "no common graph yet",
 * and while it is empty every NPC keeps running its own tab, exactly as before this build.</p>
 */
public final class NpcCommonGraphStorage extends SavedData {

    private static final String FILE = "settledlands_common_graph";
    private static final String GRAPH = "Graph";

    private NpcDialogueGraph graph;

    public static NpcCommonGraphStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE);
    }

    private static SavedData.Factory<NpcCommonGraphStorage> factory() {
        return new SavedData.Factory<>(NpcCommonGraphStorage::new, NpcCommonGraphStorage::load,
                DataFixTypes.LEVEL);
    }

    private static NpcCommonGraphStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        NpcCommonGraphStorage data = new NpcCommonGraphStorage();
        if (tag.contains(GRAPH, 10)) {
            data.graph = NpcDialogueGraph.load(tag.getCompound(GRAPH));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (graph != null) {
            tag.put(GRAPH, graph.save());
        }
        return tag;
    }

    /** The shared graph, or null while nothing has been saved into the tab yet. */
    public NpcDialogueGraph graph() {
        return graph;
    }

    public void setGraph(NpcDialogueGraph graph) {
        this.graph = graph;
        setDirty();
    }
}
