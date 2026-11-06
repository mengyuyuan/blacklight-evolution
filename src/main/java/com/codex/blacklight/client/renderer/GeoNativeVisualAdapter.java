package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.renderer.*;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.event.GeoRenderEvent;
import software.bernie.geckolib.animatable.GeoAnimatable;
import java.util.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** One framework adapter covers all normal GeoEntityRenderer hosts. No entity IDs,
 * bone tracking flags, controller replacement or modified source mesh is required. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class GeoNativeVisualAdapter implements NativeVisualAdapter {
    private record Cached(java.lang.ref.WeakReference<software.bernie.geckolib.cache.object.BakedGeoModel> source,GeoNativeGeometry geometry){}
    private static final Map<ResourceLocation,Cached> CACHE=new LinkedHashMap<>(16,.75F,true){
        @Override protected boolean removeEldestEntry(Map.Entry<ResourceLocation,Cached> eldest){return size()>64;}
    };
    private static final class Capture {
        final Mob entity;final GeoNativeGeometry geometry;int rendered;
        Capture(Mob entity,GeoNativeGeometry geometry){this.entity=entity;this.geometry=geometry;}
    }
    public record FrameStatistics(int joints,int cubes,int capturedJoints){}
    private static final Map<Mob,FrameStatistics> LAST=new WeakHashMap<>();
    public static Optional<FrameStatistics> lastFrame(Mob entity){return Optional.ofNullable(LAST.get(entity));}
    public static boolean frameActive(){return ACTIVE.get()!=null;}
    private static final ThreadLocal<Capture> ACTIVE=new ThreadLocal<>();
    @Override public ResourceLocation id(){return ResourceLocation.fromNamespaceAndPath("blacklight","geckolib");}
    @Override @SuppressWarnings({"rawtypes","unchecked"})
    public Binding bind(Mob entity,EntityRenderer<?> renderer,PoseStack stack,float partial){
        if(!(renderer instanceof GeoEntityRenderer geo)||!(entity instanceof GeoAnimatable))return null;
        var model=geo.getGeoModel();var resource=model.getModelResource((GeoAnimatable)entity,geo);
        if(resource==null)return null;
        var baked=model.getBakedModel(resource);if(baked==null)return null;
        var entry=CACHE.get(resource);
        if(entry==null||entry.source().get()!=baked){entry=new Cached(new java.lang.ref.WeakReference<>(baked),new GeoNativeGeometry(baked));CACHE.put(resource,entry);}
        var geometry=entry.geometry();if(geometry.cubeCount()==0)return null;
        var texture=geo.getTextureLocation(entity);if(texture==null)return null;
        var previous=ACTIVE.get();var frame=new Capture(entity,geometry);ACTIVE.set(frame);
        return new Binding(geometry,texture,()->{
            LAST.put(entity,new FrameStatistics(geometry.jointCount(),geometry.cubeCount(),frame.rendered));
            if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);
        });
    }
    @SubscribeEvent @SuppressWarnings({"rawtypes","unchecked"})
    public static void layers(GeoRenderEvent.Entity.CompileRenderLayers event){event.addLayer(new CaptureLayer(event.getRenderer()));}
    private static final class CaptureLayer<T extends GeoAnimatable> extends GeoRenderLayer<T>{
        CaptureLayer(GeoRenderer<T> renderer){super(renderer);}
        @Override public void renderForBone(PoseStack stack,T entity,GeoBone bone,RenderType type,MultiBufferSource buffers,VertexConsumer vertices,float partial,int light,int overlay){
            var capture=ACTIVE.get();
            if(capture!=null&&capture.entity==entity&&NativeRenderScope.owns(capture.entity,null)
                    &&capture.geometry.capture(bone,stack,light,overlay))capture.rendered++;
        }
    }
    @SubscribeEvent public static void clientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        NativeInfectionVisuals.maintainWorld(net.minecraft.client.Minecraft.getInstance().level);
    }
    @EventBusSubscriber(modid="blacklight",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void register(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event){
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->NativeInfectionVisuals.invalidateModels());
        }
    }
    public static void clearModelCache(){CACHE.clear();LAST.clear();}
}
