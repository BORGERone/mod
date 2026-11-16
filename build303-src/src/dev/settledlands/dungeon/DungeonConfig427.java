package dev.settledlands.dungeon;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 427: configuration loader for external Axiom {@code .bp} blueprint folders and
 * procedural dungeon generation parameters.
 *
 * <p>Reads {@code config/settledlands/dungeon.properties} (creating it together with
 * {@code config/settledlands/blueprints/} on first run) and supports {@code blueprints_dir=auto}
 * to automatically inspect {@code config/settledlands/blueprints}, {@code config/axiom/blueprints},
 * and {@code blueprints}.</p>
 */
public final class DungeonConfig427 {

    private static final Logger LOG = LoggerFactory.getLogger("settledlands");

    public static final String CONFIG_REL = "config/settledlands/dungeon.properties";
    public static final String DEFAULT_BLUEPRINTS_REL = "config/settledlands/blueprints";
    public static final String AXIOM_BLUEPRINTS_REL = "config/axiom/blueprints";
    public static final String ROOT_BLUEPRINTS_REL = "blueprints";

    public final Path baseDir;
    public final String blueprintsDir;
    public final String filterMode;
    public final String startName;
    public final int minPieces;
    public final int maxPieces;
    public final int maxRadius;
    public final int minY;
    public final int maxY;
    public final boolean sealStartEntrance;
    public final String doorwayBlock;
    public final int immediatePieces;
    public final int blocksPerTick;
    public final boolean useEmbeddedStartFallback;

    public DungeonConfig427(
            Path baseDir,
            String blueprintsDir,
            String filterMode,
            String startName,
            int minPieces,
            int maxPieces,
            int maxRadius,
            int minY,
            int maxY,
            boolean sealStartEntrance,
            String doorwayBlock,
            int immediatePieces,
            int blocksPerTick,
            boolean useEmbeddedStartFallback) {
        this.baseDir = baseDir == null ? Paths.get(".").toAbsolutePath().normalize() : baseDir.toAbsolutePath().normalize();
        this.blueprintsDir = (blueprintsDir == null || blueprintsDir.trim().isEmpty()) ? "auto" : blueprintsDir.trim();
        this.filterMode = (filterMode == null || filterMode.trim().isEmpty()) ? "auto" : filterMode.trim();
        this.startName = (startName == null || startName.trim().isEmpty()) ? "старт" : startName.trim();
        int minP = Math.max(1, minPieces);
        int maxP = Math.max(minP, maxPieces);
        this.minPieces = minP;
        this.maxPieces = maxP;
        this.maxRadius = Math.max(32, Math.min(240, maxRadius));
        this.minY = Math.max(-60, Math.min(200, minY));
        this.maxY = Math.max(this.minY + 32, Math.min(300, maxY));
        this.sealStartEntrance = sealStartEntrance;
        this.doorwayBlock = (doorwayBlock == null || doorwayBlock.trim().isEmpty())
                ? DungeonTemplate425.DOORWAY_REPLACEMENT : doorwayBlock.trim();
        this.immediatePieces = Math.max(1, Math.min(32, immediatePieces));
        this.blocksPerTick = Math.max(256, Math.min(32768, blocksPerTick));
        this.useEmbeddedStartFallback = useEmbeddedStartFallback;
    }

    public static DungeonConfig427 defaults(Path baseDir) {
        return new DungeonConfig427(
                baseDir,
                "auto",
                "auto",
                "старт",
                60,
                80,
                230,
                -52,
                240,
                true,
                DungeonTemplate425.DOORWAY_REPLACEMENT,
                8,
                4096,
                true);
    }

    public static DungeonConfig427 loadOrCreate() {
        return loadOrCreate(Paths.get(".").toAbsolutePath().normalize());
    }

    public static DungeonConfig427 loadOrCreate(Path baseDir) {
        Path normBase = (baseDir == null ? Paths.get(".") : baseDir).toAbsolutePath().normalize();
        Path configFile = normBase.resolve(CONFIG_REL);
        Path defaultBpDir = normBase.resolve(DEFAULT_BLUEPRINTS_REL);

        try {
            Files.createDirectories(defaultBpDir);
            Path readme = defaultBpDir.resolve("README-BLUEPRINTS-RU.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme,
                        "Положите сюда ваши файлы .bp из Axiom (включая старт.bp и комнаты/коридоры с маркерами lapis_block и redstone_block).\n"
                        + "При blueprints_dir=auto мод также сам проверяет папку Axiom: config/axiom/blueprints/ (включая подпапки).\n"
                        + "Путь и фильтр тем можно настроить в config/settledlands/dungeon.properties.\n"
                        + "Чтобы перечитать папку без перезапуска игры, используйте: /settledlands dungeon reload\n",
                        StandardCharsets.UTF_8);
            }
            if (!Files.exists(configFile)) {
                Files.createDirectories(configFile.getParent());
                Files.writeString(configFile, defaultPropertiesContent(), StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            LOG.warn("settledlands: could not initialize dungeon config files: " + ex.getMessage());
        }

        if (!Files.isRegularFile(configFile)) {
            return defaults(normBase);
        }

        Properties p = new Properties();
        try (InputStreamReader r = new InputStreamReader(Files.newInputStream(configFile), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException ex) {
            LOG.warn("settledlands: failed reading " + configFile + ", using defaults: " + ex.getMessage());
            return defaults(normBase);
        }

        return new DungeonConfig427(
                normBase,
                p.getProperty("blueprints_dir", "auto"),
                p.getProperty("filter_mode", "auto"),
                p.getProperty("start_name", "старт"),
                parseInt(p.getProperty("min_pieces"), 60),
                parseInt(p.getProperty("max_pieces"), 80),
                parseInt(p.getProperty("max_radius"), 230),
                parseInt(p.getProperty("min_y"), -52),
                parseInt(p.getProperty("max_y"), 240),
                parseBool(p.getProperty("seal_start_entrance"), true),
                p.getProperty("doorway_block", DungeonTemplate425.DOORWAY_REPLACEMENT),
                parseInt(p.getProperty("immediate_pieces"), 8),
                parseInt(p.getProperty("blocks_per_tick"), 4096),
                parseBool(p.getProperty("use_embedded_start_fallback"), true));
    }

    public List<Path> candidateBlueprintDirs() {
        List<Path> out = new ArrayList<>();
        if ("auto".equalsIgnoreCase(blueprintsDir)) {
            out.add(baseDir.resolve(DEFAULT_BLUEPRINTS_REL));
            out.add(baseDir.resolve(AXIOM_BLUEPRINTS_REL));
            out.add(baseDir.resolve(ROOT_BLUEPRINTS_REL));
        } else {
            Path p = Paths.get(blueprintsDir);
            if (!p.isAbsolute()) {
                p = baseDir.resolve(p);
            }
            out.add(p.normalize());
        }
        return out;
    }

    public boolean isAutoDir() {
        return "auto".equalsIgnoreCase(blueprintsDir);
    }

    private static int parseInt(String raw, int def) {
        if (raw == null || raw.trim().isEmpty()) return def;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    private static boolean parseBool(String raw, boolean def) {
        if (raw == null || raw.trim().isEmpty()) return def;
        String low = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(low) || "1".equals(low) || "yes".equals(low)) return true;
        if ("false".equals(low) || "0".equals(low) || "no".equals(low)) return false;
        return def;
    }

    private static String defaultPropertiesContent() {
        return "# Settled Lands — Настройки генерации «Адского данжа» (settledlands:hell_dungeon)\n"
                + "# После изменений в игре выполните /settledlands dungeon reload\n"
                + "\n"
                + "# Папка с .bp файлами Axiom:\n"
                + "#   auto — ищет .bp по очереди в config/settledlands/blueprints, config/axiom/blueprints и blueprints\n"
                + "#   либо укажите точный путь, например: config/axiom/blueprints/Данж\n"
                + "blueprints_dir=auto\n"
                + "\n"
                + "# Фильтр комнат из папки:\n"
                + "#   auto         — если в папке перемешано несколько тем (infernal/crypt/sewer/cove/flesh), берет только infernal; иначе берет все валидные .bp\n"
                + "#   all          — брать все .bp с маркером входа lapis_block\n"
                + "#   tag:infernal — фильтр по тегу Axiom (или prefix:а )\n"
                + "filter_mode=auto\n"
                + "\n"
                + "# Имя стартовой комнаты (без .bp)\n"
                + "start_name=старт\n"
                + "\n"
                + "# Целевой размер одного инстанса данжа (в комнатах/коридорах)\n"
                + "min_pieces=60\n"
                + "max_pieces=80\n"
                + "\n"
                + "# Максимальный радиус от центра ячейки 512x512 и границы высоты\n"
                + "max_radius=230\n"
                + "min_y=-52\n"
                + "max_y=240\n"
                + "\n"
                + "# Заложить ли тыловой вход (lapis_block) у стартовой комнаты, чтобы не выпасть в войд\n"
                + "seal_start_entrance=true\n"
                + "\n"
                + "# Блок замены маркеров швов и заделки неподключенных проемов\n"
                + "doorway_block=minecraft:nether_bricks\n"
                + "\n"
                + "# Сколько ближайших комнат ставить мгновенно при входе, и лимит блоков/тик при достройке\n"
                + "immediate_pieces=8\n"
                + "blocks_per_tick=4096\n"
                + "\n"
                + "# Использовать встроенный start.slbp как запасную стартовую комнату, если папка .bp пуста\n"
                + "use_embedded_start_fallback=true\n";
    }
}
