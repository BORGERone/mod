package dev.settledlands.trial;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 449: server-side file manager for {@code config/settledlands/mob_pools/*.txt}.
 */
public final class TrialMobPoolManager449 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/TrialMobPool449");
    private static final Set<String> WARNED = Collections.synchronizedSet(new HashSet<>());
    private static final String GUIDE_FILE = "README_FORMAT.txt.disabled";
    private static final String GUIDE_TEXT =
            "# SettledLands Build 449 - Mob Pools Guide / Справка по пулам мобов\n"
            + "# Put your .txt mob pool files in: config/settledlands/mob_pools/<name>.txt\n"
            + "# Format per line:\n"
            + "#   entity_id | weight\n"
            + "#\n"
            + "# Examples:\n"
            + "#   minecraft:zombie | 10\n"
            + "#   minecraft:skeleton | 6\n"
            + "#   minecraft:breeze | 3\n";

    private TrialMobPoolManager449() {}

    public static Path poolDirectory() {
        try {
            Path cfg = FMLPaths.CONFIGDIR.get();
            if (cfg != null) {
                return cfg.resolve("settledlands").resolve("mob_pools");
            }
        } catch (Throwable ignored) {
        }
        return Path.of("config", "settledlands", "mob_pools");
    }

    public static Path ensureDirectory() {
        Path dir = poolDirectory();
        try {
            boolean fresh = !Files.isDirectory(dir);
            Files.createDirectories(dir);
            Path guide = dir.resolve(GUIDE_FILE);
            if (fresh && !Files.exists(guide)) {
                Files.writeString(guide, GUIDE_TEXT, StandardCharsets.UTF_8);
            }
        } catch (Throwable ex) {
            warnOnce("mkdir:" + dir, "mob_pool: failed to create " + dir + ": " + ex.getMessage());
        }
        return dir;
    }

    public static List<String> listPools() {
        Path dir = ensureDirectory();
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path p : stream) {
                if (Files.isRegularFile(p) && p.getFileName() != null) {
                    names.add(p.getFileName().toString());
                }
            }
        } catch (Throwable ex) {
            warnOnce("list:" + dir, "mob_pool: failed to list pools in " + dir + ": " + ex.getMessage());
        }
        Collections.sort(names);
        return names;
    }

    public static boolean poolExists(String rawFileName) {
        String safe = TrialMobPool449.sanitizeFileName(rawFileName);
        if (safe.isEmpty()) return false;
        Path dir = ensureDirectory();
        Path file = dir.resolve(safe).normalize();
        return file.startsWith(dir.normalize()) && Files.isRegularFile(file);
    }

    public static List<TrialConfig446.SpawnEntry> loadPoolQuiet(String rawFileName) {
        String safe = TrialMobPool449.sanitizeFileName(rawFileName);
        if (safe.isEmpty()) return Collections.emptyList();
        Path dir = ensureDirectory();
        Path file = dir.resolve(safe).normalize();
        if (!file.startsWith(dir.normalize()) || !Files.isRegularFile(file)) {
            return Collections.emptyList();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            return TrialMobPool449.parseLines(lines);
        } catch (Throwable ex) {
            LOG.warn("mob_pool: failed to read " + file + ": " + ex.getMessage());
            return Collections.emptyList();
        }
    }

    public static String savePool(String rawFileName, List<TrialConfig446.SpawnEntry> entries) {
        String safe = TrialMobPool449.normalizeSaveName(rawFileName);
        if (safe.isEmpty()) return "";
        Path dir = ensureDirectory();
        Path normDir = dir.normalize();
        Path file = normDir.resolve(safe).normalize();
        if (!file.startsWith(normDir)) return "";
        try {
            String text = TrialMobPool449.formatPoolFile(safe, entries);
            Files.writeString(file, text, StandardCharsets.UTF_8);
            LOG.info("mob_pool: saved pool '" + safe + "' ("
                    + (entries == null ? 0 : entries.size()) + " entries)");
            return safe;
        } catch (Throwable ex) {
            LOG.warn("mob_pool: failed to save " + file + ": " + ex.getMessage());
            return "";
        }
    }

    public static boolean deletePool(String rawFileName) {
        String safe = TrialMobPool449.normalizeSaveName(rawFileName);
        if (safe.isEmpty()) return false;
        Path dir = ensureDirectory();
        Path normDir = dir.normalize();
        Path file = normDir.resolve(safe).normalize();
        if (!file.startsWith(normDir)) return false;
        try {
            boolean removed = Files.deleteIfExists(file);
            if (removed) {
                LOG.info("mob_pool: deleted pool '" + safe + "'");
            }
            return removed;
        } catch (Throwable ex) {
            LOG.warn("mob_pool: failed to delete " + file + ": " + ex.getMessage());
            return false;
        }
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) LOG.warn(message);
    }
}
