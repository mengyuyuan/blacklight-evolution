package com.codex.blacklight.client.renderer;

import com.codex.blacklight.infection.BuildingBiomassField;
import com.codex.blacklight.registry.ModBlockEntities;
import com.codex.blacklight.world.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** Coherent lobed tissue grown around the root, with a firm attachment and a slow double beat. */
@EventBusSubscriber(modid="blacklight",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BuildingHeartRenderer implements BlockEntityRenderer<BuildingHeartBlockEntity> {
    private record Face(BuildingBiomassField.Triangle triangle,boolean skin) {}
    private static final ResourceLocation FLESH=new ResourceLocation("blacklight","textures/block/hive_sac.png");
    private static final ResourceLocation SKIN=new ResourceLocation("blacklight","textures/block/hive_flesh.png");
    private static final List<Face> BODY=body();
    public BuildingHeartRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.BUILDING_HEART.get(),BuildingHeartRenderer::new);
    }
    private static double ellipsoid(double x,double y,double z,double cx,double cy,double cz,double rx,double ry,double rz) {
        return Math.pow((x-cx)/rx,2)+Math.pow((y-cy)/ry,2)+Math.pow((z-cz)/rz,2)-1;
    }
    private static double density(double x,double y,double z) {
        return Math.min(ellipsoid(x,y,z,-.24,.80,.36,.65,1.05,.53),
                Math.min(ellipsoid(x,y,z,.35,.56,.38,.52,.74,.48),ellipsoid(x,y,z,0,.14,-.03,.40,.49,.40)));
    }
    private static List<Face> body() {
        var result=new ArrayList<Face>();double step=.25;
        for(int x=-4;x<4;x++)for(int y=-2;y<8;y++)for(int z=-2;z<5;z++) {
            int mask=0;
            for(int corner=0;corner<8;corner++)if(density((x+(corner&1))*step,(y+((corner>>1)&1))*step,(z+((corner>>2)&1))*step)<=0)mask|=1<<corner;
            for(var face:BuildingBiomassField.mesh(mask)) {
                var offset=new BuildingBiomassField.Point(x,y,z);
                var a=face.a().add(offset).mul(step);var b=face.b().add(offset).mul(step);var c=face.c().add(offset).mul(step);
                double angle=Math.atan2(a.x(),a.z()-.25);
                result.add(new Face(new BuildingBiomassField.Triangle(a,b,c,face.normal()),Math.sin(angle*4+a.y()*1.2)>.5));
            }
        }
        return List.copyOf(result);
    }
    @Override public void render(BuildingHeartBlockEntity entity,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        var level=entity.getLevel();var state=entity.getBlockState();
        if(level==null||!state.hasProperty(TissueAttachmentBlock.AGE))return;
        int age=state.getValue(TissueAttachmentBlock.AGE);if(age==0)return;
        // Reduce the integer clock before conversion so a long-running server never freezes the pulse.
        double phase=(Math.floorMod(level.getGameTime()+Math.floorMod(entity.getBlockPos().asLong(),43),43)+partial)/43;
        double beat=Math.exp(-Math.pow((phase-.20)/.085,2))+.5*Math.exp(-Math.pow((phase-.39)/.07,2));
        float size=age==1?.42F:age==2?.78F:1.16F;
        poses.pushPose();poses.translate(.5,.20,.5);poses.mulPose(Axis.YP.rotationDegrees(-state.getValue(BuildingInfectionCoreBlock.FACING).toYRot()));
        poses.scale(size*(float)(1-beat*.018),size*(float)(1+beat*.045),size*(float)(1+beat*.035));
        var pose=poses.last();
        for(int material=0;material<2;material++) {
        boolean skin=material==1;
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(skin?SKIN:FLESH));
        for(var face:BODY) {
            if(face.skin()!=skin)continue;
            var triangle=face.triangle();var normal=triangle.normal();
            for(int vertex=0;vertex<4;vertex++) {
                var p=vertex==0?triangle.a():vertex==1?triangle.b():triangle.c();
                out.vertex(pose.pose(),(float)p.x(),(float)p.y(),(float)p.z()).color(255,255,255,255)
                        .uv((float)(p.x()*.35+.5),(float)(1-(p.y()+.5)/2.5)).overlayCoords(overlay).uv2(light)
                        .normal(pose.normal(),(float)normal.x(),(float)normal.y(),(float)normal.z()).endVertex();
            }
        }}
        poses.popPose();
    }
    @Override public int getViewDistance() { return 64; }
}
