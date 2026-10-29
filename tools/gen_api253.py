#!/usr/bin/env python3
"""Minimal compile-time API declarations for src/build253 (never packaged into JAR)."""
import pathlib

FILES = {
    'net/minecraft/nbt/CompoundTag.java': (
        'package net.minecraft.nbt;\n'
        'public class CompoundTag {\n'
        '    public CompoundTag() {}\n'
        '    public void putString(String key, String value) {}\n'
        '}\n'
    ),
    'net/minecraft/world/item/Item.java': (
        'package net.minecraft.world.item;\n'
        'public class Item {}\n'
    ),
    'dev/settledlands/npc/GuideNpc.java': (
        'package dev.settledlands.npc;\n'
        'public class GuideNpc {}\n'
    ),
    'dev/settledlands/npc/GuideTriggerNpc.java': (
        'package dev.settledlands.npc;\n'
        'public class GuideTriggerNpc extends GuideNpc {}\n'
    ),
    'dev/settledlands/npc/NpcContent.java': (
        'package dev.settledlands.npc;\n'
        'import net.minecraft.world.item.Item;\n'
        'public final class NpcContent {\n'
        '    public static Item spawnEgg() { return null; }\n'
        '    public static Item triggerEgg() { return null; }\n'
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
