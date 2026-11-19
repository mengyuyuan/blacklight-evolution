package com.codex.blacklight.client.renderer;

import com.codex.blacklight.registry.ModBlockEntities;
import com.codex.blacklight.world.BossSiteBlock;
import com.codex.blacklight.world.PumpStation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** One block entity owns the pump, armored tender and the pressure travelling down its actual hose. */
@EventBusSubscriber(modid="blacklight",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class PumpTruckRenderer implements BlockEntityRenderer<PumpStation> {
    private static final ResourceLocation ATLAS=ResourceLocation.fromNamespaceAndPath("blacklight","textures/block/bloodtox_tender.png");
    private static final PumpTruckModel.Mesh DIAL=new PumpTruckModel.Mesh();
    static { DIAL.box(PumpTruckModel.DIAL,-.177,-.177,-.01,.177,.177,.01); }

    public PumpTruckRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PUMP.get(),PumpTruckRenderer::new);
    }

    @Override public void render(PumpStation pump,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(pump.getLevel()==null || !pump.getBlockState().getValue(BossSiteBlock.MOTHER)) return;
        var facing=pump.getBlockState().getValue(BossSiteBlock.FACING);
        var panel=pump.getBlockPos().relative(facing);
        int panelLight=LevelRenderer.getLightColor(pump.getLevel(),panel);
        long clock=pump.getLevel().getGameTime();
        double age=pump.running()?Math.max(0,clock-pump.startedAt()+partial):0;
        double pressure=pump.running()?Math.min(age/30,1):0;
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ATLAS));
        pose.pushPose();
        pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(-pump.getBlockState().getValue(BossSiteBlock.FACING).toYRot()));
        pose.pushPose();
        pose.translate(-.178,.77,.351);
        draw(DIAL,pose,out,panelLight,overlay);
        pose.translate(0,0,.014);
        pose.mulPose(Axis.ZP.rotationDegrees((float)(88-pressure*166)));
        draw(PumpTruckModel.NEEDLE,pose,out,panelLight,overlay);
        pose.popPose();
        pose.pushPose();
        pose.translate(.18,.70,.356);
        pose.mulPose(Axis.XP.rotationDegrees((float)(22+pressure*59)));
        draw(PumpTruckModel.HANDLE,pose,out,panelLight,overlay);
        pose.popPose();
        if(pump.hasRig()) {
            double hit=pump.dent()==0?0:Math.max(0,clock-pump.impactAt()+partial);
            double dent=pump.dent()==2?Math.min(1,hit/4):0;
            double shock=pump.dent()==0?0:Math.sin(hit*.67)*Math.exp(-hit*.16);
            int rigLight=lightAt(pump,facing,-2.5,1.55,-1.5);
            draw(PumpTruckModel.WHEELS,pose,out,rigLight,overlay);
            pose.pushPose();
            pose.translate(0,-.11*dent+.065*shock,0);
            pose.mulPose(Axis.XP.rotationDegrees((float)(-3.0*dent+shock*2.7)));
            draw(PumpTruckModel.BODY,pose,out,rigLight,overlay);
            if(pump.dent()==1) draw(PumpTruckModel.SCAR,pose,out,rigLight,overlay);
            if(pump.dent()!=2) draw(PumpTruckModel.MIRROR,pose,out,rigLight,overlay);
            pose.popPose();
            if(pump.dent()==2) {
                var pivot=PumpTruckModel.MIRROR_PIVOT;
                double t=Math.min(1,hit/11);
                pose.pushPose();
                pose.translate(pivot.x+.34*t,pivot.y-1.86*t*t,pivot.z+.86*t);
                pose.mulPose(Axis.ZP.rotationDegrees((float)(-137*t)));
                pose.translate(-pivot.x,-pivot.y,-pivot.z);
                draw(PumpTruckModel.MIRROR,pose,out,rigLight,overlay);
                pose.popPose();
            }
            for(int i=0;i<PumpTruckModel.LINE.length;i++) {
                double t=i/(double)PumpTruckModel.LINE.length;
                double front=Mth.clamp(age-10-39*t,0,12)/12;
                double pulse=Math.sin(front*Math.PI);
                double lash=pump.dent()==2?Math.sin(hit*.65-t*11)*Math.exp(-hit*.095)*Math.sin(t*Math.PI):0;
                pose.pushPose();
                pose.translate(-pressure*t*(1-t)*.49+lash*.55,pulse*.19+Math.abs(lash)*.44,0);
                var point=PumpTruckModel.linePoint((i+.5)/PumpTruckModel.LINE.length);
                int hoseLight=lightAt(pump,facing,point.x,point.y+.15,point.z);
                draw(PumpTruckModel.LINE[i],pose,out,hoseLight,overlay);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    // A field rig spans a dozen blocks. The dispatcher's light comes from inside its opaque console.
    private static int lightAt(PumpStation pump,net.minecraft.core.Direction facing,double x,double y,double z){
        var side=facing.getCounterClockWise();
        var origin=pump.getBlockPos();
        var sample=net.minecraft.core.BlockPos.containing(origin.getX()+.5+side.getStepX()*x+facing.getStepX()*z,
                origin.getY()+y,origin.getZ()+.5+side.getStepZ()*x+facing.getStepZ()*z);
        return LevelRenderer.getLightColor(pump.getLevel(),sample);
    }

    private static void draw(PumpTruckModel.Mesh mesh,PoseStack stack,VertexConsumer out,int light,int overlay) {
        var pose=stack.last();
        for(var f:mesh.faces) {
            float u=(f.material()%4)*.25F+.001F,v=(f.material()/4)*.25F+.001F;
            vertex(out,pose,f.a(),f.n(),u,v+.248F,light,overlay);
            vertex(out,pose,f.b(),f.n(),u+.248F,v+.248F,light,overlay);
            vertex(out,pose,f.c(),f.n(),u+.248F,v,light,overlay);
            vertex(out,pose,f.d(),f.n(),u,v,light,overlay);
        }
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,Vec3 p,Vec3 n,float u,float v,int light,int overlay) {
        out.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(255,255,255,255).setUv(u,v).setOverlay(overlay).setLight(light).setNormal(pose,(float)n.x,(float)n.y,(float)n.z);
    }
    @Override public int getViewDistance() { return 96; }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(PumpStation pump) { return new net.minecraft.world.phys.AABB(pump.getBlockPos()).inflate(pump.hasRig()?13:1); }
}
