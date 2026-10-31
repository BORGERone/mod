package dev.settledlands.npc.client;

import dev.settledlands.npc.GuideNet;
import dev.settledlands.npc.GuidePhrase;
import dev.settledlands.npc.GuideTomeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class GuideTomeScreen extends Screen {
   private static final int PANEL_BG = -535557084;
   private static final int PANEL_EDGE = -9678962;
   private static final int LABEL = -4216356;
   private static final int DIM = -1879048192;
   private final boolean offhand;
   private final String initialPhrase;
   private final int initialNumber;
   private EditBox phraseBox;
   private EditBox numberBox;
   private Component status = Component.empty();

   public GuideTomeScreen(boolean var1, String var2, int var3) {
      super(Component.translatable("gui.settledlands.tome.title"));
      this.offhand = var1;
      this.initialPhrase = var2;
      this.initialNumber = var3;
   }

   public static void open(ItemStack var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.player != null) {
         GuidePhrase var2 = GuideTomeItem.phraseOf(var0);
         boolean var3 = var0 == var1.player.getOffhandItem();
         var1.setScreen(new GuideTomeScreen(var3, var2 == null ? "" : var2.text(), var2 == null ? 1 : var2.number()));
      }
   }

   protected void init() {
      int var1 = this.width / 2;
      int var2 = this.height / 2 - 58;
      this.phraseBox = new EditBox(this.font, var1 - 110, var2, 220, 20, Component.translatable("gui.settledlands.tome.phrase"));
      this.phraseBox.setMaxLength(480);
      this.phraseBox.setValue(this.initialPhrase);
      this.addRenderableWidget(this.phraseBox);
      this.numberBox = new EditBox(this.font, var1 - 110, var2 + 52, 60, 20, Component.translatable("gui.settledlands.tome.number"));
      this.numberBox.setMaxLength(6);
      this.numberBox.setValue(String.valueOf(this.initialNumber));
      this.addRenderableWidget(this.numberBox);
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.settledlands.tome.sign"), var1x -> this.sign()).bounds(var1 + 10, var2 + 52, 100, 20).build()
      );
      this.setInitialFocus(this.phraseBox);
   }

   private void sign() {
      String var1 = GuideTomeItem.sanitize(this.phraseBox.getValue());
      int var2 = 0;

      try {
         var2 = Integer.parseInt(this.numberBox.getValue().trim());
      } catch (NumberFormatException var4) {
         var2 = 0;
      }

      if (!var1.isEmpty() && var2 >= 1 && var2 <= 999999) {
         GuideNet.sendToServer(new GuideNet.Sign(this.offhand, var2, var1));
         this.onClose();
      } else {
         this.status = Component.translatable("gui.settledlands.tome.invalid").withStyle(ChatFormatting.RED);
      }
   }

   public boolean keyPressed(int var1, int var2, int var3) {
      if (var1 != 257 && var1 != 335) {
         return super.keyPressed(var1, var2, var3);
      } else {
         this.sign();
         return true;
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void renderBackground(GuiGraphics var1, int var2, int var3, float var4) {
      var1.fill(0, 0, this.width, this.height, -1879048192);
   }

   public void render(GuiGraphics var1, int var2, int var3, float var4) {
      int var5 = this.width / 2;
      int var6 = this.height / 2 - 58;
      int var7 = var5 - 120;
      int var8 = var5 + 120;
      var1.fill(var7, var6 - 40, var8, var6 + 82, -535557084);
      var1.fill(var7, var6 - 40, var8, var6 - 39, -9678962);
      var1.fill(var7, var6 + 81, var8, var6 + 82, -9678962);
      var1.fill(var7, var6 - 40, var7 + 1, var6 + 82, -9678962);
      var1.fill(var8 - 1, var6 - 40, var8, var6 + 82, -9678962);
      var1.drawCenteredString(this.font, this.title, var5, var6 - 30, -1647873);
      var1.drawString(this.font, Component.translatable("gui.settledlands.tome.phrase.label"), var7 + 10, var6 - 12, -4216356, false);
      var1.drawString(this.font, Component.translatable("gui.settledlands.tome.number.label"), var7 + 10, var6 + 40, -4216356, false);
      var1.drawString(this.font, Component.translatable("gui.settledlands.tome.order"), var5 + 10, var6 + 40, -4216356, false);
      super.render(var1, var2, var3, var4);
      if (!this.status.getString().isEmpty()) {
         var1.drawCenteredString(this.font, this.status, var5, var6 + 88, -32640);
      }
   }
}
