#!/usr/bin/env python3
"""
Gate (Build 458): Full verification suite for Build 457:
  1. Manifest & version (0.8.0-build458-var-catalog-ui-polish)
  2. Strict Sponge Mixin package checks (zero '$', zero non-@Mixin, zero synthetic lambdas in
     ZERO_LAMBDA_457, zero invokedynamic in execution/substitution/catalog/zone mixins)
  3. settledlands.mixins.json ordering:
     - VarActionsSubstituteMixin457 BEFORE TeleportActionsMixin424 in mixins[]
     - VarExecutionNpcMixin453 BEFORE GuideRange450Mixin in mixins[]
     - All Build 457 common mixins present in mixins[]
     - NpcDropdownFirst333 FIRST and GraphHover448Mixin LAST in client[]
     - TeleportValuePinMixin457 AFTER TeleportGraphUiMixin424 in client[]
     - VarDropdownOverlayMixin457 BEFORE GraphDropdownHover448Mixin in client[]
     - VarDataGraphUi453Mixin and VarCatalogConfigMixin457 BEFORE GraphRangeInputs450Mixin in client[]
  4. Zero "UE5" branding across ru_ru.json & en_us.json, full RU/EN key parity, and bytecode key scan
  5. Inherited Build 450 + 452 + 455 + 456 JVM probes + comprehensive Probe457 covering:
     - Local scope default on new VarDataNode453
     - Right-edge blue output pin at (220, 92) on Variable & wiring into Pursuit, IF, VARIABLE,
       LINE, BUTTON, TRIGGER, and TeleportNode424 (including concurrent orange + blue wiring)
     - WireHover453 detection, aimColoredAtPoint, and unplugFrom on blue Variable wires
     - TeleportNode424 %var% coordinate validation & summary formatting
     - VarScopeStore453 token substitution (%<name>%, %var%, %value%), blue node resolution,
       and TextVarContext stack
     - VarCatalogStore457 op routing & VarCatalogClient457 packet/dropdown/foreign-lock filtering
     - NBT & Clipboard roundtrips across Variable -> Teleport / LINE / IF
"""

import json
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import build450_check
import build452_check
import build453_check
import build455_check
import build456_check
import check_mixin_class

NEW_KEYS_457 = (
    "gui.settledlands.var453.btn_local_toggle",
    "gui.settledlands.var453.btn_local_on",
    "gui.settledlands.var453.btn_local_off",
    "gui.settledlands.var453.blue_out_card",
    "gui.settledlands.var453.blue_out_count",
    "gui.settledlands.var453.btn_unlink_blue_out",
    "gui.settledlands.var453.blue_if_var_linked",
    "gui.settledlands.var453.blue_text_linked",
    "gui.settledlands.tip448.pin.var453_blue_out_title",
    "gui.settledlands.tip448.pin.var453_blue_out",
    "gui.settledlands.tip448.pin.blue_in_title",
    "gui.settledlands.tip448.pin.blue_in",
    "gui.settledlands.var.scope.local",
    "gui.settledlands.var.scope.personal",
    "gui.settledlands.var.scope.personal_btn",
    "gui.settledlands.var.scope.shared",
    "gui.settledlands.var.scope.local.desc",
    "gui.settledlands.var.scope.personal.desc",
    "gui.settledlands.var.scope.shared.desc",
    "gui.settledlands.var_catalog.title",
    "gui.settledlands.var_catalog.gear_tip",
    "gui.settledlands.var_catalog.dropdown_pick",
    "gui.settledlands.var_catalog.create_row_tip",
    "gui.settledlands.var_catalog.edit_row_tip",
    "gui.settledlands.var_catalog.scope_tag.local",
    "gui.settledlands.var_catalog.scope_tag.personal",
    "gui.settledlands.var_catalog.scope_tag.shared",
    "gui.settledlands.var_catalog.locked_badge",
    "gui.settledlands.var_catalog.other_object",
    "gui.settledlands.var_catalog.unavailable_tip",
    "gui.settledlands.var_catalog.current_owner",
    "gui.settledlands.var_catalog.empty",
    "gui.settledlands.var_catalog.search",
    "gui.settledlands.var_catalog.filter_all",
    "gui.settledlands.var_catalog.only_avail",
    "gui.settledlands.var_catalog.show_all",
    "gui.settledlands.var_catalog.only_avail_tip",
    "gui.settledlands.var_catalog.show_all_tip",
    "gui.settledlands.var_catalog.btn_new",
    "gui.settledlands.var_catalog.btn_save",
    "gui.settledlands.var_catalog.btn_apply",
    "gui.settledlands.var_catalog.btn_delete",
    "gui.settledlands.var_catalog.btn_delete_confirm",
    "gui.settledlands.var_catalog.btn_close",
    "gui.settledlands.var_catalog.name_label",
    "gui.settledlands.var_catalog.value_label",
    "gui.settledlands.var_catalog.scope_label",
    "gui.settledlands.var_catalog.desc_label",
    "gui.settledlands.var_catalog.owner_row",
    "gui.settledlands.var_catalog.status_new",
    "gui.settledlands.var_catalog.status_saved",
    "gui.settledlands.var_catalog.status_deleted",
    "gui.settledlands.var_catalog.confirm_delete",
    "gui.settledlands.var_catalog.err_empty_name",
    "gui.settledlands.var_catalog.err_foreign_local",
)

ZERO_LAMBDA_457 = build456_check.ZERO_LAMBDA_456 + (
    "dev/settledlands/mixin/NpcPointsCatalogMixin457",
    "dev/settledlands/mixin/VarCatalogViewMixin457",
    "dev/settledlands/mixin/VarCatalogEditMixin457",
    "dev/settledlands/mixin/VarActionsSubstituteMixin457",
    "dev/settledlands/mixin/BlockTalkVarMixin457",
    "dev/settledlands/mixin/NpcProfileFillVarMixin457",
    "dev/settledlands/mixin/VarCatalogConfigMixin457",
    "dev/settledlands/mixin/VarDropdownOverlayMixin457",
    "dev/settledlands/mixin/TeleportValuePinMixin457",
)

PROBE457_JAVA = r"""
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcGraphClipboard318;
import dev.settledlands.npc.NpcPointsRemoveBridge457;
import dev.settledlands.npc.NpcWirePoints321;
import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.npc.VarCatalogStore457;
import dev.settledlands.npc.VarDataNode453;
import dev.settledlands.npc.VarScopeStore453;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.PursuitGraphNode272;
import dev.settledlands.npc.client.VarCatalogClient457;
import dev.settledlands.npc.client.VarDataCard453;
import dev.settledlands.npc.client.WireHover453;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

public class Probe457 {
    private static int checks = 0;
    private static void check(boolean cond, String msg) {
        checks++;
        if (!cond) throw new AssertionError("FAIL: " + msg);
    }

    public static void main(String[] args) throws Exception {
        // 1. VarDataNode453 defaults to Local scope and disables white flow ports
        NpcDialogueGraph g = new NpcDialogueGraph();
        NpcDialogueGraph.Node vGold = g.add(NpcDialogueGraph.TRIGGER, 0, 0);
        VarDataNode453.applyDefaults(vGold);
        vGold.pointsName = "gold";
        vGold.varValue = 250;
        check(vGold.varLocal && !vGold.pointsShared, "New VarDataNode453 defaults to Local scope");
        check(VarDataNode453.scopeOf(vGold) == VarDataNode453.SCOPE_LOCAL, "scopeOf defaults to SCOPE_LOCAL");
        check(vGold.next == -1 && vGold.falseNext == -1 && vGold.triggers.isEmpty(), "No white ports on VarDataNode453");
        check(VarDataNode453.isBlueCapableSource(vGold), "VarDataNode453 is blue-capable source");

        // Scope round-trip setter
        VarDataNode453.setScope(vGold, VarDataNode453.SCOPE_SHARED);
        check(!vGold.varLocal && vGold.pointsShared && VarDataNode453.scopeOf(vGold) == VarDataNode453.SCOPE_SHARED,
                "setScope(SHARED)");
        VarDataNode453.setScope(vGold, VarDataNode453.SCOPE_PERSONAL);
        check(!vGold.varLocal && !vGold.pointsShared && VarDataNode453.scopeOf(vGold) == VarDataNode453.SCOPE_PERSONAL,
                "setScope(PERSONAL)");
        VarDataNode453.setScope(vGold, VarDataNode453.SCOPE_LOCAL);
        check(vGold.varLocal && !vGold.pointsShared && VarDataNode453.scopeOf(vGold) == VarDataNode453.SCOPE_LOCAL,
                "setScope(LOCAL)");

        // Unnamed Variable node
        NpcDialogueGraph.Node vRaw = g.add(NpcDialogueGraph.TRIGGER, 0, 140);
        VarDataNode453.applyDefaults(vRaw);
        vRaw.varValue = 77;
        check(VarDataNode453.isBlueCapableSource(vRaw), "Unnamed VarDataNode453 is blue-capable source");

        // 2. Blue output pin geometry at (220, 92) and consumer wiring
        NpcGraphCanvas canvas = new NpcGraphCanvas();
        canvas.left = 0; canvas.top = 0; canvas.right = 1400; canvas.bottom = 900;
        check(PursuitGraphNode272.isValueSourceNode(vGold), "PursuitGraphNode272 treats VarDataNode453 as blue source");
        check(Math.abs(PursuitGraphNode272.checkPinScreenX(canvas, vGold) - canvas.screenX(vGold.x + 220.0)) < 0.01,
                "VarDataNode blue output pin sits at right edge x=220");
        check(Math.abs(PursuitGraphNode272.checkPinScreenY(canvas, vGold) - canvas.screenY(vGold.y + 92.0)) < 0.01,
                "VarDataNode blue output pin sits at y=92");
        check(Math.abs(VarDataCard453.orangeOutScreenY(canvas, vGold) - canvas.screenY(vGold.y + 50.0)) < 0.01,
                "VarDataNode orange output pin remains vertically separated at y=50");

        // Consumer nodes: Teleport, LINE, BUTTON, IF, VARIABLE, Pursuit, CHECK (for negative check)
        NpcDialogueGraph.Node tpNode = g.add(NpcDialogueGraph.TRIGGER, 320, 0);
        TeleportNode424.applyDefaults(tpNode);
        TeleportNode424.setX(tpNode, "%gold%");
        TeleportNode424.setY(tpNode, "64");
        TeleportNode424.setZ(tpNode, "%var%");
        NpcDialogueGraph.Node lineNode = g.add(NpcDialogueGraph.LINE, 320, 140);
        lineNode.text = "Hello %gold% and %var%!";
        NpcDialogueGraph.Node btnNode = g.add(NpcDialogueGraph.BUTTON, 320, 280);
        btnNode.text = "Pay %gold%";
        NpcDialogueGraph.Node ifNode = g.add(NpcDialogueGraph.IF, 320, 420);
        ifNode.pointsName = "score";
        NpcDialogueGraph.Node varMathNode = g.add(NpcDialogueGraph.VARIABLE, 320, 560);
        varMathNode.pointsName = "score";
        NpcDialogueGraph.Node pursuitNode = g.add(NpcDialogueGraph.TRIGGER, 320, 700);
        pursuitNode.action = "trigger:pursuit:start:npc:guard|1.6|8.0|1.5";
        NpcDialogueGraph.Node checkNode = g.add(NpcDialogueGraph.CHECK, -300, 0);

        check(PursuitGraphNode272.isValueInputNode(tpNode), "Teleport accepts blue wire");
        check(PursuitGraphNode272.isValueInputNode(lineNode), "LINE accepts blue wire");
        check(PursuitGraphNode272.isValueInputNode(btnNode), "BUTTON accepts blue wire");
        check(PursuitGraphNode272.isValueInputNode(ifNode), "IF accepts blue wire");
        check(PursuitGraphNode272.isValueInputNode(varMathNode), "VARIABLE accepts blue wire");
        check(PursuitGraphNode272.isPursuitNode(pursuitNode), "Pursuit accepts blue wire");

        // Wire vGold (blue) -> tpNode, lineNode, btnNode, ifNode, pursuitNode
        PursuitGraphNode272.setInputSource(tpNode, vGold.id);
        PursuitGraphNode272.setInputSource(lineNode, vRaw.id);
        PursuitGraphNode272.setInputSource(btnNode, vGold.id);
        PursuitGraphNode272.setInputSource(ifNode, vGold.id);
        PursuitGraphNode272.setSource(pursuitNode, vGold.id);

        check(tpNode.varInputNode == vGold.id, "Teleport stores blue VarData source in varInputNode");
        check(lineNode.varInputNode == vRaw.id, "LINE stores blue VarData source in varInputNode");
        check(btnNode.varInputNode == vGold.id, "BUTTON stores blue VarData source in varInputNode");
        check(VarDataNode453.entityInput(g, ifNode) == vGold.id, "IF stores blue VarData source in entityInput453");
        check(PursuitGraphNode272.sourceId(pursuitNode) == vGold.id, "Pursuit stores blue VarData source");
        check(PursuitGraphNode272.wiredTo(g, vGold.id) == 4, "wiredTo counts tpNode + btnNode + ifNode + pursuitNode");
        check(PursuitGraphNode272.wiredTo(g, vRaw.id) == 1, "wiredTo counts lineNode for vRaw");

        // Concurrent orange + blue wiring on IF node without conflict
        ifNode.varInputNode = vRaw.id;
        check(ifNode.varInputNode == vRaw.id && VarDataNode453.entityInput(g, ifNode) == vGold.id,
                "IF keeps orange varInputNode and blue entityInput453 simultaneously");

        // blueLinked, blueEnds, and WireHover453 scan on VarDataNode453 -> Teleport
        check(PursuitGraphNode272.blueLinked(g, vGold, tpNode.id), "blueLinked true for VarData -> Teleport");
        check(PursuitGraphNode272.blueLinked(g, vGold, ifNode.id), "blueLinked true for VarData -> IF");
        double[] ends = PursuitGraphNode272.blueEnds(canvas, vGold, tpNode);
        check(ends != null && Math.abs(ends[2] - canvas.screenX(vGold.x + 220.0)) < 0.01
                && Math.abs(ends[4] - canvas.screenX(tpNode.x)) < 0.01,
                "blueEnds spans from VarData right pin (220, 92) to Teleport left pin (0, 92)");

        double midBx = (ends[2] + ends[4]) * 0.5;
        double midBy = (ends[3] + ends[5]) * 0.5;
        WireHover453.WireHit hitBlue = WireHover453.scanNearestWire(null, canvas, g, midBx, midBy);
        check(hitBlue != null && hitBlue.kind == WireHover453.KIND_VALUE
                && hitBlue.from != null && hitBlue.from.id == vGold.id && hitBlue.toId == tpNode.id,
                "WireHover453 detects blue wire VarData -> Teleport");

        String pLabel = PursuitGraphNode272.sourceLabel(g, pursuitNode);
        check(pLabel != null && pLabel.contains("gold"), "Pursuit sourceLabel includes VarData name");

        // Wire points hang promotion from VarData blue pin -> varMathNode
        NpcWirePoints321.attachColor(vGold, NpcWirePoints321.KIND_VALUE, 0, 180, 320);
        check(WireHover453.aimColoredAtPoint(g, 180, 320, varMathNode.id) == 1,
                "aimColoredAtPoint promotes blue hang from VarData -> VARIABLE");
        check(VarDataNode453.entityInput(g, varMathNode) == vGold.id,
                "Promoted blue wire populated VARIABLE.entityInput453");

        // Unplugging blue wires from vGold leaves IF's orange varInputNode intact
        PursuitGraphNode272.unplugFrom(g, vGold.id);
        check(PursuitGraphNode272.wiredTo(g, vGold.id) == 0, "unplugFrom clears all blue outputs of vGold");
        check(tpNode.varInputNode == -1 && btnNode.varInputNode == -1
                && VarDataNode453.entityInput(g, ifNode) == -1
                && PursuitGraphNode272.sourceId(pursuitNode) == -1,
                "All blue consumer pins cleared");
        check(ifNode.varInputNode == vRaw.id, "Orange varInputNode on IF survived blue unplug");

        // Re-wire for subsequent runtime & serialization tests
        PursuitGraphNode272.setInputSource(tpNode, vGold.id);
        PursuitGraphNode272.setInputSource(ifNode, vGold.id);

        // 3. TeleportNode424 %var% coordinate validation & summary
        check(TeleportNode424.isCoordInput("%gold%"), "TeleportNode424 accepts %gold% coord");
        check(TeleportNode424.isCoordInput("%player_home_x%"), "TeleportNode424 accepts long %var% coord");
        check(TeleportNode424.isCoordInput("-128.5"), "TeleportNode424 accepts negative decimal");
        check(!TeleportNode424.isCoordInput("10|20"), "TeleportNode424 rejects pipe delimiter");
        check(!TeleportNode424.isCoordInput("abc"), "TeleportNode424 rejects plain letters");
        check("%gold%, 64, %var%".equals(TeleportNode424.formatSummaryCoords(tpNode)),
                "formatSummaryCoords preserves %var% tokens in display");

        // 4. VarScopeStore453 token substitution, resolveBlueVarNode, TextVarContext
        check(VarScopeStore453.resolveBlueVarNode(g, tpNode) == vGold, "resolveBlueVarNode finds vGold on Teleport");
        check(VarScopeStore453.resolveBlueVarNode(g, lineNode) == vRaw, "resolveBlueVarNode finds vRaw on LINE");
        check(VarScopeStore453.resolveBlueVarNode(g, ifNode) == vGold, "resolveBlueVarNode finds vGold on IF entityInput453");
        // Verify CHECK node is NOT treated as VarData
        NpcDialogueGraph.Node badRef = g.add(NpcDialogueGraph.LINE, 0, 500);
        badRef.varInputNode = checkNode.id;
        check(VarScopeStore453.resolveBlueVarNode(g, badRef) == null, "resolveBlueVarNode rejects non-VarData CHECK node");

        // Token substitution on Teleport command & dialogue text
        String tpSub = VarScopeStore453.substituteVarTokens(tpNode.action, null, null, null, null, g, tpNode);
        check("trigger:teleport:minecraft:overworld|250|64|250".equals(tpSub),
                "substituteVarTokens expands %gold% and %var% in teleport action: got " + tpSub);
        TeleportNode424.Parsed parsedTp = TeleportNode424.parse(tpSub);
        check(parsedTp != null && parsedTp.x == 250.0 && parsedTp.y == 64.0 && parsedTp.z == 250.0,
                "Substituted teleport action parses to exact coordinates");

        String lineSub = VarScopeStore453.substituteVarTokens("Val=%var%, Alt=%value%, Name=%GOLD%, Keep=%unknown%",
                null, null, null, null, g, tpNode);
        check("Val=250, Alt=250, Name=250, Keep=%unknown%".equals(lineSub),
                "substituteVarTokens is case-insensitive and preserves unknown %tokens%: got " + lineSub);

        String unnamedSub = VarScopeStore453.substituteVarTokens("Raw=%var%", null, null, null, null, g, lineNode);
        check("Raw=77".equals(unnamedSub), "Unnamed blue VarData expands %var%");

        // TextVarContext stack discipline
        VarScopeStore453.pushTextContext(null, null, null, null, g, lineNode);
        check(VarScopeStore453.peekTextContext() != null && VarScopeStore453.peekTextContext().node == lineNode,
                "pushTextContext/peekTextContext");
        VarScopeStore453.popTextContext();
        check(VarScopeStore453.peekTextContext() == null, "popTextContext restores empty stack");

        // 5. VarCatalogStore457 & VarCatalogClient457 packet/dropdown/availability guards
        check(VarCatalogStore457.isCatalogOp("var457.list")
                && VarCatalogStore457.isCatalogOp("var457.save")
                && VarCatalogStore457.isCatalogOp("var457.delete")
                && !VarCatalogStore457.isCatalogOp("noop"),
                "VarCatalogStore457.isCatalogOp");
        check(NpcPointsRemoveBridge457.class.isInterface(), "NpcPointsRemoveBridge457 is public interface outside mixin");

        CompoundTag pkt = new CompoundTag();
        pkt.putString("OwnerKey", "npc:uuid-1");
        pkt.putString("OwnerLabel", "NPC #1");
        pkt.putInt("ContextId", 101);
        ListTag items = new ListTag();
        items.add(makeItem("gold", VarDataNode453.SCOPE_SHARED, 500, "", "", true));
        items.add(makeItem("quest_step", VarDataNode453.SCOPE_LOCAL, 3, "npc:uuid-1", "NPC #1", true));
        items.add(makeItem("other_secret", VarDataNode453.SCOPE_LOCAL, 99, "npc:uuid-2", "NPC #2", false));
        items.add(makeItem("karma", VarDataNode453.SCOPE_PERSONAL, -5, "", "", true));
        pkt.put("Items", items);
        int revBefore = VarCatalogClient457.revision();
        VarCatalogClient457.applyPacket(pkt);
        check(VarCatalogClient457.revision() == revBefore + 1, "applyPacket increments revision");
        check(VarCatalogClient457.entries().size() == 4, "applyPacket loads 4 catalog entries");
        check(VarCatalogClient457.findMatching("GOLD", VarDataNode453.SCOPE_SHARED) != null,
                "findMatching resolves available entry");
        check(VarCatalogClient457.findMatching("other_secret", VarDataNode453.SCOPE_LOCAL) == null,
                "findMatching refuses foreign unavailable local variable");

        List<Component> ddLabels = new ArrayList<>();
        List<VarCatalogClient457.DropdownRowMeta> ddMeta = new ArrayList<>();
        int selIdx = VarCatalogClient457.buildDropdownOptions("quest_step", VarDataNode453.SCOPE_LOCAL, ddLabels, ddMeta);
        check(ddLabels.size() == 5 && ddMeta.size() == 5, "buildDropdownOptions includes create row + 4 entries");
        check(ddMeta.get(0).isCreateRow, "Row 0 is create/catalog row");
        check(selIdx == 2, "Matches quest_step at row index 2");
        check(!ddMeta.get(3).entry.available, "Foreign local variable preserves available=false in dropdown metadata");

        // 6. NBT + Clipboard full roundtrip preserving Variable local default & blue connections
        CompoundTag nbt = g.save();
        NpcDialogueGraph loaded = NpcDialogueGraph.load(nbt);
        check(loaded != null && loaded.get(vGold.id).varLocal, "varLocal preserved through NBT save/load");
        check(loaded.get(tpNode.id).varInputNode == vGold.id, "Teleport blue varInputNode preserved through NBT");
        check(loaded.get(lineNode.id).varInputNode == vRaw.id, "LINE blue varInputNode preserved through NBT");

        String clip = NpcGraphClipboard318.copy(g, List.of(vGold.id, tpNode.id, lineNode.id, vRaw.id, ifNode.id));
        NpcDialogueGraph pasted = new NpcDialogueGraph();
        List<Integer> pastedIds = NpcGraphClipboard318.paste(pasted, clip, 60, 60);
        check(pastedIds != null && pastedIds.size() == 5, "Clipboard pasted all 5 nodes");
        NpcDialogueGraph.Node pGold = null, pTp = null, pIf = null;
        for (int pid : pastedIds) {
            NpcDialogueGraph.Node pn = pasted.get(pid);
            if (VarDataNode453.isVarDataNode(pn) && "gold".equals(pn.pointsName)) pGold = pn;
            else if (TeleportNode424.isTeleportNode(pn)) pTp = pn;
            else if (pn.type == NpcDialogueGraph.IF) pIf = pn;
        }
        check(pGold != null && pTp != null && pIf != null, "Pasted graph contains Variable, Teleport, IF");
        check(pGold.varLocal, "Clipboard preserved VarDataNode453 varLocal");
        check(pTp.varInputNode == pGold.id, "Clipboard remapped Variable -> Teleport blue link");
        check(VarDataNode453.entityInput(pasted, pIf) == pGold.id, "Clipboard remapped Variable -> IF blue link");

        System.out.println("BUILD457_OK=true CHECKS=" + checks);
    }

    private static CompoundTag makeItem(String name, int scope, int val, String ownerKey, String ownerLabel, boolean avail) {
        CompoundTag row = new CompoundTag();
        row.putString("N", name);
        row.putInt("S", scope);
        row.putInt("V", val);
        row.putInt("D", val);
        row.putString("OK", ownerKey);
        row.putString("OL", ownerLabel);
        row.putString("Desc", "");
        row.putBoolean("A", avail);
        row.putInt("U", 1);
        return row;
    }
}
"""


def run_probe457(jar_path: pathlib.Path) -> str:
    java = build450_check.find_java()
    ecj = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
    stub_layers = (
        ROOT / "tools" / "api271",
        ROOT / "build303-src" / "stubs",
        ROOT / "build303-src" / "stubs345-src",
        ROOT / "build303-src" / "stubs396-src",
        ROOT / "build303-src" / "stubs420-src",
        ROOT / "build303-src" / "api-src",
        ROOT / "build303-src" / "api3-src",
        ROOT / "build303-src" / "api4-src",
        ROOT / "build303-src" / "api5-src",
        ROOT / "build303-src" / "api416-src",
        ROOT / "build303-src" / "stubs425-src",
        ROOT / "build303-src" / "stubs436-src",
        ROOT / "build303-src" / "stubs438-src",
        ROOT / "build303-src" / "stubs439-src",
        ROOT / "build303-src" / "stubs440-src",
        ROOT / "build303-src" / "stubs443-src",
        ROOT / "build303-src" / "stubs446-src",
        ROOT / "build303-src" / "stubs449-src",
    )
    with tempfile.TemporaryDirectory() as td_raw:
        td = pathlib.Path(td_raw)
        merged = td / "merged"
        stubs_out = td / "stubs_out"
        probe_out = td / "probe_out"
        merged.mkdir()
        stubs_out.mkdir()
        probe_out.mkdir()
        for layer in stub_layers:
            for src in layer.rglob("*.java"):
                dst = merged / src.relative_to(layer)
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
        stub_files = sorted(str(p) for p in merged.rglob("*.java"))
        r1 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", str(jar_path), "-d", str(stubs_out)] + stub_files,
            capture_output=True, text=True)
        if r1.returncode != 0:
            raise SystemExit("probe457 stub compile failed:\n" + r1.stdout + r1.stderr)
        probe_src = td / "Probe457.java"
        probe_src.write_text(PROBE457_JAVA)
        r2 = subprocess.run(
            [java, "-jar", str(ecj), "-21", "-proc:none", "-nowarn",
             "-cp", f"{jar_path}:{stubs_out}", "-d", str(probe_out), str(probe_src)],
            capture_output=True, text=True)
        if r2.returncode != 0:
            raise SystemExit("Probe457 compile failed:\n" + r2.stdout + r2.stderr)
        r3 = subprocess.run(
            [java, "-cp", f"{probe_out}:{jar_path}:{stubs_out}", "Probe457"],
            capture_output=True, text=True)
        if r3.returncode != 0 or "BUILD457_OK=true" not in r3.stdout:
            raise SystemExit("Probe457 failed:\n" + r3.stdout + "\n" + r3.stderr)
        return r3.stdout.strip()


def main(argv) -> int:
    if len(argv) < 1:
        print("usage: build457_check.py <jar>", file=sys.stderr)
        return 2
    jar_path = pathlib.Path(argv[0])
    if not jar_path.is_file():
        print(f"not found: {jar_path}", file=sys.stderr)
        return 2

    errors = []
    with zipfile.ZipFile(jar_path) as zf:
        names = set(zf.namelist())
        for req in build450_check.REQUIRED_CLASSES + (
            "dev/settledlands/mixin/TrialModuleSignalMixin452.class",
            "dev/settledlands/npc/VarDataNode453.class",
            "dev/settledlands/npc/VarScopeStore453.class",
            "dev/settledlands/npc/VarScopeStore453$TextVarContext.class",
            "dev/settledlands/npc/NpcPointsRemoveBridge457.class",
            "dev/settledlands/npc/VarCatalogStore457.class",
            "dev/settledlands/npc/VarCatalogStore457$CatalogItem.class",
            "dev/settledlands/npc/VarCatalogStore457$OwnerContext.class",
            "dev/settledlands/npc/VarCatalogStore457$SavedDef.class",
            "dev/settledlands/npc/client/VarCatalogClient457.class",
            "dev/settledlands/npc/client/VarCatalogClient457$Entry.class",
            "dev/settledlands/npc/client/VarCatalogClient457$DropdownRowMeta.class",
            "dev/settledlands/npc/client/VarCatalogClient457$TrackedDropdown.class",
            "dev/settledlands/npc/client/VarPoolEditorScreen457.class",
            "dev/settledlands/npc/client/VarDataCard453.class",
            "dev/settledlands/npc/client/VarDataInspector453.class",
            "dev/settledlands/npc/client/WireHover453.class",
            "dev/settledlands/npc/client/RadiusZoneUi456.class",
            "dev/settledlands/mixin/NpcPointsCatalogMixin457.class",
            "dev/settledlands/mixin/VarCatalogViewMixin457.class",
            "dev/settledlands/mixin/VarCatalogEditMixin457.class",
            "dev/settledlands/mixin/VarActionsSubstituteMixin457.class",
            "dev/settledlands/mixin/BlockTalkVarMixin457.class",
            "dev/settledlands/mixin/NpcProfileFillVarMixin457.class",
            "dev/settledlands/mixin/VarCatalogConfigMixin457.class",
            "dev/settledlands/mixin/VarDropdownOverlayMixin457.class",
            "dev/settledlands/mixin/TeleportValuePinMixin457.class",
            "dev/settledlands/mixin/VarNodeEntityInputMixin453.class",
            "dev/settledlands/mixin/VarGraphIoMixin453.class",
            "dev/settledlands/mixin/VarExecutionNpcMixin453.class",
            "dev/settledlands/mixin/VarExecutionBlockMixin453.class",
            "dev/settledlands/mixin/VarActionsSilentMixin453.class",
            "dev/settledlands/mixin/VarValueSourceBridgeMixin453.class",
            "dev/settledlands/mixin/VarDataMenuMixin453.class",
            "dev/settledlands/mixin/VarDataGraphUi453Mixin.class",
            "dev/settledlands/mixin/GraphInspectorDescMixin453.class",
            "dev/settledlands/mixin/GraphClickDragMixin453.class",
        ):
            if req not in names:
                errors.append(f"missing class {req}")

        for bad in names:
            if bad.startswith("dev/settledlands/mixin/") and "$" in bad:
                errors.append(f"forbidden '$' class inside mixin package: {bad}")

        toml = zf.read("META-INF/neoforge.mods.toml").decode("utf-8")
        if "0.8.0-build458-var-catalog-ui-polish" not in toml:
            errors.append("neoforge.mods.toml missing Build 457 version")

        mixins_cfg = json.loads(zf.read("settledlands.mixins.json").decode("utf-8"))
        common_list = mixins_cfg.get("mixins", [])
        for cm in (
            "VarNodeEntityInputMixin453",
            "VarGraphIoMixin453",
            "VarExecutionNpcMixin453",
            "VarExecutionBlockMixin453",
            "VarActionsSilentMixin453",
            "VarValueSourceBridgeMixin453",
            "RadiusZoneNodeMixin456",
            "RadiusZonePassMixin456",
            "RadiusZoneLegacy320Mixin456",
            "RadiusZoneNpcCheckMixin456",
            "RadiusZoneNpcDialogueMixin456",
            "NpcPointsCatalogMixin457",
            "VarCatalogViewMixin457",
            "VarCatalogEditMixin457",
            "VarActionsSubstituteMixin457",
            "BlockTalkVarMixin457",
            "NpcProfileFillVarMixin457",
        ):
            if cm not in common_list:
                errors.append(f"settledlands.mixins.json missing {cm} in mixins[]")
        if common_list.index("VarActionsSubstituteMixin457") > common_list.index("TeleportActionsMixin424"):
            errors.append("VarActionsSubstituteMixin457 must precede TeleportActionsMixin424 in mixins[]")
        if common_list.index("VarExecutionNpcMixin453") > common_list.index("GuideRange450Mixin"):
            errors.append("VarExecutionNpcMixin453 must precede GuideRange450Mixin in mixins[]")

        client_list = mixins_cfg.get("client", [])
        if not client_list or client_list[0] != "NpcDropdownFirst333":
            errors.append("NpcDropdownFirst333 must stay first in client[]")
        if client_list[-1] != "GraphHover448Mixin":
            errors.append("GraphHover448Mixin must stay last in client[]")
        for clm in (
            "VarDataMenuMixin453",
            "VarDataGraphUi453Mixin",
            "VarCatalogConfigMixin457",
            "VarDropdownOverlayMixin457",
            "TeleportValuePinMixin457",
            "GraphInspectorDescMixin453",
            "GraphClickDragMixin453",
        ):
            if clm not in client_list:
                errors.append(f"settledlands.mixins.json missing {clm} in client[]")
        if client_list.index("TeleportValuePinMixin457") < client_list.index("TeleportGraphUiMixin424"):
            errors.append("TeleportValuePinMixin457 must run AFTER TeleportGraphUiMixin424 in client[]")
        if client_list.index("VarDropdownOverlayMixin457") > client_list.index("GraphDropdownHover448Mixin"):
            errors.append("VarDropdownOverlayMixin457 must precede GraphDropdownHover448Mixin in client[]")
        if client_list.index("VarDataGraphUi453Mixin") > client_list.index("GraphRangeInputs450Mixin"):
            errors.append("VarDataGraphUi453Mixin must precede GraphRangeInputs450Mixin in client[]")
        if client_list.index("VarCatalogConfigMixin457") > client_list.index("GraphRangeInputs450Mixin"):
            errors.append("VarCatalogConfigMixin457 must precede GraphRangeInputs450Mixin in client[]")
        if client_list.index("GraphClickDragMixin453") < client_list.index("GraphRangeInputs450Mixin"):
            errors.append("GraphClickDragMixin453 must run AFTER GraphRangeInputs450Mixin in client[]")

        ru_raw = zf.read("assets/settledlands/lang/ru_ru.json").decode("utf-8")
        en_raw = zf.read("assets/settledlands/lang/en_us.json").decode("utf-8")
        ru = json.loads(ru_raw)
        en = json.loads(en_raw)
        if set(ru) != set(en):
            errors.append(f"lang parity diff: ru-en={len(set(ru)-set(en))} en-ru={len(set(en)-set(ru))}")
        for k in (tuple(build450_check.NEW_KEYS)
                  + build452_check.NEW_KEYS_452
                  + build453_check.NEW_KEYS_453
                  + build456_check.NEW_KEYS_456
                  + NEW_KEYS_457):
            if not ru.get(k) or not en.get(k):
                errors.append(f"missing lang key {k}")
        for lang_name, lang_map in (("ru_ru.json", ru), ("en_us.json", en)):
            for k, v in lang_map.items():
                if isinstance(v, str) and "ue5" in v.lower():
                    errors.append(f"forbidden UE5 mention in {lang_name}[{k}]: {v!r}")

        classes = check_mixin_class.load_classes(zf, [n for n in names if n.endswith(".class")])
        for mname in ZERO_LAMBDA_457:
            cf = classes.get(mname)
            if cf is None:
                errors.append(f"missing loaded class for {mname}")
                continue
            lambdas = [m.name for m in cf.methods if m.name.startswith("lambda$")]
            if lambdas:
                errors.append(f"mixin {mname} contains synthetic lambda(s): {lambdas}")

        for mname in (
            "dev/settledlands/mixin/VarExecutionBlockMixin453",
            "dev/settledlands/mixin/VarExecutionNpcMixin453",
            "dev/settledlands/mixin/VarActionsSubstituteMixin457",
            "dev/settledlands/mixin/BlockTalkVarMixin457",
            "dev/settledlands/mixin/NpcProfileFillVarMixin457",
            "dev/settledlands/mixin/NpcPointsCatalogMixin457",
            "dev/settledlands/mixin/VarCatalogViewMixin457",
            "dev/settledlands/mixin/VarCatalogEditMixin457",
            "dev/settledlands/mixin/VarCatalogConfigMixin457",
            "dev/settledlands/mixin/VarDropdownOverlayMixin457",
            "dev/settledlands/mixin/TeleportValuePinMixin457",
            "dev/settledlands/mixin/RadiusZoneNodeMixin456",
            "dev/settledlands/mixin/RadiusZonePassMixin456",
        ):
            cf = classes.get(mname)
            if cf is not None:
                indys = [k for k in cf.cp if k and k[0] == 18]
                if indys:
                    errors.append(f"mixin {mname} contains invokedynamic entries: {len(indys)}")

        allowed_stems = (
            "gui.settledlands.desc448.card.node",
            "gui.settledlands.desc448.card.mod",
            "gui.settledlands.tip448.menu.node",
            "gui.settledlands.tip448.menu.tmod",
            "gui.settledlands.tip448.node.n",
            "gui.settledlands.tip448.node.mod",
            "gui.settledlands.tip448.pin.slot",
            "gui.settledlands.tip448.varop",
            "gui.settledlands.tip448.preset",
            "gui.settledlands.tip448.op",
            "gui.settledlands.tip448.hack.slider_diff",
            "gui.settledlands.door.rows",
            "gui.settledlands.var453.op",
        )
        for n in sorted(names):
            if not n.endswith(".class"):
                continue
            blob = zf.read(n)
            for m in re.finditer(rb"(?:gui|message)\.settledlands\.[a-zA-Z0-9_.]+", blob):
                s = m.group(0).decode("ascii")
                if s.endswith(".") or s.endswith("_") or s in allowed_stems:
                    continue
                if s not in ru or s not in en:
                    errors.append(f"missing lang key {s} referenced in {n}")

    out450 = build450_check.run_probe(jar_path)
    out452 = build452_check.run_probe452(jar_path)
    out455 = build455_check.run_probe455(jar_path)
    out456 = build456_check.run_probe456(jar_path)
    out457 = run_probe457(jar_path)

    with zipfile.ZipFile(jar_path) as zf:
        classes458 = check_mixin_class.load_classes(
            zf, ["dev/settledlands/npc/client/VarPoolEditorScreen457.class"])
        vscreen = classes458.get("dev/settledlands/npc/client/VarPoolEditorScreen457")
        if vscreen is None:
            errors.append("missing VarPoolEditorScreen457 class")
        else:
            rb = [m for m in vscreen.methods
                  if m.name == "renderBackground"
                  and m.desc == "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"]
            if not rb:
                errors.append("VarPoolEditorScreen457 must override renderBackground(GuiGraphics,int,int,float) to prevent MC 1.21 world blur over content")
            else:
                code_attr = rb[0].attr("Code")
                code = code_attr.data if code_attr is not None else None
                if not code or len(code) < 9:
                    errors.append("VarPoolEditorScreen457.renderBackground missing Code attribute")
                else:
                    # Code attribute layout: max_stack(2), max_locals(2), code_length(4), code...
                    import struct
                    code_len = struct.unpack(">I", code[4:8])[0]
                    bc = code[8:8 + code_len]
                    if bc != b"\xb1":  # RETURN opcode only
                        errors.append(f"VarPoolEditorScreen457.renderBackground must be empty no-op (single RETURN), got bytecode {bc.hex()}")

    if errors:
        for e in errors:
            print("ERROR:", e, file=sys.stderr)
        return 1

    print("build458_check: OK ", jar_path)
    for out in (out450, out452, out455, out456, out457):
        for line in out.splitlines():
            print(" ", line)
    print(f"  verified: zero-lambda across {len(ZERO_LAMBDA_457)} mixins + Probe457 + all Build 450/452/455/456/457 gates OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
