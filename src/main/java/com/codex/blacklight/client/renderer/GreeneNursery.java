package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.SpiderActions;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.registry.ModBlocks;
import com.codex.blacklight.world.GreeneHiveCave;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Two rooted lips separate along the front seam. The visible occupant and the released mob share a type and exit. */
final class GreeneNursery {
    private static final ResourceLocation SKIN=new ResourceLocation("blacklight","textures/block/greene_wall_nest.png");
    private java.lang.ref.WeakReference<Mob> preview=new java.lang.ref.WeakReference<>(null);
    private final long[][] probed=new long[6][8];
    private final float[][] tilt=new float[6][8];
    private final long[] socketAt=new long[6];
    private static float smooth(float t){return t*t*(3-2*t);}

    private float surfaceAngle(EncounterMob boss,int socket,int root,BlockPos pos,Direction normal,Vec3 pivot,Vec3 tip){
        long now=boss.level().getGameTime();
        if(socketAt[socket]!=pos.asLong()){socketAt[socket]=pos.asLong();java.util.Arrays.fill(probed[socket],Long.MIN_VALUE);}
        if(probed[socket][root]<=now && probed[socket][root]>now-18)return tilt[socket][root];
        probed[socket][root]=now;
        var outward=Vec3.atLowerCornerOf(normal.getNormal());
        var right=normal==Direction.DOWN?new Vec3(1,0,0):new Vec3(outward.z,0,-outward.x);
        var up=normal==Direction.DOWN?new Vec3(0,0,1):new Vec3(0,1,0);
        var end=Vec3.atCenterOf(pos).add(right.scale(tip.x)).add(up.scale(tip.y));
        var hit=boss.level().clip(new net.minecraft.world.level.ClipContext(end.add(outward.scale(1.8)),end.subtract(outward.scale(3)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,boss));
        double z=hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?hit.getLocation().subtract(end).dot(outward)+.07:-1.5;
        return tilt[socket][root]=(float)Mth.clamp(Math.atan2(z-tip.z,Math.hypot(tip.x-pivot.x,tip.y-pivot.y)),-.57,.57);
    }

    void draw(EncounterMob boss,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        var state=boss.spiderData();long[] anchors=state.getLongArray("DenNests");
        if(anchors.length!=6)return;
        var level=boss.level();var origin=boss.getPosition(partial);
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        for(int i=0;i<6;i++){
            var pos=BlockPos.of(anchors[i]);var center=Vec3.atCenterOf(pos);
            if(center.distanceToSqr(camera)>72*72 || !level.hasChunkAt(pos) || !level.getBlockState(pos).is(ModBlocks.TISSUE_ATTACHMENT.get()))continue;
            var outward=GreeneHiveCave.support(i).getOpposite();
            boolean selected=state.contains("HatchStart") && state.getInt("HatchSlot")==i;
            float age=selected?level.getGameTime()-state.getLong("HatchStart")+partial:99;
            boolean incubating=selected && state.contains("HatchDue");
            boolean released=selected && state.contains("HatchedAt") && state.getLong("HatchedAt")>=state.getLong("HatchStart");
            float idle=(Math.floorMod(level.getGameTime()+i*17L,80)+partial)%80;
            float hatch=(incubating || released) && age>=0 && age<36?age:-1;
            var at=center.subtract(origin);
            if(incubating && age>=6 && camera.distanceToSqr(center)<54*54){
                int breed=state.getInt("HatchBreed");var occupant=state.getCompound("HatchChild");
                Mob child=preview.get();
                if(child==null || child.level()!=level || child.getType()!=SpiderActions.broodType(breed)
                        || occupant.hasUUID("UUID") && !occupant.getUUID("UUID").equals(child.getUUID())){
                    child=SpiderActions.broodType(breed).create(level);
                    if(child!=null && !occupant.isEmpty())child.load(occupant);
                    preview=new java.lang.ref.WeakReference<>(child);
                }
                if(child!=null){
                    var emergence=smooth(Mth.clamp((age-6)/12,0,1));
                    var exit=Vec3.atBottomCenterOf(pos.relative(outward,3));
                    var location=center.add(0,-child.getBbHeight()*.44,0).lerp(exit,emergence);
                    float yaw=outward.getAxis().isVertical()?0:outward.toYRot();
                    child.setPos(location);child.tickCount=boss.tickCount;child.setYRot(yaw);child.yBodyRot=child.yBodyRotO=yaw;
                    child.yHeadRot=child.yHeadRotO=yaw;
                    var relative=location.subtract(origin);
                    Minecraft.getInstance().getEntityRenderDispatcher().render(child,relative.x,relative.y,relative.z,yaw,partial,stack,buffers,light);
                }
            }
            stack.pushPose();stack.translate(at.x,at.y,at.z);
            if(outward==Direction.DOWN)stack.mulPose(Axis.XP.rotationDegrees(90));
            else stack.mulPose(Axis.YP.rotationDegrees(-outward.toYRot()));
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
            final int socket=i;
            GreeneNestModel.draw(stack,out,light,idle,hatch,(index,pivot,tip)->surfaceAngle(boss,socket,index,pos,outward,pivot,tip));
            stack.popPose();
        }
    }

}
