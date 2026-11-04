package net.minecraft.world.item;

/** Compile-time mirror of the item. Build 368 needs the real constructor: Item(Item.Properties). */
public class Item {
    public Item() {}

    public Item(Properties properties) {}

    public static class Properties {
        public Properties stacksTo(int count) { return this; }
    }
}
