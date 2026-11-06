package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Client-only shockwave: surface blocks pop up in a ring that travels out from the impact.
 * Copies are drawn over the real terrain; the world itself is never edited. */
public final class TerrainRipple {
    private record Cell(BlockPos pos,BlockState state,double distance,float jitter,float yaw){}
    private record Wave(ClientLevel level,Vec3 centre,double radius,float height,double speed,long started,List<Cell> cells){}
    private static final List<Wave> WAVES=new ArrayList<>();
    private static final int MAX_CELLS=900,MAX_WAVES=6;
    private static final double LIFT_TICKS=5;
    private static final net.minecraft.util.RandomSource RANDOM=net.minecraft.util.RandomSource.create();
    private TerrainRipple(){}

    /** strength 0..1: 0.15 light ground hit, ~0.4 heavy slam, 1 bulletdive. */
    public static void accept(double x,double y,double z,double radius,float strength){
        var mc=Minecraft.getInstance();var level=mc.level;if(level==null||!Double.isFinite(x+y+z+radius)||radius<=.5)return;
        radius=Math.min(radius,16);strength=Math.max(0,Math.min(1,strength));
        var centre=new Vec3(x,y,z);var cells=new ArrayList<Cell>();int r=(int)Math.ceil(radius);
        var rng=new Random(Double.doubleToLongBits(x)*31+Double.doubleToLongBits(z));
        BlockPos.MutableBlockPos at=new BlockPos.MutableBlockPos();
        for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            double distance=Math.sqrt((dx+.5-(x-Math.floor(x)))*(dx+.5-(x-Math.floor(x)))+(dz+.5-(z-Math.floor(z)))*(dz+.5-(z-Math.floor(z))));
            if(distance>radius)continue;
            int bx=(int)Math.floor(x)+dx,bz=(int)Math.floor(z)+dz;
            for(int dy=2;dy>=-4;dy--){
                at.set(bx,(int)Math.floor(y-.01)+dy,bz);var state=level.getBlockState(at);
                if(state.isAir()||state.getRenderShape()!=RenderShape.MODEL||!state.getFluidState().isEmpty())continue;
                var above=at.above();
                if(!level.getBlockState(above).getCollisionShape(level,above).isEmpty())break;
                if(state.getCollisionShape(level,at).isEmpty())continue;
                cells.add(new Cell(at.immutable(),state,distance,.75F+.5F*rng.nextFloat(),(rng.nextFloat()-.5F)*.35F));break;
            }
        }
        if(cells.isEmpty())return;
        if(cells.size()>MAX_CELLS){cells.sort(Comparator.comparingDouble(Cell::distance));cells.subList(MAX_CELLS,cells.size()).clear();}
        if(WAVES.size()>=MAX_WAVES)WAVES.remove(0);
        float height=.18F+.72F*strength;double speed=.8+.35*strength;
        WAVES.add(new Wave(level,centre,radius,height,speed,level.getGameTime(),List.copyOf(cells)));
    }

    public static void render(PoseStack stack,Vec3 camera,double time){
        var mc=Minecraft.getInstance();var level=mc.level;if(level==null){WAVES.clear();return;}
        WAVES.removeIf(w->w.level!=level||time-w.started>w.radius/w.speed+LIFT_TICKS+2);
        if(WAVES.isEmpty())return;
        var dispatcher=mc.getBlockRenderer();var buffers=mc.renderBuffers().bufferSource();
        stack.pushPose();stack.translate(-camera.x,-camera.y,-camera.z);
        for(var w:WAVES){
            double age=time-w.started;
            for(var c:w.cells){
                double local=age-c.distance/w.speed;if(local<=0||local>=LIFT_TICKS)continue;
                if(level.getBlockState(c.pos)!=c.state)continue; // Destroyed by the crater: do not draw a ghost.
                double phase=local/LIFT_TICKS;
                // Sharp rise, heavier fall with a small rebound.
                double lift=Math.sin(Math.PI*Math.pow(phase,.6))*(1-.25*phase);
                double falloff=Math.pow(Math.max(0,1-c.distance/w.radius),.55);
                float h=(float)(w.height*falloff*c.jitter*lift);if(h<.015F)continue;
                var out=new Vec3(c.pos.getX()+.5-w.centre.x,0,c.pos.getZ()+.5-w.centre.z);
                double len=out.length();if(len>1e-4)out=out.scale(1/len);
                float tilt=(float)(h*.55*Math.min(1,len));
                stack.pushPose();
                stack.translate(c.pos.getX()+.5,c.pos.getY()+h,c.pos.getZ()+.5);
                // Tip the outer edge up, as if the ground buckled from the centre.
                stack.mulPose(com.mojang.math.Axis.of(new org.joml.Vector3f((float)out.z,0,(float)-out.x)).rotation(-tilt));
                stack.mulPose(com.mojang.math.Axis.YP.rotation(c.yaw*h));
                stack.scale(1.002F,1.002F,1.002F);
                stack.translate(-.5,0,-.5);
                // Batched model keeps biome tint and terrain lighting of the real block.
                dispatcher.renderBatched(c.state,c.pos,level,stack,buffers.getBuffer(net.minecraft.client.renderer.ItemBlockRenderTypes.getChunkRenderType(c.state)),false,RANDOM);
                stack.popPose();
            }
        }
        buffers.endBatch();
        stack.popPose();
    }
}
