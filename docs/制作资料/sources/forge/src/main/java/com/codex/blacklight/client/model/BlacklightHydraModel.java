package com.codex.blacklight.client.model;
import com.codex.blacklight.entity.BlacklightHydra;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.core.animation.AnimationState;

public final class BlacklightHydraModel extends GeoModel<BlacklightHydra> {
    private static ResourceLocation id(String p){return new ResourceLocation("blacklight",p);}
    @Override public ResourceLocation getModelResource(BlacklightHydra e){return id("geo/blacklight_hydra.geo.json");}
    @Override public ResourceLocation getTextureResource(BlacklightHydra e){return id("textures/entity/hydra_authored.png");}
    @Override public ResourceLocation getAnimationResource(BlacklightHydra e){return id("animations/blacklight_hydra.animation.json");}
    @Override public void setCustomAnimations(BlacklightHydra e,long id,AnimationState<BlacklightHydra> s){
        super.setCustomAnimations(e,id,s);
        var motion=com.codex.blacklight.city.HydraMotion.INSTANCE;
        var pose=e.motionPose(s.getPartialTick());
        for(int i=0;i<motion.bones.length;i++){
            var bone=getAnimationProcessor().getBone(motion.bones[i]);if(bone==null)continue;
            var v=pose[i];bone.setRotX(v[0]);bone.setRotY(v[1]);bone.setRotZ(v[2]);
            bone.setPosX(v[3]*16);bone.setPosY(v[4]*16);bone.setPosZ(v[5]*16);
        }
    }
}
