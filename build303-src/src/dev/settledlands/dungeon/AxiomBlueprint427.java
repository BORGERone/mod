package dev.settledlands.dungeon;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/**
 * Build 427: pure-Java native reader for Axiom {@code .bp} blueprint files.
 *
 * <p>Decodes the Axiom header ({@code 0x0AE5BB36} magic + length-prefixed header NBT with
 * {@code Name}/{@code Tags}/{@code Version}), skips the embedded PNG thumbnail, decompresses the
 * gzipped blueprint body ({@code BlockRegion}, {@code BlockEntities}, {@code Entities}), and
 * automatically detects chamber bounds, {@code minecraft:lapis_block} entrance markers, and
 * {@code minecraft:redstone_block} exit markers without requiring an external catalog file or
 * pre-packing into {@code .slbp}.</p>
 */
public final class AxiomBlueprint427 {

    public static final int AXIOM_MAGIC = 0x0AE5BB36;

    /** Cardinal directions in XZ plane: 0=North(-Z), 1=East(+X), 2=South(+Z), 3=West(-X). */
    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int SOUTH = 2;
    public static final int WEST = 3;

    public static final int[] DX = { 0, 1, 0, -1 };
    public static final int[] DZ = { -1, 0, 1, 0 };

    private AxiomBlueprint427() {}

    public static final class Marker {
        public final int x;
        public final int y;
        public final int z;
        public final int dir;

        public Marker(int x, int y, int z, int dir) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dir = dir & 3;
        }
    }

    public static final class PieceTemplate {
        public final String key;
        public final String displayName;
        public final List<String> tags;
        public final int minX;
        public final int minY;
        public final int minZ;
        public final int maxX;
        public final int maxY;
        public final int maxZ;
        public final Marker entrance;
        public final List<Marker> exits;
        public final DungeonTemplate425.StartTemplate content;
        public final boolean embeddedFallback;

        public PieceTemplate(
                String key,
                String displayName,
                List<String> tags,
                int minX, int minY, int minZ,
                int maxX, int maxY, int maxZ,
                Marker entrance,
                List<Marker> exits,
                DungeonTemplate425.StartTemplate content,
                boolean embeddedFallback) {
            this.key = key;
            this.displayName = displayName == null || displayName.isEmpty() ? key : displayName;
            this.tags = tags == null ? Collections.emptyList() : Collections.unmodifiableList(tags);
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.entrance = entrance;
            this.exits = exits == null ? Collections.emptyList() : Collections.unmodifiableList(exits);
            this.content = content;
            this.embeddedFallback = embeddedFallback;
        }

        public int blockCount() {
            return content == null ? 0 : content.blockCount();
        }

        public boolean hasTag(String tag) {
            if (tag == null || tag.isEmpty()) return false;
            String needle = tag.toLowerCase(Locale.ROOT);
            for (String t : tags) {
                if (needle.equals(t)) return true;
            }
            return false;
        }

        public boolean isDeadEnd() {
            return exits.isEmpty();
        }
    }

    public static PieceTemplate fromStartTemplate(String key, DungeonTemplate425.StartTemplate tpl,
            boolean embeddedFallback) {
        if (tpl == null || tpl.blockCount() == 0) {
            throw new IllegalArgumentException("Empty start template: " + key);
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        List<int[]> lapisPts = new ArrayList<>();
        List<int[]> redPts = new ArrayList<>();
        int[] raw = tpl.blocks;
        for (int i = 0; i + 3 < raw.length; i += 4) {
            int x = raw[i];
            int y = raw[i + 1];
            int z = raw[i + 2];
            int pi = raw[i + 3];
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
            if (pi >= 0 && pi < tpl.palette.size()) {
                String id = tpl.palette.get(pi).id;
                if ("minecraft:lapis_block".equals(id)) {
                    lapisPts.add(new int[] { x, y, z });
                } else if ("minecraft:redstone_block".equals(id)) {
                    redPts.add(new int[] { x, y, z });
                }
            }
        }
        Marker entrance = lapisPts.isEmpty()
                ? null
                : new Marker(lapisPts.get(0)[0], lapisPts.get(0)[1], lapisPts.get(0)[2],
                        inferFaceDir(lapisPts.get(0)[0], lapisPts.get(0)[2], minX, maxX, minZ, maxZ));
        List<Marker> exits = new ArrayList<>(redPts.size());
        for (int[] rp : redPts) {
            exits.add(new Marker(rp[0], rp[1], rp[2],
                    inferFaceDir(rp[0], rp[2], minX, maxX, minZ, maxZ)));
        }
        return new PieceTemplate(key, key, Collections.emptyList(),
                minX, minY, minZ, maxX, maxY, maxZ, entrance, exits, tpl, embeddedFallback);
    }

    public static PieceTemplate loadFile(Path path, String key) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        return parseBytes(raw, key);
    }

    public static PieceTemplate parseBytes(byte[] raw, String key) throws IOException {
        if (raw == null || raw.length < 16) {
            throw new IOException("Blueprint file too short: " + key);
        }

        int posAfterHeader = -1;
        NbtCompound header = null;

        int magic = readIntBE(raw, 0);
        if (magic == AXIOM_MAGIC) {
            int hdrLen = readIntBE(raw, 4);
            if (hdrLen > 2 && 8 + hdrLen <= raw.length) {
                try {
                    ByteArrayInputStream hin = new ByteArrayInputStream(raw, 8, hdrLen);
                    NbtReader r = new NbtReader(hin);
                    if (r.readRootType() == 10) {
                        r.readString();
                        header = r.readCompound();
                        posAfterHeader = 8 + hdrLen;
                    }
                } catch (IOException ignored) {
                    header = null;
                }
            }
        }

        if (header == null) {
            for (int off = 0; off <= Math.min(24, raw.length - 4); off++) {
                if ((raw[off] & 0xFF) != 10) continue;
                try {
                    CountingInputStream cin = new CountingInputStream(raw, off);
                    NbtReader r = new NbtReader(cin);
                    r.readRootType();
                    r.readString();
                    NbtCompound cand = r.readCompound();
                    if (cand.values.containsKey("Version")) {
                        header = cand;
                        posAfterHeader = off + cin.bytesRead;
                        break;
                    }
                } catch (IOException ignored) {
                }
            }
        }

        if (header == null || posAfterHeader < 0 || posAfterHeader + 8 > raw.length) {
            throw new IOException("No valid Axiom NBT header in " + key);
        }

        String displayName = header.getString("Name");
        List<String> tags = new ArrayList<>();
        for (NbtValue item : header.getList("Tags")) {
            if (item != null && item.val instanceof String s && !s.isEmpty()) {
                tags.add(s.toLowerCase(Locale.ROOT));
            }
        }

        int pngLen = readIntBE(raw, posAfterHeader);
        int blobHeaderPos = posAfterHeader + 4 + pngLen;
        if (pngLen < 0 || blobHeaderPos + 4 > raw.length) {
            throw new IOException("Invalid PNG length in Axiom blueprint " + key);
        }
        int blobLen = readIntBE(raw, blobHeaderPos);
        int blobStart = blobHeaderPos + 4;
        if (blobLen <= 0 || blobStart + blobLen > raw.length) {
            throw new IOException("Invalid gzip blob length in Axiom blueprint " + key);
        }

        NbtCompound body;
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(raw, blobStart, blobLen))) {
            NbtReader r = new NbtReader(gz);
            if (r.readRootType() != 10) {
                throw new IOException("Blueprint body root is not TAG_Compound: " + key);
            }
            r.readString();
            body = r.readCompound();
        }

        return buildPieceFromBody(key, displayName, tags, body);
    }

    private static PieceTemplate buildPieceFromBody(String key, String displayName,
            List<String> tags, NbtCompound body) throws IOException {
        List<DungeonTemplate425.PaletteEntry> palette = new ArrayList<>();
        Map<String, Integer> palLookup = new LinkedHashMap<>();
        List<Integer> flatBlocks = new ArrayList<>(4096);

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        List<int[]> lapisPts = new ArrayList<>();
        List<int[]> redPts = new ArrayList<>();

        for (NbtValue regItem : body.getList("BlockRegion")) {
            if (regItem == null || !(regItem.val instanceof NbtCompound reg)) continue;
            int rx = reg.getInt("X") * 16;
            int ry = reg.getInt("Y") * 16;
            int rz = reg.getInt("Z") * 16;
            NbtCompound bs = reg.getCompound("BlockStates");
            if (bs == null) continue;
            List<NbtValue> rawPal = bs.getList("palette");
            if (rawPal.isEmpty()) continue;

            // Pre-decode this section's palette entries once per section
            String[] secIds = new String[rawPal.size()];
            int[] globalPalIdx = new int[rawPal.size()];
            for (int p = 0; p < rawPal.size(); p++) {
                NbtValue palItem = rawPal.get(p);
                if (palItem == null || !(palItem.val instanceof NbtCompound st)) {
                    secIds[p] = "minecraft:air";
                    globalPalIdx[p] = -1;
                    continue;
                }
                String name = st.getString("Name");
                secIds[p] = name;
                if (name.isEmpty() || "minecraft:air".equals(name) || "minecraft:void_air".equals(name)) {
                    globalPalIdx[p] = -1;
                    continue;
                }
                TreeMap<String, String> sortedProps = new TreeMap<>();
                NbtCompound propsTag = st.getCompound("Properties");
                if (propsTag != null) {
                    for (Map.Entry<String, NbtValue> pe : propsTag.values.entrySet()) {
                        sortedProps.put(pe.getKey(), String.valueOf(pe.getValue().val));
                    }
                }
                String sig = paletteSig(name, sortedProps);
                Integer idx = palLookup.get(sig);
                if (idx == null) {
                    idx = palette.size();
                    palLookup.put(sig, idx);
                    palette.add(new DungeonTemplate425.PaletteEntry(name, new LinkedHashMap<>(sortedProps)));
                }
                globalPalIdx[p] = idx;
            }

            long[] data = bs.getLongArray("data");
            boolean uniform = rawPal.size() <= 1 || data.length == 0;
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(Math.max(2, rawPal.size()) - 1));
            int per = 64 / bits;
            long mask = (1L << bits) - 1L;

            for (int i = 0; i < 4096; i++) {
                int localIdx;
                if (uniform) {
                    localIdx = 0;
                } else {
                    int w = i / per;
                    if (w >= data.length) break;
                    int shift = (i % per) * bits;
                    localIdx = (int) ((data[w] >>> shift) & mask);
                }
                if (localIdx < 0 || localIdx >= globalPalIdx.length) continue;
                int gIdx = globalPalIdx[localIdx];
                if (gIdx < 0) continue;

                int ly = i >> 8;
                int lz = (i >> 4) & 15;
                int lx = i & 15;
                int wx = rx + lx;
                int wy = ry + ly;
                int wz = rz + lz;

                flatBlocks.add(wx);
                flatBlocks.add(wy);
                flatBlocks.add(wz);
                flatBlocks.add(gIdx);

                if (wx < minX) minX = wx;
                if (wx > maxX) maxX = wx;
                if (wy < minY) minY = wy;
                if (wy > maxY) maxY = wy;
                if (wz < minZ) minZ = wz;
                if (wz > maxZ) maxZ = wz;

                String blockName = secIds[localIdx];
                if ("minecraft:lapis_block".equals(blockName)) {
                    lapisPts.add(new int[] { wx, wy, wz });
                } else if ("minecraft:redstone_block".equals(blockName)) {
                    redPts.add(new int[] { wx, wy, wz });
                }
            }
        }

        if (flatBlocks.isEmpty()) {
            throw new IOException("Blueprint contains zero non-air blocks: " + key);
        }

        // Build 430: Order blocks in 3 safe phases so floors/frames (phase 0) exist before fragile
        // wall/ceiling attachments (phase 1) and multi-block doors such as valhelsia_structures:dungeon_door
        // (phase 2, sorted by column (x, z, y) so LOWER always immediately precedes UPPER).
        int nBlocks = flatBlocks.size() / 4;
        int[] blockPhases = new int[palette.size()];
        for (int p = 0; p < palette.size(); p++) {
            blockPhases[p] = placementPhase(palette.get(p).id);
        }
        List<int[]> phase0 = new ArrayList<>(nBlocks);
        List<int[]> phase1 = new ArrayList<>();
        List<int[]> phase2 = new ArrayList<>();
        for (int bi = 0; bi < nBlocks; bi++) {
            int off = bi * 4;
            int wx = flatBlocks.get(off);
            int wy = flatBlocks.get(off + 1);
            int wz = flatBlocks.get(off + 2);
            int gIdx = flatBlocks.get(off + 3);
            int ph = blockPhases[gIdx];
            int[] entry = new int[] { wx, wy, wz, gIdx };
            if (ph == 2) phase2.add(entry);
            else if (ph == 1) phase1.add(entry);
            else phase0.add(entry);
        }
        Comparator<int[]> byYFirst = (a, b) -> {
            int c = Integer.compare(a[1], b[1]);
            if (c != 0) return c;
            c = Integer.compare(a[2], b[2]);
            return c != 0 ? c : Integer.compare(a[0], b[0]);
        };
        Comparator<int[]> byColumnBottomUp = (a, b) -> {
            int c = Integer.compare(a[0], b[0]);
            if (c != 0) return c;
            c = Integer.compare(a[2], b[2]);
            return c != 0 ? c : Integer.compare(a[1], b[1]);
        };
        phase0.sort(byYFirst);
        phase1.sort(byYFirst);
        phase2.sort(byColumnBottomUp);

        int[] blocksArr = new int[flatBlocks.size()];
        int outPos = 0;
        for (int[] e : phase0) {
            blocksArr[outPos++] = e[0];
            blocksArr[outPos++] = e[1];
            blocksArr[outPos++] = e[2];
            blocksArr[outPos++] = e[3];
        }
        for (int[] e : phase1) {
            blocksArr[outPos++] = e[0];
            blocksArr[outPos++] = e[1];
            blocksArr[outPos++] = e[2];
            blocksArr[outPos++] = e[3];
        }
        for (int[] e : phase2) {
            blocksArr[outPos++] = e[0];
            blocksArr[outPos++] = e[1];
            blocksArr[outPos++] = e[2];
            blocksArr[outPos++] = e[3];
        }

        List<DungeonTemplate425.BlockEntitySpec> beSpecs = new ArrayList<>();
        for (NbtValue beItem : body.getList("BlockEntities")) {
            if (beItem == null || !(beItem.val instanceof NbtCompound be)) continue;
            String beId = be.getString("id");
            int bx = be.getInt("x");
            int by = be.getInt("y");
            int bz = be.getInt("z");
            NbtCompound clean = be.withoutKeys("x", "y", "z");
            CompoundTag mcTag = toMinecraftTag(clean);
            beSpecs.add(new DungeonTemplate425.BlockEntitySpec(beId, bx, by, bz, mcTag));
        }

        List<DungeonTemplate425.EntitySpec> entSpecs = new ArrayList<>();
        for (NbtValue entItem : body.getList("Entities")) {
            if (entItem == null || !(entItem.val instanceof NbtCompound ent)) continue;
            String entId = ent.getString("id");
            List<NbtValue> posList = ent.getList("Pos");
            double dx = posList.size() > 0 && posList.get(0).val instanceof Number n ? n.doubleValue() : 0.0D;
            double dy = posList.size() > 1 && posList.get(1).val instanceof Number n ? n.doubleValue() : 0.0D;
            double dz = posList.size() > 2 && posList.get(2).val instanceof Number n ? n.doubleValue() : 0.0D;
            List<NbtValue> rotList = ent.getList("Rotation");
            float yaw = rotList.size() > 0 && rotList.get(0).val instanceof Number n ? n.floatValue() : 0.0F;
            float pitch = rotList.size() > 1 && rotList.get(1).val instanceof Number n ? n.floatValue() : 0.0F;
            NbtCompound clean = ent.withoutKeys("UUID", "neoforge:attachments", "Pos");
            CompoundTag mcTag = toMinecraftTag(clean);
            entSpecs.add(new DungeonTemplate425.EntitySpec(entId, dx, dy, dz, yaw, pitch, mcTag));
        }

        Marker entrance = lapisPts.size() == 1
                ? new Marker(lapisPts.get(0)[0], lapisPts.get(0)[1], lapisPts.get(0)[2],
                        inferFaceDir(lapisPts.get(0)[0], lapisPts.get(0)[1], lapisPts.get(0)[2],
                                minX, maxX, minZ, maxZ, flatBlocks))
                : null;
        if (lapisPts.size() > 1) {
            // Multiple entrances are invalid for regular pieces; leave entrance null so loader skips it safely.
            entrance = null;
        }
        List<Marker> exits = new ArrayList<>(redPts.size());
        for (int[] rp : redPts) {
            exits.add(new Marker(rp[0], rp[1], rp[2],
                    inferFaceDir(rp[0], rp[1], rp[2], minX, maxX, minZ, maxZ, flatBlocks)));
        }

        DungeonTemplate425.StartTemplate content = new DungeonTemplate425.StartTemplate(
                palette, blocksArr, beSpecs, entSpecs);
        return new PieceTemplate(key, displayName, tags,
                minX, minY, minZ, maxX, maxY, maxZ, entrance, exits, content, false);
    }

    private static int placementPhase(String blockId) {
        if (blockId == null || blockId.isEmpty()) return 0;
        String low = blockId.toLowerCase(Locale.ROOT);
        if (isDoorLike(low)) return 2;
        if ((low.contains("lantern") && !low.contains("sea_lantern") && !low.contains("jack_o_lantern"))
                || low.contains("torch")
                || low.contains("candle")
                || low.contains("campfire")
                || low.contains("button")
                || low.contains("lever")
                || low.contains("pressure_plate")
                || low.contains("_sign")
                || low.contains("banner")
                || low.contains("carpet")
                || low.contains("rail")
                || low.contains("ladder")
                || low.contains("vine")
                || low.contains("skull")
                || low.contains("_head")
                || low.contains("tripwire")
                || low.contains("redstone_wire")
                || low.contains("repeater")
                || low.contains("comparator")
                || low.contains("mushroom")
                || low.contains("fungus")
                || low.contains("roots")
                || low.contains("sprouts")
                || low.contains("wart")
                || low.contains("flower")
                || low.contains("potted_")
                || low.contains("coral_fan")
                || low.contains("moss_carpet")
                || low.contains("fern")
                || low.contains("sapling")
                || low.contains("kelp")
                || low.contains("dripstone")
                || low.contains("amethyst_cluster")
                || low.contains("amethyst_bud")
                || low.contains("scaffolding")
                || low.contains("flower_pot")
                || low.contains("cake")
                || low.contains("brewing_stand")
                || low.endsWith("_bed")
                || low.contains(":bell")) {
            return 1;
        }
        return 0;
    }

    public static boolean isDoorLike(String blockId) {
        if (blockId == null) return false;
        String low = blockId.toLowerCase(Locale.ROOT);
        return low.contains("door") && !low.contains("trapdoor");
    }

    public static int inferFaceDir(int x, int z, int minX, int maxX, int minZ, int maxZ) {
        return inferFaceDir(x, 0, z, minX, maxX, minZ, maxZ, null);
    }

    public static int inferFaceDir(int x, int y, int z, int minX, int maxX, int minZ, int maxZ,
            List<Integer> flatBlocks) {
        boolean onN = (z == minZ);
        boolean onS = (z == maxZ);
        boolean onW = (x == minX);
        boolean onE = (x == maxX);
        int count = (onN ? 1 : 0) + (onS ? 1 : 0) + (onW ? 1 : 0) + (onE ? 1 : 0);
        if (count == 1) {
            if (onN) return NORTH;
            if (onE) return EAST;
            if (onS) return SOUTH;
            return WEST;
        }
        if (flatBlocks != null && !flatBlocks.isEmpty()) {
            int[] score = new int[4];
            for (int i = 0; i + 3 < flatBlocks.size(); i += 4) {
                int bx = flatBlocks.get(i);
                int by = flatBlocks.get(i + 1);
                int bz = flatBlocks.get(i + 2);
                if (by < y || by > y + 6) continue;
                int dx = bx - x;
                int dz = bz - z;
                if (Math.abs(dx) <= 4 && Math.abs(dz) <= 4) {
                    if (dx < 0 && Math.abs(dz) <= 1) score[EAST] += (by == y + 1 ? 10 : 2);
                    if (dx > 0 && Math.abs(dz) <= 1) score[WEST] += (by == y + 1 ? 10 : 2);
                    if (dz < 0 && Math.abs(dx) <= 1) score[SOUTH] += (by == y + 1 ? 10 : 2);
                    if (dz > 0 && Math.abs(dx) <= 1) score[NORTH] += (by == y + 1 ? 10 : 2);
                }
            }
            int bestD = -1;
            int bestVal = 0;
            boolean tie = false;
            for (int d = 0; d < 4; d++) {
                if (score[d] > bestVal) {
                    bestVal = score[d];
                    bestD = d;
                    tie = false;
                } else if (score[d] == bestVal && bestVal > 0) {
                    tie = true;
                }
            }
            if (bestD >= 0 && !tie) {
                return bestD;
            }
        }
        int dN = Math.abs(z - minZ);
        int dS = Math.abs(maxZ - z);
        int dW = Math.abs(x - minX);
        int dE = Math.abs(maxX - x);
        int best = NORTH;
        int bestDist = dN;
        if (dE < bestDist) { bestDist = dE; best = EAST; }
        if (dS < bestDist) { bestDist = dS; best = SOUTH; }
        if (dW < bestDist) { best = WEST; }
        return best;
    }

    private static String paletteSig(String name, Map<String, String> props) {
        if (props.isEmpty()) return name;
        StringBuilder sb = new StringBuilder(name);
        sb.append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : props.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        sb.append(']');
        return sb.toString();
    }

    private static int readIntBE(byte[] buf, int off) {
        return ((buf[off] & 0xFF) << 24)
                | ((buf[off + 1] & 0xFF) << 16)
                | ((buf[off + 2] & 0xFF) << 8)
                | (buf[off + 3] & 0xFF);
    }

    /**
     * Converts an internal NBT compound into a genuine Minecraft {@link CompoundTag} through the
     * already-verified {@link NbtIo#readCompressed} path, avoiding any unverified MC NBT methods.
     */
    private static CompoundTag toMinecraftTag(NbtCompound compound) throws IOException {
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

    // ---- lightweight internal NBT codec ----

    private static final class NbtValue {
        final int type;
        final int listElemType;
        final Object val;

        NbtValue(int type, int listElemType, Object val) {
            this.type = type;
            this.listElemType = listElemType;
            this.val = val;
        }
    }

    private static final class NbtCompound {
        final Map<String, NbtValue> values = new LinkedHashMap<>();

        String getString(String key) {
            NbtValue v = values.get(key);
            return (v != null && v.val instanceof String s) ? s : "";
        }

        int getInt(String key) {
            NbtValue v = values.get(key);
            return (v != null && v.val instanceof Number n) ? n.intValue() : 0;
        }

        NbtCompound getCompound(String key) {
            NbtValue v = values.get(key);
            return (v != null && v.val instanceof NbtCompound c) ? c : null;
        }

        @SuppressWarnings("unchecked")
        List<NbtValue> getList(String key) {
            NbtValue v = values.get(key);
            return (v != null && v.val instanceof List<?> l) ? (List<NbtValue>) l : Collections.emptyList();
        }

        long[] getLongArray(String key) {
            NbtValue v = values.get(key);
            return (v != null && v.val instanceof long[] a) ? a : new long[0];
        }

        NbtCompound withoutKeys(String... drop) {
            NbtCompound copy = new NbtCompound();
            for (Map.Entry<String, NbtValue> e : values.entrySet()) {
                boolean skip = false;
                for (String d : drop) {
                    if (d.equals(e.getKey())) {
                        skip = true;
                        break;
                    }
                }
                if (!skip) copy.values.put(e.getKey(), e.getValue());
            }
            return copy;
        }
    }

    private static final class CountingInputStream extends ByteArrayInputStream {
        int bytesRead = 0;

        CountingInputStream(byte[] buf, int off) {
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

    private static final class NbtReader {
        private final DataInputStream in;

        NbtReader(java.io.InputStream stream) {
            this.in = new DataInputStream(stream);
        }

        int readRootType() throws IOException {
            return in.readUnsignedByte();
        }

        String readString() throws IOException {
            int len = in.readUnsignedShort();
            byte[] b = in.readNBytes(len);
            if (b.length != len) throw new EOFException("Truncated NBT string");
            return new String(b, StandardCharsets.UTF_8);
        }

        NbtCompound readCompound() throws IOException {
            NbtCompound c = new NbtCompound();
            while (true) {
                int type = in.readUnsignedByte();
                if (type == 0) return c;
                String key = readString();
                c.values.put(key, readValue(type));
            }
        }

        NbtValue readValue(int type) throws IOException {
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

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(raw.length);
        out.write(raw);
    }

    private static void writeCompound(DataOutputStream out, NbtCompound c) throws IOException {
        for (Map.Entry<String, NbtValue> e : c.values.entrySet()) {
            NbtValue v = e.getValue();
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
}
