package dev.settledlands.dungeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Build 427 / 429: deterministic procedural dungeon planner over a {@link DungeonBlueprints427.Library}.
 *
 * <p>Stitches rooms and corridors from {@code старт} by matching rotated {@code lapis_block}
 * entrances against open {@code redstone_block} exits without any bounding-box overlap inside
 * the instance's {@code 512 x 512} grid cell, caps branches with dead-end rooms ({@code 0} exits)
 * and sealed walls so there are zero void leaks, and emits a BFS-ordered {@link Plan}.</p>
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

        OpenExit(int rx, int ry, int rz, int outDir, int depth, String biome) {
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.outDir = outDir & 3;
            this.depth = depth;
            this.biome = biome;
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
        Plan best = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            long attemptSeed = seed ^ ((long) attempt * 0x9E3779B97F4A7C15L);
            Plan cand = tryGenerateFlat(lib, cfg, start, originX, originY, originZ, attemptSeed);
            if (cand.pieces.size() >= cfg.minPieces && cand.pieces.size() <= cfg.maxPieces) {
                return cand;
            }
            if (best == null || cand.pieces.size() > best.pieces.size()) {
                best = cand;
            }
        }
        return best;
    }

    private static Plan generateBiomePlan(DungeonBlueprints427.Library lib, DungeonConfig427 cfg,
            AxiomBlueprint427.PieceTemplate start, int originX, int originY, int originZ, long seed) {
        boolean needInfernal = lib.hasTheme(DungeonBlueprints427.THEME_INFERNAL);
        boolean needFlesh = lib.hasTheme(DungeonBlueprints427.THEME_FLESH);
        int wantCryptMin = Math.max(3, cfg.minPieces / 5);
        int wantDeepMin = Math.max(3, cfg.minPieces / 6);

        int maxAttempts = 40;
        Plan best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            long attemptSeed = seed ^ ((long) attempt * 0x9E3779B97F4A7C15L);
            Plan cand = tryGenerateBiome(lib, cfg, start, originX, originY, originZ, attemptSeed);
            boolean sizeOk = cand.pieces.size() >= cfg.minPieces && cand.pieces.size() <= cfg.maxPieces;
            boolean biomesOk = cand.cryptCount >= wantCryptMin
                    && (!needInfernal || cand.infernalCount >= wantDeepMin)
                    && (!needFlesh || cand.fleshCount >= wantDeepMin);
            if (sizeOk && biomesOk) {
                return cand;
            }
            int minTheme = cand.cryptCount;
            if (needInfernal) minTheme = Math.min(minTheme, cand.infernalCount);
            if (needFlesh) minTheme = Math.min(minTheme, cand.fleshCount);
            int score = minTheme * 1000 + Math.min(cand.pieces.size(), cfg.maxPieces);
            if (sizeOk) score += 200;
            if (best == null || score > bestScore) {
                best = cand;
                bestScore = score;
            }
        }
        return best;
    }

    private static Plan tryGenerateBiome(DungeonBlueprints427.Library lib, DungeonConfig427 cfg,
            AxiomBlueprint427.PieceTemplate start, int originX, int originY, int originZ, long seed) {
        Random rnd = new Random(seed);
        List<PlannedPiece> placed = new ArrayList<>();
        List<int[]> boxes = new ArrayList<>();
        List<OpenExit> openExits = new ArrayList<>();
        List<Seal> seals = new ArrayList<>();
        Map<String, Integer> usedCounts = new HashMap<>();

        boolean hasInfernal = lib.hasTheme(DungeonBlueprints427.THEME_INFERNAL);
        boolean hasFlesh = lib.hasTheme(DungeonBlueprints427.THEME_FLESH);

        int transitionDepth = Math.max(3, Math.min(4, cfg.maxPieces / 16));
        int minCryptBeforeDeep = Math.max(4, Math.min(20, (int) Math.round(cfg.maxPieces * 0.24D)));
        int minDist = Math.min(36, Math.max(8, cfg.maxPieces / 2));
        long minDistSq = (long) minDist * (long) minDist;

        int[] startBox = rotatedWorldBox(start, originX, originY, originZ, 0);
        placed.add(new PlannedPiece(start.key, originX, originY, originZ, 0,
                startBox[0], startBox[1], startBox[2], startBox[3], startBox[4], startBox[5],
                "start", 0));
        boxes.add(startBox);

        for (AxiomBlueprint427.Marker ex : start.exits) {
            openExits.add(new OpenExit(originX + ex.x, originY + ex.y, originZ + ex.z,
                    ex.dir, 0, DungeonBlueprints427.THEME_CRYPT));
        }
        if (cfg.sealStartEntrance && start.entrance != null && lib.hasExternalPieces()) {
            seals.add(new Seal(originX + start.entrance.x, originY + start.entrance.y,
                    originZ + start.entrance.z, start.entrance.dir));
        }

        int minBX = originX - cfg.maxRadius;
        int maxBX = originX + cfg.maxRadius;
        int minBZ = originZ - cfg.maxRadius;
        int maxBZ = originZ + cfg.maxRadius;
        int minBY = originY + cfg.minY;
        int maxBY = originY + cfg.maxY;

        int placedCrypt = 0;
        int placedInfernal = 0;
        int placedFlesh = 0;

        while (!openExits.isEmpty() && placed.size() < cfg.maxPieces) {
            if (placed.size() + openExits.size() >= cfg.maxPieces) {
                break;
            }

            int pickIdx = pickNextBiomeExit(openExits, rnd, transitionDepth, minCryptBeforeDeep,
                    minDistSq, originX, originZ, placedCrypt, placedInfernal, placedFlesh,
                    hasInfernal, hasFlesh);
            OpenExit cur = openExits.remove(pickIdx);
            int wantLapisDir = (cur.outDir + 2) & 3;

            String targetBiome = resolveTargetBiome(cur, rnd, transitionDepth, minCryptBeforeDeep,
                    minDistSq, originX, originZ, placedCrypt, placedInfernal, placedFlesh,
                    openExits, hasInfernal, hasFlesh);

            List<AxiomBlueprint427.PieceTemplate> rawConn = lib.connectorsForTheme(targetBiome);
            if (rawConn.isEmpty()) rawConn = lib.connectors;
            List<AxiomBlueprint427.PieceTemplate> pool = new ArrayList<>(rawConn);
            Collections.shuffle(pool, rnd);

            int openSameBiome = countOpenForBiome(openExits, targetBiome);
            int placedInBiome = countForBiome(targetBiome, placedCrypt, placedInfernal, placedFlesh);
            boolean earlyCryptBranch = DungeonBlueprints427.THEME_CRYPT.equals(targetBiome)
                    && placedCrypt < minCryptBeforeDeep && openSameBiome < 3;
            boolean earlyDeepBranch = !DungeonBlueprints427.THEME_CRYPT.equals(targetBiome)
                    && openSameBiome < 2 && placedInBiome < 6
                    && placed.size() + openExits.size() < cfg.maxPieces - 8;

            final int curPlaced = placed.size();
            final int curOpen = openExits.size();
            Map<String, Double> jitter = new HashMap<>(pool.size() * 2);
            for (AxiomBlueprint427.PieceTemplate p : pool) {
                double s = usedCounts.getOrDefault(p.key, 0) * 10.0D;
                int exCount = p.exits.size();
                if (earlyCryptBranch && exCount >= 2) s -= 18.0D;
                else if (earlyDeepBranch && exCount >= 2) s -= 14.0D;
                else if (curPlaced < 12 && curOpen < 3 && exCount >= 2) s -= 8.0D;
                else if (curPlaced + curOpen >= cfg.maxPieces - 5 && exCount >= 2) s += 22.0D;
                else if (openSameBiome >= 4 && exCount >= 2) s += 14.0D;
                s += rnd.nextDouble() * 5.0D;
                jitter.put(p.key, s);
            }
            pool.sort(Comparator.comparingDouble(p -> jitter.getOrDefault(p.key, 0.0D)));

            PlannedPiece chosen = null;
            AxiomBlueprint427.PieceTemplate chosenTpl = null;

            for (AxiomBlueprint427.PieceTemplate cand : pool) {
                PlannedPiece fit = tryFit(cand, cur, wantLapisDir, minBX, maxBX, minBY, maxBY, minBZ, maxBZ,
                        boxes, targetBiome, cur.depth + 1);
                if (fit != null) {
                    chosen = fit;
                    chosenTpl = cand;
                    break;
                }
            }

            if (chosen == null) {
                List<AxiomBlueprint427.PieceTemplate> deSrc = lib.deadEndsForTheme(targetBiome);
                if (deSrc.isEmpty()) deSrc = lib.deadEnds;
                if (!deSrc.isEmpty()) {
                    List<AxiomBlueprint427.PieceTemplate> dePool = new ArrayList<>(deSrc);
                    Collections.shuffle(dePool, rnd);
                    for (AxiomBlueprint427.PieceTemplate cand : dePool) {
                        PlannedPiece fit = tryFit(cand, cur, wantLapisDir, minBX, maxBX, minBY, maxBY, minBZ, maxBZ,
                                boxes, targetBiome, cur.depth + 1);
                        if (fit != null) {
                            chosen = fit;
                            chosenTpl = cand;
                            break;
                        }
                    }
                }
            }

            if (chosen != null && chosenTpl != null) {
                placed.add(chosen);
                boxes.add(new int[] { chosen.minX, chosen.minY, chosen.minZ, chosen.maxX, chosen.maxY, chosen.maxZ });
                usedCounts.put(chosenTpl.key, usedCounts.getOrDefault(chosenTpl.key, 0) + 1);
                if (DungeonBlueprints427.THEME_CRYPT.equals(targetBiome)) placedCrypt++;
                else if (DungeonBlueprints427.THEME_INFERNAL.equals(targetBiome)) placedInfernal++;
                else if (DungeonBlueprints427.THEME_FLESH.equals(targetBiome)) placedFlesh++;

                for (AxiomBlueprint427.Marker ex : chosenTpl.exits) {
                    int rx = rotX(ex.x, ex.z, chosen.turns);
                    int rz = rotZ(ex.x, ex.z, chosen.turns);
                    openExits.add(new OpenExit(
                            chosen.ox + rx,
                            chosen.oy + ex.y,
                            chosen.oz + rz,
                            (ex.dir + chosen.turns) & 3,
                            cur.depth + 1,
                            targetBiome));
                }
            } else {
                seals.add(new Seal(cur.rx, cur.ry, cur.rz, cur.outDir));
            }
        }

        // Cap all remaining open exits with dead-end rooms strictly matching their branch's biome.
        for (OpenExit cur : openExits) {
            if (placed.size() < cfg.maxPieces) {
                List<AxiomBlueprint427.PieceTemplate> deSrc = lib.deadEndsForTheme(cur.biome);
                if (deSrc.isEmpty()) deSrc = lib.deadEnds;
                if (!deSrc.isEmpty()) {
                    int wantLapisDir = (cur.outDir + 2) & 3;
                    List<AxiomBlueprint427.PieceTemplate> dePool = new ArrayList<>(deSrc);
                    Collections.shuffle(dePool, rnd);
                    PlannedPiece cap = null;
                    for (AxiomBlueprint427.PieceTemplate cand : dePool) {
                        PlannedPiece fit = tryFit(cand, cur, wantLapisDir, minBX, maxBX, minBY, maxBY, minBZ, maxBZ,
                                boxes, cur.biome, cur.depth + 1);
                        if (fit != null) {
                            cap = fit;
                            break;
                        }
                    }
                    if (cap != null) {
                        placed.add(cap);
                        boxes.add(new int[] { cap.minX, cap.minY, cap.minZ, cap.maxX, cap.maxY, cap.maxZ });
                        continue;
                    }
                }
            }
            seals.add(new Seal(cur.rx, cur.ry, cur.rz, cur.outDir));
        }

        return buildPlan(start, originX, originY, originZ, placed, seals);
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

    private static Plan tryGenerateFlat(DungeonBlueprints427.Library lib, DungeonConfig427 cfg,
            AxiomBlueprint427.PieceTemplate start, int originX, int originY, int originZ, long seed) {
        Random rnd = new Random(seed);
        List<PlannedPiece> placed = new ArrayList<>();
        List<int[]> boxes = new ArrayList<>();
        List<OpenExit> openExits = new ArrayList<>();
        List<Seal> seals = new ArrayList<>();
        Map<String, Integer> usedCounts = new HashMap<>();

        int[] startBox = rotatedWorldBox(start, originX, originY, originZ, 0);
        placed.add(new PlannedPiece(start.key, originX, originY, originZ, 0,
                startBox[0], startBox[1], startBox[2], startBox[3], startBox[4], startBox[5],
                "start", 0));
        boxes.add(startBox);

        for (AxiomBlueprint427.Marker ex : start.exits) {
            openExits.add(new OpenExit(originX + ex.x, originY + ex.y, originZ + ex.z,
                    ex.dir, 0, DungeonBlueprints427.THEME_OTHER));
        }
        if (cfg.sealStartEntrance && start.entrance != null && lib.hasExternalPieces()) {
            seals.add(new Seal(originX + start.entrance.x, originY + start.entrance.y,
                    originZ + start.entrance.z, start.entrance.dir));
        }

        int minBX = originX - cfg.maxRadius;
        int maxBX = originX + cfg.maxRadius;
        int minBZ = originZ - cfg.maxRadius;
        int maxBZ = originZ + cfg.maxRadius;
        int minBY = originY + cfg.minY;
        int maxBY = originY + cfg.maxY;

        while (!openExits.isEmpty() && placed.size() < cfg.maxPieces) {
            if (placed.size() + openExits.size() >= cfg.maxPieces) {
                break;
            }
            int pickIdx = rnd.nextDouble() < 0.75D ? 0 : rnd.nextInt(openExits.size());
            OpenExit cur = openExits.remove(pickIdx);
            int wantLapisDir = (cur.outDir + 2) & 3;

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

            PlannedPiece chosen = null;
            AxiomBlueprint427.PieceTemplate chosenTpl = null;

            for (AxiomBlueprint427.PieceTemplate cand : pool) {
                String candBiome = DungeonBlueprints427.themeOf(cand);
                PlannedPiece fit = tryFit(cand, cur, wantLapisDir, minBX, maxBX, minBY, maxBY, minBZ, maxBZ,
                        boxes, candBiome, cur.depth + 1);
                if (fit != null) {
                    chosen = fit;
                    chosenTpl = cand;
                    break;
                }
            }

            if (chosen == null && !lib.deadEnds.isEmpty()) {
                List<AxiomBlueprint427.PieceTemplate> dePool = new ArrayList<>(lib.deadEnds);
                Collections.shuffle(dePool, rnd);
                for (AxiomBlueprint427.PieceTemplate cand : dePool) {
                    String candBiome = DungeonBlueprints427.themeOf(cand);
                    PlannedPiece fit = tryFit(cand, cur, wantLapisDir, minBX, maxBX, minBY, maxBY, minBZ, maxBZ,
                            boxes, candBiome, cur.depth + 1);
                    if (fit != null) {
                        chosen = fit;
                        chosenTpl = cand;
                        break;
                    }
                }
            }

            if (chosen != null && chosenTpl != null) {
                placed.add(chosen);
                boxes.add(new int[] { chosen.minX, chosen.minY, chosen.minZ, chosen.maxX, chosen.maxY, chosen.maxZ });
                usedCounts.put(chosenTpl.key, usedCounts.getOrDefault(chosenTpl.key, 0) + 1);
                for (AxiomBlueprint427.Marker ex : chosenTpl.exits) {
                    int rx = rotX(ex.x, ex.z, chosen.turns);
                    int rz = rotZ(ex.x, ex.z, chosen.turns);
                    openExits.add(new OpenExit(
                            chosen.ox + rx,
                            chosen.oy + ex.y,
                            chosen.oz + rz,
                            (ex.dir + chosen.turns) & 3,
                            cur.depth + 1,
                            DungeonBlueprints427.THEME_OTHER));
                }
            } else {
                seals.add(new Seal(cur.rx, cur.ry, cur.rz, cur.outDir));
            }
        }

        for (OpenExit cur : openExits) {
            if (placed.size() < cfg.maxPieces && !lib.deadEnds.isEmpty()) {
                int wantLapisDir = (cur.outDir + 2) & 3;
                List<AxiomBlueprint427.PieceTemplate> dePool = new ArrayList<>(lib.deadEnds);
                Collections.shuffle(dePool, rnd);
                PlannedPiece cap = null;
                for (AxiomBlueprint427.PieceTemplate cand : dePool) {
                    String candBiome = DungeonBlueprints427.themeOf(cand);
                    PlannedPiece fit = tryFit(cand, cur, wantLapisDir, minBX, maxBX, minBY, maxBY, minBZ, maxBZ,
                            boxes, candBiome, cur.depth + 1);
                    if (fit != null) {
                        cap = fit;
                        break;
                    }
                }
                if (cap != null) {
                    placed.add(cap);
                    boxes.add(new int[] { cap.minX, cap.minY, cap.minZ, cap.maxX, cap.maxY, cap.maxZ });
                    continue;
                }
            }
            seals.add(new Seal(cur.rx, cur.ry, cur.rz, cur.outDir));
        }

        return buildPlan(start, originX, originY, originZ, placed, seals);
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
