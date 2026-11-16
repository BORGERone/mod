package dev.settledlands.mixin;

import dev.settledlands.npc.GuideDialogue;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcCommonGraph334;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcProfile;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 334: the "Общее" tab of every NPC is one graph, and every NPC runs it.
 *
 * <p>The tab used to be a per-NPC sub-graph: {@code GuideNpc} kept a copy in its profile and ticked
 * it three ways - checks, time and timers - from {@code tickSubGraph}, and the editor of one NPC
 * could only ever change that one NPC. The graph now lives in the world ({@code NpcCommonGraph334})
 * and this mixin makes the three places that read a per-NPC tab read the common one instead:</p>
 *
 * <ul>
 *   <li><b>tick</b> - {@code tickSubGraph} is called twice per tick, once for the walk tab and once
 *       for this one. The two are told apart by the cooldown map that comes with them, which is the
 *       NPC's own {@code animCheckCooldowns} for this pass and {@code walkCheckCooldowns} for the
 *       other. Only this pass is replaced; the walk tab stays exactly as it was, and while no common
 *       graph has been saved yet nothing is replaced at all.</li>
 *   <li><b>the editor</b> - {@code sendConfigView} writes the profile the client edits. The common
 *       graph is put in the field the tab reads for the length of that call and taken back out
 *       again afterwards, so the author always edits the common graph and no NPC ends up carrying
 *       a copy of it in its own saved data.</li>
 *   <li><b>saving</b> - {@code applyConfig} receives the graph of the tab. It is stored as the
 *       common graph, and the NPC's own field is put back to what it was, so the per-NPC graph of
 *       an older world survives untouched instead of being overwritten with the shared one.</li>
 * </ul>
 *
 * <p>Cost: one reference comparison of the cooldown map per tick and NPC, one cached store lookup,
 * and a write to world data only when the author saves the tab.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideNpc", remap = false)
public abstract class NpcCommonGraphMixin334 {

    /** The pass this mixin replaces carries this map; the walk pass carries the other one. */
    @Shadow
    private Map animCheckCooldowns;

    /** What the tab pointed at before {@code sendConfigView} swapped the common graph in. */
    @Unique
    private NpcDialogueGraph settledlands$keptAnim;

    @Inject(method = "tickSubGraph", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$tickCommon(NpcDialogueGraph graph, Map<Integer, Map<UUID, Long>> checkCd,
            Map<Integer, Long> timeCd, Map<Integer, Long> timerCd, CallbackInfo ci) {
        if (checkCd != this.animCheckCooldowns) return;      // the walk tab: stock, untouched
        GuideNpc self = (GuideNpc) (Object) this;
        Level level = ((Entity) (Object) this).level();
        MinecraftServer server = level == null ? null : level.getServer();
        NpcDialogueGraph common = NpcCommonGraph334.graph(server);
        if (common == null) return;                          // nothing saved yet: the NPC's own tab
        GuideDialogue.tickGraphCheckNodes(self, common, checkCd);
        GuideDialogue.tickGraphTimeNodes(self, common, timeCd);
        GuideDialogue.tickGraphTimerNodes(self, common, timerCd);
        ci.cancel();
    }

    @Inject(method = "sendConfigView", at = @At("HEAD"), remap = false)
    private void settledlands$sendCommon(ServerPlayer player, CallbackInfo ci) {
        NpcProfile profile = ((GuideNpc) (Object) this).profile();
        if (profile == null) return;
        this.settledlands$keptAnim = profile.animGraph;
        NpcDialogueGraph common = NpcCommonGraph334.graph(player == null ? null : player.getServer());
        if (common != null) profile.animGraph = common;
    }

    @Inject(method = "sendConfigView", at = @At("RETURN"), remap = false)
    private void settledlands$restoreAnim(ServerPlayer player, CallbackInfo ci) {
        NpcProfile profile = ((GuideNpc) (Object) this).profile();
        if (profile != null) profile.animGraph = this.settledlands$keptAnim;
        this.settledlands$keptAnim = null;
    }

    @Inject(method = "applyConfig", at = @At("HEAD"), remap = false)
    private static void settledlands$takeCommon(ServerPlayer player, int entityId, CompoundTag data,
            CallbackInfo ci) {
        if (player == null || data == null || !data.contains("AnimGraph", 10)) return;
        NpcDialogueGraph incoming = NpcDialogueGraph.load(data.getCompound("AnimGraph"));
        NpcCommonGraph334.save(player.getServer(), incoming);
        // The tab is common now: the graph that arrived is the shared one, not this NPC's. What the
        // NPC had of its own goes back exactly as it was, so an older world keeps its data.
        GuideNpc npc = GuideNpc.find(player, entityId);
        NpcProfile profile = npc == null ? null : npc.profile();
        NpcDialogueGraph own = profile == null ? null : profile.animGraph;
        if (own == null) {
            data.remove("AnimGraph");
        } else {
            data.put("AnimGraph", own.save());
        }
    }
}
