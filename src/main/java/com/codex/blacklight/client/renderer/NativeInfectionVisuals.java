package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.NativeInfection;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.EntityModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** The native renderer retains its own animation and equipment. Tissue is fitted to
 * its actual posed ModelParts using the same growth pipeline as converted hosts. */
public final class NativeInfectionVisuals {
    private static final Map<Mob,BlacklightInfectedHost> VIEWS=new WeakHashMap<>();
    private static java.lang.ref.WeakReference<net.minecraft.world.level.Level> lastLevel=new java.lang.ref.WeakReference<>(null);
    private NativeInfectionVisuals() {}
    public static void vascular(net.minecraft.world.entity.Entity entity,PoseStack stack,net.minecraft.client.renderer.MultiBufferSource buffers,float partial,int light){
        if(entity instanceof Mob mob&&!(mob instanceof BlacklightInfectedHost)&&!mob.isRemoved()&&mob.level().isClientSide&&NativeInfection.active(mob))
            TissueVascularFx.render(mob,com.codex.blacklight.infection.NativeTissue.snapshot(mob).getCompound("Vascular"),partial,stack,buffers,light);
    }
    public static net.minecraft.client.renderer.MultiBufferSource buffers(net.minecraft.client.renderer.MultiBufferSource source){return new InfectedSkinBuffers(source);}
    /** Scoped around the actual source renderer, including Gecko/custom paths. */
    public static final class CompetitionScene implements AutoCloseable {
        private final PoseStack stack;private final PoseStack.Pose baseline;
        private final net.minecraft.client.renderer.MultiBufferSource source;
        private CompetitionScene(Mob mob,PoseStack stack,net.minecraft.client.renderer.MultiBufferSource buffers,float partial,int light){
            this.stack=stack;baseline=stack.last();stack.pushPose();boolean retained=false;
            try{
                TissueCompetitionFx.render(mob,partial,stack,buffers,light);
                source=TissueCompetitionFx.prey(mob)?new CannibalCoverBuffers(buffers,mob,stack,partial,TissueCompetitionFx.started(mob)):buffers;
                TissueCompetitionFx.apply(mob,partial,stack);retained=true;
            }finally{if(!retained)while(stack.last()!=baseline)stack.popPose();}
        }
        public net.minecraft.client.renderer.MultiBufferSource buffers(){return source;}
        @Override public void close(){while(stack.last()!=baseline)stack.popPose();}
    }
    public static CompetitionScene competition(net.minecraft.world.entity.Entity entity,PoseStack stack,
            net.minecraft.client.renderer.MultiBufferSource buffers,float partial,int light){
        return entity instanceof Mob mob&&!(mob instanceof BlacklightInfectedHost)&&!mob.isRemoved()
                &&mob.level().isClientSide&&TissueCompetitionFx.active(mob)?new CompetitionScene(mob,stack,buffers,partial,light):null;
    }
    private static final ClassValue<java.util.Optional<java.lang.reflect.Field>> MODELS=new ClassValue<>() {
        @Override protected java.util.Optional<java.lang.reflect.Field> computeValue(Class<?> type){
            for(Class<?> c=type;c!=null&&c!=Object.class;c=c.getSuperclass())
                for(var field:c.getDeclaredFields())
                    if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())&&EntityModel.class.isAssignableFrom(field.getType())&&field.trySetAccessible())
                        return java.util.Optional.of(field);
            return java.util.Optional.empty();
        }
    };
    private static volatile java.util.List<NativeVisualAdapter> ADAPTERS=java.util.List.of(new GeoNativeVisualAdapter());
    public static synchronized void register(NativeVisualAdapter adapter){
        java.util.Objects.requireNonNull(adapter);java.util.Objects.requireNonNull(adapter.id());
        if(ADAPTERS.size()>=24||ADAPTERS.stream().anyMatch(a->a.id().equals(adapter.id())))throw new IllegalArgumentException("Duplicate or excessive native visual adapter: "+adapter.id());
        var next=new java.util.ArrayList<>(ADAPTERS);next.add(adapter);ADAPTERS=java.util.List.copyOf(next);
    }
    public static void maintainWorld(net.minecraft.world.level.Level level){
        if(lastLevel.get()!=level){invalidateModels();lastLevel=new java.lang.ref.WeakReference<>(level);}
    }
    public static void invalidateModels(){VIEWS.clear();GeoNativeVisualAdapter.clearModelCache();}
    public static final class Frame implements AutoCloseable {
        private final InfectedMorphology tissue;private final NativeVisualAdapter.Scope geometry;
        Frame(InfectedMorphology tissue,NativeVisualAdapter.Scope geometry){this.tissue=tissue;this.geometry=geometry;}
        @Override public void close(){try{tissue.close();}finally{geometry.close();}}
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static Frame beginCustom(net.minecraft.world.entity.Entity entity,net.minecraft.client.renderer.entity.EntityRenderer<?> renderer,
            PoseStack stack,float partial){
        if(renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer || !(entity instanceof Mob mob)
                ||!NativeInfection.active(mob))return null;
        maintainWorld(mob.level());
        NativeVisualAdapter.Binding selected=null;
        for(var adapter:ADAPTERS){selected=adapter.bind(mob,renderer,stack,partial);if(selected!=null)break;}
        if(selected==null){
            var field=MODELS.get(renderer.getClass());if(field.isEmpty())return null;
            try{
                var model=(EntityModel<?>)field.get().get(renderer);
                if(model!=null)selected=new NativeVisualAdapter.Binding(model,((net.minecraft.client.renderer.entity.EntityRenderer)renderer).getTextureLocation(entity),NativeVisualAdapter.Scope.NONE);
            }catch(IllegalAccessException ignored){return null;}
        }
        if(selected==null)return null;
        var binding=selected;boolean retained=false;
        try{
            var tissue=begin(mob,binding.model(),binding::texture,stack,partial);
            if(tissue==null)return null;
            retained=true;return new Frame(tissue,binding.scope());
        }finally{if(!retained)binding.scope().close();}
    }
    public static InfectedMorphology begin(LivingEntity entity,EntityModel<?> model,java.util.function.Supplier<ResourceLocation> skin,
            PoseStack stack,float partial) {
        if(entity==null||model==null||skin==null||stack==null||entity.isRemoved())return null;
        maintainWorld(entity.level());
        if (!(entity instanceof Mob mob) || !entity.level().isClientSide || !NativeInfection.active(entity)) {
            if(entity instanceof Mob mob)VIEWS.remove(mob);
            return null;
        }
        var view=VIEWS.computeIfAbsent(mob,key->ModEntities.INFECTED_HOST.get().create(key.level()));
        if(view==null)return null;
        var texture=skin.get();if(texture==null)return null;
        view.sampleNativeVisual(mob);
        return new InfectedMorphology(view,mob,model,texture,stack.last(),partial,new org.joml.Matrix4f(stack.last().pose()));
    }
}
