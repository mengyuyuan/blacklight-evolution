package com.codex.blacklight.client.model;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.GeoModel;
import java.util.function.Function;
public final class BlacklightCreatureModel<T extends LivingEntity & GeoEntity> extends GeoModel<T> {
    private final Function<T,String> asset;
    public BlacklightCreatureModel(Function<T,String> asset){this.asset=asset;}
    private ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("blacklight",path);}
    @Override public ResourceLocation getModelResource(T entity){return id("geo/"+asset.apply(entity)+"_mc.geo.json");}
    @Override public ResourceLocation getTextureResource(T entity){return id("textures/entity/"+asset.apply(entity)+"_mc.png");}
    @Override public ResourceLocation getAnimationResource(T entity){return id("animations/"+asset.apply(entity)+".animation.json");}
    /** Heartbeat swell of the bulb, and the crown flaps peeling open while a brood hatches. */
    private void hive(com.codex.blacklight.entity.BlacklightHive hive,float partial){
        float t=hive.tickCount+partial;float beat=(float)Math.pow(Math.max(0,Math.sin(t*.16F)),6)+.5F*(float)Math.pow(Math.max(0,Math.sin(t*.16F-.9F)),8);
        float age=hive.hatchAge(partial);float open=age<0||age>50?0:age<8?age/8F:age>20?(50-age)/30F:1;
        var torso=getAnimationProcessor().getBone("torso");
        if(torso!=null){float s=1+.05F*beat;torso.setScaleX(s);torso.setScaleZ(s);torso.setScaleY(1+.03F*beat);}
        for(int i=0;i<6;i++){var petal=getAnimationProcessor().getBone("petal"+i);if(petal==null)continue;
            double a=i*Math.PI/3;float tilt=.12F*beat+1.1F*open;
            // Tip each flap outward around the horizontal axis tangent to the rim.
            petal.setRotX((float)(Math.sin(a)*tilt));petal.setRotZ((float)(-Math.cos(a)*tilt));}
    }
    @Override public void setCustomAnimations(T entity,long instance,software.bernie.geckolib.animation.AnimationState<T> state){
        super.setCustomAnimations(entity,instance,state);
        com.codex.blacklight.client.animation.NativeReceiverPose.resetRoot(this);
        if(entity instanceof com.codex.blacklight.entity.BlacklightHive hive){hive(hive,state.getPartialTick());return;}
        if(com.codex.blacklight.client.animation.NativeReceiverPose.apply(this,entity,state.getPartialTick()))return;
        var visual=entity.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
        if(!visual.active()){
            var hit=entity.getData(com.codex.blacklight.registry.ModAttachments.HIT_REACTION);
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
