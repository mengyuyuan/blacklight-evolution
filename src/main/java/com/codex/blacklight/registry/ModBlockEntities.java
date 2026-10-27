package com.codex.blacklight.registry;

import com.codex.blacklight.world.BuildingHeartBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One small render anchor per building core; the thousands of tissue cells remain ordinary blocks. */
public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"blacklight");
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<BuildingHeartBlockEntity>> BUILDING_HEART =
            TYPES.register("building_heart",()->BlockEntityType.Builder.of(BuildingHeartBlockEntity::new,ModBlocks.BUILDING_CORE.get()).build(null));
    private ModBlockEntities() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }
}
