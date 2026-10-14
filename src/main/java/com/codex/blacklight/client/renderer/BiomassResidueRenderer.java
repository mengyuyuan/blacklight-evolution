package com.codex.blacklight.client.renderer;

import com.codex.blacklight.power.BiomassEvolution;
import com.codex.blacklight.power.BiomassState;
import com.codex.blacklight.power.BiomassSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-anchored wet pixels remain briefly after the living tail has withdrawn. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class BiomassResidueRenderer {
    private static final double CELL=1D/16;
    private static final BiomassResidueField RESIDUE=new BiomassResidueField();
    private static final Map<UUID,Vec3> LAST=new HashMap<>();
    private static ClientLevel level;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(level!=mc.level){RESIDUE.clear();LAST.clear();level=mc.level;}
        if(level==null)return;
        long now=level.getGameTime();RESIDUE.expire(now);
        LAST.keySet().removeIf(id->level.getPlayerByUUID(id)==null);
        for(Player player:level.players()){
            if(BiomassSystem.state(player).phase()!=BiomassState.CRAWLING||!player.onGround()
                    ||mc.player==null||player.distanceToSqr(mc.player)>4096){LAST.remove(player.getUUID());continue;}
            Vec3 current=player.position(),previous=LAST.get(player.getUUID());float size=BiomassEvolution.scale(player);
            if(previous==null){LAST.put(player.getUUID(),current);continue;}
            double distance=current.distanceTo(previous);
            if(distance<.14*size)continue;
            LAST.put(player.getUUID(),current);
            if(distance>3||Math.abs(current.y-previous.y)>.5)continue;
            int count=Math.min(12,Math.max(1,(int)Math.ceil(distance/(.14*size))));
            for(int i=1;i<=count;i++)deposit(player,previous.lerp(current,i/(double)count),size,now);
        }
        // Removed support blocks cannot leave floating stains.
        RESIDUE.marks().removeIf(mark->{var at=BlockPos.containing((mark.key().x()+.5)*CELL,mark.y()-.01,(mark.key().z()+.5)*CELL);
            return !level.hasChunkAt(at)||level.getBlockState(at).getCollisionShape(level,at).isEmpty();});
    }
    private static void deposit(Player player,Vec3 at,float size,long now){
        double radius=.20*size;
        for(int x=(int)Math.floor((at.x-radius)/CELL);x<=(int)Math.floor((at.x+radius)/CELL);x++)
            for(int z=(int)Math.floor((at.z-radius)/CELL);z<=(int)Math.floor((at.z+radius)/CELL);z++){
                double wx=(x+.5)*CELL,wz=(z+.5)*CELL,dx=wx-at.x,dz=wz-at.z;
                int hash=x*73428767^z*912931;
                if(dx*dx+dz*dz>radius*radius*(.8+.2*((hash>>>3)&7)/7D))continue;
                var block=BlockPos.containing(wx,at.y-.05,wz);if(!level.hasChunkAt(block))continue;
                var hit=level.clip(new ClipContext(new Vec3(wx,at.y+.25,wz),new Vec3(wx,at.y-.45,wz),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
                if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=Direction.UP)continue;
                int rgb=Math.floorMod(x+z*3,17)==0?0x592c37:(hash&3)==0?0x373039:0x231e26;
                RESIDUE.put(x,z,hit.getLocation().y(),now,LevelRenderer.getLightColor(level,hit.getBlockPos().above()),rgb);
            }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||RESIDUE.marks().isEmpty())return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.level!=level)return;
        double now=level.getGameTime()+event.getPartialTick();
        var stack=event.getPoseStack();var camera=event.getCamera().getPosition();
        var type=RenderType.entityTranslucent(ConsumedMobVisuals.WHITE);var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(type);
        stack.pushPose();stack.translate(-camera.x,-camera.y,-camera.z);
        try{
            for(var mark:RESIDUE.marks()){
                double remaining=mark.remaining(now);if(remaining<=0)continue;
                double x=mark.key().x()*CELL,z=mark.key().z()*CELL;
                if(camera.distanceToSqr(new Vec3(x,mark.y(),z))>4096)continue;
                double h=.005+.016*remaining,inset=(1-remaining)*CELL*.35;
                int argb=((int)(195*remaining)<<24)|mark.rgb();
                BiomassGeometry.box(stack,out,mark.light(),x+inset,mark.y()+.004,z+inset,
                        x+CELL-inset,mark.y()+h,z+CELL-inset,argb);
            }
        }finally{stack.popPose();}
        buffers.endBatch(type);
    }
}
