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
 * Build 427: scans an external Axiom blueprint folder ({@code *.bp}), parses each file natively,
 * applies theme/tag/prefix filtering, and indexes pieces for procedural dungeon generation.
 */
public final class DungeonBlueprints427 {

    private static final Logger LOG = LoggerFactory.getLogger("settledlands");

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
        public final int skippedNoMarkers;
        public final int failedParse;
        public final String appliedFilter;

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
            this.config = config;
            this.sourceDir = sourceDir;
            this.startPiece = startPiece;
            this.pieces = Collections.unmodifiableList(pieces);
            List<AxiomBlueprint427.PieceTemplate> conn = new ArrayList<>();
            List<AxiomBlueprint427.PieceTemplate> de = new ArrayList<>();
            for (AxiomBlueprint427.PieceTemplate p : pieces) {
                if (p.isDeadEnd()) de.add(p);
                else conn.add(p);
            }
            this.connectors = Collections.unmodifiableList(conn);
            this.deadEnds = Collections.unmodifiableList(de);
            this.byKey = Collections.unmodifiableMap(byKey);
            this.totalBpFiles = totalBpFiles;
            this.skippedNoMarkers = skippedNoMarkers;
            this.failedParse = failedParse;
            this.appliedFilter = appliedFilter;
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
            return "папка: " + dirLabel
                    + ", фильтр: " + appliedFilter
                    + ", комнат: " + pieces.size()
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
                // dungeon blueprint (either start piece or a piece with a lapis marker).
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
        List<AxiomBlueprint427.PieceTemplate> parsedPool = new ArrayList<>();
        int skippedNoMarkers = 0;
        int failedParse = 0;

        for (Path p : chosenFiles) {
            String rel = chosenDir.relativize(p).toString().replace('\\', '/');
            String key = rel.endsWith(".bp") ? rel.substring(0, rel.length() - 3) : rel;
            try {
                AxiomBlueprint427.PieceTemplate pt = AxiomBlueprint427.loadFile(p, key);
                if (isStartMatch(key, pt, config.startName)) {
                    if (startPiece == null) {
                        startPiece = pt;
                    }
                    continue;
                }
                if (pt.entrance == null) {
                    skippedNoMarkers++;
                    continue;
                }
                parsedPool.add(pt);
            } catch (Throwable ex) {
                failedParse++;
                LOG.warn("settledlands: failed parsing blueprint " + p + ": " + ex.getMessage());
            }
        }

        String appliedFilter = config.filterMode;
        List<AxiomBlueprint427.PieceTemplate> selected = filterPieces(parsedPool, config.filterMode);
        if ("auto".equalsIgnoreCase(config.filterMode)) {
            appliedFilter = selected.size() == parsedPool.size() ? "auto->all" : "auto->infernal";
        }

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
        for (AxiomBlueprint427.PieceTemplate pt : selected) {
            byKey.put(pt.key, pt);
        }

        Library lib = new Library(config, chosenDir, startPiece, selected, byKey,
                chosenFiles.size(), skippedNoMarkers, failedParse, appliedFilter);
        LOG.info("settledlands: blueprints loaded (" + lib.summaryLine()
                + ", scanned=" + chosenFiles.size()
                + ", skipped_no_lapis=" + skippedNoMarkers
                + ", parse_errors=" + failedParse + ")");
        return lib;
    }

    private static List<Path> scanBpFiles(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir, 8)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bp"))
                    .sorted(Comparator.comparing(p -> dir.relativize(p).toString().replace('\\', '/')))
                    .forEach(out::add);
        } catch (IOException ex) {
            LOG.warn("settledlands: failed scanning blueprint folder " + dir + ": " + ex.getMessage());
        }
        return out;
    }

    private static boolean hasAnyUsableBlueprint(Path dir, List<Path> files, String startName) {
        for (Path p : files) {
            String rel = dir.relativize(p).toString().replace('\\', '/');
            String key = rel.endsWith(".bp") ? rel.substring(0, rel.length() - 3) : rel;
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
        String want = startName == null ? "старт" : startName.toLowerCase(Locale.ROOT);
        if (base.equals(want) || "start".equals(base) || "старт".equals(base)) {
            return true;
        }
        return pt != null && pt.hasTag("start");
    }

    private static String baseName(String key) {
        int slash = key.lastIndexOf('/');
        return slash >= 0 ? key.substring(slash + 1) : key;
    }

    private static List<AxiomBlueprint427.PieceTemplate> filterPieces(
            List<AxiomBlueprint427.PieceTemplate> all, String filterMode) {
        if (all.isEmpty()) return Collections.emptyList();
        String mode = filterMode == null ? "auto" : filterMode.trim();
        String low = mode.toLowerCase(Locale.ROOT);

        if ("all".equals(low) || "*".equals(low)) {
            return new ArrayList<>(all);
        }
        if (low.startsWith("tag:")) {
            String tag = low.substring(4).trim();
            List<AxiomBlueprint427.PieceTemplate> out = new ArrayList<>();
            for (AxiomBlueprint427.PieceTemplate p : all) {
                if (p.hasTag(tag)) out.add(p);
            }
            return out;
        }
        if (low.startsWith("prefix:")) {
            String pref = mode.substring(7);
            List<AxiomBlueprint427.PieceTemplate> out = new ArrayList<>();
            for (AxiomBlueprint427.PieceTemplate p : all) {
                if (baseName(p.key).startsWith(pref)) out.add(p);
            }
            return out;
        }

        // auto mode: if mixed themes from Данж.rar exist together, pick infernal cleanly;
        // otherwise use every valid blueprint in the user's folder.
        List<AxiomBlueprint427.PieceTemplate> infernal = new ArrayList<>();
        boolean hasOtherThemes = false;
        for (AxiomBlueprint427.PieceTemplate p : all) {
            String bn = baseName(p.key).toLowerCase(Locale.ROOT);
            boolean isInf = p.hasTag("infernal") || bn.startsWith("а ");
            if (isInf) {
                infernal.add(p);
            }
            if (p.hasTag("crypt") || p.hasTag("sewer") || p.hasTag("cove") || p.hasTag("flesh")) {
                hasOtherThemes = true;
            }
        }
        if (!infernal.isEmpty() && hasOtherThemes) {
            return infernal;
        }
        return new ArrayList<>(all);
    }
}
