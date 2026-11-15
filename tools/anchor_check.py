#!/usr/bin/env python3
"""Gate (Build 420): the anchor - a build's graphs travel inside a blueprint.

The author's report: he builds in Axiom, copies the selection, pastes it elsewhere, and every graph
stays behind. The graphs of a block, a door and a chest are world data keyed by absolute position, so a
copy tool has nothing to carry. Build 420 stuffs them into a marker entity, relative to the marker, and
writes them back where the marker turns up.

Verified on the target jar:
  1) the five classes exist - `NpcGraphAnchorCore420` (+ its Entry/Fingerprint/Result/StoreSink/
     BlockLookup inner classes), `NpcGraphAnchorFiles420`, `NpcGraphAnchor420`,
     `NpcGraphAnchorEvents420`, `NpcGraphAnchorInteract420` - and the rule class reaches no game class
     except `BlockPos`, `CompoundTag` and `ListTag`, which is what makes it testable without a game;
  2) the four world files are the ones the mod already keeps: `settledlands_block_triggers`,
     `settledlands_door_graphs`, `settledlands_chest_graphs` and `settledlands_doors` are all reached,
     so a pasted build is an ordinary build to every reader built before this one;
  3) the marker is the base mod's own invisible trigger (`settledlands:guide_trigger`) and the list
     lives in the entity's persistent data (`IEntityExtension#getPersistentData`), i.e. in the NBT a
     copy tool carries - not in a new block, not in a new store;
  4) the pass is a plain `ServerTickEvent.Post` and is paced (`% PERIOD`), and its yaw is never read:
     the rotation comes from the fingerprint (`bestTurn`), because a blueprint's turn does not reach an
     entity's yaw;
  5) the interact hook exists and cancels a click **only** when an anchor was really written
     (`wandOn(...) == true` before `setCanceled`), so nothing else changes meaning;
  6) every Minecraft call the new classes make is a call the existing jar already makes (or, for
     `Entity#moveTo(DDD)`, `Entity#setYRot(F)` and `BlockState#isAir()`, a name whose sibling descriptor
     in the same package is already called - listed explicitly in `UNVERIFIED_EXTERNAL` below);
  7) live Java 21 JVM run of `tools/harness/Anchor420Probe.java` - the eight turns, the packing round
     trip, the rotation a paste really left behind, the fallback to identity among strangers, the
     materialisation of a block + a twice-locked door + a chest, and an unsealed marker writing nothing.
"""

import pathlib
import struct
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classfile
import disasm

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = pathlib.Path("/tmp/venv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java")
if not JAVA.is_file():
    try:
        import jdk4py
        JAVA = pathlib.Path(jdk4py.JAVA_HOME) / "bin" / "java"
    except Exception:  # pragma: no cover - the sandbox always has it
        JAVA = pathlib.Path("java")
ECJ = ROOT / "tools" / "vendor" / "ecj-3.46.100.jar"
PROBE = ROOT / "tools" / "harness" / "Anchor420Probe.java"

CORE = "dev/settledlands/npc/NpcGraphAnchorCore420.class"
CORE_INNER = (
    "dev/settledlands/npc/NpcGraphAnchorCore420$Entry.class",
    "dev/settledlands/npc/NpcGraphAnchorCore420$Fingerprint.class",
    "dev/settledlands/npc/NpcGraphAnchorCore420$Result.class",
    "dev/settledlands/npc/NpcGraphAnchorCore420$StoreSink.class",
    "dev/settledlands/npc/NpcGraphAnchorCore420$BlockLookup.class",
    "dev/settledlands/npc/NpcGraphAnchorCore420$Placement.class",
)
FILES = "dev/settledlands/npc/NpcGraphAnchorFiles420.class"
WORLD = "dev/settledlands/npc/NpcGraphAnchor420.class"
EVENTS = "dev/settledlands/npc/NpcGraphAnchorEvents420.class"
INTERACT = "dev/settledlands/npc/NpcGraphAnchorInteract420.class"

# The four world files, reached through the classes that own them - a build's graphs must land in the
# very files the readers built before this build already read.
STORES = ("dev/settledlands/npc/BlockTriggerStore351", "dev/settledlands/door/DoorGraph382Store",
          "dev/settledlands/chest/ChestGraph396Store", "dev/settledlands/door/DoorStore368")

# Calls whose exact descriptor this build cannot read off the jar, with the sibling the jar does call
# and a one-line reason each. Anything else outside java.* must already be in the base jar.
UNVERIFIED_EXTERNAL = {
    ("net/minecraft/core/Registry", "getKey",
     "(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;"):
        "NpcItems calls this exact signature and WorldEvents calls getKey on a registry - ecj only wrote "
        "DefaultedRegistry as the owner there because that was the static type of the call site; Registry "
        "is the class that declares it",
    ("net/minecraft/core/Registry", "get",
     "(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;"):
        "NpcItems calls this very signature on DefaultedRegistry; Registry declares it, and the anchor "
        "resolves its own marker type id through it",
    ("net/minecraft/world/entity/Entity", "moveTo", "(DDD)V"):
        "the jar places a body with moveTo(DDDFF)V on GuideNpc and GuideCorpseNpc, and the 3-argument "
        "overload is the game's own (Entity#moveTo(double,double,double)); the marker needs no yaw "
        "change",
    ("net/minecraft/world/entity/Entity", "setYRot", "(F)V"):
        "the jar calls setYRot(F) on GuideNpc, GuideCorpseNpc, Zombie and Player",
    ("net/minecraft/world/level/block/state/BlockState", "isAir", "()Z"):
        "the jar reads a state through BlockState#getBlock()/is(); isEmptyBlock is a call of "
        "ServerLevel and is not used here - isAir is the vanilla predicate for exactly this question",
    ("net/minecraft/core/registries/BuiltInRegistries", "BLOCK",
     "Lnet/minecraft/core/Registry;"):
        "the jar reads BuiltInRegistries.ITEM and BuiltInRegistries.ENTITY_TYPE as "
        "Registry-typed fields and calls Registry#getKey on both; BLOCK is the same registry kind",
    ("net/minecraft/core/registries/BuiltInRegistries", "ENTITY_TYPE",
     "Lnet/minecraft/core/Registry;"):
        "the jar calls getKey on BuiltInRegistries.ENTITY_TYPE and keeps a ResourceLocation - the id of "
        "a type is exactly what the anchor stores",
}

HARNESS_STUBS = {
    "net/minecraft/nbt/Tag.java": """\
package net.minecraft.nbt;
public interface Tag {}
""",
    "net/minecraft/nbt/ListTag.java": """\
package net.minecraft.nbt;
import java.util.ArrayList;
public class ListTag extends ArrayList<CompoundTag> implements Tag {
    public CompoundTag getCompound(int index) { return get(index); }
}
""",
    "net/minecraft/nbt/CompoundTag.java": """\
package net.minecraft.nbt;
import java.util.HashMap;
import java.util.Map;
public class CompoundTag implements Tag {
    private final Map<String, Object> values = new HashMap<>();
    public boolean isEmpty() { return values.isEmpty(); }
    public boolean contains(String key) { return values.containsKey(key); }
    public boolean contains(String key, int type) { return values.containsKey(key); }
    public ListTag getList(String key, int elementType) {
        Object value = values.get(key);
        return value instanceof ListTag list ? list : new ListTag();
    }
    public int getInt(String key) { Object v = values.get(key); return v instanceof Number n ? n.intValue() : 0; }
    public CompoundTag getCompound(String key) { Object v = values.get(key); return v instanceof CompoundTag t ? t : new CompoundTag(); }
    public String getString(String key) { Object v = values.get(key); return v instanceof String s ? s : ""; }
    public void putString(String key, String value) { values.put(key, value); }
    public void remove(String key) { values.remove(key); }
    public boolean getBoolean(String key) { return Boolean.TRUE.equals(values.get(key)); }
    public void putBoolean(String key, boolean value) { values.put(key, value); }
    public void putInt(String key, int value) { values.put(key, value); }
    public void putIntArray(String key, int[] value) { values.put(key, value); }
    public int[] getIntArray(String key) { Object v = values.get(key); return v instanceof int[] a ? a : new int[0]; }
    public void putFloat(String key, float value) { values.put(key, value); }
    public void putDouble(String key, double value) { values.put(key, value); }
    public Tag put(String key, Tag value) { values.put(key, value); return value; }
    @Override public String toString() { return values.toString(); }
}
""",
    "net/minecraft/core/BlockPos.java": """\
package net.minecraft.core;
public class BlockPos {
    private final int x, y, z;
    public BlockPos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public BlockPos offset(int dx, int dy, int dz) { return new BlockPos(x + dx, y + dy, z + dz); }
    @Override public String toString() { return x + " " + y + " " + z; }
}
""",
}


# ---- bytecode helpers -------------------------------------------------------

def _cp_text(parsed, index):
    cp = parsed["cp"]
    if not (0 < index < len(cp)) or cp[index] is None:
        return None
    return cp[index][1] if cp[index][0] == 1 else None


def _cp_class(parsed, index):
    cp = parsed["cp"]
    if not (0 < index < len(cp)) or cp[index] is None or cp[index][0] != 7:
        return None
    return _cp_text(parsed, struct.unpack(">H", cp[index][1])[0])


def class_refs(raw):
    """Every CONSTANT_Class / Methodref / Fieldref owner+name+desc of one class file."""
    parsed = disasm.parse_class(raw)
    cp = parsed["cp"]
    classes, methods, fields = set(), set(), set()
    for entry in cp:
        if entry is None:
            continue
        tag, data = entry
        if tag == 7:
            classes.add(_cp_text(parsed, struct.unpack(">H", data)[0]))
        elif tag in (10, 11):
            owner = _cp_class(parsed, struct.unpack(">H", data[:2])[0])
            nat = cp[struct.unpack(">H", data[2:4])[0]]
            if nat is None:
                continue
            name_i, desc_i = struct.unpack(">HH", nat[1])
            target = (owner, _cp_text(parsed, name_i), _cp_text(parsed, desc_i))
            methods.add(target)
        elif tag == 9:
            owner = _cp_class(parsed, struct.unpack(">H", data[:2])[0])
            nat = cp[struct.unpack(">H", data[2:4])[0]]
            if nat is None:
                continue
            name_i, desc_i = struct.unpack(">HH", nat[1])
            fields.add((owner, _cp_text(parsed, name_i), _cp_text(parsed, desc_i)))
    return classes, methods, fields


def method_listing(raw, name):
    parsed = disasm.parse_class(raw)
    cp = parsed["cp"]
    for item in parsed["methods"]:
        _acc, name_idx, _desc = item[0], item[1], item[2]
        attrs = item[3]
        if _cp_text(parsed, name_idx) != name:
            continue
        code = disasm.code_of(attrs, cp)
        if code is None:
            return ""
        listing, _ = disasm.disasm(code["code"], cp, labels_only=True)
        return listing
    return ""


def strings_of(raw):
    """The printable strings of a class file, enough to check a message is really in the bytecode."""
    out, current = [], bytearray()
    for byte in raw:
        if 32 <= byte < 127 or byte >= 0xC0:
            current.append(byte)
        else:
            if len(current) >= 4:
                out.append(bytes(current).decode("utf-8", "ignore"))
            current = bytearray()
    if len(current) >= 4:
        out.append(bytes(current).decode("utf-8", "ignore"))
    return out


# ---- the checks -------------------------------------------------------------

def check_classes(blobs, problems):
    for entry in (CORE, FILES, WORLD, EVENTS, INTERACT) + CORE_INNER:
        if entry not in blobs:
            problems.append("%s missing from the jar" % entry)

    if CORE in blobs:
        classes, methods, fields = class_refs(blobs[CORE])
        allowed = {"net/minecraft/core/BlockPos", "net/minecraft/nbt/CompoundTag",
                   "net/minecraft/nbt/ListTag", "[I", "[Lnet/minecraft/nbt/CompoundTag;"}
        stem = CORE[: -len(".class")]
        own = (stem, stem + "$")
        for name in sorted(c for c in classes if c and not c.startswith("java/")):
            if name not in allowed and not name.startswith(own):
                problems.append("%s reaches %s - the rule must stay free of the game" % (CORE, name))
        allowed_prefixes = ("java/", "dev/settledlands/npc/NpcGraphAnchorCore",
                            "net/minecraft/nbt/CompoundTag", "net/minecraft/nbt/ListTag",
                            "net/minecraft/nbt/Tag", "net/minecraft/core/BlockPos")
        for owner, _name, _desc in sorted(methods):
            if owner and not owner.startswith(allowed_prefixes):
                problems.append("%s calls %s - the rule must stay free of the game" % (CORE, owner))


def check_stores(blobs, problems):
    text = b" ".join(blobs[e] for e in (FILES, WORLD) if e in blobs)
    for store in STORES:
        if store.encode("ascii") not in text:
            problems.append("the anchor never reaches %s - a pasted build would be invisible to the "
                            "readers built before it" % store)


def check_marker(blobs, problems):
    raw = blobs.get(WORLD, b"")
    if not raw:
        return
    strings = " ".join(strings_of(raw))
    if "settledlands" not in strings or "guide_trigger" not in strings:
        problems.append("%s does not name the guide_trigger marker" % WORLD)
    if b"IEntityExtension" not in raw or b"getPersistentData" not in raw:
        problems.append("%s does not keep its list in the entity's persistent data" % WORLD)
    if b"addFreshEntity" not in raw:
        problems.append("%s never places a marker" % WORLD)
    if b"BlockEntity" in raw:
        problems.append("%s mentions a BlockEntity - this build adds no block on purpose" % WORLD)


def check_pass(blobs, problems):
    events = blobs.get(EVENTS, b"")
    if events:
        if b"ServerTickEvent" not in events:
            problems.append("%s is not a server tick listener" % EVENTS)
        if b"getGameTime" not in events or b"NpcGraphAnchor420" not in events:
            problems.append("%s does not pace itself on the world clock through the anchor's own "
                            "PERIOD" % EVENTS)
    world = blobs.get(WORLD, b"")
    if world:
        listing = method_listing(world, "materialiseIfMoved")
        # The turn must come from the fingerprint - never from the marker's own yaw. Both the joint
        # search ("Placement", build 423) and the older separate one ("bestTurn") satisfy that, as long
        # as the answer is derived from blocks read around the marker.
        if "bestTurn" not in listing and "Placement" not in listing:
            problems.append("%s#materialiseIfMoved does not take the turn from the fingerprint" % WORLD)
        if "getYRot" in listing:
            problems.append("%s#materialiseIfMoved reads the marker's yaw - a blueprint's turn does not "
                            "reach an entity's yaw" % WORLD)
        # Build 422: the cell of a paste comes from the fingerprint, not from the marker's feet. A marker
        # is a mob with a double y, so reading its cell put every restored graph one block under the
        # blocks it belongs to - the author's «по прежнему не копируется».
        if "bestPlace" not in listing and "Placement" not in listing:
            problems.append("%s#materialiseIfMoved does not let the fingerprint place the build - a "
                            "marker's own cell is a double and can sit half a block off" % WORLD)
        # Build 423: the cell and the arrangement must be one search. Asking for the cell first and the
        # turn second puts every turned paste on a shifted anchor - «с поворотами графы съезжают».
        if "bestTurn" in listing:
            problems.append("%s#materialiseIfMoved still asks for the turn separately from the cell - the "
                            "two are one question once a build can be pasted turned" % WORLD)
        # Build 423: when the rule owns a joint search, its answer type has to be packaged with it - a
        # caller that got a half-answer is exactly the bug this build fixes.
        if "NpcGraphAnchorCore420$Placement" in blobs and "Placement" not in listing:
            problems.append("%s has a Placement but #materialiseIfMoved does not use it" % WORLD)


def check_interact(blobs, problems):
    raw = blobs.get(INTERACT, b"")
    if not raw:
        return
    listing = method_listing(raw, "onEntityInteract")
    if "wandOn" not in listing:
        problems.append("%s never asks the anchor whether it wrote anything" % INTERACT)
    if "setCanceled" not in listing or "setCancellationResult" not in listing:
        problems.append("%s does not answer the click it took" % INTERACT)
    cancel_at = listing.find("setCanceled")
    wrote_at = listing.find("wandOn")
    if wrote_at >= 0 and cancel_at >= 0 and cancel_at < wrote_at:
        problems.append("%s cancels before it writes - a click with nothing to write must keep its "
                        "old meaning" % INTERACT)
    if b"NpcContent" not in raw or b"wand" not in raw:
        problems.append("%s does not ask for the NPC wand" % INTERACT)


def check_external(base_blobs, new_blobs, problems):
    base_calls, base_fields = set(), set()
    for raw in base_blobs.values():
        _c, methods, fields = class_refs(raw)
        base_calls |= methods
        base_fields |= fields
    for entry in (FILES, WORLD, EVENTS, INTERACT):
        raw = new_blobs.get(entry)
        if not raw:
            continue
        _classes, methods, fields = class_refs(raw)
        for ref in sorted(methods):
            owner = ref[0] or ""
            if not owner.startswith(("net/minecraft", "net/neoforged")):
                continue
            if ref in base_calls or ref in UNVERIFIED_EXTERNAL:
                continue
            problems.append("%s calls an external method the base jar never calls: %s.%s%s"
                            % (entry, owner, ref[1], ref[2]))
        for ref in sorted(fields):
            owner = ref[0] or ""
            if not owner.startswith(("net/minecraft", "net/neoforged")):
                continue
            if ref in base_fields or ref in UNVERIFIED_EXTERNAL:
                continue
            problems.append("%s reads an external field the base jar never reads: %s.%s"
                            % (entry, owner, ref[1]))


def run_probe(jar_path, problems, blobs):
    if not JAVA.is_file():
        problems.append("no java runtime found for the probe")
        return
    if CORE not in blobs:
        problems.append("the probe cannot run: %s is not in the jar" % CORE)
        return
    core_raw = blobs[CORE]
    with tempfile.TemporaryDirectory() as tmp:
        tmp = pathlib.Path(tmp)
        src = tmp / "src"
        stub_src = src / "stubs"
        core_src = src / "core"
        for rel, body in HARNESS_STUBS.items():
            path = stub_src / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(body)
        core_src.mkdir(parents=True, exist_ok=True)
        core_path = core_src / CORE
        core_path.parent.mkdir(parents=True, exist_ok=True)
        core_path.write_bytes(core_raw)
        stub_out = tmp / "stubout"
        core_out = tmp / "coreout"
        probe_out = tmp / "probeout"
        for out in (stub_out, core_out, probe_out):
            out.mkdir()
        stub_files = sorted(str(p) for p in stub_src.rglob("*.java"))
        r = subprocess.run([str(JAVA), "-jar", str(ECJ), "-21", "-proc:none", "-nowarn",
                            "-d", str(stub_out)] + stub_files,
                           capture_output=True, text=True)
        if r.returncode != 0:
            problems.append("anchor probe: stub compile failed:\n" + r.stderr)
            return
        (core_out / CORE).parent.mkdir(parents=True, exist_ok=True)
        (core_out / CORE).write_bytes(core_raw)
        for inner in CORE_INNER:
            if inner in blobs:
                (core_out / inner).parent.mkdir(parents=True, exist_ok=True)
                (core_out / inner).write_bytes(blobs[inner])
        for inner in CORE_INNER:
            if inner not in blobs:
                problems.append("the probe cannot run: %s is not in the jar" % inner)
                return
            data = blobs[inner]
            path = core_src / inner
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        r = subprocess.run([str(JAVA), "-jar", str(ECJ), "-21", "-proc:none", "-nowarn",
                            "-cp", "%s:%s" % (stub_out, core_out), "-d", str(probe_out),
                            str(PROBE)],
                           capture_output=True, text=True)
        if r.returncode != 0:
            problems.append("anchor probe: the probe did not compile:\n" + r.stderr)
            return
        r = subprocess.run([str(JAVA), "-cp", "%s:%s:%s" % (probe_out, stub_out, core_out),
                            "Anchor420Probe"], capture_output=True, text=True)
        if r.returncode != 0 or "ANCHOR_OK=true" not in r.stdout:
            problems.append("anchor probe failed:\n" + r.stdout + r.stderr)
        else:
            print("  jvm probe   : " + r.stdout.strip().replace("\n", " | "))


def main(argv):
    args = list(argv)
    base_path = None
    if "--base" in args:
        at = args.index("--base")
        if at + 1 >= len(args):
            print("usage: anchor_check.py <jar> [--base <base.jar>]", file=sys.stderr)
            return 2
        base_path = pathlib.Path(args[at + 1])
        del args[at:at + 2]
    if len(args) != 1:
        print("usage: anchor_check.py <jar> [--base <base.jar>]", file=sys.stderr)
        return 2
    jar_path = pathlib.Path(args[0])
    if not jar_path.is_file():
        print("no such jar: %s" % jar_path, file=sys.stderr)
        return 2

    with zipfile.ZipFile(jar_path) as zf:
        blobs = {name: zf.read(name) for name in zf.namelist()}
    base_blobs = {}
    if base_path and base_path.is_file():
        with zipfile.ZipFile(base_path) as zf:
            base_blobs = {name: zf.read(name) for name in zf.namelist()
                          if name.endswith(".class") and name.startswith("dev/settledlands/")}

    problems = []
    check_classes(blobs, problems)
    check_stores(blobs, problems)
    check_marker(blobs, problems)
    check_pass(blobs, problems)
    check_interact(blobs, problems)
    if base_blobs:
        check_external(base_blobs, blobs, problems)
    run_probe(jar_path, problems, blobs)

    if problems:
        for line in problems:
            print("FAIL:", line, file=sys.stderr)
        return 1

    print("anchor_check: OK  %s" % jar_path)
    print("  rule        : turn/pack/fingerprint/materialize, no game class touched")
    print("  files       : block triggers, door graphs, chest graphs, door locks")
    print("  marker      : settledlands:guide_trigger + IEntityExtension persistent data")
    print("  pass        : ServerTickEvent.Post, paced, the turn comes from the fingerprint")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
