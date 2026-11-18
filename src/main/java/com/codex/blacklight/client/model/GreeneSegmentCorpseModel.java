package com.codex.blacklight.client.model;

import com.codex.blacklight.combat.GreeneMotion;
import com.codex.blacklight.entity.GreeneSegmentCorpse;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public final class GreeneSegmentCorpseModel extends GeoModel<GreeneSegmentCorpse> {
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("blacklight",path);}
    // A distinct baked model prevents corpse visibility edits from leaking into
    // the live Mother's shared Gecko cache. Its geometry is generated verbatim
    // from just the thirty segment leaves, with all native helpers retained.
    @Override public ResourceLocation getModelResource(GreeneSegmentCorpse entity){return id("geo/elizabeth_greene_segment_corpse.geo.json");}
    @Override public ResourceLocation getTextureResource(GreeneSegmentCorpse entity){return id("textures/entity/elizabeth_greene_mc.png");}
    @Override public ResourceLocation getAnimationResource(GreeneSegmentCorpse entity){return id("animations/elizabeth_greene.animation.json");}
    @Override public void setCustomAnimations(GreeneSegmentCorpse entity,long instance,AnimationState<GreeneSegmentCorpse> state){
        super.setCustomAnimations(entity,instance,state);
        var table=GreeneMotion.table();var pose=entity.pose(state.getPartialTick());String group="weak_"+entity.segment()+"_";
        for(int i=0;i<table.bones.length;i++){
            var bone=getAnimationProcessor().getBone(table.bones[i]);if(bone==null)continue;
            var p=pose[i];bone.updateRotation(p[0],p[1],p[2]);bone.setPosX(-p[3]);bone.setPosY(p[4]);bone.setPosZ(p[5]);
            bone.setScaleX(p.length>=9?p[6]:1);bone.setScaleY(p.length>=9?p[7]:1);bone.setScaleZ(p.length>=9?p[8]:1);
            bone.setHidden(table.bones[i].startsWith("weak_")&&!table.bones[i].startsWith(group));
        }
    }
}
