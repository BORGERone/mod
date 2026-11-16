#!/usr/bin/env python3
"""Gate (Build 416): Hackers 'n Slashers stealth compatibility for NPC head gaze and Stealth'o Meter.

Verifies on the target jar:
  1) `NpcHnsStealth416` and the three common mixins (`GuideNpcStealth416Mixin`,
     `NpcAutoAiStealth416Mixin`, `GuideCorpseStealth416Mixin`) exist in the jar and are
     registered in `settledlands.mixins.json` under `mixins` (server/common side).
  2) Zero hard `CONSTANT_Class` bytecode references to `net/dndats/*` exist anywhere in the jar
     (all Hackers 'n Slashers calls use JPMS-safe public reflection so SettledLands still runs
     cleanly without Hackers 'n Slashers installed).
  3) Every `@Redirect` in `GuideNpcStealth416Mixin` and `NpcAutoAiStealth416Mixin` encodes `at`
     as a single `@At` annotation (`0x40`), NOT an array (`0x5b`), and `GuideCorpseStealth416Mixin`
     hooks `GuideCorpseNpc#tick` (since `GuideCorpseNpc#customServerAiStep` is a no-op).
  4) Both built-in Hackers 'n Slashers datapack overrides
     `data/settledlands/mob_behavior_override/{guide_trigger,guide_corpse}.json` exist and set
     `visionRange=0`, `listeningRange=0`, `fieldOfView=0`, `hasTrueVision=false`,
     `canSearch=false`, `canLookAround=false`, `canChangeTarget=false`.
  5) Neither the «В радиусе» (`CHECK`) classes nor «Преследование» (`NpcPursuit269`) are hooked
     by the stealth mixins.
  6) Live Java 21 JVM execution of `tools/harness/HnsStealth416Probe.java` against the real
     `NpcHnsStealth416.class` inside the target jar passes all 9 stealth/gaze/blind-entity
     scenarios WITH Hackers 'n Slashers present AND degrades cleanly WITHOUT Hackers 'n Slashers
     on the classpath.
"""

import json
import pathlib
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import check_mixin_class as cmc
import classfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = pathlib.Path("/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java")
if not JAVA.is_file():
    import jdk4py
    JAVA = pathlib.Path(jdk4py.JAVA_HOME) / "bin" / "java"
ECJ = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
PROBE = ROOT / "tools" / "harness" / "HnsStealth416Probe.java"

HELPER = "dev/settledlands/npc/NpcHnsStealth416.class"
MIX_GUIDE = "dev/settledlands/mixin/GuideNpcStealth416Mixin.class"
MIX_IDLE = "dev/settledlands/mixin/NpcAutoAiStealth416Mixin.class"
MIX_CORPSE = "dev/settledlands/mixin/GuideCorpseStealth416Mixin.class"
DATA_TRIGGER = "data/settledlands/mob_behavior_override/guide_trigger.json"
DATA_CORPSE = "data/settledlands/mob_behavior_override/guide_corpse.json"

HARNESS_RUNTIME_STUBS = {
    "net/minecraft/util/Mth.java": """\
package net.minecraft.util;
public final class Mth {
    public static double atan2(double y, double x) { return Math.atan2(y, x); }
    public static float wrapDegrees(float deg) {
        float d = deg % 360.0F;
        if (d >= 180.0F) d -= 360.0F;
        if (d < -180.0F) d += 360.0F;
        return d;
    }
    public static float rotLerp(float delta, float start, float end) {
        return start + delta * wrapDegrees(end - start);
    }
}
""",
    "net/minecraft/world/level/Level.java": """\
package net.minecraft.world.level;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
public class Level {
    public boolean isClientSide = false;
    private List<Player> players = Collections.emptyList();
    public void setPlayers(List<Player> list) { this.players = list; }
    public List<Player> players() { return this.players; }
    public Player getNearestPlayer(Entity self, double maxDist) {
        Player best = null;
        double bestSq = maxDist * maxDist;
        for (Player p : players) {
            double d = self.distanceToSqr(p);
            if (d <= bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }
}
""",
    "net/minecraft/world/entity/Entity.java": """\
package net.minecraft.world.entity;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.Level;
public class Entity {
    private final Level level;
    private final UUID uuid;
    private final double x, y, z;
    public float yRot;
    public float xRot;
    private boolean alive = true;
    private boolean invisible = false;
    public Entity(Level level, UUID uuid, double x, double y, double z) {
        this.level = level;
        this.uuid = uuid;
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public Level level() { return level; }
    public UUID getUUID() { return uuid; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getEyeY() { return y + 1.62D; }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean v) { this.alive = v; }
    public boolean isRemoved() { return !alive; }
    public boolean isInvisible() { return invisible; }
    public void setInvisible(boolean v) { this.invisible = v; }
    public double distanceToSqr(Entity other) {
        double dx = other.x - this.x;
        double dy = other.y - this.y;
        double dz = other.z - this.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
""",
    "net/minecraft/world/entity/LivingEntity.java": """\
package net.minecraft.world.entity;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.Level;
public class LivingEntity extends Entity {
    public float yHeadRot;
    public float yHeadRotO;
    public float yBodyRot;
    public float yBodyRotO;
    private final Set<Entity> los = new HashSet<>();
    public LivingEntity(Level level, UUID uuid, double x, double y, double z) {
        super(level, uuid, x, y, z);
    }
    public void setLineOfSight(Entity target, boolean canSee) {
        if (canSee) los.add(target);
        else los.remove(target);
    }
    public boolean hasLineOfSight(Entity other) { return los.contains(other); }
}
""",
    "net/minecraft/world/entity/Mob.java": """\
package net.minecraft.world.entity;
import java.util.UUID;
import net.minecraft.world.level.Level;
public class Mob extends LivingEntity {
    private LivingEntity target;
    public Mob(Level level, UUID uuid, double x, double y, double z) {
        super(level, uuid, x, y, z);
    }
    public LivingEntity getTarget() { return target; }
    public void setTarget(LivingEntity t) { this.target = t; }
}
""",
    "net/minecraft/world/entity/PathfinderMob.java": """\
package net.minecraft.world.entity;
import java.util.UUID;
import net.minecraft.world.level.Level;
public class PathfinderMob extends Mob {
    public PathfinderMob(Level level, UUID uuid, double x, double y, double z) {
        super(level, uuid, x, y, z);
    }
}
""",
    "net/minecraft/world/entity/player/Player.java": """\
package net.minecraft.world.entity.player;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
public class Player extends LivingEntity {
    private boolean spectator = false;
    private boolean creative = false;
    public Player(Level level, UUID uuid, double x, double y, double z) {
        super(level, uuid, x, y, z);
    }
    public boolean isSpectator() { return spectator; }
    public void setSpectator(boolean v) { this.spectator = v; }
    public boolean isCreative() { return creative; }
    public void setCreative(boolean v) { this.creative = v; }
}
""",
    "net/minecraft/server/level/ServerPlayer.java": """\
package net.minecraft.server.level;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
public class ServerPlayer extends Player {
    public ServerPlayer(Level level, UUID uuid, double x, double y, double z) {
        super(level, uuid, x, y, z);
    }
}
""",
    "dev/settledlands/npc/GuideNpc.java": """\
package dev.settledlands.npc;
import java.util.UUID;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
public class GuideNpc extends PathfinderMob {
    private boolean talking = false;
    public GuideNpc(Level level, double x, double y, double z) {
        super(level, UUID.randomUUID(), x, y, z);
    }
    public boolean isTalking() { return talking; }
    public void setTalking(boolean v) { this.talking = v; }
}
""",
    "dev/settledlands/npc/GuideTriggerNpc.java": """\
package dev.settledlands.npc;
import net.minecraft.world.level.Level;
public class GuideTriggerNpc extends GuideNpc {
    public GuideTriggerNpc(Level level, double x, double y, double z) {
        super(level, x, y, z);
    }
}
""",
    "dev/settledlands/npc/GuideCorpseNpc.java": """\
package dev.settledlands.npc;
import net.minecraft.world.level.Level;
public class GuideCorpseNpc extends GuideNpc {
    public GuideCorpseNpc(Level level, double x, double y, double z) {
        super(level, x, y, z);
    }
}
""",
    "dev/settledlands/npc/NpcAutoAiEngine255.java": """\
package dev.settledlands.npc;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
public final class NpcAutoAiEngine255 {
    public static boolean shouldLookAtPlayer(GuideNpc npc) {
        return npc != null;
    }
    public static void lookHeadAtPlayer(GuideNpc npc, Player player) {
        double dx = player.getX() - npc.getX();
        double dz = player.getZ() - npc.getZ();
        float targetYaw = (float) (Mth.atan2(dz, dx) * 57.29577951308232D) - 90.0F;
        npc.yHeadRot = Mth.rotLerp(0.35F, npc.yHeadRot, targetYaw);
    }
}
""",
}

HNS_MOCK_STUBS = {
    "net/dndats/hackersandslashers/config/ServerConfig.java": """\
package net.dndats.hackersandslashers.config;
public class ServerConfig {
    public boolean enable_stealth_system = true;
}
""",
    "net/dndats/hackersandslashers/HackersAndSlashers.java": """\
package net.dndats.hackersandslashers;
import net.dndats.hackersandslashers.config.ServerConfig;
public class HackersAndSlashers {
    public static ServerConfig serverConfig = new ServerConfig();
}
""",
    "net/dndats/hackersandslashers/utils/helper/EntityHelper.java": """\
package net.dndats.hackersandslashers.utils.helper;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
public class EntityHelper {
    private static final Map<Mob, Map<UUID, Double>> SIGHT = new HashMap<>();
    private static final Set<Mob> SUSPICION = new HashSet<>();

    public static double getSightLevel(Player player, Mob mob) {
        if (player == null || mob == null) return 0.0D;
        Map<UUID, Double> m = SIGHT.get(mob);
        if (m == null) return 0.0D;
        Double v = m.get(player.getUUID());
        return v == null ? 0.0D : v.doubleValue();
    }

    public static void setSightLevel(Player player, Mob mob, double level) {
        if (player == null || mob == null) return;
        Map<UUID, Double> m = SIGHT.computeIfAbsent(mob, k -> new HashMap<>());
        if (level <= 0.0D) {
            m.remove(player.getUUID());
        } else {
            m.put(player.getUUID(), level);
        }
    }

    public static Set<UUID> getTrackedPlayers(Mob mob) {
        if (mob == null) return Collections.emptySet();
        Map<UUID, Double> m = SIGHT.get(mob);
        return m == null ? Collections.emptySet() : m.keySet();
    }

    public static void addDummySuspicion(Mob mob) {
        if (mob != null) SUSPICION.add(mob);
    }

    public static boolean hasSuspicionEntries(Mob mob) {
        return mob != null && SUSPICION.contains(mob);
    }

    public static void clearSuspicionEntries(Mob mob) {
        if (mob != null) SUSPICION.remove(mob);
    }
}
""",
    "net/dndats/hackersandslashers/utils/helper/DatapackHelper.java": """\
package net.dndats.hackersandslashers.utils.helper;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.Mob;
public class DatapackHelper {
    private static final Set<Mob> TRUE_VISION = new HashSet<>();
    public static void setMobTrueVision(Mob mob, boolean v) {
        if (v) TRUE_VISION.add(mob);
        else TRUE_VISION.remove(mob);
    }
    public static boolean hasMobTrueVision(Mob mob) {
        return mob != null && TRUE_VISION.contains(mob);
    }
}
""",
}

NO_HNS_PROBE = """\
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.GuideTriggerNpc;
import dev.settledlands.npc.NpcHnsStealth416;
import java.util.Collections;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class HnsAbsent416Probe {
    public static void main(String[] args) {
        Level level = new Level();
        GuideNpc guard = new GuideNpc(level, 0.0D, 64.0D, 0.0D);
        Player player = new Player(level, UUID.randomUUID(), 4.0D, 64.0D, 0.0D);
        level.setPlayers(Collections.singletonList(player));

        boolean hnsActive = NpcHnsStealth416.isHnsStealthActive();
        guard.setLineOfSight(player, true);
        boolean seesWithLos = (NpcHnsStealth416.pickVisibleLookPlayer(level, guard, 12.0D) == player);
        guard.setLineOfSight(player, false);
        boolean blockedByWall = (NpcHnsStealth416.pickVisibleLookPlayer(level, guard, 12.0D) == null);

        GuideTriggerNpc trigger = new GuideTriggerNpc(level, 0.0D, 64.0D, 0.0D);
        NpcHnsStealth416.clearBlindEntitySight(trigger);
        boolean triggerBlind = (NpcHnsStealth416.pickVisibleLookPlayer(level, trigger, 12.0D) == null);

        System.out.println("NO_HNS active=" + hnsActive + " seesWithLos=" + seesWithLos
                + " blockedByWall=" + blockedByWall + " triggerBlind=" + triggerBlind);
    }
}
"""


def _check_override_json(blobs, path, problems):
    if path not in blobs:
        problems.append("missing Hackers 'n Slashers override JSON: %s" % path)
        return
    try:
        obj = json.loads(blobs[path].decode("utf-8"))
    except Exception as exc:
        problems.append("invalid JSON in %s: %s" % (path, exc))
        return
    perc = obj.get("perception")
    if not isinstance(perc, dict):
        problems.append("%s: missing 'perception' object" % path)
        return
    expected = {
        "hasTrueVision": False,
        "fieldOfView": 0,
        "listeningRange": 0,
        "visionRange": 0,
        "canSearch": False,
        "canLookAround": False,
        "canChangeTarget": False,
    }
    for k, want in expected.items():
        got = perc.get(k)
        if got != want or type(got) is not type(want):
            problems.append("%s: expected perception.%s=%r, got %r" % (path, k, want, got))


def _check_no_direct_hns_class_refs(blobs, problems):
    for name, raw in blobs.items():
        if not name.endswith(".class"):
            continue
        cf = classfile.parse(raw)
        for idx, entry in enumerate(cf.cp):
            if entry and entry[0] == "Class":
                cname = cf.utf8(entry[1]) or ""
                if cname.startswith("net/dndats/"):
                    problems.append("%s has hard CONSTANT_Class ref to %s (must use reflection)"
                                    % (name, cname))


def _check_redirect_annotations(blobs, entry, expected_count, problems):
    cf = classfile.parse(blobs[entry])
    found = 0
    for m in cf.methods:
        for ann in cmc.member_annotations(cf, m):
            if cmc.ann_desc(cf, ann) == cmc.DESC_REDIRECT:
                found += 1
                args = cmc.ann_args(cf, ann)
                raw_at = args.get("at")
                if raw_at is None or raw_at[0] != "annotation":
                    problems.append("%s#%s: @Redirect 'at' must be single annotation (0x40), got %r"
                                    % (entry, m.name, None if raw_at is None else raw_at[0]))
    if found != expected_count:
        problems.append("%s: expected %d @Redirect methods, found %d"
                        % (entry, expected_count, found))


def _run_jvm_probes(jar_path, problems):
    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = pathlib.Path(tmp)
        rt_src = tmpdir / "rt-src"
        rt_out = tmpdir / "rt-out"
        hns_src = tmpdir / "hns-src"
        hns_out = tmpdir / "hns-out"
        rt_out.mkdir(parents=True)
        hns_out.mkdir(parents=True)

        src_files = []
        for rel, code in HARNESS_RUNTIME_STUBS.items():
            p = rt_src / rel
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(code, encoding="utf-8")
            src_files.append(str(p))

        no_hns_file = rt_src / "HnsAbsent416Probe.java"
        no_hns_file.write_text(NO_HNS_PROBE, encoding="utf-8")
        src_files.append(str(no_hns_file))

        res = subprocess.run(
            [str(JAVA), "-jar", str(ECJ), "-21", "-nowarn",
             "-cp", str(jar_path), "-d", str(rt_out)] + src_files,
            capture_output=True, text=True
        )
        if res.returncode != 0:
            problems.append("failed compiling runtime stubs:\n%s\n%s" % (res.stdout, res.stderr))
            return

        hns_files = []
        for rel, code in HNS_MOCK_STUBS.items():
            p = hns_src / rel
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(code, encoding="utf-8")
            hns_files.append(str(p))
        hns_files.append(str(PROBE))

        res2 = subprocess.run(
            [str(JAVA), "-jar", str(ECJ), "-21", "-nowarn",
             "-cp", "%s:%s" % (rt_out, jar_path), "-d", str(hns_out)] + hns_files,
            capture_output=True, text=True
        )
        if res2.returncode != 0:
            problems.append("failed compiling HnsStealth416Probe:\n%s\n%s"
                            % (res2.stdout, res2.stderr))
            return

        # 1) Run WITH Hackers 'n Slashers present on classpath.
        run1 = subprocess.run(
            [str(JAVA), "-cp", "%s:%s:%s" % (hns_out, rt_out, jar_path), "HnsStealth416Probe"],
            capture_output=True, text=True
        )
        if run1.returncode != 0:
            problems.append("HnsStealth416Probe exited %d:\n%s\n%s"
                            % (run1.returncode, run1.stdout, run1.stderr))
            return
        lines1 = [ln.strip() for ln in run1.stdout.splitlines() if ln.strip()]
        expected1 = [
            "HIDDEN_BEHIND pickNull=true look=false headUnchanged=true",
            "SUSPICIOUS_55 pickNull=true canAcquire=false",
            "MASKING_BYPASS pickedVisible=true look=true headMoved=true",
            "HYSTERESIS retain50=true pick50=true drop20=true pick20Null=true",
            "WALL_OCCLUSION blocked=true",
            "INVISIBILITY hidden=true trueVision=true",
            "DIALOGUE_OVERRIDE looks=true",
            "BLIND_ENTITIES noLook=true scrubbed=true",
            "STEALTH_DISABLED losLooks=true wallBlocks=true",
        ]
        if lines1 != expected1:
            problems.append("HnsStealth416Probe output mismatch:\n  got:  %r\n  want: %r"
                            % (lines1, expected1))

        # 2) Run WITHOUT Hackers 'n Slashers on classpath (clean degradation test).
        run2 = subprocess.run(
            [str(JAVA), "-cp", "%s:%s" % (rt_out, jar_path), "HnsAbsent416Probe"],
            capture_output=True, text=True
        )
        if run2.returncode != 0:
            problems.append("HnsAbsent416Probe exited %d:\n%s\n%s"
                            % (run2.returncode, run2.stdout, run2.stderr))
            return
        lines2 = [ln.strip() for ln in run2.stdout.splitlines() if ln.strip()]
        expected2 = [
            "NO_HNS active=false seesWithLos=true blockedByWall=true triggerBlind=true",
        ]
        if lines2 != expected2:
            problems.append("HnsAbsent416Probe output mismatch:\n  got:  %r\n  want: %r"
                            % (lines2, expected2))


def main(argv):
    if len(argv) != 1:
        print("usage: hns_stealth_check.py <mod.jar>", file=sys.stderr)
        return 2
    jar_path = pathlib.Path(argv[0])
    if not jar_path.is_file():
        print("no such jar: %s" % jar_path, file=sys.stderr)
        return 2

    problems = []
    with zipfile.ZipFile(jar_path) as zf:
        blobs = {name: zf.read(name) for name in zf.namelist()}

    for req in (HELPER, MIX_GUIDE, MIX_IDLE, MIX_CORPSE):
        if req not in blobs:
            problems.append("missing class in jar: %s" % req)

    if problems:
        for p in problems:
            print("FAIL:", p, file=sys.stderr)
        return 1

    mixins_cfg = json.loads(blobs["settledlands.mixins.json"].decode("utf-8"))
    common_mixins = set(mixins_cfg.get("mixins", []))
    client_mixins = set(mixins_cfg.get("client", []))
    for mname in ("GuideNpcStealth416Mixin", "NpcAutoAiStealth416Mixin", "GuideCorpseStealth416Mixin"):
        if mname not in common_mixins:
            problems.append("%s missing from settledlands.mixins.json 'mixins'" % mname)
        if mname in client_mixins:
            problems.append("%s must not be in client-only mixins" % mname)

    _check_no_direct_hns_class_refs(blobs, problems)
    _check_redirect_annotations(blobs, MIX_GUIDE, 3, problems)
    _check_redirect_annotations(blobs, MIX_IDLE, 1, problems)
    _check_override_json(blobs, DATA_TRIGGER, problems)
    _check_override_json(blobs, DATA_CORPSE, problems)

    # Ensure none of the 416 mixins touch GuideDialogue / NpcRadiusZone320 / NpcPursuit269.
    for entry in (MIX_GUIDE, MIX_IDLE, MIX_CORPSE):
        cf = classfile.parse(blobs[entry])
        margs = cmc.annotation_args_by_desc(cf, cf, cmc.DESC_MIXIN, members_of_class=True) or {}
        targets = []
        for val in (margs.get("targets"), margs.get("value")):
            for s in cmc.as_str_list(cf, val):
                targets.append(s.lstrip("L").rstrip(";").replace(".", "/"))
        for forbidden in ("dev/settledlands/npc/GuideDialogue",
                          "dev/settledlands/npc/NpcRadiusZone320",
                          "dev/settledlands/npc/NpcPursuit269"):
            if forbidden in targets:
                problems.append("%s must not target %s (out of agreed scope)" % (entry, forbidden))

    _run_jvm_probes(jar_path, problems)

    if problems:
        for p in problems:
            print("FAIL:", p, file=sys.stderr)
        return 1

    print("hns_stealth_check: OK  %s" % jar_path)
    print("  classes     : NpcHnsStealth416 + 3 common mixins (@Redirect at=annotation verified)")
    print("  datapack    : guide_trigger.json & guide_corpse.json (vision=0, hearing=0, fov=0)")
    print("  jvm probes  : 9/9 HnS scenarios + clean no-HnS fallback passed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
