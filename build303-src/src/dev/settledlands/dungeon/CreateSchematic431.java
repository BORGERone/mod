package dev.settledlands.dungeon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;

/**
 * Build 431: pure-Java reader for Create ({@code Schematic & Quill}) and vanilla
 * {@code StructureTemplate} {@code .nbt} schematic files.
 *
 * <p>Decodes gzipped or raw NBT roots containing {@code size}, {@code palette} (or
 * {@code palettes[0]}), {@code blocks}, and {@code entities}, ignores {@code minecraft:air} and
 * {@code minecraft:structure_void}, detects {@code minecraft:lapis_block} / {@code redstone_block}
 * markers just like {@link AxiomBlueprint427}, and preserves full {@link DungeonNbt431.NbtCompound}
 * metadata so Create {@code BlockEntity} and {@code super_glue} entities rotate accurately.</p>
 */
public final class CreateSchematic431 {

    private CreateSchematic431() {}

    public static AxiomBlueprint427.PieceTemplate loadFile(Path path, String key) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        return parseBytes(raw, key);
    }

    public static AxiomBlueprint427.PieceTemplate parseBytes(byte[] raw, String key) throws IOException {
        DungeonNbt431.NbtCompound root = DungeonNbt431.readRootCompound(raw, key);
        return buildPieceFromStructure(key, root);
    }

    private static AxiomBlueprint427.PieceTemplate buildPieceFromStructure(
            String key, DungeonNbt431.NbtCompound root) throws IOException {
        List<DungeonNbt431.NbtValue> rawPalette = root.getList("palette");
        if (rawPalette.isEmpty()) {
            List<DungeonNbt431.NbtValue> palettes = root.getList("palettes");
            if (!palettes.isEmpty() && palettes.get(0) != null
                    && palettes.get(0).val instanceof List<?> firstPal) {
                rawPalette = new ArrayList<>(firstPal.size());
                for (Object o : firstPal) {
                    if (o instanceof DungeonNbt431.NbtValue nv) {
                        rawPalette.add(nv);
                    }
                }
            }
        }
        if (rawPalette.isEmpty()) {
            throw new IOException("Structure .nbt has no palette: " + key);
        }

        List<DungeonTemplate425.PaletteEntry> palette = new ArrayList<>();
        Map<String, Integer> palLookup = new LinkedHashMap<>();
        String[] palIds = new String[rawPalette.size()];
        int[] palRemap = new int[rawPalette.size()];

        for (int i = 0; i < rawPalette.size(); i++) {
            DungeonNbt431.NbtValue pv = rawPalette.get(i);
            if (pv == null || !(pv.val instanceof DungeonNbt431.NbtCompound st)) {
                palIds[i] = "minecraft:air";
                palRemap[i] = -1;
                continue;
            }
            String name = st.getString("Name");
            palIds[i] = name;
            if (name.isEmpty()
                    || "minecraft:air".equals(name)
                    || "minecraft:cave_air".equals(name)
                    || "minecraft:void_air".equals(name)
                    || "minecraft:structure_void".equals(name)) {
                palRemap[i] = -1;
                continue;
            }
            TreeMap<String, String> sortedProps = new TreeMap<>();
            DungeonNbt431.NbtCompound propsTag = st.getCompound("Properties");
            if (propsTag != null) {
                for (Map.Entry<String, DungeonNbt431.NbtValue> pe : propsTag.values.entrySet()) {
                    if (pe.getValue() != null && pe.getValue().val != null) {
                        sortedProps.put(pe.getKey(), String.valueOf(pe.getValue().val));
                    }
                }
            }
            String sig = paletteSig(name, sortedProps);
            Integer gIdx = palLookup.get(sig);
            if (gIdx == null) {
                gIdx = palette.size();
                palLookup.put(sig, gIdx);
                palette.add(new DungeonTemplate425.PaletteEntry(name, new LinkedHashMap<>(sortedProps)));
            }
            palRemap[i] = gIdx;
        }

        List<DungeonNbt431.NbtValue> rawBlocks = root.getList("blocks");
        List<Integer> flatBlocks = new ArrayList<>(rawBlocks.size() * 4);
        List<DungeonTemplate425.BlockEntitySpec> beSpecs = new ArrayList<>();

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        List<int[]> lapisPts = new ArrayList<>();
        List<int[]> redPts = new ArrayList<>();

        for (DungeonNbt431.NbtValue bItem : rawBlocks) {
            if (bItem == null || !(bItem.val instanceof DungeonNbt431.NbtCompound bTag)) continue;
            int[] pos = readIntVec3(bTag.values.get("pos"));
            if (pos == null) continue;
            int bx = pos[0], by = pos[1], bz = pos[2];

            int localState = bTag.getInt("state");
            if (localState < 0 || localState >= palRemap.length) continue;
            int gIdx = palRemap[localState];
            if (gIdx < 0) continue;

            flatBlocks.add(bx);
            flatBlocks.add(by);
            flatBlocks.add(bz);
            flatBlocks.add(gIdx);

            if (bx < minX) minX = bx;
            if (bx > maxX) maxX = bx;
            if (by < minY) minY = by;
            if (by > maxY) maxY = by;
            if (bz < minZ) minZ = bz;
            if (bz > maxZ) maxZ = bz;

            String blockName = palIds[localState];
            if ("minecraft:lapis_block".equals(blockName)) {
                lapisPts.add(new int[] { bx, by, bz });
            } else if ("minecraft:redstone_block".equals(blockName)) {
                redPts.add(new int[] { bx, by, bz });
            }

            DungeonNbt431.NbtCompound beTag = bTag.getCompound("nbt");
            if (beTag != null && !beTag.isEmpty()) {
                String beId = beTag.getString("id");
                if (beId.isEmpty()) {
                    beId = blockName;
                }
                DungeonNbt431.NbtCompound clean = beTag.withoutKeys("x", "y", "z");
                CompoundTag mcTag = DungeonNbt431.toMinecraftTag(clean);
                beSpecs.add(new DungeonTemplate425.BlockEntitySpec(beId, bx, by, bz, mcTag, clean));
            }
        }

        if (flatBlocks.isEmpty()) {
            throw new IOException("Structure .nbt contains zero non-air blocks: " + key);
        }

        int nBlocks = flatBlocks.size() / 4;
        int[] blockPhases = new int[palette.size()];
        for (int p = 0; p < palette.size(); p++) {
            blockPhases[p] = AxiomBlueprint427.placementPhase(palette.get(p).id);
        }
        List<int[]> phase0 = new ArrayList<>(nBlocks);
        List<int[]> phase1 = new ArrayList<>();
        List<int[]> phase2 = new ArrayList<>();
        List<int[]> phase3 = new ArrayList<>();
        for (int bi = 0; bi < nBlocks; bi++) {
            int off = bi * 4;
            int[] entry = new int[] {
                    flatBlocks.get(off), flatBlocks.get(off + 1),
                    flatBlocks.get(off + 2), flatBlocks.get(off + 3)
            };
            int ph = blockPhases[entry[3]];
            if (ph == 3) phase3.add(entry);
            else if (ph == 2) phase2.add(entry);
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
        phase2.sort(byYFirst);
        phase3.sort(byColumnBottomUp);

        int[] blocksArr = new int[flatBlocks.size()];
        int outPos = 0;
        for (int[] e : phase0) {
            blocksArr[outPos++] = e[0]; blocksArr[outPos++] = e[1];
            blocksArr[outPos++] = e[2]; blocksArr[outPos++] = e[3];
        }
        for (int[] e : phase1) {
            blocksArr[outPos++] = e[0]; blocksArr[outPos++] = e[1];
            blocksArr[outPos++] = e[2]; blocksArr[outPos++] = e[3];
        }
        for (int[] e : phase2) {
            blocksArr[outPos++] = e[0]; blocksArr[outPos++] = e[1];
            blocksArr[outPos++] = e[2]; blocksArr[outPos++] = e[3];
        }
        for (int[] e : phase3) {
            blocksArr[outPos++] = e[0]; blocksArr[outPos++] = e[1];
            blocksArr[outPos++] = e[2]; blocksArr[outPos++] = e[3];
        }

        List<DungeonTemplate425.EntitySpec> entSpecs = new ArrayList<>();
        for (DungeonNbt431.NbtValue entItem : root.getList("entities")) {
            if (entItem == null || !(entItem.val instanceof DungeonNbt431.NbtCompound entWrapper)) continue;
            DungeonNbt431.NbtCompound entNbt = entWrapper.getCompound("nbt");
            if (entNbt == null) {
                entNbt = new DungeonNbt431.NbtCompound();
            }
            String entId = entNbt.getString("id");
            if (entId.isEmpty()) {
                entId = entWrapper.getString("id");
            }
            if (entId.isEmpty()) continue;

            double[] pos = readDoubleVec3(entWrapper.getList("pos"));
            if (pos == null) {
                int[] bp = readIntVec3(entWrapper.values.get("blockPos"));
                if (bp != null) {
                    pos = new double[] { bp[0] + 0.5D, bp[1], bp[2] + 0.5D };
                } else {
                    pos = readDoubleVec3(entNbt.getList("Pos"));
                }
            }
            if (pos == null) {
                pos = new double[] { 0.0D, 0.0D, 0.0D };
            }

            List<DungeonNbt431.NbtValue> rotList = entNbt.getList("Rotation");
            float yaw = rotList.size() > 0 && rotList.get(0).val instanceof Number n ? n.floatValue() : 0.0F;
            float pitch = rotList.size() > 1 && rotList.get(1).val instanceof Number n ? n.floatValue() : 0.0F;

            DungeonNbt431.NbtCompound clean = entNbt.withoutKeys("UUID", "neoforge:attachments", "Pos");
            if (!clean.contains("id")) {
                clean.putString("id", entId);
            }
            CompoundTag mcTag = DungeonNbt431.toMinecraftTag(clean);
            entSpecs.add(new DungeonTemplate425.EntitySpec(
                    entId, pos[0], pos[1], pos[2], yaw, pitch, mcTag, clean));
        }

        int[] oldSchemaOrigin = DungeonNbt431.inferSchemaOrigin(beSpecs);

        AxiomBlueprint427.Marker entrance = lapisPts.size() == 1
                ? new AxiomBlueprint427.Marker(
                        lapisPts.get(0)[0], lapisPts.get(0)[1], lapisPts.get(0)[2],
                        AxiomBlueprint427.inferFaceDir(
                                lapisPts.get(0)[0], lapisPts.get(0)[1], lapisPts.get(0)[2],
                                minX, maxX, minZ, maxZ, flatBlocks))
                : null;
        if (lapisPts.size() > 1) {
            entrance = null;
        }
        List<AxiomBlueprint427.Marker> exits = new ArrayList<>(redPts.size());
        for (int[] rp : redPts) {
            exits.add(new AxiomBlueprint427.Marker(
                    rp[0], rp[1], rp[2],
                    AxiomBlueprint427.inferFaceDir(
                            rp[0], rp[1], rp[2], minX, maxX, minZ, maxZ, flatBlocks)));
        }

        DungeonTemplate425.StartTemplate content = new DungeonTemplate425.StartTemplate(
                palette, blocksArr, beSpecs, entSpecs, oldSchemaOrigin);
        String displayName = baseName(key);
        return new AxiomBlueprint427.PieceTemplate(key, displayName, Collections.emptyList(),
                minX, minY, minZ, maxX, maxY, maxZ, entrance, exits, content, false);
    }

    private static int[] readIntVec3(DungeonNbt431.NbtValue v) {
        if (v == null) return null;
        if (v.val instanceof int[] a && a.length >= 3) {
            return new int[] { a[0], a[1], a[2] };
        }
        if (v.val instanceof List<?> list && list.size() >= 3) {
            if (list.get(0) instanceof DungeonNbt431.NbtValue v0 && v0.val instanceof Number n0
                    && list.get(1) instanceof DungeonNbt431.NbtValue v1 && v1.val instanceof Number n1
                    && list.get(2) instanceof DungeonNbt431.NbtValue v2 && v2.val instanceof Number n2) {
                return new int[] { n0.intValue(), n1.intValue(), n2.intValue() };
            }
        }
        return null;
    }

    private static double[] readDoubleVec3(List<DungeonNbt431.NbtValue> list) {
        if (list == null || list.size() < 3) return null;
        if (list.get(0) != null && list.get(0).val instanceof Number n0
                && list.get(1) != null && list.get(1).val instanceof Number n1
                && list.get(2) != null && list.get(2).val instanceof Number n2) {
            return new double[] { n0.doubleValue(), n1.doubleValue(), n2.doubleValue() };
        }
        return null;
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

    private static String baseName(String key) {
        if (key == null) return "";
        int slash = key.lastIndexOf('/');
        return slash >= 0 ? key.substring(slash + 1) : key;
    }
}
