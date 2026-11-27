package dev.settledlands.npc;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Build 453/456: Read/Write variable data node ({@code Variable Value}) and complete math
 * operations + dual left inputs (blue entity/target input + orange variable/number input) for
 * {@link VarDataNode453}, {@link NpcDialogueGraph#VARIABLE}, and {@link NpcDialogueGraph#IF}.
 *
 * <p>Stored inside the stock graph as {@link NpcDialogueGraph#TRIGGER} ({@code 2}, {@code special == 0})
 * with {@code action} carrying {@code "trigger:var453:..."} so NBT, packet sync, validation,
 * clipboard copy/paste, and undo/redo all preserve it seamlessly across every graph editor.</p>
 */
public final class VarDataNode453 {

    /** Canonical action prefix stored in {@link NpcDialogueGraph.Node#action}. */
    public static final String ACTION_PREFIX = "trigger:var453:";

    /** Form seen if {@link NpcActions} already stripped {@code "trigger:"}. */
    public static final String STRIPPED_PREFIX = "var453:";

    /** Default action marker for a newly created Variable Value node. */
    public static final String DEFAULT_ACTION = ACTION_PREFIX + "0";

    /** Row index in the NPC/common context menu (17 Teleport, 18 Trial Control, 19 Variable Value). */
    public static final int MENU_ROW = 19;

    /** Total rows in the NPC/common context menu once Variable Value (19) joins. */
    public static final int NPC_MENU_ITEMS = 20;

    /** Visible rows in the NPC/common context menu window. */
    public static final int NPC_VISIBLE_ROWS = 5;

    /** Maximum {@code scrollRow} in the NPC/common context menu ({@code 20 - 5 = 15}). */
    public static final int NPC_MAX_SCROLL = NPC_MENU_ITEMS - NPC_VISIBLE_ROWS;

    /** Distinct warm orange variable-value accent. */
    public static final int ACCENT_COLOR = -290244; // 0xFFFB923C

    /** Brighter orange used for card borders and active highlights. */
    public static final int PIN_COLOR = 0xFFFB923C;

    /** Soft dark orange tint for card header/body accents. */
    public static final int HEADER_BG = 0xFF5A2B0C;

    /** Localization key for create menu rows. */
    public static final String MENU_LABEL_KEY = "gui.settledlands.var453.menu";

    /** Localization key for node/inspector header. */
    public static final String TITLE_KEY = "gui.settledlands.var453.title";

    // ---- Math operation codes stored in NpcDialogueGraph.Node.varOp for VARIABLE (4) ----
    public static final int OP_ADD = 0;      // x + v (default, backward compatible)
    public static final int OP_SUB = 1;      // x - v
    public static final int OP_MUL = 2;      // x * v
    public static final int OP_DIV = 3;      // x / v (guarded v == 0)
    public static final int OP_SET = 4;      // = v
    public static final int OP_POW = 5;      // x ^ v
    public static final int OP_PCT_OF = 6;   // v% of x  -> (x * v / 100)
    public static final int OP_ADD_PCT = 7;  // x + v% of x
    public static final int OP_SUB_PCT = 8;  // x - v% of x
    public static final int OP_MOD = 9;      // x % v (remainder)
    public static final int OP_MIN = 10;     // min(x, v)
    public static final int OP_MAX = 11;     // max(x, v)
    public static final int OP_ABS = 12;     // |x|
    public static final int OP_NEG = 13;     // -x
    public static final int OP_COUNT = 14;

    private static final long MAX_VAR_ABS = 1_000_000_000L;
    private static final Map<NpcDialogueGraph.Node, Integer> FALLBACK_ENTITY_INPUTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Mixin-implemented accessor on {@link NpcDialogueGraph.Node} holding the blue entity/target
     * input id independently of the orange variable input ({@code varInputNode}).
     */
    public interface EntityInputHost {
        int settledlands$getEntityInput453();

        void settledlands$setEntityInput453(int nodeId);
    }

    private VarDataNode453() {}

    public static boolean isVarDataAction(String action) {
        if (action == null) return false;
        String lower = action.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith(ACTION_PREFIX) || lower.equals("trigger:var453");
    }

    public static boolean isStrippedVarDataAction(String body) {
        if (body == null) return false;
        String lower = body.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith(STRIPPED_PREFIX) || lower.equals("var453");
    }

    public static boolean isVarDataNode(NpcDialogueGraph.Node node) {
        return node != null
                && node.special == 0
                && node.type == NpcDialogueGraph.TRIGGER
                && isVarDataAction(node.action);
    }

    /** True if {@code node} supplies a numeric variable or constant via an orange wire. */
    public static boolean isOrangeSourceNode(NpcDialogueGraph.Node node) {
        return isVarDataNode(node) || (node != null && node.special == 0
                && node.type == NpcDialogueGraph.VARIABLE);
    }

    /** True if {@code node} accepts an orange variable/numeric wire on its left edge. */
    public static boolean isOrangeInputNode(NpcDialogueGraph.Node node) {
        return isVarDataNode(node) || (node != null && node.special == 0
                && (node.type == NpcDialogueGraph.VARIABLE || node.type == NpcDialogueGraph.IF));
    }

    /** True if {@code node} is a blue body/entity source node (Check, Damage, Door, Chest, Trial, Pursuit). */
    public static boolean isBlueSourceId(NpcDialogueGraph graph, int id) {
        if (graph == null || id < 0) return false;
        NpcDialogueGraph.Node n = graph.get(id);
        return isBlueSourceNode(n);
    }

    public static boolean isBlueSourceNode(NpcDialogueGraph.Node node) {
        if (node == null || node.special != 0) return false;
        int t = node.type;
        if (t == NpcDialogueGraph.CHECK || t == NpcDialogueGraph.DAMAGE
                || t == 40 || t == 50 || t == 60) {
            return true;
        }
        if (t == NpcDialogueGraph.TRIGGER && node.action != null) {
            String lower = node.action.trim().toLowerCase(Locale.ROOT);
            return lower.startsWith("trigger:pursuit:") || lower.startsWith("trigger:pursuit ")
                    || lower.startsWith("pursuit:") || lower.startsWith("pursuit ");
        }
        return false;
    }

    public static void applyDefaults(NpcDialogueGraph.Node node) {
        if (node == null) return;
        node.type = NpcDialogueGraph.TRIGGER;
        node.text = "";
        node.action = DEFAULT_ACTION;
        if (node.pointsName == null || node.pointsName.isBlank()) {
            node.pointsName = "var";
        }
        node.varValue = 0;
        node.pointsShared = false;
        node.varLocal = false;
        node.varOp = 0;
        node.varInputNode = -1;
        node.next = -1;
        node.falseNext = -1;
        setEntityInput(node, -1);
    }

    // ---- Dual left-input separation (blue entity input vs orange variable input) ----

    /**
     * Reads the blue entity/target input id for any value-input node.
     *
     * <p>{@code LINE (0)} and ordinary {@code TRIGGER (2)} keep using {@code node.varInputNode}
     * for full compatibility with {@code NpcValueSource290}. {@code IF (3)}, {@code VARIABLE (4)},
     * and {@link #isVarDataNode(NpcDialogueGraph.Node)} store their blue input in the dedicated
     * {@code entityInput453} field so it can coexist with the orange {@code varInputNode}.</p>
     */
    public static int entityInput(NpcDialogueGraph.Node node) {
        if (node == null) return -1;
        if (node.type == NpcDialogueGraph.IF || node.type == NpcDialogueGraph.VARIABLE
                || isVarDataNode(node)) {
            Object raw = node;
            if (raw instanceof EntityInputHost host) {
                int v = host.settledlands$getEntityInput453();
                if (v >= 0) return v;
            } else {
                Integer fb = FALLBACK_ENTITY_INPUTS.get(node);
                if (fb != null && fb >= 0) return fb;
            }
            return -1;
        }
        if (node.type == NpcDialogueGraph.LINE || node.type == NpcDialogueGraph.TRIGGER) {
            return node.varInputNode;
        }
        return -1;
    }

    /** Writes the blue entity/target input id without overwriting the orange {@code varInputNode}. */
    public static void setEntityInput(NpcDialogueGraph.Node node, int sourceId) {
        if (node == null) return;
        int clean = sourceId == node.id ? -1 : sourceId;
        if (node.type == NpcDialogueGraph.IF || node.type == NpcDialogueGraph.VARIABLE
                || isVarDataNode(node)) {
            Object raw = node;
            if (raw instanceof EntityInputHost host) {
                host.settledlands$setEntityInput453(clean);
            } else if (clean < 0) {
                FALLBACK_ENTITY_INPUTS.remove(node);
            } else {
                FALLBACK_ENTITY_INPUTS.put(node, clean);
            }
        } else {
            node.varInputNode = clean;
        }
    }

    /** Reads the blue entity/target input id with fallback migration when graph lookup is available. */
    public static int entityInput(NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
        if (node == null) return -1;
        int direct = entityInput(node);
        if (direct >= 0) return direct;
        if ((node.type == NpcDialogueGraph.IF || node.type == NpcDialogueGraph.VARIABLE
                || isVarDataNode(node))
                && node.varInputNode >= 0 && graph != null) {
            NpcDialogueGraph.Node legacy = graph.get(node.varInputNode);
            if (isBlueSourceNode(legacy)) {
                return node.varInputNode;
            }
        }
        return -1;
    }

    /**
     * Migrates a legacy {@code IF}/{@code VARIABLE}/{@code VarData} blue wire stored in {@code varInputNode}
     * into the dedicated {@code entityInput} slot when loading an older graph.
     */
    public static void migrateLegacyInputs(NpcDialogueGraph graph) {
        if (graph == null || graph.nodes == null) return;
        for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            if (n == null || n.special != 0) continue;
            if (n.type != NpcDialogueGraph.IF && n.type != NpcDialogueGraph.VARIABLE
                    && !isVarDataNode(n)) {
                continue;
            }
            if (entityInput(n) < 0 && n.varInputNode >= 0) {
                NpcDialogueGraph.Node src = graph.get(n.varInputNode);
                if (isBlueSourceNode(src)) {
                    setEntityInput(n, n.varInputNode);
                    n.varInputNode = -1;
                }
            }
        }
    }

    /**
     * Finds the first {@link #isVarDataNode(NpcDialogueGraph.Node)} whose orange input is wired from
     * {@code sourceId} (used when a math {@code VARIABLE} node with a blank name outputs into a variable value node).
     */
    public static NpcDialogueGraph.Node findLinkedOrangeTarget(NpcDialogueGraph graph, int sourceId) {
        if (graph == null || graph.nodes == null || sourceId < 0) return null;
        for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            if (isVarDataNode(n) && n.varInputNode == sourceId && n.id != sourceId) {
                return n;
            }
        }
        return null;
    }

    /** Returns the orange source node connected to {@code consumer}, or null. */
    public static NpcDialogueGraph.Node resolveOrangeSource(NpcDialogueGraph graph,
            NpcDialogueGraph.Node consumer) {
        if (graph == null || consumer == null || !isOrangeInputNode(consumer)) return null;
        if (consumer.varInputNode < 0 || consumer.varInputNode == consumer.id) return null;
        NpcDialogueGraph.Node src = graph.get(consumer.varInputNode);
        return isOrangeSourceNode(src) ? src : null;
    }

    /** How many nodes connect to {@code sourceId} via their orange {@code varInputNode} input. */
    public static int wiredOrangeConsumers(NpcDialogueGraph graph, int sourceId) {
        if (graph == null || graph.nodes == null || sourceId < 0) return 0;
        int count = 0;
        for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            if (isOrangeInputNode(n) && n.varInputNode == sourceId) {
                count++;
            }
        }
        return count;
    }

    /** Unplugs all orange input wires coming from {@code sourceId}. */
    public static void unplugOrangeFrom(NpcDialogueGraph graph, int sourceId) {
        if (graph == null || graph.nodes == null || sourceId < 0) return;
        for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            if (isOrangeInputNode(n) && n.varInputNode == sourceId) {
                n.varInputNode = -1;
            }
        }
    }

    // ---- Scope helpers (Personal / Shared / Local) ----

    public static final int SCOPE_PERSONAL = 0;
    public static final int SCOPE_SHARED = 1;
    public static final int SCOPE_LOCAL = 2;

    public static int scopeOf(NpcDialogueGraph.Node node) {
        if (node == null) return SCOPE_PERSONAL;
        if (node.varLocal) return SCOPE_LOCAL;
        if (node.pointsShared) return SCOPE_SHARED;
        return SCOPE_PERSONAL;
    }

    public static void setScope(NpcDialogueGraph.Node node, int scope) {
        if (node == null) return;
        if (scope == SCOPE_LOCAL) {
            node.varLocal = true;
            node.pointsShared = false;
        } else if (scope == SCOPE_SHARED) {
            node.varLocal = false;
            node.pointsShared = true;
        } else {
            node.varLocal = false;
            node.pointsShared = false;
        }
    }

    public static String scopeBadgeKey(NpcDialogueGraph.Node node) {
        return switch (scopeOf(node)) {
            case SCOPE_SHARED -> "gui.settledlands.var453.scope_shared";
            case SCOPE_LOCAL -> "gui.settledlands.var453.scope_local";
            default -> "gui.settledlands.var453.scope_personal";
        };
    }

    public static int scopeColor(NpcDialogueGraph.Node node) {
        return switch (scopeOf(node)) {
            case SCOPE_SHARED -> 0xFF4ADE80;
            case SCOPE_LOCAL -> 0xFFC084FC;
            default -> 0xFF38BDF8;
        };
    }

    // ---- Math evaluation for VARIABLE (4) ----

    public static int normalizeOp(int op) {
        return op >= 0 && op < OP_COUNT ? op : OP_ADD;
    }

    public static boolean opUsesOperand(int op) {
        int norm = normalizeOp(op);
        return norm != OP_ABS && norm != OP_NEG;
    }

    public static String opSymbol(int op) {
        return switch (normalizeOp(op)) {
            case OP_ADD -> "+=";
            case OP_SUB -> "-=";
            case OP_MUL -> "*=";
            case OP_DIV -> "/=";
            case OP_SET -> "=";
            case OP_POW -> "^=";
            case OP_PCT_OF -> "% от";
            case OP_ADD_PCT -> "+%";
            case OP_SUB_PCT -> "-%";
            case OP_MOD -> "mod";
            case OP_MIN -> "min";
            case OP_MAX -> "max";
            case OP_ABS -> "|x|";
            case OP_NEG -> "-x";
            default -> "+=";
        };
    }

    public static String opLabelKey(int op) {
        return "gui.settledlands.var453.op_" + normalizeOp(op);
    }

    /** Formats a compact math summary for the card on the graph canvas. */
    public static String formatCardOp(int op, int value, String valueOverrideText) {
        int norm = normalizeOp(op);
        String rhs = valueOverrideText != null ? valueOverrideText : String.valueOf(value);
        return switch (norm) {
            case OP_ADD -> (valueOverrideText == null && value >= 0) ? "+" + rhs : "+ " + rhs;
            case OP_SUB -> "- " + rhs;
            case OP_MUL -> "* " + rhs;
            case OP_DIV -> "/ " + rhs;
            case OP_SET -> "= " + rhs;
            case OP_POW -> "^ " + rhs;
            case OP_PCT_OF -> rhs + "% от числа";
            case OP_ADD_PCT -> "+ " + rhs + "%";
            case OP_SUB_PCT -> "- " + rhs + "%";
            case OP_MOD -> "% (ост.) " + rhs;
            case OP_MIN -> "min(" + rhs + ")";
            case OP_MAX -> "max(" + rhs + ")";
            case OP_ABS -> "|x| (модуль)";
            case OP_NEG -> "-x (знак)";
            default -> "+ " + rhs;
        };
    }

    /** Applies any of the 14 integer math operations safely with overflow and div-by-zero guards. */
    public static int applyMathOp(int current, int op, int operand) {
        int norm = normalizeOp(op);
        long cur = current;
        long val = operand;
        return switch (norm) {
            case OP_ADD -> clampVar(cur + val);
            case OP_SUB -> clampVar(cur - val);
            case OP_MUL -> clampVar(cur * val);
            case OP_DIV -> val == 0L ? current : clampVar(cur / val);
            case OP_SET -> clampVar(val);
            case OP_POW -> safePow(cur, val);
            case OP_PCT_OF -> clampVar(Math.round((double) cur * (double) val / 100.0D));
            case OP_ADD_PCT -> clampVar(cur + Math.round((double) cur * (double) val / 100.0D));
            case OP_SUB_PCT -> clampVar(cur - Math.round((double) cur * (double) val / 100.0D));
            case OP_MOD -> val == 0L ? current : clampVar(cur % val);
            case OP_MIN -> clampVar(Math.min(cur, val));
            case OP_MAX -> clampVar(Math.max(cur, val));
            case OP_ABS -> clampVar(Math.abs(cur));
            case OP_NEG -> clampVar(-cur);
            default -> clampVar(cur + val);
        };
    }

    private static int safePow(long base, long exp) {
        if (exp == 0L) return 1;
        if (exp < 0L) {
            if (base == 0L) return 0;
            if (base == 1L) return 1;
            if (base == -1L) return (exp & 1L) == 0L ? 1 : -1;
            return 0;
        }
        if (base == 0L) return 0;
        if (base == 1L) return 1;
        if (base == -1L) return (exp & 1L) == 0L ? 1 : -1;
        long result = 1L;
        long b = base;
        long e = exp;
        while (e > 0L) {
            if ((e & 1L) != 0L) {
                double prod = (double) result * (double) b;
                if (prod > MAX_VAR_ABS) return (int) MAX_VAR_ABS;
                if (prod < -MAX_VAR_ABS) return (int) -MAX_VAR_ABS;
                result = result * b;
            }
            e >>= 1;
            if (e > 0L) {
                double sq = (double) b * (double) b;
                if (sq > MAX_VAR_ABS * 2.0D) {
                    // Any remaining multiply with |base|>=2 exceeds MAX_VAR_ABS unless negative parity flips sign
                    boolean negSign = result < 0 || (b < 0 && (e & 1L) != 0L);
                    return (int) (negSign ? -MAX_VAR_ABS : MAX_VAR_ABS);
                }
                b = b * b;
            }
        }
        return clampVar(result);
    }

    private static int clampVar(long v) {
        if (v > MAX_VAR_ABS) return (int) MAX_VAR_ABS;
        if (v < -MAX_VAR_ABS) return (int) -MAX_VAR_ABS;
        return (int) v;
    }
}
