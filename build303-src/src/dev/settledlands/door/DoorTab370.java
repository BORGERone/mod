package dev.settledlands.door;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 370: the door key appears in the mod's creative tab.
 *
 * <p>Build 369 registered the key ({@code settledlands:door_key}) and gave it a name, a model and a
 * texture - and it was still invisible in creative. Registration and the tab are two different lists: a
 * tab is filled by its own display-items generator, and the mod fills its own with a hand-written list
 * of nine entries in the base code. Nothing was ever going to put a new item there.</p>
 *
 * <p>So the key is added the way this mod already adds the revival altar to vanilla tabs
 * ({@code PurgatoryManager.onBuildCreativeTab}): the tab is filled, then the game asks every listener
 * whether it wants to add something, and this one answers for {@code settledlands:guide} - the mod's own
 * tab - and, as a safety net, for the vanilla tools tab, because an item nobody can reach is an item
 * that does not exist for the author.</p>
 *
 * <p>Cost: one string comparison per tab built, and the game builds a handful of tabs once. The message
 * is logged once, so the log itself says whether the key landed in a tab.</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class DoorTab370 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DoorTab370");

    /** The mod's own tab, the one this key belongs in. */
    public static final String MOD_TAB = "settledlands:guide";

    /** The vanilla tab kept as a fallback, so the key is never unreachable. */
    private static final String FALLBACK_TAB = "minecraft:tools_and_utilities";

    private static boolean said;

    private DoorTab370() {}

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        try {
            if (event == null || event.getTabKey() == null || event.getTabKey().location() == null) return;
            String tab = event.getTabKey().location().toString();
            if (!MOD_TAB.equals(tab) && !FALLBACK_TAB.equals(tab)) return;
            Item key = DoorKey368.key();
            if (key == null) return;
            event.accept(new ItemStack(key), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            if (!said) {
                said = true;
                LOG.info("door key added to the creative tab {}", tab);
            }
        } catch (Throwable failure) {
            if (said) return;
            said = true;
            LOG.warn("the door key could not be added to a creative tab", failure);
        }
    }
}
