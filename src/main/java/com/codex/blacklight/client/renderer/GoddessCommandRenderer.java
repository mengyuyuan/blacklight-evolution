package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.GoddessCommandProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class GoddessCommandRenderer extends EntityRenderer<GoddessCommandProjectile> {
    public GoddessCommandRenderer(EntityRendererProvider.Context ctx) { super(ctx); }
    @Override public ResourceLocation getTextureLocation(GoddessCommandProjectile e) { return ConsumedMobVisuals.WHITE; }
    @Override public boolean shouldRender(GoddessCommandProjectile e, Frustum frustum, double x, double y, double z) {
        return e.slot() >= 6 && e.distanceToSqr(x,y,z) < 112 * 112 || super.shouldRender(e,frustum,x,y,z);
    }
    private static void tissue(PoseStack pose, VertexConsumer out, int light, Vec3[] path, double[] width, int color) {
        var axis = path[path.length - 1].subtract(path[0]).normalize();
        var side = axis.cross(Math.abs(axis.y) > .88 ? new Vec3(1,0,0) : new Vec3(0,1,0)).normalize();
        if (side.lengthSqr() < .1) side = new Vec3(1,0,0);
        ConsumeTendrilSegmentMesh.draw(pose,out,light,path,width,side,color,false);
    }
    @Override public void render(GoddessCommandProjectile e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        var origin = e.getPosition(partial);
        var boss = e.caller();
        if (e.slot() >= 10) {
            if(boss==null)return;
            var signal=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            var toward=boss.goddess.attacks.commands.packAim().subtract(origin).multiply(1,0,1);
            double range=Math.min(6.5,toward.length());
            if(range<.3)return;
            var axis=toward.normalize(); var side=new Vec3(axis.z,0,-axis.x);
            float t=boss.goddess.attacks.age(partial);
            for(int i=0;i<4;i++){
                double d=(i+.38)*range/4;
                var center=axis.scale(d);
                double size=.28+(e.slot()==10?.20:.03);
                for(int sign:new int[]{-1,1})
                    tissue(pose,signal,LightTexture.FULL_BRIGHT,
                        new Vec3[]{center.add(side.scale(sign*size)),center.add(axis.scale(.31)),center.add(axis.scale(.53))},
                        new double[]{.04,.09,.023},i==((int)(t/3)%4)?0xffffe5b0:0xffbc584a);
            }
            return;
        }
        if (e.slot() >= 6) {
            if (boss == null) return;
            var texture = buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumeTendrilSegmentMesh.TEXTURE));
            float age = boss.goddess.attacks.age(partial);
            if (e.slot() < 8) {
                var commands=boss.goddess.attacks.commands;
                int side=e.slot()%2;
                var root=commands.tendonRoot(side,partial);
                var end=commands.stoneAnchor(side);
                var path = new Vec3[11];
                var widths = new double[11];
                for (int i = 0; i < path.length; i++) {
                    double t = i / 10.;
                    path[i] = GoddessCommandProjectile.tendonPoint(root,end,age,t).subtract(origin);
                    widths[i] = .26 + .12 * (1 - t) + .055 * Math.sin(age * .7 - t * 14);
                }
                tissue(pose,texture,light,path,widths,0xffa14554);
            }
            var node = buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            tissue(pose,node,LightTexture.FULL_BRIGHT,new Vec3[]{new Vec3(0,-.35,0),Vec3.ZERO,new Vec3(0,.38,0)},
                    new double[]{.06,.33,.05},0xffebb487);
            return;
        }
        var solid = buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        float t = e.tickCount + partial;
        double pulse = .85 + .15 * Math.sin(t * .46 + e.slot());
        tissue(pose,solid,LightTexture.FULL_BRIGHT,new Vec3[]{new Vec3(0,-.46*pulse,0),new Vec3(0,-.20*pulse,0),new Vec3(0,.20*pulse,0),new Vec3(0,.46*pulse,0)},
                new double[]{.08,.43*pulse,.43*pulse,.08},0xffc1ff86);
        var glow = buffers.getBuffer(RenderType.eyes(ConsumedMobVisuals.WHITE));
        for (int k = 0; k < 3; k++) {
            var path = new Vec3[9]; var widths = new double[9];
            for (int i = 0; i < 9; i++) {
                double a = i*Math.PI/4 + t*.18 + k, lean = k*1.04 + t*.043;
                path[i] = new Vec3(Math.cos(a)*.70,Math.sin(a)*.70*Math.cos(lean),Math.sin(a)*.70*Math.sin(lean));
                widths[i] = .04 + .04 * (1 + Math.cos(a-t*.3));
            }
            tissue(pose,glow,LightTexture.FULL_BRIGHT,path,widths,0xff55c132);
        }
        var motion = e.getDeltaMovement();
        if (motion.lengthSqr() > .03) {
            var tail = motion.normalize().scale(-2.4);
            tissue(pose,glow,LightTexture.FULL_BRIGHT,new Vec3[]{Vec3.ZERO,tail.scale(.45),tail},
                    new double[]{.28,.19,.012},0xff67db31);
        }
    }
}
