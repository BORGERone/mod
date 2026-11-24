package dev.settledlands.dungeon;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/**
 * Build 431: pure-Java NBT codec and rotation/translation engine for both Axiom {@code .bp} and
 * Create/vanilla {@code .nbt} schematics.
 *
 * <p>Operates cleanly in pure Java before converting to Minecraft's {@link CompoundTag} via the
 * already-verified {@link NbtIo#readCompressed} bridge, ensuring zero unverified Mojmap NBT
 * method signatures while supporting full rotation of Create {@code BlockEntity} and
 * {@code Entity} data ({@code create:super_glue}, {@code Belt}, {@code FluidTank}, {@code ItemVault},
 * {@code ChainConveyor}, {@code MechanicalArm}, {@code DisplayLink}, {@code MechanicalCrafter},
 * {@code Copycat}, brackets, pistons, bearings, and kinetic networks).</p>
 */
public final class DungeonNbt431 {

    private DungeonNbt431() {}

    public static final class NbtValue {
        public final int type;
        public final int listElemType;
        public final Object val;

        public NbtValue(int type, int listElemType, Object val) {
            this.type = type;
            this.listElemType = listElemType;
            this.val = val;
        }

        public NbtValue copy() {
            if (type == 10 && val instanceof NbtCompound c) {
                return new NbtValue(10, 0, c.copy());
            }
            if (type == 9 && val instanceof List<?> raw) {
                List<NbtValue> out = new ArrayList<>(raw.size());
                for (Object item : raw) {
                    out.add(item instanceof NbtValue nv ? nv.copy() : null);
                }
                return new NbtValue(9, listElemType, out);
            }
            if (type == 7 && val instanceof byte[] ba) {
                byte[] copy = new byte[ba.length];
                System.arraycopy(ba, 0, copy, 0, ba.length);
                return new NbtValue(7, 0, copy);
            }
            if (type == 11 && val instanceof int[] ia) {
                int[] copy = new int[ia.length];
                System.arraycopy(ia, 0, copy, 0, ia.length);
                return new NbtValue(11, 0, copy);
            }
            if (type == 12 && val instanceof long[] la) {
                long[] copy = new long[la.length];
                System.arraycopy(la, 0, copy, 0, la.length);
                return new NbtValue(12, 0, copy);
            }
            return new NbtValue(type, listElemType, val);
        }
    }

    public static final class NbtCompound {
        public final Map<String, NbtValue> values = new LinkedHashMap<>();

        public boolean isEmpty() {
            return values.isEmpty();
        }

        public boolean contains(String key) {
            return values.containsKey(key);
        }

        public String getString(String key) {
            NbtValue v = values.get(key);
            return (v != null && v.val instanceof String s) ? s : "";
        }

        public int getInt(String key) {
            NbtValue v = values.get(key);
            return (v != null && v.val instanceof Number n) ? n.intValue() : 0;
        }

        public boolean getBoolean(String key) {
            NbtValue v = values.get(key);
            if (v == null) return false;
            if (v.val instanceof Boolean b) return b;
            if (v.val instanceof Number n) return n.byteValue() != 0;
            return false;
        }

        public int[] getIntArray(String key) {
            NbtValue v = values.get(key);
            return (v != null && v.val instanceof int[] a) ? a : null;
        }

        public NbtCompound getCompound(String key) {
            NbtValue v = values.get(key);
            return (v != null && v.val instanceof NbtCompound c) ? c : null;
        }

        @SuppressWarnings("unchecked")
        public List<NbtValue> getList(String key) {
            NbtValue v = values.get(key);
            return (v != null && v.val instanceof List<?> l) ? (List<NbtValue>) l : Collections.emptyList();
        }

        public long[] getLongArray(String key) {
            NbtValue v = values.get(key);
            return (v != null && v.val instanceof long[] a) ? a : new long[0];
        }

        public void putByte(String key, byte val) {
            values.put(key, new NbtValue(1, 0, val));
        }

        public void putInt(String key, int val) {
            values.put(key, new NbtValue(3, 0, val));
        }

        public void putLong(String key, long val) {
            values.put(key, new NbtValue(4, 0, val));
        }

        public void putFloat(String key, float val) {
            values.put(key, new NbtValue(5, 0, val));
        }

        public void putDouble(String key, double val) {
            values.put(key, new NbtValue(6, 0, val));
        }

        public void putString(String key, String val) {
            values.put(key, new NbtValue(8, 0, val == null ? "" : val));
        }

        public void putIntArray(String key, int[] val) {
            values.put(key, new NbtValue(11, 0, val));
        }

        public void putCompound(String key, NbtCompound val) {
            values.put(key, new NbtValue(10, 0, val));
        }

        public void putDoubleList(String key, double a, double b, double c) {
            List<NbtValue> list = new ArrayList<>(3);
            list.add(new NbtValue(6, 0, a));
            list.add(new NbtValue(6, 0, b));
            list.add(new NbtValue(6, 0, c));
            values.put(key, new NbtValue(9, 6, list));
        }

        public void remove(String key) {
            values.remove(key);
        }

        public NbtCompound copy() {
            NbtCompound out = new NbtCompound();
            for (Map.Entry<String, NbtValue> e : values.entrySet()) {
                out.values.put(e.getKey(), e.getValue() == null ? null : e.getValue().copy());
            }
            return out;
        }

        public NbtCompound withoutKeys(String... drop) {
            NbtCompound copy = new NbtCompound();
            for (Map.Entry<String, NbtValue> e : values.entrySet()) {
                boolean skip = false;
                for (String d : drop) {
                    if (d.equals(e.getKey())) {
                        skip = true;
                        break;
                    }
                }
                if (!skip && e.getValue() != null) {
                    copy.values.put(e.getKey(), e.getValue().copy());
                }
            }
            return copy;
        }
    }

    public static final class CountingInputStream extends ByteArrayInputStream {
        public int bytesRead = 0;

        public CountingInputStream(byte[] buf, int off) {
            super(buf, off, buf.length - off);
        }

        @Override
        public synchronized int read() {
            int b = super.read();
            if (b >= 0) bytesRead++;
            return b;
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            int n = super.read(b, off, len);
            if (n > 0) bytesRead += n;
            return n;
        }
    }

    public static final class NbtReader {
        private final DataInputStream in;

        public NbtReader(java.io.InputStream stream) {
            this.in = new DataInputStream(stream);
        }

        public int readRootType() throws IOException {
            return in.readUnsignedByte();
        }

        public String readString() throws IOException {
            int len = in.readUnsignedShort();
            byte[] b = in.readNBytes(len);
            if (b.length != len) throw new EOFException("Truncated NBT string");
            return new String(b, StandardCharsets.UTF_8);
        }

        public NbtCompound readCompound() throws IOException {
            NbtCompound c = new NbtCompound();
            while (true) {
                int type = in.readUnsignedByte();
                if (type == 0) return c;
                String key = readString();
                c.values.put(key, readValue(type));
            }
        }

        public NbtValue readValue(int type) throws IOException {
            return switch (type) {
                case 1 -> new NbtValue(1, 0, in.readByte());
                case 2 -> new NbtValue(2, 0, in.readShort());
                case 3 -> new NbtValue(3, 0, in.readInt());
                case 4 -> new NbtValue(4, 0, in.readLong());
                case 5 -> new NbtValue(5, 0, in.readFloat());
                case 6 -> new NbtValue(6, 0, in.readDouble());
                case 7 -> {
                    int n = in.readInt();
                    if (n < 0 || n > 64_000_000) throw new IOException("Bad byte[] len " + n);
                    yield new NbtValue(7, 0, in.readNBytes(n));
                }
                case 8 -> new NbtValue(8, 0, readString());
                case 9 -> {
                    int elemType = in.readUnsignedByte();
                    int n = in.readInt();
                    if (n < 0 || n > 10_000_000) throw new IOException("Bad list len " + n);
                    List<NbtValue> items = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        items.add(elemType == 0 ? new NbtValue(0, 0, null) : readValue(elemType));
                    }
                    yield new NbtValue(9, elemType, items);
                }
                case 10 -> new NbtValue(10, 0, readCompound());
                case 11 -> {
                    int n = in.readInt();
                    if (n < 0 || n > 16_000_000) throw new IOException("Bad int[] len " + n);
                    int[] arr = new int[n];
                    for (int i = 0; i < n; i++) arr[i] = in.readInt();
                    yield new NbtValue(11, 0, arr);
                }
                case 12 -> {
                    int n = in.readInt();
                    if (n < 0 || n > 16_000_000) throw new IOException("Bad long[] len " + n);
                    long[] arr = new long[n];
                    for (int i = 0; i < n; i++) arr[i] = in.readLong();
                    yield new NbtValue(12, 0, arr);
                }
                default -> throw new IOException("Unsupported NBT tag type: " + type);
            };
        }
    }

    public static NbtCompound readRootCompound(byte[] rawBytes, String label) throws IOException {
        if (rawBytes == null || rawBytes.length < 4) {
            throw new IOException("NBT payload too short: " + label);
        }
        boolean isGzip = (rawBytes[0] & 0xFF) == 0x1F && (rawBytes[1] & 0xFF) == 0x8B;
        try (java.io.InputStream in = isGzip
                ? new GZIPInputStream(new ByteArrayInputStream(rawBytes))
                : new ByteArrayInputStream(rawBytes)) {
            NbtReader r = new NbtReader(in);
            int rootType = r.readRootType();
            if (rootType != 10) {
                throw new IOException("Root NBT tag is not TAG_Compound (" + rootType + "): " + label);
            }
            r.readString();
            return r.readCompound();
        }
    }

    public static CompoundTag toMinecraftTag(NbtCompound compound) throws IOException {
        if (compound == null || compound.values.isEmpty()) {
            return new CompoundTag();
        }
        ByteArrayOutputStream rawBuf = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(rawBuf);
             DataOutputStream out = new DataOutputStream(gz)) {
            out.writeByte(10);
            writeString(out, "");
            writeCompound(out, compound);
        }
        return NbtIo.readCompressed(new ByteArrayInputStream(rawBuf.toByteArray()),
                NbtAccounter.unlimitedHeap());
    }

    public static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(raw.length);
        out.write(raw);
    }

    public static void writeCompound(DataOutputStream out, NbtCompound c) throws IOException {
        for (Map.Entry<String, NbtValue> e : c.values.entrySet()) {
            NbtValue v = e.getValue();
            if (v == null) continue;
            out.writeByte(v.type);
            writeString(out, e.getKey());
            writeValue(out, v.type, v.listElemType, v.val);
        }
        out.writeByte(0);
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(DataOutputStream out, int type, int elemType, Object val) throws IOException {
        switch (type) {
            case 1 -> out.writeByte(((Number) val).byteValue());
            case 2 -> out.writeShort(((Number) val).shortValue());
            case 3 -> out.writeInt(((Number) val).intValue());
            case 4 -> out.writeLong(((Number) val).longValue());
            case 5 -> out.writeFloat(((Number) val).floatValue());
            case 6 -> out.writeDouble(((Number) val).doubleValue());
            case 7 -> {
                byte[] b = (byte[]) val;
                out.writeInt(b.length);
                out.write(b);
            }
            case 8 -> writeString(out, (String) val);
            case 9 -> {
                List<NbtValue> items = (List<NbtValue>) val;
                int et = items.isEmpty() ? 0 : elemType;
                out.writeByte(et);
                out.writeInt(items.size());
                for (NbtValue item : items) {
                    writeValue(out, et, item == null ? 0 : item.listElemType, item == null ? null : item.val);
                }
            }
            case 10 -> writeCompound(out, (NbtCompound) val);
            case 11 -> {
                int[] a = (int[]) val;
                out.writeInt(a.length);
                for (int x : a) out.writeInt(x);
            }
            case 12 -> {
                long[] a = (long[]) val;
                out.writeInt(a.length);
                for (long x : a) out.writeLong(x);
            }
            default -> throw new IOException("Unsupported NBT write type: " + type);
        }
    }

    // ---- Create & Vanilla NBT Transformation Helpers ----

    /**
     * Infers the original capture-world origin from any multiblock block entity
     * (e.g. {@code create:belt}, {@code create:fluid_tank}, {@code create:item_vault}) whose
     * {@code Controller} tag still stores absolute source-world coordinates.
     */
    public static int[] inferSchemaOrigin(List<DungeonTemplate425.BlockEntitySpec> blockEntities) {
        if (blockEntities == null || blockEntities.isEmpty()) return null;
        int[] fallbackOrigin = null;
        for (DungeonTemplate425.BlockEntitySpec be : blockEntities) {
            if (be == null || be.rawNbt == null) continue;
            int[] ctrl = readBlockPosArray(be.rawNbt.values.get("Controller"));
            if (ctrl == null) continue;
            int ox = ctrl[0] - be.x;
            int oy = ctrl[1] - be.y;
            int oz = ctrl[2] - be.z;
            if (be.rawNbt.getBoolean("IsController")) {
                if (ox != 0 || oy != 0 || oz != 0) {
                    return new int[] { ox, oy, oz };
                }
            } else if (fallbackOrigin == null && (ox > 10 || ox < -10 || oy > 10 || oy < -10 || oz > 10 || oz < -10)) {
                // Non-controller tank/vault/belt segment: controller is within the local structure,
                // so a large offset from (0,0,0) reliably reflects the original world origin.
                fallbackOrigin = new int[] { ox, oy, oz };
            }
        }
        return fallbackOrigin;
    }

    public static NbtCompound transformBlockEntityNbt(
            String id,
            NbtCompound raw,
            int originX,
            int originY,
            int originZ,
            int newWx,
            int newWy,
            int newWz,
            int turns,
            int[] oldSchemaOrigin) {
        NbtCompound nbt = raw == null ? new NbtCompound() : raw.copy();
        int t = turns & 3;
        if (id != null && !id.isEmpty()) {
            nbt.putString("id", id);
        }
        nbt.putInt("x", newWx);
        nbt.putInt("y", newWy);
        nbt.putInt("z", newWz);

        String lowId = id == null ? "" : id.toLowerCase(Locale.ROOT);
        boolean isCreate = lowId.startsWith("create:");
        boolean isKinetic = isCreate
                || nbt.contains("Source")
                || nbt.contains("Network")
                || nbt.contains("NeedsSpeedUpdate")
                || nbt.contains("Speed");

        if (isKinetic) {
            nbt.remove("Source");
            nbt.remove("Network");
            nbt.remove("LastKnownPos");
            nbt.remove("DestroyEffect");
            nbt.remove("Targets");
            nbt.remove("FilteredTargets");
            nbt.remove("EarlyTarget");
            nbt.remove("EarlyTargetPos");
            nbt.remove("LastException");
            nbt.remove("MirrorParent");
            nbt.remove("MirrorChildren");
            nbt.putFloat("Speed", 0.0F);
            nbt.putByte("NeedsSpeedUpdate", (byte) 1);

            if ("create:windmill_bearing".equals(lowId)
                    || nbt.contains("LastGenerated")
                    || nbt.contains("QueueAssembly")) {
                if (nbt.getBoolean("Running")) {
                    nbt.putByte("Running", (byte) 0);
                }
                nbt.putByte("QueueAssembly", (byte) 1);
            }
        }

        // Multiblock Controller handling (Belt, FluidTank, ItemVault):
        // When rotated, Belt's vertical START/END and Tank/Vault's (minX,minY,minZ) controller corner
        // change in world space, so force clean re-formation via BeltBlock.initBelt / Uninitialized=1.
        if ("create:belt".equals(lowId)) {
            if (t == 0 && nbt.getBoolean("IsController")) {
                nbt.putIntArray("Controller", new int[] { newWx, newWy, newWz });
            } else if (t == 0 && nbt.contains("Controller") && oldSchemaOrigin != null) {
                int[] oldCtrl = readBlockPosArray(nbt.values.get("Controller"));
                if (oldCtrl != null) {
                    int ncx = originX + (oldCtrl[0] - oldSchemaOrigin[0]);
                    int ncy = originY + (oldCtrl[1] - oldSchemaOrigin[1]);
                    int ncz = originZ + (oldCtrl[2] - oldSchemaOrigin[2]);
                    nbt.putIntArray("Controller", new int[] { ncx, ncy, ncz });
                } else {
                    nbt.remove("Controller");
                    nbt.putByte("IsController", (byte) 0);
                    nbt.putInt("Length", 0);
                    nbt.putInt("Index", 0);
                }
            } else {
                nbt.remove("Controller");
                nbt.putByte("IsController", (byte) 0);
                nbt.putInt("Length", 0);
                nbt.putInt("Index", 0);
            }
        } else if ("create:fluid_tank".equals(lowId) || "create:item_vault".equals(lowId)) {
            if (nbt.contains("Controller")) {
                int[] oldCtrl = readBlockPosArray(nbt.values.get("Controller"));
                if (t == 0 && oldCtrl != null && oldSchemaOrigin != null) {
                    nbt.putIntArray("Controller", new int[] {
                            originX + (oldCtrl[0] - oldSchemaOrigin[0]),
                            originY + (oldCtrl[1] - oldSchemaOrigin[1]),
                            originZ + (oldCtrl[2] - oldSchemaOrigin[2])
                    });
                } else {
                    nbt.remove("Controller");
                    nbt.putInt("Size", 1);
                    if ("create:fluid_tank".equals(lowId)) {
                        nbt.putInt("Height", 1);
                    } else {
                        nbt.putInt("Length", 1);
                    }
                    nbt.putByte("Uninitialized", (byte) 1);
                }
            } else if (t != 0 && (nbt.getInt("Size") > 1
                    || ("create:item_vault".equals(lowId) && nbt.getInt("Length") > 1))) {
                nbt.putInt("Size", 1);
                if ("create:fluid_tank".equals(lowId)) {
                    nbt.putInt("Height", 1);
                } else {
                    nbt.putInt("Length", 1);
                }
                nbt.putByte("Uninitialized", (byte) 1);
            } else if (!nbt.contains("Size")) {
                nbt.putByte("Uninitialized", (byte) 1);
            }
        } else if (nbt.getBoolean("IsController")) {
            nbt.putIntArray("Controller", new int[] { newWx, newWy, newWz });
        } else if (nbt.contains("Controller")) {
            int[] oldCtrl = readBlockPosArray(nbt.values.get("Controller"));
            if (oldCtrl != null && oldSchemaOrigin != null) {
                int lx = oldCtrl[0] - oldSchemaOrigin[0];
                int ly = oldCtrl[1] - oldSchemaOrigin[1];
                int lz = oldCtrl[2] - oldSchemaOrigin[2];
                int ncx = originX + DungeonGenerator427.rotX(lx, lz, t);
                int ncy = originY + ly;
                int ncz = originZ + DungeonGenerator427.rotZ(lx, lz, t);
                nbt.putIntArray("Controller", new int[] { ncx, ncy, ncz });
            } else {
                nbt.remove("Controller");
                nbt.putByte("Uninitialized", (byte) 1);
            }
        }

        // Steam engine PoweredShaft absolute reference
        if (nbt.contains("EnginePos")) {
            int[] oldEp = readBlockPosArray(nbt.values.get("EnginePos"));
            if (oldEp != null && oldSchemaOrigin != null) {
                int lx = oldEp[0] - oldSchemaOrigin[0];
                int ly = oldEp[1] - oldSchemaOrigin[1];
                int lz = oldEp[2] - oldSchemaOrigin[2];
                nbt.putIntArray("EnginePos", new int[] {
                        originX + DungeonGenerator427.rotX(lx, lz, t),
                        originY + ly,
                        originZ + DungeonGenerator427.rotZ(lx, lz, t)
                });
            } else {
                nbt.remove("EnginePos");
            }
        }

        if (t != 0) {
            // DisplayLink relative TargetOffset
            rotatePosField(nbt, "TargetOffset", t);

            // ChainConveyor relative Connections list & TravellingPackages[].Target
            NbtValue connVal = nbt.values.get("Connections");
            if (connVal != null && connVal.type == 9 && connVal.val instanceof List<?> rawList) {
                @SuppressWarnings("unchecked")
                List<NbtValue> items = (List<NbtValue>) rawList;
                for (int i = 0; i < items.size(); i++) {
                    NbtValue rotated = rotatePosValue(items.get(i), t);
                    if (rotated != null) {
                        items.set(i, rotated);
                    }
                }
            }
            for (NbtValue pkgItem : nbt.getList("TravellingPackages")) {
                if (pkgItem != null && pkgItem.val instanceof NbtCompound pkg) {
                    rotatePosField(pkg, "Target", t);
                }
            }

            // MechanicalArm relative InteractionPoints[].Pos
            for (NbtValue ptItem : nbt.getList("InteractionPoints")) {
                if (ptItem != null && ptItem.val instanceof NbtCompound pt) {
                    rotatePosField(pt, "Pos", t);
                }
            }

            // MechanicalCrafter ConnectedInput Data[].{X, Y, Z}
            NbtCompound inputTag = nbt.getCompound("Input");
            if (inputTag != null) {
                for (NbtValue dItem : inputTag.getList("Data")) {
                    if (dItem != null && dItem.val instanceof NbtCompound dc) {
                        rotateXyzCompound(dc, t);
                    }
                }
            }

            // Embedded BlockStates inside Bracket (FluidPipe / Belt / Shaft) & Copycat Material
            rotateStateTag(nbt.getCompound("Bracket"), t);
            rotateStateTag(nbt.getCompound("Material"), t);
        }

        return nbt;
    }

    public static final class TransformedEntity {
        public final double wx;
        public final double wy;
        public final double wz;
        public final float yaw;
        public final float pitch;
        public final boolean moveBeforeLoad;
        public final CompoundTag mcNbt;

        public TransformedEntity(double wx, double wy, double wz, float yaw, float pitch,
                boolean moveBeforeLoad, CompoundTag mcNbt) {
            this.wx = wx;
            this.wy = wy;
            this.wz = wz;
            this.yaw = yaw;
            this.pitch = pitch;
            this.moveBeforeLoad = moveBeforeLoad;
            this.mcNbt = mcNbt;
        }
    }

    public static TransformedEntity transformEntityPlacement(
            DungeonTemplate425.EntitySpec eSpec,
            int originX,
            int originY,
            int originZ,
            int turns) throws IOException {
        int t = turns & 3;
        NbtCompound nbt = eSpec.rawNbt == null ? null : eSpec.rawNbt.copy();

        boolean isSuperGlue = "create:super_glue".equals(eSpec.id)
                || (nbt != null && !nbt.getList("From").isEmpty() && !nbt.getList("To").isEmpty());

        if (isSuperGlue && nbt != null) {
            double[] from = readDoubleVec3(nbt.getList("From"));
            double[] to = readDoubleVec3(nbt.getList("To"));
            if (from != null && to != null) {
                double sx0 = eSpec.dx + from[0];
                double sy0 = eSpec.dy + from[1];
                double sz0 = eSpec.dz + from[2];
                double sx1 = eSpec.dx + to[0];
                double sy1 = eSpec.dy + to[1];
                double sz1 = eSpec.dz + to[2];

                double rx0 = DungeonGenerator427.rotEntityX(sx0, sz0, t);
                double rz0 = DungeonGenerator427.rotEntityZ(sx0, sz0, t);
                double rx1 = DungeonGenerator427.rotEntityX(sx1, sz1, t);
                double rz1 = DungeonGenerator427.rotEntityZ(sx1, sz1, t);

                double minX = Math.min(rx0, rx1);
                double maxX = Math.max(rx0, rx1);
                double minY = Math.min(sy0, sy1);
                double maxY = Math.max(sy0, sy1);
                double minZ = Math.min(rz0, rz1);
                double maxZ = Math.max(rz0, rz1);

                double cx = (minX + maxX) * 0.5D;
                double cz = (minZ + maxZ) * 0.5D;
                double wx = originX + cx;
                double wy = originY + minY;
                double wz = originZ + cz;
                nbt.putDoubleList("From", minX - cx, 0.0D, minZ - cz);
                nbt.putDoubleList("To", maxX - cx, maxY - minY, maxZ - cz);
                nbt.remove("UUID");
                // Preserve transformed "Pos" because Vanilla Entity.load(CompoundTag) reads "Pos"
                // (defaulting to [0,0,0] if stripped) before SuperGlueEntity.readAdditionalSaveData.
                nbt.putDoubleList("Pos", wx, wy, wz);
                nbt.remove("neoforge:attachments");
                return new TransformedEntity(wx, wy, wz, 0.0F, 0.0F, true, toMinecraftTag(nbt));
            }
        }

        boolean isContraption = nbt != null && (nbt.contains("Contraption")
                || (eSpec.id != null && eSpec.id.startsWith("create:") && eSpec.id.contains("contraption")));
        if (isContraption) {
            return transformContraptionEntity(eSpec, nbt, originX, originY, originZ, t);
        }

        double edx = DungeonGenerator427.rotEntityX(eSpec.dx, eSpec.dz, t);
        double edz = DungeonGenerator427.rotEntityZ(eSpec.dx, eSpec.dz, t);
        double wx = originX + edx;
        double wy = originY + eSpec.dy;
        double wz = originZ + edz;
        float yaw = eSpec.yaw + 90.0F * t;

        if (nbt != null) {
            nbt.remove("UUID");
            nbt.putDoubleList("Pos", wx, wy, wz);
            nbt.remove("neoforge:attachments");
            if (nbt.contains("facing")) {
                int f = nbt.getInt("facing");
                if (f >= 2 && f <= 5) {
                    nbt.putByte("facing", (byte) rotateDirectionByte3D(f, t));
                }
            }
            int bx = (int) Math.floor(wx);
            int by = (int) Math.floor(wy);
            int bz = (int) Math.floor(wz);
            if (nbt.contains("block_pos")) {
                nbt.putIntArray("block_pos", new int[] { bx, by, bz });
            }
            if (nbt.contains("TileX")) {
                nbt.putInt("TileX", bx);
                nbt.putInt("TileY", by);
                nbt.putInt("TileZ", bz);
            }
            return new TransformedEntity(wx, wy, wz, yaw, eSpec.pitch, false, toMinecraftTag(nbt));
        }

        CompoundTag fallback = eSpec.nbt == null ? new CompoundTag() : eSpec.nbt.copy();
        fallback.remove("UUID");
        fallback.remove("Pos");
        fallback.remove("neoforge:attachments");
        return new TransformedEntity(wx, wy, wz, yaw, eSpec.pitch, false, fallback);
    }

    private static boolean isWholeCoord(double v) {
        return Math.abs(v - Math.rint(v)) < 1.0E-5D;
    }

    public static NbtCompound prepareContraptionNbt(
            DungeonTemplate425.EntitySpec eSpec,
            int originX,
            int originY,
            int originZ,
            int turns) {
        if (eSpec == null || eSpec.rawNbt == null) return null;
        return transformContraptionNbt(eSpec, eSpec.rawNbt.copy(), originX, originY, originZ, turns & 3, null);
    }

    private static TransformedEntity transformContraptionEntity(
            DungeonTemplate425.EntitySpec eSpec,
            NbtCompound nbt,
            int originX,
            int originY,
            int originZ,
            int t) throws IOException {
        double[] outCoords = new double[3];
        NbtCompound transformed = transformContraptionNbt(eSpec, nbt, originX, originY, originZ, t, outCoords);
        return new TransformedEntity(outCoords[0], outCoords[1], outCoords[2], 0.0F, 0.0F, true,
                toMinecraftTag(transformed));
    }

    private static NbtCompound transformContraptionNbt(
            DungeonTemplate425.EntitySpec eSpec,
            NbtCompound nbt,
            int originX,
            int originY,
            int originZ,
            int t,
            double[] outCoords) {
        NbtCompound contr = nbt.getCompound("Contraption");
        int[] oldAnchor = contr != null ? readBlockPosArray(contr.values.get("Anchor")) : null;
        double[] oldPosVec = readDoubleVec3(nbt.getList("Pos"));

        double wx;
        double wy = originY + eSpec.dy;
        double wz;
        int anchorX;
        int anchorY;
        int anchorZ;
        if (isWholeCoord(eSpec.dx) && isWholeCoord(eSpec.dz)) {
            int bx = (int) Math.round(eSpec.dx);
            int bz = (int) Math.round(eSpec.dz);
            wx = originX + DungeonGenerator427.rotX(bx, bz, t);
            wz = originZ + DungeonGenerator427.rotZ(bx, bz, t);
            anchorX = (int) Math.floor(wx);
            anchorY = (int) Math.floor(wy);
            anchorZ = (int) Math.floor(wz);
        } else if (oldAnchor != null && oldPosVec != null) {
            double offX = oldPosVec[0] - oldAnchor[0];
            double offY = oldPosVec[1] - oldAnchor[1];
            double offZ = oldPosVec[2] - oldAnchor[2];
            int oldAnchorLocalX = (int) Math.round(eSpec.dx - offX);
            int oldAnchorLocalY = (int) Math.round(eSpec.dy - offY);
            int oldAnchorLocalZ = (int) Math.round(eSpec.dz - offZ);
            anchorX = originX + DungeonGenerator427.rotX(oldAnchorLocalX, oldAnchorLocalZ, t);
            anchorY = originY + oldAnchorLocalY;
            anchorZ = originZ + DungeonGenerator427.rotZ(oldAnchorLocalX, oldAnchorLocalZ, t);
            double[] rotOff = rotateHorizontalVec(offX, offZ, t);
            wx = anchorX + rotOff[0];
            wy = anchorY + offY;
            wz = anchorZ + rotOff[1];
        } else {
            wx = originX + DungeonGenerator427.rotEntityX(eSpec.dx, eSpec.dz, t);
            wz = originZ + DungeonGenerator427.rotEntityZ(eSpec.dx, eSpec.dz, t);
            anchorX = (int) Math.floor(wx);
            anchorY = (int) Math.floor(wy);
            anchorZ = (int) Math.floor(wz);
        }
        if (outCoords != null && outCoords.length >= 3) {
            outCoords[0] = wx;
            outCoords[1] = wy;
            outCoords[2] = wz;
        }

        int newEntBx = (int) Math.floor(wx);
        int newEntBy = (int) Math.floor(wy);
        int newEntBz = (int) Math.floor(wz);

        nbt.remove("UUID");
        // Critical: Entity.load(CompoundTag) reads "Pos" (defaulting to [0,0,0] if absent!) BEFORE
        // ControlledContraptionEntity.readAdditional computes controllerPos = ControllerRelative + blockPosition().
        nbt.putDoubleList("Pos", wx, wy, wz);
        nbt.remove("neoforge:attachments");

        if (nbt.contains("ControllerRelative")) {
            int[] oldCtrlRel = readBlockPosArray(nbt.values.get("ControllerRelative"));
            if (oldCtrlRel != null) {
                int oldEntBx = (int) Math.floor(eSpec.dx);
                int oldEntBy = (int) Math.floor(eSpec.dy);
                int oldEntBz = (int) Math.floor(eSpec.dz);
                int oldCtrlLocalX = oldEntBx + oldCtrlRel[0];
                int oldCtrlLocalY = oldEntBy + oldCtrlRel[1];
                int oldCtrlLocalZ = oldEntBz + oldCtrlRel[2];
                int newCtrlWx = originX + DungeonGenerator427.rotX(oldCtrlLocalX, oldCtrlLocalZ, t);
                int newCtrlWy = originY + oldCtrlLocalY;
                int newCtrlWz = originZ + DungeonGenerator427.rotZ(oldCtrlLocalX, oldCtrlLocalZ, t);
                int[] exactRel = new int[] {
                        newCtrlWx - newEntBx,
                        newCtrlWy - newEntBy,
                        newCtrlWz - newEntBz
                };
                NbtValue crPrev = nbt.values.get("ControllerRelative");
                if (crPrev != null && crPrev.val instanceof NbtCompound) {
                    NbtCompound crComp = new NbtCompound();
                    crComp.putInt("X", exactRel[0]);
                    crComp.putInt("Y", exactRel[1]);
                    crComp.putInt("Z", exactRel[2]);
                    nbt.putCompound("ControllerRelative", crComp);
                } else {
                    nbt.putIntArray("ControllerRelative", exactRel);
                }
            } else if (t != 0) {
                rotatePosField(nbt, "ControllerRelative", t);
            }
        }

        if (t != 0) {
            if ((t & 1) == 1 && nbt.contains("Axis")) {
                String ax = nbt.getString("Axis");
                if ("X".equals(ax)) nbt.putString("Axis", "Z");
                else if ("Z".equals(ax)) nbt.putString("Axis", "X");
                else if ("x".equals(ax)) nbt.putString("Axis", "z");
                else if ("z".equals(ax)) nbt.putString("Axis", "x");
            }
            rotateVec3Horizontal(nbt, "Motion", t);
        }

        if (contr != null) {
            contr.putIntArray("Anchor", new int[] { anchorX, anchorY, anchorZ });

            if (t != 0) {
                NbtValue fv = contr.values.get("Facing");
                if (fv != null && fv.val instanceof Number fn) {
                    int f = fn.intValue();
                    if (f >= 2 && f <= 5) {
                        int nf = rotateDirectionByte3D(f, t);
                        if (fv.type == 1) contr.putByte("Facing", (byte) nf);
                        else contr.putInt("Facing", nf);
                    }
                }

                rotateBoundsField(contr, "BoundsFront", t);

                NbtCompound blocksTag = contr.getCompound("Blocks");
                if (blocksTag != null) {
                    for (NbtValue palItem : blocksTag.getList("Palette")) {
                        if (palItem != null && palItem.val instanceof NbtCompound stateTag) {
                            rotateStateTag(stateTag, t);
                        }
                    }
                    for (NbtValue blkItem : blocksTag.getList("BlockList")) {
                        if (blkItem == null || !(blkItem.val instanceof NbtCompound entry)) continue;
                        NbtValue pv = entry.values.get("Pos");
                        if (pv != null && pv.val instanceof Number pn) {
                            long pos = pn.longValue();
                            int lx = (int) (pos >> 38);
                            int ly = (int) ((pos << 52) >> 52);
                            int lz = (int) ((pos << 26) >> 38);
                            int nlx = DungeonGenerator427.rotX(lx, lz, t);
                            int nlz = DungeonGenerator427.rotZ(lx, lz, t);
                            long npos = (((long) nlx & 0x3FFFFFFL) << 38)
                                    | (((long) nlz & 0x3FFFFFFL) << 12)
                                    | ((long) ly & 0xFFFL);
                            entry.putLong("Pos", npos);

                            NbtCompound data = entry.getCompound("Data");
                            if (data != null && !data.isEmpty()) {
                                if (data.contains("x")) data.putInt("x", nlx);
                                if (data.contains("y")) data.putInt("y", ly);
                                if (data.contains("z")) data.putInt("z", nlz);
                                rotatePosField(data, "Controller", t);
                                rotatePosField(data, "TargetOffset", t);
                                rotateStateTag(data.getCompound("Bracket"), t);
                                rotateStateTag(data.getCompound("Material"), t);
                            }
                        }
                    }
                }

                NbtValue seatsVal = contr.values.get("Seats");
                if (seatsVal != null && seatsVal.type == 9 && seatsVal.val instanceof List<?> rawSeats) {
                    @SuppressWarnings("unchecked")
                    List<NbtValue> seats = (List<NbtValue>) rawSeats;
                    for (int i = 0; i < seats.size(); i++) {
                        NbtValue rotSeat = rotatePosValue(seats.get(i), t);
                        if (rotSeat != null) {
                            seats.set(i, rotSeat);
                        }
                    }
                }

                NbtValue sgVal = contr.values.get("Superglue");
                if (sgVal != null && sgVal.type == 9 && sgVal.val instanceof List<?> rawSg) {
                    @SuppressWarnings("unchecked")
                    List<NbtValue> sgList = (List<NbtValue>) rawSg;
                    for (int i = 0; i < sgList.size(); i++) {
                        NbtValue item = sgList.get(i);
                        if (item == null) continue;
                        if (item.type == 9 && item.val instanceof List<?>) {
                            NbtValue rotatedBox = rotateAabbList(item, t);
                            if (rotatedBox != null) sgList.set(i, rotatedBox);
                        } else if (item.val instanceof NbtCompound sgc) {
                            if (sgc.contains("From") && sgc.contains("To")) {
                                double[] from = readDoubleVec3(sgc.getList("From"));
                                double[] to = readDoubleVec3(sgc.getList("To"));
                                if (from != null && to != null) {
                                    double rx0 = DungeonGenerator427.rotEntityX(from[0], from[2], t);
                                    double rz0 = DungeonGenerator427.rotEntityZ(from[0], from[2], t);
                                    double rx1 = DungeonGenerator427.rotEntityX(to[0], to[2], t);
                                    double rz1 = DungeonGenerator427.rotEntityZ(to[0], to[2], t);
                                    sgc.putDoubleList("From", Math.min(rx0, rx1), Math.min(from[1], to[1]), Math.min(rz0, rz1));
                                    sgc.putDoubleList("To", Math.max(rx0, rx1), Math.max(from[1], to[1]), Math.max(rz0, rz1));
                                }
                            }
                        }
                    }
                }

                for (NbtValue mbItem : contr.getList("CapturedMultiblocks")) {
                    if (mbItem == null || !(mbItem.val instanceof NbtCompound mb)) continue;
                    rotatePosField(mb, "Controller", t);
                    for (NbtValue pItem : mb.getList("Parts")) {
                        if (pItem == null || !(pItem.val instanceof NbtCompound pc)) continue;
                        if (pc.contains("Pos")) rotatePosField(pc, "Pos", t);
                        else rotateXyzCompound(pc, t);
                    }
                }

                for (NbtValue interItem : contr.getList("Interactors")) {
                    if (interItem != null && interItem.val instanceof NbtCompound ic) {
                        rotatePosField(ic, "Pos", t);
                    }
                }
            }

            for (NbtValue actorItem : contr.getList("Actors")) {
                if (actorItem == null || !(actorItem.val instanceof NbtCompound actor)) continue;
                if (t != 0) {
                    rotatePosField(actor, "Pos", t);
                    rotateVec3Horizontal(actor, "Motion", t);
                    rotateVec3Horizontal(actor, "RelativeMotion", t);
                }
                double[] oldPos = readDoubleVec3(actor.getList("Position"));
                if (oldPos != null && oldAnchor != null) {
                    double dx = oldPos[0] - oldAnchor[0];
                    double dy = oldPos[1] - oldAnchor[1];
                    double dz = oldPos[2] - oldAnchor[2];
                    double nx = t == 0 ? anchorX + dx : anchorX + DungeonGenerator427.rotEntityX(dx, dz, t);
                    double nz = t == 0 ? anchorZ + dz : anchorZ + DungeonGenerator427.rotEntityZ(dx, dz, t);
                    actor.putDoubleList("Position", nx, anchorY + dy, nz);
                } else {
                    int[] localPos = readBlockPosArray(actor.values.get("Pos"));
                    if (localPos != null) {
                        actor.putDoubleList("Position",
                                anchorX + localPos[0] + 0.5D,
                                anchorY + localPos[1] + 0.5D,
                                anchorZ + localPos[2] + 0.5D);
                    }
                }
            }
        }

        return nbt;
    }

    private static double[] rotateHorizontalVec(double vx, double vz, int turns) {
        return switch (turns & 3) {
            case 1 -> new double[] { -vz, vx };
            case 2 -> new double[] { -vx, -vz };
            case 3 -> new double[] { vz, -vx };
            default -> new double[] { vx, vz };
        };
    }

    private static void rotateVec3Horizontal(NbtCompound c, String key, int turns) {
        if (c == null || turns == 0 || !c.contains(key)) return;
        NbtValue v = c.values.get(key);
        if (v == null || v.type != 9 || !(v.val instanceof List<?> raw) || raw.size() < 3) return;
        double[] vec = readDoubleVec3(c.getList(key));
        if (vec == null) return;
        double nx = switch (turns & 3) {
            case 1 -> -vec[2];
            case 2 -> -vec[0];
            case 3 -> vec[2];
            default -> vec[0];
        };
        double nz = switch (turns & 3) {
            case 1 -> vec[0];
            case 2 -> -vec[2];
            case 3 -> -vec[0];
            default -> vec[2];
        };
        if (v.listElemType == 5) {
            List<NbtValue> list = new ArrayList<>(3);
            list.add(new NbtValue(5, 0, (float) nx));
            list.add(new NbtValue(5, 0, (float) vec[1]));
            list.add(new NbtValue(5, 0, (float) nz));
            c.values.put(key, new NbtValue(9, 5, list));
        } else {
            c.putDoubleList(key, nx, vec[1], nz);
        }
    }

    private static void rotateBoundsField(NbtCompound c, String key, int turns) {
        if (c == null || turns == 0 || !c.contains(key)) return;
        NbtValue rotated = rotateAabbList(c.values.get(key), turns);
        if (rotated != null) {
            c.values.put(key, rotated);
        }
    }

    private static NbtValue rotateAabbList(NbtValue v, int turns) {
        if (v == null || v.type != 9 || !(v.val instanceof List<?> raw) || raw.size() < 6) return null;
        double[] b = new double[6];
        for (int i = 0; i < 6; i++) {
            Object item = raw.get(i);
            if (!(item instanceof NbtValue nv) || !(nv.val instanceof Number num)) return null;
            b[i] = num.doubleValue();
        }
        double rx0 = DungeonGenerator427.rotEntityX(b[0], b[2], turns);
        double rz0 = DungeonGenerator427.rotEntityZ(b[0], b[2], turns);
        double rx1 = DungeonGenerator427.rotEntityX(b[3], b[5], turns);
        double rz1 = DungeonGenerator427.rotEntityZ(b[3], b[5], turns);
        double minX = Math.min(rx0, rx1);
        double minY = Math.min(b[1], b[4]);
        double minZ = Math.min(rz0, rz1);
        double maxX = Math.max(rx0, rx1);
        double maxY = Math.max(b[1], b[4]);
        double maxZ = Math.max(rz0, rz1);

        int et = v.listElemType == 5 ? 5 : 6;
        List<NbtValue> out = new ArrayList<>(6);
        if (et == 5) {
            out.add(new NbtValue(5, 0, (float) minX));
            out.add(new NbtValue(5, 0, (float) minY));
            out.add(new NbtValue(5, 0, (float) minZ));
            out.add(new NbtValue(5, 0, (float) maxX));
            out.add(new NbtValue(5, 0, (float) maxY));
            out.add(new NbtValue(5, 0, (float) maxZ));
        } else {
            out.add(new NbtValue(6, 0, minX));
            out.add(new NbtValue(6, 0, minY));
            out.add(new NbtValue(6, 0, minZ));
            out.add(new NbtValue(6, 0, maxX));
            out.add(new NbtValue(6, 0, maxY));
            out.add(new NbtValue(6, 0, maxZ));
        }
        return new NbtValue(9, et, out);
    }

    private static int rotateDirectionByte3D(int d, int turns) {
        // Direction 3D bytes: 2=NORTH, 5=EAST, 3=SOUTH, 4=WEST
        int idx = switch (d) {
            case 2 -> 0;
            case 5 -> 1;
            case 3 -> 2;
            case 4 -> 3;
            default -> -1;
        };
        if (idx < 0) return d;
        return switch ((idx + turns) & 3) {
            case 0 -> 2;
            case 1 -> 5;
            case 2 -> 3;
            default -> 4;
        };
    }

    private static double[] readDoubleVec3(List<NbtValue> list) {
        if (list == null || list.size() < 3) return null;
        if (list.get(0).val instanceof Number n0
                && list.get(1).val instanceof Number n1
                && list.get(2).val instanceof Number n2) {
            return new double[] { n0.doubleValue(), n1.doubleValue(), n2.doubleValue() };
        }
        return null;
    }

    private static int[] readBlockPosArray(NbtValue v) {
        if (v == null) return null;
        if (v.val instanceof int[] a && a.length >= 3) {
            return new int[] { a[0], a[1], a[2] };
        }
        if (v.val instanceof NbtCompound c) {
            if (c.contains("X") || c.contains("Y") || c.contains("Z")) {
                return new int[] { c.getInt("X"), c.getInt("Y"), c.getInt("Z") };
            }
            if (c.contains("x") || c.contains("y") || c.contains("z")) {
                return new int[] { c.getInt("x"), c.getInt("y"), c.getInt("z") };
            }
        }
        return null;
    }

    private static void rotatePosField(NbtCompound c, String key, int turns) {
        if (c == null || !c.contains(key)) return;
        NbtValue rotated = rotatePosValue(c.values.get(key), turns);
        if (rotated != null) {
            c.values.put(key, rotated);
        }
    }

    private static NbtValue rotatePosValue(NbtValue v, int turns) {
        if (v == null) return null;
        if (v.val instanceof int[] a && a.length >= 3) {
            int nx = DungeonGenerator427.rotX(a[0], a[2], turns);
            int nz = DungeonGenerator427.rotZ(a[0], a[2], turns);
            return new NbtValue(11, 0, new int[] { nx, a[1], nz });
        }
        if (v.val instanceof NbtCompound c) {
            NbtCompound copy = c.copy();
            rotateXyzCompound(copy, turns);
            return new NbtValue(10, 0, copy);
        }
        return null;
    }

    private static void rotateXyzCompound(NbtCompound c, int turns) {
        if (c == null) return;
        if (c.contains("X") || c.contains("Z")) {
            int x = c.getInt("X");
            int z = c.getInt("Z");
            c.putInt("X", DungeonGenerator427.rotX(x, z, turns));
            c.putInt("Z", DungeonGenerator427.rotZ(x, z, turns));
        } else if (c.contains("x") || c.contains("z")) {
            int x = c.getInt("x");
            int z = c.getInt("z");
            c.putInt("x", DungeonGenerator427.rotX(x, z, turns));
            c.putInt("z", DungeonGenerator427.rotZ(x, z, turns));
        }
    }

    private static void rotateStateTag(NbtCompound stateTag, int turns) {
        if (stateTag == null || turns == 0) return;
        NbtCompound propsTag = stateTag.getCompound("Properties");
        if (propsTag == null || propsTag.isEmpty()) return;
        Map<String, String> inMap = new LinkedHashMap<>();
        for (Map.Entry<String, NbtValue> e : propsTag.values.entrySet()) {
            if (e.getValue() != null && e.getValue().val != null) {
                inMap.put(e.getKey(), String.valueOf(e.getValue().val));
            }
        }
        Map<String, String> rotMap = DungeonTemplate425.rotateProps(inMap, turns, stateTag.getString("Name"));
        NbtCompound outProps = new NbtCompound();
        for (Map.Entry<String, String> e : rotMap.entrySet()) {
            outProps.putString(e.getKey(), e.getValue());
        }
        stateTag.putCompound("Properties", outProps);
    }
}
