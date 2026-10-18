package com.codex.blacklight.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** TheTitansNeo 1.0.8 still lists two unregistered pig-zombie types in its undead tag. */
@Mixin(TagLoader.class)
public abstract class TheTitansNeoTagsMixin<T> {
    @Shadow @Final private String directory;
    @Shadow @Final private Function<ResourceLocation,Optional<? extends T>> idToValue;

    @Inject(method="load",at=@At("RETURN"))
    private void blacklight$optionalUnregisteredTitans(ResourceManager resources,
            CallbackInfoReturnable<Map<ResourceLocation,List<TagLoader.EntryWithSource>>> callback) {
        if(!"tags/entity_type".equals(directory) || !ModList.get().isLoaded("thetitansneo"))return;
        callback.getReturnValue().values().forEach(entries->entries.replaceAll(item->{
            TagEntry entry=item.entry();
            if(item.source().equals("mod/thetitansneo") && entry.isRequired() && !entry.isTag()
                    && entry.getId().getNamespace().equals("thetitansneo")
                    && Set.of("pig_zombie_titan","pig_zombie_titan_minion").contains(entry.getId().getPath())
                    && idToValue.apply(entry.getId()).isEmpty())
                return new TagLoader.EntryWithSource(TagEntry.optionalElement(entry.getId()),item.source(),item.remove());
            return item;
        }));
    }
}
