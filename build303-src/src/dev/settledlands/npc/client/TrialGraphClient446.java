package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.NpcProfile;
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialGraph446;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 446: client bridge and callback factories for the Trial Spawner card ({@code TRIAL_TYPE = 60})
 * and its inspector/config screen.
 */
public final class TrialGraphClient446 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/TrialGraphClient446");
    private static boolean reported;

    private TrialGraphClient446() {}

    public static NpcDialogueGraph graphOf(NpcNet.CfgView view) {
        CompoundTag data = view == null ? null : view.data();
        if (data == null || !data.contains("DialogueGraph", 10)) {
            return TrialGraph446.defaultGraph(null);
        }
        try {
            NpcDialogueGraph graph = NpcDialogueGraph.load(data.getCompound("DialogueGraph"));
            graph = TrialGraph446.withTrialNode(graph);
            TrialGraph446.stripCards(graph);
            return graph;
        } catch (Throwable failure) {
            report(failure);
            return TrialGraph446.defaultGraph(null);
        }
    }

    public static void openEditor(NpcNet.CfgView view) {
        if (view == null) return;
        try {
            NpcProfile profile = new NpcProfile();
            profile.dialogueGraph = graphOf(view);
            Minecraft.getInstance().setScreen(
                    new NpcDialogueGraphScreen(view.entityId(), profile, null, -1));
        } catch (Throwable failure) {
            report(failure);
        }
    }

    public static String[] lines(NpcDialogueGraph.Node node) {
        TrialConfig446 cfg = TrialGraph446.configOf(node);
        String modeKey = TrialConfig446.MODE_OMINOUS.equals(cfg.mode)
                ? "gui.settledlands.trial.mode.ominous"
                : TrialConfig446.MODE_SPECIAL.equals(cfg.mode)
                ? "gui.settledlands.trial.mode.special"
                : "gui.settledlands.trial.mode.normal";
        String l1 = safe("gui.settledlands.trial.line_mode",
                Component.translatable(modeKey).getString());
        String firstMob = cfg.mobs.isEmpty() ? "?" : shortId(cfg.mobs.get(0).entityId);
        if (cfg.mobs.size() > 1) firstMob += " +" + (cfg.mobs.size() - 1);
        String l2 = safe("gui.settledlands.trial.line_mobs",
                cfg.totalMobs + " (x" + cfg.simultaneousMobs + ") " + firstMob);
        String cdSec = Integer.toString(Math.max(0, cfg.cooldownTicks / 20));
        String l3 = safe("gui.settledlands.trial.line_rewards",
                cdSec + "s \u00b7 " + cfg.rewards.size());
        String l4 = safe("gui.settledlands.trial.line_waves",
                Integer.toString(TrialGraph446.waves(node)));
        String who = TrialGraph446.lastPlayer(node);
        String l5 = who.isEmpty()
                ? Component.translatable("gui.settledlands.trial.player_none").getString()
                : safe("gui.settledlands.trial.player_last", who);
        return new String[] {l1, l2, l3, l4, l5};
    }

    public static Runnable openConfigAction(NpcDialogueGraphScreen screen,
            NpcDialogueGraph.Node node) {
        return () -> Minecraft.getInstance().setScreen(
                new TrialControlConfigScreen446(screen, node, false));
    }

    public static Runnable cycleModeAction(NpcDialogueGraphScreen screen,
            NpcDialogueGraph.Node node, Runnable commitPrefix) {
        return () -> {
            TrialConfig446 cfg = TrialGraph446.configOf(node);
            cfg.mode = TrialConfig446.nextMode(cfg.mode);
            TrialGraph446.setConfig(node, cfg);
            if (commitPrefix != null) commitPrefix.run();
            if (screen != null) screen.settledlands$changed();
        };
    }

    public static Runnable clearSocketsAction(NpcDialogueGraphScreen screen,
            NpcDialogueGraph.Node node) {
        return () -> {
            if (screen == null || node == null) return;
            screen.settledlands$remember();
            TrialGraph446.setStartWaveTarget(node, -1);
            TrialGraph446.setWaveClearTarget(node, -1);
            TrialGraph446.setDestroyTarget(node, -1);
            screen.settledlands$changed();
        };
    }

    public static String shortId(String raw) {
        if (raw == null) return "";
        int colon = raw.indexOf(':');
        return colon >= 0 && colon + 1 < raw.length() ? raw.substring(colon + 1) : raw;
    }

    public static String safe(String key, String arg) {
        try {
            return Component.translatable(key, Component.literal(arg == null ? "" : arg)).getString();
        } catch (Throwable failure) {
            return key;
        }
    }

    private static void report(Throwable failure) {
        if (reported) return;
        reported = true;
        LOG.warn("trial graph client error: " + String.valueOf(failure));
    }
}
