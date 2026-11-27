package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcItems;
import dev.settledlands.trial.TrialConfig446;
import dev.settledlands.trial.TrialModule447;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Build 449: registry catalog of all mobs in the modpack plus crash-safe live 3D entity
 * thumbnail rendering via vanilla {@link InventoryScreen#renderEntityInInventoryFollowsMouse}.
 *
 * <p>Budgets entity instantiation per render frame so scrolling through hundreds of modded mobs
 * remains completely stutter-free, and falls back to matching {@code *_spawn_egg} icons or
 * category badges whenever a modded entity cannot be constructed on the client.</p>
 */
public final class TrialMobPreview449 {

    public static final int CAT_ALL = 0;
    public static final int CAT_HOSTILE = 1;
    public static final int CAT_FRIENDLY = 2;

    private static final int MAX_ENTITY_CACHE = 96;
    private static final int MAX_CREATES_PER_FRAME = 4;

    private static final Map<String, Entity> ENTITY_CACHE =
            new LinkedHashMap<>(64, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Entity> eldest) {
                    return size() > MAX_ENTITY_CACHE;
                }
            };
    private static final Set<String> FAILED_IDS = new HashSet<>();
    private static final Map<String, EntityType> TYPE_BY_ID = new HashMap<>();
    private static final Map<String, String> DISPLAY_NAMES = new HashMap<>();
    private static final Map<String, String> CATEGORY_OF = new HashMap<>();
    private static Map<String, ItemStack> EGG_ICONS;

    private static Level lastClientLevel;
    private static int createsThisFrame;

    private static volatile boolean dimMethodsProbed;
    private static Method mGetBbHeight;
    private static Method mGetBbWidth;

    private TrialMobPreview449() {}

    public static final class MobEntry {
        public final String id;
        public final String modId;
        public final String path;
        public final String displayName;
        public final String categoryName;
        public final boolean hostile;

        public MobEntry(String id, String modId, String path, String displayName,
                String categoryName, boolean hostile) {
            this.id = id;
            this.modId = modId;
            this.path = path;
            this.displayName = displayName;
            this.categoryName = categoryName;
            this.hostile = hostile;
        }
    }

    public static void beginFrame() {
        createsThisFrame = 0;
    }

    public static void clearCache() {
        ENTITY_CACHE.clear();
        FAILED_IDS.clear();
        createsThisFrame = 0;
    }

    /** Collects all registered living mobs / bosses across vanilla and every installed mod. */
    public static List<MobEntry> collectAllMobs() {
        ensureTypeIndex();
        List<MobEntry> out = new ArrayList<>();
        try {
            for (EntityType type : BuiltInRegistries.ENTITY_TYPE) {
                if (type == null) continue;
                ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (rl == null) continue;
                String id = rl.toString();
                MobCategory cat = safeCategory(type);
                String catName = cat == null ? "MISC" : ((Enum<?>) cat).name();
                if ("MISC".equals(catName) && isNonLivingNoise(id, rl.getPath())) {
                    continue;
                }
                String name = safeName(type, id);
                boolean hostile = cat == MobCategory.MONSTER;
                out.add(new MobEntry(id, rl.getNamespace(), rl.getPath(), name, catName, hostile));
            }
        } catch (Throwable ignored) {
        }
        out.sort(Comparator
                .comparing((MobEntry e) -> e.displayName.toLowerCase(Locale.ROOT))
                .thenComparing(e -> e.id));
        return out;
    }

    public static String displayName(String entityId) {
        if (entityId == null || entityId.isEmpty()) return "";
        String norm = TrialConfig446.normalizeId(entityId);
        if (norm == null) return entityId;
        ensureTypeIndex();
        String found = DISPLAY_NAMES.get(norm);
        if (found != null && !found.isEmpty()) return found;
        return TrialGraphClient446.shortId(norm);
    }

    public static boolean isHostileCategory(String entityId) {
        if (entityId == null || entityId.isEmpty()) return false;
        String norm = TrialConfig446.normalizeId(entityId);
        if (norm == null) return false;
        ensureTypeIndex();
        return "MONSTER".equals(CATEGORY_OF.get(norm));
    }

    public static String categoryLabelKey(String categoryName) {
        if ("MONSTER".equals(categoryName)) return "gui.settledlands.trial.mob_cat.cat_hostile";
        if ("CREATURE".equals(categoryName)) return "gui.settledlands.trial.mob_cat.cat_friendly";
        if ("WATER_CREATURE".equals(categoryName)
                || "WATER_AMBIENT".equals(categoryName)
                || "UNDERGROUND_WATER_CREATURE".equals(categoryName)
                || "AXOLOTLS".equals(categoryName)) {
            return "gui.settledlands.trial.mob_cat.cat_water";
        }
        if ("AMBIENT".equals(categoryName)) return "gui.settledlands.trial.mob_cat.cat_ambient";
        return "gui.settledlands.trial.mob_cat.cat_misc";
    }

    public static int categoryBadgeColor(String categoryName) {
        if ("MONSTER".equals(categoryName)) return 0xFFE56B6F;
        if ("CREATURE".equals(categoryName)) return 0xFF52C478;
        if ("WATER_CREATURE".equals(categoryName)
                || "WATER_AMBIENT".equals(categoryName)
                || "UNDERGROUND_WATER_CREATURE".equals(categoryName)
                || "AXOLOTLS".equals(categoryName)) {
            return 0xFF42A5F5;
        }
        if ("AMBIENT".equals(categoryName)) return 0xFFF5B841;
        return 0xFF9AA5B8;
    }

    /**
     * Draws a live 3D entity preview inside {@code [x1..x2, y1..y2]}, or a safe spawn-egg /
     * category monogram fallback if the entity cannot be instantiated on the client.
     */
    public static void renderPreviewBox(GuiGraphics graphics, Font font, String entityId,
            int x1, int y1, int x2, int y2, float lookX, float lookY) {
        if (graphics == null || entityId == null || entityId.isEmpty()) return;
        String norm = TrialConfig446.normalizeId(entityId);
        if (norm == null) return;

        LivingEntity le = resolveLivingPreview(norm);
        if (le != null) {
            int boxW = Math.max(12, x2 - x1);
            int boxH = Math.max(12, y2 - y1);
            float h = Math.max(0.35F, entityHeight(le));
            float w = Math.max(0.35F, entityWidth(le));
            float scaleF = Math.min((boxH * 0.78F) / h, (boxW * 0.74F) / (w * 1.15F));
            int scale = Math.max(4, Math.min(44, (int) Math.floor(scaleF)));
            try {
                graphics.enableScissor(x1, y1, x2, y2);
                try {
                    InventoryScreen.renderEntityInInventoryFollowsMouse(
                            graphics, x1, y1, x2, y2, scale, 0.0625F, lookX, lookY, le);
                } finally {
                    graphics.disableScissor();
                }
                return;
            } catch (Throwable ignored) {
                FAILED_IDS.add(norm);
                ENTITY_CACHE.remove(norm);
            }
        }

        renderFallbackIcon(graphics, font, norm, x1, y1, x2, y2);
    }

    private static void renderFallbackIcon(GuiGraphics graphics, Font font, String normId,
            int x1, int y1, int x2, int y2) {
        int cx = (x1 + x2) / 2;
        int cy = (y1 + y2) / 2;
        ItemStack egg = findSpawnEgg(normId);
        if (!egg.isEmpty()) {
            graphics.renderItem(egg, cx - 8, cy - 8);
            return;
        }
        String cat = CATEGORY_OF.getOrDefault(normId, "MISC");
        int col = categoryBadgeColor(cat);
        int r = Math.max(7, Math.min((x2 - x1) / 2 - 2, (y2 - y1) / 2 - 2));
        graphics.fill(cx - r, cy - r, cx + r, cy + r, (0x33 << 24) | (col & 0x00FFFFFF));
        graphics.renderOutline(cx - r, cy - r, r * 2, r * 2, (0xAA << 24) | (col & 0x00FFFFFF));
        if (font != null) {
            String name = displayName(normId);
            String ch = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            graphics.drawCenteredString(font, ch, cx, cy - 4, col);
        }
    }

    private static LivingEntity resolveLivingPreview(String normId) {
        if (FAILED_IDS.contains(normId)) return null;
        Minecraft mc = Minecraft.getInstance();
        Level lvl = mc == null ? null : mc.level;
        if (lvl == null) return null;
        if (lvl != lastClientLevel) {
            lastClientLevel = lvl;
            ENTITY_CACHE.clear();
            FAILED_IDS.clear();
        }
        Entity cached = ENTITY_CACHE.get(normId);
        if (cached != null) {
            return cached instanceof LivingEntity le ? le : null;
        }
        if (createsThisFrame >= MAX_CREATES_PER_FRAME) {
            return null;
        }
        ensureTypeIndex();
        EntityType type = TYPE_BY_ID.get(normId);
        if (type == null) return null;
        createsThisFrame++;
        try {
            Entity created = type.create(lvl);
            if (!(created instanceof LivingEntity le)) {
                FAILED_IDS.add(normId);
                return null;
            }
            ENTITY_CACHE.put(normId, created);
            return le;
        } catch (Throwable ignored) {
            FAILED_IDS.add(normId);
            return null;
        }
    }

    private static void ensureTypeIndex() {
        if (!TYPE_BY_ID.isEmpty()) return;
        try {
            for (EntityType type : BuiltInRegistries.ENTITY_TYPE) {
                if (type == null) continue;
                ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (rl == null) continue;
                String id = rl.toString();
                TYPE_BY_ID.put(id, type);
                DISPLAY_NAMES.put(id, safeName(type, id));
                MobCategory cat = safeCategory(type);
                CATEGORY_OF.put(id, cat == null ? "MISC" : ((Enum<?>) cat).name());
            }
        } catch (Throwable ignored) {
        }
    }

    private static ItemStack findSpawnEgg(String normId) {
        if (EGG_ICONS == null) {
            Map<String, ItemStack> built = new HashMap<>();
            try {
                List<ItemStack> stacks = NpcItems.allStacks();
                for (int i = 0; i < stacks.size(); i++) {
                    ItemStack st = stacks.get(i);
                    if (st == null || st.isEmpty()) continue;
                    ResourceLocation rl = BuiltInRegistries.ITEM.getKey(st.getItem());
                    if (rl == null) continue;
                    String path = rl.getPath();
                    if (path.endsWith("_spawn_egg")) {
                        String entityPath = path.substring(0, path.length() - "_spawn_egg".length());
                        built.putIfAbsent(rl.getNamespace() + ":" + entityPath, st);
                    }
                }
            } catch (Throwable ignored) {
            }
            EGG_ICONS = built;
        }
        ItemStack found = EGG_ICONS.get(normId);
        return found == null ? ItemStack.EMPTY : found;
    }

    private static MobCategory safeCategory(EntityType type) {
        try {
            return type.getCategory();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeName(EntityType type, String fallbackId) {
        try {
            String s = type.getDescription().getString();
            if (s != null && !s.isBlank()) return s;
        } catch (Throwable ignored) {
        }
        return TrialGraphClient446.shortId(fallbackId);
    }

    private static float entityHeight(LivingEntity le) {
        probeDimMethods();
        if (mGetBbHeight != null) {
            try {
                Object v = mGetBbHeight.invoke(le);
                if (v instanceof Float f && Float.isFinite(f) && f > 0.05F) return f;
            } catch (Throwable ignored) {
            }
        }
        return 1.85F;
    }

    private static float entityWidth(LivingEntity le) {
        probeDimMethods();
        if (mGetBbWidth != null) {
            try {
                Object v = mGetBbWidth.invoke(le);
                if (v instanceof Float f && Float.isFinite(f) && f > 0.05F) return f;
            } catch (Throwable ignored) {
            }
        }
        return 0.70F;
    }

    private static void probeDimMethods() {
        if (dimMethodsProbed) return;
        dimMethodsProbed = true;
        try {
            mGetBbHeight = Entity.class.getMethod("getBbHeight");
            mGetBbWidth = Entity.class.getMethod("getBbWidth");
        } catch (Throwable ignored) {
        }
    }

    private static boolean isNonLivingNoise(String id, String path) {
        if ("minecraft:player".equals(id) || "minecraft:marker".equals(id)
                || "minecraft:armor_stand".equals(id)) {
            return true;
        }
        return path.endsWith("arrow")
                || path.endsWith("boat")
                || path.endsWith("raft")
                || path.endsWith("minecart")
                || path.equals("item")
                || path.endsWith("_item")
                || path.equals("experience_orb")
                || path.equals("falling_block")
                || path.equals("area_effect_cloud")
                || path.equals("lightning_bolt")
                || path.endsWith("fireball")
                || path.equals("snowball")
                || path.equals("egg")
                || path.endsWith("ender_pearl")
                || path.equals("eye_of_ender")
                || path.equals("experience_bottle")
                || path.equals("potion")
                || path.equals("tnt")
                || path.equals("firework_rocket")
                || path.equals("fishing_bobber")
                || path.equals("leash_knot")
                || path.equals("painting")
                || path.endsWith("item_frame")
                || path.equals("end_crystal")
                || path.equals("evoker_fangs")
                || path.equals("llama_spit")
                || path.equals("shulker_bullet")
                || path.equals("wither_skull")
                || path.equals("trident")
                || path.endsWith("wind_charge")
                || path.endsWith("_display")
                || path.equals("interaction")
                || path.equals("ominous_item_spawner");
    }

    public static String clip(Font font, String text, int maxW) {
        if (font == null || text == null || maxW <= 0) return "";
        if (font.width(text) <= maxW) return text;
        String s = text;
        while (s.length() > 1 && font.width(s + "\u2026") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? "" : s + "\u2026";
    }

    public static int categoryMatches(MobEntry e, int filterMode) {
        if (e == null) return 0;
        if (filterMode == CAT_HOSTILE) return e.hostile ? 1 : 0;
        if (filterMode == CAT_FRIENDLY) return !e.hostile ? 1 : 0;
        return 1;
    }

    /** Utility used by static callers that still read the raw color helper. */
    public static int mobAccent() {
        return TrialModule447.color(TrialModule447.SLOT_MOBS);
    }
}
