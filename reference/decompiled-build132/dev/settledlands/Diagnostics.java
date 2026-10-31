package dev.settledlands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Diagnostics {
   private static final Logger LOG = LoggerFactory.getLogger("settledlands");
   public static final int EXPECTED_MAX_LEVEL = 2;
   private boolean reportedDataFailure;

   public static int loadedMaxLevel(MinecraftServer var0) {
      Holder var1 = (Holder)var0.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(Sanctity.KEY).orElse(null);
      return var1 == null ? -1 : ((Enchantment)var1.value()).getMaxLevel();
   }

   public static String version() {
      Optional var0 = ModList.get().getModContainerById("settledlands");
      String var1 = var0.<String>map(var0x -> var0x.getModInfo().getVersion().toString()).orElse("не загружен");
      IModFileInfo var2 = ModList.get().getModFileById("settledlands");
      String var3 = var2 == null ? "файл неизвестен" : var2.getFile().getFileName();
      return var1 + " (" + var3 + ")";
   }

   public static Component report(MinecraftServer var0) {
      int var1 = loadedMaxLevel(var0);
      String var2 = "Settled Lands: версия мода " + version() + " | максимальный уровень Святости в реестре: " + var1;
      return var1 == 2
         ? Component.literal(var2 + " — всё в порядке.")
         : Component.literal(
            var2
               + " — ОШИБКА. Ожидается 2. Значит данные зачарования подменяет что-то ещё: старый JAR рядом в mods, копия старого файла в datapacks/ или в мире (папка datapacks), либо сторонний датапак с тем же путём. Проверьте /datapack list и папки mods и datapacks."
         );
   }

   @SubscribeEvent
   public void dataFailure(Post var1) {
      if (!this.reportedDataFailure && TerritoryData.LAST_LOAD_FAILED) {
         this.reportedDataFailure = true;
         Path var2 = var1.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("settledlands_territories.dat");
         Path var3 = null;

         try {
            if (Files.exists(var2)) {
               var3 = var2.resolveSibling("settledlands_territories.corrupt-" + System.currentTimeMillis() + ".dat.bak");
               Files.copy(var2, var3, StandardCopyOption.COPY_ATTRIBUTES);
            }
         } catch (IOException var5) {
            LOG.error("[settledlands] Не удалось скопировать файл данных территорий.", var5);
         }

         LOG.error(
            "[settledlands] Данные территорий НЕ загружены, изменения не сохраняются до перезапуска сервера с исправленным файлом. Файл: {}; копия: {}",
            var2,
            var3 == null ? "не создана" : var3.toString()
         );
         LOG.error(
            "[settledlands] Проверьте world/data/settledlands_territories.dat (при необходимости подставьте копию из бэкапа) и перезапустите сервер. Если файл не нужен — удалите его, территории начнут отсчёт заново."
         );
      }
   }

   @SubscribeEvent
   public void started(ServerStartedEvent var1) {
      int var2 = loadedMaxLevel(var1.getServer());
      if (var2 == 2) {
         LOG.info("[settledlands] loaded {}; Sanctity max level = {} as expected", version(), var2);
      } else {
         LOG.error("[settledlands] WARNING: loaded {} but the enchantment registry reports max level {} instead of {}.", new Object[]{version(), var2, 2});
         LOG.error(
            "[settledlands] Something else provides data/settledlands/enchantment/sanctity.json: an old Settled Lands jar, a copy of the old datapack, or a world datapack."
         );
         LOG.error("[settledlands] Run /cleansing version in game and /datapack list to see the active data packs.");
      }
   }
}
