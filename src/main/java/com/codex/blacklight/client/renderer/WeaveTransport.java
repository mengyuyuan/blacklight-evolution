package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.animation.WeaveBrace;
import com.codex.blacklight.world.WeaveCell;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

final class WeaveTransport {
    private static final Vec3 UP=new Vec3(0,1,0);
    private static final int FIBERS=7;
    private static final double TAU=2*Math.PI;
    private WeaveTransport() {}

    static void draw(WeaveCell cell,Player actor,double t,PoseStack pose,MultiBufferSource buffers,int light) {
        Vec3 origin=Vec3.atLowerCornerOf(cell.getBlockPos());
        Vec3 f=cell.inward(),right=new Vec3(-f.z,0,f.x);
        Vec3 center=new Vec3(.5,0,.5);
        var mc=Minecraft.getInstance();
        if(actor==mc.player && actor==mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()
                && !actor.isInvisible() && actor instanceof AbstractClientPlayer player) {
            var skin=buffers.getBuffer(RenderType.entityCutoutNoCull(player.getSkin().texture()));
            for(int side:new int[]{-1,1}){
                Vec3 shoulder=cell.stationFeet().subtract(origin).add(right.scale(.29*side)).add(0,1.39,0);
                Vec3 elbow=WeaveBrace.elbow(cell,side,t).subtract(origin);
                Vec3 wrist=WeaveBrace.wrist(cell,side,t).subtract(origin);
                int u=side<0?32:40, v=side<0?48:16;
                int width=player.getSkin().model()==net.minecraft.client.resources.PlayerSkin.Model.SLIM?3:4;
                arm(pose,skin,light,shoulder,elbow,right,u,v,width,0,.116);
                arm(pose,skin,light,elbow,wrist,right,u,v,width,6,.113);
                var sleeve=side<0?net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE:net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE;
                if (player.isModelPartShown(sleeve)) {
                    int sleeveU=side<0?48:40, sleeveV=side<0?48:32;
                    arm(pose,skin,light,shoulder,elbow,right,sleeveU,sleeveV,width,0,.127);
                    arm(pose,skin,light,elbow,wrist,right,sleeveU,sleeveV,width,6,.124);
                }
            }
        }
        // Both arms feed a pair of compact umbilicals. Each filament follows a helix,
        // then peels away to its own opening in the landing collar.
        var skin=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumeTendrilSegmentMesh.TEXTURE));
        var veins=new java.util.ArrayList<Vein>();
        for(int side:new int[]{-1,1}){
            Vec3 wrist=WeaveBrace.wrist(cell,side,t).subtract(origin);
            Vec3 elbow=WeaveBrace.elbow(cell,side,t).subtract(origin);
            Vec3 cuff=wrist.lerp(elbow,.56).add(right.scale(side*.055));
            Vec3 port=center.add(WeaveMantle.socket(f,side));
            for(int i=0;i<FIBERS;i++){
                double delay=12+i*1.45+(side>0?2:0);
                double reach=WeaveBrace.ease((t-delay)/16)*(1-WeaveBrace.ease((t-79-i*.70)/10));
                if(reach<.003)continue;
                double angle=TAU*i/FIBERS;
                Vec3 tip=port.add(right.scale(.132*Math.cos(angle))).add(f.scale(.064*Math.sin(angle))).add(0,-.041,0);
                Vec3 release=cuff.lerp(wrist,.38+.31*Math.cos(angle)).add(right.scale(.052*side));
                Vec3 guide=cuff.add(right.scale(side*.15)).add(f.scale(.41)).add(0,.42,0);
                Vec3 arch=port.subtract(f.scale(.39)).add(right.scale(-side*.10)).add(0,.43,0);
                var points=new Vec3[27];
                var widths=new double[points.length];
                Vec3 lastRidge=null;
                double lastPulse=0;
                for(int j=0;j<points.length;j++){
                    double u=reach*j/(points.length-1);
                    Vec3 axis=bezier(release,guide,arch,tip,u);
                    Vec3 tangent=guide.subtract(release).scale((1-u)*(1-u))
                            .add(arch.subtract(guide).scale(2*(1-u)*u)).add(tip.subtract(arch).scale(u*u)).normalize();
                    Vec3 lateral=tangent.cross(UP);
                    if(lateral.lengthSqr()<.006)lateral=right;
                    lateral=lateral.normalize();
                    Vec3 normal=lateral.cross(tangent).normalize();
                    double sweep=Math.sin(Math.PI*u);
                    double lay=angle+7.6*u+side*(.23*Math.sin(t*.14-u*5))*sweep;
                    double radius=(.050+.050*sweep+.086*WeaveBrace.ease((u-.62)/.25))*Math.sin(Math.PI*Math.min(1,u*1.1+.035));
                    Vec3 strand=axis.add(lateral.scale(Math.cos(lay)*radius)).add(normal.scale(Math.sin(lay)*radius));
                    // Unseated tips search briefly, then dip into their sockets and hold.
                    double probe=.085*(1-reach)*Math.pow(j/(points.length-1.0),3);
                    points[j]=strand.add(lateral.scale(probe*Math.sin(t*.48+i))).add(0,probe*Math.cos(t*.33+i),0);
                    double pressure=surge(t,side,i,u);
                    double taper=WeaveBrace.ease((1-j/(points.length-1.0))/.13);
                    widths[j]=(.029+.009*(i%3)+.024*pressure)*(1-.19*u)*(.08+.92*taper)*Math.min(1,reach*4);
                    Vec3 crest=points[j].add(normal.scale(widths[j]*.92)).add(lateral.scale(widths[j]*.32));
                    if(j>0 && (pressure>.05 || lastPulse>.05))
                        veins.add(new Vein(lastRidge,crest,(lastPulse+pressure)*.5));
                    lastRidge=crest;lastPulse=pressure;
                }
                ConsumeTendrilSegmentMesh.draw(pose,skin,light,points,widths,right,0xffffffff,false);
            }
        }
        var glint=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        for(var vein:veins){
            double p=vein.pressure();
            ConsumedMobVisuals.tube(pose,glint,0xf000f0,vein.start(),vein.end(),.009+.010*p,WeaveMantle.pigment(p));
        }
    }

    private record Vein(Vec3 start,Vec3 end,double pressure) {}

    // Distance 0 is the actor, 1 the receiving port, 2 the iris. The leading edge
    // contracts more quickly than the trailing tissue so the direction remains legible.
    static double surge(double time,int side,int fiber,double distance){
        double lag=(side>0?2.3:0)+.42*Math.sin(fiber*1.79)*Math.min(1,Math.abs(1-distance));
        double transit=distance<=1?distance*10.5:10.5+(distance-1)*8;
        double elapsed=time-30-lag-transit;
        double pulse=0;
        for(int i=0;i<3;i++){
            double age=elapsed-i*16;
            if(age>=0 && age<9){
                double onset=WeaveBrace.ease(age/1.6);
                pulse=Math.max(pulse,onset*(1-WeaveBrace.ease((age-1.6)/7.4)));
            }
        }
        return pulse;
    }

    private static Vec3 bezier(Vec3 a,Vec3 b,Vec3 c,Vec3 d,double u){
        double v=1-u;
        return a.scale(v*v*v).add(b.scale(3*v*v*u)).add(c.scale(3*v*u*u)).add(d.scale(u*u*u));
    }

    // Split the real skin's arm UV at the elbow. Faces share the same wrist positions as the fibers.
    private static void arm(PoseStack pose,VertexConsumer out,int light,Vec3 a,Vec3 b,Vec3 right,
                            int tu,int tv,int width,int row,double radius) {
        Vec3 axis=b.subtract(a).normalize(), depth=right.cross(axis).normalize();
        Vec3 side=axis.cross(depth).normalize().scale(radius*width/4.0);
        depth=depth.scale(radius);
        Vec3[] ring={side.subtract(depth),side.add(depth),depth.subtract(side),side.add(depth).scale(-1)};
        int[] uv={0,4,4+width,8+width,8+width*2};
        for(int i=0;i<4;i++){
            int k=(i+1)%4;
            Vec3 n=ring[i].add(ring[k]).normalize();
            quad(pose,out,light,a.add(ring[i]),a.add(ring[k]),b.add(ring[k]),b.add(ring[i]),n,
                    tu+uv[i],tv+4+row,tu+uv[i+1],tv+10+row);
        }
        if(row==6)quad(pose,out,light,b.add(ring[0]),b.add(ring[1]),b.add(ring[2]),b.add(ring[3]),axis,tu+4+width,tv,tu+4+width*2,tv+4);
    }
    private static void quad(PoseStack pose,VertexConsumer out,int light,Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 n,
                             int u0,int v0,int u1,int v1){
        ConsumedMobVisuals.vertex(pose,out,light,a,n,0xffffffff,u0/64f,v0/64f);
        ConsumedMobVisuals.vertex(pose,out,light,b,n,0xffffffff,u1/64f,v0/64f);
        ConsumedMobVisuals.vertex(pose,out,light,c,n,0xffffffff,u1/64f,v1/64f);
        ConsumedMobVisuals.vertex(pose,out,light,d,n,0xffffffff,u0/64f,v1/64f);
    }
}
