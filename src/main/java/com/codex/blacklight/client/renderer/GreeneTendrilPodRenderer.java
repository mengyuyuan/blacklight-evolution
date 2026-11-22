package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.GreeneTendrilPod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;
import java.util.*;

public final class GreeneTendrilPodRenderer extends EntityRenderer<GreeneTendrilPod> {
    private record NearbyBlocks(long tick,List<AABB> boxes){}
    private final Map<GreeneTendrilPod,NearbyBlocks> obstacles=new WeakHashMap<>();
    public GreeneTendrilPodRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(GreeneTendrilPod pod){return ConsumedMobVisuals.WHITE;}
    private static Vec3 vec(BlackTendrilMesh.P p){return new Vec3(p.x(),p.y(),p.z());}
    static BlackTendrilMesh.Surface surface(PoseStack pose,VertexConsumer out,int light){
        int readLight=LightTexture.pack(Math.max(9,LightTexture.block(light)),LightTexture.sky(light));
        return (p,q,r,s,color)->{
            var a=vec(p);var b=vec(q);var c=vec(r);var d=vec(s);
            var normal=b.subtract(a).cross(d.subtract(a)).normalize();
            ConsumedMobVisuals.vertex(pose,out,readLight,a,normal,color,.5F,.5F);
            ConsumedMobVisuals.vertex(pose,out,readLight,b,normal,color,.5F,.5F);
            ConsumedMobVisuals.vertex(pose,out,readLight,c,normal,color,.5F,.5F);
            ConsumedMobVisuals.vertex(pose,out,readLight,d,normal,color,.5F,.5F);
        };
    }
    @Override public void render(GreeneTendrilPod pod,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        long tick=pod.level().getGameTime();
        var terrain=obstacles.get(pod);
        if(terrain==null || terrain.tick()!=tick){
            var around=pod.getBoundingBox().inflate(GreeneTendrilPod.RADIUS+.25,2,GreeneTendrilPod.RADIUS+.25);
            var boxes=new ArrayList<AABB>();
            for(var shape:pod.level().getBlockCollisions(pod,around))
                for(var box:shape.toAabbs())if(box.maxY>pod.getY()+.015)boxes.add(box.move(-pod.getX(),-pod.getY(),-pod.getZ()));
            terrain=new NearbyBlocks(tick,boxes);obstacles.put(pod,terrain);
        }
        var boxes=terrain.boxes();
        BlackTendrilMesh.draw(surface(pose,out,light),(a,b)->{
            if(boxes.isEmpty())return null;
            var from=vec(a);var to=vec(b);Vec3 hit=null;
            for(var box:boxes){
                if(box.contains(from))return a;
                var contact=box.clip(from,to);
                if(contact.isPresent()){hit=contact.get();to=hit;}
            }
            return hit==null?null:new BlackTendrilMesh.P(hit.x,hit.y,hit.z);
        },pod.age(partial),Math.floorMod(pod.getUUID().hashCode(),8192)*.001,GreeneTendrilPod.WINDUP,GreeneTendrilPod.RADIUS);
    }
    @Override public boolean shouldRender(GreeneTendrilPod pod,net.minecraft.client.renderer.culling.Frustum f,double x,double y,double z){
        return pod.shouldRender(x,y,z)&&f.isVisible(pod.getBoundingBox().inflate(GreeneTendrilPod.RADIUS));
    }
}
