package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.GoddessAttacks;
import com.codex.blacklight.combat.GoddessTerrainCast;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Geometry remains readable without particles or a post-processing chain. All vertices use the combat coordinates. */
final class GoddessTerrainRenderer {
    private static final Vec3 UP=new Vec3(0,1,0);
    private static final int SECTORS=48;
    private static final class Ground {
        int cast=-1, waveTick=-1;
        final Vec3[] spikes=new Vec3[GoddessTerrainCast.ROWS*GoddessTerrainCast.LANES], ring=new Vec3[SECTORS];
        final int[] sampled=new int[spikes.length];
    }
    private static final WeakHashMap<EncounterMob,Ground> floors=new WeakHashMap<>();

    static void draw(EncounterMob boss,float partial,PoseStack s,MultiBufferSource buffers,int light) {
        var attack=boss.goddess.attacks; var cast=attack.terrain;
        if(!cast.ready())return;
        float age=attack.age(partial); Vec3 root=boss.getPosition(partial);
        var mesh=new Mesh(s,buffers.getBuffer(RenderType.entityTranslucentEmissive(ConsumedMobVisuals.WHITE)),root,light);

        if(attack.move()==GoddessAttacks.Move.SONIC_CRY) {
            for(int i=0;i<2;i++) sound(mesh,cast,age,i);
        } else {
            var cache=floors.computeIfAbsent(boss,v->new Ground());
            if(cache.cast!=attack.sequence()){cache.cast=attack.sequence();cache.waveTick=-1;Arrays.fill(cache.sampled,-100);}
            if(attack.move()==GoddessAttacks.Move.SPIKE_FIELD){
                spikes(mesh,cast,cache,age);
                drawOrganicSpines(s,buffers,light,root,cache,age);
            }
            else wave(mesh,cast,cache,age);
        }

    }
    private static void sound(Mesh mesh,GoddessTerrainCast cast,float age,int pulse) {
        Vec3 axis=cast.axis(pulse), source=cast.mouth(pulse);
        float front=GoddessTerrainCast.soundRadius(age,pulse);
        float reach=GoddessTerrainCast.soundReach(pulse);
        if(axis.lengthSqr()<.5 || front<=0 || front>reach+5)return;
        Vec3 right=axis.cross(Math.abs(axis.y)>.95?new Vec3(1,0,0):UP).normalize(), up=right.cross(axis);
        double angle=GoddessTerrainCast.soundAngle(pulse);
        for(int band=0;band<4;band++) {
            double r=front-band*(pulse==0?1.25:2.15);
            if(r<.22)continue;
            float fade=(float)Math.min(1,r/3)*Math.min(1,(reach+5-front)/6)*(1-band*.17F);
            // Angular latitude rings bow forward from the lips. Closely spaced repeats are a single acoustic pulse.
            for(int arc=0;arc<3;arc++) {
                double latitude=angle*(.36+.32*arc);
                double lip=latitude-Math.min(.064,(.24+.018*r)/r);
                double cap=Math.cos(latitude),sin=Math.sin(latitude),innerCos=Math.cos(lip),innerSin=Math.sin(lip);
                for(int j=0;j<SECTORS;j++) {
                    double a=j*Math.PI*2/SECTORS,b=(j+1)*Math.PI*2/SECTORS;
                    Vec3 v=right.scale(Math.cos(a)).add(up.scale(Math.sin(a))),w=right.scale(Math.cos(b)).add(up.scale(Math.sin(b)));
                    Vec3 outer=source.add(axis.scale(r*cap));
                    Vec3 inner=source.add(axis.scale(r*innerCos));
                    int rim=(int)(fade*(arc==2?198:115))<<24|0xc6ecec, flank=(int)(fade*88)<<24|0x322a4a;
                    mesh.quad(outer.add(v.scale(r*sin)),outer.add(w.scale(r*sin)),inner.add(w.scale(r*innerSin)),inner.add(v.scale(r*innerSin)),rim,rim,flank,flank);
                }
            }
        }
    }
    private static void spikes(Mesh mesh,GoddessTerrainCast cast,Ground cache,float age) {
        for(int row=0;row<GoddessTerrainCast.ROWS;row++) {
            float phase=age-GoddessTerrainCast.eruption(row);
            if(phase < -14 || phase>=11)continue;
            for(int lane=0;lane<GoddessTerrainCast.LANES;lane++) {
                int index=row*GoddessTerrainCast.LANES+lane;
                if((int)age-cache.sampled[index]>=3){
                    Vec3 p=cast.surface(cast.spike(row,lane));
                    cache.spikes[index]=p!=null && cast.clear(cast.base().add(0,1.8,0),p.add(0,.9,0))?p:null;
                    cache.sampled[index]=(int)age;
                }
                Vec3 floor=cache.spikes[index];if(floor==null)continue;
                double rotation=(row*13+lane*17)*.38;
                double opening=Math.min(1,Math.max(0,(phase+14)/9));
                if(row>0 && cache.spikes[index-GoddessTerrainCast.LANES]!=null) {
                    Vec3 previous=cache.spikes[index-GoddessTerrainCast.LANES];
                    if(Math.abs(floor.y-previous.y)<.35){
                        Vec3 a=previous.add(0,.05,0),end=a.lerp(floor.add(0,.06,0),opening);
                        Vec3 axis=end.subtract(a).multiply(1,0,1).normalize();
                        Vec3 side=new Vec3(axis.z,0,-axis.x);
                        Vec3 bend=a.lerp(end,.55).add(side.scale(.38*Math.sin(index*8.31)));
                        mesh.quad(a,bend.add(side.scale(.11)),end,bend.subtract(side.scale(.09)),0x5c371219,0xf19a3348,0x4c1a1115,0xf13a141b);
                    }
                }
                for(int i=0;i<5;i++) {
                    double a=rotation+i*Math.PI*2/5;
                    Vec3 radial=new Vec3(Math.cos(a),0,Math.sin(a)),side=new Vec3(-radial.z,0,radial.x);
                    Vec3 at=floor.add(0,.045,0),joint=at.add(radial.scale(.98*opening)).add(side.scale(.26)),tip=at.add(radial.scale(2.1*opening));
                    mesh.quad(at,joint.add(side.scale(.13)),tip,joint.add(side.scale(-.13)),0xc1250616,0xedb92548,0x226b1832,0xdd430b28);
                }
            }
        }
    }

    private static void drawOrganicSpines(PoseStack stack,MultiBufferSource buffers,int light,Vec3 origin,Ground ground,float age){
        for(int row=0;row<GoddessTerrainCast.ROWS;row++){
            float height=GoddessTerrainCast.spikeHeight(age-GoddessTerrainCast.eruption(row));
            if(height<=0)continue;
            for(int lane=0;lane<GoddessTerrainCast.LANES;lane++){
                int index=row*GoddessTerrainCast.LANES+lane;
                Vec3 root=ground.spikes[index];
                if(root==null)continue;
                boolean cluster=(row+lane)%3==0;
                Vec3 local=root.subtract(origin);
                stack.pushPose();
                stack.translate(local.x,local.y+.025,local.z);
                // The two existing weapon sculpts have different crown heights. Both crests reach the tested tip.
                stack.scale(cluster?1.02F:1.28F,GoddessTerrainCast.SPIKE_HEIGHT/(cluster?2.992425F:4.54106F),cluster?1.02F:1.28F);
                AuthoredDevastatorMesh.playerShard(stack,buffers,light,cluster?"skill_groundspike_cluster_small":"skill_groundspike_mid",
                        (float)(index*2.399963),height/GoddessTerrainCast.SPIKE_HEIGHT);
                stack.popPose();
            }
        }
    }
    private static void wave(Mesh mesh,GoddessTerrainCast cast,Ground cache,float age) {
        boolean gathering=age<GoddessTerrainCast.RELEASE;
        double r=gathering?Math.max(2,14-age*.46):GoddessTerrainCast.waveRadius(age);
        if(r<.1 || r>69)return;
        Vec3 base=cast.base();
        if(cache.waveTick!=(int)age){
            cache.waveTick=(int)age;
            for(int j=0;j<SECTORS;j++){
                double a=j*Math.PI*2/SECTORS;
                Vec3 p=cast.surface(base.add(r*Math.cos(a),0,r*Math.sin(a)));
                cache.ring[j]=p!=null && cast.clear(base.add(0,1.8,0),p.add(0,.8,0))?p:null;
            }
        }
        for(int j=0;j<SECTORS;j++){
            int next=(j+1)%SECTORS;
            Vec3 v=cache.ring[j],w=cache.ring[next];
            if(v==null || w==null || Math.abs(v.y-w.y)>3)continue;
            double a=j*Math.PI*2/SECTORS,b=(j+1)*Math.PI*2/SECTORS;
            Vec3 dir1=new Vec3(Math.cos(a),0,Math.sin(a)),dir2=new Vec3(Math.cos(b),0,Math.sin(b));
            Vec3 p=new Vec3(base.x+r*dir1.x,v.y+.08,base.z+r*dir1.z),q=new Vec3(base.x+r*dir2.x,w.y+.08,base.z+r*dir2.z);
            double h=gathering?.28+.20*age/26:.90+.15*Math.sin(j*2.1);
            Vec3 crest=p.add(0,h,0),crest2=q.add(0,h,0);
            mesh.quad(p,q,crest2,crest,0x3b311120,0x3b311120,0xebc64e61,0xebc64e61);
            mesh.quad(crest,crest2,q.subtract(dir2.scale(2.6)),p.subtract(dir1.scale(2.6)),0xe5ed9390,0xe5ed9390,0x031b0e16,0x031b0e16);
        }
    }
    private record Mesh(PoseStack s, VertexConsumer out, Vec3 origin, int light) {
        void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int ca,int cb,int cc,int cd) {
            vertex(a,ca);vertex(b,cb);vertex(c,cc);vertex(d,cd);
        }
        private void vertex(Vec3 world,int color) {
            Vec3 p=world.subtract(origin);
            var pose=s.last();
            out.vertex(pose.pose(),(float)p.x,(float)p.y,(float)p.z).color(color).uv(.5F,.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(),0,1,0).endVertex();
        }
    }
    private GoddessTerrainRenderer(){}
}
