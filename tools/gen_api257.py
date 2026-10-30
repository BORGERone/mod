#!/usr/bin/env python3
"""Compile-time Minecraft API silhouettes for build 257 (never packaged)."""
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_api255

FILES = dict(gen_api255.FILES)
FILES.update({
    'net/minecraft/resources/ResourceLocation.java': '''
package net.minecraft.resources;
public final class ResourceLocation {
    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) { return null; }
}
''',
    'net/minecraft/world/entity/ai/attributes/AttributeModifier.java': '''
package net.minecraft.world.entity.ai.attributes;
import net.minecraft.resources.ResourceLocation;
public final class AttributeModifier {
    public enum Operation { ADD_VALUE, ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL }
    public AttributeModifier(ResourceLocation id, double amount, Operation operation) {}
    public double amount() { return 0.0D; }
    public Operation operation() { return Operation.ADD_VALUE; }
}
''',
    'net/minecraft/world/entity/ai/attributes/AttributeInstance.java': '''
package net.minecraft.world.entity.ai.attributes;
import net.minecraft.resources.ResourceLocation;
public class AttributeInstance {
    public double getValue() { return 0.0D; }
    public AttributeModifier getModifier(ResourceLocation id) { return null; }
    public void removeModifier(ResourceLocation id) {}
    public void addOrReplacePermanentModifier(AttributeModifier modifier) {}
}
''',
    'net/minecraft/world/entity/ai/attributes/Attributes.java': '''
package net.minecraft.world.entity.ai.attributes;
import net.minecraft.core.Holder;
public final class Attributes {
    public static Holder<Attribute> ATTACK_DAMAGE;
    public static Holder<Attribute> MAX_HEALTH;
    public static Holder<Attribute> MOVEMENT_SPEED;
}
''',
})
_living = FILES['net/minecraft/world/entity/LivingEntity.java']
_living = _living.replace(
    "    public AttributeInstance getAttribute(Holder<Attribute> attr) { return null; }\n",
    "    public AttributeInstance getAttribute(Holder<Attribute> attr) { return null; }\n"
    "    public double getAttributeValue(Holder<Attribute> attr) { return 0.0D; }\n"
    "    public float getHealth() { return 0.0F; }\n"
    "    public float getMaxHealth() { return 0.0F; }\n"
    "    public void setHealth(float value) {}\n",
)
FILES['net/minecraft/world/entity/LivingEntity.java'] = _living
_guide = FILES['dev/settledlands/npc/GuideNpc.java']
_guide = _guide.replace(
    "    public double walkingNavigationSpeed(boolean returning) { return 1.0D; }\n",
    "    public double walkingNavigationSpeed(boolean returning) { return 1.0D; }\n"
    "    public IvyCombatAi ivyCombat() { return null; }\n",
)
FILES['dev/settledlands/npc/GuideNpc.java'] = _guide
FILES['dev/settledlands/npc/NpcStatsGraph.java'] = '''
package dev.settledlands.npc;
public final class NpcStatsGraph {
    public static final int TYPE = 16, DEFAULT = 10, MIN = 1, MAX = 100;
    public static int health(NpcDialogueGraph graph) { return DEFAULT; }
    public static int damage(NpcDialogueGraph graph) { return DEFAULT; }
    public static int speed(NpcDialogueGraph graph) { return DEFAULT; }
}
'''


def main(out: Path):
    for rel, source in FILES.items():
        path = out / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding='utf-8')

if __name__ == '__main__':
    main(Path(sys.argv[1]))
