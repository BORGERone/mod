package dev.settledlands.dungeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Build 427 / 429 / 432: deterministic procedural dungeon planner over a
 * {@link DungeonBlueprints427.Library}.
 *
 * <p>Stitches rooms and corridors from {@code старт} by matching rotated {@code lapis_block}
 * entrances against open {@code redstone_block} exits without any bounding-box overlap inside
 * the instance's {@code 512 x 512} grid cell. Maintains a reserved dead-end cap invariant on
 * every open exit (when dead-end templates exist) so corridors and intersections always terminate
 * cleanly in real dead-end rooms instead of flat {@code nether_bricks} seal walls.</p>
 *
 * <p>When {@link DungeonBlueprints427.Library#biomeProgression} is active (e.g. {@code Данж.rar}),
 * the entrance region around {@code старт} is built strictly from {@code crypt} («к») rooms,
 * and deeper branches split into distinct sticky deep biomes: {@code infernal} («а») and
 * {@code flesh} («п»), each using its own corridors, halls, and dead-ends.</p>
 */
public final class DungeonGenerator427 {

    private DungeonGenerator427() {}

    public static final class PlannedPiece {
        public final String pieceKey;
        public final int ox;
        public final int oy;
        public final int oz;
        public final int turns;
        public final int minX;
        public final int minY;
        public final int minZ;
        public final int maxX;
        public final int maxY;
        public final int maxZ;
        public final String biome;
        public final int depth;

        public PlannedPiece(String pieceKey, int ox, int oy, int oz, int turns,
                int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this(pieceKey, ox, oy, oz, turns, minX, minY, minZ, maxX, maxY, maxZ,
                    DungeonBlueprints427.THEME_OTHER, 0);
        }

        public PlannedPiece(String pieceKey, int ox, int oy, int oz, int turns,
                int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                String biome, int depth) {
            this.pieceKey = pieceKey;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.turns = turns & 3;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.biome = biome == null ? DungeonBlueprints427.THEME_OTHER : biome;
            this.depth = Math.max(0, depth);
        }
    }

    public static final class Seal {
        public final int x;
        public final int y;
        public final int z;
        public final int outDir;

        public Seal(int x, int y, int z, int outDir) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.outDir = outDir & 3;
        }
    }

    public static final class Plan {
        public final List<PlannedPiece> pieces;
        public final List<Seal> seals;
        public final double spawnX;
        public final double spawnY;
        public final double spawnZ;
        public final float spawnYaw;
        public final float spawnPitch;
        public final int cryptCount;
        public final int infernalCount;
        public final int fleshCount;

        public Plan(List<PlannedPiece> pieces, List<Seal> seals,
                double spawnX, double spawnY, double spawnZ, float spawnYaw, float spawnPitch) {
            this.pieces = Collections.unmodifiableList(pieces);
            this.seals = Collections.unmodifiableList(seals);
            this.spawnX = spawnX;
            this.spawnY = spawnY;
            this.spawnZ = spawnZ;
            this.spawnYaw = spawnYaw;
            this.spawnPitch = spawnPitch;
            int cc = 0, ic = 0, fc = 0;
            for (PlannedPiece p : pieces) {
                if (DungeonBlueprints427.THEME_CRYPT.equals(p.biome)) cc++;
                else if (DungeonBlueprints427.THEME_INFERNAL.equals(p.biome)) ic++;
                else if (DungeonBlueprints427.THEME_FLESH.equals(p.biome)) fc++;
            }
            this.cryptCount = cc;
            this.infernalCount = ic;
            this.fleshCount = fc;
        }
    }

    private static final class OpenExit {
        final int rx;
        final int ry;
        final int rz;
        final int outDir;
        final int depth;
        final String biome;
        PlannedPiece reservedCap;
        int[] reservedBox;

        OpenExit(int rx, int ry, int rz, int outDir, int depth, String biome) {
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.outDir = outDir & 3;
            this.depth = depth;
            this.biome = biome;
        }
    }

    private static final class PlacementAttempt {
        final PlannedPiece main;
        final List<PlannedPiece> addedPieces = new ArrayList<>();
        final List<String> addedKeys = new ArrayList<>();
        final List<OpenExit> newExits = new ArrayList<>();
        final Map<Integer, PlannedPiece> otherReReservations = new HashMap<>();

        PlacementAttempt(PlannedPiece main, String key) {
            this.main = main;
            this.addedPieces.add(main);
            this.addedKeys.add(key);
        }
    }

    public static int rotX(int x, int z, int turns) {
        return switch (turns & 3) {
            case 1 -> -z;
            case 2 -> -x;
            case 3 -> z;
            default -> x;
        };
    }

    public static int rotZ(int x, int z, int turns) {
        return switch (turns & 3) {
            case 1 -> x;
            case 2 -> -z;
            case 3 -> -x;
            default -> z;
        };
    }

    public static double rotEntityX(double x, double z, int turns) {
        return switch (turns & 3) {
            case 1 -> 1.0D - z;
            case 2 -> 1.0D - x;
            case 3 -> z;
            default -> x;
        };
    }

    public static double rotEntityZ(double x, double z, int turns) {
        return switch (turns & 3) {
            case 1 -> x;
            case 2 -> 1.0D - z;
            case 3 -> 1.0D - x;
            default -> z;
        };
    }

    public static int[] rotatedWorldBox(AxiomBlueprint427.PieceTemplate piece, int ox, int oy, int oz, int turns) {
        int c1x = rotX(piece.minX, piece.minZ, turns);
        int c1z = rotZ(piece.minX, piece.minZ, turns);
        int c2x = rotX(piece.maxX, piece.maxZ, turns);
        int c2z = rotZ(piece.maxX, piece.maxZ, turns);
        return new int[] {
            ox + Math.min(c1x, c2x),
            oy + piece.minY,
            oz + Math.min(c1z, c2z),
            ox + Math.max(c1x, c2x),
            oy + piece.maxY,
            oz + Math.max(c1z, c2z)
        };
    }

    public static boolean overlaps(int[] a, int[] b) {
        return !(a[3] < b[0] || a[0] > b[3]
                || a[4] < b[1] || a[1] > b[4]
                || a[5] < b[2] || a[2] > b[5]);
    }

    private static int[] boxOf(PlannedPiece p) {
        return new int[] { p.minX, p.minY, p.minZ, p.maxX, p.maxY, p.maxZ };
    }

    public static Plan generate(DungeonBlueprints427.Library lib, int originX, int originY, int originZ, long seed) {
        if (lib == null || lib.startPiece == null) {
            throw new IllegalStateException("No start blueprint available for dungeon generation");
        }
        DungeonConfig427 cfg = lib.config != null ? lib.config : DungeonConfig427.defaults(null);
        AxiomBlueprint427.PieceTemplate start = lib.startPiece;

        if (lib.biomeProgression) {
            return generateBiomePlan(lib, cfg, start, originX, originY, originZ, seed);
        }

        // Flat single-pool generation (when filter_mode is tag:..., prefix:..., all, or single-theme folder)
        int maxAttempts = lib.connectors.isEmpty() ? 1 : 25;
        int startSeals = (cfg.sealStartEntrance && start.entrance != null && lib.hasExternalPieces()) ? 1 : 0;
        boolean hasCaps = !lib.deadEnds.isEmpty();
        Plan best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            boolean strictCaps = hasCaps && (attempt < 20 || (best != null && best.pieces.size() > 1));
            long attemptSeed = seed ^ ((long) attempt * 0x9E3779B97F4A7C15L);
            Plan cand = tryGenerateFlat(lib, cfg, start, originX, originY, originZ, attemptSeed, strictCaps);
            int nonStartSeals = Math.max(0, cand.seals.size() - startSeals);
            boolean sizeOk = cand.pieces.size() >= cfg.minPieces && cand.pieces.size() <= cfg.maxPieces;
            if (nonStartSeals == 0 && sizeOk) {
                return cand;
            }
            int score = (nonStartSeals == 0 ? 500000 : -nonStartSeals * 50000)
                    + (sizeOk ? 20000 : 0)
                    + Math.min(cand.pieces.size(), cfg.maxPieces);
            if (best == null || score > bestScore) {
                best = cand;
                bestScore = score;
            }
        }
        return best;
    }

    private static Plan generateBiomePlan(DungeonBlueprints427.Library lib, DungeonConfig427 cfg,
            AxiomBlueprint427.PieceTemplate start, int originX, int originY, int originZ, long seed) {
        boolean needInfernal = lib.hasTheme(DungeonBlueprints427.THEME_INFERNAL);
        boolean needFlesh = lib.hasTheme(DungeonBlueprints427.THEME_FLESH);
        int wantCryptMin = Math.max(4, (int) Math.round(cfg.minPieces * 0.34D));
        int wantDeepMin = Math.max(3, cfg.minPieces / 7);
        int desiredDeepDist = (int) Math.min(Math.round(cfg.maxRadius * 0.45D), Math.max(12L, cfg.maxPieces));
        long desiredDeepDistSq = (long) desiredDeepDist * (long) desiredDeepDist;
        int startSeals = (cfg.sealStartEntrance && start.entrance != null && lib.hasExternalPieces()) ? 1 : 0;
        boolean hasCaps = !lib.deadEnds.isEmpty();

        int maxAttempts = 40;
        Plan best = null;
        int bestScore = Integer.MIN_VALUE;
        int goodCount = 0;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            boolean strictCaps = hasCaps && (attempt < 35 || (best != null && best.pieces.size() >= 3));
            long attemptSeed = seed ^ ((long) attempt * 0x9E3779B97F4A7C15L);
            Plan cand = tryGenerateBiome(lib, cfg, start, originX, originY, originZ, attemptSeed, strictCaps);
            int nonStartSeals = Math.max(0, cand.seals.size() - startSeals);
            boolean sizeOk = cand.pieces.size() >= cfg.minPieces && cand.pieces.size() <= cfg.maxPieces;
            boolean biomesOk = cand.cryptCount >= wantCryptMin
                    && (!needInfernal || cand.infernalCount >= wantDeepMin)
                    && (!needFlesh || cand.fleshCount >= wantDeepMin);

            long minDeepDistSq = Long.MAX_VALUE;
            int minDeepDepth = Integer.MAX_VALUE;
            for (int i = 1; i < cand.pieces.size(); i++) {
                PlannedPiece pp = cand.pieces.get(i);
                if (!DungeonBlueprints427.THEME_CRYPT.equals(pp.biome)) {
                    long cx = ((long) pp.minX + pp.maxX) / 2L - originX;
                    long cz = ((long) pp.minZ + pp.maxZ) / 2L - originZ;
                    long d2 = cx * cx + cz * cz;
                    if (d2 < minDeepDistSq) minDeepDistSq = d2;
                    if (pp.depth < minDeepDepth) minDeepDepth = pp.depth;
                }
            }
            boolean farOk = minDeepDistSq == Long.MAX_VALUE || minDeepDistSq >= desiredDeepDistSq;
            if (nonStartSeals == 0 && sizeOk && biomesOk && farOk) {
                goodCount++;
                if (goodCount >= 3 || cand.cryptCount >= (int) Math.round(cfg.maxPieces * 0.38D)) {
                    return cand;
                }
            }

            int minTheme = cand.cryptCount;
            if (needInfernal) minTheme = Math.min(minTheme, cand.infernalCount);
            if (needFlesh) minTheme = Math.min(minTheme, cand.fleshCount);
            int deepDistScore = minDeepDistSq == Long.MAX_VALUE ? 0
                    : (int) Math.min(400L, Math.round(Math.sqrt(minDeepDistSq)));
            int score = (nonStartSeals == 0 ? 500000 : -nonStartSeals * 50000)
                    + (sizeOk ? 50000 : 0)
                    + (biomesOk ? 20000 : 0)
                    + minTheme * 500
                    + deepDistScore * 20
                    + (minDeepDepth == Integer.MAX_VALUE ? 0 : Math.min(12, minDeepDepth) * 40)
                    + Math.min(cand.pieces.size(), cfg.maxPieces);
            if (best == null || score > bestScore) {
                best = cand;
                bestScore = score;
            }
        }
        return best;
    }

    private static Plan tryGenerateBiome(DungeonBlueprints427.Library lib, DungeonConfig427 cfg,
            AxiomBlueprint427.PieceTemplate start, int originX, int originY, int originZ, long seed,
            boolean strictCaps) {
        Random rnd = new Random(seed);
        List<PlannedPiece> placed = new ArrayList<>();
        List<int[]> boxes = new ArrayList<>();
        List<OpenExit> openExits = new ArrayList<>();
        List<Seal> seals = new ArrayList<>();
        Map<String, Integer> usedCounts = new HashMap<>();

        boolean hasInfernal = lib.hasTheme(DungeonBlueprints427.THEME_INFERNAL);
        boolean hasFlesh = lib.hasTheme(DungeonBlueprints427.THEME_FLESH);

        // Larger crypt core & farther deep biomes:
        //   maxPieces=110 -> transitionDepth=8, minCrypt=46, minDist=120 blocks
        //   maxPieces=95  -> transitionDepth=7, minCrypt=40, minDist=109 blocks
        //   maxPieces=80  -> transitionDepth=6, minCrypt=34, minDist=92 blocks
        int transitionDepth = Math.max(4, Math.min(8, cfg.maxPieces / 12));
        int minCryptBeforeDeep = Math.max(4, (int) Math.round(cfg.maxPieces * 0.42D));
        int minDist = (int) Math.min(Math.round(cfg.maxRadius * 0.50D), Math.max(12L, Math.round(cfg.maxPieces * 1.15D)));
        long minDistSq = (long) minDist * (long) minDist;
        int innerSafeRadius = (int) Math.round(minDist * 0.85D);
        long innerSafeSq = (long) innerSafeRadius * (long) innerSafeRadius;

        int minBX = originX - cfg.maxRadius;
        int maxBX = originX + cfg.maxRadius;
        int minBZ = originZ - cfg.maxRadius;
        int maxBZ = originZ + cfg.maxRadius;
        int minBY = originY + cfg.minY;
        int maxBY = originY + cfg.maxY;

        int[] startBox = rotatedWorldBox(start, originX, originY, originZ, 0);
        placed.add(new PlannedPiece(start.key, originX, originY, originZ, 0,
                startBox[0], startBox[1], startBox[2], startBox[3], startBox[4], startBox[5],
                "start", 0));
        boxes.add(startBox);

        for (AxiomBlueprint427.Marker ex : start.exits) {
            OpenExit oe = new OpenExit(originX + ex.x, originY + ex.y, originZ + ex.z,
                    ex.dir, 0, DungeonBlueprints427.THEME_CRYPT);
            if (strictCaps) {
                PlannedPiece cap = findCapAvoiding(lib, oe.rx, oe.ry, oe.rz, oe.outDir, oe.biome, 1,
                        minBX, maxBX, minBY, maxBY, minBZ, maxBZ, boxes, null, null, openExits, -1, null,
                        true, false, rnd);
                if (cap != null) {
                    oe.reservedCap = cap;
                    oe.reservedBox = boxOf(cap);
                }
            }
            openExits.add(oe);
        }
        if (cfg.sealStartEntrance && start.entrance != null && lib.hasExternalPieces()) {
            seals.add(new Seal(originX + start.entrance.x, originY + start.entrance.y,
                    originZ + start.entrance.z, start.entrance.dir));
        }

        int placedCrypt = 0;
        int placedInfernal = 0;
        int placedFlesh = 0;
        boolean useCapsBiome = strictCaps && !lib.deadEnds.isEmpty();

        while (!openExits.isEmpty() && (useCapsBiome
                ? placed.size() + openExits.size() < cfg.maxPieces
                : placed.size() < cfg.maxPieces)) {
            int pickIdx = pickNextBiomeExit(openExits, rnd, transitionDepth, minCryptBeforeDeep,
                    minDistSq, originX, originZ, placedCrypt, placedInfernal, placedFlesh,
                    hasInfernal, hasFlesh);
            OpenExit cur = openExits.remove(pickIdx);

            String targetBiome = resolveTargetBiome(cur, rnd, transitionDepth, minCryptBeforeDeep,
                    minDistSq, originX, originZ, placedCrypt, placedInfernal, placedFlesh,
                    openExits, hasInfernal, hasFlesh);

            List<AxiomBlueprint427.PieceTemplate> rawConn = lib.connectorsForTheme(targetBiome);
            if (rawConn.isEmpty()) rawConn = lib.connectors;
            List<AxiomBlueprint427.PieceTemplate> pool = new ArrayList<>(rawConn);
            Collections.shuffle(pool, rnd);

            int openSameBiome = countOpenForBiome(openExits, targetBiome);
            int placedInBiome = countForBiome(targetBiome, placedCrypt, placedInfernal, placedFlesh);
            boolean isCrypt = DungeonBlueprints427.THEME_CRYPT.equals(targetBiome);
            boolean earlyCryptBranch = isCrypt && placedCrypt < minCryptBeforeDeep && openSameBiome < 3;
            boolean earlyDeepBranch = !isCrypt
                    && openSameBiome < 2 && placedInBiome < 6
                    && placed.size() + openExits.size() < cfg.maxPieces - 8;

            final int curPlaced = placed.size();
            final int curOpen = openExits.size();
            Map<String, Double> jitter = new HashMap<>(pool.size() * 2);
            for (AxiomBlueprint427.PieceTemplate p : pool) {
                double s = usedCounts.getOrDefault(p.key, 0) * 14.0D;
                int exCount = p.exits.size();
                if (earlyCryptBranch && exCount >= 2) s -= 18.0D;
                else if (earlyDeepBranch && exCount >= 2) s -= 14.0D;
                else if (curPlaced < 14 && curOpen < 3 && exCount >= 2) s -= 8.0D;
                else if (curPlaced + curOpen >= cfg.maxPieces - 6 && exCount >= 2) s += 22.0D;
                else if (openSameBiome >= 4 && exCount >= 2) s += 14.0D;
                s += rnd.nextDouble() * 5.0D;
                jitter.put(p.key, s);
            }
            pool.sort(Comparator.comparingDouble(p -> jitter.getOrDefault(p.key, 0.0D)));

            int slotsRemaining = cfg.maxPieces - placed.size() - openExits.size();
            PlacementAttempt chosen = null;
            PlacementAttempt nearFallback = null;

            for (AxiomBlueprint427.PieceTemplate cand : pool) {
                if (useCapsBiome && hasCapsFor(lib, targetBiome, true) && 1 + cand.exits.size() > slotsRemaining) {
                    continue;
                }
                PlacementAttempt pa = tryPlaceWithCaps(lib, cand, cur, targetBiome, cur.depth + 1,
                        slotsRemaining, minBX, maxBX, minBY, maxBY, minBZ, maxBZ, boxes, openExits,
                        true, strictCaps, rnd);
                if (pa == null) continue;
                if (!isCrypt && centerDistSq(pa.main, originX, originZ) < innerSafeSq) {
                    if (nearFallback == null) nearFallback = pa;
                    continue;
                }
                chosen = pa;
                break;
            }
            if (chosen == null) chosen = nearFallback;

            if (chosen != null) {
                for (Map.Entry<Integer, PlannedPiece> e : chosen.otherReReservations.entrySet()) {
                    OpenExit other = openExits.get(e.getKey());
                    other.reservedCap = e.getValue();
                    other.reservedBox = boxOf(e.getValue());
                }
                for (int ai = 0; ai < chosen.addedPieces.size(); ai++) {
                    PlannedPiece pp = chosen.addedPieces.get(ai);
                    String key = chosen.addedKeys.get(ai);
                    placed.add(pp);
                    boxes.add(boxOf(pp));
                    usedCounts.put(key, usedCounts.getOrDefault(key, 0) + 1);
                    if (DungeonBlueprints427.THEME_CRYPT.equals(pp.biome)) placedCrypt++;
                    else if (DungeonBlueprints427.THEME_INFERNAL.equals(pp.biome)) placedInfernal++;
                    else if (DungeonBlueprints427.THEME_FLESH.equals(pp.biome)) placedFlesh++;
                }
                openExits.addAll(chosen.newExits);
            } else {
                // Close this branch cleanly with a matching dead-end room instead of a seal wall.
                PlannedPiece cap = findCapAvoiding(lib, cur.rx, cur.ry, cur.rz, cur.outDir, cur.biome,
                        cur.depth + 1, minBX, maxBX, minBY, maxBY, minBZ, maxBZ,
                        boxes, null, null, openExits, -1, null, true, true, rnd);
                if (cap == null) cap = cur.reservedCap;
                if (cap != null) {
                    placed.add(cap);
                    boxes.add(boxOf(cap));
                    if (DungeonBlueprints427.THEME_CRYPT.equals(cap.biome)) placedCrypt++;
                    else if (DungeonBlueprints427.THEME_INFERNAL.equals(cap.biome)) placedInfernal++;
                    else if (DungeonBlueprints427.THEME_FLESH.equals(cap.biome)) placedFlesh++;
                } else {
                    seals.add(new Seal(cur.rx, cur.ry, cur.rz, cur.outDir));
                }
            }
        }

        // Cap all remaining open exits with dead-end rooms strictly matching their branch's biome.
        for (int i = 0; i < openExits.size(); i++) {
            OpenExit cur = openExits.get(i);
            if (placed.size() < cfg.maxPieces) {
                PlannedPiece cap = findCapAvoiding(lib, cur.rx, cur.ry, cur.rz, cur.outDir, cur.biome,
                        cur.depth + 1, minBX, maxBX, minBY, maxBY, minBZ, maxBZ,
                        boxes, null, null, openExits, i, null, true, true, rnd);
                if (cap == null) cap = cur.reservedCap;
                if (cap != null) {
                    placed.add(cap);
                    boxes.add(boxOf(cap));
                    cur.reservedCap = cap;
                    cur.reservedBox = boxOf(cap);
                    continue;
                }
            } else if (cur.reservedCap != null) {
                placed.add(cur.reservedCap);
                boxes.add(boxOf(cur.reservedCap));
                continue;
            }
            seals.add(new Seal(cur.rx, cur.ry, cur.rz, cur.outDir));
        }

        return buildPlan(start, originX, originY, originZ, placed, seals);
    }

    private static Plan tryGenerateFlat(DungeonBlueprints427.Library lib, DungeonConfig427 cfg,
            AxiomBlueprint427.PieceTemplate start, int originX, int originY, int originZ, long seed,
            boolean strictCaps) {
        Random rnd = new Random(seed);
        List<PlannedPiece> placed = new ArrayList<>();
        List<int[]> boxes = new ArrayList<>();
        List<OpenExit> openExits = new ArrayList<>();
        List<Seal> seals = new ArrayList<>();
        Map<String, Integer> usedCounts = new HashMap<>();

        int minBX = originX - cfg.maxRadius;
        int maxBX = originX + cfg.maxRadius;
        int minBZ = originZ - cfg.maxRadius;
        int maxBZ = originZ + cfg.maxRadius;
        int minBY = originY + cfg.minY;
        int maxBY = originY + cfg.maxY;

        int[] startBox = rotatedWorldBox(start, originX, originY, originZ, 0);
        placed.add(new PlannedPiece(start.key, originX, originY, originZ, 0,
                startBox[0], startBox[1], startBox[2], startBox[3], startBox[4], startBox[5],
                "start", 0));
        boxes.add(startBox);

        String startTheme = DungeonBlueprints427.themeOf(start);
        for (AxiomBlueprint427.Marker ex : start.exits) {
            OpenExit oe = new OpenExit(originX + ex.x, originY + ex.y, originZ + ex.z,
                    ex.dir, 0, startTheme);
            if (strictCaps) {
                PlannedPiece cap = findCapAvoiding(lib, oe.rx, oe.ry, oe.rz, oe.outDir, oe.biome, 1,
                        minBX, maxBX, minBY, maxBY, minBZ, maxBZ, boxes, null, null, openExits, -1, null,
                        false, false, rnd);
                if (cap != null) {
                    oe.reservedCap = cap;
                    oe.reservedBox = boxOf(cap);
                }
            }
            openExits.add(oe);
        }
        if (cfg.sealStartEntrance && start.entrance != null && lib.hasExternalPieces()) {
            seals.add(new Seal(originX + start.entrance.x, originY + start.entrance.y,
                    originZ + start.entrance.z, start.entrance.dir));
        }

        boolean useCapsFlat = strictCaps && !lib.deadEnds.isEmpty();
        while (!openExits.isEmpty() && (useCapsFlat
                ? placed.size() + openExits.size() < cfg.maxPieces
                : placed.size() < cfg.maxPieces)) {
            int pickIdx = rnd.nextDouble() < 0.75D ? 0 : rnd.nextInt(openExits.size());
            OpenExit cur = openExits.remove(pickIdx);

            List<AxiomBlueprint427.PieceTemplate> pool = new ArrayList<>(lib.connectors);
            Collections.shuffle(pool, rnd);

            final int curPlaced = placed.size();
            final int curOpen = openExits.size();
            Map<String, Double> jitter = new HashMap<>(pool.size() * 2);
            for (AxiomBlueprint427.PieceTemplate p : pool) {
                double s = usedCounts.getOrDefault(p.key, 0) * 10.0D;
                int exCount = p.exits.size();
                if (curPlaced < 12 && curOpen < 3 && exCount >= 2) s -= 8.0D;
                else if (curPlaced + curOpen >= cfg.maxPieces - 4 && exCount >= 2) s += 20.0D;
                else if (curOpen >= 4 && exCount >= 2) s += 12.0D;
                s += rnd.nextDouble() * 5.0D;
                jitter.put(p.key, s);
            }
            pool.sort(Comparator.comparingDouble(p -> jitter.getOrDefault(p.key, 0.0D)));

            int slotsRemaining = cfg.maxPieces - placed.size() - openExits.size();
            PlacementAttempt chosen = null;

            for (AxiomBlueprint427.PieceTemplate cand : pool) {
                if (strictCaps && !lib.deadEnds.isEmpty() && 1 + cand.exits.size() > slotsRemaining) continue;
                String candBiome = DungeonBlueprints427.themeOf(cand);
                PlacementAttempt pa = tryPlaceWithCaps(lib, cand, cur, candBiome, cur.depth + 1,
                        slotsRemaining, minBX, maxBX, minBY, maxBY, minBZ, maxBZ, boxes, openExits,
                        false, strictCaps, rnd);
                if (pa != null) {
                    chosen = pa;
                    break;
                }
            }

            if (chosen != null) {
                for (Map.Entry<Integer, PlannedPiece> e : chosen.otherReReservations.entrySet()) {
                    OpenExit other = openExits.get(e.getKey());
                    other.reservedCap = e.getValue();
                    other.reservedBox = boxOf(e.getValue());
                }
                for (int ai = 0; ai < chosen.addedPieces.size(); ai++) {
                    PlannedPiece pp = chosen.addedPieces.get(ai);
                    String key = chosen.addedKeys.get(ai);
                    placed.add(pp);
                    boxes.add(boxOf(pp));
                    usedCounts.put(key, usedCounts.getOrDefault(key, 0) + 1);
                }
                openExits.addAll(chosen.newExits);
            } else {
                PlannedPiece cap = findCapAvoiding(lib, cur.rx, cur.ry, cur.rz, cur.outDir, cur.biome,
                        cur.depth + 1, minBX, maxBX, minBY, maxBY, minBZ, maxBZ,
                        boxes, null, null, openExits, -1, null, false, true, rnd);
                if (cap == null) cap = cur.reservedCap;
                if (cap != null) {
                    placed.add(cap);
                    boxes.add(boxOf(cap));
                } else {
                    seals.add(new Seal(cur.rx, cur.ry, cur.rz, cur.outDir));
                }
            }
        }

        for (int i = 0; i < openExits.size(); i++) {
            OpenExit cur = openExits.get(i);
            if (placed.size() < cfg.maxPieces) {
                PlannedPiece cap = findCapAvoiding(lib, cur.rx, cur.ry, cur.rz, cur.outDir, cur.biome,
                        cur.depth + 1, minBX, maxBX, minBY, maxBY, minBZ, maxBZ,
                        boxes, null, null, openExits, i, null, false, true, rnd);
                if (cap == null) cap = cur.reservedCap;
                if (cap != null) {
                    placed.add(cap);
                    boxes.add(boxOf(cap));
                    cur.reservedCap = cap;
                    cur.reservedBox = boxOf(cap);
                    continue;
                }
            } else if (cur.reservedCap != null) {
                placed.add(cur.reservedCap);
                boxes.add(boxOf(cur.reservedCap));
                continue;
            }
            seals.add(new Seal(cur.rx, cur.ry, cur.rz, cur.outDir));
        }

        return buildPlan(start, originX, originY, originZ, placed, seals);
    }

    private static PlacementAttempt tryPlaceWithCaps(
            DungeonBlueprints427.Library lib,
            AxiomBlueprint427.PieceTemplate cand,
            OpenExit cur,
            String biome,
            int depth,
            int slotsRemaining,
            int minBX,
            int maxBX,
            int minBY,
            int maxBY,
            int minBZ,
            int maxBZ,
            List<int[]> boxes,
            List<OpenExit> openExits,
            boolean strictThemeCaps,
            boolean strictCaps,
            Random rnd) {
        int wantDir = (cur.outDir + 2) & 3;
        PlannedPiece fit = tryFit(cand, cur, wantDir, minBX, maxBX, minBY, maxBY, minBZ, maxBZ,
                boxes, biome, depth);
        if (fit == null) return null;

        if (!strictCaps || !hasCapsFor(lib, biome, strictThemeCaps)) {
            PlacementAttempt pa = new PlacementAttempt(fit, cand.key);
            for (AxiomBlueprint427.Marker ex : cand.exits) {
                int rx = fit.ox + rotX(ex.x, ex.z, fit.turns);
                int rz = fit.oz + rotZ(ex.x, ex.z, fit.turns);
                pa.newExits.add(new OpenExit(rx, fit.oy + ex.y, rz,
                        (ex.dir + fit.turns) & 3, depth, biome));
            }
            return pa;
        }

        int[] wbb = boxOf(fit);

        // Keep every existing open exit safely capable of terminating in a dead-end room.
        Map<Integer, int[]> reResBoxes = new HashMap<>();
        Map<Integer, PlannedPiece> reResCaps = new HashMap<>();
        for (int i = 0; i < openExits.size(); i++) {
            OpenExit other = openExits.get(i);
            if (other.reservedBox != null && overlaps(wbb, other.reservedBox)) {
                PlannedPiece alt = findCapAvoiding(lib, other.rx, other.ry, other.rz, other.outDir,
                        other.biome, other.depth + 1, minBX, maxBX, minBY, maxBY, minBZ, maxBZ,
                        boxes, wbb, null, openExits, i, reResBoxes, strictThemeCaps, false, rnd);
                if (alt == null) {
                    return null;
                }
                reResCaps.put(i, alt);
                reResBoxes.put(i, boxOf(alt));
            }
        }

        PlacementAttempt pa = new PlacementAttempt(fit, cand.key);
        pa.otherReReservations.putAll(reResCaps);
        List<int[]> newCapBoxes = new ArrayList<>();
        List<AxiomBlueprint427.Marker> missingCapsFor = new ArrayList<>();

        for (AxiomBlueprint427.Marker ex : cand.exits) {
            int rx = fit.ox + rotX(ex.x, ex.z, fit.turns);
            int ry = fit.oy + ex.y;
            int rz = fit.oz + rotZ(ex.x, ex.z, fit.turns);
            int eDir = (ex.dir + fit.turns) & 3;
            PlannedPiece cap = findCapAvoiding(lib, rx, ry, rz, eDir, biome, depth + 1,
                    minBX, maxBX, minBY, maxBY, minBZ, maxBZ,
                    boxes, wbb, newCapBoxes, openExits, -1, reResBoxes, strictThemeCaps, false, rnd);
            if (cap != null) {
                OpenExit ne = new OpenExit(rx, ry, rz, eDir, depth, biome);
                ne.reservedCap = cap;
                ne.reservedBox = boxOf(cap);
                newCapBoxes.add(ne.reservedBox);
                pa.newExits.add(ne);
            } else {
                missingCapsFor.add(ex);
            }
        }

        // Handle tight multi-exit pieces (e.g. Перекрёсток 1) where adjacent dead-ends clip corners:
        // extend the conflicting exit by one narrow 1-exit corridor that safely leads to a dead-end.
        if (!missingCapsFor.isEmpty()) {
            List<AxiomBlueprint427.PieceTemplate> bridges = shortestBridgeCorridors(lib, biome, strictThemeCaps);
            if (bridges.isEmpty()) return null;
            List<int[]> allOtherCaps = new ArrayList<>(openExits.size());
            for (int i = 0; i < openExits.size(); i++) {
                int[] b = reResBoxes.containsKey(i) ? reResBoxes.get(i) : openExits.get(i).reservedBox;
                if (b != null) allOtherCaps.add(b);
            }
            for (int mi = 0; mi < missingCapsFor.size(); mi++) {
                int remAfter = missingCapsFor.size() - (mi + 1);
                if (pa.addedPieces.size() + pa.newExits.size() + 2 + remAfter > slotsRemaining) {
                    return null;
                }
                AxiomBlueprint427.Marker ex = missingCapsFor.get(mi);
                int rx = fit.ox + rotX(ex.x, ex.z, fit.turns);
                int ry = fit.oy + ex.y;
                int rz = fit.oz + rotZ(ex.x, ex.z, fit.turns);
                int eDir = (ex.dir + fit.turns) & 3;
                boolean bridged = false;

                for (AxiomBlueprint427.PieceTemplate br : bridges) {
                    PlannedPiece bfit = fitExtra(br, rx, ry, rz, eDir,
                            minBX, maxBX, minBY, maxBY, minBZ, maxBZ,
                            boxes, pa.addedPieces, newCapBoxes, allOtherCaps, biome, depth + 1);
                    if (bfit == null) continue;

                    AxiomBlueprint427.Marker bex = br.exits.get(0);
                    int brx = bfit.ox + rotX(bex.x, bex.z, bfit.turns);
                    int bry = bfit.oy + bex.y;
                    int brz = bfit.oz + rotZ(bex.x, bex.z, bfit.turns);
                    int beDir = (bex.dir + bfit.turns) & 3;

                    List<int[]> extraForCap = new ArrayList<>(newCapBoxes.size() + pa.addedPieces.size());
                    extraForCap.addAll(newCapBoxes);
                    for (PlannedPiece ap : pa.addedPieces) extraForCap.add(boxOf(ap));

                    PlannedPiece cap = findCapAvoiding(lib, brx, bry, brz, beDir, biome, depth + 2,
                            minBX, maxBX, minBY, maxBY, minBZ, maxBZ,
                            boxes, boxOf(bfit), extraForCap, openExits, -1, reResBoxes,
                            strictThemeCaps, false, rnd);
                    if (cap != null) {
                        pa.addedPieces.add(bfit);
                        pa.addedKeys.add(br.key);
                        OpenExit ne = new OpenExit(brx, bry, brz, beDir, depth + 1, biome);
                        ne.reservedCap = cap;
                        ne.reservedBox = boxOf(cap);
                        newCapBoxes.add(ne.reservedBox);
                        pa.newExits.add(ne);
                        bridged = true;
                        break;
                    }
                }
                if (!bridged) return null;
            }
        }

        return pa;
    }

    private static boolean hasCapsFor(DungeonBlueprints427.Library lib, String biome, boolean strictTheme) {
        if (strictTheme) {
            List<AxiomBlueprint427.PieceTemplate> themed = lib.deadEndsForTheme(biome);
            if (!themed.isEmpty()) return true;
        }
        return !lib.deadEnds.isEmpty();
    }

    private static List<AxiomBlueprint427.PieceTemplate> sortedCaps(
            DungeonBlueprints427.Library lib, String biome, boolean strictTheme, boolean randomize, Random rnd) {
        List<AxiomBlueprint427.PieceTemplate> themed = (biome == null || biome.isEmpty())
                ? Collections.emptyList() : lib.deadEndsForTheme(biome);
        List<AxiomBlueprint427.PieceTemplate> out;
        if (strictTheme) {
            out = new ArrayList<>(themed.isEmpty() ? lib.deadEnds : themed);
            if (randomize && out.size() > 1) {
                Collections.shuffle(out, rnd);
            } else {
                out.sort(Comparator.comparingLong(DungeonGenerator427::pieceVolume));
            }
            return out;
        }
        if (!themed.isEmpty() && themed.size() < lib.deadEnds.size()) {
            List<AxiomBlueprint427.PieceTemplate> head = new ArrayList<>(themed);
            List<AxiomBlueprint427.PieceTemplate> tail = new ArrayList<>(lib.deadEnds.size());
            for (AxiomBlueprint427.PieceTemplate p : lib.deadEnds) {
                if (!themed.contains(p)) tail.add(p);
            }
            if (randomize) {
                Collections.shuffle(head, rnd);
                Collections.shuffle(tail, rnd);
            } else {
                head.sort(Comparator.comparingLong(DungeonGenerator427::pieceVolume));
                tail.sort(Comparator.comparingLong(DungeonGenerator427::pieceVolume));
            }
            head.addAll(tail);
            return head;
        }
        out = new ArrayList<>(lib.deadEnds);
        if (randomize && out.size() > 1) {
            Collections.shuffle(out, rnd);
        } else {
            out.sort(Comparator.comparingLong(DungeonGenerator427::pieceVolume));
        }
        return out;
    }

    private static long pieceVolume(AxiomBlueprint427.PieceTemplate p) {
        return (long) (p.maxX - p.minX + 1) * (long) (p.maxY - p.minY + 1) * (long) (p.maxZ - p.minZ + 1);
    }

    private static List<AxiomBlueprint427.PieceTemplate> shortestBridgeCorridors(
            DungeonBlueprints427.Library lib, String biome, boolean strictTheme) {
        List<AxiomBlueprint427.PieceTemplate> src = strictTheme
                ? lib.connectorsForTheme(biome) : lib.connectors;
        if (src.isEmpty()) src = lib.connectors;
        List<AxiomBlueprint427.PieceTemplate> out = new ArrayList<>();
        for (AxiomBlueprint427.PieceTemplate p : src) {
            if (p.exits.size() == 1 && (p.maxX - p.minX + 1) <= 13 && (p.maxZ - p.minZ + 1) <= 22) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparingLong(p ->
                (long) (p.maxX - p.minX + 1) * (long) (p.maxZ - p.minZ + 1)));
        return out;
    }

    private static PlannedPiece findCapAvoiding(
            DungeonBlueprints427.Library lib,
            int rx,
            int ry,
            int rz,
            int outDir,
            String biome,
            int depth,
            int minBX,
            int maxBX,
            int minBY,
            int maxBY,
            int minBZ,
            int maxBZ,
            List<int[]> boxes,
            int[] extraBox1,
            List<int[]> extraBoxes2,
            List<OpenExit> openExits,
            int skipOpenIdx,
            Map<Integer, int[]> overrides,
            boolean strictTheme,
            boolean randomize,
            Random rnd) {
        List<AxiomBlueprint427.PieceTemplate> caps = sortedCaps(lib, biome, strictTheme, randomize, rnd);
        if (caps.isEmpty()) return null;

        List<int[]> otherCaps = new ArrayList<>(openExits.size());
        for (int i = 0; i < openExits.size(); i++) {
            if (i == skipOpenIdx) continue;
            int[] ob = (overrides != null && overrides.containsKey(i))
                    ? overrides.get(i) : openExits.get(i).reservedBox;
            if (ob != null) otherCaps.add(ob);
        }

        int wantDir = (outDir + 2) & 3;
        for (AxiomBlueprint427.PieceTemplate de : caps) {
            if (de == null || de.entrance == null) continue;
            int turns = (wantDir - de.entrance.dir) & 3;
            int lx = rotX(de.entrance.x, de.entrance.z, turns);
            int lz = rotZ(de.entrance.x, de.entrance.z, turns);
            int ox = rx + AxiomBlueprint427.DX[outDir] - lx;
            int oy = ry - de.entrance.y;
            int oz = rz + AxiomBlueprint427.DZ[outDir] - lz;

            int[] wbb = rotatedWorldBox(de, ox, oy, oz, turns);
            if (wbb[0] < minBX || wbb[3] > maxBX
                    || wbb[2] < minBZ || wbb[5] > maxBZ
                    || wbb[1] < minBY || wbb[4] > maxBY) {
                continue;
            }
            if (extraBox1 != null && overlaps(wbb, extraBox1)) continue;
            boolean clash = false;
            for (int[] b : boxes) {
                if (overlaps(wbb, b)) {
                    clash = true;
                    break;
                }
            }
            if (clash) continue;
            if (extraBoxes2 != null) {
                for (int[] b : extraBoxes2) {
                    if (b != null && overlaps(wbb, b)) {
                        clash = true;
                        break;
                    }
                }
                if (clash) continue;
            }
            for (int[] b : otherCaps) {
                if (overlaps(wbb, b)) {
                    clash = true;
                    break;
                }
            }
            if (clash) continue;
            return new PlannedPiece(de.key, ox, oy, oz, turns,
                    wbb[0], wbb[1], wbb[2], wbb[3], wbb[4], wbb[5], biome, depth);
        }
        return null;
    }

    private static PlannedPiece fitExtra(
            AxiomBlueprint427.PieceTemplate cand,
            int rx,
            int ry,
            int rz,
            int outDir,
            int minBX,
            int maxBX,
            int minBY,
            int maxBY,
            int minBZ,
            int maxBZ,
            List<int[]> boxes,
            List<PlannedPiece> extraPlaced,
            List<int[]> extraBoxes2,
            List<int[]> extraBoxes3,
            String biome,
            int depth) {
        if (cand == null || cand.entrance == null) return null;
        int wantDir = (outDir + 2) & 3;
        int turns = (wantDir - cand.entrance.dir) & 3;
        int lx = rotX(cand.entrance.x, cand.entrance.z, turns);
        int lz = rotZ(cand.entrance.x, cand.entrance.z, turns);
        int ox = rx + AxiomBlueprint427.DX[outDir] - lx;
        int oy = ry - cand.entrance.y;
        int oz = rz + AxiomBlueprint427.DZ[outDir] - lz;

        int[] wbb = rotatedWorldBox(cand, ox, oy, oz, turns);
        if (wbb[0] < minBX || wbb[3] > maxBX
                || wbb[2] < minBZ || wbb[5] > maxBZ
                || wbb[1] < minBY || wbb[4] > maxBY) {
            return null;
        }
        for (int[] b : boxes) {
            if (overlaps(wbb, b)) return null;
        }
        if (extraPlaced != null) {
            for (PlannedPiece ep : extraPlaced) {
                if (overlaps(wbb, boxOf(ep))) return null;
            }
        }
        if (extraBoxes2 != null) {
            for (int[] b : extraBoxes2) {
                if (b != null && overlaps(wbb, b)) return null;
            }
        }
        if (extraBoxes3 != null) {
            for (int[] b : extraBoxes3) {
                if (b != null && overlaps(wbb, b)) return null;
            }
        }
        return new PlannedPiece(cand.key, ox, oy, oz, turns,
                wbb[0], wbb[1], wbb[2], wbb[3], wbb[4], wbb[5], biome, depth);
    }

    private static long centerDistSq(PlannedPiece p, int originX, int originZ) {
        long cx = ((long) p.minX + p.maxX) / 2L - originX;
        long cz = ((long) p.minZ + p.maxZ) / 2L - originZ;
        return cx * cx + cz * cz;
    }

    private static int pickNextBiomeExit(
            List<OpenExit> openExits,
            Random rnd,
            int transitionDepth,
            int minCryptBeforeDeep,
            long minDistSq,
            int originX,
            int originZ,
            int placedCrypt,
            int placedInfernal,
            int placedFlesh,
            boolean hasInfernal,
            boolean hasFlesh) {
        // 1. Always finish shallow crypt exits near старт first so the entry area is 100% К
        //    and builds branches leading outward.
        int bestShallowCrypt = -1;
        int bestShallowDepth = Integer.MAX_VALUE;
        for (int i = 0; i < openExits.size(); i++) {
            OpenExit ex = openExits.get(i);
            if (DungeonBlueprints427.THEME_CRYPT.equals(ex.biome) && ex.depth + 1 < transitionDepth) {
                if (ex.depth < bestShallowDepth) {
                    bestShallowDepth = ex.depth;
                    bestShallowCrypt = i;
                }
            }
        }
        if (bestShallowCrypt >= 0) {
            return bestShallowCrypt;
        }

        // 2. While building initial crypt minimum, advance shallowest crypt exit.
        if (placedCrypt < minCryptBeforeDeep) {
            int idx = findShallowestForBiome(openExits, DungeonBlueprints427.THEME_CRYPT);
            if (idx >= 0) return idx;
        }

        int openInf = countOpenForBiome(openExits, DungeonBlueprints427.THEME_INFERNAL);
        int openFl = countOpenForBiome(openExits, DungeonBlueprints427.THEME_FLESH);
        int openCrypt = countOpenForBiome(openExits, DungeonBlueprints427.THEME_CRYPT);

        // 3. If a deep biome has not yet opened at all and a deep-ready crypt exit exists, launch it now!
        boolean needLaunchInf = hasInfernal && (placedInfernal + openInf) == 0;
        boolean needLaunchFl = hasFlesh && (placedFlesh + openFl) == 0;
        if ((needLaunchInf || needLaunchFl) && openCrypt > 0) {
            int ready = findTransitionReadyCrypt(openExits, transitionDepth, minDistSq, originX, originZ);
            if (ready >= 0) return ready;
            int anyCrypt = findShallowestForBiome(openExits, DungeonBlueprints427.THEME_CRYPT);
            if (anyCrypt >= 0) return anyCrypt;
        }

        // 4. Balance growth between active deep biomes so neither starves the other.
        if (openInf > 0 && openFl > 0) {
            String lagging = placedInfernal <= placedFlesh
                    ? DungeonBlueprints427.THEME_INFERNAL : DungeonBlueprints427.THEME_FLESH;
            if (rnd.nextDouble() < 0.85D) {
                int idx = findShallowestForBiome(openExits, lagging);
                if (idx >= 0) return idx;
            }
        } else if (openInf > 0 && hasFlesh && openCrypt > 0 && placedFlesh < placedInfernal) {
            int ready = findTransitionReadyCrypt(openExits, transitionDepth, minDistSq, originX, originZ);
            if (ready >= 0) return ready;
        } else if (openFl > 0 && hasInfernal && openCrypt > 0 && placedInfernal < placedFlesh) {
            int ready = findTransitionReadyCrypt(openExits, transitionDepth, minDistSq, originX, originZ);
            if (ready >= 0) return ready;
        }

        // 5. Fallback: BFS order most of the time, occasional randomness
        if (rnd.nextDouble() < 0.75D) {
            int best = 0;
            for (int i = 1; i < openExits.size(); i++) {
                if (openExits.get(i).depth < openExits.get(best).depth) {
                    best = i;
                }
            }
            return best;
        }
        return rnd.nextInt(openExits.size());
    }

    private static String resolveTargetBiome(
            OpenExit cur,
            Random rnd,
            int transitionDepth,
            int minCryptBeforeDeep,
            long minDistSq,
            int originX,
            int originZ,
            int placedCrypt,
            int placedInfernal,
            int placedFlesh,
            List<OpenExit> openExits,
            boolean hasInfernal,
            boolean hasFlesh) {
        if (!DungeonBlueprints427.THEME_CRYPT.equals(cur.biome)) {
            return cur.biome; // Deep biomes are strictly sticky!
        }
        if (!hasInfernal && !hasFlesh) {
            return DungeonBlueprints427.THEME_CRYPT;
        }
        long dx = cur.rx - originX;
        long dz = cur.rz - originZ;
        boolean farEnough = (dx * dx + dz * dz) >= minDistSq;
        boolean deepEnough = (cur.depth + 1) >= transitionDepth;
        boolean cryptReady = placedCrypt >= minCryptBeforeDeep;

        if (!(deepEnough && farEnough && cryptReady)) {
            return DungeonBlueprints427.THEME_CRYPT;
        }

        if (hasInfernal && !hasFlesh) return DungeonBlueprints427.THEME_INFERNAL;
        if (!hasInfernal && hasFlesh) return DungeonBlueprints427.THEME_FLESH;

        int totalInf = placedInfernal + countOpenForBiome(openExits, DungeonBlueprints427.THEME_INFERNAL);
        int totalFl = placedFlesh + countOpenForBiome(openExits, DungeonBlueprints427.THEME_FLESH);
        if (totalInf == 0 && totalFl == 0) {
            return rnd.nextBoolean() ? DungeonBlueprints427.THEME_INFERNAL : DungeonBlueprints427.THEME_FLESH;
        }
        if (totalInf == 0) return DungeonBlueprints427.THEME_INFERNAL;
        if (totalFl == 0) return DungeonBlueprints427.THEME_FLESH;
        return totalInf <= totalFl ? DungeonBlueprints427.THEME_INFERNAL : DungeonBlueprints427.THEME_FLESH;
    }

    private static int findShallowestForBiome(List<OpenExit> openExits, String biome) {
        int best = -1;
        int bestDepth = Integer.MAX_VALUE;
        for (int i = 0; i < openExits.size(); i++) {
            OpenExit ex = openExits.get(i);
            if (biome.equals(ex.biome) && ex.depth < bestDepth) {
                bestDepth = ex.depth;
                best = i;
            }
        }
        return best;
    }

    private static int findTransitionReadyCrypt(List<OpenExit> openExits, int transitionDepth,
            long minDistSq, int originX, int originZ) {
        int best = -1;
        int bestDepth = Integer.MAX_VALUE;
        for (int i = 0; i < openExits.size(); i++) {
            OpenExit ex = openExits.get(i);
            if (!DungeonBlueprints427.THEME_CRYPT.equals(ex.biome)) continue;
            if (ex.depth + 1 < transitionDepth) continue;
            long dx = ex.rx - originX;
            long dz = ex.rz - originZ;
            if (dx * dx + dz * dz < minDistSq) continue;
            if (ex.depth < bestDepth) {
                bestDepth = ex.depth;
                best = i;
            }
        }
        return best;
    }

    private static int countOpenForBiome(List<OpenExit> openExits, String biome) {
        int c = 0;
        for (OpenExit ex : openExits) {
            if (biome.equals(ex.biome)) c++;
        }
        return c;
    }

    private static int countForBiome(String biome, int cCrypt, int cInf, int cFl) {
        if (DungeonBlueprints427.THEME_CRYPT.equals(biome)) return cCrypt;
        if (DungeonBlueprints427.THEME_INFERNAL.equals(biome)) return cInf;
        if (DungeonBlueprints427.THEME_FLESH.equals(biome)) return cFl;
        return 0;
    }

    private static Plan buildPlan(AxiomBlueprint427.PieceTemplate start, int originX, int originY, int originZ,
            List<PlannedPiece> placed, List<Seal> seals) {
        double sx, sy, sz;
        float syaw;
        if (start.entrance != null) {
            int fx = AxiomBlueprint427.DX[start.entrance.dir];
            int fz = AxiomBlueprint427.DZ[start.entrance.dir];
            sx = originX + start.entrance.x - 2.0D * fx + 0.5D;
            sy = originY + start.entrance.y + 2.0D;
            sz = originZ + start.entrance.z - 2.0D * fz + 0.5D;
            syaw = yawForDir((start.entrance.dir + 2) & 3);
        } else {
            sx = originX + (start.minX + start.maxX) * 0.5D + 0.5D;
            sy = originY + start.minY + 2.0D;
            sz = originZ + (start.minZ + start.maxZ) * 0.5D + 0.5D;
            syaw = DungeonTemplate425.SPAWN_YAW;
        }
        return new Plan(placed, seals, sx, sy, sz, syaw, 0.0F);
    }

    private static PlannedPiece tryFit(AxiomBlueprint427.PieceTemplate cand, OpenExit cur, int wantLapisDir,
            int minBX, int maxBX, int minBY, int maxBY, int minBZ, int maxBZ, List<int[]> boxes,
            String biome, int depth) {
        if (cand == null || cand.entrance == null) return null;
        int turns = (wantLapisDir - cand.entrance.dir) & 3;
        int lx = rotX(cand.entrance.x, cand.entrance.z, turns);
        int lz = rotZ(cand.entrance.x, cand.entrance.z, turns);
        int ox = cur.rx + AxiomBlueprint427.DX[cur.outDir] - lx;
        int oy = cur.ry - cand.entrance.y;
        int oz = cur.rz + AxiomBlueprint427.DZ[cur.outDir] - lz;

        int[] wbb = rotatedWorldBox(cand, ox, oy, oz, turns);
        if (wbb[0] < minBX || wbb[3] > maxBX
                || wbb[2] < minBZ || wbb[5] > maxBZ
                || wbb[1] < minBY || wbb[4] > maxBY) {
            return null;
        }
        for (int[] b : boxes) {
            if (overlaps(wbb, b)) {
                return null;
            }
        }
        return new PlannedPiece(cand.key, ox, oy, oz, turns,
                wbb[0], wbb[1], wbb[2], wbb[3], wbb[4], wbb[5], biome, depth);
    }

    public static float yawForDir(int dir) {
        return switch (dir & 3) {
            case AxiomBlueprint427.SOUTH -> 0.0F;
            case AxiomBlueprint427.WEST -> 90.0F;
            case AxiomBlueprint427.NORTH -> 180.0F;
            case AxiomBlueprint427.EAST -> -90.0F;
            default -> 90.0F;
        };
    }
}
