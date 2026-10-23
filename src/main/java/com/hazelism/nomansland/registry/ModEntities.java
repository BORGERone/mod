package com.hazelism.nomansland.registry;

import com.hazelism.nomansland.NoMansLand;
import com.hazelism.nomansland.entity.BannerBearerEntity;
import com.hazelism.nomansland.entity.UndeadEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, NoMansLand.MOD_ID);

    /** Collision box mirrors the Bedrock original: 0.6 x 1.8. */
    public static final DeferredHolder<EntityType<?>, EntityType<UndeadEntity>> UNDEAD = ENTITY_TYPES.register("undead",
            () -> EntityType.Builder.of(UndeadEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .build("undead"));

    /** Support mob of the horde; same humanoid footprint. Model is a placeholder for now. */
    public static final DeferredHolder<EntityType<?>, EntityType<BannerBearerEntity>> BANNER_BEARER = ENTITY_TYPES.register("banner_bearer",
            () -> EntityType.Builder.of(BannerBearerEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .build("banner_bearer"));

    private ModEntities() {}
}
