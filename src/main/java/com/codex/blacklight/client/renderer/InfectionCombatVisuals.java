package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Clocked local geometry; has no damage or world mutation. Limits are per visible actor. */
public final class InfectionCombatVisuals {
    private InfectionCombatVisuals(){}
    public static void host(BlacklightInfectedHost host,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        if(host.isInvisible()||host.isMetamorphosing())return;
        var mc=net.minecraft.client.Minecraft.getInstance();if(mc.player==null||mc.player.distanceToSqr(host)>96*96)return;
        String action=host.nativeAction();float age=host.nativeActionElapsed(partial);
        if(!action.isEmpty()){
            var kind=host.nativeCombat().profile();
            double height=host.bossProfile()==null?host.getBbHeight()*.7:host.bossProfile().visualHeight()*host.bodyScale()*.77;
            var origin=new Vec3(0,height,0);var target=host.level().getEntity(host.nativeActionTarget());
            float load=EvolvedForm.smooth(age/kind.contact)*(1-EvolvedForm.smooth((age-kind.contact)/8));
            var out=buffers.getBuffer(RenderType.entityTranslucentEmissive(ConsumedMobVisuals.WHITE));
            ring(pose,out,origin,new Vec3(0,1,0),Math.max(.16,host.getBbWidth()*.22)*(.5+load),.012,((int)(load*200)<<24)|0xad2534,24);
            if(target!=null&&(kind==NativeInfectedProfile.SONIC||kind==NativeInfectedProfile.GUARDIAN)){
                var end=target.getPosition(partial).add(0,target.getBbHeight()*.5,0).subtract(host.getPosition(partial));var delta=end.subtract(origin);
                if(age<kind.contact&&kind==NativeInfectedProfile.GUARDIAN){ConsumedMobVisuals.tube(pose,out,LightTexture.FULL_BRIGHT,origin,end,.012+.025*load,0x804fbead);}
                else if(age>=kind.contact&&age<kind.contact+8){
                    float t=(age-kind.contact)/8;int alpha=(int)((1-t)*210);int rgb=kind==NativeInfectedProfile.SONIC?0xb7dfde:0xbf3448;
                    ConsumedMobVisuals.tube(pose,out,LightTexture.FULL_BRIGHT,origin,end,.03*(1-t),(alpha<<24)|rgb);
                    for(int i=0;i<5;i++){double at=com.codex.blacklight.forge.WingMath.clamp(t*1.3-i*.11,0,1);ring(pose,out,origin.lerp(end,at),delta.normalize(),.25+at*.65,.02,(alpha<<24)|rgb,16);}
                }
            }
        }
        if(!host.isEvolved())return;
        var move=EvolvedSpecialMove.resolve(host.evolvedAction());if(move==null)return;age=host.evolvedActionElapsed(partial);
        var out=buffers.getBuffer(RenderType.entityTranslucentEmissive(ConsumedMobVisuals.WHITE));
        if(move==EvolvedSpecialMove.SLAM&&age>=move.contact&&age<move.contact+13){
            float t=(age-move.contact)/13;int a=(int)((1-t)*190);
            for(int i=0;i<2;i++)ring(pose,out,new Vec3(0,.07+i*.035,0),new Vec3(0,1,0),.25+Math.min(1,t*1.7)*4.5-i*.13,.025,(a<<24)|(i==0?0xd0b9a4:0x8e283b),40);
        }
        if(move==EvolvedSpecialMove.BASH&&age<move.contact+6){
            double yaw=Math.toRadians(host.yBodyRot);var f=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
            float load=EvolvedForm.smooth(age/move.contact)*(1-EvolvedForm.smooth((age-move.contact)/6));
            ring(pose,out,new Vec3(f.x*.8,host.getBbHeight()*.6,f.z*.8),f,.45+load*.4,.025,((int)(load*165)<<24)|0xc1c4b5,20);
        }
        if(move==EvolvedSpecialMove.VOLLEY&&age<move.retract){
            float pulse=(float)(.55+.4*Math.sin(age*.5));ring(pose,out,new Vec3(0,host.getBbHeight()*.88,0),new Vec3(0,1,0),.25,.014,((int)(pulse*150)<<24)|0xb03e4b,16);
        }
    }
    static void ring(PoseStack pose,VertexConsumer out,Vec3 centre,Vec3 normal,double radius,double width,int color,int segments){
        var n=normal.lengthSqr()<.01?new Vec3(0,1,0):normal.normalize();var u=n.cross(new Vec3(0,1,0));if(u.lengthSqr()<.01)u=new Vec3(1,0,0);u=u.normalize();var v=n.cross(u);
        Vec3 before=centre.add(u.scale(radius));
        for(int i=1;i<=segments;i++){double a=i*Math.PI*2/segments;var next=centre.add(u.scale(Math.cos(a)*radius)).add(v.scale(Math.sin(a)*radius));ConsumedMobVisuals.tube(pose,out,LightTexture.FULL_BRIGHT,before,next,width,color);before=next;}
    }
    static void flesh(PoseStack pose,VertexConsumer out,int light,Vec3 center,double radius,float pulse){
        // Broad contiguous lobes, rather than a spray of detached little cubes.
        box(pose,out,light,center,radius*1.45*pulse,radius*1.4/pulse,radius*1.45,0xff542432);
        box(pose,out,light,center.add(radius*.32,radius*.28,-radius*.12),radius*1.25,radius*.95,radius*1.2,0xff772c3a);
        box(pose,out,light,center.add(-radius*.35,-radius*.25,radius*.08),radius*1.1,radius*.9,radius*1.25,0xff30232b);
    }
    private static final int[][] F={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};
    static void box(PoseStack pose,VertexConsumer out,int light,Vec3 p,double x,double y,double z,int rgb){
        var c=new Vec3[]{p.add(-x/2,-y/2,-z/2),p.add(x/2,-y/2,-z/2),p.add(x/2,y/2,-z/2),p.add(-x/2,y/2,-z/2),p.add(-x/2,-y/2,z/2),p.add(x/2,-y/2,z/2),p.add(x/2,y/2,z/2),p.add(-x/2,y/2,z/2)};
        for(int face=0;face<6;face++){var ids=F[face];var n=c[ids[1]].subtract(c[ids[0]]).cross(c[ids[2]].subtract(c[ids[0]])).normalize();float shade=face==4?1:face==5?.6F:.8F;int color=(rgb&0xff000000)|((int)(((rgb>>16)&255)*shade)<<16)|((int)(((rgb>>8)&255)*shade)<<8)|(int)((rgb&255)*shade);
            for(int id:ids){var point=c[id];out.vertex(pose.last().pose(),(float)point.x,(float)point.y,(float)point.z).color(color).uv(.5F,.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.last().normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();}
        }
    }
}
