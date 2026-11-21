package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.ConsumeReceiverScene;
import com.codex.blacklight.combat.ConsumeSystem;
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
    // Signed revolutions are measured between the two shoulder/hip leads.
    // The main cable winds around both bodies; the thinner crossing cables grip the receiver.
    private record Cord(double side, double rootHeight, double upper, double lower,
                        double radius, double delay, double phase, double turns, boolean bothBodies) {}
    private static final Cord[] CORDS = {
            new Cord( 1, .96,  .94, -.96, .050, 0,    1.38, -3.05, true),
            new Cord(-1, .86,  .79, -.86, .036, .07, -1.26,  1.84, false),
            new Cord(-1, .38, -.59,  .76, .025, .14, -1.82, -1.36, true),
            new Cord( 1, .28, -.28, -.80, .031, .11,  1.56,  -.49, false),
            new Cord(-1, .76,  .62, -.05, .019, .21, -1.71,   .63, false)
    };
    private SmallMobConsumeSling(){}

    static boolean applies(LivingEntity victim,ConsumeVisual scene){
        // The reference chest punch shows an exterior wrap. Other finishers dispatch
        // their own FIG bridges and retain their own receiver silhouettes.
        return ChestConsumeTendrils.matches(scene)&&ConsumeSystem.smallDirectReceiver(victim);
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
        CoilFrame frame=new CoilFrame(waist,upper,axis,outward,across,actorRadius,
                center,mobAxis,around,rim,radius,half,drag);
        for(int i=0;i<CORDS.length;i++){
            Cord c=CORDS[i];
            double growth=smooth((time-begin-c.delay)/.48);
            if(growth<=0)continue;
            Vec3[] path=coilPath(i,frame,release);
            // Keep the courses separated even on short receivers and juvenile mobs.
            double pitch=half*Math.abs(c.upper-c.lower)/Math.abs(c.turns);
            double width=Math.min(c.radius*scale,Math.max(.012,pitch*.32));
            sweep(stack,out,light,camera,path,width,growth,release,i,time);
        }
    }

    record CoilFrame(Vec3 waist, Vec3 chest, Vec3 actorAxis, Vec3 outward, Vec3 across,
                     double actorRadius, Vec3 center, Vec3 mobAxis, Vec3 around, Vec3 rim,
                     double mobRadius, double halfHeight, Vec3 drag) {}

    static Vec3[] coilPath(int index, CoilFrame f, double release){
        Cord c=CORDS[index];
        int segments=(int)Math.ceil(16*Math.abs(c.turns));
        Vec3[] path=new Vec3[segments+5];
        Vec3 side=f.across.scale(c.side),root=f.waist.lerp(f.chest,c.rootHeight);
        path[0]=root.add(side.scale(f.actorRadius*.83)).subtract(f.outward.scale(f.actorRadius*.25));
        path[1]=root.add(side.scale(f.actorRadius*1.23)).add(f.actorAxis.scale(c.rootHeight>.65?.12:.02))
                .add(f.outward.scale(f.actorRadius*.27));
        for(int j=0;j<=segments;j++){
            double u=j/(double)segments;
            double angle=c.phase+2*Math.PI*c.turns*u+.13*Math.sin(2*Math.PI*u);
            double cosine=Math.cos(angle),sine=Math.sin(angle);
            double level=c.upper+(c.lower-c.upper)*u;
            // A shallow oblique path crosses the ribs; the tips still sit below the face.
            level+=.095*Math.sin(angle+.5*index)*Math.sin(Math.PI*u);
            double clearance=(1-release)*(.031+.012*Math.cos(2*angle+.6*index));
            double receiverRadius=f.mobRadius*1.11+clearance;
            Vec3 receiverEdge=f.center.add(f.mobAxis.scale(f.halfHeight*level))
                    .add(f.around.scale(cosine*receiverRadius)).add(f.rim.scale(sine*receiverRadius));
            if(c.bothBodies){
                // On the back half, the same strand follows the player's posed back and flanks.
                // The outer half follows the prey even when its torso tilts during the carry.
                double height=.50+level*.73;
                double actorRadius=f.actorRadius*1.14+clearance;
                Vec3 actorEdge=f.waist.lerp(f.chest,height)
                        .add(f.outward.scale(cosine*actorRadius)).add(f.across.scale(sine*actorRadius));
                receiverEdge=actorEdge.lerp(receiverEdge,smooth((cosine+.48)/.96));
            }
            path[j+2]=receiverEdge.subtract(f.drag.scale(.18*Math.sin(Math.PI*u)*(1-release)));
        }
        int last=path.length-1;
        Vec3 returnRoot=f.waist.lerp(f.chest,c.lower>0?.79:.16).subtract(side.scale(f.actorRadius*.76));
        path[last-1]=returnRoot.subtract(side.scale(f.actorRadius*.46)).add(f.outward.scale(f.actorRadius*.37));
        path[last]=returnRoot;
        return path;
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
        final int steps=Math.max(30,(int)Math.ceil((path.length-1)*1.6*growth*(1-release*.86))),sides=7;
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
            double v=t*(path.length-1)*.31+id*.38-time*.20;
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
