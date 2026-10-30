package com.codex.blacklight.client.model;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.GeoModel;
import java.util.function.Function;
public final class BlacklightCreatureModel<T extends LivingEntity & GeoEntity> extends GeoModel<T> {
    private final Function<T,String> asset;
    public BlacklightCreatureModel(Function<T,String> asset){this.asset=asset;}
    private ResourceLocation id(String path){return new ResourceLocation("blacklight",path);}
    @Override public ResourceLocation getModelResource(T entity){return id("geo/"+asset.apply(entity)+".geo.json");}
    @Override public ResourceLocation getTextureResource(T entity){return id("textures/entity/infected_species.png");}
    @Override public ResourceLocation getAnimationResource(T entity){return id("animations/"+asset.apply(entity)+".animation.json");}
    @Override public void setCustomAnimations(T entity,long instance,software.bernie.geckolib.core.animation.AnimationState<T> state){
        super.setCustomAnimations(entity,instance,state);
        com.codex.blacklight.client.animation.NativeReceiverPose.resetRoot(this);
        if(com.codex.blacklight.client.animation.NativeReceiverPose.apply(this,entity,state.getPartialTick()))return;
        var visual=com.codex.blacklight.registry.ModAttachments.getData(entity, com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
        if(!visual.active()){
            var hit=com.codex.blacklight.registry.ModAttachments.getData(entity, com.codex.blacklight.registry.ModAttachments.HIT_REACTION);
            float w=hit.weight(entity.level().getGameTime(),state.getPartialTick());if(w<=0)return;
            var head=getAnimationProcessor().getBone("head");if(head!=null)head.setRotX(head.getRotX()-.25F*w);
            for(String side:new String[]{"right","left"}){
                var arm=getAnimationProcessor().getBone(side+"_arm");if(arm!=null)arm.setRotX(-.45F*w);
                var leg=getAnimationProcessor().getBone(side+"_leg");if(leg!=null)leg.setRotX((side.equals("right")?.45F:-.30F)*w);
            }
            return;
        }
        if(com.codex.blacklight.client.animation.NativeGeckoConsume.apply(this,entity,state.getPartialTick()))return;
        var reaction=com.codex.blacklight.client.animation.ConsumeVictimPose.reaction(entity,state.getPartialTick());
        for(String side:new String[]{"right","left"}){
            var arm=getAnimationProcessor().getBone(side+"_arm");if(arm!=null)arm.setRotX(-reaction.brace()*1.3F);
            var leg=getAnimationProcessor().getBone(side+"_leg");if(leg!=null)leg.setRotX(reaction.kick()*(side.equals("right")?.8F:-.8F));
        }
        var head=getAnimationProcessor().getBone("head");if(head!=null)head.setRotX(reaction.head());
    }
}
