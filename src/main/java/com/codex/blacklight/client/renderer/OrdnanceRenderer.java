package com.codex.blacklight.client.renderer;

import com.codex.blacklight.firearms.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.phys.Vec3;

/** Projectile silhouette and fins follow actual motion; rockets and cannon shells are distinct. */
public final class OrdnanceRenderer<T extends ThrowableItemProjectile> extends EntityRenderer<T> {
    public OrdnanceRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.08F;}
    @Override public ResourceLocation getTextureLocation(T entity){return ConsumedMobVisuals.WHITE;}
    @Override public void render(T entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        boolean hellfire=entity instanceof VehicleOrdnanceProjectile p&&p.hellfire();
        boolean rapid=entity instanceof VehicleOrdnanceProjectile p&&p.rapidRocket();
        var velocity=hellfire||rapid?((VehicleOrdnanceProjectile)entity).flightHeading():entity.getDeltaMovement();if(velocity.lengthSqr()<1e-8)velocity=new Vec3(0,0,1);
        boolean vehicle=entity instanceof VehicleOrdnanceProjectile;
        String id=vehicle?((VehicleOrdnanceProjectile)entity).weaponId():"";
        boolean rocket=vehicle?hellfire||rapid||id.equals("vehicle_rocket"):entity instanceof OrdnanceProjectile p&&(p.definition().kind()==WeaponDefinition.Kind.ROCKET||p.definition().kind()==WeaponDefinition.Kind.MISSILE);
        boolean infection=entity instanceof OrdnanceProjectile p&&p.definition().kind()==WeaponDefinition.Kind.INFECTION;
        double length=hellfire?com.codex.blacklight.military.aircraft.HelicopterHellfireRack.MISSILE_LENGTH:rapid?com.codex.blacklight.military.aircraft.HelicopterRapidFlight.LENGTH:rocket?.75:vehicle?.48:.28,radius=hellfire?.055:rapid?.034602:rocket?.095:vehicle?.055:.095;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation((float)Math.atan2(velocity.x,velocity.z)));
        pose.mulPose(Axis.XP.rotation((float)-Math.atan2(velocity.y,velocity.horizontalDistance())));
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
        GunplayGeometry.rod(pose,out,new Vec3(0,0,-length*.5),new Vec3(0,0,length*.25),radius,radius,infection?0xff582132:0xff626e54,light);
        GunplayGeometry.rod(pose,out,new Vec3(0,0,length*.25),new Vec3(0,0,length*.5),radius,0,vehicle?0xffc6aa69:0xff333840,light);
        GunplayGeometry.rod(pose,out,new Vec3(0,0,-length*.4),new Vec3(0,0,-length*.3),radius*1.08,radius*1.08,0xffb6a270,light);
        GunplayGeometry.box(pose,out,new Vec3(0,0,-length*.5),radius*1.4,radius*1.4,.03,0xff303239,light);
        if(rocket){
            GunplayGeometry.box(pose,out,new Vec3(0,0,-length*.35),hellfire?.2177034:rapid?.069204:.32,.025,.20,0xff3d4246,light);
            GunplayGeometry.box(pose,out,new Vec3(0,0,-length*.35),.025,hellfire?.282635:rapid?.069204:.32,.20,0xff3d4246,light);
            if(!hellfire||((VehicleOrdnanceProjectile)entity).flightAge()>=10)GunplayGeometry.rod(pose,out,new Vec3(0,0,-length*.52),new Vec3(0,0,-length*.95),.055,0,0xffffd388,LightTexture.FULL_BRIGHT);
        }
        pose.popPose();super.render(entity,yaw,partial,pose,buffers,light);
    }
}
