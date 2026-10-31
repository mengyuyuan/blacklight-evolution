package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.InfectedTumor;
import com.codex.blacklight.infection.EvolvedForm;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class InfectedTumorRenderer extends EntityRenderer<InfectedTumor> {
    public InfectedTumorRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.25F;}
    @Override public ResourceLocation getTextureLocation(InfectedTumor e){return ConsumedMobVisuals.WHITE;}
    @Override public boolean shouldRender(InfectedTumor e,Frustum camera,double x,double y,double z){return e.shouldRender(x,y,z)&&camera.isVisible(e.getBoundingBox().inflate(e.erupted()?8:.6));}
    @Override public void render(InfectedTumor e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float age=e.burstAge(partial),clock=e.tickCount+partial;
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        float pulse=1+(float)Math.sin(clock*.55)*.06F;
        if(age<0){
            InfectionCombatVisuals.flesh(pose,out,light,Vec3.ZERO,.36,pulse);
            var axis=e.getDeltaMovement().normalize();
            for(int i=0;i<3;i++){var a=axis.scale(-.2-i*.14).add(0,Math.sin(clock*.3+i)*.03,0);ConsumedMobVisuals.tube(pose,out,light,a,a.add(axis.scale(-.22)),.06/(i+1),0xff8d2c3d);}
        }else{
            float extension=EvolvedForm.smooth(age/12)*(1-EvolvedForm.smooth((age-24)/12));
            InfectionCombatVisuals.flesh(pose,out,light,Vec3.ZERO,.42*(1-EvolvedForm.smooth((age-24)/12)),pulse);
            int index=0;for(var end:e.tendrils()){
                Vec3 before=Vec3.ZERO;
                for(int i=1;i<=14;i++){double t=i/14D;var p=end.scale(t*extension).add(0,Math.sin(Math.PI*t)*(.3+Math.sin(clock*.2+index+t*4)*.1)*extension,0);
                    ConsumedMobVisuals.tube(pose,out,light,before,p,(.18*(1-t)+.012)*extension,i%4==0?0xff8a2e3d:0xff33222b);
                    if(i==5||i==9){var side=end.normalize().cross(new Vec3(0,1,0)).scale((i==5?.22:-.16)*extension);ConsumedMobVisuals.tube(pose,out,light,p,p.add(side).add(0,.12*extension,0),.025*extension,0xff9a6660);}before=p;
                }index++;
            }
        }
        var glow=buffers.getBuffer(RenderType.entityTranslucentEmissive(ConsumedMobVisuals.WHITE));
        float alpha=age<0?.7F:1-EvolvedForm.smooth((age-12)/20);
        if(alpha>.01&&age<0)InfectionCombatVisuals.ring(pose,glow,Vec3.ZERO,new Vec3(0,1,0),.27,.012,((int)(alpha*110)<<24)|0xbd3646,12);
        super.render(e,yaw,partial,pose,buffers,light);
    }
}
