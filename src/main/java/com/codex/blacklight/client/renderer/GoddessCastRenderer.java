package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.GoddessAttacks;
import com.codex.blacklight.combat.GoddessCastPose;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Broken, translucent wavefronts show the direction of pressure from the joint that drives it. */
final class GoddessCastRenderer {
    static void draw(EncounterMob boss,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        if(com.codex.blacklight.combat.GoddessTerrainCast.active(boss.goddess.attacks.move())) {
            GoddessTerrainRenderer.draw(boss,partial,stack,buffers,light); return;
        }
        var attack=boss.goddess.attacks;
        var move=attack.move();
        if(!GoddessCastPose.active(move) && move!=GoddessAttacks.Move.CALL && move!=GoddessAttacks.Move.STRONG_CALL)return;
        float age=attack.age(partial);
        Vec3 aim=attack.center(),base=boss.getPosition(partial);
        if(attack.move()==GoddessAttacks.Move.VACUOLE){
            Vec3 mark=aim.subtract(base);
            if(age<20){
                // The eyes turn first. A small glint stays on the face for the entire windup.
                Vec3 eye=GoddessCastPose.face(boss,partial).subtract(base);
                pulse(stack,buffers,light,eye,eye.add(mark.subtract(eye).normalize()),0,
                        .27F+.18F*Mth.clamp(age/16,0,1),.46F,Math.min(1,age/8),age*.07F);
            }else if(age<28 || attack.caught() && age<52 || attack.ruptured() && age<61){
                boolean burst=age>=52;
                float close=Mth.clamp((age-20)/8,0,1);
                float squeeze=Mth.clamp((age-28)/23,0,1);
                float release=Mth.clamp((age-52)/9,0,1);
                float radius=burst?1.2F+4.4F*release:3.13F-.42F*close-1.79F*squeeze;
                float alpha=burst?1-release:Math.min(.79F,.36F+.08F*(age-20));
                for(int i=0;i<3;i++){
                    Vec3 axis=i==0?new Vec3(.11,1,.18):i==1?new Vec3(1,.21,.28):new Vec3(.34,-.12,1);
                    pulse(stack,buffers,light,mark,mark.add(axis),0,radius*(1-.045F*i),
                            burst?.59F:.44F,alpha,age*.035F*(i==1?-1:1)+i*1.72F);
                }
            }
            return;
        }
        if(attack.move()==GoddessAttacks.Move.NEEDLE_RAIN && age>=3 && age<=40){
            var crown=boss.hurtRegions(partial).get(11);
            if(crown.boxes().isEmpty())return;
            float windup=Mth.clamp((age-3)/8,0,1);
            float flick=age<12?windup:1-Mth.clamp(((age-12)%6)/6,0,1);
            if(age>=9){
                float launch=((age-9)%6)/6;
                var source=crown.bounds().getCenter().subtract(base);
                GoddessFlare.petals(stack,buffers,source,aim.subtract(base),launch,4.5F,1-launch*.7F,7);
                coil(stack,buffers,light,source,aim.subtract(base),0,.08F+.10F*launch,
                        3.4F*(1-launch)+.55F,.32F,age*.11F,.92F*(1-launch),5);
            }
            // Each corona forms at the same crown surface used to launch the needles.
            for(int i=0;i<3;i++){
                var box=crown.boxes().get(i*(crown.boxes().size()-1)/2);
                pulse(stack,buffers,light,box.nearest(aim).subtract(base),aim.subtract(base),
                        .03F+.04F*(1-flick),.95F+.62F*flick,1,.62F*flick,age*.09F+i);
            }
        }
        if(move==GoddessAttacks.Move.WALL_SPEARS){
            var chest=boss.hurtRegions(partial).get(1).bounds().getCenter().subtract(base);
            var target=aim.subtract(base);
            if(age>=24 && age<34)GoddessFlare.petals(stack,buffers,chest,target,(age-24)/10,4.3F,1,7);
            if(age>=3 && age<29){
                float wind=Mth.clamp((age-3)/22,0,1);
                coil(stack,buffers,light,chest,target,.008F,.13F,5.2F-2.6F*wind,.7F,age*.08F,wind,7);
                pulse(stack,buffers,light,chest,target,.035F,2.2F+1.7F*wind,.85F,wind,-age*.09F);
            }else if(age>=28 && age<37){
                float t=(age-28)/9;
                coil(stack,buffers,light,chest,target,t*.55F,t*.55F+.13F,1.5F,.5F,age*.14F,1-t,5);
            }else if(age>=48 && age<72){
                float t=(age-48)/24;
                travel(stack,buffers,light,target,chest,t,2.2F);
            }
            return;
        }
        if(move==GoddessAttacks.Move.CALL || move==GoddessAttacks.Move.STRONG_CALL){
            // Orders travel across the conductor's body on the same beat as the reinforcement signal.
            boolean strong=move==GoddessAttacks.Move.STRONG_CALL;
            var regions=boss.hurtRegions(partial);
            Vec3 chest=regions.get(1).bounds().getCenter().subtract(base);
            Vec3 crown=regions.get(11).bounds().getCenter().subtract(base);
            if(age>=2 && age<(strong?65:48)){
                for(int slot:new int[]{5,9,11}){
                    if(regions.get(slot).boxes().isEmpty())continue;
                    Vec3 end=slot==11?crown:regions.get(slot).bounds().getCenter().subtract(base);
                    float wave=((age+slot)%8)/8;
                    coil(stack,buffers,light,chest,end,Math.max(0,wave-.38F),wave,
                            strong?2.3F:1.5F,.55F,slot+age*.18F,.95F,3);
                    if(strong)pulse(stack,buffers,light,chest,end,.84F,1.4F+wave*1.3F,.78F,1-wave,slot);
                }
            }
            return;
        }
        if(attack.move()==GoddessAttacks.Move.BLOOD_SURF){
            float beat=age<18 ? (age-5)/7 : (age-30)/8;
            if(beat<0 || beat>1.24F)return;
            var forward=aim.subtract(base).multiply(1,0,1).normalize();
            if(forward.lengthSqr()<.1)forward=new Vec3(0,0,1);
            var rank=aim.subtract(forward.scale(14));
            var floor=boss.level().clip(new ClipContext(rank.add(0,4,0),rank.add(0,-7,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
            if(floor.getType()!=HitResult.Type.BLOCK)return;
            int mask=boss.goddess.removed(),wrist=(mask&1)==0?5:(mask&2)==0?9:1;
            var source=boss.hurtRegions(partial).get(wrist).bounds().getCenter().subtract(base);
            var point=floor.getLocation().subtract(base).add(0,age<18?.65:2.30,0);
            GoddessFlare.petals(stack,buffers,source,point,Math.min(1,beat),age<18?3.3F:4.3F,.86F,5);
            travel(stack,buffers,light,source,point,Math.min(1,beat),3.15F);
            coil(stack,buffers,light,source,point,Math.max(0,beat-.37F),Math.min(1,beat),
                    age<18?2.9F:3.7F,.57F,age*(age<18?.21F:-.20F),1,5);
        }
    }
    /** A short moving ribbon has a dark outer face and a narrow wet edge. Nothing fills its centre. */
    static void coil(PoseStack stack,MultiBufferSource buffers,int light,Vec3 from,Vec3 to,float start,float end,
                     float rootRadius,float tipRadius,float phase,float alpha,int strands){
        if(end<=start || alpha<=.02F)return;
        Vec3 axis=to.subtract(from).normalize();
        if(axis.lengthSqr()<.5)return;
        Vec3 right=axis.cross(Math.abs(axis.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        Vec3 up=axis.cross(right);
        var out=buffers.getBuffer(RenderType.entityTranslucentEmissive(ConsumedMobVisuals.WHITE));
        for(int i=0;i<strands;i++){
            Vec3 prevOuter=null,prevInner=null,prevRidge=null;
            for(int j=0;j<=8;j++){
                float u=j/8F;
                double twist=phase+i*Math.PI*2/strands+u*(2.5+.4*(i%2));
                Vec3 radial=right.scale(Math.cos(twist)).add(up.scale(Math.sin(twist)));
                Vec3 tangent=axis.cross(radial);
                double radius=Mth.lerp(u,rootRadius,tipRadius)*(1+.11*Math.sin(phase+u*7+i));
                Vec3 mid=from.lerp(to,Mth.lerp(u,start,end)).add(radial.scale(radius));
                double width=(.05+.30*Math.min(2.8,rootRadius))*Math.sin(Math.PI*u);
                Vec3 outer=mid.add(tangent.scale(width)),ridge=mid.add(radial.scale(width*.23)).add(tangent.scale(width*.56)),
                        inner=mid.subtract(tangent.scale(width));
                if(j>0){
                    int ink=((int)(172*alpha)<<24)|0x201023;
                    int vein=((int)(202*alpha)<<24)|0x923948;
                    int edge=((int)(207*alpha)<<24)|0xe3b6a7;
                    vertex(stack,out,prevInner,radial,ink,light);vertex(stack,out,inner,radial,ink,light);
                    vertex(stack,out,ridge,radial,vein,light);vertex(stack,out,prevRidge,radial,vein,light);
                    vertex(stack,out,prevRidge,radial,vein,light);vertex(stack,out,ridge,radial,vein,light);
                    vertex(stack,out,outer,radial,edge,light);vertex(stack,out,prevOuter,radial,edge,light);
                }
                prevOuter=outer;prevInner=inner;prevRidge=ridge;
            }
        }
    }
    private static void travel(PoseStack s,MultiBufferSource b,int l,Vec3 from,Vec3 to,float t,float radius){
        float fade=Math.min(1,t*12);
        for(int ring=0;ring<3;ring++){
            float position=t-ring*.075F;
            if(position<=0 || position>1)continue;
            pulse(s,b,l,from,to,position,radius*(.55F+.45F*t)*(1-ring*.16F),
                    1-ring*.17F,fade*(1-ring*.18F),.44F*ring+t*1.4F);
        }
    }
    private static void pulse(PoseStack s,MultiBufferSource b,int light,Vec3 from,Vec3 to,float t,
                              float radius,float width,float alpha,float angle){
        var axis=to.subtract(from).normalize();
        if(axis.lengthSqr()<.6 || alpha<.025)return;
        var right=axis.cross(Math.abs(axis.y)>.94?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        var up=axis.cross(right);
        Vec3 center=from.lerp(to,t);
        var out=b.getBuffer(RenderType.entityTranslucentEmissive(ConsumedMobVisuals.WHITE));
        for(int arc=0;arc<3;arc++){
            double start=angle+arc*2*Math.PI/3;
            for(int j=0;j<7;j++){
                double a=start+j*.215, z=a+.215;
                var r1=right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                var r2=right.scale(Math.cos(z)).add(up.scale(Math.sin(z)));
                // Asymmetric vanishing edges catch light over dark rock and over bright lava.
                float end=(float)Math.sin((j+.5)*Math.PI/7)*alpha;
                int ink=((int)(170*end)<<24)|0x17101d, gloss=((int)(155*end)<<24)|0xc9b5bc;
                Vec3 outer1=center.add(r1.scale(radius)),outer2=center.add(r2.scale(radius));
                var inner1=center.add(r1.scale(radius*(1-.18F*width))).add(axis.scale(-.26F*width));
                var inner2=center.add(r2.scale(radius*(1-.18F*width))).add(axis.scale(-.26F*width));
                vertex(s,out,inner1,axis,ink,light);vertex(s,out,outer1,axis,gloss,light);
                vertex(s,out,outer2,axis,gloss,light);vertex(s,out,inner2,axis,ink,light);
            }
        }
    }
    private static void vertex(PoseStack stack,VertexConsumer out,Vec3 p,Vec3 n,int color,int light){
        var pose=stack.last();
        out.vertex(pose.pose(),(float)p.x,(float)p.y,(float)p.z).color(color).uv(.5F,.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
    }
    private GoddessCastRenderer(){}
}
