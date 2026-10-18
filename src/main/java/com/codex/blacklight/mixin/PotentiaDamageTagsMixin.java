package com.codex.blacklight.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Potentia beta 0.12.2 ships obsolete thetitans damage IDs as required entries. */
@Mixin(TagLoader.class)
public abstract class PotentiaDamageTagsMixin<T> {
    @Shadow @Final private String directory;
    @Shadow @Final private Function<ResourceLocation,Optional<? extends T>> idToValue;

    @Inject(method="load",at=@At("RETURN"))
    private void blacklight$optionalLegacyDamageIds(ResourceManager resources,
            CallbackInfoReturnable<Map<ResourceLocation,List<TagLoader.EntryWithSource>>> callback) {
        if(!"tags/damage_type".equals(directory) || !ModList.get().isLoaded("potentia"))return;
        String pack=ModList.get().getModFileById("potentia").getFile().getFileName();
        callback.getReturnValue().values().forEach(entries->entries.replaceAll(item->{
            TagEntry entry=item.entry();
            // Only the offending mod's unresolved legacy element IDs, never another pack or valid registry entry.
            if(item.source().equals(pack) && entry.isRequired() && !entry.isTag()
                    && entry.getId().getNamespace().equals("thetitans") && idToValue.apply(entry.getId()).isEmpty())
                return new TagLoader.EntryWithSource(TagEntry.optionalElement(entry.getId()),item.source(),item.remove());
            return item;
        }));
    }
}
