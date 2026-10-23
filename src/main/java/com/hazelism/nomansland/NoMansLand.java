package com.hazelism.nomansland;

import com.hazelism.nomansland.entity.BannerBearerEntity;
import com.hazelism.nomansland.entity.UndeadEntity;
import com.hazelism.nomansland.registry.ModCreativeTabs;
import com.hazelism.nomansland.registry.ModEntities;
import com.hazelism.nomansland.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import org.slf4j.Logger;

/**
 * No Man's Land - Dark Ages (NeoForge 1.21.1 port of the Bedrock addon).
 *
 * <p>Performance rules for the whole mod (do not break them in later stages):
 * <ul>
 *   <li>no world scans / entity iteration in ticks - react to events instead;</li>
 *   <li>no allocations in {@code tick()}, animation predicates or particle code;</li>
 *   <li>client-only classes live under {@code .client} and are never referenced
 *       from common code, so a dedicated server never loads rendering classes;</li>
 *   <li>state that must reach the client goes through SynchedEntityData, not packets,
 *       unless there is no vanilla mechanism for it.</li>
 * </ul>
 */
@Mod(NoMansLand.MOD_ID)
public final class NoMansLand {
    public static final String MOD_ID = "nomansland";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NoMansLand(IEventBus modBus, ModContainer container) {
        // GeckoLib 4.7 self-initialises via its own @Mod entrypoint - no explicit init call.
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);

        modBus.addListener(this::registerAttributes);
        modBus.addListener(this::registerSpawnPlacements);

        container.registerConfig(ModConfig.Type.COMMON, NMLConfig.SPEC);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.UNDEAD.get(), UndeadEntity.createAttributes().build());
        event.put(ModEntities.BANNER_BEARER.get(), BannerBearerEntity.createAttributes().build());
    }

    private void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        // Cheap vanilla ground check; moon-phase gating will be added per-mob in a later stage
        // inside checkSpawnRules overrides, not by scanning the world.
        event.register(ModEntities.BANNER_BEARER.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BannerBearerEntity::checkBannerBearerSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.UNDEAD.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                UndeadEntity::checkUndeadSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
