package com.codex.blacklight.client.model;

import com.codex.blacklight.entity.BlacklightHunter;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class BlacklightHunterModel extends GeoModel<BlacklightHunter> {
    private static ResourceLocation resource(String path) { return new ResourceLocation("blacklight", path); }
    @Override public ResourceLocation getModelResource(BlacklightHunter entity) { return resource("geo/blacklight_hunter_authored.geo.json"); }
    @Override public ResourceLocation getTextureResource(BlacklightHunter entity) { return resource("textures/entity/hunter_authored.png"); }
    @Override public ResourceLocation getAnimationResource(BlacklightHunter entity) { return resource("animations/blacklight_hunter.animation.json"); }

    @Override public void setCustomAnimations(BlacklightHunter entity, long instanceId,
            software.bernie.geckolib.core.animation.AnimationState<BlacklightHunter> state) {
        super.setCustomAnimations(entity, instanceId, state);
        if(entity.committedAttack()&&!entity.isBeingConsumed()){
            var table=com.codex.blacklight.combat.CreaturePoseTable.HUNTER;
            float age=entity.attackAge(state.getPartialTick());var move=entity.attackMove();
            var pose=table.sample(move.clip,age);
            var rest=table.sample("idle",0);
            float weight=net.minecraft.util.Mth.clamp(Math.min(age/3,(move.ticks-age)/5),0,1);
            for(int i=0;i<table.bones.length;i++){
                var bone=getAnimationProcessor().getBone(table.bones[i]);if(bone==null)continue;
                // Blend from the sampled idle at the endpoints, not from a stale attack controller.
                var blended=com.codex.blacklight.combat.CreaturePoseTable.blend(rest[i],pose[i],weight);
                bone.updateRotation(blended[0],blended[1],blended[2]);
                bone.setPosX(blended[3]);bone.setPosY(blended[4]);bone.setPosZ(blended[5]);
            }
        }
        com.codex.blacklight.client.animation.NativeReceiverPose.resetRoot(this);
        var visual = com.codex.blacklight.registry.ModAttachments.getData(entity, com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
        float t = visual.active() ? visual.progress(entity.level().getGameTime(), state.getPartialTick()) : 0;
        var hit=com.codex.blacklight.registry.ModAttachments.getData(entity, com.codex.blacklight.registry.ModAttachments.HIT_REACTION);
        float hitWeight=hit.weight(entity.level().getGameTime(),state.getPartialTick());
        boolean sourcePose=com.codex.blacklight.client.animation.NativeReceiverPose.apply(this,entity,state.getPartialTick());
        if(!sourcePose&&!visual.active()&&hitWeight>0){
            applyReaction("head",-.3F*hitWeight,0,.08F*hitWeight);
            applyReaction("torso",-.15F*hitWeight,0,0);
            for(String side:new String[]{"right","left"}){
                float sign=side.equals("right")?1:-1;
                applyReaction(side+"_arm",-.5F*hitWeight,0,sign*.25F*hitWeight);
                applyReaction(side+"_forearm",-.8F*hitWeight,0,0);
                applyReaction(side+"_thigh",-.4F*hitWeight,0,sign*.12F*hitWeight);
                applyReaction(side+"_shin",.6F*hitWeight,0,0);
            }
        }
        if (!sourcePose&&visual.active() && !com.codex.blacklight.client.animation.NativeGeckoConsume.apply(this,entity,state.getPartialTick())) {
            var reaction = com.codex.blacklight.client.animation.ConsumeVictimPose.reaction(entity, state.getPartialTick());
            applyReaction("torso", reaction.bend()*.55F, reaction.twist(), 0);
            applyReaction("head", -reaction.head()*.65F, -reaction.twist(), reaction.twist()*.4F);
            for(String side:new String[]{"right","left"}) {
                float sign=side.equals("right")?1:-1;
                applyReaction(side+"_arm", -reaction.brace()*1.2F, sign*.12F, sign*.28F);
                applyReaction(side+"_forearm", -.4F-reaction.brace()*.6F, 0, 0);
                applyReaction(side+"_thigh", -reaction.kick()*.6F, 0, sign*.10F);
                applyReaction(side+"_shin", reaction.kick()*.95F, 0, 0);
            }
        }
        // Preserve the receiver's full skeletal action; old timed bone shrinkage
        // erased the forearms/head while the source was still reacting.
        var root = getAnimationProcessor().getBone("root");
        if (root != null) { root.setHidden(t >= .995F); root.setChildrenHidden(t >= .995F); }
        String[] names = {"right_forearm","left_forearm","right_shin","left_shin","head",
                "right_arm","left_arm","right_thigh","left_thigh","torso","hips","jaw"};
        for (int i=0;i<names.length;i++) {
            var bone = getAnimationProcessor().getBone(names[i]); if (bone == null) continue;
            bone.setHidden(false);bone.setChildrenHidden(false);
            bone.setScaleX(1);bone.setScaleY(1);bone.setScaleZ(1);
        }
        if(visual.stage()==com.codex.blacklight.combat.ConsumeVisual.HUNTER_CONSUME) {
            float seconds=com.codex.blacklight.combat.HunterConsume.seconds(visual,entity.level().getGameTime(),state.getPartialTick());
            float[] scale=com.codex.blacklight.combat.HunterConsume.jawScale(seconds);
            var jaw=getAnimationProcessor().getBone("jaw");
            if(jaw!=null){jaw.setScaleX(scale[0]);jaw.setScaleY(scale[1]);jaw.setScaleZ(scale[2]);}
        }
        if(entity.isConsumeRemains()) {
            var head=getAnimationProcessor().getBone("head");
            if(head!=null){head.setHidden(true);head.setChildrenHidden(true);}
        }
    }
    private void applyReaction(String name, float x, float y, float z) {
        var bone=getAnimationProcessor().getBone(name);if(bone==null)return;
        bone.setRotX(x);bone.setRotY(y);bone.setRotZ(z);
    }
}
