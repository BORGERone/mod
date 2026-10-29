#!/usr/bin/env python3
"""Minimal compile-time API declarations for src/build254 (never packaged into JAR)."""
import pathlib

FILES = {
    'net/minecraft/nbt/CompoundTag.java': (
        'package net.minecraft.nbt;\n'
        'public class CompoundTag {\n'
        '    public boolean contains(String key, int type) { return false; }\n'
        '    public int getInt(String key) { return 0; }\n'
        '    public String getString(String key) { return ""; }\n'
        '    public ListTag getList(String key, int type) { return null; }\n'
        '}\n'
    ),
    'net/minecraft/nbt/ListTag.java': (
        'package net.minecraft.nbt;\n'
        'public class ListTag {\n'
        '    public int size() { return 0; }\n'
        '    public CompoundTag getCompound(int index) { return null; }\n'
        '}\n'
    ),
    'net/minecraft/network/chat/Component.java': (
        'package net.minecraft.network.chat;\n'
        'public interface Component {\n'
        '    static MutableComponent translatable(String key) { return null; }\n'
        '    String getString();\n'
        '}\n'
    ),
    'net/minecraft/network/chat/MutableComponent.java': (
        'package net.minecraft.network.chat;\n'
        'public class MutableComponent implements Component {\n'
        '    public String getString() { return ""; }\n'
        '}\n'
    ),
    'com/mojang/blaze3d/vertex/PoseStack.java': (
        'package com.mojang.blaze3d.vertex;\n'
        'public class PoseStack {\n'
        '    public void pushPose() {}\n'
        '    public void popPose() {}\n'
        '    public void translate(float x, float y, float z) {}\n'
        '    public void translate(double x, double y, double z) {}\n'
        '    public void scale(float x, float y, float z) {}\n'
        '}\n'
    ),
    'net/minecraft/client/gui/Font.java': (
        'package net.minecraft.client.gui;\n'
        'public class Font {\n'
        '    public int width(String text) { return 0; }\n'
        '}\n'
    ),
    'net/minecraft/client/gui/GuiGraphics.java': (
        'package net.minecraft.client.gui;\n'
        'import com.mojang.blaze3d.vertex.PoseStack;\n'
        'public class GuiGraphics {\n'
        '    public PoseStack pose() { return null; }\n'
        '    public void flush() {}\n'
        '    public void fill(int x1, int y1, int x2, int y2, int color) {}\n'
        '    public int drawString(Font f, String s, int x, int y, int color, boolean shadow) { return 0; }\n'
        '}\n'
    ),
    'dev/settledlands/model/BbModelStorage.java': (
        'package dev.settledlands.model;\n'
        'import java.nio.file.Path;\n'
        'public final class BbModelStorage {\n'
        '    public static Path getModelsDir() { return null; }\n'
        '}\n'
    ),
    'dev/settledlands/npc/NpcProfile.java': (
        'package dev.settledlands.npc;\n'
        'public class NpcProfile {\n'
        '    public String texture;\n'
        '    public int scale;\n'
        '    public boolean lockInteraction;\n'
        '    public NpcDialogueGraph dialogueGraph;\n'
        '    public NpcProfile() {}\n'
        '}\n'
    ),
    'dev/settledlands/npc/NpcDialogueGraph.java': (
        'package dev.settledlands.npc;\n'
        'import java.util.*;\n'
        'public final class NpcDialogueGraph {\n'
        '    public int entry;\n'
        '    public final LinkedHashMap<Integer, Node> nodes = new LinkedHashMap<>();\n'
        '    public Node get(int id) { return null; }\n'
        '    public Node add(int type, int x, int y) { return null; }\n'
        '    public boolean settledlands$removeOriginal(int id) { return false; }\n'
        '    public Node settledlands$addOriginal(int type, int x, int y) { return null; }\n'
        '    public boolean settledlands$connectOriginal(int a, int b, int port) { return false; }\n'
        '    public static class Node {\n'
        '        public int id, type, x, y, special, varValue, checkRadius, checkCooldown, next, falseNext, varInputNode;\n'
        '        public List<Integer> choices, triggers, randomBranches;\n'
        '        public Node(int id, int type, int x, int y) {}\n'
        '    }\n'
        '}\n'
    ),
    'dev/settledlands/npc/GuideNpc.java': (
        'package dev.settledlands.npc;\n'
        'public class GuideNpc {\n'
        '    protected int initialScale() { return 100; }\n'
        '    protected boolean initialLockInteraction() { return false; }\n'
        '}\n'
    ),
    'dev/settledlands/npc/GuideTriggerNpc.java': (
        'package dev.settledlands.npc;\n'
        'public class GuideTriggerNpc extends GuideNpc {}\n'
    ),
    'dev/settledlands/npc/GuideCorpseNpc.java': (
        'package dev.settledlands.npc;\n'
        'public class GuideCorpseNpc extends GuideNpc {}\n'
    ),
    'dev/settledlands/npc/client/NpcUi.java': (
        'package dev.settledlands.npc.client;\n'
        'import net.minecraft.client.gui.GuiGraphics;\n'
        'public class NpcUi {\n'
        '    public static class NpcDropdown {\n'
        '        public void renderPopup(GuiGraphics g, int mouseX, int mouseY) {}\n'
        '    }\n'
        '}\n'
    ),
    'dev/settledlands/npc/client/NpcGraphCanvas.java': (
        'package dev.settledlands.npc.client;\n'
        'import dev.settledlands.npc.NpcDialogueGraph;\n'
        'public class NpcGraphCanvas {\n'
        '    public double panX, panY, zoom;\n'
        '    public int left, right, top, bottom;\n'
        '    public double screenX(double d) { return 0; }\n'
        '    public double screenY(double d) { return 0; }\n'
        '    public double worldX(double d) { return 0; }\n'
        '    public double worldY(double d) { return 0; }\n'
        '    public boolean contains(double x, double y) { return false; }\n'
        '    public int hit(NpcDialogueGraph g, double x, double y) { return -1; }\n'
        '    public int settledlands$hitPortOriginal(NpcDialogueGraph g, double x, double y, boolean o) { return -1; }\n'
        '}\n'
    ),
    'dev/settledlands/npc/client/NpcDialogueGraphScreen.java': (
        'package dev.settledlands.npc.client;\n'
        'import dev.settledlands.npc.NpcDialogueGraph;\n'
        'import net.minecraft.client.gui.Font;\n'
        'import net.minecraft.client.gui.GuiGraphics;\n'
        'public class NpcDialogueGraphScreen {\n'
        '    public NpcDialogueGraph settledlands$graph() { return null; }\n'
        '    public NpcGraphCanvas settledlands$canvas() { return null; }\n'
        '    public Font settledlands$font() { return null; }\n'
        '    public boolean settledlands$isSelected(int id) { return false; }\n'
        '    public void settledlands$drawOriginal(GuiGraphics g, NpcDialogueGraph.Node n) {}\n'
        '    public void settledlands$changed() {}\n'
        '    public void settledlands$remember() {}\n'
        '    public boolean settledlands$contextOpen() { return false; }\n'
        '    public boolean settledlands$insideInspector(double x, double y) { return false; }\n'
        '    public NpcUi.NpcDropdown activeDropdown() { return null; }\n'
        '    public boolean settledlands$mouseClickedOriginal(double x, double y, int b) { return false; }\n'
        '    public void settledlands$hideStatsInspector() {}\n'
        '}\n'
    ),
}


def main(out: pathlib.Path):
    for rel, src in FILES.items():
        p = out / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(src, encoding='utf-8')


if __name__ == '__main__':
    import sys
    main(pathlib.Path(sys.argv[1]))
