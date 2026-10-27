#!/usr/bin/env python3
"""Build 238 without javac: owner-only corpse item recovery and 1/60 HP.

Builds on the exact, in-game-tested build 237. Existing corpse-interaction
handlers cancel ordinary clicks, so they call the helper just before canceling;
coffin/lead/editing-tool paths are left alone. The helper executes on the server,
checks the owner UUID and purgatory queue/dimension, and transfers each stack
incrementally. If inventory is full, remaining serialized items stay in the
corpse. Ordinary non-possessed player deaths retain their existing behavior.
"""
import argparse
import hashlib
import json
import pathlib
import struct
import sys
import tomllib
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classedit
import decode
from patch_earlytex_mixin import CP, u2, u4, attr
from patch_probe233 import Asm
from patch_corpse_pose237 import assert_pool_roundtrip, find_invoke

BASE_SHA = '9abf34da8a566d882e4bb7a4561a93e019aa4c5a58461ec1e54fc51039e58c45'
OLD_VERSION = '0.8.0-build237-corpse-pose'
NEW_VERSION = '0.8.0-build238-corpse-recovery'
CORPSE = 'dev/settledlands/npc/GuideCorpseNpc'
IVY = 'dev/settledlands/npc/IvyDeathScene'
HELPER = 'dev/settledlands/npc/CorpseRecovery'
PLAYER = 'net/minecraft/world/entity/player/Player'
SERVER_PLAYER = 'net/minecraft/server/level/ServerPlayer'
ENTITY = 'net/minecraft/world/entity/Entity'
LEVEL = 'net/minecraft/world/level/Level'
SERVER_LEVEL = 'net/minecraft/server/level/ServerLevel'
SERVER = 'net/minecraft/server/MinecraftServer'
PURGATORY = 'dev/settledlands/revival/PurgatoryManager'
PURGATORY_DATA = 'dev/settledlands/revival/PurgatoryData'
RESOURCE_KEY = 'net/minecraft/resources/ResourceKey'
TAG = 'net/minecraft/nbt/Tag'
COMPOUND = 'net/minecraft/nbt/CompoundTag'
LISTTAG = 'net/minecraft/nbt/ListTag'
EXT = 'net/neoforged/neoforge/common/extensions/IEntityExtension'
REGISTRY = 'net/minecraft/core/RegistryAccess'
PROVIDER = 'net/minecraft/core/HolderLookup$Provider'
STACK = 'net/minecraft/world/item/ItemStack'
INVENTORY = 'net/minecraft/world/entity/player/Inventory'
COMPONENT = 'net/minecraft/network/chat/Component'
MUTABLE = 'net/minecraft/network/chat/MutableComponent'
COMPONENT_CONTENTS = 'net/minecraft/network/chat/ComponentContents'
TRANSLATABLE_CONTENTS = 'net/minecraft/network/chat/contents/TranslatableContents'
HOLDER = 'net/minecraft/core/Holder'
DYNAMIC_OPS = 'com/mojang/serialization/DynamicOps'
CODEC = 'com/mojang/serialization/Codec'
DATA_RESULT = 'com/mojang/serialization/DataResult'
NBT_OPS = 'net/minecraft/nbt/NbtOps'
REGISTRY_OPS = 'net/minecraft/resources/RegistryOps'
ATTRIBUTES = 'net/minecraft/world/entity/ai/attributes/Attributes'
ATTRIBUTE_INSTANCE = 'net/minecraft/world/entity/ai/attributes/AttributeInstance'
OWNER_GETTER = 'settledlands$getOwnerUuid'
ITEM_KEY = 'settledlands_corpse_items'
DONE_KEY = 'message.settledlands.corpse.items_returned'
PARTIAL_KEY = 'message.settledlands.corpse.items_remaining'
EVENTS = {
    'onInteractCorpse': 'net/neoforged/neoforge/event/entity/player/PlayerInteractEvent$EntityInteract',
    'onInteractSpecificCorpse': 'net/neoforged/neoforge/event/entity/player/PlayerInteractEvent$EntityInteractSpecific',
}
EXPECTED_CHANGED = {CORPSE + '.class', IVY + '.class', 'META-INF/neoforge.mods.toml',
                    'settledlands-patch-info.txt', 'assets/settledlands/lang/en_us.json',
                    'assets/settledlands/lang/ru_ru.json'}
CHANGELOG = ('Build 238: a player who escaped purgatory can right-click their own possessed-body corpse '
             'to recover its stored items; overflow remains on that corpse. Corpses have 1/1 health, '
             'coffins 60/60, and remain invulnerable. Ordinary player death drops are unchanged.')
PATCH_INFO = ('сборка 238: после самостоятельного выхода из чистилища владелец может ПКМ забрать '
              'содержимое именно своего трупа-тела; то, что не поместилось, остаётся в этом трупе. '
              'Трупу задаётся 1/1 HP, гробу 60/60 HP; неуязвимость сохраняется. Обычные смерти без '
              'вселения не меняются.')


def invokeinterface(a, cp, owner, name, desc, count):
    cls = cp.cls(owner)
    nat = cp.nat(name, desc)
    index = cp._add(('im', owner, name, desc), b'\x0b' + u2(cls) + u2(nat))
    a.op(0xb9, index)
    a.raw(count, 0)


def ldc(a, cp, value):
    idx = cp.string(value)
    if idx <= 255:
        a.raw(0x12, idx)
    else:
        a.op(0x13, idx)


def local(a, kind, i):
    # optimized forms for the first four locals; all fixture locals fit in u1
    base, long_op = {'aload': (0x2a, 0x19), 'astore': (0x4b, 0x3a),
                     'iload': (0x1a, 0x15), 'istore': (0x3b, 0x36)}[kind]
    if i < 4:
        a.raw(base + i)
    else:
        a.raw(long_op, i)


def build_recovery_class():
    cp = CP()
    this_i, super_i = cp.cls(HELPER), cp.cls('java/lang/Object')
    code_name = cp.utf8('Code')

    def method(owner, name, desc):
        return cp.method(owner, name, desc)

    def fld(owner, name, desc):
        return cp.field(owner, name, desc)

    # syncHealth(corpse): only server-side, and only on construction or coffin
    # transition/load. Existing IvyDeathScene maintains current HP on later ticks.
    a = Asm()
    a.raw(0x2a); a.op(0xb6, method(CORPSE, 'level', '()L' + LEVEL + ';'))
    a.op(0xb6, method(LEVEL, 'isClientSide', '()Z')); a.jump(0x99, 'health_server')
    a.raw(0xb1)
    a.label('health_server')
    a.raw(0x2a); a.op(0xb2, fld(ATTRIBUTES, 'MAX_HEALTH', 'L' + HOLDER + ';'))
    a.op(0xb6, method(CORPSE, 'getAttribute', '(L' + HOLDER + ';)L' + ATTRIBUTE_INSTANCE + ';'))
    a.raw(0x4c, 0x2b); a.jump(0xc6, 'no_attribute')
    a.raw(0x2b, 0x2a); a.op(0xb6, method(CORPSE, 'isCoffin', '()Z'))
    a.jump(0x99, 'base_body'); a.raw(0x10, 60, 0x87)  # bipush 60; i2d
    a.jump(0xa7, 'set_base')
    a.label('base_body'); a.raw(0x0f)  # dconst_1
    a.label('set_base'); a.op(0xb6, method(ATTRIBUTE_INSTANCE, 'setBaseValue', '(D)V'))
    a.label('no_attribute')
    a.raw(0x2a, 0x2a); a.op(0xb6, method(CORPSE, 'isCoffin', '()Z'))
    a.jump(0x99, 'health_body'); a.raw(0x10, 60, 0x86)  # bipush 60; i2f
    a.jump(0xa7, 'set_health')
    a.label('health_body'); a.raw(0x0c)  # fconst_1
    a.label('set_health'); a.op(0xb6, method(CORPSE, 'setHealth', '(F)V'))
    a.raw(0xb1)
    health = a.finish()

    # Insert a bounded chunk into one known slot at a time. Vanilla's add(stack)
    # silently zeros leftover stacks for creative players when space runs out;
    # pre-sizing each chunk prevents that loss in both survival and creative.
    # 0=inventory, 1=remaining stack, 2=original NBT, 3=slot, 4=slot stack,
    # 5=space in that slot, 6=chunk count, 7=chunk, 8=actually inserted.
    a = Asm()
    a.label('insert_loop')
    local(a, 'aload', 1); a.op(0xb6, method(STACK, 'isEmpty', '()Z')); a.jump(0x9a, 'insert_done')
    local(a, 'aload', 1); a.op(0xb6, method(STACK, 'isDamaged', '()Z')); a.jump(0x9a, 'free_slot')
    local(a, 'aload', 0); local(a, 'aload', 1)
    a.op(0xb6, method(INVENTORY, 'getSlotWithRemainingSpace', '(L' + STACK + ';)I'))
    local(a, 'istore', 3); local(a, 'iload', 3); a.jump(0x9c, 'slot_ready')
    a.label('free_slot')
    local(a, 'aload', 0); a.op(0xb6, method(INVENTORY, 'getFreeSlot', '()I')); local(a, 'istore', 3)
    a.label('slot_ready'); local(a, 'iload', 3); a.jump(0x9b, 'insert_done')
    local(a, 'aload', 0); local(a, 'iload', 3); a.op(0xb6, method(INVENTORY, 'getItem', '(I)L' + STACK + ';'))
    local(a, 'astore', 4); local(a, 'aload', 4); a.op(0xb6, method(STACK, 'isEmpty', '()Z'))
    a.jump(0x99, 'occupied_slot')
    local(a, 'aload', 0); local(a, 'aload', 1)
    a.op(0xb6, method(INVENTORY, 'getMaxStackSize', '(L' + STACK + ';)I')); local(a, 'istore', 5)
    a.jump(0xa7, 'space_ready')
    a.label('occupied_slot'); local(a, 'aload', 0); local(a, 'aload', 4)
    a.op(0xb6, method(INVENTORY, 'getMaxStackSize', '(L' + STACK + ';)I'))
    local(a, 'aload', 4); a.op(0xb6, method(STACK, 'getCount', '()I')); a.raw(0x64)  # isub
    local(a, 'istore', 5)
    a.label('space_ready'); local(a, 'iload', 5); a.jump(0x9e, 'insert_done')
    local(a, 'iload', 5); local(a, 'aload', 1); a.op(0xb6, method(STACK, 'getCount', '()I'))
    a.op(0xb8, method('java/lang/Math', 'min', '(II)I')); local(a, 'istore', 6)
    local(a, 'iload', 6); a.jump(0x9e, 'insert_done')
    local(a, 'aload', 1); local(a, 'iload', 6)
    a.op(0xb6, method(STACK, 'copyWithCount', '(I)L' + STACK + ';')); local(a, 'astore', 7)
    local(a, 'aload', 0); local(a, 'iload', 3); local(a, 'aload', 7)
    a.op(0xb6, method(INVENTORY, 'add', '(IL' + STACK + ';)Z')); a.raw(0x57)
    local(a, 'iload', 6); local(a, 'aload', 7); a.op(0xb6, method(STACK, 'getCount', '()I'))
    a.raw(0x64); local(a, 'istore', 8)
    local(a, 'iload', 8); a.jump(0x9e, 'insert_done')
    local(a, 'aload', 1); local(a, 'iload', 8); a.op(0xb6, method(STACK, 'shrink', '(I)V'))
    local(a, 'aload', 2); ldc(a, cp, 'count'); local(a, 'aload', 1)
    a.op(0xb6, method(STACK, 'getCount', '()I')); a.op(0xb6, method(COMPOUND, 'putInt', '(Ljava/lang/String;I)V'))
    a.jump(0xa7, 'insert_loop')
    a.label('insert_done'); a.raw(0xb1)
    insert_safe = a.finish()

    # tryClaim(Player, Entity): the two input types match the real event API.
    # 2=ServerPlayer, 3=corpse, 4=owner UUID, 5=server, 6=corpse data,
    # 7=ListTag, 8=RegistryAccess, 9=index, 10=item CompoundTag,
    # 11=ItemStack, 12=original count, 13=remaining count.
    a = Asm()
    local(a, 'aload', 0); a.op(0xc1, cp.cls(SERVER_PLAYER)); a.jump(0x99, 'return')
    local(a, 'aload', 1); a.op(0xc1, cp.cls(CORPSE)); a.jump(0x99, 'return')
    local(a, 'aload', 0); a.op(0xc0, cp.cls(SERVER_PLAYER)); local(a, 'astore', 2)
    local(a, 'aload', 1); a.op(0xc0, cp.cls(CORPSE)); local(a, 'astore', 3)
    local(a, 'aload', 2); a.op(0xb6, method(SERVER_PLAYER, 'isAlive', '()Z')); a.jump(0x99, 'return')
    local(a, 'aload', 3); a.op(0xb6, method(CORPSE, OWNER_GETTER, '()Ljava/util/UUID;'))
    local(a, 'astore', 4); local(a, 'aload', 4); a.jump(0xc6, 'return')
    local(a, 'aload', 4); local(a, 'aload', 2); a.op(0xb6, method(SERVER_PLAYER, 'getUUID', '()Ljava/util/UUID;'))
    a.op(0xb6, method('java/util/UUID', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x99, 'return')
    local(a, 'aload', 2); a.op(0xb6, method(SERVER_PLAYER, 'getServer', '()L' + SERVER + ';'))
    local(a, 'astore', 5); local(a, 'aload', 5); a.jump(0xc6, 'return')
    local(a, 'aload', 5); a.op(0xb8, method(PURGATORY_DATA, 'getMaster', '(L' + SERVER + ';)L' + PURGATORY_DATA + ';'))
    local(a, 'aload', 4); a.op(0xb6, method(PURGATORY_DATA, 'isDeadInQueue', '(Ljava/util/UUID;)Z'))
    a.jump(0x9a, 'return')
    a.op(0xb2, fld(PURGATORY, 'PURGATORY_LEVEL', 'L' + RESOURCE_KEY + ';'))
    local(a, 'aload', 2); a.op(0xb6, method(SERVER_PLAYER, 'serverLevel', '()L' + SERVER_LEVEL + ';'))
    a.op(0xb6, method(SERVER_LEVEL, 'dimension', '()L' + RESOURCE_KEY + ';'))
    a.op(0xb6, method('java/lang/Object', 'equals', '(Ljava/lang/Object;)Z')); a.jump(0x9a, 'return')
    local(a, 'aload', 3); a.op(0xc0, cp.cls(EXT))
    invokeinterface(a, cp, EXT, 'getPersistentData', '()L' + COMPOUND + ';', 1)
    local(a, 'astore', 6); local(a, 'aload', 6); ldc(a, cp, ITEM_KEY)
    a.op(0xb6, method(COMPOUND, 'contains', '(Ljava/lang/String;)Z')); a.jump(0x99, 'return')
    local(a, 'aload', 6); ldc(a, cp, ITEM_KEY); a.raw(0x10, 10)
    a.op(0xb6, method(COMPOUND, 'getList', '(Ljava/lang/String;I)L' + LISTTAG + ';'))
    local(a, 'astore', 7); local(a, 'aload', 7); a.op(0xb6, method(LISTTAG, 'isEmpty', '()Z'))
    a.jump(0x9a, 'return')
    local(a, 'aload', 2); a.op(0xb6, method(SERVER_PLAYER, 'level', '()L' + LEVEL + ';'))
    a.op(0xb6, method(LEVEL, 'registryAccess', '()L' + REGISTRY + ';')); local(a, 'astore', 8)
    a.raw(0x03); local(a, 'istore', 9)
    a.label('loop')
    local(a, 'iload', 9); local(a, 'aload', 7); a.op(0xb6, method(LISTTAG, 'size', '()I'))
    a.jump(0xa2, 'finish')  # index >= size
    local(a, 'aload', 7); local(a, 'iload', 9); a.op(0xb6, method(LISTTAG, 'getCompound', '(I)L' + COMPOUND + ';'))
    local(a, 'astore', 10)
    # ItemStack.parse uses resultOrPartial: that could silently strip invalid
    # modded components. Accept only a *complete* codec result and keep the
    # original NBT on parse failure, including exceptions from mod codecs.
    a.label('parse_start')
    a.op(0xb2, fld(STACK, 'CODEC', 'L' + CODEC + ';'))
    local(a, 'aload', 8); a.op(0xb2, fld(NBT_OPS, 'INSTANCE', 'L' + NBT_OPS + ';'))
    invokeinterface(a, cp, PROVIDER, 'createSerializationContext', '(L' + DYNAMIC_OPS + ';)L' + REGISTRY_OPS + ';', 2)
    local(a, 'aload', 10)
    invokeinterface(a, cp, CODEC, 'parse', '(L' + DYNAMIC_OPS + ';Ljava/lang/Object;)L' + DATA_RESULT + ';', 3)
    invokeinterface(a, cp, DATA_RESULT, 'result', '()Ljava/util/Optional;', 1)
    a.op(0xb2, fld(STACK, 'EMPTY', 'L' + STACK + ';'))
    a.op(0xb6, method('java/util/Optional', 'orElse', '(Ljava/lang/Object;)Ljava/lang/Object;'))
    a.op(0xc0, cp.cls(STACK)); local(a, 'astore', 11)
    a.label('parse_end'); a.jump(0xa7, 'parsed')
    a.label('parse_exception'); local(a, 'astore', 14); a.jump(0xa7, 'next')
    a.label('parsed')
    local(a, 'aload', 11); a.op(0xb6, method(STACK, 'isEmpty', '()Z')); a.jump(0x9a, 'next')
    local(a, 'aload', 11); a.op(0xb6, method(STACK, 'getCount', '()I')); local(a, 'istore', 12)
    local(a, 'aload', 2); a.op(0xb6, method(SERVER_PLAYER, 'getInventory', '()L' + INVENTORY + ';'))
    local(a, 'aload', 11); local(a, 'aload', 10)
    a.op(0xb8, method(HELPER, 'insertSafely', '(L' + INVENTORY + ';L' + STACK + ';L' + COMPOUND + ';)V'))
    local(a, 'aload', 11); a.op(0xb6, method(STACK, 'getCount', '()I')); local(a, 'istore', 13)
    local(a, 'iload', 13); local(a, 'iload', 12); a.jump(0xa2, 'next')  # nothing moved
    local(a, 'iload', 13); a.jump(0x9a, 'next')  # partial NBT already updated in insertSafely
    local(a, 'aload', 7); local(a, 'iload', 9); a.op(0xb6, method(LISTTAG, 'remove', '(I)L' + TAG + ';'))
    a.raw(0x57); a.jump(0xa7, 'loop')  # removed: same index now names the next stack
    a.label('next'); a.raw(0x84, 9, 1); a.jump(0xa7, 'loop')
    a.label('finish')
    local(a, 'aload', 7); a.op(0xb6, method(LISTTAG, 'isEmpty', '()Z')); a.jump(0x99, 'partial_message')
    local(a, 'aload', 6); ldc(a, cp, ITEM_KEY); a.op(0xb6, method(COMPOUND, 'remove', '(Ljava/lang/String;)V'))
    def message(key):
        # Component is an interface: a Java-5 classfile cannot invokestatic an
        # InterfaceMethodref. This is exactly Component.translatable(key)'s
        # vanilla 1.21 implementation, using class (not interface) static calls.
        local(a, 'aload', 2)
        a.op(0xbb, cp.cls(TRANSLATABLE_CONTENTS)); a.raw(0x59); ldc(a, cp, key); a.raw(0x01)
        a.op(0xb2, fld(TRANSLATABLE_CONTENTS, 'NO_ARGS', '[Ljava/lang/Object;'))
        a.op(0xb7, method(TRANSLATABLE_CONTENTS, '<init>', '(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V'))
        a.op(0xb8, method(MUTABLE, 'create', '(L' + COMPONENT_CONTENTS + ';)L' + MUTABLE + ';'))
        a.raw(0x04); a.op(0xb6, method(SERVER_PLAYER, 'displayClientMessage', '(L' + COMPONENT + ';Z)V'))
    message(DONE_KEY)
    a.raw(0xb1)
    a.label('partial_message')
    message(PARTIAL_KEY)
    a.label('return'); a.raw(0xb1)
    claim = a.finish()

    init = b'\x2a\xb7' + u2(method('java/lang/Object', '<init>', '()V')) + b'\xb1'
    exception = (a.labels['parse_start'], a.labels['parse_end'], a.labels['parse_exception'],
                 cp.cls('java/lang/Exception'))
    specs = [(0x0002, '<init>', '()V', 1, 1, init, []),
             (0x0009, 'syncHealth', '(L' + CORPSE + ';)V', 4, 2, health, []),
             (0x000a, 'insertSafely', '(L' + INVENTORY + ';L' + STACK + ';L' + COMPOUND + ';)V', 3, 9, insert_safe, []),
             (0x0009, 'tryClaim', '(L' + PLAYER + ';L' + ENTITY + ';)V', 6, 15, claim, [exception])]
    bodies = []
    for flags, name, desc, stack, locals_, code, exceptions in specs:
        exc = b''.join(u2(start) + u2(end) + u2(handler) + u2(typ)
                       for start, end, handler, typ in exceptions)
        info = u2(stack) + u2(locals_) + u4(len(code)) + code + u2(len(exceptions)) + exc + u2(0)
        bodies.append(u2(flags) + u2(cp.utf8(name)) + u2(cp.utf8(desc)) + u2(1) + attr(code_name, info))
    # Build every pool entry before dumping it. major 49 intentionally uses the
    # JVM inference verifier for this small generated class with branches.
    return (b'\xca\xfe\xba\xbe' + u2(0) + u2(49) + cp.dump() +
            u2(0x0031) + u2(this_i) + u2(super_i) + u2(0) + u2(0) +
            u2(len(bodies)) + b''.join(bodies) + u2(0))


def patch_corpse_method(raw, name, desc):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    m = k.find_method(name, desc); assert m
    parts = classedit.code_parts(k, m)
    last_pc, last_ins = list(decode.walk(parts['code']))[-1]
    assert last_ins == b'\xb1' and last_pc not in decode.targets(parts['code'])
    sync = k.add_methodref(HELPER, 'syncHealth', '(L' + CORPSE + ';)V')
    parts = classedit.insert(parts, last_pc, b'\x2a\xb8' + u2(sync))
    parts['max_stack'] = max(parts['max_stack'], 1)
    return k.dump(m, classedit.build_code_body(k, parts))


def add_owner_getter(raw):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    assert k.find_method(OWNER_GETTER) is None
    owner = k.add_fieldref(CORPSE, 'ownerUuid', 'Ljava/util/UUID;')
    code = b'\x2a\xb4' + u2(owner) + b'\xb0'
    body = u2(1) + u2(1) + u4(len(code)) + code + u2(0) + u2(0)
    added = (u2(0x0001) + u2(k.add_utf8(OWNER_GETTER)) + u2(k.add_utf8('()Ljava/util/UUID;')) +
             u2(1) + u2(k.add_utf8('Code')) + u4(len(body)) + body)
    def member(m):
        attrs = b''.join(u2(x['name_idx']) + u4(len(x['body'])) + x['body'] for x in m['attrs'])
        return u2(m['acc']) + u2(m['name_idx']) + u2(m['desc_idx']) + u2(len(m['attrs'])) + attrs
    attrs = b''.join(u2(x['name_idx']) + u4(len(x['body'])) + x['body'] for x in k.attrs)
    return (raw[:8] + u2(len(k.cp)) + k._pool_bytes() + u2(k.access) + u2(k.this) + u2(k.super) +
            u2(len(k.ifaces)) + b''.join(u2(x) for x in k.ifaces) +
            u2(len(k.fields)) + b''.join(member(x) for x in k.fields) +
            u2(len(k.methods) + 1) + b''.join(member(x) for x in k.methods) + added +
            u2(len(k.attrs)) + attrs)


def patch_event(raw, name, event):
    k = classedit.Klass(raw); assert_pool_roundtrip(k, raw)
    m = k.find_method(name, '(L' + event + ';)V'); assert m
    parts = classedit.code_parts(k, m)
    pc, _ = find_invoke(k, parts['code'], event, 'setCanceled', '(Z)V')
    at = pc - 2  # aload_0; iconst_1; invokevirtual setCanceled
    assert parts['code'][at:pc] == b'\x2a\x04' and at not in decode.targets(parts['code'])
    get_player = k.add_methodref(event, 'getEntity', '()L' + PLAYER + ';')
    get_target = k.add_methodref(event, 'getTarget', '()L' + ENTITY + ';')
    claim = k.add_methodref(HELPER, 'tryClaim', '(L' + PLAYER + ';L' + ENTITY + ';)V')
    ins = b'\x2a\xb6' + u2(get_player) + b'\x2a\xb6' + u2(get_target) + b'\xb8' + u2(claim)
    parts = classedit.insert(parts, at, ins)
    parts['max_stack'] = max(parts['max_stack'], 2)
    return k.dump(m, classedit.build_code_body(k, parts))


def add_messages(raw, done, partial):
    text = raw.decode('utf-8'); orig = json.loads(text)
    assert DONE_KEY not in orig and PARTIAL_KEY not in orig and text.endswith('\n}\n')
    text = text[:-3] + ',\n  ' + json.dumps(DONE_KEY) + ': ' + json.dumps(done, ensure_ascii=False)
    text += ',\n  ' + json.dumps(PARTIAL_KEY) + ': ' + json.dumps(partial, ensure_ascii=False) + '\n}\n'
    new = json.loads(text)
    assert {k: v for k, v in new.items() if k not in (DONE_KEY, PARTIAL_KEY)} == orig
    return text.encode('utf-8')


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--base', required=True, type=pathlib.Path)
    p.add_argument('--out', required=True, type=pathlib.Path)
    args = p.parse_args()
    digest = hashlib.sha256(args.base.read_bytes()).hexdigest()
    if digest != BASE_SHA:
        raise SystemExit('unexpected base SHA-256: ' + digest)
    with zipfile.ZipFile(args.base) as base:
        assert base.testzip() is None and len(base.namelist()) == len(set(base.namelist()))
        entries = {i.filename: base.read(i.filename) for i in base.infolist()}
        updated = dict(entries)
        corpse = patch_corpse_method(entries[CORPSE + '.class'], '<init>',
                                     '(Lnet/minecraft/world/entity/EntityType;L' + LEVEL + ';)V')
        corpse = patch_corpse_method(corpse, 'setCoffin', '(Z)V')
        updated[CORPSE + '.class'] = add_owner_getter(corpse)
        ivy = entries[IVY + '.class']
        for name, event in EVENTS.items():
            ivy = patch_event(ivy, name, event)
        updated[IVY + '.class'] = ivy
        updated[HELPER + '.class'] = build_recovery_class()
        updated['assets/settledlands/lang/en_us.json'] = add_messages(
            entries['assets/settledlands/lang/en_us.json'], 'Your items have been recovered from your body.',
            'Some items remain in your body.')
        updated['assets/settledlands/lang/ru_ru.json'] = add_messages(
            entries['assets/settledlands/lang/ru_ru.json'], 'Вещи возвращены из твоего тела.',
            'Часть вещей осталась в твоём теле.')
        text = entries['META-INF/neoforge.mods.toml'].decode('utf-8')
        needle = 'version="' + OLD_VERSION + '"'; assert text.count(needle) == 1
        text = text.replace(needle, 'version="' + NEW_VERSION + '"', 1)
        assert text.count("description='''\n") == 1
        text = text.replace("description='''\n", "description='''\n" + CHANGELOG + '\n', 1)
        assert tomllib.loads(text)['mods'][0]['version'] == NEW_VERSION
        updated['META-INF/neoforge.mods.toml'] = text.encode('utf-8')
        updated['settledlands-patch-info.txt'] = (PATCH_INFO + '\n' +
            entries['settledlands-patch-info.txt'].decode('utf-8')).encode('utf-8')
        changed = {n for n in entries if updated[n] != entries[n]}
        added = set(updated) - set(entries)
        assert changed == EXPECTED_CHANGED and added == {HELPER + '.class'}, (changed, added)
        with zipfile.ZipFile(args.out, 'w') as out:
            for info in base.infolist():
                out.writestr(info, updated[info.filename])
            # New member uses an explicit stable timestamp for reproducible output.
            info = zipfile.ZipInfo(HELPER + '.class', (2026, 10, 27, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            out.writestr(info, updated[HELPER + '.class'])
    with zipfile.ZipFile(args.out) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
    data = args.out.read_bytes()
    print('added:', sorted(added), '| changed:', sorted(changed))
    print(f'{args.out}: {len(data)} bytes sha256 {hashlib.sha256(data).hexdigest()}')


if __name__ == '__main__':
    main()
