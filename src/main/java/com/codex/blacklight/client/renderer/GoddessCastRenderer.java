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
        var attack=boss.goddess.attacks;
        if(!GoddessCastPose.active(attack.move()))return;
        float age=attack.age(partial);
        Vec3 aim=attack.center(),base=boss.getPosition(partial);
        if(attack.move()==GoddessAttacks.Move.VACUOLE){
            var eye=GoddessCastPose.face(boss,partial);
            if(age>=1 && age<21)pulse(stack,buffers,light,eye.subtract(base),aim.subtract(base),
                    .013F,.75F+.11F*age,1,Math.min(1,age/6),age*.06F);
            if(age>=20 && age<=28)travel(stack,buffers,light,eye.subtract(base),aim.subtract(base),(age-20)/8,2.9F);
            if(attack.caught() && age>=29 && age<=52){
                float sweep=((age-29)%8)/8;
                travel(stack,buffers,light,eye.subtract(base),aim.subtract(base),sweep,1.6F);
            }
            return;
        }
        if(attack.move()==GoddessAttacks.Move.NEEDLE_RAIN && age>=3 && age<=40){
            var crown=boss.hurtRegions(partial).get(11);
            if(crown.boxes().isEmpty())return;
            float windup=Mth.clamp((age-3)/8,0,1);
            float flick=age<12?windup:1-Mth.clamp(((age-12)%6)/6,0,1);
            // Each corona forms at the same crown surface used to launch the needles.
            for(int i=0;i<3;i++){
                var box=crown.boxes().get(i*(crown.boxes().size()-1)/2);
                pulse(stack,buffers,light,box.nearest(aim).subtract(base),aim.subtract(base),
                        .03F+.04F*(1-flick),.95F+.62F*flick,1,.62F*flick,age*.09F+i);
            }
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
            travel(stack,buffers,light,source,point,Math.min(1,beat),3.15F);
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
        out.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(color).setUv(.5F,.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,(float)n.x,(float)n.y,(float)n.z);
    }
    private GoddessCastRenderer(){}
}
