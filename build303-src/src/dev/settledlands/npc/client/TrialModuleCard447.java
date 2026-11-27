package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialModule447;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Build 447: canvas card renderer and socket hit-testing for the 8 specialized
 * {@link TrialModule447} cards ({@code mode, radius, wave, timing, mobs, rewards, ominous, protect}).
 */
public final class TrialModuleCard447 {

    public static final int CARD_W = NpcGraphCanvas.NODE_W;
    public static final int CARD_H = NpcGraphCanvas.NODE_H;
    public static final int OUTPUT_PIN_Y = NpcGraphCanvas.NODE_H / 2;
    private static final double HIT = 8.0D;

    private static final int SELECTED = -1458585;
    private static final int IDLE = -12168601;
    private static final int BODY = -14274756;
    private static final int HEADER = -15658735;
    private static final int EDGE = -5723992;
    private static final int LABEL = -5592406;
    private static final int BRIGHT = 0xFFE8EEF5;

    private TrialModuleCard447() {}

    public static double outputPinScreenX(NpcGraphCanvas canvas, Node mod) {
        return canvas == null || mod == null ? 0.0D : canvas.screenX(mod.x + CARD_W);
    }

    public static double outputPinScreenY(NpcGraphCanvas canvas, Node mod) {
        return canvas == null || mod == null ? 0.0D : canvas.screenY(mod.y + OUTPUT_PIN_Y);
    }

    public static void draw(NpcDialogueGraphScreen screen, GuiGraphics graphics, Node node) {
        if (screen == null || graphics == null || node == null) return;
        int slot = TrialModule447.slotOf(node);
        if (slot < 0) return;

        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        NpcDialogueGraph graph = screen.settledlands$graph();
        if (canvas == null || font == null) return;

        int accent = TrialModule447.color(slot);
        boolean selected = screen.settledlands$isSelected(node.id);
        Node targetHub = (graph != null && node.next >= 0) ? graph.get(node.next) : null;
        boolean connected = TrialGraph446.isTrialNode(targetHub);
        int textMax = CARD_W - 22;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0D);
        pose.scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
        try {
            graphics.fill(-2, -2, CARD_W + 2, CARD_H + 2, selected ? SELECTED : IDLE);
            graphics.fill(0, 0, CARD_W, CARD_H, BODY);
            graphics.fill(0, 0, CARD_W, 20, HEADER);
            graphics.fill(0, 19, CARD_W, 20, accent);

            String idTag = "#" + node.id;
            int idW = font.width(idTag);
            graphics.drawString(font, clip(font, tr(TrialModule447.titleKey(slot)),
                    CARD_W - 16 - idW - 4), 8, 6, accent, false);
            graphics.drawString(font, idTag, CARD_W - 8 - idW, 6, EDGE, false);

            String[] lines = cardLines(node);
            graphics.drawString(font, clip(font, lines[0], textMax), 8, 27, BRIGHT, false);
            if (!lines[1].isEmpty()) {
                graphics.drawString(font, clip(font, lines[1], textMax), 8, 41, LABEL, false);
            }
            if (!lines[2].isEmpty()) {
                graphics.drawString(font, clip(font, lines[2], textMax), 8, 55, LABEL, false);
            }

            graphics.fill(8, 75, CARD_W - 12, 76, 0xFF283444);
            String linkText = connected
                    ? "\u2192 " + tr(TrialGraph446.TITLE_KEY) + " #" + targetHub.id
                    : tr("gui.settledlands.trial.mod.unlinked");
            graphics.drawString(font, clip(font, linkText, textMax), 8, 82,
                    connected ? accent : EDGE, false);

            if (connected) {
                DoorCard382.diamond(graphics, CARD_W, OUTPUT_PIN_Y, 5, accent);
            } else {
                DoorCard382.diamond(graphics, CARD_W, OUTPUT_PIN_Y, 5, TrialModule447.dimColor(slot));
                graphics.fill(CARD_W - 2, OUTPUT_PIN_Y - 2, CARD_W + 2, OUTPUT_PIN_Y + 2, BODY);
            }
        } finally {
            pose.popPose();
        }
    }

    public static String[] cardLines(Node node) {
        int slot = TrialModule447.slotOf(node);
        return switch (slot) {
            case TrialModule447.SLOT_MODE -> {
                TrialModule447.ModeData d = TrialModule447.readMode(node);
                String modeName = tr(modeKey(d.mode));
                String scale = yesNo(d.scaleWithPlayers);
                yield new String[] {
                    safe("gui.settledlands.trial.line_mode", modeName),
                    safe("gui.settledlands.trial.mod.card_scale", scale),
                    ""
                };
            }
            case TrialModule447.SLOT_RADIUS -> {
                TrialModule447.RadiusData d = TrialModule447.readRadius(node);
                yield new String[] {
                    safe("gui.settledlands.trial.mod.card_act",
                            TrialModuleClient447.rangeText(d.activationRange, d.activationRangeText)),
                    safe("gui.settledlands.trial.mod.card_spawn",
                            TrialModuleClient447.rangeText(d.spawnRange, d.spawnRangeText)),
                    ""
                };
            }
            case TrialModule447.SLOT_WAVE -> {
                TrialModule447.WaveData d = TrialModule447.readWave(node);
                String tot = TrialModuleClient447.rangeText(d.totalMobs, d.totalMobsText);
                String totP = TrialModuleClient447.rangeText(d.totalMobsAddedPerPlayer, d.totalMobsAddedPerPlayerText);
                String sim = TrialModuleClient447.rangeText(d.simultaneousMobs, d.simultaneousMobsText);
                String simP = TrialModuleClient447.rangeText(d.simultaneousMobsAddedPerPlayer, d.simultaneousMobsAddedPerPlayerText);
                String batch = TrialModuleClient447.rangeText(d.spawnBatchSize, d.spawnBatchSizeText);
                yield new String[] {
                    safe("gui.settledlands.trial.mod.card_total", tot + " (+" + totP + ")"),
                    safe("gui.settledlands.trial.mod.card_sim", sim + " (+" + simP + ")"),
                    safe("gui.settledlands.trial.mod.card_batch", batch)
                };
            }
            case TrialModule447.SLOT_TIMING -> {
                TrialModule447.TimingData d = TrialModule447.readTiming(node);
                String cdStr = d.cooldownTicksText != null && !d.cooldownTicksText.isBlank()
                        ? d.cooldownTicksText + "t"
                        : Math.max(0, d.cooldownTicks / 20) + "s (" + d.cooldownTicks + "t)";
                yield new String[] {
                    safe("gui.settledlands.trial.mod.card_delay",
                            TrialModuleClient447.delayText(d)),
                    safe("gui.settledlands.trial.mod.card_cd", cdStr),
                    ""
                };
            }
            case TrialModule447.SLOT_MOBS -> {
                List<TrialConfig446.SpawnEntry> mobs = TrialModule447.readMobs(node);
                String pool = TrialModule447.mobPoolFile(node);
                String l1 = pool.isEmpty()
                        ? safe("gui.settledlands.trial.mod.card_mob_inline", String.valueOf(mobs.size()))
                        : Component.translatable("gui.settledlands.trial.mod.card_pool_file",
                                pool, String.valueOf(mobs.size())).getString();
                String l2 = mobs.isEmpty() ? "" : "1. " + TrialMobPreview449.displayName(mobs.get(0).entityId)
                        + " (w:" + mobs.get(0).displayWeight() + ")";
                String l3 = mobs.size() > 1 ? "2. " + TrialMobPreview449.displayName(mobs.get(1).entityId)
                        + " (w:" + mobs.get(1).displayWeight() + ")"
                        + (mobs.size() > 2 ? " +" + (mobs.size() - 2) : "")
                        : "";
                yield new String[] { l1, l2, l3 };
            }
            case TrialModule447.SLOT_REWARDS -> {
                List<TrialConfig446.RewardEntry> rewards = TrialModule447.readRewards(node);
                String pool = TrialModule447.rewardPoolFile(node);
                String l1 = pool.isEmpty()
                        ? safe("gui.settledlands.trial.mod.card_rew_inline", String.valueOf(rewards.size()))
                        : Component.translatable("gui.settledlands.trial.mod.card_pool_file",
                                pool, String.valueOf(rewards.size())).getString();
                String l2 = rewards.isEmpty() ? tr("gui.settledlands.trial.mod.card_rew_empty")
                        : formatRewardShort(1, rewards.get(0));
                String l3 = rewards.size() > 1
                        ? formatRewardShort(2, rewards.get(1))
                                + (rewards.size() > 2 ? " +" + (rewards.size() - 2) : "")
                        : "";
                yield new String[] { l1, l2, l3 };
            }
            case TrialModule447.SLOT_OMINOUS -> {
                TrialModule447.OminousData d = TrialModule447.readOminous(node);
                String mult = (d.mobMultiplierText != null && !d.mobMultiplierText.isBlank())
                        ? "x" + d.mobMultiplierText
                        : String.format(Locale.ROOT, "x%.2f", d.mobMultiplier);
                String iv = TrialModuleClient447.rangeText(d.hazardIntervalTicks, d.hazardIntervalTicksText);
                yield new String[] {
                    safe("gui.settledlands.trial.mod.card_om_mult", mult),
                    safe("gui.settledlands.trial.mod.card_om_flags",
                            yesNo(d.equipment) + " / " + yesNo(d.hazards) + " (" + iv + "t)"),
                    safe("gui.settledlands.trial.mod.card_om_keys", yesNo(d.keyConversion))
                };
            }
            case TrialModule447.SLOT_PROTECT -> {
                TrialModule447.ProtectData d = TrialModule447.readProtect(node);
                String rTxt = TrialModuleClient447.rangeText(d.noBreakRadius, d.noBreakRadiusText);
                String sTxt = TrialModuleClient447.rangeText(d.noBreakSeconds, d.noBreakSecondsText);
                String wTxt = TrialModuleClient447.rangeText(d.selfDestructAfterWaves, d.selfDestructAfterWavesText);
                String cTxt = TrialModuleClient447.rangeText(d.selfDestructChancePercent, d.selfDestructChancePercentText);
                yield new String[] {
                    safe("gui.settledlands.trial.mod.card_nb",
                            d.noBreakEnabled
                                    ? yesNo(true) + " (R" + rTxt + ", " + sTxt + "s)"
                                    : yesNo(false)),
                    safe("gui.settledlands.trial.mod.card_sd",
                            d.selfDestruct
                                    ? wTxt + "w · " + cTxt + "%"
                                    : yesNo(false)),
                    ""
                };
            }
            default -> new String[] { "", "", "" };
        };
    }

    private static String formatRewardShort(int num, TrialConfig446.RewardEntry r) {
        String count = r.minCount == r.maxCount ? String.valueOf(r.minCount)
                : r.minCount + "-" + r.maxCount;
        return num + ". " + TrialGraphClient446.shortId(r.itemId)
                + " x" + count + " (" + r.displayChance() + "%)";
    }

    public static Node moduleAtPin(NpcDialogueGraph graph, NpcGraphCanvas canvas,
            double mouseX, double mouseY) {
        if (graph == null || canvas == null || graph.nodes == null) return null;
        double limit = Math.max(HIT * 0.65D, HIT * canvas.zoom);
        for (Node node : graph.nodes.values()) {
            if (!TrialModule447.isModuleNode(node)) continue;
            double dx = mouseX - outputPinScreenX(canvas, node);
            double dy = mouseY - outputPinScreenY(canvas, node);
            if (dx * dx + dy * dy <= limit * limit) return node;
        }
        return null;
    }

    /** Returns {@code {hub.id, slot}} if the cursor is on one of the 8 left input slots of a hub card, else null. */
    public static int[] hubSlotAt(NpcDialogueGraph graph, NpcGraphCanvas canvas,
            double mouseX, double mouseY) {
        if (graph == null || canvas == null || graph.nodes == null) return null;
        double limit = Math.max(HIT * 0.65D, HIT * canvas.zoom);
        for (Node hub : graph.nodes.values()) {
            if (!TrialGraph446.isTrialNode(hub)) continue;
            double px = TrialCard446.inputPinScreenX(canvas, hub);
            double dx = mouseX - px;
            if (Math.abs(dx) > limit) continue;
            for (int s = 0; s < TrialModule447.MODULE_COUNT; s++) {
                double py = TrialCard446.inputPinScreenY(canvas, hub, s);
                double dy = mouseY - py;
                if (dx * dx + dy * dy <= limit * limit) {
                    return new int[] { hub.id, s };
                }
            }
        }
        return null;
    }

    /** Returns the connected module node whose wire to the hub passes under the cursor, or null. */
    public static Node wireAt(NpcDialogueGraph graph, NpcGraphCanvas canvas,
            double mouseX, double mouseY) {
        if (graph == null || canvas == null || graph.nodes == null) return null;
        for (Node mod : graph.nodes.values()) {
            int slot = TrialModule447.slotOf(mod);
            if (slot < 0 || mod.next < 0) continue;
            Node hub = graph.get(mod.next);
            if (!TrialGraph446.isTrialNode(hub)) continue;
            double x1 = outputPinScreenX(canvas, mod);
            double y1 = outputPinScreenY(canvas, mod);
            double x2 = TrialCard446.inputPinScreenX(canvas, hub);
            double y2 = TrialCard446.inputPinScreenY(canvas, hub, slot);
            if (PursuitGraphNode272.hitValueWire(canvas, x1, y1, x2, y2, mouseX, mouseY)) {
                return mod;
            }
        }
        return null;
    }

    public static String modeKey(String mode) {
        if (TrialConfig446.MODE_OMINOUS.equals(mode)) return "gui.settledlands.trial.mode.ominous";
        if (TrialConfig446.MODE_SPECIAL.equals(mode)) return "gui.settledlands.trial.mode.special";
        return "gui.settledlands.trial.mode.normal";
    }

    public static String yesNo(boolean v) {
        return tr(v ? "gui.settledlands.trial.cfg.yes" : "gui.settledlands.trial.cfg.no");
    }

    private static String safe(String key, String arg) {
        try {
            return Component.translatable(key, Component.literal(arg == null ? "" : arg)).getString();
        } catch (Throwable failure) {
            return key;
        }
    }

    private static String tr(String key) {
        try {
            return Component.translatable(key).getString();
        } catch (Throwable failure) {
            return key;
        }
    }

    private static String clip(Font font, String text, int width) {
        if (font == null || text == null || font.width(text) <= width) return text == null ? "" : text;
        String cut = text;
        while (!cut.isEmpty() && font.width(cut + "\u2026") > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.isEmpty() ? "" : cut + "\u2026";
    }
}
