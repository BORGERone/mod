package dev.settledlands.npc;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Cutscene wand: right-click toggles the spectator camera session (permission 2+). */
public final class CutsceneWandItem extends Item {
   public CutsceneWandItem(Properties properties) {
      super(properties);
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (player instanceof ServerPlayer sp) {
         if (!sp.hasPermissions(2)) {
            sp.displayClientMessage(Component.translatable("message.settledlands.cfg.noperm"), true);
            return InteractionResultHolder.fail(stack);
         }
         if (CutsceneCamera.isActive(sp)) {
            CutsceneCamera.exit(sp);
         } else {
            CutsceneCamera.enter(sp);
         }
         return InteractionResultHolder.sidedSuccess(stack, false);
      }
      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
   }
}
