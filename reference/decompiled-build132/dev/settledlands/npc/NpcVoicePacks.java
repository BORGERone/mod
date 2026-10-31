package dev.settledlands.npc;

import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.Pack.Metadata;
import net.minecraft.server.packs.repository.Pack.Position;
import net.minecraft.server.packs.repository.Pack.ResourcesSupplier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

@EventBusSubscriber(
   modid = "settledlands",
   bus = Bus.MOD
)
public final class NpcVoicePacks {
   public static final Path DIR = FMLPaths.CONFIGDIR.get().resolve("settledlands-voices");

   private NpcVoicePacks() {
   }

   @SubscribeEvent
   public static void onAddPackFinders(AddPackFindersEvent var0) {
      if (var0.getPackType() == PackType.CLIENT_RESOURCES) {
         NpcVoiceFolderPack.ensureDir(DIR);
         var0.addRepositorySource(
            var0x -> {
               Pack var1 = Pack.readMetaAndCreate(
                  new PackLocationInfo("settledlands-voices", NpcVoiceFolderPack.TITLE, PackSource.BUILT_IN, Optional.empty()), new ResourcesSupplier() {
                     public PackResources openPrimary(PackLocationInfo var1) {
                        return new NpcVoiceFolderPack(NpcVoicePacks.DIR);
                     }

                     public PackResources openFull(PackLocationInfo var1, Metadata var2) {
                        return new NpcVoiceFolderPack(NpcVoicePacks.DIR);
                     }
                  }, PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Position.BOTTOM, false)
               );
               if (var1 != null) {
                  var0x.accept(var1);
               }
            }
         );
      }
   }
}
