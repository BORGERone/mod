package dev.settledlands;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

public final class Settings {
   private static final Builder B = new Builder();
   public static final IntValue TEMP_MAX = B.comment("Temporary threshold. Restart after changing settings.")
      .defineInRange("temporaryThreshold", 100, 1, 100000);
   public static final IntValue PERM_MAX = B.defineInRange("permanentThreshold", 200, 1, 1000000);
   public static final BooleanValue PERMANENT = B.define("permanentSafety", true);
   public static final IntValue GRACE = B.comment("Active seconds after a kill before decay.").defineInRange("decayGraceSeconds", 1800, 0, 604800);
   public static final IntValue DECAY = B.comment("Active seconds per lost temporary point.").defineInRange("secondsPerLostPoint", 300, 1, 604800);
   public static final IntValue KILL_RADIUS = B.comment("Horizontal cell radius; 1 = 3x3. Cells are fixed at 16x8x16.")
      .defineInRange("killRadiusCells", 1, 0, 3);
   public static final IntValue VISIT_GRACE = B.defineInRange("visitGraceSeconds", 60, 0, 3600);
   public static final IntValue HABITATION = B.comment("Occupied seconds after visit delay required to establish a household.")
      .defineInRange("habitationSeconds", 1800, 1, 604800);
   public static final IntValue CATEGORIES = B.defineInRange("requiredHouseholdCategories", 2, 1, 4);
   public static final IntValue MAX_CELLS = B.comment("Per-dimension soft limit, including permanent cells. No progress is evicted.")
      .defineInRange("maxTrackedCells", 250000, 1000, 10000000);
   public static final IntValue MAX_BLOCKS = B.defineInRange("maxHouseholdBlocks", 100000, 1000, 10000000);
   public static final BooleanValue PAUSE_EMPTY = B.define("pauseDecayWhenEmpty", true);
   public static final BooleanValue UNKNOWN = B.comment("Credit unmarked mobs, including pre-install mobs. False prevents unknown-source exploits.")
      .define("creditUnknownOrigins", false);
   public static final ConfigValue<List<? extends String>> EXCLUDED = B.defineListAllowEmpty(
      "excludedEntities",
      List.of("minecraft:slime", "minecraft:magma_cube", "minecraft:wither", "minecraft:ender_dragon", "minecraft:warden"),
      () -> "minecraft:slime",
      x -> x instanceof String
   );
   public static final ConfigValue<List<? extends String>> INCLUDED = B.comment("Extra non-MONSTER entity IDs; exclusions take precedence.")
      .defineListAllowEmpty("includedEntities", List.of(), () -> "example:hostile", x -> x instanceof String);
   public static final BooleanValue RITUALS = B.comment(
         "Ordinary zombies prioritize holy banners and perform the ritual within 8 blocks. One ritual per server. Respects mobGriefing."
      )
      .define("zombieRituals", true);
   public static final BooleanValue SANCTITY_FIRE = B.comment(
         "Sanctity II sets hostile mobs on fire within 12 blocks of the banner. Vanilla fire damage; never ignites blocks. Water and rain still extinguish."
      )
      .define("sanctityFire", true);
   public static final IntValue COST_LEVEL_I = B.comment("Experience levels the anvil charges for Sanctity I (transferring it onto a plain banner).")
      .defineInRange("sanctityLevelOneCost", 2, 0, 64);
   public static final IntValue COST_LEVEL_II = B.comment("Experience levels the anvil charges for Sanctity II (merging two level I banners).")
      .defineInRange("sanctityLevelTwoCost", 6, 0, 64);
   public static final ModConfigSpec SPEC = B.build();

   private Settings() {
   }
}
