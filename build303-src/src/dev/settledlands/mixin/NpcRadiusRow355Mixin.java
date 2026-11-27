package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.RadiusPass354;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.RadiusZoneUi456;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 355/456: the "Видимость" and "Форма зоны (Круг / Квадрат)" inspector rows of a radius
 * node, plus the compact zone-shape badge on {@code CHECK}/{@code RADIUS} canvas cards. Zero new lambdas.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcRadiusRow355Mixin implements RadiusZoneUi456.Host {

    @Shadow
    private NpcDialogueGraph graph;

    @Shadow
    private int selected;

    @Shadow
    private int inspectorX;

    @Shadow
    private int inspectorY;

    @Shadow
    private int inspectorW;

    @Shadow
    private int inspectorH;

    @Shadow
    private int sidebarScroll;

    @Shadow
    private int sidebarHeight;

    @Shadow
    private boolean buildingInspector;

    @Shadow
    private void remember() {
        throw new AssertionError();
    }

    @Shadow
    private void changed() {
        throw new AssertionError();
    }

    @Shadow
    private void widgets() {
        throw new AssertionError();
    }

    @Shadow
    private boolean save() {
        throw new AssertionError();
    }

    @Shadow
    private Button button(String key, int x, int y, int width, int height, Runnable action) {
        throw new AssertionError();
    }

    @Override
    public void settledlands$remember456() {
        remember();
    }

    @Override
    public void settledlands$changed456() {
        changed();
    }

    @Override
    public boolean settledlands$save456() {
        return save();
    }

    @Override
    public void settledlands$widgets456() {
        widgets();
    }

    @Override
    public Button settledlands$button456(String key, int x, int y, int width, int height,
            Runnable action) {
        return button(key, x, y, width, height, action);
    }

    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("TAIL"), remap = false)
    private void settledlands$drawZoneShapeBadge456(GuiGraphics graphics, NpcDialogueGraph.Node node,
            CallbackInfo ci) {
        RadiusZoneUi456.drawCardBadge((NpcDialogueGraphScreen) (Object) this, graphics, node);
    }

    @Inject(method = "widgets()V", at = @At("RETURN"), remap = false)
    private void settledlands$visibleRow(CallbackInfo ci) {
        if (graph == null || selected < 0) return;
        NpcDialogueGraph.Node node = graph.get(selected);
        if (!RadiusPass354.isRadiusNode(node)) return;

        int x = inspectorX + 8;
        int width = inspectorW - 16;
        int y = inspectorY + 24 - sidebarScroll + sidebarHeight + 2;

        buildingInspector = true;
        try {
            Button toggle = button("gui.settledlands.block.visible", x, y, width, 16, () -> {
                remember();
                RadiusPass354.setVisible(node, !RadiusPass354.visible(node));
                changed();
                save();     // the ring is drawn by the server: hand it the new flag now
                widgets();
            });
            if (toggle == null) return;
            toggle.setMessage(Component.translatable(RadiusPass354.visible(node)
                    ? "gui.settledlands.block.visible_on" : "gui.settledlands.block.visible_off"));
            toggle.visible = y >= inspectorY + 22
                    && y + toggle.getHeight() <= inspectorY + inspectorH - 4;
            sidebarHeight += 22;

            int shapeY = inspectorY + 24 - sidebarScroll + sidebarHeight + 2;
            sidebarHeight += RadiusZoneUi456.addShapeToggleRow(
                    this, node, x, shapeY, width, inspectorY, inspectorH);
        } finally {
            buildingInspector = false;
        }
    }
}
