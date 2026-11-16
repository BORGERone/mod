#!/usr/bin/env python3
"""
Pack an Axiom schematic (e.g. ``старт.bp``) into ``start.slbp`` (reproducible gzipped MC NBT)
for Build 425 («Адский данж»).

Output schema (root CompoundTag):
  * ``FormatVersion`` (Int = 1)
  * ``Palette``       (List[CompoundTag]: ``Name`` (String), ``Props`` (CompoundTag[String->String]))
  * ``Blocks``        (IntArray: flat [x, y, z, palIdx, ...] for every non-air / non-void_air block)
  * ``BlockEntities`` (List[CompoundTag]: ``id`` (String), ``x`` (Int), ``y`` (Int), ``z`` (Int),
                      ``nbt`` (CompoundTag))
  * ``Entities``      (List[CompoundTag]: ``id`` (String), ``dx`` (Double), ``dy`` (Double),
                      ``dz`` (Double), ``yaw`` (Float), ``pitch`` (Float), ``nbt`` (CompoundTag)
                      with ``UUID`` and ``neoforge:attachments`` stripped)

Usage:
    python3 tools/dungeon/pack_start.py /tmp/dungeonz/старт.bp \
        --out build303-src/data425/settledlands/dungeon/start.slbp
"""

import argparse
import gzip
import io
import math
import pathlib
import struct
import sys

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


class NbtReader:
    def __init__(self, data: bytes, pos: int = 0):
        self.data = data
        self.pos = pos

    def u1(self) -> int:
        v = self.data[self.pos]
        self.pos += 1
        return v

    def i1(self) -> int:
        v = struct.unpack_from(">b", self.data, self.pos)[0]
        self.pos += 1
        return v

    def u2(self) -> int:
        v = struct.unpack_from(">H", self.data, self.pos)[0]
        self.pos += 2
        return v

    def i2(self) -> int:
        v = struct.unpack_from(">h", self.data, self.pos)[0]
        self.pos += 2
        return v

    def i4(self) -> int:
        v = struct.unpack_from(">i", self.data, self.pos)[0]
        self.pos += 4
        return v

    def i8(self) -> int:
        v = struct.unpack_from(">q", self.data, self.pos)[0]
        self.pos += 8
        return v

    def f4(self) -> float:
        v = struct.unpack_from(">f", self.data, self.pos)[0]
        self.pos += 4
        return v

    def f8(self) -> float:
        v = struct.unpack_from(">d", self.data, self.pos)[0]
        self.pos += 8
        return v

    def string(self) -> str:
        n = self.u2()
        raw = self.data[self.pos:self.pos + n]
        self.pos += n
        return raw.decode("utf-8", "replace")

    def read_payload(self, tid: int):
        if tid == TAG_BYTE:
            return (TAG_BYTE, self.i1())
        if tid == TAG_SHORT:
            return (TAG_SHORT, self.i2())
        if tid == TAG_INT:
            return (TAG_INT, self.i4())
        if tid == TAG_LONG:
            return (TAG_LONG, self.i8())
        if tid == TAG_FLOAT:
            return (TAG_FLOAT, self.f4())
        if tid == TAG_DOUBLE:
            return (TAG_DOUBLE, self.f8())
        if tid == TAG_BYTE_ARRAY:
            n = self.i4()
            raw = self.data[self.pos:self.pos + n]
            self.pos += n
            return (TAG_BYTE_ARRAY, raw)
        if tid == TAG_STRING:
            return (TAG_STRING, self.string())
        if tid == TAG_LIST:
            item_tid = self.u1()
            n = self.i4()
            items = [self.read_payload(item_tid) for _ in range(n)]
            return (TAG_LIST, (item_tid, items))
        if tid == TAG_COMPOUND:
            out = {}
            while True:
                t = self.u1()
                if t == TAG_END:
                    break
                k = self.string()
                out[k] = self.read_payload(t)
            return (TAG_COMPOUND, out)
        if tid == TAG_INT_ARRAY:
            n = self.i4()
            vals = list(struct.unpack_from(f">{n}i", self.data, self.pos))
            self.pos += 4 * n
            return (TAG_INT_ARRAY, vals)
        if tid == TAG_LONG_ARRAY:
            n = self.i4()
            vals = list(struct.unpack_from(f">{n}q", self.data, self.pos))
            self.pos += 8 * n
            return (TAG_LONG_ARRAY, vals)
        raise ValueError(f"unsupported NBT tag type {tid}")

    @classmethod
    def read_root(cls, raw: bytes):
        r = cls(raw)
        tid = r.u1()
        if tid != TAG_COMPOUND:
            raise ValueError(f"expected root TAG_Compound, got {tid}")
        name = r.string()
        return name, r.read_payload(TAG_COMPOUND)


class NbtWriter:
    def __init__(self):
        self.buf = io.BytesIO()

    def write_string(self, s: str):
        raw = s.encode("utf-8")
        self.buf.write(struct.pack(">H", len(raw)))
        self.buf.write(raw)

    def write_payload(self, typed_val):
        tid, val = typed_val
        if tid == TAG_BYTE:
            self.buf.write(struct.pack(">b", val))
        elif tid == TAG_SHORT:
            self.buf.write(struct.pack(">h", val))
        elif tid == TAG_INT:
            self.buf.write(struct.pack(">i", val))
        elif tid == TAG_LONG:
            self.buf.write(struct.pack(">q", val))
        elif tid == TAG_FLOAT:
            self.buf.write(struct.pack(">f", val))
        elif tid == TAG_DOUBLE:
            self.buf.write(struct.pack(">d", val))
        elif tid == TAG_BYTE_ARRAY:
            self.buf.write(struct.pack(">i", len(val)))
            self.buf.write(val)
        elif tid == TAG_STRING:
            self.write_string(val)
        elif tid == TAG_LIST:
            item_tid, items = val
            if not items:
                item_tid = TAG_END
            self.buf.write(struct.pack(">Bi", item_tid, len(items)))
            for item in items:
                self.write_payload(item)
        elif tid == TAG_COMPOUND:
            for k, v in val.items():
                self.buf.write(struct.pack(">B", v[0]))
                self.write_string(k)
                self.write_payload(v)
            self.buf.write(b"\x00")
        elif tid == TAG_INT_ARRAY:
            self.buf.write(struct.pack(f">i{len(val)}i", len(val), *val))
        elif tid == TAG_LONG_ARRAY:
            self.buf.write(struct.pack(f">i{len(val)}q", len(val), *val))
        else:
            raise ValueError(f"unsupported write tag {tid}")

    @classmethod
    def encode_root(cls, root_compound, name: str = "") -> bytes:
        w = cls()
        w.buf.write(struct.pack(">B", TAG_COMPOUND))
        w.write_string(name)
        w.write_payload(root_compound)
        return w.buf.getvalue()


def read_bp_typed(path: pathlib.Path):
    raw = path.read_bytes()
    header_end = None
    for off in range(0, 24):
        try:
            r = NbtReader(raw, off)
            if r.u1() != TAG_COMPOUND:
                continue
            r.string()
            hdr = r.read_payload(TAG_COMPOUND)[1]
            if "Version" in hdr:
                header_end = r.pos
                break
        except Exception:
            continue
    if header_end is None:
        raise ValueError(f"no Axiom header found in {path}")
    rest = raw[header_end:]
    png_len = struct.unpack_from(">i", rest, 0)[0]
    after_png = rest[4 + png_len:]
    blob_len = struct.unpack_from(">i", after_png, 0)[0]
    body_raw = gzip.decompress(after_png[4:4 + blob_len])
    _, data_typed = NbtReader.read_root(body_raw)
    return data_typed[1]


def unpack_palette_indices(region_map):
    bs = region_map["BlockStates"][1]
    pal_list = bs["palette"][1][1]
    longs = bs["data"][1] if "data" in bs else []
    if len(pal_list) <= 1 or not longs:
        return pal_list, [0] * 4096
    bits = max(4, math.ceil(math.log2(max(2, len(pal_list)))))
    per = 64 // bits
    mask = (1 << bits) - 1
    out = []
    for v in longs:
        u = v & 0xFFFFFFFFFFFFFFFF
        for k in range(per):
            out.append((u >> (k * bits)) & mask)
    return pal_list, out[:4096]


def convert_bp_to_slbp(bp_path: pathlib.Path) -> bytes:
    data = read_bp_typed(bp_path)
    palette = []
    pal_lookup = {}
    blocks_flat = []

    regions = data.get("BlockRegion", (TAG_LIST, (TAG_COMPOUND, [])))[1][1]
    for _, reg_map in regions:
        rx = reg_map["X"][1] * 16
        ry = reg_map["Y"][1] * 16
        rz = reg_map["Z"][1] * 16
        pal_entries, idxs = unpack_palette_indices(reg_map)
        for i, pal_idx in enumerate(idxs):
            if pal_idx >= len(pal_entries):
                continue
            st_map = pal_entries[pal_idx][1]
            name = st_map["Name"][1]
            if name in ("minecraft:air", "minecraft:void_air"):
                continue
            props_dict = {}
            if "Properties" in st_map:
                for pk, pv in sorted(st_map["Properties"][1].items()):
                    props_dict[pk] = (TAG_STRING, str(pv[1]))
            sig = (name, tuple((k, v[1]) for k, v in props_dict.items()))
            out_idx = pal_lookup.get(sig)
            if out_idx is None:
                out_idx = len(palette)
                pal_lookup[sig] = out_idx
                palette.append((
                    TAG_COMPOUND,
                    {
                        "Name": (TAG_STRING, name),
                        "Props": (TAG_COMPOUND, props_dict),
                    },
                ))
            y = i >> 8
            z = (i >> 4) & 15
            x = i & 15
            blocks_flat.extend((rx + x, ry + y, rz + z, out_idx))

    block_entities_out = []
    if "BlockEntities" in data:
        for _, be in data["BlockEntities"][1][1]:
            be_id = be.get("id", (TAG_STRING, ""))[1]
            bx = be.get("x", (TAG_INT, 0))[1]
            by = be.get("y", (TAG_INT, 0))[1]
            bz = be.get("z", (TAG_INT, 0))[1]
            clean_nbt = {k: v for k, v in be.items() if k not in ("x", "y", "z")}
            block_entities_out.append((
                TAG_COMPOUND,
                {
                    "id": (TAG_STRING, be_id),
                    "x": (TAG_INT, bx),
                    "y": (TAG_INT, by),
                    "z": (TAG_INT, bz),
                    "nbt": (TAG_COMPOUND, clean_nbt),
                },
            ))

    entities_out = []
    if "Entities" in data:
        for _, ent in data["Entities"][1][1]:
            ent_id = ent.get("id", (TAG_STRING, ""))[1]
            pos_list = ent.get("Pos", (TAG_LIST, (TAG_DOUBLE, [])))[1][1]
            dx = float(pos_list[0][1]) if len(pos_list) > 0 else 0.0
            dy = float(pos_list[1][1]) if len(pos_list) > 1 else 0.0
            dz = float(pos_list[2][1]) if len(pos_list) > 2 else 0.0
            rot_list = ent.get("Rotation", (TAG_LIST, (TAG_FLOAT, [])))[1][1]
            yaw = float(rot_list[0][1]) if len(rot_list) > 0 else 0.0
            pitch = float(rot_list[1][1]) if len(rot_list) > 1 else 0.0
            clean_ent = {
                k: v for k, v in ent.items()
                if k not in ("UUID", "neoforge:attachments", "Pos")
            }
            entities_out.append((
                TAG_COMPOUND,
                {
                    "id": (TAG_STRING, ent_id),
                    "dx": (TAG_DOUBLE, dx),
                    "dy": (TAG_DOUBLE, dy),
                    "dz": (TAG_DOUBLE, dz),
                    "yaw": (TAG_FLOAT, yaw),
                    "pitch": (TAG_FLOAT, pitch),
                    "nbt": (TAG_COMPOUND, clean_ent),
                },
            ))

    root = (
        TAG_COMPOUND,
        {
            "FormatVersion": (TAG_INT, 1),
            "Palette": (TAG_LIST, (TAG_COMPOUND if palette else TAG_END, palette)),
            "Blocks": (TAG_INT_ARRAY, blocks_flat),
            "BlockEntities": (
                TAG_LIST,
                (TAG_COMPOUND if block_entities_out else TAG_END, block_entities_out),
            ),
            "Entities": (
                TAG_LIST,
                (TAG_COMPOUND if entities_out else TAG_END, entities_out),
            ),
        },
    )
    raw_nbt = NbtWriter.encode_root(root)
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as gz:
        gz.write(raw_nbt)
    return buf.getvalue()


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("input", help="Path to Axiom .bp file (e.g. старт.bp)")
    ap.add_argument("--out", required=True, help="Output .slbp path")
    args = ap.parse_args(argv)

    inp = pathlib.Path(args.input)
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    slbp_bytes = convert_bp_to_slbp(inp)
    out.write_bytes(slbp_bytes)

    # Round-trip summary
    _, decoded = NbtReader.read_root(gzip.decompress(slbp_bytes))
    d = decoded[1]
    n_pal = len(d["Palette"][1][1])
    n_blk = len(d["Blocks"][1]) // 4
    n_be = len(d["BlockEntities"][1][1])
    n_ent = len(d["Entities"][1][1])
    print(f"wrote {out} ({len(slbp_bytes)} bytes: {n_blk} blocks, {n_pal} palette, {n_be} blockEntities, {n_ent} entities)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
