package dev.settledlands.npc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackResources.ResourceOutput;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

public final class NpcVoiceFolderPack implements PackResources {
   public static final String NAMESPACE = "settledlands";
   public static final String PREFIX = "sounds/npc/";
   static final Component TITLE = Component.literal("Settled Lands - голоса NPC (папка)");
   private final Path dir;
   private final PackLocationInfo location;

   public NpcVoiceFolderPack(Path var1) {
      this.dir = var1;
      this.location = new PackLocationInfo("settledlands-voices", TITLE, PackSource.BUILT_IN, Optional.empty());
   }

   public static void ensureDir(Path var0) {
      try {
         Files.createDirectories(var0);
         Path var1 = var0.resolve("ПРОЧТИ.txt");
         if (Files.notExists(var1)) {
            Files.writeString(
               var1,
               "Кидайте сюда ogg-файлы голосов NPC.\nИмя файла = имя голоса в редакторе NPC (Скеlet.ogg -> \"skelet\", пробелы -> \"_\").\nПосле добавления/замены файлов: F3+T (перезагрузка ресурсов), затем заново открыть редактор NPC.\n",
               StandardCharsets.UTF_8
            );
         }
      } catch (IOException var2) {
      }
   }

   public PackLocationInfo location() {
      return this.location;
   }

   public IoSupplier<InputStream> getRootResource(String... var1) {
      return null;
   }

   public IoSupplier<InputStream> getResource(PackType var1, ResourceLocation var2) {
      if (var1 == PackType.CLIENT_RESOURCES && var2.getNamespace().equals("settledlands")) {
         String var3 = var2.getPath();
         if (var3.startsWith("sounds/npc/") && var3.endsWith(".ogg")) {
            String var4 = var3.substring("sounds/npc/".length(), var3.length() - ".ogg".length());
            if (!ResourceLocation.isValidPath(var3)) {
               return null;
            } else {
               Path var5 = this.resolve(var4);
               return var5 == null ? null : () -> {
                  try {
                     return Files.newInputStream(var5);
                  } catch (IOException var2x) {
                     throw new RuntimeException(var2x);
                  }
               };
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public void listResources(PackType var1, String var2, String var3, ResourceOutput var4) {
      if (var1 == PackType.CLIENT_RESOURCES && var2.equals("settledlands") && var3.startsWith("sounds/npc") && Files.isDirectory(this.dir)) {
         try (Stream var5 = Files.list(this.dir)) {
            var5.filter(var0 -> Files.isRegularFile(var0) && var0.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg")).forEach(var1x -> {
               String var2x = cleanName(var1x.getFileName().toString());
               if (!var2x.isEmpty()) {
                  String var3x = "sounds/npc/" + var2x + ".ogg";
                  if (ResourceLocation.isValidPath(var3x)) {
                     var4.accept(ResourceLocation.fromNamespaceAndPath("settledlands", var3x), (IoSupplier)() -> {
                        try {
                           return Files.newInputStream(var1x);
                        } catch (IOException var2xx) {
                           throw new RuntimeException(var2xx);
                        }
                     });
                  }
               }
            });
         } catch (IOException var10) {
         }
      }
   }

   static String cleanName(String var0) {
      String var1 = var0.toLowerCase(Locale.ROOT).replace(' ', '_');
      int var2 = var1.lastIndexOf(".ogg");
      return var2 > 0 ? var1.substring(0, var2) : "";
   }

   public Set<String> getNamespaces(PackType var1) {
      return var1 == PackType.CLIENT_RESOURCES ? Set.of("settledlands") : Set.of();
   }

   public <T> T getMetadataSection(MetadataSectionSerializer<T> var1) {
      return (T)(var1 == PackMetadataSection.TYPE ? new PackMetadataSection(TITLE, 34, Optional.empty()) : null);
   }

   public void close() {
   }

   private Path resolve(String var1) {
      Path var2 = this.dir.resolve(var1 + ".ogg");
      if (Files.isRegularFile(var2)) {
         return var2;
      } else {
         try {
            Path var4;
            try (Stream var3 = Files.list(this.dir)) {
               var4 = var3.filter(var1x -> var1x.getFileName().toString().equalsIgnoreCase(var1 + ".ogg") && Files.isRegularFile(var1x))
                  .findFirst()
                  .orElse(null);
            }

            return var4;
         } catch (IOException var8) {
            return null;
         }
      }
   }
}
