package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Checkbox.OnValueChange;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

public final class NpcUi {
   public static final int LABEL = -2302756;
   public static final int DIM = -5723992;
   public static final int VALUE = -8080;
   public static final int SECTION = -11702;
   public static final int PANEL = -267251180;
   public static final int BLOCK = 419430399;
   public static final int BLOCK_EDGE = 721420287;
   public static final int BLOCK_SEP = 486539263;
   public static final int ROW = 822083583;
   public static final int ROW_HOVER = 1493172223;
   private static NpcUi.NpcSlider heldSlider;

   private NpcUi() {
   }

   public static Component questTopicName(NpcProfile.Topic var0) {
      MutableComponent var1 = Component.translatable(switch (var0.questRole) {
         case 2 -> "gui.settledlands.quest.dialog.revisit";
         case 3 -> "gui.settledlands.quest.dialog.hand";
         case 4 -> "gui.settledlands.quest.dialog.where";
         default -> "gui.settledlands.quest.dialog.offer";
      });
      String var2 = var0.title == null ? "" : var0.title;
      return var2.isEmpty() ? var1 : var1.copy().append(Component.literal(" · " + var2));
   }

   public static NpcUi.NpcSlider slider(int var0, int var1, int var2, int var3, int var4, int var5, IntFunction<Component> var6, IntConsumer var7) {
      return new NpcUi.NpcSlider(var0, var1, var2, var3, var4, var5, var6, var7);
   }

   public static boolean grabSlider(ContainerEventHandler var0, double var1, double var3) {
      for (GuiEventListener var6 : var0.children()) {
         if (var6 instanceof NpcUi.NpcSlider var7 && var7.grab(var1, var3)) {
            var0.setFocused(var7);
            var0.setDragging(true);
            return true;
         }
      }

      return false;
   }

   public static boolean dragSlider(double var0) {
      if (heldSlider == null) {
         return false;
      } else {
         heldSlider.dragTo(var0);
         return true;
      }
   }

   public static void endSliderDrag() {
      if (heldSlider != null) {
         heldSlider.dragging = false;
      }

      heldSlider = null;
   }

   public static double sliderFraction(double var0, int var2, int var3) {
      if (var3 <= 8) {
         return 0.0;
      } else {
         double var4 = (var0 - (var2 + 4)) / (var3 - 8);
         return var4 < 0.0 ? 0.0 : (var4 > 1.0 ? 1.0 : var4);
      }
   }

   public static NpcUi.NpcDropdown dropdown(int var0, int var1, int var2, List<Component> var3, int var4, IntConsumer var5) {
      return new NpcUi.NpcDropdown(var0, var1, var2, var3, var4, var5);
   }

   public static NpcUi.ItemButton itemButton(int var0, int var1, int var2, ItemStack var3, Runnable var4) {
      return new NpcUi.ItemButton(var0, var1, var2, var3, var4);
   }

   public static Checkbox checkbox(int var0, int var1, Component var2, boolean var3, OnValueChange var4) {
      Font var5 = Minecraft.getInstance().font;
      Checkbox var6 = Checkbox.builder(var2, var5).pos(var0, var1).selected(var3).maxWidth(Math.max(60, var5.width(var2) + 24)).onValueChange(var4).build();
      var6.setTooltip(Tooltip.create(var2));
      return var6;
   }

   public static final class ItemButton extends AbstractWidget {
      private final ItemStack stack;
      private final Runnable onPress;

      ItemButton(int var1, int var2, int var3, ItemStack var4, Runnable var5) {
         super(var1, var2, var3, 20, Component.empty());
         this.stack = var4 == null ? ItemStack.EMPTY : var4;
         if (!this.stack.isEmpty()) {
            this.setTooltip(Tooltip.create(this.stack.getHoverName()));
         }

         this.onPress = var5;
      }

      public void onClick(double var1, double var3) {
         if (this.onPress != null) {
            this.onPress.run();
         }
      }

      public void onRelease(double var1, double var3) {
      }

      protected void renderWidget(GuiGraphics var1, int var2, int var3, float var4) {
         int var5 = this.getX();
         int var6 = this.getY();
         int var7 = this.getWidth();
         int var8 = this.getHeight();
         boolean var9 = this.active && this.isHoveredOrFocused();
         var1.fill(var5 - 1, var6 - 1, var5 + var7 + 1, var6 + var8 + 1, -13948117);
         var1.fill(var5, var6, var5 + var7, var6 + var8, var9 ? -12961222 : -14474461);
         Font var10 = Minecraft.getInstance().font;
         if (this.stack.isEmpty()) {
            var1.drawString(var10, "?", var5 + 5, var6 + (var8 - 8) / 2, -5723992, false);
         } else {
            var1.renderItem(this.stack, var5 + 3, var6 + 2);
            String var11 = this.stack.getHoverName().getString();
            int var12 = Math.max(8, var7 - 26);
            String var13 = var10.width(var11) <= var12 ? var11 : var10.plainSubstrByWidth(var11, var12 - 4) + "…";
            var1.drawString(var10, var13, var5 + 23, var6 + (var8 - 8) / 2, this.active ? -1 : -5723992, false);
         }
      }

      public void updateWidgetNarration(NarrationElementOutput var1) {
         this.defaultButtonNarrationText(var1);
      }
   }

   public static final class NpcDropdown extends AbstractWidget {
      private static final int ROW_HEIGHT = 14;
      private static final int MAX_VISIBLE = 7;
      private final List<Component> entries = new ArrayList<>();
      private final IntConsumer onChange;
      private int index;
      private int scroll;
      private boolean open;

      NpcDropdown(int var1, int var2, int var3, List<Component> var4, int var5, IntConsumer var6) {
         super(var1, var2, var3, 20, Component.empty());
         if (var4 != null) {
            this.entries.addAll(var4);
         }

         this.onChange = var6;
         this.index = Math.max(0, Math.min(var5, Math.max(0, this.entries.size() - 1)));
         this.updateMessage();
      }

      public Component current() {
         return (Component)(this.entries.isEmpty() ? Component.empty() : this.entries.get(this.clampIndex(this.index)));
      }

      private int clampIndex(int var1) {
         return Math.max(0, Math.min(var1, this.entries.size() - 1));
      }

      private void updateMessage() {
         Component var1 = this.current();
         this.setMessage(Component.empty().append(var1).append(" ▾"));
      }

      public void onClick(double var1, double var3) {
         this.open = !this.open;
         if (this.open) {
            this.scroll = Math.max(0, Math.min(this.index - 3, Math.max(0, this.entries.size() - 7)));
         }

         Minecraft var5 = Minecraft.getInstance();
         if (var5.screen instanceof NpcUi.PopupHost var6) {
            var6.openDropdown(this.open ? this : null);
         }
      }

      public void onRelease(double var1, double var3) {
      }

      protected void renderWidget(GuiGraphics var1, int var2, int var3, float var4) {
         int var5 = this.getX();
         int var6 = this.getY();
         int var7 = this.getWidth();
         int var8 = this.getHeight();
         boolean var9 = this.isHoveredOrFocused();
         var1.fill(var5 - 1, var6 - 1, var5 + var7 + 1, var6 + var8 + 1, -13948117);
         var1.fill(var5, var6, var5 + var7, var6 + var8, var9 ? -12961222 : -14474461);
         Font var10 = Minecraft.getInstance().font;
         Component var11 = this.current();
         int var12 = this.active ? -1 : -5723992;
         int var13 = var5 + 5;
         String var14 = var10.plainSubstrByWidth(var11.getString(), var7 - 20);
         Object var15 = var11.getString().equals(var14) ? var11 : Component.literal(var14);
         var1.drawString(var10, (Component)var15, var13, var6 + (var8 - 8) / 2, var12, false);
         var1.drawString(var10, "▾", var5 + var7 - 10, var6 + (var8 - 8) / 2, -5723992, false);
      }

      public void updateWidgetNarration(NarrationElementOutput var1) {
         this.defaultButtonNarrationText(var1);
      }

      public void renderPopup(GuiGraphics var1, int var2, int var3) {
         if (this.open && !this.entries.isEmpty()) {
            Font var4 = Minecraft.getInstance().font;
            int var5 = Math.min(7, this.entries.size());
            int var6 = this.getX();
            int var7 = this.getY() + this.getHeight();
            int var8 = Math.max(this.getWidth(), 90);
            var1.fill(var6 - 1, var7 - 1, var6 + var8 + 1, var7 + var5 * 14 + 1, -16777216);
            var1.fill(var6, var7, var6 + var8, var7 + var5 * 14, -266856424);

            for (int var9 = 0; var9 < var5; var9++) {
               int var10 = this.scroll + var9;
               if (var10 >= 0 && var10 < this.entries.size()) {
                  int var11 = var7 + var9 * 14;
                  boolean var12 = var2 >= var6 && var2 <= var6 + var8 && var3 >= var11 && var3 < var11 + 14;
                  if (var12) {
                     var1.fill(var6, var11, var6 + var8, var11 + 14, 1493172223);
                  }

                  Component var13 = this.entries.get(var10);
                  String var14 = var4.plainSubstrByWidth(var13.getString(), var8 - 10);
                  var1.drawString(var4, var14, var6 + 5, var11 + 3, var10 == this.index ? -8080 : -2302756, false);
               }
            }

            if (this.entries.size() > var5) {
               var1.fill(var6 + var8 - 3, var7 + 1, var6 + var8 - 1, var7 + 1 + Math.max(6, var5 * 14 * var5 / this.entries.size()), -9803158);
            }
         }
      }

      public boolean popupClicked(double var1, double var3) {
         if (!this.open) {
            return false;
         } else {
            int var5 = Math.min(7, this.entries.size());
            int var6 = this.getX();
            int var7 = this.getY() + this.getHeight();
            int var8 = Math.max(this.getWidth(), 90);
            if (!(var1 < var6) && !(var1 > var6 + var8) && !(var3 < var7) && !(var3 > var7 + var5 * 14)) {
               int var9 = (int)((var3 - var7) / 14.0);
               int var10 = this.scroll + var9;
               if (var10 >= 0 && var10 < this.entries.size()) {
                  this.index = var10;
                  this.updateMessage();
                  this.open = false;
                  if (this.onChange != null) {
                     this.onChange.accept(this.index);
                  }
               }

               return true;
            } else {
               this.open = false;
               return this.isMouseOver(var1, var3);
            }
         }
      }

      public boolean popupOpen() {
         return this.open;
      }

      public void closePopup() {
         this.open = false;
      }

      public boolean popupScrolled(double var1, double var3, int var5) {
         if (!this.open) {
            return false;
         } else {
            int var6 = Math.min(7, this.entries.size());
            if (this.entries.size() <= var6) {
               return false;
            } else {
               this.scroll = Math.max(0, Math.min(this.scroll - var5, this.entries.size() - var6));
               return true;
            }
         }
      }
   }

   public static final class NpcSlider extends AbstractSliderButton {
      private final int min;
      private final int max;
      private final IntFunction<Component> label;
      private final IntConsumer onChange;
      private boolean silent;
      private boolean dragging;

      NpcSlider(int var1, int var2, int var3, int var4, int var5, int var6, IntFunction<Component> var7, IntConsumer var8) {
         super(var1, var2, var3, 20, Component.empty(), toFraction(var6, var4, var5));
         this.min = var4;
         this.max = var5;
         this.label = var7;
         this.onChange = var8;
         this.silent = true;
         this.updateMessage();
         this.silent = false;
      }

      private static double toFraction(int var0, int var1, int var2) {
         return var2 <= var1 ? 0.0 : (double)(Math.max(var1, Math.min(var2, var0)) - var1) / (var2 - var1);
      }

      public int intValue() {
         return (int)Math.round(this.min + this.value * (this.max - this.min));
      }

      boolean grab(double var1, double var3) {
         if (this.active && this.visible && this.isMouseOver(var1, var3)) {
            this.dragging = true;
            NpcUi.heldSlider = this;
            this.dragTo(var1);
            return true;
         } else {
            return false;
         }
      }

      void dragTo(double var1) {
         int var3 = this.intValue();
         this.value = NpcUi.sliderFraction(var1, this.getX(), this.getWidth());
         if (this.intValue() != var3) {
            this.updateMessage();
            this.applyValue();
         }
      }

      public void onClick(double var1, double var3) {
         this.grab(var1, var3);
      }

      public boolean mouseClicked(double var1, double var3, int var5) {
         return var5 == 0 && this.grab(var1, var3) ? true : super.mouseClicked(var1, var3, var5);
      }

      public boolean mouseDragged(double var1, double var3, int var5, double var6, double var8) {
         if (this.dragging && var5 == 0) {
            this.dragTo(var1);
            return true;
         } else {
            return super.mouseDragged(var1, var3, var5, var6, var8);
         }
      }

      public boolean mouseReleased(double var1, double var3, int var5) {
         boolean var6 = this.dragging && var5 == 0;
         this.dragging = false;
         if (NpcUi.heldSlider == this) {
            NpcUi.heldSlider = null;
         }

         return var6 || super.mouseReleased(var1, var3, var5);
      }

      protected void updateMessage() {
         this.setMessage(this.label.apply(this.intValue()));
      }

      protected void applyValue() {
         if (!this.silent && this.onChange != null) {
            this.onChange.accept(this.intValue());
         }
      }

      public void renderWidget(GuiGraphics var1, int var2, int var3, float var4) {
         int var5 = this.getX();
         int var6 = this.getY();
         int var7 = this.getWidth();
         int var8 = this.getHeight();
         int var9 = this.active ? -13948117 : -14474461;
         var1.fill(var5, var6, var5 + var7, var6 + var8, -15263974);
         var1.fill(var5 + 1, var6 + 1, var5 + var7 - 1, var6 + var8 - 1, var9);
         int var10 = var5 + (int)Math.round(this.value * (var7 - 8));
         int var11 = this.active ? -10770372 : -12620238;
         var1.fill(var5 + 1, var6 + 1, var10, var6 + var8 - 1, var11);
         var1.fill(var10, var6, var10 + 8, var6 + var8, this.isHoveredOrFocused() ? -1 : -2565928);
         var1.fill(var10, var6, var10 + 8, var6 + 1, -9803158);
         var1.fill(var10, var6 + var8 - 1, var10 + 8, var6 + var8, -9803158);
         Font var12 = Minecraft.getInstance().font;
         Component var13 = this.getMessage();
         if (!var13.getString().isEmpty()) {
            int var14 = this.active ? -1 : -5723992;
            var1.drawString(var12, var13, var5 + 6, var6 + (var8 - 8) / 2, var14, true);
         }
      }

      public void updateWidgetNarration(NarrationElementOutput var1) {
         this.defaultButtonNarrationText(var1);
      }
   }

   public interface PopupHost {
      void openDropdown(NpcUi.NpcDropdown var1);

      NpcUi.NpcDropdown activeDropdown();
   }
}
