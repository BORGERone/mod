package dev.settledlands.npc;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Build 348 - an NPC wears the armour it carries.
 *
 * <p>An NPC keeps what the player put into it in its own 27-slot inventory, and until now that armour
 * only existed as an item in a list: the client draws whatever sits in the mob's equipment slots, so
 * nothing showed up on the body. This class mirrors the inventory into those four slots. No new UI and
 * no new storage - the source of truth stays the NPC's inventory, exactly as the author asked.</p>
 *
 * <h2>Why the equipment slots and not a packet</h2>
 * <p>{@code LivingEntity#setItemSlot} marks the slot as changed; the server entity tracker picks that up on its
 * own and sends the vanilla equipment update to every client watching the NPC. So the plate reaches
 * the client through the game's own path, and the renderer only has to read {@code getItemBySlot}.</p>
 *
 * <h2>What it does not do</h2>
 * <p>It never moves or removes anything from the inventory: the worn piece is a copy of the stack the
 * inventory holds, re-read whenever the inventory changes, so a stack can be neither duplicated nor
 * lost by this code. Taking the piece out of the inventory clears the slot again.</p>
 */
public final class NpcArmor348 {
    /** The four armour slots, in the order vanilla's armour layer walks them. */
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private NpcArmor348() {
    }

    /**
     * Makes the mob's armour slots match the NPC's inventory.
     *
     * <p>Cheap enough to run on every inventory edit: at most 4 x 27 stack reads, and a slot is only
     * written when the item actually differs, so an unchanged inventory costs nothing but the reads.</p>
     *
     * @param npc       the NPC whose body should show the armour
     * @param inventory the NPC's own inventory
     */
    public static void sync(GuideNpc npc, Container inventory) {
        if (npc == null || inventory == null) {
            return;
        }
        int size = inventory.getContainerSize();
        for (int s = 0; s < SLOTS.length; s++) {
            EquipmentSlot slot = SLOTS[s];
            ItemStack wanted = null;
            for (int i = 0; i < size; i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                if (!(stack.getItem() instanceof ArmorItem)) {
                    continue;
                }
                ArmorItem armor = (ArmorItem) stack.getItem();
                if (armor.getEquipmentSlot() != slot) {
                    continue;
                }
                wanted = stack;
                break;
            }
            apply(npc, slot, wanted);
        }
    }

    /**
     * Writes one slot, and leaves it alone when the two already agree.
     *
     * <p>The comparison is on the item, not on the stack, so re-reading an unchanged inventory neither
     * allocates nor resends an equipment update.</p>
     */
    private static void apply(GuideNpc npc, EquipmentSlot slot, ItemStack wanted) {
        ItemStack worn = ((LivingEntity) npc).getItemBySlot(slot);
        Item wantedItem = wanted == null ? null : wanted.getItem();
        Item wornItem = worn == null || worn.isEmpty() ? null : worn.getItem();
        if (wornItem == wantedItem) {
            return;
        }
        ((LivingEntity) npc).setItemSlot(slot, wanted == null ? ItemStack.EMPTY : wanted.copy());
    }
}
