#!/usr/bin/env python3
"""Build 273 - repair the Build 272 pursuit mixins that abort the client.

Diagnosis (see tools/check_mixin_class.py output on the shipped build 272 jar):
  1. NpcPursuitCanvasMixin272#settledlands$pursuitFocus
     injects into NpcGraphCanvas#focus(Node)V, which returns void, but the
     callback is CallbackInfoReturnable -> "Critical injection failure".
  2. NpcPursuitGraphUiMixin272#<shadow> selected() - NpcDialogueGraphScreen has
     no selected() method (it has a private int field `selected` and
     `node()` = graph.get(selected)) -> unresolved shadow, mixin apply error.
  3. NpcPursuitGraphUiMixin272#settledlands$pursuitCharTyped
     injects into charTyped(CI)V, which the screen does not declare (Screen's
     charTyped is (CI)Z and inherited methods are not injectable)
     -> target not found, injection failure.

Repair, bytecode level, no recompile of unrelated classes:
  * drop the focus handler          (canvas focus stays vanilla)
  * drop the charTyped handler      (typing into the node field needs a source
                                     build; see README-Build273)
  * retarget the shadow to node()   (same semantics: graph.get(selected))
    and rewrite the remaining call site in the keyPressed handler

Everything else in the jar is untouched: entries are copied byte for byte.

Usage: python3 tools/patch_build273.py <base-272.jar> [--out DIR]
"""

import argparse
import hashlib
import os
import pathlib
import re
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import classfile                                        # noqa: E402
from classfile import Reader, ClassFormatError           # noqa: E402

BASE_VERSION = "0.8.0-build272-pursuit-single-node"
NEW_VERSION = "0.8.0-build273-pursuit-mixinfix"

UI_MIXIN = "dev/settledlands/mixin/NpcPursuitGraphUiMixin272.class"
CANVAS_MIXIN = "dev/settledlands/mixin/NpcPursuitCanvasMixin272.class"
SCREEN = "dev/settledlands/npc/client/NpcDialogueGraphScreen"
NODE_DESC = "()Ldev/settledlands/npc/NpcDialogueGraph$Node;"

CHANGELOG_EN = (
    "Build 273: fixes the Build 272 startup crash. Three pursuit mixins were "
    "invalid - a CallbackInfoReturnable injected into the void NpcGraphCanvas#focus, "
    "a @Shadow for the non-existent NpcDialogueGraphScreen#selected(), and an "
    "@Inject into the non-existent charTyped(CI)V. The focus and charTyped handlers "
    "are removed and the shadow now resolves to node() (= graph.get(selected)); the "
    "pursuit card, dropdown, stop toggle, node creation and key handling keep working. "
    "Typing text into the pursuit field still needs a source build because the screen "
    "never declares charTyped. "
)
PATCHINFO = (
    "Сборка 273 исправляет краш Build272: три mixin-а преследования были невалидны "
    "(CallbackInfoReturnable в void-метод focus, @Shadow на несуществующий selected(), "
    "@Inject в несуществующий charTyped). Обработчики focus и charTyped удалены, "
    "shadow перенаправлен на node() = graph.get(selected). Карточка ноды, dropdown, "
    "тумблер и создание ноды работают; ввод текста в поле требует сборки из исходников. "
)


# ------------------------------------------------------------------ layout
def scan(data):
    """Absolute offsets of everything the patcher needs to rewrite."""
    r = Reader(data, "<scan>")
    r.u4()
    r.u2()
    r.u2()
    cp_count_off = r.p
    cp_count = r.u2()
    cp_start = r.p
    i = 1
    while i < cp_count:
        tag = r.u1()
        if tag == 1:
            r.bytes(r.u2())
        elif tag in (3, 4):
            r.skip(4)
        elif tag in (5, 6):
            r.skip(8)
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            r.skip(2)
        elif tag in (9, 10, 11, 12, 17, 18):
            r.skip(4)
        elif tag == 15:
            r.skip(3)
        else:
            raise SystemExit("unknown cp tag %d" % tag)
        i += 1
    cp_end = r.p
    info = {
        "cp_count": cp_count,
        "cp_count_off": cp_count_off,
        "cp_end": cp_end,
        "cp_start": cp_start,
    }
    r.u2()                      # access
    r.u2()
    r.u2()
    for _ in range(r.u2()):     # interfaces
        r.u2()

    def members(count):
        out = []
        for _ in range(count):
            start = r.p
            r.u2()
            name_idx = r.u2()
            desc_idx = r.u2()
            attrs = []
            for _ in range(r.u2()):
                an = r.u2()
                alen = r.u4()
                payload = r.p
                r.skip(alen)
                attrs.append({"name_idx": an, "start": payload, "len": alen,
                              "end": payload + alen})
            out.append({"start": start, "end": r.p, "name_idx": name_idx,
                        "desc_idx": desc_idx, "attrs": attrs})
        return out

    fields_count_off = r.p
    n_fields = r.u2()
    info["fields"] = members(n_fields)
    info["fields_count_off"] = fields_count_off
    info["methods_count_off"] = r.p
    n_methods = r.u2()
    info["methods"] = members(n_methods)
    info["methods_declared"] = n_methods
    return info


def utf8_of(data, count, idx):
    """Read cp entry bytes -> decoded Utf8 value (for tag 1)."""
    r = Reader(data, "<cp>")
    r.u4()
    r.u2()
    r.u2()
    n = r.u2()
    i = 1
    while i < n:
        tag = r.u1()
        if tag == 1:
            length = r.u2()
            raw = r.bytes(length)
            if i == idx:
                return raw.decode("utf-8", "replace")
        elif tag in (3, 4):
            r.skip(4)
        elif tag in (5, 6):
            r.skip(8)
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            r.skip(2)
        elif tag in (9, 10, 11, 12, 17, 18):
            r.skip(4)
        elif tag == 15:
            r.skip(3)
        i += 1
    return None


def cp_entry_bytes(tag, payload):
    return bytes([tag]) + payload


def u2b(v):
    return v.to_bytes(2, "big")


def utf8_entry(text):
    raw = text.encode("utf-8")
    return bytes([1]) + u2b(len(raw)) + raw


# ------------------------------------------------------------------ edits
class ClassEdit:
    """Byte-range edits applied to one class file, bottom-up."""

    def __init__(self, data, entry):
        self.orig = data
        self.entry = entry
        self.info = scan(data)
        self.cf = classfile.parse(data, entry)
        self.edits = []           # (start, end, replacement)
        self.cp_add = []          # list of raw entry bytes (tag included)
        self.cp_count = self.info["cp_count"]

    # -- constant pool ---------------------------------------------------
    def _find_utf8(self, text):
        for i in range(1, self.info["cp_count"]):
            if self.cf.cp[i] and self.cf.cp[i][0] == 1 and self.cf.cp[i][1] == text:
                return i
        return None

    def _find_entry(self, tag, payload_check):
        for i in range(1, self.info["cp_count"]):
            if self.cf.cp[i] and self.cf.cp[i][0] == tag and payload_check(i, self.cf.cp[i][1]):
                return i
        return None

    def utf8(self, text):
        idx = self._find_utf8(text)
        if idx:
            return idx
        self.cp_add.append(utf8_entry(text))
        return self.info["cp_count"] + len(self.cp_add) - 1

    def class_ref(self, internal_name):
        u = self.utf8(internal_name)

        def check(i, payload):
            return self.cf.cp[i][0] == 7 and payload == u
        idx = self._find_entry(7, check)
        if idx:
            return idx
        self.cp_add.append(cp_entry_bytes(7, u2b(u)))
        return self.info["cp_count"] + len(self.cp_add) - 1

    def name_and_type(self, name, desc):
        n = self.utf8(name)
        d = self.utf8(desc)

        def check(i, payload):
            return self.cf.cp[i][0] == 12 and payload == (n, d)
        idx = self._find_entry(12, check)
        if idx:
            return idx
        self.cp_add.append(cp_entry_bytes(12, u2b(n) + u2b(d)))
        return self.info["cp_count"] + len(self.cp_add) - 1

    def method_ref(self, cls, name, desc):
        c = self.class_ref(cls)
        nt = self.name_and_type(name, desc)

        def check(i, payload):
            return self.cf.cp[i][0] == 10 and payload == (c, nt)
        idx = self._find_entry(10, check)
        if idx:
            return idx
        self.cp_add.append(cp_entry_bytes(10, u2b(c) + u2b(nt)))
        return self.info["cp_count"] + len(self.cp_add) - 1

    # -- members ---------------------------------------------------------
    def method(self, name, desc=None):
        for m in self.info["methods"]:
            if utf8_of(self.orig, self.info["cp_count"], m["name_idx"]) != name:
                continue
            if desc and utf8_of(self.orig, self.info["cp_count"], m["desc_idx"]) != desc:
                continue
            return m
        return None

    def drop_method(self, name, desc=None):
        m = self.method(name, desc)
        if m is None:
            raise SystemExit("%s: method %s%s not found" % (self.entry, name, desc or ""))
        self.edits.append((m["start"], m["end"], b""))
        return m

    def rename_method(self, new_name, desc=None, old_name=None):
        m = self.method(old_name, desc)
        if m is None:
            raise SystemExit("%s: method %s not found" % (self.entry, old_name))
        idx = self.utf8(new_name)
        self.edits.append((m["start"] + 2, m["start"] + 4, u2b(idx)))
        return m

    def patch_code_operands(self, m, wanted_old_ref, new_ref):
        """Replace u2 cp operands that reference wanted_old_ref method."""
        code_attr = None
        for a in m["attrs"]:
            if utf8_of(self.orig, self.info["cp_count"], a["name_idx"]) == "Code":
                code_attr = a
                break
        if code_attr is None:
            raise SystemExit("%s: method %s has no Code" % (self.entry, m["name_idx"]))
        r = Reader(self.orig[code_attr["start"]:code_attr["end"]], "<code>")
        r.u2()
        r.u2()
        code = r.bytes(r.u4())
        hits = 0
        for off, op, _len in classfile.walk_code(code):
            if op not in (0xb6, 0xb7, 0xb8, 0xb9):
                continue
            idx = int.from_bytes(code[off + 1:off + 3], "big")
            if idx == wanted_old_ref:
                abs_off = code_attr["start"] + 8 + off + 1
                self.edits.append((abs_off, abs_off + 2, u2b(new_ref)))
                hits += 1
        return hits


def apply_edits(data, edits, info, cp_add, removed_methods=0):
    out = bytearray(data)
    for start, end, repl in sorted(edits, key=lambda e: -e[0]):
        out[start:end] = repl
    if cp_add:
        block = b"".join(cp_add)
        # the pool grows before every member, so the insertion point is the
        # original cp_end shifted only by edits that end before it (none: all
        # member edits live after the pool)
        at = _shift(info["cp_end"], edits, b"")
        out[at:at] = block
        new_count = info["cp_count"] + sum(
            2 if (len(e) >= 1 and e[0] in (5, 6)) else 1 for e in cp_add)
        out[info["cp_count_off"]:info["cp_count_off"] + 2] = u2b(new_count)
    if removed_methods:
        count_off = _shift(info["methods_count_off"], edits, cp_add)
        old = int.from_bytes(bytes(out[count_off:count_off + 2]), "big")
        out[count_off:count_off + 2] = u2b(old - removed_methods)
    return bytes(out)


def _shift(offset, edits, cp_add):
    """Map an original offset to its position after edits + cp insertion."""
    cp_len = len(b"".join(cp_add)) if cp_add else 0
    delta = 0
    for start, end, repl in sorted(edits, key=lambda e: e[0]):
        if end <= offset:
            delta += len(repl) - (end - start)
        elif start < offset:
            raise SystemExit("edit overlaps a structural offset - aborting")
    return offset + delta + cp_len


# ------------------------------------------------------------------ main
def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("base")
    ap.add_argument("--out", default=".")
    args = ap.parse_args(argv)

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    out_name = "settledlands-0.8.0-build273-pursuit-mixinfix.jar"
    out_path = outdir / out_name

    with zipfile.ZipFile(args.base) as z:
        entries = z.infolist()
        blobs = {i.filename: z.read(i.filename) for i in entries}

    # ---- 1. canvas mixin: drop the void-target focus handler ------------
    data = blobs[CANVAS_MIXIN]
    edit = ClassEdit(data, CANVAS_MIXIN)
    info = edit.info
    edit.drop_method("settledlands$pursuitFocus")
    fixed = apply_edits(data, edit.edits, info, edit.cp_add, removed_methods=1)
    blobs[CANVAS_MIXIN] = fixed
    print("  %s: removed settledlands$pursuitFocus" % CANVAS_MIXIN)

    # ---- 2. UI mixin: drop charTyped handler, retarget the shadow -------
    data = blobs[UI_MIXIN]
    edit = ClassEdit(data, UI_MIXIN)
    info = edit.info
    # the mixin calls its own shadow, so the owner is the mixin class itself
    old_selected_ref = None
    owner = None
    for i in range(1, info["cp_count"]):
        e = edit.cf.cp[i]
        if e and e[0] == 10:
            cls_i, nt_i = e[1]
            cls = edit.cf.class_name(cls_i)
            nt = edit.cf.cp[nt_i][1]
            if (edit.cf.utf8(nt[0]) == "selected"
                    and edit.cf.utf8(nt[1]) == NODE_DESC):
                old_selected_ref = i
                owner = cls
                break
    if old_selected_ref is None:
        raise SystemExit("could not find the selected() Methodref in %s" % UI_MIXIN)
    print("  shadow call site: cp#%d owner=%s" % (old_selected_ref, owner))
    new_node_ref = edit.method_ref(owner, "node", NODE_DESC)
    edit.drop_method("settledlands$pursuitCharTyped")
    edit.rename_method("node", desc=NODE_DESC, old_name="selected")
    patched = 0
    for m in edit.info["methods"]:
        name = utf8_of(edit.orig, info["cp_count"], m["name_idx"])
        if name in ("settledlands$pursuitCharTyped",):
            continue
        if m["start"] in [e[0] for e in edit.edits]:
            continue
        patched += edit.patch_code_operands(m, old_selected_ref, new_node_ref)
    fixed = apply_edits(data, edit.edits, info, edit.cp_add, removed_methods=1)
    blobs[UI_MIXIN] = fixed
    print("  %s: removed settledlands$pursuitCharTyped, shadow selected() -> node(), "
          "%d call site(s) retargeted" % (UI_MIXIN, patched))

    # ---- 3. metadata ----------------------------------------------------
    toml = blobs["META-INF/neoforge.mods.toml"].decode("utf-8")
    if 'version="%s"' % BASE_VERSION not in toml:
        raise SystemExit("base toml does not declare %s" % BASE_VERSION)
    toml = toml.replace('version="%s"' % BASE_VERSION, 'version="%s"' % NEW_VERSION)

    marker = "description='''"
    if marker not in toml:
        raise SystemExit("cannot find the description block in the toml")
    toml = toml.replace(marker, marker + CHANGELOG_EN + "\n\n", 1)
    blobs["META-INF/neoforge.mods.toml"] = toml.encode("utf-8")

    if "settledlands-patch-info.txt" in blobs:
        blobs["settledlands-patch-info.txt"] = (PATCHINFO + "\n").encode("utf-8")

    # ---- 4. write the jar deterministically -----------------------------
    order = [i.filename for i in entries]
    for extra in ("settledlands-patch-info.txt",):
        if extra in blobs and extra not in order:
            order.append(extra)
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in order:
            zi = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, blobs[name])

    digest = hashlib.sha256(out_path.read_bytes()).hexdigest()
    sums = outdir / (out_name + ".sha256sums.txt")
    sums.write_text("%s  %s\n" % (digest, out_name), encoding="utf-8")
    print("  wrote %s" % out_path)
    print("  wrote %s" % sums)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
