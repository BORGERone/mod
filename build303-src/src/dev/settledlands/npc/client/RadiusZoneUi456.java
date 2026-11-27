package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.RadiusPass354;
import dev.settledlands.npc.RadiusZone456;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Build 456: client UI helpers for Square vs Circle radius zones on {@code CHECK} ("В радиусе")
 * and {@code RADIUS} ("Цель") nodes. Kept external to {@code dev.settledlands.mixin} so
 * {@code NpcRadiusRow355Mixin} introduces zero synthetic lambdas.
 */
public final class RadiusZoneUi456 {

    public interface Host {
        void settledlands$remember456();

        void settledlands$changed456();

        boolean settledlands$save456();

        void settledlands$widgets456();

        Button settledlands$button456(String key, int x, int y, int width, int height,
                Runnable action);
    }

    private RadiusZoneUi456() {}

    /** Adds the Square/Circle shape toggle row under "Видимость" and returns its consumed height. */
    public static int addShapeToggleRow(
            Host host,
            NpcDialogueGraph.Node node,
            int x,
            int y,
            int width,
            int inspectorY,
            int inspectorH) {
        if (host == null || node == null || !RadiusPass354.isRadiusNode(node)) return 0;
        Button toggle = host.settledlands$button456(
                "gui.settledlands.radius_shape_btn",
                x, y, width, 16,
                () -> {
                    host.settledlands$remember456();
                    RadiusZone456.setSquare(node, !RadiusZone456.isSquare(node));
                    host.settledlands$changed456();
                    host.settledlands$save456();
                    host.settledlands$widgets456();
                });
        if (toggle == null) return 0;
        toggle.setMessage(Component.translatable(RadiusZone456.isSquare(node)
                ? "gui.settledlands.radius_shape_square"
                : "gui.settledlands.radius_shape_circle"));
        toggle.visible = y >= inspectorY + 22
                && y + toggle.getHeight() <= inspectorY + inspectorH - 4;
        return 22;
    }

    /** Renders a compact shape badge ("□ Квадрат" or "○ Круг") on the top-right header of CHECK/RADIUS cards. */
    public static void drawCardBadge(
            NpcDialogueGraphScreen screen,
            GuiGraphics graphics,
            NpcDialogueGraph.Node node) {
        if (screen == null || graphics == null || node == null || !RadiusPass354.isRadiusNode(node)) {
            return;
        }
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        Font font = screen.settledlands$font();
        if (canvas == null || font == null) return;

        boolean sq = RadiusZone456.isSquare(node);
        String tag = (sq ? "\u25A1 " : "\u25CB ") + Component.translatable(sq
                ? "gui.settledlands.radius_shape_square_short"
                : "gui.settledlands.radius_shape_circle_short").getString();
        int cardW = GraphCardDesc448.cardWidth(node);
        int pillW = font.width(tag) + 8;
        int px = Math.max(4, cardW - pillW - 5);
        int py = 4;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.screenX(node.x), canvas.screenY(node.y), 0.0D);
        pose.scale((float) canvas.zoom, (float) canvas.zoom, 1.0F);
        try {
            graphics.fill(px, py, px + pillW, py + 13, sq ? 0xFF1F3B2C : 0xFF1A2538);
            graphics.fill(px, py, px + 2, py + 13, sq ? 0xFF4ADE80 : 0xFF60A5FA);
            graphics.drawString(font, tag, px + 4, py + 3,
                    sq ? 0xFFD1FAE5 : 0xFFDBEAFE, false);
        } finally {
            pose.popPose();
        }
    }
}
