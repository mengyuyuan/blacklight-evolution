package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/** Cached authored models use the liquid fragment shader with per-instance pose/light.
 * Dynamic tissue and native player arms retain their original material path. */
@EventBusSubscriber(modid="blacklight",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class RigidBiomassMaterial extends RenderType {
    private static final Logger LOGGER=LogUtils.getLogger();
    private static ShaderInstance shader;
    private static Uniform worldPose,worldNormal,instanceLight,instanceAlpha;
    private static Uniform curveMode,curveB,curveC,curveReference,curveMetrics,curveTwist;
    record Curve(float bx,float by,float bz,float cx,float cy,float cz,float referenceX,float referenceZ,
                 float minY,float sourceLength,float length,float width,float cosine,float sine){
        static Curve of(Vec3 root,Vec3 control,Vec3 tip,float minY,double sourceLength,double length,double width,double twist){
            double hx=tip.x-root.x,hz=tip.z-root.z,hl=Math.sqrt(hx*hx+hz*hz);
            return new Curve((float)(control.x-root.x),(float)(control.y-root.y),(float)(control.z-root.z),
                    (float)hx,(float)(tip.y-root.y),(float)hz,hl<.00001?1:(float)(-hz/hl),hl<.00001?0:(float)(hx/hl),
                    minY,(float)sourceLength,(float)length,(float)width,(float)Math.cos(twist),(float)Math.sin(twist));
        }
    }
    private static final Function<ResourceLocation,RenderType> MESH=Util.memoize(texture->create(
            "blacklight_rigid_biomass",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.TRIANGLES,
            1536,false,false,CompositeState.builder()
                    .setShaderState(new ShaderStateShard(()->shader))
                    .setTextureState(new TextureStateShard(texture,false,false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                    .createCompositeState(false)));
    private RigidBiomassMaterial(){
        super("blacklight_rigid_biomass_factory",DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.TRIANGLES,1536,false,false,()->{},()->{});
    }
    @SubscribeEvent public static void register(RegisterShadersEvent event){
        shader=null;worldPose=worldNormal=instanceLight=instanceAlpha=null;
        curveMode=curveB=curveC=curveReference=curveMetrics=curveTwist=null;
        try{
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath("blacklight","rigid_biomass"),DefaultVertexFormat.NEW_ENTITY),loaded->{
                worldPose=loaded.getUniform("WorldPose");worldNormal=loaded.getUniform("WorldNormal");
                instanceLight=loaded.getUniform("InstanceLight");instanceAlpha=loaded.getUniform("InstanceAlpha");
                curveMode=loaded.getUniform("CurveMode");curveB=loaded.getUniform("CurveB");curveC=loaded.getUniform("CurveC");
                curveReference=loaded.getUniform("CurveReference");curveMetrics=loaded.getUniform("CurveMetrics");curveTwist=loaded.getUniform("CurveTwist");
                if(worldPose==null||worldNormal==null||instanceLight==null||instanceAlpha==null||curveMode==null
                        ||curveB==null||curveC==null||curveReference==null||curveMetrics==null||curveTwist==null){
                    LOGGER.error("Rigid biomass instance uniforms are missing; using the dynamic model path");
                    return;
                }
                shader=loaded;
            });
        }catch(IOException error){LOGGER.error("Could not load rigid biomass material; using the dynamic model path",error);}
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((ResourceManagerReloadListener)manager->
                Minecraft.getInstance().execute(AuthoredDevastatorMesh::clearRigidBuffers));
    }
    public static boolean ready(){return shader!=null;}
    static ShaderInstance shader(){return shader;}
    static RenderType mesh(ResourceLocation texture){return MESH.apply(texture);}
    static void instance(Matrix4f pose,Matrix3f normal,int light,int alpha,Curve curve){
        worldPose.set(pose);worldNormal.set(normal);
        instanceLight.set((float)(light&65535),(float)(light>>>16&65535));
        instanceAlpha.set(Math.clamp(alpha,0,255)/255F);
        curveMode.set(curve==null?0F:1F);
        if(curve!=null){
            curveB.set(curve.bx,curve.by,curve.bz);curveC.set(curve.cx,curve.cy,curve.cz);
            curveReference.set(curve.referenceX,0,curve.referenceZ);
            curveMetrics.set(curve.minY,curve.sourceLength,curve.length,curve.width);
            curveTwist.set(curve.cosine,curve.sine);
        }
    }
    @EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
    public static final class WorldLifecycle {
        private static Object level;
        @SubscribeEvent public static void tick(ClientTickEvent.Post event){
            var current=Minecraft.getInstance().level;
            if(current!=level){AuthoredDevastatorMesh.clearRigidBuffers();level=current;}
        }
    }
}
