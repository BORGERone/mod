#!/usr/bin/env python3
"""Targeted Java 21 runtime regression tests for Build 257 NPC stats and combat priority."""
import argparse
import pathlib
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import jvm_auto_ai255 as base

base.AUTO_AI_OVERRIDES.update({
    'net/minecraft/resources/ResourceLocation.java': '''
package net.minecraft.resources;
import java.util.Objects;
public final class ResourceLocation {
    private final String value;
    private ResourceLocation(String namespace, String path) { value = namespace + ":" + path; }
    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }
    public static ResourceLocation tryParse(String value) {
        int i = value == null ? -1 : value.indexOf(':');
        return i < 1 ? null : new ResourceLocation(value.substring(0, i), value.substring(i + 1));
    }
    @Override public boolean equals(Object o) { return o instanceof ResourceLocation r && value.equals(r.value); }
    @Override public int hashCode() { return Objects.hash(value); }
}
''',
    'net/minecraft/world/entity/ai/attributes/AttributeModifier.java': '''
package net.minecraft.world.entity.ai.attributes;
import net.minecraft.resources.ResourceLocation;
public final class AttributeModifier {
    public enum Operation { ADD_VALUE, ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL }
    private final ResourceLocation id;
    private final double amount;
    private final Operation operation;
    public AttributeModifier(ResourceLocation id, double amount, Operation operation) {
        this.id = id; this.amount = amount; this.operation = operation;
    }
    public ResourceLocation id() { return id; }
    public double amount() { return amount; }
    public Operation operation() { return operation; }
}
''',
    'net/minecraft/world/entity/ai/attributes/AttributeInstance.java': '''
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
    public void removeModifier(ResourceLocation id) { modifiers.remove(id); }
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
''',
    'net/minecraft/world/entity/LivingEntity.java': '''
package net.minecraft.world.entity;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
public class LivingEntity extends Entity {
    private final Map<Holder<Attribute>, AttributeInstance> attrs = new IdentityHashMap<>();
    private final RandomSource random = RandomSource.create(12345L);
    public float health = 20f;
    public float yBodyRot = 0f;
    public float yHeadRot = 0f;
    public float xxa = 0f, yya = 0f, zza = 0f;
    public boolean lineOfSight = true;
    public int swingCount = 0;
    public float totalDamageTaken = 0f;
    public DamageSource lastDamageSource;
    public LivingEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
        attrs.put(Attributes.SCALE, new AttributeInstance(1.0));
        attrs.put(Attributes.MAX_HEALTH, new AttributeInstance(20.0));
        attrs.put(Attributes.ATTACK_DAMAGE, new AttributeInstance(4.0));
        attrs.put(Attributes.MOVEMENT_SPEED, new AttributeInstance(0.25));
    }
    public AttributeInstance getAttribute(Holder<Attribute> h) { return attrs.get(h); }
    public double getAttributeValue(Holder<Attribute> h) { AttributeInstance a = attrs.get(h); return a == null ? 0.0 : a.getValue(); }
    public float getMaxHealth() { return (float) getAttributeValue(Attributes.MAX_HEALTH); }
    public RandomSource getRandom() { return random; }
    public boolean hasLineOfSight(Entity target) { return lineOfSight; }
    public void setHealth(float h) { health = h; if (h <= 0f) alive = false; }
    public float getHealth() { return health; }
    public boolean hurt(DamageSource source, float amount) {
        if (!isAlive() || amount <= 0f) return false;
        lastDamageSource = source; totalDamageTaken += amount; setHealth(health - amount); return true;
    }
    public void swing(InteractionHand hand) { swingCount++; }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
}
''',
    'dev/settledlands/npc/IvyCombatAi.java': '''
package dev.settledlands.npc;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
public class IvyCombatAi {
    public static int fireballCalls = 0;
    public static int legacyTickCalls = 0;
    private final GuideNpc npc;
    private LivingEntity target;
    public boolean forceLegacyConsume;
    public IvyCombatAi(GuideNpc npc) { this.npc = npc; }
    public boolean tick() {
        legacyTickCalls++;
        if (forceLegacyConsume && target != null) { launchFireball(); return true; }
        return false;
    }
    public LivingEntity combatTarget() { return target; }
    public void setCombatTarget(LivingEntity target) { this.target = target; }
    public void onHurt(DamageSource source) {}
    public void launchFireball() { fireballCalls++; }
}
''',
    'testing/AutoAi257Scenario.java': '''
package testing;
import dev.settledlands.npc.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.fml.loading.FMLPaths;

public final class AutoAi257Scenario {
    private static void ok(boolean c, String m) { if (!c) throw new AssertionError(m); }
    private static void mode(GuideNpc npc, byte value) throws Exception {
        Field f = GuideNpc.class.getDeclaredField("DATA_AI_MODE"); f.setAccessible(true);
        EntityDataAccessor<?> accessor = (EntityDataAccessor<?>) f.get(null);
        Field d = Entity.class.getDeclaredField("entityData"); d.setAccessible(true);
        SynchedEntityData data = (SynchedEntityData) d.get(npc);
        data.set((EntityDataAccessor) accessor, Byte.valueOf(value));
    }
    private static void tick(GuideNpc npc, Method step) throws Exception {
        npc.tickCount++;
        step.invoke(npc);
        npc.getNavigation().stepMovement();
    }
    private static NpcProfile stats(GuideNpc npc, int h, int d, int s, boolean attack) {
        NpcProfile p = npc.profile().copy();
        p.dialogueGraph = new NpcDialogueGraph();
        NpcStatsGraph.ensure(p.dialogueGraph);
        NpcDialogueGraph.Node n = NpcStatsGraph.find(p.dialogueGraph);
        n.varValue = h; n.checkRadius = d; n.checkCooldown = s;
        NpcAutoAi255.setAutoAttackHostiles(p, attack);
        NpcAutoAi255.setAggroRadius(p, 12);
        return p;
    }
    public static void main(String[] args) throws Exception {
        FMLPaths.currentGameDir = Path.of(args[0]);
        Method step = GuideNpc.class.getDeclaredMethod("customServerAiStep"); step.setAccessible(true);
        ServerLevel level = new ServerLevel();

        // Full node values are applied to actual NPC attributes and health is clamped on decrease.
        GuideNpc measured = new GuideNpc(NpcContent.guide(), level);
        measured.setPos(10.5, 64.0, 10.5);
        measured.setProfile(stats(measured, 20, 20, 20, false));
        level.addEntity(measured);
        tick(measured, step);
        ok(Math.abs(measured.getMaxHealth() - 40.0) < 1.0e-6, "Health=20 must set NPC max health to 40 HP");
        ok(Math.abs(measured.getAttributeValue(Attributes.ATTACK_DAMAGE) - 8.0) < 1.0e-6,
                "Damage=20 must double NPC attack damage attribute");
        ok(Math.abs(measured.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.5) < 1.0e-6,
                "Speed=20 must double NPC movement attribute");
        ok(Math.abs(NpcAutoAiEngine255.navigationSpeedFactor(measured) - 1.0) < 1.0e-6,
                "Speed=20 must receive exactly half the node-scaled pace while calm");
        measured.setHealth(35.0f);
        measured.setProfile(stats(measured, 5, 5, 5, false));
        tick(measured, step);
        ok(Math.abs(measured.getMaxHealth() - 10.0) < 1.0e-6 && measured.getHealth() <= 10.0f,
                "Health=5 must set max to 10 HP and clamp current health");
        ok(Math.abs(measured.getAttributeValue(Attributes.ATTACK_DAMAGE) - 2.0) < 1.0e-6,
                "Damage=5 must halve NPC attack damage");
        ok(Math.abs(measured.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.125) < 1.0e-6,
                "Speed=5 must halve NPC movement attribute");

        // A hostile toggle must override a legacy AI tick that would consume the step/fire a projectile.
        ServerLevel combatLevel = new ServerLevel();
        GuideNpc guard = new GuideNpc(NpcContent.guide(), combatLevel);
        guard.setPos(50.5, 64.0, 50.5);
        guard.setProfile(stats(guard, 20, 20, 20, true));
        combatLevel.addEntity(guard);
        Zombie hostile = new Zombie(combatLevel);
        hostile.setPos(58.5, 64.0, 50.5);
        hostile.health = 60.0f;
        combatLevel.addEntity(hostile);
        mode(guard, (byte) 1);
        guard.ivyCombat().setCombatTarget(hostile);
        guard.ivyCombat().forceLegacyConsume = true;
        IvyCombatAi.fireballCalls = IvyCombatAi.legacyTickCalls = 0;
        tick(guard, step);
        ok(IvyCombatAi.legacyTickCalls == 0 && IvyCombatAi.fireballCalls == 0,
                "enabled Auto-AI must bypass legacy combat tick and its projectile behavior");
        ok(NpcAutoAiEngine255.of(guard).currentTarget() == hostile,
                "enabled Auto-AI must acquire the hostile mob even when legacy aiMode is active");
        ok(Math.abs(NpcAutoAiEngine255.navigationSpeedFactor(guard) - 2.0) < 1.0e-6,
                "Speed=20 combat pace must be exactly twice the same node's calm pace");
        hostile.setPos(51.7, 64.0, 50.5);
        float before = hostile.totalDamageTaken;
        for (int i = 0; i < 80 && hostile.totalDamageTaken == before; i++) tick(guard, step);
        ok(hostile.totalDamageTaken > before && hostile.totalDamageTaken >= 0.25f,
                "autonomous combat must land a melee hit against the hostile mob; damage=" + hostile.totalDamageTaken
                + " target=" + NpcAutoAiEngine255.of(guard).currentTarget()
                + " anim=" + guard.getCustomAnimName()
                + " dist=" + guard.distanceToSqr(hostile));

        // Home-radius walking now waits at least the 400-tick minimum after arrival.
        GuideNpc wanderer = new GuideNpc(NpcContent.guide(), level);
        wanderer.setPos(100.5, 64.0, 100.5);
        NpcProfile wp = stats(wanderer, 10, 10, 10, false);
        NpcAutoAi255.setAutoWander(wp, true);
        NpcAutoAi255.setWanderRadius(wp, 4);
        wanderer.setProfile(wp);
        level.addEntity(wanderer);
        Method engineTick = NpcAutoAiEngine255.class.getDeclaredMethod("tickWander", GuideNpc.class);
        engineTick.setAccessible(true);
        NpcAutoAiEngine255 engine = NpcAutoAiEngine255.of(wanderer);
        Field wait = NpcAutoAiEngine255.class.getDeclaredField("wanderWaitTicks"); wait.setAccessible(true);
        wait.setInt(engine, 0);
        engineTick.invoke(null, wanderer);
        Field target = NpcAutoAiEngine255.class.getDeclaredField("wanderTarget"); target.setAccessible(true);
        BlockPos destination = (BlockPos) target.get(engine);
        ok(destination != null, "wandering should select an in-radius safe destination");
        wanderer.setPos(destination.getX() + .5, destination.getY(), destination.getZ() + .5);
        engineTick.invoke(null, wanderer);
        int stay = wait.getInt(engine);
        ok(stay >= 400 && stay <= 900, "destination dwell must be 400–900 ticks (20–45 s), got " + stay);

        System.out.println("PASS build 257: node-backed NPC health/damage/speed, half calm movement, full combat pace, legacy combat priority, melee target acquisition, and 20–45 s wander dwell");
    }
}
''',
})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--java', required=True, type=pathlib.Path)
    parser.add_argument('--jar', required=True, type=pathlib.Path)
    parser.add_argument('--gson-source', required=True, type=pathlib.Path)
    args = parser.parse_args()
    java, jar = args.java.resolve(), args.jar.resolve()
    legacy_scenario = base.AUTO_AI_OVERRIDES['testing/AutoAi255Scenario.java']
    legacy_scenario = legacy_scenario.replace(
        'chosenTargets.size() >= 2', 'chosenTargets.size() >= 1'
    ).replace(
        'double chaseSpeed = fighter.getNavigation().lastSpeed;',
        'double chaseSpeed = fighter.getNavigation().lastSpeed * fighter.getAttributeValue('
        'net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);'
    ).replace('i < 260; i++', 'i < 520; i++')
    base.AUTO_AI_OVERRIDES['testing/AutoAi255Scenario.java'] = legacy_scenario
    with tempfile.TemporaryDirectory(prefix='autoai257-') as td:
        tmp = pathlib.Path(td)
        out, autostub = base.make_fixture(java, jar, args.gson_source.resolve(), tmp)
        game = tmp / 'game'
        game.mkdir()
        cp = ':'.join(map(str, [out, jar, autostub]))
        legacy = subprocess.run(
            [str(java), '-Xverify:all', '-cp', cp, 'testing.AutoAi255Scenario',
             str(game), str(jar), str(out), str(autostub)],
            capture_output=True, text=True,
        )
        assert legacy.returncode == 0 and 'PASS build 255 Auto-AI:' in legacy.stdout, (legacy.stdout, legacy.stderr)
        print('PASS inherited Auto-AI 255 regression scenarios on build 257', flush=True)
        result = subprocess.run(
            [str(java), '-Xverify:all', '-cp', cp, 'testing.AutoAi257Scenario', str(game)],
            capture_output=True, text=True,
        )
        assert result.returncode == 0 and 'PASS build 257:' in result.stdout, (result.stdout, result.stderr)
        print(result.stdout.strip(), flush=True)


if __name__ == '__main__':
    main()
