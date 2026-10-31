package dev.settledlands;

import dev.settledlands.revival.PurgatoryManager;
import dev.settledlands.ritual.RitualManager;
import dev.settledlands.ritual.RitualPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.LoggerFactory;

@Mod("settledlands")
public final class SettledLands {
   public static final String ID = "settledlands";
   public static final String BUILD = "46-purgatory-rules";

   public SettledLands(IEventBus var1, ModContainer var2) {
      LoggerFactory.getLogger("settledlands").info("Settled Lands: сборка {} загружена", "46-purgatory-rules");
      var2.registerConfig(Type.SERVER, Settings.SPEC);
      NeoForge.EVENT_BUS.register(new WorldEvents());
      NeoForge.EVENT_BUS.register(new Sanctity());
      NeoForge.EVENT_BUS.register(new Diagnostics());
      NeoForge.EVENT_BUS.register(new AnvilBannerMerge());
      NeoForge.EVENT_BUS.register(new SanctityTooltip());
      var1.addListener(DebugPayload::register);
      NeoForge.EVENT_BUS.register(new RitualManager());
      var1.addListener(RitualPayload::register);
      PurgatoryManager.init(var1);
   }
}
