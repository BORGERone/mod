package dev.settledlands.door;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 368: the key of the doors - one new item, {@code settledlands:door_key}.
 *
 * <p>The item itself does nothing: a key is not a tool, it is a marker. The behaviour lives in
 * {@link DoorEvents368}, which asks "is the thing in the hand this item" and then locks or unlocks the
 * door that was clicked. That is on purpose - an item with its own {@code useOn} would have to be a
 * class of ours, and every other item of this mod is a plain {@code Item} registered the same way the
 * mob effect already is ({@code PossessionSystem$ModBus}), so nothing new is invented here.</p>
 *
 * <p>Registration runs on the mod bus, once, at start-up. {@code key()} is then a plain static read,
 * safe from a tick, a screen or a probe.</p>
 */
@EventBusSubscriber(modid = "settledlands", bus = EventBusSubscriber.Bus.MOD)
public final class DoorKey368 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DoorKey368");

    /** The registry name of the item: {@code settledlands:door_key}. */
    public static final String ID = "door_key";

    private static Item item;
    private static boolean failed;

    private DoorKey368() {}

    /** The key, or null when the game never registered it (then nothing locks and nothing is lost). */
    public static Item key() {
        return item;
    }

    /** True when the thing in hand is the key. Null-safe: an empty hand is not a lock-picking tool. */
    public static boolean isKey(net.minecraft.world.item.ItemStack stack) {
        return item != null && stack != null && !stack.isEmpty() && stack.is(item);
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        try {
            if (event == null || !Registries.ITEM.equals(event.getRegistryKey())) return;
            item = new Item(new Item.Properties().stacksTo(1));
            event.register(Registries.ITEM, NpcIds368.id(ID), () -> item);
            LOG.info("door: settledlands:door_key registered");
        } catch (Throwable failure) {
            if (failed) return;
            failed = true;
            LOG.warn("the door key could not be registered: " + failure);
        }
    }
}
