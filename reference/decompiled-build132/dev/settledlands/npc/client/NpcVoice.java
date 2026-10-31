package dev.settledlands.npc.client;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.Sound.Type;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.slf4j.Logger;

public final class NpcVoice {
   private static final RandomSource RANDOM = RandomSource.create();
   private static final Logger LOGGER = LogUtils.getLogger();
   private static String lastLoggedState = "";

   private NpcVoice() {
   }

   public static List<String> list() {
      ArrayList var0 = new ArrayList();
      Map var1 = Minecraft.getInstance()
         .getResourceManager()
         .listResources("sounds/npc", var0x -> var0x.getPath().endsWith(".ogg") && var0x.getNamespace().equals("settledlands"));

      for (ResourceLocation var3 : var1.keySet()) {
         String var4 = var3.getPath();
         var4 = var4.substring("sounds/npc/".length(), var4.length() - ".ogg".length());
         if (!var0.contains(var4)) {
            var0.add(var4);
         }
      }

      var0.sort(String::compareTo);
      return var0;
   }

   public static void playTyping(String var0, int var1) {
      float var2 = 1.0F;
      if (var1 > 0) {
         double var3 = (RANDOM.nextDouble() * 2.0 - 1.0) * var1 / 12.0;
         var2 = (float)Math.pow(2.0, var3);
      }

      boolean var5 = var0 != null && !var0.isEmpty() && available(var0);
      String var4 = var0 + "|" + var5 + "|" + var1;
      if (!var4.equals(lastLoggedState)) {
         lastLoggedState = var4;
         if (var5) {
            LOGGER.info("[settledlands] Печать: голос='{}' — файл есть, играю его (громкость 1.0, разброс ±{} пт)", var0, var1);
         } else if (var0 != null && !var0.isEmpty()) {
            LOGGER.info("[settledlands] Печать: голос='{}' — ФАЙЛ НЕ НАЙДЕН в ресурсах, играет стандартный клик", var0);
         } else {
            LOGGER.info("[settledlands] Печать: голос не задан (стандартный), разброс ±{} пт", var1);
         }
      }

      if (var5) {
         Minecraft.getInstance().getSoundManager().play(new NpcVoice.Direct(var0, 1.0F, var2));
      } else {
         Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.22F * var2));
      }
   }

   private static boolean available(String var0) {
      return Minecraft.getInstance().getResourceManager().getResource(soundFile(var0)).isPresent();
   }

   private static ResourceLocation soundFile(String var0) {
      return ResourceLocation.fromNamespaceAndPath("settledlands", "sounds/npc/" + var0 + ".ogg");
   }

   private static final class Direct extends SimpleSoundInstance {
      Direct(String var1, float var2, float var3) {
         super(
            ResourceLocation.fromNamespaceAndPath("settledlands", "npc/" + var1),
            SoundSource.MASTER,
            var2,
            var3,
            SoundInstance.createUnseededRandom(),
            false,
            0,
            Attenuation.NONE,
            0.0,
            0.0,
            0.0,
            true
         );
      }

      public WeighedSoundEvents resolve(SoundManager var1) {
         this.sound = new Sound(this.location, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Type.FILE, false, false, 16);
         return new WeighedSoundEvents(this.location, null);
      }
   }
}
