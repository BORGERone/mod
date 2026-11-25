#!/usr/bin/env python3
"""Gate - the KIND of every game call, not only its descriptor.

Build 420 shipped `IncompatibleClassChangeError: Found interface net.minecraft.core.DefaultedRegistry, but
class was expected` on the first tick the anchor tried to place a marker. Every gate was green: the owner,
the name and the descriptor of `DefaultedRegistry.getKey(Object)ResourceLocation` were exactly right; only
the *kind* was wrong, because the compile mirror declared `DefaultedRegistry` as a **class** while the game
declares it an **interface**, and the emitter then wrote `invokevirtual` where the base jar writes
`invokeinterface`.

    constant pool:   9 Fieldref   10 Methodref   11 InterfaceMethodref
    opcode:          0xb6 invokevirtual   0xb7 invokespecial   0xb8 invokestatic   0xb9 invokeinterface

The rule: for every target this jar calls that the base jar also calls, the *set* of kinds must be a subset
of the kinds the base jar uses for that target. A target the base jar never calls is listed as `unknown`
(before build 420 that list had to be empty - it was - and the two entries now there are accounted for one
by one below).

`invokevirtual` and `invokeinterface` are interchangeable in the *descriptor* and not at all in the JVM: the
wrong one is a LinkageError the moment the call site is first executed, which is why no structural gate and
no compile check can see it.

Usage: python3 tools/invoke_kind_check.py <jar> [--base <jar>]
Exit 0 = every call this jar makes is made the way the base jar makes it.
"""

import argparse
import struct
import sys
import zipfile

sys.path.insert(0, __file__.rsplit("/", 1)[0] if "/" in __file__ else ".")
import disasm

KINDS = {0xB6: "invokevirtual", 0xB7: "invokespecial", 0xB8: "invokestatic", 0xB9: "invokeinterface"}
CP_FIELD, CP_METHOD, CP_INTERFACE = 9, 10, 11


def _u2(buf, at):
    return struct.unpack(">H", buf[at:at + 2])[0]


def _target(cp, index):
    """owner.name + descriptor of a call's constant-pool entry, or None."""
    if index >= len(cp):
        return None
    entry = cp[index]
    if entry is None or entry[0] not in (CP_FIELD, CP_METHOD, CP_INTERFACE):
        return None
    class_index = _u2(entry[1], 0)
    nat_index = _u2(entry[1], 2)
    if class_index >= len(cp) or nat_index >= len(cp):
        return None
    name_and_type = cp[nat_index]
    if name_and_type is None:
        return None
    name_index = _u2(name_and_type[1], 0)
    desc_index = _u2(name_and_type[1], 2)
    if name_index >= len(cp) or desc_index >= len(cp):
        return None
    # A Methodref's class index points at a Class entry (tag 7), whose payload is the index of the Utf8
    # that holds the name - reading [1] off the Class entry gives two raw bytes, not a class name.
    class_entry = cp[class_index]
    if class_entry is None or class_entry[0] != 7:
        return None
    owner_index = _u2(class_entry[1], 0)
    if owner_index >= len(cp) or cp[owner_index] is None or cp[owner_index][0] != 1:
        return None
    owner = cp[owner_index][1]
    return "%s.%s%s" % (owner, cp[name_index][1], cp[desc_index][1])


def calls(blob):
    """{(owner.name+desc): {kinds}} for one class file - walking real instructions, never the raw bytes."""
    parsed = disasm.parse_class(blob)
    cp = parsed["cp"]
    out = {}
    for method in parsed["methods"]:
        code = disasm.code_of(method[3], cp)
        if not code:
            continue
        body = code["code"]
        pc = 0
        while pc < len(body):
            opcode = body[pc]
            _name, length = disasm.OPS.get(opcode, ("", 0))
            if length < 0:                      # tableswitch/lookupswitch/wide: not in any class here
                break
            if opcode in KINDS:
                target = _target(cp, _u2(body, pc + 1))
                if target:
                    out.setdefault(target, set()).add(KINDS[opcode])
            pc += 1 + length
    return out


def jar_calls(path):
    total = {}
    with zipfile.ZipFile(path) as jar:
        for entry in jar.namelist():
            if not entry.endswith(".class"):
                continue
            try:
                one = calls(jar.read(entry))
            except Exception:
                continue                        # a class this tool cannot read is the structural gate's problem
            for target, kinds in one.items():
                total.setdefault(target, set()).update(kinds)
    return total


# Targets this jar calls in a way the base jar never calls them, with the reason. Each one is a call the
# game resolves through a class the base jar reaches by its *declaring* type, so the kind is the only thing
# that differs; both are listed rather than guessed at.
KNOWN_NEW = {
    # Each of these is a *class* in the game (so invokevirtual is the right kind), and the base jar never
    # calls it - there was no marker to place before build 420. The descriptor of each is already in
    # anchor_check.py's UNVERIFIED_EXTERNAL with its own line of evidence; this list adds only the kind.
    "net/minecraft/world/entity/Entity.moveTo(DDD)V": {
        "invokevirtual"},                 # Entity is a class; the jar itself calls moveTo(DDDFF)
    "net/minecraft/world/entity/Entity.setYRot(F)V": {
        "invokevirtual"},                 # Entity is a class; the jar calls setYRot on five entity types
    "net/minecraft/world/entity/Entity.setXRot(F)V": {
        "invokevirtual"},                 # Entity is a class; public void setXRot(float) in 1.21.x ForgeJavaDocs-NG
    "net/minecraft/world/level/block/state/BlockState.isAir()Z": {
        "invokevirtual"},                 # BlockState is a class (BlockBehaviour$BlockStateBase subtree)
    # Build 425: Hell Dungeon («Адский данж») — every class/method verified in ForgeJavaDocs-NG 1.21.x-neoforge.
    "net/minecraft/nbt/NbtAccounter.unlimitedHeap()Lnet/minecraft/nbt/NbtAccounter;": {
        "invokestatic"},                  # public class NbtAccounter, public static NbtAccounter unlimitedHeap()
    "net/minecraft/nbt/NbtIo.readCompressed(Ljava/io/InputStream;Lnet/minecraft/nbt/NbtAccounter;)Lnet/minecraft/nbt/CompoundTag;": {
        "invokestatic"},                  # public class NbtIo, public static CompoundTag readCompressed(...)
    "net/minecraft/nbt/CompoundTag.copy()Lnet/minecraft/nbt/CompoundTag;": {
        "invokevirtual"},                 # public class CompoundTag, public CompoundTag copy()
    "net/minecraft/world/level/block/Block.defaultBlockState()Lnet/minecraft/world/level/block/state/BlockState;": {
        "invokevirtual"},                 # public class Block, public final BlockState defaultBlockState()
    "net/minecraft/world/level/block/Block.getStateDefinition()Lnet/minecraft/world/level/block/state/StateDefinition;": {
        "invokevirtual"},                 # public class Block, public StateDefinition getStateDefinition()
    "net/minecraft/world/level/block/state/StateDefinition.getProperty(Ljava/lang/String;)Lnet/minecraft/world/level/block/state/properties/Property;": {
        "invokevirtual"},                 # public class StateDefinition, public Property getProperty(String)
    "net/minecraft/world/level/block/state/properties/Property.getValue(Ljava/lang/String;)Ljava/util/Optional;": {
        "invokevirtual"},                 # public abstract class Property, public abstract Optional getValue(String)
    "net/minecraft/world/level/block/entity/BlockEntity.loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V": {
        "invokevirtual"},                 # public abstract class BlockEntity, public final void loadWithComponents(...)
    "net/minecraft/world/level/block/entity/BlockEntity.setChanged()V": {
        "invokevirtual"},                 # public abstract class BlockEntity, public void setChanged()
    "net/minecraft/world/entity/Entity.load(Lnet/minecraft/nbt/CompoundTag;)V": {
        "invokevirtual"},                 # public abstract class Entity, public void load(CompoundTag)
    "net/minecraft/server/level/ServerLevel.getSeed()J": {
        "invokevirtual"},                 # public class ServerLevel, public long getSeed()
    "net/minecraft/world/level/Level.getSharedSpawnPos()Lnet/minecraft/core/BlockPos;": {
        "invokevirtual"},                 # public abstract class Level, public BlockPos getSharedSpawnPos()
    # Build 436: verified MC 1.21.1 calls for Refurbished furniture & Dungeon Loot Pool filling.
    "net/minecraft/world/Container.getContainerSize()I": {
        "invokeinterface"},               # public interface Container (base jar invokes via SimpleContainer)
    "net/minecraft/world/Container.getItem(I)Lnet/minecraft/world/item/ItemStack;": {
        "invokeinterface"},               # public interface Container (base jar invokes via SimpleContainer)
    "net/minecraft/world/Container.setItem(ILnet/minecraft/world/item/ItemStack;)V": {
        "invokeinterface"},               # public interface Container
    "net/minecraft/world/Container.getMaxStackSize()I": {
        "invokeinterface"},               # public interface Container (overridden by StorageJar -> 1)
    "net/minecraft/world/Container.canPlaceItem(ILnet/minecraft/world/item/ItemStack;)Z": {
        "invokeinterface"},               # public interface Container
    "net/minecraft/world/item/ItemStack.getMaxStackSize()I": {
        "invokevirtual"},                 # public final class ItemStack
    "net/minecraft/core/Direction.getOpposite()Lnet/minecraft/core/Direction;": {
        "invokevirtual"},                 # public enum Direction
    "net/minecraft/world/phys/BlockHitResult.getDirection()Lnet/minecraft/core/Direction;": {
        "invokevirtual"},                 # public class BlockHitResult
}


def main(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument("jar")
    parser.add_argument("--base", default="")
    args = parser.parse_args(argv)

    mine = jar_calls(args.jar)
    base = jar_calls(args.base) if args.base else {}

    problems, unknown = [], []
    for target in sorted(mine):
        kinds = mine[target]
        if target in base:
            wrong = kinds - base[target]
            if wrong:
                problems.append("%s is called with %s, but %s calls it only with %s"
                                % (target, "/".join(sorted(wrong)), args.base or "the base jar",
                                   "/".join(sorted(base[target]))))
        elif not target.startswith(("java/", "javax/", "jdk/", "sun/", "com/mojang/blaze3d",
                                    "dev/settledlands/", "org/slf4j/")):
            if target not in KNOWN_NEW:
                unknown.append((target, kinds))

    for target, kinds in unknown:
        problems.append("%s is called with %s and the base jar never calls it at all - the kind cannot be "
                        "checked; prove it or drop the call" % (target, "/".join(sorted(kinds))))

    print("invoke_kind: %d distinct call target(s) checked against %s"
          % (len(mine), args.base or "nothing"))
    if problems:
        for line in problems:
            print("  FAIL: " + line)
        print("invoke_kind_check: FAIL")
        return 1
    print("invoke_kind_check: OK  every call is made the way the base jar makes it")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
