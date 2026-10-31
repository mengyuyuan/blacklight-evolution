package com.codex.blacklight.city;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** A persistent endpoint identity. Entity IDs cannot survive a world reload. */
public record HydraLink(ResourceKey<Level> dimension, BlockPos position, UUID endpoint) {
    public CompoundTag save() {
        var tag=new CompoundTag();tag.putString("Dimension",dimension.location().toString());
        tag.putLong("Position",position.asLong());tag.putUUID("Endpoint",endpoint);return tag;
    }
    public static HydraLink load(CompoundTag tag) {
        var id=ResourceLocation.tryParse(tag.getString("Dimension"));
        if(id==null||!tag.contains("Position")||!tag.hasUUID("Endpoint"))return null;
        return new HydraLink(ResourceKey.create(Registries.DIMENSION,id),BlockPos.of(tag.getLong("Position")),tag.getUUID("Endpoint"));
    }
}
