"""Dependency-free JVM class-file reader/walker for the SettledLands build gates.

Standard library only: the gates must run without a JDK, because the workspace
agent normally has python3 but no `java`.

Used by jvm_verify.py and check_mixin_class.py.
"""

MAGIC = 0xCAFEBABE

CP_TAGS = {
    1: "Utf8", 3: "Integer", 4: "Float", 5: "Long", 6: "Double", 7: "Class",
    8: "String", 9: "Fieldref", 10: "Methodref", 11: "InterfaceMethodref",
    12: "NameAndType", 15: "MethodHandle", 16: "MethodType", 17: "Dynamic",
    18: "InvokeDynamic", 19: "Module", 20: "Package",
}

# operand byte counts, -1 == variable length (tableswitch/lookupswitch/wide)
OPLEN = {}
for _op in list(range(0x00, 0x10)) + list(range(0x1a, 0x36)) + \
        list(range(0x3b, 0x84)) + list(range(0x85, 0x99)) + \
        list(range(0xac, 0xb2)) + [0xbe, 0xbf, 0xc2, 0xc3]:
    OPLEN[_op] = 0
for _op in (0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38,
            0x39, 0x3a, 0xa9, 0xbc):
    OPLEN[_op] = 1
for _op in (0x11, 0x13, 0x14, 0x84, 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e,
            0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8,
            0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xbb, 0xbd, 0xc0,
            0xc1, 0xc6, 0xc7):
    OPLEN[_op] = 2
for _op in (0xc5,):
    OPLEN[_op] = 3
for _op in (0xb9, 0xba, 0xc8, 0xc9):
    OPLEN[_op] = 4
for _op in (0xaa, 0xab, 0xc4):
    OPLEN[_op] = -1

# opcodes whose u2 operand is a constant-pool index
CP_U2 = {
    0x12: (3, 4, 7, 8, 15, 16, 17),          # ldc (1 byte, uses nibble of this set)
    0x13: (3, 4, 7, 8, 15, 16, 17),          # ldc_w
    0x14: (5, 6, 17),                        # ldc2_w
    0xb2: (9,), 0xb3: (9,), 0xb4: (9,), 0xb5: (9,),    # get/put static/field
    0xb6: (10,), 0xb7: (10, 11),                         # invokevirtual / invokespecial
    0xb8: (10, 11),                                      # invokestatic (interfaces since JVMS 8)
    0xb9: (11,),                                         # invokeinterface
    0xba: (18,),                                         # invokedynamic
    0xbb: (7,), 0xbd: (7,), 0xc0: (7,), 0xc1: (7,), 0xc5: (7,),  # new/anewarray/checkcast/instanceof/multianewarray
}


class ClassFormatError(Exception):
    pass


class Reader:
    def __init__(self, data, name="<class>"):
        self.b = data
        self.p = 0
        self.name = name

    def _need(self, n):
        if self.p + n > len(self.b):
            raise ClassFormatError(
                "%s: truncated at offset %d (need %d byte(s), %d left)"
                % (self.name, self.p, n, len(self.b) - self.p))

    def u1(self):
        self._need(1)
        v = self.b[self.p]
        self.p += 1
        return v

    def u2(self):
        self._need(2)
        v = (self.b[self.p] << 8) | self.b[self.p + 1]
        self.p += 2
        return v

    def u4(self):
        self._need(4)
        v = int.from_bytes(self.b[self.p:self.p + 4], "big")
        self.p += 4
        return v

    def bytes(self, n):
        self._need(n)
        v = self.b[self.p:self.p + n]
        self.p += n
        return v

    def skip(self, n):
        self._need(n)
        self.p += n

    def at_end(self):
        return self.p == len(self.b)


class Attr:
    __slots__ = ("name", "data", "offset")

    def __init__(self, name, data, offset):
        self.name = name
        self.data = data
        self.offset = offset

    def __repr__(self):
        return "<Attr %s %dB>" % (self.name, len(self.data))


class Member:
    __slots__ = ("access", "name", "desc", "attrs", "is_method")

    def __init__(self, access, name, desc, attrs, is_method):
        self.access = access
        self.name = name
        self.desc = desc
        self.attrs = attrs
        self.is_method = is_method

    def attr(self, n):
        for a in self.attrs:
            if a.name == n:
                return a
        return None


class ClassFile:
    def __init__(self):
        self.minor = 0
        self.major = 0
        self.cp = [None]
        self.access = 0
        self.this_class = 0
        self.super_class = 0
        self.interfaces = []
        self.fields = []
        self.methods = []
        self.attrs = []
        self.name = ""          # internal name, e.g. dev/settledlands/Foo
        self.class_attrs = {}

    # ---- constant pool helpers -------------------------------------------
    def tag(self, idx):
        if not 1 <= idx < len(self.cp) or self.cp[idx] is None:
            raise ClassFormatError("%s: constant-pool index %d out of range (size %d)"
                                   % (self.name, idx, len(self.cp)))
        return self.cp[idx][0]

    def utf8(self, idx):
        if self.tag(idx) != 1:
            raise ClassFormatError("%s: cp#%d is %s, expected Utf8"
                                   % (self.name, idx, CP_TAGS.get(self.tag(idx), "?")))
        return self.cp[idx][1]

    def class_name(self, idx):
        if idx == 0:
            return None
        if self.tag(idx) != 7:
            raise ClassFormatError("%s: cp#%d is %s, expected Class"
                                   % (self.name, idx, CP_TAGS.get(self.tag(idx), "?")))
        return self.utf8(self.cp[idx][1])

    def annotations(self, attr_names=("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations")):
        """Flattened list of annotation type descriptors on the class itself."""
        out = []
        for a in self.attrs:
            if a.name not in attr_names:
                continue
            r = Reader(a.data, "%s:%s" % (self.name, a.name))
            for _ in range(r.u2()):
                out.append(r.u2())     # annotation type_index (Utf8 descriptor)
                _skip_element_pairs(r)
        return out

    def annotation_descriptors(self, attr_names=("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations")):
        out = []
        for idx in self.annotations(attr_names):
            try:
                out.append(self.utf8(idx))
            except ClassFormatError:
                pass
        return out

    def retention(self):
        """Return 'RUNTIME'/'CLASS'/'SOURCE' for annotation types, else None."""
        for a in self.attrs:
            if a.name not in ("RuntimeVisibleAnnotations",):
                continue
            r = Reader(a.data, "%s:RuntimeVisibleAnnotations" % self.name)
            for _ in range(r.u2()):
                tidx = r.u2()
                try:
                    desc = self.utf8(tidx)
                except ClassFormatError:
                    desc = ""
                if desc != "Ljava/lang/annotation/Retention;":
                    _skip_element_pairs(r)
                    continue
                for _pair in range(r.u2()):
                    _name_idx = r.u2()
                    ev = r.u1()
                    if ev in (0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x4b, 0x51, 0x52, 0x53, 0x54):
                        # primitive/string constant: e -> +u2
                        if ev == 0x73:
                            r.u2()
                        else:
                            r.u1()
                    elif ev == 0x65:      # enum: type_name_index + const_name_index
                        r.u2()
                        r.u2()
                        enum_const = r.p
                        r.p = enum_const
                        return _enum_name(self, enum_const - 4)
                    else:
                        _skip_element_value(r, ev)
        return None


def _enum_name(cf, type_name_idx):
    # type_name_index points at a Utf8 descriptor like "Ljava/lang/annotation/RetentionPolicy;"
    try:
        cf.utf8(type_name_idx)
    except ClassFormatError:
        return None
    return None


def _skip_element_value(r, ev):
    if ev in (0x42, 0x43, 0x44, 0x46, 0x48, 0x49, 0x4a, 0x53, 0x5a):
        r.u2()
    elif ev == 0x65:
        r.u2()
        r.u2()
    elif ev == 0x73:
        r.u2()
    elif ev == 0x63:
        r.u2()
    elif ev == 0x40:
        _skip_annotation(r)
    elif ev == 0x5b:
        for _ in range(r.u2()):
            _skip_element_value(r, r.u1())
    else:
        raise ClassFormatError("bad element_value tag 0x%02x" % ev)


def _skip_annotation(r):
    r.u2()
    _skip_element_pairs(r)


def _skip_element_pairs(r):
    for _ in range(r.u2()):
        r.u2()
        _skip_element_value(r, r.u1())


def _read_attrs(r, cf):
    out = []
    for _ in range(r.u2()):
        name_idx = r.u2()
        length = r.u4()
        off = r.p
        data = r.bytes(length)
        try:
            name = cf.utf8(name_idx)
        except ClassFormatError:
            name = "<bad-cp#%d>" % name_idx
        out.append(Attr(name, data, off))
    return out


def parse(data, name="<class>"):
    """Parse a class file. Raises ClassFormatError on any structural damage."""
    r = Reader(data, name)
    if len(data) < 10:
        raise ClassFormatError("%s: file too short (%d bytes)" % (name, len(data)))
    if r.u4() != MAGIC:
        raise ClassFormatError("%s: bad magic (not a class file)" % name)
    cf = ClassFile()
    cf.name = name
    cf.minor = r.u2()
    cf.major = r.u2()
    if not 45 <= cf.major <= 70:
        raise ClassFormatError("%s: implausible major version %d" % (name, cf.major))

    count = r.u2()
    if count == 0:
        raise ClassFormatError("%s: constant_pool_count == 0" % name)
    i = 1
    while i < count:
        tag = r.u1()
        if tag not in CP_TAGS:
            raise ClassFormatError("%s: unknown constant-pool tag %d at entry %d"
                                   % (name, tag, i))
        if tag == 1:
            cf.cp.append((1, r.bytes(r.u2()).decode("utf-8", "replace")))
        elif tag in (3, 4):
            cf.cp.append((tag, r.u4()))
        elif tag in (5, 6):
            if i + 1 >= count:
                raise ClassFormatError("%s: 8-byte constant %d overflows the pool" % (name, i))
            cf.cp.append((tag, (r.u4() << 32) | r.u4()))
            cf.cp.append(None)          # long/double take two slots
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            cf.cp.append((tag, r.u2()))
        elif tag in (9, 10, 11, 12, 17, 18):
            cf.cp.append((tag, (r.u2(), r.u2())))
        elif tag == 15:
            cf.cp.append((tag, (r.u1(), r.u2())))
        i += 1

    cf.access = r.u2()
    cf.this_class = r.u2()
    cf.super_class = r.u2()
    cf.name = cf.class_name(cf.this_class) or name
    if cf.super_class:
        cf.class_name(cf.super_class)

    for _ in range(r.u2()):
        cf.interfaces.append(cf.class_name(r.u2()))

    def members(is_method):
        out = []
        for _ in range(r.u2()):
            acc = r.u2()
            n = cf.utf8(r.u2())
            d = cf.utf8(r.u2())
            out.append(Member(acc, n, d, _read_attrs(r, cf), is_method))
        return out

    cf.fields = members(False)
    cf.methods = members(True)
    cf.attrs = _read_attrs(r, cf)
    cf.class_attrs = {a.name: a for a in cf.attrs}

    if not r.at_end():
        raise ClassFormatError("%s: %d trailing byte(s) after the class structure"
                               % (cf.name, len(data) - r.p))
    return cf


def walk_code(code, owner="<code>"):
    """Yield (offset, opcode, length). Raises ClassFormatError when the
    instruction stream is not decodable, which is what a bad byte patch looks
    like."""
    i = 0
    n = len(code)
    targets = []
    while i < n:
        op = code[i]
        if op not in OPLEN:
            raise ClassFormatError("%s: invalid opcode 0x%02x at %d" % (owner, op, i))
        if op in (0xaa, 0xab):
            pad = (4 - ((i + 1) % 4)) % 4
            base = i + 1 + pad
            if base + 12 > n:
                raise ClassFormatError("%s: truncated switch at %d" % (owner, i))
            if op == 0xaa:
                low = int.from_bytes(code[base + 4:base + 8], "big", signed=True)
                high = int.from_bytes(code[base + 8:base + 12], "big", signed=True)
                cnt = high - low + 1
                if cnt < 0 or cnt > 65536:
                    raise ClassFormatError("%s: bad tableswitch bounds at %d" % (owner, i))
                length = 1 + pad + 12 + 4 * cnt
            else:
                npairs = int.from_bytes(code[base + 4:base + 8], "big", signed=True)
                if npairs < 0 or npairs > 65536:
                    raise ClassFormatError("%s: bad lookupswitch npairs at %d" % (owner, i))
                length = 1 + pad + 8 + 8 * npairs
        elif op == 0xc4:            # wide
            if i + 1 >= n:
                raise ClassFormatError("%s: truncated wide at %d" % (owner, i))
            sub = code[i + 1]
            length = 1 + 1 + (4 if sub == 0x84 else 2)
        else:
            length = 1 + OPLEN[op]
        if i + length > n:
            raise ClassFormatError("%s: instruction 0x%02x at %d runs past the end of code"
                                   % (owner, op, i))
        yield i, op, length
        i += length


def instruction_offsets(code, owner="<code>"):
    return {off for off, _op, _len in walk_code(code, owner)}
