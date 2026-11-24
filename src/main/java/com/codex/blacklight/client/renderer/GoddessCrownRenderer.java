package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.GoddessAttacks;
import com.codex.blacklight.combat.GoddessCrownCast;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

final class GoddessCrownRenderer {
    private static final Vec3 UP=new Vec3(0,1,0);
    static void draw(EncounterMob boss,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        var attack=boss.goddess.attacks;var cast=attack.crown;
        if(!cast.acting())return;
        float age=attack.age(partial);Vec3 origin=boss.getPosition(partial);
        if(attack.move()==GoddessAttacks.Move.INVERTED_ACID){
            var tissue=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            if(age< GoddessCrownCast.SPIT_B){
                var rows=boss.hurtRegions(partial);
                for(int row=0;row<2;row++){
                    float fill=GoddessCrownCast.smooth(age,9+row*17,32+row*17);
                    var glands=rows.get(GoddessCrownCast.FIRST_ROW+row).boxes();
                    for(int i=0;i<glands.size();i++){
                        var mouth=glands.get(i).center.subtract(origin);
                        double swell=.10+.16*fill+.028*Math.sin(age*.65-i*.41);
                        drop(stack,tissue,light,mouth,swell,0xFF87943E);
                    }
                }
            }
            for(var glob:cast.globs())if(glob.visible(age)){
                var at=glob.point(age).subtract(origin);var trail=glob.point(age-1.5F).subtract(origin);
                ConsumedMobVisuals.tube(stack,tissue,light,trail,at,.135,0xFF869547);
                drop(stack,tissue,light,at,.30,0xFFC1CE63);
                drop(stack,tissue,light,at.lerp(trail,.42).add(.05,.11,0),.11,0xFFECEDA8);
            }
            return;
        }
        var skin=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumeTendrilSegmentMesh.TEXTURE));
        Vec3 center=cast.base(partial);
        float draw=GoddessCrownCast.smooth(age,2,24)*(1-GoddessCrownCast.smooth(age,68,78));
        if(draw<.01)return;
        for(int i=0;i<GoddessCrownCast.BLADES;i++){
            var path=cast.strand(center,i,age,true);
            if(path.length<2)continue;
            double[] widths=new double[path.length];
            for(int j=0;j<path.length;j++){
                path[j]=path[j].subtract(origin);
                widths[j]=draw*GoddessCrownCast.width(j)*(.92+.15*Math.sin(age*.4-j*.86+i));
            }
            widths[widths.length-1]=.045*draw;
            var side=path[path.length-1].subtract(path[0]).cross(UP).normalize();
            ConsumeTendrilSegmentMesh.draw(stack,skin,light,path,widths,side,0xFFD5B5AB,true);
        }
        if(age<33){
            var wet=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            for(int i=0;i<GoddessCrownCast.BLADES;i++){
                Vec3 rim=cast.tip(i);if(rim==null)continue;
                var toe=rim.subtract(origin).add(0,-.67,0);var dir=toe.subtract(center.subtract(origin)).multiply(1,0,1).normalize();
                var a=toe.subtract(dir.scale(3.6));var b=toe.add(dir.scale(.5));
                ConsumedMobVisuals.tube(stack,wet,light,a,b,.07+.11*draw,0xFFB5957E);
            }
        }
    }
    private static void drop(PoseStack stack,VertexConsumer out,int light,Vec3 p,double r,int color){
        for(int band=0;band<3;band++)for(int face=0;face<6;face++){
            for(int vertex=0;vertex<4;vertex++){
                int k=(vertex==1||vertex==2)?1:0;
                double theta=2*Math.PI*(face+k)/6,phi=Math.PI*((band+(vertex>=2?1:0))/3.-.5);
                var n=new Vec3(Math.cos(phi)*Math.cos(theta),Math.sin(phi),Math.cos(phi)*Math.sin(theta));
                ConsumedMobVisuals.vertex(stack,out,light,p.add(n.scale(r)),n,color,.5F,.5F);
            }
        }
    }
    private GoddessCrownRenderer(){}
}
