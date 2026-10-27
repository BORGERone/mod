#!/usr/bin/env python3
"""Build 230 «modelfix» — хотфикс рендера трупа БЕЗ компилятора (правка байткода).

Причина (диагностика 27.10): GuideRenderer берёт имя модели из
GuideNpc.texturePath() -> entityData(DATA_TEXTURE), НЕ из NBT. DATA_TEXTURE
выставляется только в GuideNpc.applyProfile() и ПОСЛЕДНЕЙ строкой метода, а
PlayerCorpsePossession.dressBodyCorpse вызывает applyProfile() внутри
`catch (Throwable ignored)`. Любое исключение выше по методу = пустой
DATA_TEXTURE = молчаливый fallback (встроенная GuideModel + guide_npc.png).

Фикс: в самое начало applyProfile() вставляются 12 байт, которые выставляют
DATA_TEXTURE из profile.texture ДО остальной работы метода:

    aload_0
    getfield      GuideNpc.entityData      (#248)
    getstatic     GuideNpc.DATA_TEXTURE    (#222)
    aload_1
    getfield      NpcProfile.texture       (#316)
    invokevirtual SynchedEntityData.set    (#252)

Никаких новых ветвлений => новых stack map frames не нужно; новая констант
в пул не добавляется; max_stack уже 5 (нужно 3). Сдвигаются только абсолютные
смещения: первая запись StackMapTable (offset_delta) и start_pc в
LineNumberTable — оба увеличиваются на длину вставки.

    python3 tools/patch_model_fix.py --base settledlands-0.8.0-build229-sablesweep.jar \
        --out settledlands-0.8.0-build230-modelfix.jar
"""
import argparse, struct, sys, zipfile, tomllib

TARGET_CLASS = "dev/settledlands/npc/GuideNpc.class"
TARGET_METHOD = "applyProfile"
FIELD_ENTITY_DATA = 248      # dev/settledlands/npc/GuideNpc.entityData
FIELD_DATA_TEXTURE = 222     # dev/settledlands/npc/GuideNpc.DATA_TEXTURE
FIELD_TEXTURE = 316          # dev/settledlands/npc/NpcProfile.texture
METHOD_SET = 252             # SynchedEntityData.set(EntityDataAccessor, Object)
NEW_VERSION = "0.8.0-build230-modelfix"
CHANGELOG = (
    "Build 230: corpse model fix - the renderer reads the model name from synched entity data, but "
    "applyProfile() wrote it as its LAST statement and the corpse dressing call swallows exceptions, so any "
    "failure above left the field empty and the corpse silently fell back to the built-in placeholder model. "
    "DATA_TEXTURE is now written at the START of applyProfile(), before anything else can throw."
)
PATCHINFO = (
    "сборка 230: починка модели трупа. GuideRenderer берёт имя модели из entityData(DATA_TEXTURE), а "
    "applyProfile() выставлял его последней строкой, да ещё внутри catch(Throwable ignored) в dressBodyCorpse — "
    "любое исключение выше оставляло поле пустым, и труп молча рисовался встроенной заглушкой (guide_npc.png). "
    "Теперь DATA_TEXTURE пишется в начале applyProfile(), до всего остального."
)


class Cursor:
    def __init__(self, data):
        self.d = data
        self.p = 0

    def u1(self):
        v = self.d[self.p]; self.p += 1; return v

    def u2(self):
        v = struct.unpack(">H", self.d[self.p:self.p + 2])[0]; self.p += 2; return v

    def u4(self):
        v = struct.unpack(">I", self.d[self.p:self.p + 4])[0]; self.p += 4; return v

    def take(self, n):
        v = self.d[self.p:self.p + n]; self.p += n; return v


def read_cp(raw):
    """Возвращает список записей: (tag, raw_bytes) с индекса 1; None — второй слот long/double."""
    c = Cursor(raw)
    c.take(8)
    cp_count = c.u2()
    entries = [None] * cp_count
    i = 1
    while i < cp_count:
        start = c.p
        tag = c.u1()
        if tag == 1:
            ln = c.u2(); c.take(ln)
        elif tag in (7, 8, 16, 19, 20):
            c.take(2)
        elif tag == 15:
            c.take(3)
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            c.take(4)
        elif tag in (5, 6):
            c.take(8)
            entries[i] = (tag, raw[start:c.p])
            i += 2
            continue
        else:
            raise SystemExit(f"неизвестный тег CP {tag} @ {i}")
        entries[i] = (tag, raw[start:c.p])
        i += 1
    return entries, cp_count, c.p


def utf8_of(entries, idx):
    tag, b = entries[idx]
    ln = struct.unpack(">H", b[1:3])[0]
    return b[3:3 + ln].decode("utf-8", "replace")


def patch_method(raw_attrs, insert_len):
    """Перепаковывает таблицу атрибутов метода: вставляет код, правит смещения."""
    c = Cursor(raw_attrs)
    n = c.u2()
    entries = []
    for _ in range(n):
        name_idx = c.u2()
        length = c.u4()
        entries.append((name_idx, c.take(length)))
    new_entries = []
    inserted = False
    for name_idx, body in entries:
        name = utf8_of(CP, name_idx)
        if name != "Code" or inserted:
            new_entries.append((name_idx, body))
            continue
        bc = Cursor(body)
        max_stack = bc.u2(); max_locals = bc.u2()
        code_len = bc.u4()
        code = bc.take(code_len)
        ins = (b"\x2a"
               + b"\xb4" + struct.pack(">H", FIELD_ENTITY_DATA)
               + b"\xb2" + struct.pack(">H", FIELD_DATA_TEXTURE)
               + b"\x2b"
               + b"\xb4" + struct.pack(">H", FIELD_TEXTURE)
               + b"\xb6" + struct.pack(">H", METHOD_SET))
        assert len(ins) == insert_len, len(ins)
        if max_stack < 3:
            raise SystemExit("max_stack слишком мал")
        code = ins + code
        etl = bc.u2()
        et = bytearray(struct.pack(">H", etl))
        for _ in range(etl):
            s0, e0, h0, ct = struct.unpack(">HHHH", bc.take(8))
            et += struct.pack(">HHHH", s0 + insert_len, e0 + insert_len, h0 + insert_len, ct)
        na = bc.u2()
        nested = []
        for _ in range(na):
            ai = bc.u2(); ln = bc.u4(); nested.append((ai, bc.take(ln)))
        new_nested = []
        for ai, ab in nested:
            an = utf8_of(CP, ai)
            if an == "LineNumberTable":
                lnn = struct.unpack(">H", ab[:2])[0]
                nb = bytearray(struct.pack(">H", lnn))
                for k in range(lnn):
                    spc, line = struct.unpack(">HH", ab[2 + k * 4: 6 + k * 4])
                    nb += struct.pack(">HH", spc + insert_len, line)
                ab = bytes(nb)
            elif an == "StackMapTable":
                nf = struct.unpack(">H", ab[:2])[0]
                if nf > 0:
                    ftype = ab[2]
                    if 0 <= ftype <= 63:
                        nd = ftype + insert_len
                        if nd <= 63:
                            ab = ab[:2] + bytes([nd]) + ab[3:]
                        else:
                            ab = ab[:2] + bytes([251]) + struct.pack(">H", nd) + ab[3:]
                    elif ftype == 251:
                        d0 = struct.unpack(">H", ab[3:5])[0]
                        ab = ab[:2] + bytes([251]) + struct.pack(">H", d0 + insert_len) + ab[5:]
                    elif 252 <= ftype <= 255:
                        d0 = struct.unpack(">H", ab[3:5])[0]
                        ab = ab[:2] + bytes([ftype]) + struct.pack(">H", d0 + insert_len) + ab[5:]
                    else:
                        raise SystemExit(f"первый фрейм типа {ftype} не поддержан")
            new_nested.append((ai, ab))
        nb = bytearray()
        nb += struct.pack(">H", max_stack) + struct.pack(">H", max_locals)
        nb += struct.pack(">I", len(code)) + code
        nb += et
        nb += struct.pack(">H", len(new_nested))
        for ai, ab in new_nested:
            nb += struct.pack(">H", ai) + struct.pack(">I", len(ab)) + ab
        new_entries.append((name_idx, bytes(nb)))
        inserted = True
    if not inserted:
        raise SystemExit("метод applyProfile без Code")
    blob = bytearray(struct.pack(">H", len(new_entries)))
    for name_idx, body in new_entries:
        blob += struct.pack(">H", name_idx) + struct.pack(">I", len(body)) + body
    return bytes(blob)


def bump_toml(raw):
    text = raw.decode("utf-8")
    if f'version="{NEW_VERSION}"' in text:
        return raw
    text = text.replace('version="0.8.0-build229-sablesweep"', f'version="{NEW_VERSION}"', 1)
    if f'version="{NEW_VERSION}"' not in text:
        raise SystemExit("в toml не найдена версия 229")
    marker = "description='''\n"
    if marker not in text:
        raise SystemExit("нет description")
    text = text.replace(marker, marker + CHANGELOG + "\n", 1)
    out = text.encode("utf-8")
    tomllib.loads(out.decode("utf-8"))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    src = zipfile.ZipFile(a.base)
    class_bytes = src.read(TARGET_CLASS)

    global CP
    CP, cp_count, cp_end = read_cp(class_bytes)

    c = Cursor(class_bytes)
    c.p = cp_end
    access = c.take(2); this_cls = c.take(2); super_cls = c.take(2)
    ifc = c.u2(); ifaces = c.take(2 * ifc)

    def read_members(cur):
        cnt = cur.u2()
        items = []
        for _ in range(cnt):
            head = cur.take(8)          # access, name, desc, attrs_count
            an = struct.unpack(">H", head[6:8])[0]
            attrs_start = cur.p
            for _ in range(an):
                cur.take(2); ln = cur.u4(); cur.take(ln)
            items.append((head[:6], struct.pack(">H", an) + class_bytes[attrs_start:cur.p]))
        return items

    fields = read_members(c)
    methods = read_members(c)
    class_attrs = class_bytes[c.p:]

    # пересборка методов
    new_methods = []
    patched = 0
    for head, attrs in methods:
        name_idx = struct.unpack(">H", head[2:4])[0]
        if utf8_of(CP, name_idx) == TARGET_METHOD and patched == 0:
            attrs = patch_method(attrs, 14)
            patched += 1
        new_methods.append((head, attrs))
    if patched != 1:
        raise SystemExit("applyProfile не найден однозначно")
    # имя метода обязано остаться прежним — проверим ещё раз по индексу
    out = bytearray()
    out += class_bytes[:cp_end]
    out += access + this_cls + super_cls + ifc.to_bytes(2, "big") + ifaces
    out += struct.pack(">H", len(fields))
    for head, attrs in fields:
        out += head + attrs
    out += struct.pack(">H", len(new_methods))
    for head, attrs in new_methods:
        out += head + attrs
    out += class_attrs

    # запись jar
    zout = zipfile.ZipFile(a.out, "w")
    changed = []
    for info in src.infolist():
        data = src.read(info.filename)
        if info.filename == TARGET_CLASS:
            data = bytes(out)
            changed.append(info.filename)
        elif info.filename == "META-INF/neoforge.mods.toml":
            data = bump_toml(data)
            changed.append(info.filename)
        elif info.filename == "settledlands-patch-info.txt":
            data = data.rstrip(b"\n") + b"\n" + PATCHINFO.encode("utf-8") + b"\n"
            changed.append(info.filename)
        zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
        zi.compress_type = info.compress_type
        zi.external_attr = info.external_attr
        zi.create_system = info.create_system
        zout.writestr(zi, data)
    zout.close()
    print("вставлено байт в applyProfile:", 14)
    print("изменено:", ", ".join(changed))
    print("готово:", a.out)


if __name__ == "__main__":
    sys.exit(main())
