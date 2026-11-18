package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import java.util.*;

/** The same short ballistic block fragments, born on the displayed contact frame.
 * Fixed game-tick integration, bounded count and terrain collision; no persistent entities. */
public final class ContactTerrainDebris {
    private static final List<Piece> PIECES=new ArrayList<>();
    private static final class Piece {
        final BlockState state;final float size;final int life;final double born;
        Vec3 at,visible,velocity;int step;
        Piece(BlockState state,Vec3 at,Vec3 velocity,float size,int life,double born){this.state=state;this.at=this.visible=at;this.velocity=velocity;this.size=size;this.life=life;this.born=born;}
    }
    private ContactTerrainDebris(){}
    public static int activeCount(){return PIECES.size();}
    public static void clear(){PIECES.clear();}
    public static void spawn(BlockState state,Vec3 at,Vec3 velocity,float size,int life,double born){
        if(PIECES.size()<64)PIECES.add(new Piece(state,at,velocity,size,life,born));
    }
    public static void render(PoseStack pose,Vec3 camera,double time){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null){clear();return;}
        PIECES.removeIf(p->{
            double age=time-p.born;if(age<0)return true;
            if(age>=p.life){shatter(p,p.at);return true;}
            while(p.step<(int)Math.floor(age)){
                p.velocity=p.velocity.add(0,-.04,0);var next=p.at.add(p.velocity);
                var hit=mc.level.clip(new ClipContext(p.at,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
                if(hit.getType()!=HitResult.Type.MISS){shatter(p,hit.getLocation());return true;}
                p.at=next;p.step++;
            }
            p.visible=p.at.add(p.velocity.add(0,-.04,0).scale(age-Math.floor(age)));
            var partialHit=mc.level.clip(new ClipContext(p.at,p.visible,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
            if(partialHit.getType()!=HitResult.Type.MISS){shatter(p,partialHit.getLocation());return true;}
            return false;
        });
        if(PIECES.isEmpty())return;
        var buffers=mc.renderBuffers().bufferSource();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        for(var p:PIECES){
            double age=time-p.born;var at=p.visible;
            pose.pushPose();pose.translate(at.x,at.y,at.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float)age*19));pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees((float)age*11));
            pose.scale(p.size,p.size,p.size);pose.translate(-.5,-.5,-.5);
            mc.getBlockRenderer().renderSingleBlock(p.state,pose,buffers,LevelRenderer.getLightColor(mc.level,BlockPos.containing(at)),OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        buffers.endBatch();pose.popPose();
    }
    private static void shatter(Piece piece,Vec3 at){
        var level=Minecraft.getInstance().level;if(level==null)return;
        for(int i=0;i<6;i++)level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,piece.state),at.x,at.y,at.z,(level.random.nextDouble()-.5)*.12,.06,(level.random.nextDouble()-.5)*.12);
        for(int i=0;i<2;i++)level.addParticle(ParticleTypes.CLOUD,at.x,at.y,at.z,0,.015,0);
    }
}
