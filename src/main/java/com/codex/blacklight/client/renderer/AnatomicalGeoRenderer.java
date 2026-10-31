package com.codex.blacklight.client.renderer;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.GeoBone;

public abstract class AnatomicalGeoRenderer<T extends LivingEntity & GeoEntity> extends GeoEntityRenderer<T>{
    protected AnatomicalGeoRenderer(EntityRendererProvider.Context context,GeoModel<T> model){super(context,model);}
    @Override public void renderCubesOfBone(PoseStack stack,GeoBone bone,VertexConsumer out,int light,int overlay,float red,float green,float blue,float alpha){
        var actor=getAnimatable();var name=bone.getName().toLowerCase(java.util.Locale.ROOT);
        if(actor instanceof net.minecraft.world.entity.Mob mob&&com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(mob)
                &&(name.endsWith("forearm")||name.endsWith("hand")||name.endsWith("claw")))
            InfectedLimbTrails.capture(mob,name.hashCode(),stack.last(),new net.minecraft.world.phys.Vec3(0,-.18,0));
        if(out instanceof ConsumeAnatomy.Consumer consumer){
            int id=-1;for(GeoBone b=bone;b!=null&&id<0;b=b.getParent())id=ConsumeAnatomy.named(b.getName(),-1);
            consumer.anatomy(Math.max(0,id));
        }
        super.renderCubesOfBone(stack,bone,out,light,overlay,red,green,blue,alpha);
    }
}
