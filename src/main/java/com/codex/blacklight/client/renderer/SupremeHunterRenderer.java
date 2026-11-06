package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.SupremeHunterBlockRig;
import com.codex.blacklight.combat.SupremeHunterMove;
import com.codex.blacklight.entity.BlacklightSupremeHunter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Blocky Supreme Hunter. Every cuboid is rigid on one joint of the original skeleton, so
 * elbows, knees, spine and claws hinge like a Minecraft model instead of stretching. */
public final class SupremeHunterRenderer extends EntityRenderer<BlacklightSupremeHunter> {
    private static final ResourceLocation SKIN=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/supreme_hunter_block.png");
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/claw_white.png");
    private static final class State {String clip;float since;float[] from,last;double walked;Vec3 lastPos;float lastClock;}
    private final java.util.Map<BlacklightSupremeHunter,State> states=new java.util.WeakHashMap<>();
    public SupremeHunterRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=1.75F;}
    @Override public ResourceLocation getTextureLocation(BlacklightSupremeHunter e){return SKIN;}

    private String clip(BlacklightSupremeHunter e,State s,float partial,float[] time){
        var rig=SupremeHunterBlockRig.get();
        if(e.deathTime>0||!e.isAlive()){time[0]=e.deathTime+partial;return "death";}
        if(e.isBeingConsumed()){time[0]=1e4F;return "leg_collapse";}
        var move=e.move();
        if(move!=SupremeHunterMove.NONE&&rig.has(move.clip)){time[0]=e.moveAge(partial);return move.clip;}
        if(e.weakened()){time[0]=1e4F;return "leg_collapse";}
        if(!e.onGround()&&e.getDeltaMovement().y<-.25){time[0]=e.tickCount+partial;return "fall";}
        double speed=e.getDeltaMovement().horizontalDistance();
        // Stride phase follows travelled distance so feet do not skate: source walk covers
        // 4.1 blocks per cycle, the sprint 12 blocks.
        if(speed>.03){boolean run=speed>.2;String key=run?"run":"walk";float stride=run?12F:4.1F;
            time[0]=(float)(s.walked/stride*rig.seconds(key)*20);return key;}
        time[0]=e.tickCount+partial;return "idle";
    }
    @Override public void render(BlacklightSupremeHunter e,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        var visual=e.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL);
        float consumeT=visual.active()?visual.progress(e.level().getGameTime(),partial):0;
        if(consumeT<.995F)draw(e,partial,stack,buffers,light);
        var owner=e.level().getEntity(e.consumerId());
        if(owner!=null&&visual.active()){
            Vec3 delta=owner.getPosition(partial).subtract(e.getPosition(partial));
            ConsumedMobVisuals.whip(e,stack,buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)),light,delta,ConsumedMobVisuals.tetherProgress(e,partial));
        }
        super.render(e,yaw,partial,stack,buffers,light);
    }
    private void draw(BlacklightSupremeHunter e,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        var rig=SupremeHunterBlockRig.get();var s=states.computeIfAbsent(e,k->new State());
        Vec3 pos=e.getPosition(partial);if(s.lastPos!=null)s.walked+=Math.min(2,Math.hypot(pos.x-s.lastPos.x,pos.z-s.lastPos.z));s.lastPos=pos;
        float clock=e.tickCount+partial;float[] time={0};String key=clip(e,s,partial,time);
        if(!key.equals(s.clip)){s.from=s.last;s.clip=key;s.since=clock;}
        float[] row=rig.sample(key,time[0]);
        float fade=Math.clamp((clock-s.since)/4F,0,1);row=SupremeHunterBlockRig.blend(s.from,row,fade*fade*(3-2*fade));s.last=row;
        int n=rig.names.length;var rot=new Quaternionf[n];var at=new Vector3f[n];
        for(int i=0;i<n;i++){int o=i*4;rot[i]=new Quaternionf(row[o],row[o+1],row[o+2],row[o+3]);
            int p=rig.parents[i];at[i]=p<0?new Vector3f(rig.pivots[i]).add(row[n*4],row[n*4+1],row[n*4+2])
                :rot[p].transform(new Vector3f(rig.pivots[i]).sub(rig.pivots[p])).add(at[p]);}
        stack.pushPose();
        float bodyYaw=Mth.rotLerp(partial,e.yBodyRotO,e.yBodyRot);
        stack.mulPose(Axis.YP.rotationDegrees(180-bodyYaw));float px=BlacklightSupremeHunter.SIZE/16F;stack.scale(px,px,px);
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));var pose=stack.last();
        int overlay=OverlayTexture.pack(0,e.hurtTime>0||e.deathTime>0);
        int color=e.weakened()||e.isBeingConsumed()?0xFFFFB0A8:e.phase()>=3?0xFFE8A098:0xFFFFFFFF;
        var v=new Vector3f();var normal=new Vector3f();
        for(var q:rig.quads){int b=q.bone();rot[b].transform(normal.set(q.nx(),q.ny(),q.nz()));
            for(int k=0;k<4;k++){v.set(q.xyz()[k*3],q.xyz()[k*3+1],q.xyz()[k*3+2]).sub(rig.pivots[b]);rot[b].transform(v).add(at[b]);
                out.addVertex(pose,v.x,v.y,v.z).setColor(color).setUv(q.uv()[k*2],q.uv()[k*2+1]).setOverlay(overlay).setLight(light).setNormal(pose,normal.x,normal.y,normal.z);}
        }
        stack.popPose();
        if(e.move()==SupremeHunterMove.DEVASTATOR){
            // Launch roots for the Thousand Tendrils barrage, in world space.
            var turn=new Quaternionf().rotationY((float)Math.toRadians(180-bodyYaw));
            java.util.function.IntFunction<Vec3> world=i->{var w=turn.transform(new Vector3f(at[i]).mul(BlacklightSupremeHunter.SIZE/16F));return pos.add(w.x,w.y,w.z);};
            int chest=index(rig,"Spine_3"),belly=index(rig,"Spine_2");
            var c=world.apply(chest);
            var right=turn.transform(new Vector3f(1,0,0));var fwd=turn.transform(new Vector3f(0,0,-1));
            DevastatorEffectRenderer.captureCreature(e.getId(),c.add(0,.4,0),world.apply(belly),c.add(right.x,right.y,right.z),c.subtract(right.x,right.y,right.z),new Vec3(fwd.x,fwd.y,fwd.z),
                    world.apply(index(rig,"Shoulder_R")),world.apply(index(rig,"Shoulder_L")),world.apply(index(rig,"Wrist_R")),world.apply(index(rig,"Wrist_L")));
        }
    }
    private static int index(SupremeHunterBlockRig rig,String name){for(int i=0;i<rig.names.length;i++)if(rig.names[i].equals(name))return i;throw new IllegalArgumentException(name);}
}
