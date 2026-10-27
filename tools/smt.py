#!/usr/bin/env python3
"""Разбор/пересборка StackMapTable (инцидент 230/234).

Таблица разбирается строго по JVMS 4.7.4: append_frame (252..254) читает свои
verification_type_info, chop_frame (248..250) — только delta, full_frame (255) —
locals + stack. Прежний код путал порядок ветвей, «съедал» не то число байт, и
смещения фреймов уезжали — проверки проходили на мусоре.

Пересборка НЕ трогает типы: для каждого фрейма сохраняется исходный payload, и
меняется только кодировка delta (same_frame -> same_frame_extended и т.п.).
Так исключается искажение locals/stack — ровно там раньше рождались VerifyError.
"""
import struct


def vtype_len(b, p):
    tag = b[p]
    if tag in (7, 8):          # Object_variable_info / Uninitialized_variable_info
        return 3
    if 0 <= tag <= 6:
        return 1
    raise ValueError(f"неизвестный verification_type {tag} на {p}")


def parse(body):
    """-> список {kind, delta, offset, payload}; offset абсолютный, payload —
    байты после delta (или None, если их нет)."""
    if not body:
        return []
    cnt = struct.unpack(">H", body[0:2])[0]
    p = 2
    out = []
    prev = -1
    for _ in range(cnt):
        ft = body[p]
        p += 1
        payload = b""
        if ft <= 63:
            d = ft
        elif ft <= 127:
            d = ft - 64
            n = vtype_len(body, p)
            payload = bytes(body[p:p + n]); p += n
        elif ft == 247:
            d = struct.unpack(">H", body[p:p + 2])[0]; p += 2
            n = vtype_len(body, p)
            payload = bytes(body[p:p + n]); p += n
        elif 248 <= ft <= 251:
            d = struct.unpack(">H", body[p:p + 2])[0]; p += 2
        elif 252 <= ft <= 254:
            d = struct.unpack(">H", body[p:p + 2])[0]; p += 2
            start = p
            for _ in range(ft - 251):
                p += vtype_len(body, p)
            payload = bytes(body[start:p])
        elif ft == 255:
            d = struct.unpack(">H", body[p:p + 2])[0]; p += 2
            start = p
            nl = struct.unpack(">H", body[p:p + 2])[0]; p += 2
            for _ in range(nl):
                p += vtype_len(body, p)
            ns = struct.unpack(">H", body[p:p + 2])[0]; p += 2
            for _ in range(ns):
                p += vtype_len(body, p)
            payload = bytes(body[start:p])
        else:
            raise ValueError(f"недопустимый frame_type {ft}")
        off = d if prev < 0 else prev + d + 1
        prev = off
        out.append(dict(kind=ft, delta=d, offset=off, payload=payload))
    if p != len(body):
        raise ValueError(f"разбор не дошёл до конца: {p} из {len(body)}")
    return out


def build(frames):
    """Собрать таблицу заново; типы берутся из payload без изменений."""
    out = [struct.pack(">H", len(frames))]
    prev = -1
    for f in frames:
        kind = f["kind"]
        payload = f.get("payload", b"")
        d = f["offset"] if prev < 0 else f["offset"] - prev - 1
        if d < 0:
            raise ValueError(f"фреймы не по порядку: {f['offset']} после {prev}")
        if kind <= 63:                                  # same_frame
            out.append(bytes([d]) if d <= 63 else b"\xfb" + struct.pack(">H", d))
        elif kind <= 127:                               # same_locals_1_stack_item
            out.append((bytes([64 + d]) if d <= 63
                        else b"\xf7" + struct.pack(">H", d)) + payload)
        elif kind == 247:
            out.append(b"\xf7" + struct.pack(">H", d) + payload)
        elif 248 <= kind <= 251:
            out.append(bytes([kind]) + struct.pack(">H", d))
        elif 252 <= kind <= 254:
            out.append(bytes([kind]) + struct.pack(">H", d) + payload)
        elif kind == 255:
            out.append(b"\xff" + struct.pack(">H", d) + payload)
        else:
            raise ValueError(f"недопустимый frame_type {kind}")
        prev = f["offset"]
    return b"".join(out)


def shift(frames, at, n):
    """Сдвинуть фреймы: всё, что начинается с offset >= at, уезжает на n."""
    out = []
    for f in frames:
        g = dict(f)
        if f["offset"] >= at:
            g["offset"] = f["offset"] + n
        out.append(g)
    return out
