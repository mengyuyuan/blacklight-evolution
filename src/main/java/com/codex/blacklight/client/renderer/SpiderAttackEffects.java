package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.SpiderActions.Move;
import com.codex.blacklight.combat.SpiderRupture;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.vertex.*;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;

final class SpiderAttackEffects {
    private record FloorSamples(Move move,long start,long refresh,Vec3[] points) {}
    private final WeakHashMap<EncounterMob,FloorSamples> samples = new WeakHashMap<>();

    private Vec3[] contacts(EncounterMob boss,Move move){
        long tick=boss.level().getGameTime(), start=boss.spiderData().getLong("Start");
        var cached=samples.get(boss);
        if(cached!=null && cached.move==move && cached.start==start && tick-cached.refresh<4)return cached.points;
        Vec3[] points;
        if(move==Move.SPIKES){
            points=SpiderRupture.spikeSites(boss,SpiderRupture.ROWS-1);
        }else{
            points=new Vec3[2*SpiderRuptureMesh.SPOKES];
            var origin=boss.spider.actions.aim();
            for(int i=0;i<points.length;i++){
                var local=SpiderRuptureMesh.sample(i/2,i%2==1,(boss.getId()%31)*.2);
                var site=SpiderRupture.floor(boss,origin,origin.add(local.x(),0,local.z()));
                points[i]=site==null?null:site.subtract(origin);
            }
        }
        samples.put(boss,new FloorSamples(move,start,tick,points));
        return points;
    }
    private static BlackTendrilMesh.P point(Vec3 v){return v==null?null:new BlackTendrilMesh.P(v.x,v.y,v.z);}
    void draw(EncounterMob boss,float partial,PoseStack stack,VertexConsumer out,int light){
        var actions=boss.spider.actions;
        var move=actions.move();float t=actions.age(partial);
        if(move!=Move.HEAVY_STAMP && move!=Move.SPIKES)return;
        var origin=boss.getPosition(partial);
        if(move==Move.HEAVY_STAMP){
            if(t<1 || t>=33 || t>=18 && !boss.spiderData().contains("Lodged"))return;
            var p=contacts(boss,move);
            var local=actions.aim().subtract(origin);
            stack.pushPose();stack.translate(local.x,local.y+.02,local.z);
            var surface=GreeneTendrilPodRenderer.surface(stack,out,light);
            var center=new BlackTendrilMesh.P(0,0,0);
            for(int i=0;i<SpiderRuptureMesh.SPOKES;i++)
                SpiderRuptureMesh.spoke(surface,i,t,center,point(p[2*i]),point(p[2*i+1]));
            stack.popPose();
        }else{
            if(t<2 || t>39)return;
            var points=contacts(boss,move);
            for(int row=0;row<SpiderRupture.ROWS;row++)for(int lane=-1;lane<=1;lane++){
                var at=points[row*3+lane+1];if(at==null)continue;
                float dt=t-(18+row*4);
                if(dt < -16 || dt>5)continue;
                var axis=actions.aim().subtract(boss.position()).multiply(1,0,1).normalize();
                var side=new Vec3(axis.z,0,-axis.x);
                var p=at.subtract(origin).add(0,.08,0);
                double pulse=Math.max(0,1-Math.abs(dt)/16),w=.055+.075*pulse;
                // A pair of parted lips marks each eruption before the actual shard breaks through.
                for(int sign:new int[]{-1,1}){
                    var middle=p.add(side.scale(sign*(.12+.45*pulse))).add(0,.06+.14*pulse,0);
                    var start=p.subtract(axis.scale(.7+1.4*pulse));var end=p.add(axis.scale(.7+1.3*pulse));
                    ConsumedMobVisuals.tube(stack,out,light,start,middle,w*1.6,0xff29232f);
                    ConsumedMobVisuals.tube(stack,out,LightTexture.FULL_BRIGHT,middle,end,w,dt<0?0xffa6464e:0xffe59578);
                }
            }
        }
    }
}
