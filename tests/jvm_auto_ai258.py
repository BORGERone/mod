#!/usr/bin/env python3
"""Build 258 runtime suite with the production 1.21.1 AttributeInstance ABI."""
import sys
import contextlib
import io
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import jvm_auto_ai257 as previous

base = previous.base
base.AUTO_AI_OVERRIDES['net/minecraft/world/entity/ai/attributes/AttributeInstance.java'] = '''
package net.minecraft.world.entity.ai.attributes;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
public class AttributeInstance {
    public double baseValue = 1.0;
    private final Map<ResourceLocation, AttributeModifier> modifiers = new HashMap<>();
    public AttributeInstance() {}
    public AttributeInstance(double baseValue) { this.baseValue = baseValue; }
    public void setBaseValue(double v) { this.baseValue = v; }
    public double getBaseValue() { return baseValue; }
    public AttributeModifier getModifier(ResourceLocation id) { return modifiers.get(id); }
    public boolean removeModifier(ResourceLocation id) { return modifiers.remove(id) != null; }
    public void addOrReplacePermanentModifier(AttributeModifier m) { modifiers.put(m.id(), m); }
    public double getValue() {
        double value = baseValue;
        for (AttributeModifier m : modifiers.values()) if (m.operation() == AttributeModifier.Operation.ADD_VALUE) value += m.amount();
        double beforeTotal = value;
        for (AttributeModifier m : modifiers.values()) if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) value += baseValue * m.amount();
        for (AttributeModifier m : modifiers.values()) if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) value *= 1.0 + m.amount();
        return value;
    }
}
'''
scenario = base.AUTO_AI_OVERRIDES['testing/AutoAi257Scenario.java']
needle = '        ServerLevel level = new ServerLevel();\n'
insert = '''        ServerLevel level = new ServerLevel();

        // Default graph values must safely remove absent/zero stat modifiers using the 1.21.1 boolean ABI.
        GuideNpc defaults = new GuideNpc(NpcContent.guide(), level);
        defaults.setProfile(stats(defaults, 10, 10, 10, false));
        level.addEntity(defaults);
        tick(defaults, step);
        ok(Math.abs(defaults.getMaxHealth() - 20.0) < 1.0e-6
                && Math.abs(defaults.getAttributeValue(Attributes.ATTACK_DAMAGE) - 4.0) < 1.0e-6
                && Math.abs(defaults.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.25) < 1.0e-6,
                "default Stats values must leave vanilla NPC attributes unchanged");
'''
assert scenario.count(needle) == 1
scenario = scenario.replace(needle, insert, 1)
needle2 = '''        ok(Math.abs(measured.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.125) < 1.0e-6,
                "Speed=5 must halve NPC movement attribute");
'''
insert2 = needle2 + '''        measured.setProfile(stats(measured, 10, 10, 10, false));
        tick(measured, step);
        ok(Math.abs(measured.getMaxHealth() - 20.0) < 1.0e-6
                && Math.abs(measured.getAttributeValue(Attributes.ATTACK_DAMAGE) - 4.0) < 1.0e-6
                && Math.abs(measured.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.25) < 1.0e-6,
                "returning Stats values to 10 must remove all settledlands modifiers");
'''
assert scenario.count(needle2) == 1
scenario = scenario.replace(needle2, insert2, 1)
base.AUTO_AI_OVERRIDES['testing/AutoAi257Scenario.java'] = scenario

if __name__ == '__main__':
    captured = io.StringIO()
    with contextlib.redirect_stdout(captured):
        previous.main()
    print(captured.getvalue().replace('on build 257', 'on build 258').replace('PASS build 257:', 'PASS build 258:'), end='')
