package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.BlacklightCreatureModel;
import com.codex.blacklight.entity.MilitaryUav;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Single source-referenced ducted rotor and a bone-selected, emissive recognition lamp. */
public final class UavRenderer extends AnatomicalGeoRenderer<MilitaryUav> {
    public UavRenderer(EntityRendererProvider.Context context){
        super(context,new BlacklightCreatureModel<>(MilitaryUav::asset));shadowRadius=.9F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
