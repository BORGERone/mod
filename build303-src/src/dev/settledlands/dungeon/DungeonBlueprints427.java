package dev.settledlands.dungeon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 427 / 429 / 431: scans an external schematic folder ({@code *.bp} and {@code *.nbt}),
 * parses each file natively, classifies rooms into themes ({@code crypt} / «к»,
 * {@code infernal} / «а», {@code flesh} / «п», etc.), and indexes pieces for biome-layered
 * procedural dungeon generation.
 */
public final class DungeonBlueprints427 {

    private static final Logger LOG = LoggerFactory.getLogger("settledlands");

    public static final String THEME_CRYPT = "crypt";
    public static final String THEME_INFERNAL = "infernal";
    public static final String THEME_FLESH = "flesh";
    public static final String THEME_SEWER = "sewer";
    public static final String THEME_COVE = "cove";
    public static final String THEME_OTHER = "other";

    private static volatile Library cachedLibrary;

    private DungeonBlueprints427() {}

    public static final class Library {
        public final DungeonConfig427 config;
        public final Path sourceDir;
        public final AxiomBlueprint427.PieceTemplate startPiece;
        public final List<AxiomBlueprint427.PieceTemplate> pieces;
        public final List<AxiomBlueprint427.PieceTemplate> connectors;
        public final List<AxiomBlueprint427.PieceTemplate> deadEnds;
        public final Map<String, AxiomBlueprint427.PieceTemplate> byKey;
        public final int totalBpFiles;
        public final int nbtCount;
        public final int nbtInPoolCount;
        public final int nbtFilteredCount;
        public final int nbtSkippedNoLapisCount;
        public final int unusedNbtInOtherDirs;
        public final String unusedNbtOtherDirHint;
        public final int skippedNoMarkers;
        public final int failedParse;
        public final String appliedFilter;
        public final boolean biomeProgression;
        public final Map<String, List<AxiomBlueprint427.PieceTemplate>> connectorsByTheme;
        public final Map<String, List<AxiomBlueprint427.PieceTemplate>> deadEndsByTheme;
        public final int cryptCount;
        public final int infernalCount;
        public final int fleshCount;

        public Library(
                DungeonConfig427 config,
                Path sourceDir,
                AxiomBlueprint427.PieceTemplate startPiece,
                List<AxiomBlueprint427.PieceTemplate> pieces,
                Map<String, AxiomBlueprint427.PieceTemplate> byKey,
                int totalBpFiles,
                int skippedNoMarkers,
                int failedParse,
                String appliedFilter) {
            this(config, sourceDir, startPiece, pieces, byKey, totalBpFiles, skippedNoMarkers,
                    failedParse, appliedFilter, inferBiomeMode(appliedFilter), 0);
        }

        public Library(
                DungeonConfig427 config,
                Path sourceDir,
                AxiomBlueprint427.PieceTemplate startPiece,
                List<AxiomBlueprint427.PieceTemplate> pieces,
                Map<String, AxiomBlueprint427.PieceTemplate> byKey,
                int totalBpFiles,
                int skippedNoMarkers,
                int failedParse,
                String appliedFilter,
                boolean biomeProgression) {
            this(config, sourceDir, startPiece, pieces, byKey, totalBpFiles, skippedNoMarkers,
                    failedParse, appliedFilter, biomeProgression, 0);
        }

        public Library(
                DungeonConfig427 config,
                Path sourceDir,
                AxiomBlueprint427.PieceTemplate startPiece,
                List<AxiomBlueprint427.PieceTemplate> pieces,
                Map<String, AxiomBlueprint427.PieceTemplate> byKey,
                int totalBpFiles,
                int skippedNoMarkers,
                int failedParse,
                String appliedFilter,
                boolean biomeProgression,
                int nbtCount) {
            this(config, sourceDir, startPiece, pieces, byKey, totalBpFiles, skippedNoMarkers,
                    failedParse, appliedFilter, biomeProgression, nbtCount, nbtCount, 0, 0, 0, "");
        }

        public Library(
                DungeonConfig427 config,
                Path sourceDir,
                AxiomBlueprint427.PieceTemplate startPiece,
                List<AxiomBlueprint427.PieceTemplate> pieces,
                Map<String, AxiomBlueprint427.PieceTemplate> byKey,
                int totalBpFiles,
                int skippedNoMarkers,
                int failedParse,
                String appliedFilter,
                boolean biomeProgression,
                int nbtCount,
                int nbtInPoolCount,
                int nbtFilteredCount,
                int nbtSkippedNoLapisCount,
                int unusedNbtInOtherDirs,
                String unusedNbtOtherDirHint) {
            this.config = config;
            this.sourceDir = sourceDir;
            this.startPiece = startPiece;
            this.pieces = Collections.unmodifiableList(pieces);

            List<AxiomBlueprint427.PieceTemplate> conn = new ArrayList<>();
            List<AxiomBlueprint427.PieceTemplate> de = new ArrayList<>();
            Map<String, List<AxiomBlueprint427.PieceTemplate>> connByT = new LinkedHashMap<>();
            Map<String, List<AxiomBlueprint427.PieceTemplate>> deByT = new LinkedHashMap<>();
            int cCrypt = 0;
            int cInfernal = 0;
            int cFlesh = 0;

            for (AxiomBlueprint427.PieceTemplate p : pieces) {
                String theme = themeOf(p);
                if (THEME_CRYPT.equals(theme)) cCrypt++;
                else if (THEME_INFERNAL.equals(theme)) cInfernal++;
                else if (THEME_FLESH.equals(theme)) cFlesh++;

                if (p.isDeadEnd()) {
                    de.add(p);
                    deByT.computeIfAbsent(theme, k -> new ArrayList<>()).add(p);
                } else {
                    conn.add(p);
                    connByT.computeIfAbsent(theme, k -> new ArrayList<>()).add(p);
                }
            }
            this.connectors = Collections.unmodifiableList(conn);
            this.deadEnds = Collections.unmodifiableList(de);
            this.connectorsByTheme = freezeBuckets(connByT);
            this.deadEndsByTheme = freezeBuckets(deByT);
            this.cryptCount = cCrypt;
            this.infernalCount = cInfernal;
            this.fleshCount = cFlesh;
            this.byKey = Collections.unmodifiableMap(byKey);
            this.totalBpFiles = totalBpFiles;
            this.nbtCount = nbtCount;
            this.nbtInPoolCount = nbtInPoolCount;
            this.nbtFilteredCount = nbtFilteredCount;
            this.nbtSkippedNoLapisCount = nbtSkippedNoLapisCount;
            this.unusedNbtInOtherDirs = unusedNbtInOtherDirs;
            this.unusedNbtOtherDirHint = unusedNbtOtherDirHint == null ? "" : unusedNbtOtherDirHint;
            this.skippedNoMarkers = skippedNoMarkers;
            this.failedParse = failedParse;
            this.appliedFilter = appliedFilter;
            this.biomeProgression = biomeProgression
                    && cCrypt > 0 && (cInfernal > 0 || cFlesh > 0);
        }

        private static Map<String, List<AxiomBlueprint427.PieceTemplate>> freezeBuckets(
                Map<String, List<AxiomBlueprint427.PieceTemplate>> src) {
            Map<String, List<AxiomBlueprint427.PieceTemplate>> out = new LinkedHashMap<>();
            for (Map.Entry<String, List<AxiomBlueprint427.PieceTemplate>> e : src.entrySet()) {
                out.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
            }
            return Collections.unmodifiableMap(out);
        }

        private static boolean inferBiomeMode(String appliedFilter) {
            return appliedFilter != null && appliedFilter.contains("biomes");
        }

        public List<AxiomBlueprint427.PieceTemplate> connectorsForTheme(String theme) {
            List<AxiomBlueprint427.PieceTemplate> themed = connectorsByTheme.getOrDefault(theme, Collections.emptyList());
            List<AxiomBlueprint427.PieceTemplate> otherNbt = connectorsByTheme.getOrDefault(THEME_OTHER, Collections.emptyList());
            if (biomeProgression && !otherNbt.isEmpty()
                    && (THEME_INFERNAL.equals(theme) || THEME_FLESH.equals(theme)
                            || (THEME_CRYPT.equals(theme) && infernalCount == 0 && fleshCount == 0))) {
                if (themed.isEmpty()) return otherNbt;
                List<AxiomBlueprint427.PieceTemplate> merged = new ArrayList<>(themed.size() + otherNbt.size());
                merged.addAll(themed);
                merged.addAll(otherNbt);
                return merged;
            }
            return themed;
        }

        public List<AxiomBlueprint427.PieceTemplate> deadEndsForTheme(String theme) {
            List<AxiomBlueprint427.PieceTemplate> themed = deadEndsByTheme.getOrDefault(theme, Collections.emptyList());
            List<AxiomBlueprint427.PieceTemplate> otherNbt = deadEndsByTheme.getOrDefault(THEME_OTHER, Collections.emptyList());
            if (biomeProgression && !otherNbt.isEmpty()
                    && (THEME_INFERNAL.equals(theme) || THEME_FLESH.equals(theme)
                            || (THEME_CRYPT.equals(theme) && infernalCount == 0 && fleshCount == 0))) {
                if (themed.isEmpty()) return otherNbt;
                List<AxiomBlueprint427.PieceTemplate> merged = new ArrayList<>(themed.size() + otherNbt.size());
                merged.addAll(themed);
                merged.addAll(otherNbt);
                return merged;
            }
            return themed;
        }

        public boolean hasTheme(String theme) {
            return !connectorsForTheme(theme).isEmpty();
        }

        public AxiomBlueprint427.PieceTemplate resolvePiece(String key) {
            if (key == null) return null;
            AxiomBlueprint427.PieceTemplate found = byKey.get(key);
            if (found != null) return found;
            if (startPiece != null && key.equals(startPiece.key)) return startPiece;
            if ("__embedded_start__".equals(key)) {
                return AxiomBlueprint427.fromStartTemplate(
                        "__embedded_start__", DungeonTemplate425.loadStart(), true);
            }
            return null;
        }

        public boolean hasExternalPieces() {
            return !pieces.isEmpty();
        }

        public String summaryLine() {
            String dirLabel = sourceDir == null ? "<none>" : sourceDir.toString();
            String counts = biomeProgression
                    ? pieces.size() + " [к:" + cryptCount + ", а:" + infernalCount + ", п:" + fleshCount + "]"
                    : Integer.toString(pieces.size());
            String nbtPart = "";
            if (nbtCount > 0 || nbtSkippedNoLapisCount > 0) {
                if (nbtInPoolCount == nbtCount && nbtSkippedNoLapisCount == 0) {
                    nbtPart = " (.nbt:" + nbtCount + ")";
                } else {
                    nbtPart = " (.nbt: в пуле " + nbtInPoolCount + "/" + nbtCount
                            + (nbtFilteredCount > 0 ? ", отфильтровано:" + nbtFilteredCount : "")
                            + (nbtSkippedNoLapisCount > 0 ? ", без 1 lapis:" + nbtSkippedNoLapisCount : "")
                            + ")";
                }
            }
            return "папка: " + dirLabel
                    + ", фильтр: " + appliedFilter
                    + ", комнат: " + counts
                    + nbtPart
                    + " (тупиков: " + deadEnds.size() + ")"
                    + ", старт: " + (startPiece == null ? "нет"
                            : (startPiece.embeddedFallback ? "встроенный start.slbp" : startPiece.key));
        }
    }

    public static Library get() {
        Library lib = cachedLibrary;
        if (lib != null) return lib;
        synchronized (DungeonBlueprints427.class) {
            if (cachedLibrary == null) {
                cachedLibrary = load(DungeonConfig427.loadOrCreate());
            }
            return cachedLibrary;
        }
    }

    public static synchronized Library reload() {
        cachedLibrary = load(DungeonConfig427.loadOrCreate());
        return cachedLibrary;
    }

    public static synchronized Library reload(Path baseDir) {
        cachedLibrary = load(DungeonConfig427.loadOrCreate(baseDir));
        return cachedLibrary;
    }

    public static Library load(DungeonConfig427 cfg) {
        DungeonConfig427 config = cfg == null ? DungeonConfig427.defaults(null) : cfg;
        List<Path> candidates = config.candidateBlueprintDirs();

        Path chosenDir = null;
        List<Path> chosenFiles = Collections.emptyList();

        for (Path dir : candidates) {
            List<Path> found = scanBpFiles(dir);
            if (!found.isEmpty()) {
                // In auto mode, pick first candidate folder that actually contains at least one
                // dungeon blueprint or schematic (either start piece or a piece with a lapis marker).
                if (!config.isAutoDir() || hasAnyUsableBlueprint(dir, found, config.startName)) {
                    chosenDir = dir;
                    chosenFiles = found;
                    break;
                }
            }
        }
        if (chosenDir == null && !candidates.isEmpty()) {
            chosenDir = candidates.get(0);
        }

        AxiomBlueprint427.PieceTemplate startPiece = null;
        boolean startIsNbt = false;
        Map<String, AxiomBlueprint427.PieceTemplate> poolByKey = new LinkedHashMap<>();
        int skippedNoMarkers = 0;
        int failedParse = 0;
        int nbtCount = 0;
        int nbtSkippedNoLapisCount = 0;
        int replacedBpByNbt = 0;

        for (Path p : chosenFiles) {
            String rel = chosenDir.relativize(p).toString().replace('\\', '/');
            boolean isNbt = rel.toLowerCase(Locale.ROOT).endsWith(".nbt");
            String baseKey = stripBlueprintExt(rel);
            try {
                AxiomBlueprint427.PieceTemplate pt = AxiomBlueprint427.loadFile(p, baseKey);
                if (isNbt) nbtCount++;
                if (isStartMatch(baseKey, pt, config.startName)) {
                    if (startPiece == null) {
                        startPiece = pt;
                        startIsNbt = isNbt;
                    } else if (isNbt && !startIsNbt) {
                        startPiece = pt;
                        startIsNbt = true;
                        replacedBpByNbt++;
                    }
                    continue;
                }
                if (pt.entrance == null) {
                    skippedNoMarkers++;
                    if (isNbt) nbtSkippedNoLapisCount++;
                    continue;
                }
                AxiomBlueprint427.PieceTemplate prev = poolByKey.get(baseKey);
                if (prev == null) {
                    poolByKey.put(baseKey, pt);
                } else if (isNbt && !prev.isNbt) {
                    poolByKey.put(baseKey, pt);
                    replacedBpByNbt++;
                } else if (!isNbt && prev.isNbt) {
                    replacedBpByNbt++;
                } else {
                    String disambig = baseKey + (isNbt ? "#nbt" : "#bp");
                    AxiomBlueprint427.PieceTemplate altPt = new AxiomBlueprint427.PieceTemplate(
                            disambig, pt.displayName, pt.tags,
                            pt.minX, pt.minY, pt.minZ, pt.maxX, pt.maxY, pt.maxZ,
                            pt.entrance, pt.exits, pt.content, pt.embeddedFallback, pt.isNbt);
                    poolByKey.put(disambig, altPt);
                }
            } catch (Throwable ex) {
                failedParse++;
                LOG.warn("settledlands: failed parsing schematic " + p + ": " + ex.getMessage());
            }
        }

        List<AxiomBlueprint427.PieceTemplate> parsedPool = new ArrayList<>(poolByKey.values());
        SelectionResult sel = selectPieces(parsedPool, config.filterMode);

        if (startPiece == null && config.useEmbeddedStartFallback) {
            try {
                startPiece = AxiomBlueprint427.fromStartTemplate(
                        "__embedded_start__", DungeonTemplate425.loadStart(), true);
            } catch (Throwable ex) {
                LOG.warn("settledlands: could not load embedded start.slbp fallback: " + ex.getMessage());
            }
        }

        Map<String, AxiomBlueprint427.PieceTemplate> byKey = new LinkedHashMap<>();
        if (startPiece != null) {
            byKey.put(startPiece.key, startPiece);
        }
        int nbtInSelPieces = 0;
        for (AxiomBlueprint427.PieceTemplate pt : sel.pieces) {
            byKey.put(pt.key, pt);
            if (pt.isNbt) nbtInSelPieces++;
        }
        int nbtInParsedPool = 0;
        for (AxiomBlueprint427.PieceTemplate pt : parsedPool) {
            if (pt.isNbt) nbtInParsedPool++;
        }
        int nbtInPoolCount = (startPiece != null && startPiece.isNbt ? 1 : 0) + nbtInSelPieces;
        int nbtFilteredCount = Math.max(0, nbtInParsedPool - nbtInSelPieces);

        int unusedNbtOther = 0;
        String unusedHintDir = "";
        if (config.isAutoDir() && chosenDir != null) {
            for (Path cand : candidates) {
                if (cand == null || cand.equals(chosenDir) || !Files.isDirectory(cand)) continue;
                int cnt = countNbtFiles(cand);
                if (cnt > 0) {
                    if (unusedHintDir.isEmpty()) unusedHintDir = cand.toString();
                    unusedNbtOther += cnt;
                }
            }
        }

        Library lib = new Library(config, chosenDir, startPiece, sel.pieces, byKey,
                chosenFiles.size(), skippedNoMarkers, failedParse, sel.appliedFilter,
                sel.biomeProgression, nbtCount, nbtInPoolCount, nbtFilteredCount,
                nbtSkippedNoLapisCount, unusedNbtOther, unusedHintDir);
        LOG.info("settledlands: blueprints loaded (" + lib.summaryLine()
                + ", scanned=" + chosenFiles.size()
                + ", nbt=" + nbtCount
                + ", nbt_in_pool=" + nbtInPoolCount
                + ", nbt_filtered=" + nbtFilteredCount
                + ", nbt_no_lapis=" + nbtSkippedNoLapisCount
                + ", replaced_bp=" + replacedBpByNbt
                + ", skipped_no_lapis=" + skippedNoMarkers
                + ", parse_errors=" + failedParse + ")");
        if (unusedNbtOther > 0) {
            LOG.warn("settledlands: found " + unusedNbtOther
                    + " .nbt file(s) in " + unusedHintDir
                    + " which were NOT loaded because active folder is " + chosenDir
                    + " — move/copy your .nbt schematics into " + chosenDir
                    + " or point blueprints_dir to that folder.");
        }
        return lib;
    }

    public static String themeOf(AxiomBlueprint427.PieceTemplate p) {
        if (p == null) return THEME_OTHER;
        if (p.hasTag(THEME_CRYPT)) return THEME_CRYPT;
        if (p.hasTag(THEME_INFERNAL)) return THEME_INFERNAL;
        if (p.hasTag(THEME_FLESH)) return THEME_FLESH;
        if (p.hasTag(THEME_SEWER)) return THEME_SEWER;
        if (p.hasTag(THEME_COVE)) return THEME_COVE;
        String bn = baseName(p.key).toLowerCase(Locale.ROOT);
        if (bn.startsWith("к ") || bn.startsWith("к_")) return THEME_CRYPT;
        if (bn.startsWith("а ") || bn.startsWith("а_")) return THEME_INFERNAL;
        if (bn.startsWith("п ") || bn.startsWith("п_")) return THEME_FLESH;
        if (bn.startsWith("с ") || bn.startsWith("с_")) return THEME_SEWER;
        if (bn.startsWith("б ") || bn.startsWith("б_")) return THEME_COVE;
        return THEME_OTHER;
    }

    private static List<Path> scanBpFiles(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir, 8)) {
            walk.filter(Files::isRegularFile)
                    .filter(DungeonBlueprints427::isBlueprintOrNbt)
                    .sorted(Comparator.comparing(p -> dir.relativize(p).toString().replace('\\', '/')))
                    .forEach(out::add);
        } catch (IOException ex) {
            LOG.warn("settledlands: failed scanning blueprint folder " + dir + ": " + ex.getMessage());
        }
        return out;
    }

    private static boolean isBlueprintOrNbt(Path p) {
        if (p == null || p.getFileName() == null) return false;
        String low = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return low.endsWith(".bp") || low.endsWith(".nbt");
    }

    private static int countNbtFiles(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return 0;
        final int[] count = { 0 };
        try (Stream<Path> walk = Files.walk(dir, 8)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null
                            && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".nbt"))
                    .forEach(p -> count[0]++);
        } catch (IOException ignored) {
        }
        return count[0];
    }

    private static String stripBlueprintExt(String rel) {
        if (rel == null) return "";
        String low = rel.toLowerCase(Locale.ROOT);
        if (low.endsWith(".bp")) return rel.substring(0, rel.length() - 3);
        if (low.endsWith(".nbt")) return rel.substring(0, rel.length() - 4);
        return rel;
    }

    private static String stripThemePrefix(String base) {
        if (base == null) return "";
        String low = base.trim().toLowerCase(Locale.ROOT);
        for (String pref : new String[] { "к ", "к_", "а ", "а_", "п ", "п_", "с ", "с_", "б ", "б_" }) {
            if (low.startsWith(pref)) {
                return low.substring(pref.length()).trim();
            }
        }
        return low;
    }

    private static AxiomBlueprint427.PieceTemplate withInheritedTheme(
            AxiomBlueprint427.PieceTemplate p, String theme) {
        if (p == null || theme == null || theme.isEmpty() || p.hasTag(theme)) {
            return p;
        }
        List<String> tags = new ArrayList<>(p.tags.size() + 1);
        tags.addAll(p.tags);
        tags.add(theme);
        return new AxiomBlueprint427.PieceTemplate(
                p.key, p.displayName, tags,
                p.minX, p.minY, p.minZ, p.maxX, p.maxY, p.maxZ,
                p.entrance, p.exits, p.content, p.embeddedFallback, p.isNbt);
    }

    private static boolean hasAnyUsableBlueprint(Path dir, List<Path> files, String startName) {
        for (Path p : files) {
            String rel = dir.relativize(p).toString().replace('\\', '/');
            String key = stripBlueprintExt(rel);
            try {
                AxiomBlueprint427.PieceTemplate pt = AxiomBlueprint427.loadFile(p, key);
                if (isStartMatch(key, pt, startName) || pt.entrance != null) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static boolean isStartMatch(String key, AxiomBlueprint427.PieceTemplate pt, String startName) {
        String base = baseName(key).toLowerCase(Locale.ROOT);
        int hash = base.indexOf('#');
        if (hash >= 0) base = base.substring(0, hash);
        String want = startName == null ? "старт" : startName.toLowerCase(Locale.ROOT);
        if (base.equals(want) || "start".equals(base) || "старт".equals(base)) {
            return true;
        }
        return pt != null && pt.hasTag("start");
    }

    private static String baseName(String key) {
        if (key == null) return "";
        int slash = key.lastIndexOf('/');
        return slash >= 0 ? key.substring(slash + 1) : key;
    }

    private static final class SelectionResult {
        final List<AxiomBlueprint427.PieceTemplate> pieces;
        final String appliedFilter;
        final boolean biomeProgression;

        SelectionResult(List<AxiomBlueprint427.PieceTemplate> pieces, String appliedFilter, boolean biomeProgression) {
            this.pieces = pieces;
            this.appliedFilter = appliedFilter;
            this.biomeProgression = biomeProgression;
        }
    }

    private static SelectionResult selectPieces(
            List<AxiomBlueprint427.PieceTemplate> all, String filterMode) {
        if (all.isEmpty()) {
            return new SelectionResult(Collections.emptyList(),
                    filterMode == null ? "auto->all" : filterMode, false);
        }
        String mode = filterMode == null ? "auto" : filterMode.trim();
        String low = mode.toLowerCase(Locale.ROOT);

        if ("all".equals(low) || "*".equals(low)) {
            return new SelectionResult(new ArrayList<>(all), mode, false);
        }
        if (low.startsWith("tag:")) {
            String tag = low.substring(4).trim();
            List<AxiomBlueprint427.PieceTemplate> out = new ArrayList<>();
            for (AxiomBlueprint427.PieceTemplate p : all) {
                if (p.hasTag(tag)) out.add(p);
            }
            return new SelectionResult(out, mode, false);
        }
        if (low.startsWith("prefix:")) {
            String pref = mode.substring(7);
            List<AxiomBlueprint427.PieceTemplate> out = new ArrayList<>();
            for (AxiomBlueprint427.PieceTemplate p : all) {
                if (baseName(p.key).startsWith(pref)) out.add(p);
            }
            return new SelectionResult(out, mode, false);
        }

        List<AxiomBlueprint427.PieceTemplate> crypt = new ArrayList<>();
        List<AxiomBlueprint427.PieceTemplate> infernal = new ArrayList<>();
        List<AxiomBlueprint427.PieceTemplate> flesh = new ArrayList<>();
        List<AxiomBlueprint427.PieceTemplate> sewer = new ArrayList<>();
        List<AxiomBlueprint427.PieceTemplate> cove = new ArrayList<>();
        List<AxiomBlueprint427.PieceTemplate> otherNbt = new ArrayList<>();

        for (AxiomBlueprint427.PieceTemplate p : all) {
            String theme = themeOf(p);
            if (THEME_CRYPT.equals(theme)) crypt.add(p);
            else if (THEME_INFERNAL.equals(theme)) infernal.add(p);
            else if (THEME_FLESH.equals(theme)) flesh.add(p);
            else if (THEME_SEWER.equals(theme)) sewer.add(p);
            else if (THEME_COVE.equals(theme)) cove.add(p);
            else if (p.isNbt) otherNbt.add(p);
        }

        boolean explicitBiomes = "biomes".equals(low);
        if (explicitBiomes || (!crypt.isEmpty() && (!infernal.isEmpty() || !flesh.isEmpty()))) {
            List<AxiomBlueprint427.PieceTemplate> unmatchedOtherNbt = new ArrayList<>();
            if (!explicitBiomes && !otherNbt.isEmpty()) {
                for (AxiomBlueprint427.PieceTemplate nbtPiece : otherNbt) {
                    String stripped = stripThemePrefix(baseName(nbtPiece.key));
                    String matchedTheme = replaceMatchingBpInBucket(stripped, infernal, nbtPiece, THEME_INFERNAL);
                    if (matchedTheme == null) {
                        matchedTheme = replaceMatchingBpInBucket(stripped, flesh, nbtPiece, THEME_FLESH);
                    }
                    if (matchedTheme == null) {
                        matchedTheme = replaceMatchingBpInBucket(stripped, crypt, nbtPiece, THEME_CRYPT);
                    }
                    if (matchedTheme == null) {
                        unmatchedOtherNbt.add(nbtPiece);
                    }
                }
            }

            List<AxiomBlueprint427.PieceTemplate> combined = new ArrayList<>(
                    crypt.size() + infernal.size() + flesh.size() + unmatchedOtherNbt.size());
            combined.addAll(crypt);
            combined.addAll(infernal);
            combined.addAll(flesh);
            combined.addAll(unmatchedOtherNbt);
            if (combined.isEmpty()) {
                return new SelectionResult(new ArrayList<>(all),
                        explicitBiomes ? "biomes->all" : "auto->all", false);
            }
            String desc;
            if (!infernal.isEmpty() && !flesh.isEmpty()) desc = "к->а+п";
            else if (!infernal.isEmpty()) desc = "к->а";
            else if (!flesh.isEmpty()) desc = "к->п";
            else desc = "к";
            String label = (explicitBiomes ? "biomes(" : "auto->biomes(") + desc + ")";
            return new SelectionResult(combined, label, !crypt.isEmpty() && (!infernal.isEmpty() || !flesh.isEmpty()));
        }

        if (!infernal.isEmpty() && (!sewer.isEmpty() || !cove.isEmpty())) {
            if (!otherNbt.isEmpty()) {
                List<AxiomBlueprint427.PieceTemplate> merged = new ArrayList<>(infernal.size() + otherNbt.size());
                merged.addAll(infernal);
                for (AxiomBlueprint427.PieceTemplate nbtPiece : otherNbt) {
                    String stripped = stripThemePrefix(baseName(nbtPiece.key));
                    if (replaceMatchingBpInBucket(stripped, merged, nbtPiece, THEME_INFERNAL) == null) {
                        merged.add(nbtPiece);
                    }
                }
                return new SelectionResult(merged, "auto->infernal", false);
            }
            return new SelectionResult(infernal, "auto->infernal", false);
        }
        return new SelectionResult(new ArrayList<>(all), "auto->all", false);
    }

    private static String replaceMatchingBpInBucket(
            String strippedNbtName,
            List<AxiomBlueprint427.PieceTemplate> bucket,
            AxiomBlueprint427.PieceTemplate nbtPiece,
            String theme) {
        if (strippedNbtName == null || strippedNbtName.isEmpty()) return null;
        for (int i = 0; i < bucket.size(); i++) {
            AxiomBlueprint427.PieceTemplate existing = bucket.get(i);
            if (!existing.isNbt && strippedNbtName.equals(stripThemePrefix(baseName(existing.key)))) {
                bucket.set(i, withInheritedTheme(nbtPiece, theme));
                return theme;
            }
        }
        return null;
    }
}
