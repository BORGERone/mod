package dev.settledlands.npc;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Coordinate recording stick.
 * Right-clicking a block renames the stick to the block's "X Y Z" coordinates
 * and displays an actionbar confirmation to the player.
 */
public final class CoordStickItem extends Item {
   public CoordStickItem(Properties properties) {
      super(properties);
   }

   @Override
   public InteractionResult useOn(UseOnContext context) {
      BlockPos pos = context.getClickedPos();
      if (context.getLevel().isClientSide()) {
         return InteractionResult.SUCCESS;
      }

      ItemStack stack = context.getItemInHand();
      String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
      stack.set(DataComponents.CUSTOM_NAME, Component.literal(coords));

      if (context.getPlayer() != null) {
         context.getPlayer().displayClientMessage(
            Component.literal("§a📍 Координаты записаны в палку: §f" + coords),
            true
         );
         context.getLevel().playSound(
            null,
            pos,
            SoundEvents.EXPERIENCE_ORB_PICKUP,
            SoundSource.PLAYERS,
            0.6f,
            1.2f
         );
      }
      return InteractionResult.SUCCESS;
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("item.settledlands.coord_stick.desc"));
   }
}
