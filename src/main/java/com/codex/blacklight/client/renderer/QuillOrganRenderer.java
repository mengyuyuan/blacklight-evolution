package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.QuillOrgan;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Lobes and tendrils share the wound's origin; the returning spicule uses the actual shoulder mesh. */
public final class QuillOrganRenderer extends EntityRenderer<QuillOrgan> {
    private static final Vec3 UP=new Vec3(0,1,0);
    private static final int SHELL=0xff271b23, FLESH=0xff702935, SCAR=0xffc84948;
    public QuillOrganRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(QuillOrgan q){return RoofPerformance.ATLAS;}
    private static Vec3 radial(double angle){return new Vec3(Math.cos(angle),0,Math.sin(angle));}
    private static void ring(PoseStack pose, VertexConsumer out,int light,Vec3 from,Vec3 to,double a,double b,int color){
        Vec3 axis=to.subtract(from).normalize();
        if(axis.lengthSqr()<.5)return;
        Vec3 u=axis.cross(Math.abs(axis.y)>.9?new Vec3(1,0,0):UP).normalize(),v=axis.cross(u);
        for(int j=0;j<6;j++){
            double x=j*Math.PI/3,y=(j+1)*Math.PI/3;
            Vec3 n=u.scale(Math.cos(x)).add(v.scale(Math.sin(x))),m=u.scale(Math.cos(y)).add(v.scale(Math.sin(y)));
            ConsumedMobVisuals.vertex(pose,out,light,from.add(n.scale(a)),n,color,0,0);
            ConsumedMobVisuals.vertex(pose,out,light,from.add(m.scale(a)),m,color,1,0);
            ConsumedMobVisuals.vertex(pose,out,light,to.add(m.scale(b)),m,color,1,1);
            ConsumedMobVisuals.vertex(pose,out,light,to.add(n.scale(b)),n,color,0,1);
        }
    }
    private static double pulse(double t,double center,double width){double x=(t-center)/width;return Math.exp(-x*x);}
    public static void contractHost(net.minecraft.world.entity.LivingEntity host,PoseStack pose,float partial){
        var q=QuillOrgan.extracting(host);if(q==null)return;
        if(!AdaptiveRagdoll.active(host)){
            var hit=ConsumeBodyImpact.quill(host,q,partial);
            ConsumeBodyImpact.poseAt(hit,pose,host.getPosition(partial));
        }
    }
    private static void penetration(QuillOrgan q,net.minecraft.world.entity.Mob host,PoseStack pose,
                                    VertexConsumer mesh,int light,float partial,double age,Vec3 anchor){
        Vec3 wound=anchor.subtract(host.getPosition(partial));
        var fan=QuillTendrils.at(q,host,wound);
        double tension=ease((age-QuillOrgan.PIERCE_TICK-.4)/2.4);
        double retract=ease((age-8.2)/3.2);
        pose.pushPose();
        pose.translate(-fan.wound.x,-fan.wound.y,-fan.wound.z);
        for(int i=0;i<fan.strands.length;i++){
            var strand=fan.strands[i];
            double reach=QuillTendrils.emergence(age,i);
            if(reach<=.01)continue;
            int segments=18;
            var path=new Vec3[segments+1];
            var widths=new double[path.length];
            for(int k=0;k<=segments;k++){
                double along=k/(double)segments,u=reach*along;
                path[k]=strand.point(u,tension);
                double flow=pulse(age,QuillOrgan.FIRST_DRAW_TICK+.9-1.6*u,.66)
                        +pulse(age,QuillOrgan.SECOND_DRAW_TICK+.9-1.6*u,.66);
                widths[k]=fan.girth*(i%3==0?1.10:.77)*(1-.42*retract)*Math.pow(1-along,.55)*(1+.48*flow);
            }
            ConsumeTendrils.sweep(pose,mesh,light,path,widths,UP);
        }
        pose.popPose();
    }
    private void roots(QuillOrgan q,PoseStack pose,MultiBufferSource buffers,int light,float partial,Vec3 anchor,QuillSkinContact.Surface skin){
        double t=q.age()+partial;
        boolean extract=q.phase()==QuillOrgan.EXTRACT;
        double filling=extract?ease((t-QuillOrgan.PIERCE_TICK)/5)*(1-.68*ease((t-10)/4)):0;
        double plunge=extract?ease((t-QuillOrgan.PIERCE_TICK)/1.2):0;
        double hostScale=1;
        if(q.level().getEntity(q.hostId()) instanceof net.minecraft.world.entity.Mob host){
            hostScale=Mth.clamp(Math.sqrt(host.getBbWidth()*host.getBbHeight()),.50,1.65);
            if(extract && t>0 && t<13){
                var mesh=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
                penetration(q,host,pose,mesh,light,partial,t,anchor);
            }
        }
        if(skin!=null)pose.mulPose(skin.rotation());
        else{
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(q.getYRot()));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90-q.getXRot()));
        }
        QuillLesion.draw(q,skin,hostScale,partial,pose,buffers,light,filling,plunge);
    }
    private static double ease(double f){
        f=Mth.clamp(f,0,1);
        return f*f*(3-2*f);
    }
    private static void receiveWounded(QuillOrgan q,net.minecraft.client.player.AbstractClientPlayer owner,
                                       float partial,PoseStack pose,MultiBufferSource buffers,int light){
        double age=q.age()+partial;
        boolean right=q.arrivesRight();
        var mesh=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        Vec3 origin=q.getPosition(partial);
        Vec3 mouth=QuillReception.backPoint(owner,partial,right,0).subtract(origin);
        Vec3 spine=QuillReception.backPoint(owner,partial,right,1).subtract(origin);
        Vec3 outward=mouth.subtract(spine).normalize();
        Vec3 back=new Vec3(0,0,-1).yRot((float)Math.toRadians(-Mth.rotLerp(partial,owner.yBodyRotO,owner.yBodyRot)));
        outward=outward.add(back.scale(.82)).add(0,.15,0).normalize();
        double soften=ease((age-2.8)/5.6);
        double solid=1-soften;
        Vec3 touch=Vec3.ZERO.lerp(mouth,ease(age/3));
        Vec3 tail=touch.add(outward.scale(.82*solid+.18));
        Vec3 cross=outward.cross(UP).normalize();
        if(age>2 && age<10){
            for(int strand=-1;strand<=1;strand++){
                double width=(strand==0?.064:.041)*Math.sin(Math.PI*ease((age-2)/8));
                Vec3 curl=cross.scale(strand*(.12+.09*soften));
                Vec3 bend=tail.lerp(mouth,.56).add(curl).add(0,.12*(1-soften),0);
                ring(pose,mesh,light,tail.add(curl.scale(.22)),bend,width*.36,width,SHELL);
                ring(pose,mesh,light,bend,mouth,width,width*.40,strand==0?FLESH:SCAR);
            }
        }
        double front=ease((age-2.4)/6.0);
        double clear=ease((age-7.6)/3.4);
        if(front>clear+.015){
            int segments=7;
            for(int branch=-1;branch<=1;branch++){
                Vec3 from=null;
                double previous=0;
                for(int i=0;i<=segments;i++){
                    double u=Mth.lerp(i/(double)segments,clear,front);
                    double bow=Math.sin(u*Math.PI);
                    Vec3 point=QuillReception.backPoint(owner,partial,right,u).subtract(origin)
                            .add(0,branch*bow*.092,0).add(back.scale(.015+bow*(branch==0?.035:.018)));
                    double swell=pulse(age-5.3*u,3.7,.95)+.78*pulse(age-4.5*u,6.6,1.1);
                    double width=(branch==0?.041:.022)*(1-clear)*(.58+.95*swell)*(1-.30*u);
                    if(from!=null){
                        ring(pose,mesh,light,from,point,previous,width,branch==0?FLESH:SHELL);
                        if(swell>.22)ring(pose,mesh,0x00f000f0,from.add(back.scale(previous*.83)),point.add(back.scale(width*.83)),previous*.43,width*.43,SCAR);
                    }
                    previous=width;from=point;
                }
            }
        }
        if(solid>.018){
            var dir=outward.reverse();
            pose.pushPose();
            Vec3 center=touch.add(outward.scale(.44*solid));
            pose.translate(center.x,center.y,center.z);
            pose.mulPose(new Quaternionf().rotationTo(new org.joml.Vector3f(0,1,-.2F).normalize(),dir.toVector3f()));
            float width=(float)(.40+.60*Math.sqrt(solid));
            pose.scale(width,(float)solid,width);
            RoofPerformance.detachedQuill(q.socket(),pose,buffers,light);
            pose.popPose();
        }
    }
    // Match the origin LevelRenderer supplies to EntityRenderDispatcher, including position corrections.
    private static Vec3 renderPosition(net.minecraft.world.entity.Entity entity,float partial){
        return new Vec3(Mth.lerp(partial,entity.xOld,entity.getX()),
                Mth.lerp(partial,entity.yOld,entity.getY()),Mth.lerp(partial,entity.zOld,entity.getZ()));
    }
    @Override public boolean shouldRender(QuillOrgan q,net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z){
        if(q.phase()==QuillOrgan.ORBIT && q.level().getEntity(q.actor()) instanceof net.minecraft.world.entity.LivingEntity owner){
            var bounds=owner.getBoundingBox().expandTowards(owner.xOld-owner.getX(),owner.yOld-owner.getY(),owner.zOld-owner.getZ());
            double radius=com.codex.blacklight.config.GameplayBalanceConfig.QUILL_ORBIT_RADIUS.get();
            return q.firstSlot()>=0 && owner.shouldRender(x,y,z) && frustum.isVisible(bounds.inflate(radius+1));
        }
        return super.shouldRender(q,frustum,x,y,z);
    }
    private void orbit(QuillOrgan q, net.minecraft.world.entity.LivingEntity owner, float partial,
                       PoseStack pose, MultiBufferSource buffers, int light){
        var center=renderPosition(owner,partial).add(0,owner.getBbHeight()*.80,0);
        double radius=com.codex.blacklight.config.GameplayBalanceConfig.QUILL_ORBIT_RADIUS.get();
        double time=q.level().getGameTime()+partial;
        int slot=q.firstSlot();
        if(slot<0)return;
        var offset=com.codex.blacklight.combat.QuillFormation.offset(slot,q.formation(),time,radius);
        var ahead=com.codex.blacklight.combat.QuillFormation.offset(slot,q.formation(),time+.25,radius);
        var goal=center.add(offset);
        var wall=q.level().clip(new net.minecraft.world.level.ClipContext(center,goal,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,q));
        if(wall.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)goal=wall.getLocation().subtract(offset.normalize().scale(.18));
        var delta=goal.subtract(renderPosition(q,partial));
        pose.pushPose();
        pose.translate(delta.x,delta.y,delta.z);
        pose.mulPose(new Quaternionf().rotationTo(new org.joml.Vector3f(0,1,-.2F).normalize(),ahead.subtract(offset).normalize().toVector3f()));
        RoofPerformance.detachedQuill(q.socket(),pose,buffers,light);
        pose.popPose();
    }
    @Override public void render(QuillOrgan q,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        if(q.phase()==QuillOrgan.ORBIT && q.level().getEntity(q.actor()) instanceof net.minecraft.world.entity.LivingEntity owner){
            orbit(q,owner,partial,pose,buffers,light);
            return;
        }
        var mesh=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        pose.pushPose();
        if(q.phase()<=QuillOrgan.EXTRACT){
            Vec3 anchor=q.getPosition(partial);
            QuillSkinContact.Surface skin=null;
            if(q.level().getEntity(q.hostId()) instanceof net.minecraft.world.entity.LivingEntity host){
                if(host instanceof com.codex.blacklight.entity.BlacklightSupremeHunter hunter &&
                        entityRenderDispatcher.getRenderer(hunter) instanceof SupremeHunterRenderer renderer &&
                        !AdaptiveRagdoll.active(host)){
                    skin=renderer.wound(hunter,q,partial);
                }else if(!AdaptiveRagdoll.active(host)){
                    skin=QuillSkinContact.lookup(host,q);
                }
                anchor=skin!=null?skin.point():AdaptiveRagdoll.quillAnchor(host,q,partial);
                var shift=anchor.subtract(q.getPosition(partial));
                pose.translate(shift.x,shift.y,shift.z);
            }
            pose.pushPose();
            roots(q,pose,buffers,light,partial,anchor,skin);
            pose.popPose();
            if(q.phase()==QuillOrgan.EXTRACT && q.age()+partial>8 && q.level().getEntity(q.actor()) instanceof net.minecraft.world.entity.LivingEntity owner){
                float grow=(float)ease((q.age()+partial-8)/6);
                Vec3 aim=owner.getPosition(partial).add(0,owner.getBbHeight()*.80,0).subtract(anchor).normalize();
                if(aim.lengthSqr()>.1){
                    pose.mulPose(new Quaternionf().rotationTo(new org.joml.Vector3f(0,1,-.2F).normalize(),aim.toVector3f()));
                    pose.translate(0,grow*.18,0);
                    pose.scale(.46F+.54F*grow,grow,.46F+.54F*grow);
                    RoofPerformance.detachedQuill(q.socket(),pose,buffers,light);
                }
            }
        }else if(q.phase()==QuillOrgan.LIQUID && q.level().getEntity(q.actor()) instanceof net.minecraft.client.player.AbstractClientPlayer player){
            receiveWounded(q,player,partial,pose,buffers,light);
        }else{
            Vec3 travel=q.heading(partial);
            if(travel.lengthSqr()<.000001)travel=radial(q.offset());
            var dir=travel.normalize();double age=q.age()+partial;
            double length=q.phase()==QuillOrgan.STRIKE?.68:.42;
            double width=.045;
            Vec3 mid=dir.scale(-length*.43).add(0,.035,0);
            if(q.phase()!=QuillOrgan.ORBIT){
                ring(pose,mesh,light,dir.scale(.04),mid,width*.55,width,FLESH);
                ring(pose,mesh,light,mid,dir.scale(-length),width,.001,SHELL);
            }
            if(q.phase()==QuillOrgan.RETURN){
                Vec3 across=dir.cross(Math.abs(dir.y)>.9?new Vec3(1,0,0):UP).normalize();
                for(int strand=-1;strand<=1;strand+=2){
                    double sweep=.09*Math.sin((age+q.offset())*.66+strand);
                    Vec3 start=dir.scale(-.09).add(across.scale(strand*.034));
                    Vec3 bend=dir.scale(-length*.72).add(across.scale(sweep*strand)).add(0,.06,0);
                    Vec3 tip=dir.scale(-length*(strand>0?1.55:1.18)).add(across.scale(-sweep*.7));
                    ring(pose,mesh,light,start,bend,.022,.014,FLESH);
                    ring(pose,mesh,light,bend,tip,.014,.001,SCAR);
                }
            }
            var rotation=new Quaternionf().rotationTo(new org.joml.Vector3f(0,1,-.2F).normalize(),dir.toVector3f());
            pose.mulPose(rotation);
            RoofPerformance.detachedQuill(q.socket(),pose,buffers,light);
        }
        pose.popPose();
    }
}
