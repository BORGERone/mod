#!/usr/bin/env python3
"""Gate 3/4 - mixin class verification (config + target resolution).

Phase A - configuration
  * every mixin config parses, has package + compatibilityLevel JAVA_21
  * every listed class exists in the jar and carries @Mixin
  * no class listed twice / in both the common and client lists
  * bundled mixin annotation stubs keep @Retention(CLASS)

Phase C - dispatcher hooks
  A class that only exists to be called from a mixin (the pursuit card
  renderer) is useless when the mixin no longer references it: Build 277 lost
  the drawNode @Inject and every pursuit node fell back to the stock trigger
  drawing. If such a class is in the jar, some mixin in the configs must name
  the hook method.

Phase B - target resolution (catches "Critical injection failure" crashes)
  Targets that live inside the jar (the mod's own classes) are resolved for
  real:
  * @Mixin target classes exist
  * @Inject / @Redirect / @ModifyConstant target methods exist with the exact
    name and descriptor written in the annotation
  * the callback's last parameter matches the target: CallbackInfo for a void
    target, CallbackInfoReturnable for a non-void one (build 257-258 lesson)
  * callback parameter count = target parameters + callback
  * @ModifyConstant(@Constant(intValue = N)) finds N in the target method's
    bytecode (injectors.defaultRequire=1 turns a miss into a hard crash)
  * @Shadow fields and methods exist in the target with the same descriptor
  * @Overwrite matches an existing method
  * an @Inject handler that calls CallbackInfo.cancel() or
    CallbackInfoReturnable.setReturnValue(...) but whose annotation is not
    `cancellable = true` -> CancellationException at runtime
    ("The call <method> is not cancellable")
  Targets from Minecraft or other mods cannot be resolved here - they are
  listed as "external" instead of being guessed at.

Usage: python3 tools/check_mixin_class.py <jar> [--verbose]
Exit code 0 = pass, 1 = fail.
"""

import argparse
import json
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import classfile                                        # noqa: E402
from classfile import ClassFormatError, Reader          # noqa: E402

ACC_ANNOTATION = 0x2000
ANNOTATION_ATTRS = ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations")

PKG = "Lorg/spongepowered/asm/mixin/"
DESC_MIXIN = PKG + "Mixin;"
DESC_INJECT = PKG + "injection/Inject;"
DESC_REDIRECT = PKG + "injection/Redirect;"
DESC_MODIFY_CONST = PKG + "injection/ModifyConstant;"
DESC_MODIFY_ARG = PKG + "injection/ModifyArg;"
DESC_MODIFY_VAR = PKG + "injection/ModifyVariable;"
DESC_OVERWRITE = PKG + "Overwrite;"
DESC_SHADOW = PKG + "Shadow;"
DESC_CONSTANT = PKG + "injection/Constant;"
DESC_AT = PKG + "injection/At;"
DESC_RETENTION = "Ljava/lang/annotation/Retention;"
# class -> method that some mixin must call, else the class is dead code
DISPATCH_HOOKS = {
    "dev/settledlands/npc/client/PursuitGraphNode272": ("drawNode", "the pursuit card"),
}

DESC_CALLBACK_INFO = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;"
DESC_CALLBACK_RET = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;"


class Fail(Exception):
    pass


# ---------------------------------------------------------------- annotations
def _elem_value(r):
    tag = r.u1()
    if tag in (0x42, 0x43, 0x44, 0x46, 0x48, 0x49, 0x4a, 0x53, 0x5a):
        return ("const", r.u2())
    if tag == 0x65:
        return ("enum", r.u2(), r.u2())
    if tag in (0x73, 0x63):
        return (tag, r.u2())
    if tag == 0x40:
        return _parse_annotation(r)
    if tag == 0x5b:
        return ("array", [_elem_value(r) for _ in range(r.u2())])
    raise ClassFormatError("bad element_value tag 0x%02x" % tag)


def _parse_annotation(r):
    type_idx = r.u2()
    pairs = []
    for _ in range(r.u2()):
        name_idx = r.u2()
        pairs.append((name_idx, _elem_value(r)))
    return ("annotation", type_idx, pairs)


def read_annotations(cf, attr_names=ANNOTATION_ATTRS):
    out = []
    for a in cf.attrs:
        if a.name not in attr_names:
            continue
        r = Reader(a.data, "%s:%s" % (cf.name, a.name))
        for _ in range(r.u2()):
            out.append(_parse_annotation(r))
    return out


def member_annotations(cf, member, attr_names=ANNOTATION_ATTRS):
    out = []
    for a in member.attrs:
        if a.name not in attr_names:
            continue
        r = Reader(a.data, "%s.%s:%s" % (cf.name, member.name, a.name))
        for _ in range(r.u2()):
            out.append(_parse_annotation(r))
    return out


def ann_desc(cf, ann):
    try:
        return cf.utf8(ann[1])
    except ClassFormatError:
        return ""


def ann_args(cf, ann):
    """{element name: value} with names resolved to strings."""
    out = {}
    for name_idx, val in ann[2]:
        try:
            name = cf.utf8(name_idx)
        except ClassFormatError:
            name = "<bad#%d>" % name_idx
        out[name] = val
    return out


def as_str(cf, val):
    if val is None:
        return None
    if val[0] in (0x73, 0x63):
        try:
            return cf.utf8(val[1])
        except ClassFormatError:
            return None
    if val[0] == "const":                      # boolean / int element encoded as raw index
        return None
    return None


def as_str_list(cf, val):
    if val is None:
        return []
    if val[0] == "array":
        return [s for s in (as_str(cf, v) for v in val[1]) if s]
    single = as_str(cf, val)
    return [single] if single else []


def as_int(cf, val):
    if val is None:
        return None
    if val[0] == "const":
        try:
            tag = cf.tag(val[1])
        except ClassFormatError:
            return None
        raw = cf.cp[val[1]][1]
        if tag == 3:
            return raw - (1 << 32) if raw >= (1 << 31) else raw
        return None
    return None


def as_bool(cf, val, default=False):
    v = as_int(cf, val)
    return default if v is None else bool(v)


def annotation_args_by_desc(cf, member, want_desc, members_of_class=False):
    anns = read_annotations(cf) if members_of_class else member_annotations(cf, member)
    for a in anns:
        if ann_desc(cf, a) == want_desc:
            return ann_args(cf, a)
    return None


# ---------------------------------------------------------------- descriptors
def split_method_desc(desc):
    """'(IJLjava/lang/String;)Z' -> (['I','J','Ljava/lang/String;'], 'Z')"""
    if not desc or not desc.startswith("("):
        return None, None
    depth = 0
    end = -1
    for i, ch in enumerate(desc):
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                end = i
                break
    if end < 0:
        return None, None
    args, i = [], 1
    while i < end:
        j = i
        while desc[j] == "[":
            j += 1
        if desc[j] == "L":
            j = desc.index(";", j) + 1
        else:
            j += 1
        args.append(desc[i:j])
        i = j
    return args, desc[end + 1:]


def parse_method_spec(spec):
    """'name(desc)ret' / 'name(desc)' / 'name' -> (name, [full_desc or None])"""
    if not spec:
        return None, []
    if "(" not in spec:
        return spec, []
    name = spec[:spec.index("(")]
    inner = spec[spec.index("(") + 1:spec.index(")")]
    ret = spec[spec.index(")") + 1:]
    return name, ["(%s)%s" % (inner, ret or "?")]


def return_of_desc(desc):
    if desc and desc.endswith(")V"):
        return "V"
    return None if not desc else desc[desc.rindex(")") + 1:]


def int_constants_in(code):
    """Set of integer literals pushed by a method's bytecode."""
    found = set()
    for off, op, _len in classfile.walk_code(code):
        if 0x02 <= op <= 0x08:
            found.add(op - 0x03)             # iconst_m1..iconst_5
        elif op == 0x10:
            v = code[off + 1]
            found.add(v - 256 if v > 127 else v)
        elif op == 0x11:
            v = int.from_bytes(code[off + 1:off + 3], "big")
            found.add(v - 65536 if v > 32767 else v)
        elif op in (0x12, 0x13):
            idx = code[off + 1] if op == 0x12 else int.from_bytes(
                code[off + 1:off + 3], "big")
            if 0 < idx < 0x10000:
                found.add(("cpidx", idx))
    return found


def resolve_int_constants(cf, raw):
    out = set()
    for item in raw:
        if isinstance(item, tuple):
            idx = item[1]
            try:
                if cf.tag(idx) == 3:
                    v = cf.cp[idx][1]
                    out.add(v - (1 << 32) if v >= (1 << 31) else v)
            except ClassFormatError:
                pass
        else:
            out.add(item)
    return out


# ---------------------------------------------------------------- jar helpers
def load_classes(z, names):
    out = {}
    for n in names:
        if not n.endswith(".class"):
            continue
        try:
            out[n[:-6]] = classfile.parse(z.read(n), n)
        except ClassFormatError:
            pass
    return out


def find_method(cf, name, descs):
    for m in cf.methods:
        if m.name != name:
            continue
        if not descs or any(m.desc == d for d in descs):
            return m
    return None


def find_field(cf, name):
    for f in cf.fields:
        if f.name == name:
            return f
    return None


def has_method_named(cf, name):
    return any(m.name == name for m in cf.methods)


# ---------------------------------------------------------------- phase B
def check_targets(cf, entry, classes, problems, external, verbose):
    mixin_ann = None
    for a in read_annotations(cf):
        if ann_desc(cf, a) == DESC_MIXIN:
            mixin_ann = a
            break
    if mixin_ann is None:
        return
    args = ann_args(cf, mixin_ann)
    targets = []
    for val in (args.get("targets"), args.get("value")):
        for s in as_str_list(cf, val):
            if s.startswith("L") and s.endswith(";"):
                targets.append(s[1:-1])
            else:
                targets.append(s.replace(".", "/"))
    if not targets:
        return

    resolved = []
    for t in targets:
        target_cf = classes.get(t)
        if target_cf is None:
            external.append("%s -> %s" % (entry, t))
        else:
            resolved.append((t, target_cf))

    # @Shadow members must exist in a resolved target
    for f in cf.fields:
        if annotation_args_by_desc(cf, f, DESC_SHADOW) is None:
            continue
        if not resolved:
            continue
        for tname, tcf in resolved:
            tf = find_field(tcf, f.name)
            if tf is None:
                problems.append("%s: @Shadow field %s does not exist in %s"
                                % (entry, f.name, tname))
            elif tf.desc != f.desc:
                problems.append("%s: @Shadow field %s type %s != %s in %s"
                                % (entry, f.name, f.desc, tf.desc, tname))

    for m in cf.methods:
        shadows = annotation_args_by_desc(cf, m, DESC_SHADOW)
        if shadows is not None:
            if resolved:
                for tname, tcf in resolved:
                    tm = find_method(tcf, m.name, [m.desc])
                    if tm is None:
                        if has_method_named(tcf, m.name):
                            problems.append("%s: @Shadow %s%s - target %s has the "
                                            "name with a different descriptor"
                                            % (entry, m.name, m.desc, tname))
                        else:
                            problems.append("%s: @Shadow method %s%s does not exist "
                                            "in %s" % (entry, m.name, m.desc, tname))
            continue

        for desc_kind, want in ((DESC_INJECT, "inject"),
                                (DESC_REDIRECT, "redirect"),
                                (DESC_MODIFY_CONST, "modifyconstant"),
                                (DESC_MODIFY_ARG, "modifyarg"),
                                (DESC_MODIFY_VAR, "modifyvariable"),
                                (DESC_OVERWRITE, "overwrite")):
            a = annotation_args_by_desc(cf, m, desc_kind)
            if a is None:
                continue
            specs = as_str_list(cf, a.get("method"))
            if want == "overwrite":
                specs = ["%s%s" % (m.name, m.desc)]
            if want == "inject":
                check_cancellable(cf, m, a, entry, problems)
            if not specs:
                problems.append("%s: %s%s has a %s annotation without a method "
                                "target" % (entry, m.name, m.desc, desc_kind))
                continue
            if not resolved:
                continue
            for spec in specs:
                name, descs = parse_method_spec(spec)
                if not name:
                    continue
                if descs and descs[0].endswith("?"):
                    descs = []
                found = None
                for tname, tcf in resolved:
                    tm = find_method(tcf, name, descs)
                    if tm is not None:
                        found = (tname, tcf, tm)
                        break
                    if not descs and has_method_named(tcf, name):
                        for cand in tcf.methods:
                            if cand.name == name:
                                found = (tname, tcf, cand)
                                break
                        if found:
                            break
                if found is None:
                    names_hint = ", ".join(t for t, _ in resolved)
                    if descs:
                        problems.append("%s: %s targets %s%s which does not exist "
                                        "in %s" % (entry, m.name, name, descs[0],
                                                   names_hint))
                    else:
                        problems.append("%s: %s targets %s() which does not exist "
                                        "in %s" % (entry, m.name, name, names_hint))
                    continue
                tname, tcf, tm = found
                if want == "inject":
                    # @Redirect / @ModifyArg / @ModifyVariable handlers have a
                    # different signature rule (args + target return value, no
                    # CallbackInfo), so only @Inject is checked here.
                    check_callback(cf, m, tm, tname, entry, want, problems)
                if want == "modifyconstant":
                    check_constant(cf, m, tm, tname, entry, a, problems)
                if verbose:
                    print("    ok  %s.%s -> %s#%s%s" % (entry, m.name, tname,
                                                        tm.name, tm.desc))


def check_callback(cf, m, tm, tname, entry, kind, problems):
    margs, mret = split_method_desc(m.desc)
    targs, tret = split_method_desc(tm.desc)
    if margs is None or targs is None:
        return
    if not margs:
        return
    last = margs[-1]
    if last not in (DESC_CALLBACK_INFO, DESC_CALLBACK_RET):
        problems.append("%s: %s%s - last parameter must be CallbackInfo or "
                        "CallbackInfoReturnable, got %s"
                        % (entry, m.name, m.desc, last))
        return
    if tret == "V" and last != DESC_CALLBACK_INFO:
        problems.append("%s: %s%s injects into %s#%s which returns void - the "
                        "callback must be CallbackInfo, not "
                        "CallbackInfoReturnable (mixin apply failure)"
                        % (entry, m.name, m.desc, tname, tm.name))
    if tret != "V" and last == DESC_CALLBACK_INFO:
        problems.append("%s: %s%s injects into %s#%s which returns %s - the "
                        "callback must be CallbackInfoReturnable<%s>"
                        % (entry, m.name, m.desc, tname, tm.name, tret, tret))
    expected = len(targs) + 1
    if len(margs) != expected:
        problems.append("%s: %s%s has %d parameters, %s#%s%s + callback needs %d"
                        % (entry, m.name, m.desc, len(margs), tname, tm.name,
                           tm.desc, expected))
    if kind == "inject":
        return


DESC_CB_INFO = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;"
DESC_CB_RET = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;"


def _invoked_returns(cf, m):
    """True when the handler calls cancel() or setReturnValue(...)."""
    code_attr = m.attr("Code")
    if code_attr is None:
        return False
    r = Reader(code_attr.data, "%s.%s:Code" % (cf.name, m.name))
    r.u2()
    r.u2()
    code = r.bytes(r.u4())
    for off, op, _len in classfile.walk_code(code):
        if op not in (0xb6, 0xb9):
            continue
        idx = int.from_bytes(code[off + 1:off + 3], "big")
        if not 0 < idx < len(cf.cp) or cf.cp[idx] is None or cf.cp[idx][0] != 10:
            continue
        cls_i, nt_i = cf.cp[idx][1]
        try:
            owner = cf.class_name(cls_i)
            name, desc = cf.utf8(cf.cp[nt_i][1][0]), cf.utf8(cf.cp[nt_i][1][1])
        except ClassFormatError:
            continue
        if owner.startswith("org/spongepowered/asm/mixin/injection/callback/"):
            if name in ("cancel", "setReturnValue"):
                return True
    return False


def check_cancellable(cf, m, args, entry, problems):
    """A handler that cancels must be declared cancellable = true."""
    if as_bool(cf, args.get("cancellable"), False):
        return
    if _invoked_returns(cf, m):
        problems.append(
            "%s: %s%s calls cancel()/setReturnValue() but its @Inject is not "
            "cancellable = true -> CancellationException \"The call is not "
            "cancellable\" at runtime" % (entry, m.name, m.desc))


def check_constant(cf, m, tm, tname, entry, args, problems):
    const_ann = args.get("constant")
    want = []
    if const_ann and const_ann[0] == "annotation":
        cargs = {}
        for name_idx, val in const_ann[2]:
            try:
                cargs[cf.utf8(name_idx)] = val
            except ClassFormatError:
                pass
        if "intValue" in cargs:
            want.append(as_int(cf, cargs["intValue"]))
        if "stringValue" in cargs:
            want.append(("string", as_str(cf, cargs["stringValue"])))
    if not want:
        return
    code_attr = tm.attr("Code")
    if code_attr is None:
        problems.append("%s: %s targets %s#%s which has no code"
                        % (entry, m.name, tname, tm.name))
        return
    r = Reader(code_attr.data, "%s#%s" % (tname, tm.name))
    r.u2()
    r.u2()
    code = r.bytes(r.u4())
    present = resolve_int_constants(cf, int_constants_in(code))
    for w in want:
        if isinstance(w, tuple):
            continue
        if w not in present:
            problems.append("%s: %s() @ModifyConstant wants int %d but %s#%s does "
                            "not contain it (defaultRequire=1 -> critical "
                            "injection failure)" % (entry, m.name, w, tname, tm.name))


# ---------------------------------------------------------------- phase A
def check_stub_retention(cf, entry):
    if not cf.access & ACC_ANNOTATION:
        return None
    anns = read_annotations(cf)
    descs = [ann_desc(cf, a) for a in anns]
    if DESC_RETENTION not in descs:
        return ("%s: bundled annotation stub has no @Retention - Mixin resolves "
                "annotations from the class file, keep @Retention(CLASS) as in "
                "the real library (build 267-268 lesson)" % entry)
    for a in anns:
        if ann_desc(cf, a) != DESC_RETENTION:
            continue
        for name, val in ann_args(cf, a).items():
            if name != "value" or val[0] != "enum":
                continue
            try:
                policy = cf.utf8(val[2])
            except ClassFormatError:
                policy = "?"
            if policy != "CLASS":
                return ("%s: @Retention(%s) - the real mixin library uses CLASS "
                        "here (build 267-268 lesson)" % (entry, policy))
    return None


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("jar")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args(argv)
    jar = args.jar
    if not os.path.exists(jar):
        print("check_mixin_class: FAIL - no such file: %s" % jar)
        return 1

    problems = []
    external = []
    listed_mixins = []
    checked = 0
    try:
        with zipfile.ZipFile(jar) as z:
            names = z.namelist()
            nameset = set(names)
            classes = load_classes(z, names)
            configs = sorted(n for n in names
                             if n.endswith(".mixins.json")
                             or (n.startswith("META-INF/") and n.endswith("mixins.json")))
            if not configs:
                print("check_mixin_class: OK  (no mixin configs in jar)")
                return 0
            for cfg in configs:
                try:
                    doc = json.loads(z.read(cfg).decode("utf-8"))
                except Exception as exc:
                    problems.append("%s: invalid JSON (%s)" % (cfg, exc))
                    continue
                pkg = doc.get("package")
                if not pkg:
                    problems.append("%s: no 'package'" % cfg)
                    continue
                level = doc.get("compatibilityLevel")
                if not level:
                    problems.append("%s: no compatibilityLevel (defaults to "
                                    "JAVA_6, cannot read Java 21 classes)" % cfg)
                elif level != "JAVA_21":
                    problems.append("%s: compatibilityLevel=%r, expected JAVA_21 "
                                    "for MC 1.21.1" % (cfg, level))
                seen = {}
                for group in ("mixins", "client", "server"):
                    for name in doc.get(group) or []:
                        entry = "%s/%s.class" % (pkg.replace(".", "/"),
                                                 name.replace(".", "/"))
                        if entry in seen:
                            problems.append("%s: %s listed in both %s and %s"
                                            % (cfg, name, seen[entry], group))
                            continue
                        seen[entry] = group
                        if entry not in nameset:
                            problems.append("%s: class %s is missing from the jar"
                                            % (cfg, entry))
                            continue
                        cf = classes.get(entry[:-6])
                        if cf is None:
                            problems.append("%s: %s cannot be parsed" % (cfg, entry))
                            continue
                        listed_mixins.append(entry)
                        if not any(ann_desc(cf, a) == DESC_MIXIN
                                   for a in read_annotations(cf)):
                            problems.append("%s: %s carries no @Mixin annotation "
                                            "in the class file" % (cfg, entry))
                            continue
                        checked += 1
                        check_targets(cf, entry, classes, problems, external,
                                      args.verbose)

            for owner, (hook, what) in DISPATCH_HOOKS.items():
                if owner not in classes:
                    continue
                short = owner.rsplit("/", 1)[-1]
                hooked = False
                for entry in listed_mixins:
                    mcf = classes.get(entry[:-6])
                    if mcf is None:
                        continue
                    try:
                        strings = {mcf.utf8(i) for i in range(1, len(mcf.cp))
                                   if mcf.tag(i) == 1}
                    except ClassFormatError:
                        continue
                    named = owner in strings or short in strings or any(
                        v.endswith("/" + short + ";") or v == "L" + owner + ";"
                        for v in strings)
                    if named and hook in strings:
                        hooked = True
                        break
                if not hooked:
                    problems.append("%s#%s is in the jar but no mixin in the "
                                    "configs calls it - %s would be drawn by "
                                    "the stock code (dead hook)" % (owner, hook, what))

            for entry in sorted(n for n in names
                                if n.startswith("org/spongepowered/asm")
                                and n.endswith(".class")):
                cf = classes.get(entry[:-6])
                if cf is None:
                    continue
                msg = check_stub_retention(cf, entry)
                if msg:
                    problems.append(msg)
    except zipfile.BadZipFile as exc:
        print("check_mixin_class: FAIL - not a readable jar: %s" % exc)
        return 1

    if external:
        uniq = sorted({e.split(" -> ")[1] for e in external})
        print("  external targets (Minecraft / other mods, not verifiable here): "
              "%d" % len(uniq))
        for u in uniq[:10]:
            print("    - %s" % u)

    if problems:
        print("check_mixin_class: FAIL")
        for p in problems:
            print("  - %s" % p)
        return 1
    print("check_mixin_class: OK  %s" % os.path.basename(jar))
    print("  mixin classes verified : %d" % checked)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
