package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.ConsumeReceiverScene;
import com.codex.blacklight.combat.ConsumeSystem;
import com.codex.blacklight.combat.ConsumeVariant;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.combat.MobileConsumeCarry;
import com.codex.blacklight.power.PowerForm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Exterior load-bearing strands between the giver's shoulders/waist and the visible
 * small receiver. Each bundle samples that receiver's own clock, pose and carry slot. */
final class SmallMobConsumeSling {
    private static final Vec3 UP=new Vec3(0,1,0);
    // Roots alternate between shoulders and the opposite flank. The outer turns bind
    // the chest and hips at different heights and leave the head, extremities and gaps visible.
    private record Cord(double side,double rootHeight,double upper,double lower,double radius,double delay,double turn){}
    private static final Cord[] CORDS={
            new Cord( 1,.95,.53,-.32,.051,0,.21),new Cord(-1,.85,.39,-.46,.044,.075,-.22),
            new Cord( 1,.25,.03,-.57,.039,.14,.50),new Cord(-1,.48,.60,-.08,.038,.17,-.36),
            new Cord( 1,.74,.43,-.23,.019,.09,.58),new Cord(-1,.91,.31,-.54,.016,.21,-.57),
            new Cord(-1,.20,.06,-.44,.023,.26,.45)};
    private SmallMobConsumeSling(){}

    static boolean applies(LivingEntity victim,ConsumeVisual scene){
        return (scene.stage()==ConsumeVisual.CONSUME||scene.stage()==ConsumeVisual.MOBILE_CONSUME)
                &&!ConsumeVariant.stealth(PowerForm.values()[scene.form()],scene.variant())
                &&ConsumeSystem.smallDirectReceiver(victim);
    }
    private static double smooth(double x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}

    static void draw(PoseStack stack,VertexConsumer out,int light,Vec3 camera,LivingEntity victim,LivingEntity owner,
                     ConsumeVisual scene,float partial){
        if(victim.isInvisible()||owner.isInvisible()||!victim.isAlive()||!owner.isAlive())return;
        PowerForm form=PowerForm.values()[scene.form()];
        var profile=ConsumeReceiverScene.profile(form,false,scene.variant());
        double cue=ConsumeReceiverScene.absorptionSeconds(form,scene.variant());
        double end=profile==null?scene.duration()/20.:profile.finishSeconds();
        double begin=profile==null?Math.max(0,cue-.42):Math.min(cue,Math.max(profile.deformSeconds(),cue-1.18));
        double time=scene.elapsed(victim.level().getGameTime(),partial)/20.;
        if(time<=begin||time>=end)return;
        double release=smooth((time-(end-.30))/.30);
        Vec3[] actor=ChestConsumeTendrils.actorPoints(owner,partial), mob=ChestConsumeTendrils.receiverPoints(victim,partial);
        Vec3 upper=actor[0],waist=actor[2],axis=upper.subtract(waist).normalize();
        if(axis.lengthSqr()<.5)axis=UP;
        Vec3 mobAxis=mob[1].subtract(mob[0]).normalize();
        if(mobAxis.lengthSqr()<.5)mobAxis=UP;
        Vec3 center=mob[0].lerp(mob[1],.44);
        Vec3 carry=MobileConsumeCarry.center(victim,owner,scene,partial);
        // The compressed last skin fragment can become too small to supply torso joints.
        // The owner's moving slot remains the common destination for skin and cables.
        if(carry!=null&&AdaptiveRagdoll.chestConsumeJoints(victim,partial)==null)center=carry;
        if(center.distanceToSqr(waist)>16)return;
        Vec3 facing=Vec3.directionFromRotation(0,Mth.rotLerp(partial,owner.yBodyRotO,owner.yBodyRot));
        Vec3 separation=center.subtract(waist.lerp(upper,.5));
        Vec3 lateral=separation.subtract(axis.scale(separation.dot(axis)));
        Vec3 outward=lateral.add(facing.scale(.09)).normalize();
        if(outward.lengthSqr()<.5)outward=facing;
        Vec3 across=axis.cross(outward).normalize();
        if(across.lengthSqr()<.5)across=new Vec3(facing.z,0,-facing.x);
        Vec3 around=outward.subtract(mobAxis.scale(outward.dot(mobAxis)));
        if(around.lengthSqr()<.04)around=across.cross(mobAxis);
        around=around.normalize();
        Vec3 rim=mobAxis.cross(around).normalize();
        if(rim.dot(across)<0)rim=rim.scale(-1);
        float flow=ConsumeFracture.phase(victim,partial).returnFlow();
        double fit=carry==null?1-.84*flow:1-.97*MobileConsumeCarry.contraction(flow);
        if(time>=cue){
            float skin=AdaptiveRagdoll.carrySkinFraction(victim,partial);
            if(skin>=0){
                if(skin<.06F)return;
                fit=Math.min(fit,skin);
                release=Math.max(release,smooth((.56-skin)/.50));
            }
        }
        double radius=Math.max(.11,Math.min(.56,victim.getBbWidth()*.54))*fit;
        double half=Math.max(.12,Math.min(.85,.31*victim.getBbHeight()))*fit;
        double actorRadius=Math.max(.20,Math.min(.62,owner.getBbWidth()*.52));
        double scale=Math.min(1.28,Math.max(.68,victim.getBbWidth()/.61));
        Vec3 drag=owner.getPosition(1).subtract(owner.getPosition(0));
        if(drag.lengthSqr()>.16)drag=drag.normalize().scale(.40);
        for(int i=0;i<CORDS.length;i++){
            Cord c=CORDS[i];double growth=Math.min(1,Math.max(0,(time-begin-c.delay)/.48));
            if(growth<=0)continue;
            Vec3 side=across.scale(c.side),root=waist.lerp(upper,c.rootHeight);
            Vec3 hold=root.add(side.scale(actorRadius*.83)).add(outward.scale(-actorRadius*.25));
            Vec3 crest=root.add(side.scale(actorRadius*(1.18+.13*Math.abs(c.turn))))
                    .add(axis.scale(c.rootHeight>.65?.12:.02)).add(outward.scale(actorRadius*.36));
            Vec3 returnRoot=waist.lerp(upper,c.rootHeight>.6?.22:.7).subtract(side.scale(actorRadius*.74));
            Vec3 returnCrest=returnRoot.subtract(side.scale(actorRadius*.53)).add(outward.scale(actorRadius*.53));
            Vec3[] path=new Vec3[9];
            path[0]=hold;path[1]=crest;
            for(int j=0;j<5;j++){
                double u=j*.25;
                double angle=c.side*(1.37-2.85*u)+c.turn;
                double r=radius*(1.08+.16*Math.sin(Math.PI*u))+.04*(1-release);
                path[j+2]=center.add(mobAxis.scale(half*(c.upper+(c.lower-c.upper)*u)))
                        .add(around.scale(r*Math.cos(angle))).add(rim.scale(r*Math.sin(angle)))
                        .subtract(drag.scale(.28*Math.sin(Math.PI*u)*(1-release)));
            }
            path[7]=returnCrest;path[8]=returnRoot;
            // The last fibers slide back along their own route; no cable remains attached
            // to the player's next target when this receiver finishes.
            sweep(stack,out,light,camera,path,c.radius*scale,growth,release,i,time);
        }
    }
    private static Vec3 sample(Vec3[] p,double t){
        double x=Math.min(1,Math.max(0,t))*(p.length-1);int i=Math.min(p.length-2,(int)x);
        double u=x-i,v=u*u,w=v*u;
        Vec3 a=p[Math.max(0,i-1)],b=p[i],c=p[i+1],d=p[Math.min(p.length-1,i+2)];
        return a.scale((2*v-u-w)*.5).add(b.scale(1+(3*w-5*v)*.5))
                .add(c.scale((u+4*v-3*w)*.5)).add(d.scale((w-v)*.5));
    }
    private static void sweep(PoseStack stack,VertexConsumer out,int light,Vec3 camera,Vec3[] path,
                              double width,double growth,double release,int id,double time){
        final int steps=30,sides=7;
        Vec3 previous=null,normal=UP;
        Vec3[] previousRing=new Vec3[sides],previousNormals=new Vec3[sides];
        Vec3[] ring=new Vec3[sides],normals=new Vec3[sides];
        double oldV=0;
        for(int j=0;j<=steps;j++){
            double u=j/(double)steps,t=u*growth*(1-release*.86);
            Vec3 pos=sample(path,t).subtract(camera),next=sample(path,Math.min(1,t+.003)).subtract(camera);
            Vec3 tangent=next.subtract(pos).normalize();
            if(tangent.lengthSqr()<.5)tangent=previous==null?UP:pos.subtract(previous).normalize();
            if(tangent.lengthSqr()<.5)return;
            normal=normal.subtract(tangent.scale(normal.dot(tangent)));
            if(normal.lengthSqr()<.002)normal=tangent.cross(new Vec3(1,0,0));
            if(normal.lengthSqr()<.002)normal=tangent.cross(new Vec3(0,0,1));
            normal=normal.normalize();
            Vec3 binormal=tangent.cross(normal).normalize();
            double r=width*(1-.86*release)*(.12+.88*Math.pow(Math.max(0,Math.sin(Math.PI*u)),.50))
                    *Math.min(1,growth*4)*(1+.09*Math.sin(13*t-time*5+id));
            double v=t*2.8+id*.38-time*.20;
            for(int s=0;s<sides;s++){
                double theta=2*Math.PI*s/sides+1.6*t+.31*id;
                Vec3 n=normal.scale(Math.cos(theta)).add(binormal.scale(Math.sin(theta)));
                normals[s]=n;ring[s]=pos.add(n.scale(r*(1+.09*Math.cos(3*theta+6*t))));
            }
            if(previous!=null)for(int s=0;s<sides;s++){
                int k=(s+1)%sides;float a=s/(float)sides,b=(s+1)/(float)sides;
                vertex(stack,out,light,previousRing[s],previousNormals[s],a,(float)oldV);
                vertex(stack,out,light,previousRing[k],previousNormals[k],b,(float)oldV);
                vertex(stack,out,light,ring[k],normals[k],b,(float)v);
                vertex(stack,out,light,ring[s],normals[s],a,(float)v);
            }
            var swap=previousRing;previousRing=ring;ring=swap;
            swap=previousNormals;previousNormals=normals;normals=swap;
            previous=pos;oldV=v;
        }
    }
    private static void vertex(PoseStack stack,VertexConsumer out,int light,Vec3 p,Vec3 n,float u,float v){
        out.vertex(stack.last().pose(),(float)p.x,(float)p.y,(float)p.z).color(-1).uv(u,v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(stack.last().normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
    }
}
