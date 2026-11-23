package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.GoddessEmplacement;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** The vertebrae and exposed wrist end at the same world-space point as the attackable entity. */
public final class GoddessEmplacementRenderer extends EntityRenderer<GoddessEmplacement> {
    public GoddessEmplacementRenderer(EntityRendererProvider.Context ctx){super(ctx);}
    @Override public ResourceLocation getTextureLocation(GoddessEmplacement e){return ConsumeTendrilSegmentMesh.TEXTURE;}
    private static void strand(PoseStack pose,VertexConsumer out,int light,Vec3 from,Vec3 joint,Vec3 end,
                               double base,double mid,double tip,Vec3 side,int color){
        ConsumeTendrilSegmentMesh.draw(pose,out,light,new Vec3[]{from,joint,end},new double[]{base,mid,tip},side,color,false);
    }
    @Override public void render(GoddessEmplacement e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float age=e.age(partial);
        if(age<0 || age>55)return;
        Vec3 origin=e.getPosition(partial),root=e.root().subtract(origin),tip=e.tip(age).subtract(origin);
        var heading=e.heading();var side=new Vec3(heading.z,0,-heading.x).normalize();
        if(side.lengthSqr()<.1)side=new Vec3(1,0,0);
        var up=e.cannon()?side.cross(e.normal()).normalize():new Vec3(0,1,0);
        if(up.y<0)up=up.scale(-1);
        var tissue=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumeTendrilSegmentMesh.TEXTURE));
        int lit=LightTexture.pack(Math.max(12,LightTexture.block(light)),LightTexture.sky(light));
        for(int i=0;i<6;i++){
            double a=i*Math.PI/3;
            var tangent=(e.cannon()?up:heading).scale(Math.sin(a)).add(side.scale(Math.cos(a)));
            strand(pose,tissue,lit,root.add(tangent.scale(.96)),root.add(tangent.scale(.47)).add(e.normal().scale(.16)),
                    root.add(e.normal().scale(.27)),.036,.105,.18,side,0xff73383c);
        }
        if(e.cannon()){
            Vec3 bow=e.bend(age).subtract(origin);
            strand(pose,tissue,lit,root,bow,tip,.34,.30,.25,side,0xff74353c);
            var bone=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            double pulse=.76+.18*Math.sin(age*.73);
            for(int sign:new int[]{-1,1}){
                var edge=side.scale(sign*.23);
                strand(pose,bone,lit,root.add(edge),bow.add(edge.scale(1.2)),tip.add(edge),.09,.115,.075,side,0xff9c4e4c);
                strand(pose,bone,LightTexture.FULL_BRIGHT,root.add(edge.scale(1.05)),bow.add(edge.scale(1.55)),
                        tip.add(edge.scale(1.13)),.018,.034*pulse,.015,side,0xffb7493b);
            }
            for(int i=0;i<6;i++){
                double u=(i+1)/7.0;
                var center=root.lerp(bow,u).lerp(bow.lerp(tip,u),u);
                double lift=Math.max(0,Math.min(1,(age-i*2)/6.0));
                for(int sign:new int[]{-1,1}){
                    var rib=center.add(side.scale(sign*(.20+.14*lift))).add(0,.12*lift,0);
                    var horn=rib.add(side.scale(sign*.12)).add(heading.scale(-.22)).add(0,.12+.27*lift,0);
                    strand(pose,bone,lit,center,rib,horn,.135,.105,.014,side,0xffbda58f);
                }
            }
            for(int sign:new int[]{-1,1}){
                var jaw=tip.add(side.scale(sign*.19));
                strand(pose,bone,lit,jaw.subtract(heading.scale(.36)),jaw,jaw.add(heading.scale(.38)),.12,.15,.035,side,0xffd3b89a);
            }
        }else{
            var elbow=e.bend(age).subtract(origin);
            strand(pose,tissue,lit,root,elbow,tip,.27,.20,.21,side,0xff75323a);
            var bone=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            for(int sign:new int[]{-1,1}){
                var edge=side.scale(sign*.16);
                strand(pose,bone,lit,root.add(edge),elbow.add(edge.scale(1.3)),tip.add(edge),.08,.10,.06,side,0xff97504c);
                strand(pose,bone,LightTexture.FULL_BRIGHT,root.add(edge.scale(1.1)),elbow.add(edge.scale(1.62)),
                        tip.add(edge.scale(1.13)),.015,.026,.012,side,0xffa14238);
            }
            double spread=.32+.19*Math.max(0,1-Math.abs(age-18)/7);
            for(int i=-1;i<=1;i++){
                var knuckle=tip.add(side.scale(i*spread)).add(heading.scale(.22));
                var nail=knuckle.add(heading.scale(.26)).add(side.scale(-i*.11)).add(0,-.16,0);
                strand(pose,bone,lit,tip.add(side.scale(i*.1)),knuckle,nail,.125,.14,.017,side,0xffb69a7b);
            }
        }
    }
}
