#!/usr/bin/env python3
"""Patch Blockbench name handling in the bundled Settled Lands JAR.

The repository contains the released JAR rather than Java sources. This narrowly
patches the compiled Blockbench parser and model lookup, adds a tiny helper
class, and normalizes exported animation identifiers like
"ivy_oleander.animation.walk" to "walk". Other mod behavior is left alone.
"""
from __future__ import annotations

import argparse
import os
import struct
import tempfile
import zipfile
from pathlib import Path

PARSER = "dev/settledlands/model/BbModelParser.class"
MODEL = "dev/settledlands/model/BbModel.class"
HELPER = "dev/settledlands/model/BbNameNormalizer.class"
HELPER_INTERNAL = "dev/settledlands/model/BbNameNormalizer"
STRING_TRIM = ("java/lang/String", "trim", "()Ljava/lang/String;")
STRING_LOWER = ("java/lang/String", "toLowerCase", "()Ljava/lang/String;")
PARSE_DESC = "(Ljava/lang/String;Ljava/lang/String;Ljava/io/File;)Ldev/settledlands/model/BbModel;"
PARSE_BONE_DESC = "(Lcom/google/gson/JsonObject;Ljava/util/Map;Ljava/util/Map;Ljava/util/Set;[I)Ldev/settledlands/model/BbModelParser$BoneNode;"
GET_PART_DESC = "(Ljava/lang/String;)Lnet/minecraft/client/model/geom/ModelPart;"


def u1(data: bytes, offset: int) -> int:
    return data[offset]


def u2(data: bytes, offset: int) -> int:
    return struct.unpack_from(">H", data, offset)[0]


def u4(data: bytes, offset: int) -> int:
    return struct.unpack_from(">I", data, offset)[0]


def put_u2(value: int) -> bytes:
    return struct.pack(">H", value)


def parse_constant_pool(data: bytes):
    count = u2(data, 8)
    entries: list[tuple[int, object] | None] = [None] * count
    offset = 10
    index = 1
    while index < count:
        tag = u1(data, offset)
        offset += 1
        if tag == 1:
            size = u2(data, offset)
            offset += 2
            raw = data[offset:offset + size]
            offset += size
            # All constants used by this patch are ASCII; decode the input's
            # modified UTF-8 through latin-1 only for stable identity here.
            entries[index] = (tag, raw.decode("utf-8", errors="replace"))
        elif tag in (3, 4):
            entries[index] = (tag, data[offset:offset + 4])
            offset += 4
        elif tag in (5, 6):
            entries[index] = (tag, data[offset:offset + 8])
            offset += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            entries[index] = (tag, u2(data, offset))
            offset += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            entries[index] = (tag, (u2(data, offset), u2(data, offset + 2)))
            offset += 4
        elif tag == 15:
            entries[index] = (tag, (u1(data, offset), u2(data, offset + 1)))
            offset += 3
        else:
            raise ValueError(f"Unknown constant-pool tag {tag} at index {index}")
        index += 1
    return entries, offset


class PoolAppender:
    def __init__(self, entries: list[tuple[int, object] | None]):
        self.entries = entries
        self.additions: list[bytes] = []
        self.index: dict[tuple, int] = {}
        for i, entry in enumerate(entries):
            if entry is not None:
                self.index[entry] = i

    def _add(self, key: tuple, encoded: bytes) -> int:
        existing = self.index.get(key)
        if existing is not None:
            return existing
        idx = len(self.entries)  # current index, including prior additions
        self.entries.append(key)
        self.additions.append(encoded)
        self.index[key] = idx
        return idx

    def utf8(self, text: str) -> int:
        raw = text.encode("utf-8")
        key = (1, text)
        return self._add(key, b"\x01" + put_u2(len(raw)) + raw)

    def clazz(self, name: str) -> int:
        name_idx = self.utf8(name)
        return self._add((7, name_idx), b"\x07" + put_u2(name_idx))

    def string(self, value: str) -> int:
        value_idx = self.utf8(value)
        return self._add((8, value_idx), b"\x08" + put_u2(value_idx))

    def name_type(self, name: str, desc: str) -> int:
        n, d = self.utf8(name), self.utf8(desc)
        return self._add((12, n, d), b"\x0c" + put_u2(n) + put_u2(d))

    def methodref(self, owner: str, name: str, desc: str) -> int:
        c, nt = self.clazz(owner), self.name_type(name, desc)
        return self._add((10, c, nt), b"\x0a" + put_u2(c) + put_u2(nt))


def cp_value(entries, idx):
    return entries[idx]


def resolve_class_name(entries, idx):
    tag, name_idx = cp_value(entries, idx)
    assert tag == 7
    return cp_value(entries, name_idx)[1]


def resolve_name_type(entries, idx):
    tag, (name_idx, desc_idx) = cp_value(entries, idx)
    assert tag == 12
    return cp_value(entries, name_idx)[1], cp_value(entries, desc_idx)[1]


def method_ref(entries, idx):
    tag, (class_idx, nt_idx) = cp_value(entries, idx)
    if tag not in (10, 11):
        return None
    owner = resolve_class_name(entries, class_idx)
    name, desc = resolve_name_type(entries, nt_idx)
    return owner, name, desc


def instruction_length(code: bytes, pos: int) -> int:
    op = code[pos]
    if op == 0xAA:  # tableswitch
        p = pos + 1
        p += (-p) & 3
        low, high = struct.unpack_from(">ii", code, p + 4)
        return (p - pos) + 12 + 4 * (high - low + 1)
    if op == 0xAB:  # lookupswitch
        p = pos + 1
        p += (-p) & 3
        npairs = u4(code, p + 4)
        return (p - pos) + 8 + 8 * npairs
    if op == 0xC4:  # wide
        return 6 if code[pos + 1] == 0x84 else 4
    if op in (0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19,
              0x36, 0x37, 0x38, 0x39, 0x3A, 0xA9, 0xBC):
        return 2
    if op in (0x11, 0x13, 0x14, 0x84,
              *range(0x99, 0xA9), 0xB2, 0xB3, 0xB4, 0xB5,
              0xB6, 0xB7, 0xB8, 0xBB, 0xBD, 0xC0, 0xC1,
              0xC6, 0xC7):
        return 3
    if op in (0xB9, 0xBA, 0xC8, 0xC9):
        return 5
    if op == 0xC5:
        return 4
    return 1


def inject_methodref(data: bytes, pool: PoolAppender) -> bytes:
    additions = b"".join(pool.additions)
    new_count = len(pool.entries)
    if new_count > 65535:
        raise ValueError("Constant pool limit exceeded")
    _, cp_end = parse_constant_pool(data)
    return data[:8] + put_u2(new_count) + data[10:cp_end] + additions + data[cp_end:]


def locate_methods(data: bytes):
    entries, cp_end = parse_constant_pool(data)
    off = cp_end
    off += 6  # access_flags, this_class, super_class
    interfaces = u2(data, off)
    off += 2 + 2 * interfaces

    def skip_members(offset):
        count = u2(data, offset)
        offset += 2
        for _ in range(count):
            offset += 6
            attrs = u2(data, offset)
            offset += 2
            for _ in range(attrs):
                length = u4(data, offset + 2)
                offset += 6 + length
        return offset

    off = skip_members(off)  # fields
    method_count = u2(data, off)
    off += 2
    methods = {}
    for _ in range(method_count):
        access = u2(data, off)
        name_idx = u2(data, off + 2)
        desc_idx = u2(data, off + 4)
        name = entries[name_idx][1]
        desc = entries[desc_idx][1]
        attr_count = u2(data, off + 6)
        off += 8
        for _ in range(attr_count):
            attr_name_idx = u2(data, off)
            attr_length = u4(data, off + 2)
            attr_name = entries[attr_name_idx][1]
            attr_start = off + 6
            if attr_name == "Code":
                code_len = u4(data, attr_start + 4)
                code_start = attr_start + 8
                methods[(name, desc)] = (code_start, data[code_start:code_start + code_len], access)
            off = attr_start + attr_length
    return entries, methods


def patch_calls(
    data: bytes,
    method_specs: dict[tuple[str, str], list[tuple[tuple, int]]],
    helper_method: str = "normalize",
) -> tuple[bytes, dict]:
    entries, _ = parse_constant_pool(data)
    pool = PoolAppender(entries)
    helper_ref = pool.methodref(HELPER_INTERNAL, helper_method, "(Ljava/lang/String;)Ljava/lang/String;")
    data = inject_methodref(data, pool)
    entries, methods = locate_methods(data)
    patched = {}
    mutable = bytearray(data)

    for method_key, expected in method_specs.items():
        if method_key not in methods:
            raise ValueError(f"Expected method not found: {method_key}")
        code_start, code, _ = methods[method_key]
        candidates = []
        pos = 0
        wanted_refs = {spec[0] for spec in expected}
        while pos < len(code):
            op = code[pos]
            if op in (0xB6, 0xB8):
                cp_idx = u2(code, pos + 1)
                ref = method_ref(entries, cp_idx)
                if ref in wanted_refs:
                    candidates.append((pos, ref))
            pos += instruction_length(code, pos)
        # Select requested matching occurrence(s), in instruction order.
        for ref, occurrence in expected:
            matches = [pos for pos, got in candidates if got == ref]
            if occurrence >= len(matches):
                raise ValueError(f"Did not find occurrence {occurrence} of {ref} in {method_key}; found {len(matches)}")
            pos = matches[occurrence]
            mutable[code_start + pos] = 0xB8  # invokevirtual -> invokestatic; stack effect stays ref -> ref
            mutable[code_start + pos + 1:code_start + pos + 3] = put_u2(helper_ref)
            patched.setdefault(f"{method_key[0]}{method_key[1]}", []).append(pos)
    return bytes(mutable), patched


def make_helper_class() -> bytes:
    cp: list[bytes] = []
    indices: dict[tuple, int] = {}

    def add(key, raw):
        if key in indices:
            return indices[key]
        index = len(cp) + 1
        cp.append(raw)
        indices[key] = index
        return index

    def utf8(s):
        raw = s.encode("utf-8")
        return add((1, s), b"\x01" + put_u2(len(raw)) + raw)

    def cls(s):
        name = utf8(s)
        return add((7, s), b"\x07" + put_u2(name))

    def nt(name, desc):
        n, d = utf8(name), utf8(desc)
        return add((12, name, desc), b"\x0c" + put_u2(n) + put_u2(d))

    def method(owner, name, desc):
        c, ntype = cls(owner), nt(name, desc)
        return add((10, owner, name, desc), b"\x0a" + put_u2(c) + put_u2(ntype))

    this_idx = cls(HELPER_INTERNAL)
    super_idx = cls("java/lang/Object")
    code_name = utf8("Code")
    string_class = "java/lang/String"
    trim_ref = method(string_class, "trim", "()Ljava/lang/String;")
    replace_ref = method(string_class, "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;")
    lower_ref = method(string_class, "toLowerCase", "()Ljava/lang/String;")
    replace_first_ref = method(string_class, "replaceFirst", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
    underscore = add((8, "_"), b"\x08" + put_u2(utf8("_")))
    empty = add((8, ""), b"\x08" + put_u2(utf8("")))
    animation_prefix_regex = "^.*[.]animation[.]"
    prefix_regex = add((8, animation_prefix_regex), b"\x08" + put_u2(utf8(animation_prefix_regex)))

    def add_method(name: str, code: bytes, max_stack: int, max_locals: int):
        method_name = utf8(name)
        method_desc = utf8("(Ljava/lang/String;)Ljava/lang/String;")
        code_attr = put_u2(max_stack) + put_u2(max_locals) + struct.pack(">I", len(code)) + code + put_u2(0) + put_u2(0)
        return (
            put_u2(0x0009) + put_u2(method_name) + put_u2(method_desc) + put_u2(1)
            + put_u2(code_name) + struct.pack(">I", len(code_attr)) + code_attr
        )

    def ldc(index: int) -> bytes:
        return bytes([0x12, index]) if index <= 0xFF else bytes([0x13]) + put_u2(index)

    normalize_code = bytearray([0x2A, 0xB6]) + bytearray(put_u2(trim_ref))
    normalize_code += ldc(underscore) + ldc(empty)
    normalize_code += bytes([0xB6]) + put_u2(replace_ref)
    normalize_code += bytes([0xB6]) + put_u2(lower_ref) + bytes([0xB0])

    # Normalize animation identifiers such as "ivy_oleander.animation.walk"
    # to "walk" while preserving underscores in the actual animation name.
    # Keep this straight-line bytecode: the helper has no StackMapTable
    # generator, so a conditional branch here would fail JVM verification.
    animation_code = bytearray([0x2A, 0xB6]) + bytearray(put_u2(trim_ref))
    animation_code += bytes([0xB6]) + put_u2(lower_ref)
    animation_code += ldc(prefix_regex) + ldc(empty)
    animation_code += bytes([0xB6]) + put_u2(replace_first_ref) + bytes([0xB0])

    methods = [
        add_method("normalize", bytes(normalize_code), 3, 1),
        add_method("normalizeAnimation", bytes(animation_code), 3, 3),
    ]
    return (
        b"\xca\xfe\xba\xbe" + put_u2(0) + put_u2(65) + put_u2(len(cp) + 1)
        + b"".join(cp) + put_u2(0x0031) + put_u2(this_idx) + put_u2(super_idx)
        + put_u2(0) + put_u2(0) + put_u2(len(methods)) + b"".join(methods) + put_u2(0)
    )

def patch_jar(input_path: Path, output_path: Path):
    with zipfile.ZipFile(input_path, "r") as src:
        names = set(src.namelist())
        if PARSER not in names or MODEL not in names:
            raise ValueError("This is not the expected Settled Lands JAR (model classes missing)")
        if HELPER in names:
            raise ValueError("Jar already contains the Blockbench name helpers; refusing to patch twice")
        parser = src.read(PARSER)
        model = src.read(MODEL)

        # Keep the prior underscore-insensitive animator and bone matching.
        # Patch the later trim call first so the animation-name call remains
        # occurrence zero when it is normalized in the following pass.
        parser, parser_changes = patch_calls(parser, {
            ("parse", PARSE_DESC): [(STRING_TRIM, 1)],
            ("parseBone", PARSE_BONE_DESC): [(STRING_TRIM, 0), (STRING_TRIM, 1)],
        })
        # Strip the Blockbench project/export prefix from animation names so
        # e.g. "ivy_oleander.animation.walk" is indexed as exactly "walk".
        parser, animation_name_changes = patch_calls(parser, {
            ("parse", PARSE_DESC): [(STRING_TRIM, 0)],
        }, "normalizeAnimation")
        # Normalize caller-supplied animation names too, then exact lookup wins
        # over the legacy substring fallback (so "walk" won't resolve to
        # "orthodox_walk" merely because that entry appears first).
        model, lookup_changes = patch_calls(model, {
            ("findAnimation", "(Ljava/lang/String;)Ldev/settledlands/model/BbAnimation;"): [(STRING_LOWER, 0)],
        }, "normalizeAnimation")
        model, part_changes = patch_calls(model, {
            ("getPart", GET_PART_DESC): [(STRING_LOWER, 0)],
        })
        model_changes = {**lookup_changes, **part_changes}

        output_path.parent.mkdir(parents=True, exist_ok=True)
        fd, temporary = tempfile.mkstemp(prefix=output_path.name + ".", suffix=".tmp", dir=output_path.parent)
        os.close(fd)
        try:
            with zipfile.ZipFile(temporary, "w") as dst:
                for item in src.infolist():
                    if item.filename == PARSER:
                        contents = parser
                    elif item.filename == MODEL:
                        contents = model
                    else:
                        contents = src.read(item.filename)
                    dst.writestr(item, contents)
                dst.writestr(HELPER, make_helper_class(), compress_type=zipfile.ZIP_DEFLATED)
            os.replace(temporary, output_path)
            os.chmod(output_path, 0o644)
        except Exception:
            try:
                os.unlink(temporary)
            except FileNotFoundError:
                pass
            raise
    print(f"Wrote {output_path}")
    print(f"Patched parser bone/animator call sites: {parser_changes}")
    print(f"Patched parser animation-name call sites: {animation_name_changes}")
    print(f"Patched model lookup call sites: {model_changes}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    patch_jar(args.input, args.output)


if __name__ == "__main__":
    main()
