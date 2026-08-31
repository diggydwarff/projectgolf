package dev.projectgolf.registry;

import dev.projectgolf.ProjectGolf;
import dev.projectgolf.entity.GolfBallEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GolfEntities {
    private GolfEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ProjectGolf.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<GolfBallEntity>> GOLF_BALL =
            ENTITIES.register("golf_ball", () -> EntityType.Builder
                    .<GolfBallEntity>of(GolfBallEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .build(ProjectGolf.MOD_ID + ":golf_ball"));
}
