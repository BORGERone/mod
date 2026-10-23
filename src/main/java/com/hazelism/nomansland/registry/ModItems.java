package com.hazelism.nomansland.registry;

import com.hazelism.nomansland.NoMansLand;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All items. Stage 1 (this slice): plain items + spawn egg.
 * The pots become real throwables in stage 2 - do not add use()/releaseUsing() logic here,
 * it belongs to the projectile items themselves.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NoMansLand.MOD_ID);

    // Throwable pots (Bedrock: max_stack_size 16). Behaviour arrives with the projectile stage.
    public static final DeferredItem<Item> FIRE_POT  = ITEMS.registerSimpleItem("fire_pot",  new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> SMOKE_POT = ITEMS.registerSimpleItem("smoke_pot", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> RESIN_POT = ITEMS.registerSimpleItem("resin_pot", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> RATTLE    = ITEMS.registerSimpleItem("rattle",    new Item.Properties().stacksTo(16));

    public static final DeferredItem<Item> SPRUCE_RESIN      = ITEMS.registerSimpleItem("spruce_resin");
    public static final DeferredItem<Item> WOODEN_BARRICADE  = ITEMS.registerSimpleItem("wooden_barricade", new Item.Properties().stacksTo(64));

    // DeferredSpawnEggItem takes a Supplier, so registration order vs. ModEntities does not matter.
    public static final DeferredItem<Item> UNDEAD_SPAWN_EGG = ITEMS.registerItem("undead_spawn_egg",
            props -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.UNDEAD, 0x4A5D23, 0x8B0000, props));

    public static final DeferredItem<Item> BANNER_BEARER_SPAWN_EGG = ITEMS.registerItem("banner_bearer_spawn_egg",
            props -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.BANNER_BEARER, 0x5D1A1A, 0xD8C690, props));

    private ModItems() {}
}
