package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Complete donor body regions rooted in the receiver's animated native trunk. */
final class ChimeraTissue {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/tissue_anatomy.png");
    private static final List<TissueCore> UPPER_ORGANS=List.of(TissueCore.BLADE,TissueCore.CHELA,TissueCore.HYDROSTAT,TissueCore.HORN,TissueCore.PHARYNX,TissueCore.SADDLE,TissueCore.CLUB,TissueCore.TONGUE,TissueCore.CNIDOCYTE,TissueCore.CROP);
    private record DonorVisual(TissueChimera.Body body,List<RenderType> materials,ProceduralTissueBlueprint.Snapshot skeleton,Map<String,Matrix4f> bindPoses){}
    private record UpperModule(TissueCore core,String species,TissueMorphogenesis.Mesh mesh,TissueMorphogenesis.Bone frame,Matrix4f bonePose,TissueChimera.Piece rest,boolean support){}
    private record Remodel(TissueMorphogenesis.Bone frame,Matrix4f posed,Matrix4f inverse){}
    private record SourceKey(ResourceLocation species,int plan,long variant,long age,String core,TissueEvolution.State authored,TissueEvolution.State procedural,Map<TissueCore,TissueInheritance.Organ> genes,String appearance){
        SourceKey(String id,TissueInheritance.BodySource s){this(ResourceLocation.parse(id),s.plan(),s.variant(),s.age(),s.nativeCore(),s.authored(),s.procedural(),s.genes(),s.appearance());}
        TissueInheritance.BodySource descriptor(){return new TissueInheritance.BodySource(plan,variant,0,1,age,core,authored,procedural,genes,appearance);}
    }
    private static final Map<SourceKey,Optional<DonorVisual>> DONORS=new LinkedHashMap<>(32,.75F,true){@Override protected boolean removeEldestEntry(Map.Entry<SourceKey,Optional<DonorVisual>> e){return size()>96;}};
    private record Cached(EntityModel<?> model,Set<SourceKey> sources,TissueChimera.Assembly assembly,Map<String,DonorVisual> visuals,List<UpperModule> modules,Set<net.minecraft.client.model.geom.ModelPart> heads,int socketBudget,Map<String,Remodel> remodeling){}
    private static final Map<UUID,Cached> CACHE=new LinkedHashMap<>(32,.75F,true){@Override protected boolean removeEldestEntry(Map.Entry<UUID,Cached> e){return size()>64;}};
    private final BlacklightInfectedHost host;private final float partial;
    private final ProceduralTissueBlueprint.Snapshot nativeBody;private final TissueChimera.Assembly assembly;
    private final Map<String,DonorVisual> visuals;
    private final List<UpperModule> modules;
    private final Set<net.minecraft.client.model.geom.ModelPart> heads;
    private final Map<String,Remodel> remodeling;
    private PoseStack.Pose pose;private int light,overlay;
    static void reload(){DONORS.clear();CACHE.clear();ProceduralTissueBlueprint.reloadNative();}
    static TissueEvolution.State receiverEvolution(BlacklightInfectedHost host,boolean authored,float partial){
        var inherited=host.tissueEvolution(authored,partial);var cached=CACHE.get(host.getUUID());
        if(cached==null||!cached.assembly.uprightUpper())return host.tissueInheritance().sources().isEmpty()?inherited:new TissueEvolution.State(inherited.generation(),inherited.muscle(),inherited.carapace(),inherited.jaw(),inherited.grasp(),inherited.crest());
        for(var piece:cached.assembly.pieces())if(piece.name().equals("upper_body_fusion")){
            var source=host.tissueInheritance().sources().get(piece.species());if(source==null)return inherited;
            var baseline=TissueEvolution.nativeState(host.nativeTissueCore(),TissueOrgans.seed(host.getUUID()),host.tissueAge(),authored);
            return TissueChimera.receiverEvolution(baseline,inherited,TissueChimera.growth(source,host.tissueInheritanceVisualWork(partial)));
        }
        return inherited;
    }
    static boolean routed(BlacklightInfectedHost host,TissueCore core){
        if(!UPPER_ORGANS.contains(core))return false;
        var cached=CACHE.get(host.getUUID());if(cached==null||!cached.assembly.uprightUpper())return false;
        return cached.modules.stream().anyMatch(m->{var source=host.tissueInheritance().sources().get(m.species);return m.core==core&&source!=null&&TissueChimera.growth(source,0)>=.999F;});
    }
    static ChimeraTissue bind(BlacklightInfectedHost host,EntityModel<?> model,float partial){
        if(host.tissueInheritance().sources().isEmpty()||host.hasMultipartBody())return null;
        var snapshot=ProceduralTissueBlueprint.snapshot(model);if(snapshot==null)return null;
        var keys=new TreeSet<>(host.tissueInheritance().sources().keySet());keys.remove(host.speciesId());
        var sourceKeys=new HashSet<SourceKey>();for(String id:keys)sourceKeys.add(new SourceKey(id,host.tissueInheritance().sources().get(id)));
        var cached=CACHE.get(host.getUUID());TissueChimera.Assembly assembly;
        Map<String,DonorVisual> visuals;List<UpperModule> modules;
        int socketBudget=host.tissueBodyPlan().accessorySockets();
        if(cached!=null&&cached.model==model&&cached.sources.equals(sourceKeys)&&cached.socketBudget==socketBudget){assembly=cached.assembly;visuals=cached.visuals;modules=cached.modules;}
        else{
            var donors=new ArrayList<TissueChimera.Donor>();var visualMap=new HashMap<String,DonorVisual>();
            for(String id:keys){var key=new SourceKey(id,host.tissueInheritance().sources().get(id));
                var body=DONORS.computeIfAbsent(key,ChimeraTissue::donor);if(body.isPresent()){donors.add(new TissueChimera.Donor(id,host.tissueInheritance().sources().get(id),body.get().body));visualMap.put(id,body.get());}
            }
            var thorax=snapshot.body().parts().stream().filter(p->p.role()==0).map(p->TissueChimera.bounds(p.vertices()))
                    .min(Comparator.comparingDouble(b->b.centre().z())).orElse(snapshot.anchorBox());
            assembly=TissueChimera.assemble(snapshot.anchorBox(),host.nativeTissuePlan(),donors,thorax,snapshot.body().bounds().high().y(),socketBudget);
            visuals=Map.copyOf(visualMap);modules=upperModules(host,assembly,visuals);
            var heads=Collections.newSetFromMap(new IdentityHashMap<net.minecraft.client.model.geom.ModelPart,Boolean>());
            for(var p:snapshot.body().parts())if(p.role()==1){var node=snapshot.paths().get(p.name().substring(0,p.name().lastIndexOf('#')));if(node!=null)heads.add(node);}
            cached=new Cached(model,Set.copyOf(sourceKeys),assembly,visuals,modules,heads,socketBudget,remodeling(assembly,visuals));CACHE.put(host.getUUID(),cached);
        }
        return assembly.pieces().isEmpty()?null:new ChimeraTissue(host,partial,snapshot,assembly,visuals,modules,cached.heads,cached.remodeling);
    }
    private ChimeraTissue(BlacklightInfectedHost host,float partial,ProceduralTissueBlueprint.Snapshot body,TissueChimera.Assembly assembly,Map<String,DonorVisual> visuals,List<UpperModule> modules,Set<net.minecraft.client.model.geom.ModelPart> heads,Map<String,Remodel> remodeling){this.host=host;this.partial=partial;this.nativeBody=body;this.assembly=assembly;this.visuals=visuals;this.modules=modules;this.heads=heads;this.remodeling=remodeling;}
    float nativeWeight(net.minecraft.client.model.geom.ModelPart part){
        if(!assembly.uprightUpper()||!heads.contains(part))return 1;
        for(var p:assembly.pieces())if(p.name().equals("upper_body_fusion")){var source=host.tissueInheritance().sources().get(p.species());return source==null?1:1-TissueChimera.growth(source,host.tissueInheritanceVisualWork(partial));}
        return 1;
    }
    PoseStack.Pose nativePose(net.minecraft.client.model.geom.ModelPart part,PoseStack.Pose raw){
        float weight=nativeWeight(part);if(weight>=.9999F)return raw;
        var copy=new PoseStack();copy.last().pose().set(raw.pose());copy.last().normal().set(raw.normal());copy.scale(Math.max(.0001F,weight),Math.max(.0001F,weight),Math.max(.0001F,weight));return copy.last();
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static Optional<DonorVisual> donor(SourceKey key){
        var id=key.species;
        var mc=Minecraft.getInstance();if(mc.level==null||!BuiltInRegistries.ENTITY_TYPE.containsKey(id))return Optional.empty();
        var proxy=BuiltInRegistries.ENTITY_TYPE.get(id).create(mc.level);
        if(!(proxy instanceof net.minecraft.world.entity.Mob living))return Optional.empty();
        var renderer=mc.getEntityRenderDispatcher().getRenderer(living);
        if(!(renderer instanceof LivingEntityRenderer<?,?> modelRenderer))return Optional.empty();
        var model=modelRenderer.getModel();var snapshot=ProceduralTissueBlueprint.snapshot(model);if(snapshot==null)return Optional.empty();
        var captured=EvolvedDonorCapture.capture(id,model,living,key.descriptor());
        return captured==null?Optional.empty():Optional.of(new DonorVisual(captured.body(),captured.materials(),snapshot,captured.bindPoses()));
    }
    private static List<UpperModule> upperModules(BlacklightInfectedHost host,TissueChimera.Assembly assembly,Map<String,DonorVisual> visuals){
        if(assembly.upper()==null)return List.of();
        String species=assembly.pieces().stream().filter(p->p.name().equals("upper_body_fusion")).map(TissueChimera.Piece::species).findFirst().orElse("");
        var visual=visuals.get(species);if(visual==null)return List.of();var result=new ArrayList<UpperModule>();long seed=TissueOrgans.seed(host.getUUID());
        for(var core:UPPER_ORGANS){
            boolean head=core==TissueCore.HORN||core==TissueCore.PHARYNX||core==TissueCore.TONGUE||core==TissueCore.CROP;
            int side=(core==TissueCore.BLADE||core==TissueCore.CLUB?1:-1)*TissueOrganLayout.bladeSide(seed);
            var arm=visual.skeleton.body().parts().stream().filter(p->head?p.role()==1:p.role()==2||p.role()==3).filter(p->head||side*TissueChimera.bounds(p.vertices()).centre().x()>0)
                    .max(Comparator.comparingDouble(p->{var b=TissueChimera.bounds(p.vertices());return b.span(0)*b.span(1)*b.span(2);})).orElse(null);
            if(arm==null)continue;var box=TissueChimera.bounds(arm.vertices());
            var frame=new TissueMorphogenesis.Bone(arm.name(),arm.role(),box,new TissueMorphogenesis.V(0,-1,0),new TissueMorphogenesis.V(0,0,-1),new TissueMorphogenesis.V(1,0,0),box.centre());
            var bonePose=visual.bindPoses.getOrDefault(arm.name(),new Matrix4f());
            for(var mesh:TissueMorphogenesis.functional(core,frame,0,seed))result.add(module(core,species,mesh,frame,bonePose,arm,assembly,false));
        }
        // A connected upper body keeps developing its own support armour/crest.
        // Mesh count is fixed; organ load changes this anatomy instead of adding body copies.
        for(var arm:visual.skeleton.body().parts())if(arm.role()==0||arm.role()==1){
            var frame=frame(arm);var bonePose=visual.bindPoses.getOrDefault(arm.name(),new Matrix4f());
            for(var core:new TissueCore[]{TissueCore.CARAPACE,TissueCore.CREST,TissueCore.JAW}){
                for(var region:TissueMorphogenesis.generate(TissueCore.visualSeed(core,seed),List.of(frame),true).regions())for(var mesh:region.meshes()){
                    if(!TissueGraftLayout.owns(core,mesh.name())||visual.body.parts().stream().anyMatch(p->p.name().contains("_"+mesh.name()+"_")))continue;
                    result.add(module(core,species,mesh,frame,bonePose,arm,assembly,true));
                }
            }
        }return List.copyOf(result);
    }
    private static TissueMorphogenesis.Bone frame(TissueChimera.Part part){
        var box=TissueChimera.bounds(part.vertices());
        return new TissueMorphogenesis.Bone(part.name(),part.role(),box,new TissueMorphogenesis.V(0,-1,0),new TissueMorphogenesis.V(0,0,-1),new TissueMorphogenesis.V(1,0,0),box.centre());
    }
    private static UpperModule module(TissueCore core,String species,TissueMorphogenesis.Mesh mesh,TissueMorphogenesis.Bone frame,Matrix4f bonePose,TissueChimera.Part arm,TissueChimera.Assembly assembly,boolean support){
        var faces=new ArrayList<int[]>();var uv=new ArrayList<float[]>();for(var f:mesh.faces()){
            faces.add(new int[]{(int)f[0],(int)f[1],(int)f[2],(int)f[3]});float u=((f[4]-1)*16+.5F)/128;
            uv.add(new float[]{u,.02F,u+.10F,.02F,u+.10F,.4F,u,.4F});
        }
        var points=new ArrayList<TissueMorphogenesis.V>();float[] v=mesh.stages()[2];for(int i=0;i<v.length;i+=3){var p=bonePose.transformPosition(new Vector3f(v[i],v[i+1],v[i+2]));points.add(assembly.upper().point(new TissueMorphogenesis.V(p.x,p.y,p.z)));}
        var pivot=bonePose.transformPosition(new Vector3f(arm.pivot().x(),arm.pivot().y(),arm.pivot().z()));
        var piece=new TissueChimera.Piece(species,mesh.name(),arm.role(),assembly.upper().point(new TissueMorphogenesis.V(pivot.x,pivot.y,pivot.z)),assembly.upper().root(),List.copyOf(points),List.copyOf(faces),List.copyOf(uv),Collections.nCopies(faces.size(),-1),-2);
        return new UpperModule(core,species,mesh,frame,new Matrix4f(bonePose),piece,support);
    }
    private static Map<String,Remodel> remodeling(TissueChimera.Assembly assembly,Map<String,DonorVisual> visuals){
        if(assembly.upper()==null)return Map.of();var result=new HashMap<String,Remodel>();
        String species=assembly.pieces().stream().filter(p->p.name().equals("upper_body_fusion")).map(TissueChimera.Piece::species).findFirst().orElse("");
        for(var piece:assembly.pieces()){
            var visual=visuals.get(piece.species());if(!piece.species().equals(species)||visual==null||piece.name().contains("fusion"))continue;
            var part=visual.skeleton.body().parts().stream().filter(p->p.role()==piece.role()&&piece.name().startsWith("evolved_"+p.role()+"_"+p.name().substring(0,p.name().lastIndexOf('#')).replace('/','_')+"_"))
                    .max(Comparator.comparingDouble(p->TissueChimera.bounds(p.vertices()).volume())).orElse(null);
            if(part==null)part=visual.skeleton.body().parts().stream().filter(p->p.role()==piece.role())
                    .max(Comparator.comparingDouble(p->TissueChimera.bounds(p.vertices()).volume())).orElse(null);
            if(part==null)continue;var posed=visual.bindPoses.getOrDefault(part.name(),new Matrix4f());
            result.put(piece.name(),new Remodel(frame(part),new Matrix4f(posed),new Matrix4f(posed).invert()));
        }return Map.copyOf(result);
    }
    private TissueChimera.Piece remodel(TissueChimera.Piece piece,TissueInheritance.BodySource source){
        var region=remodeling.get(piece.name());if(region==null||!assembly.uprightUpper())return piece;
        var delta=TissueEvolution.additionalDevelopment(source.authored(),host.tissueEvolution(true,partial));if(!delta.active())return piece;
        var mapping=assembly.upper();var points=new ArrayList<TissueMorphogenesis.V>(piece.vertices().size());
        for(var point:piece.vertices()){
            var p=point.sub(mapping.root()).mul(1/mapping.scale()).add(mapping.origin());
            var local=region.inverse.transformPosition(new Vector3f(p.x(),p.y(),p.z()));
            p=TissueEvolution.develop(region.frame,new TissueMorphogenesis.V(local.x,local.y,local.z),delta,false);
            var posed=region.posed.transformPosition(new Vector3f(p.x(),p.y(),p.z()));points.add(mapping.point(new TissueMorphogenesis.V(posed.x,posed.y,posed.z)));
        }
        return new TissueChimera.Piece(piece.species(),piece.name(),piece.role(),piece.pivot(),piece.root(),points,piece.faces(),piece.uv(),piece.colors(),piece.material());
    }
    void capture(net.minecraft.client.model.geom.ModelPart part,PoseStack.Pose pose,int light,int overlay){
        if(part!=nativeBody.anchor()||this.pose!=null)return;
        var copy=new PoseStack();copy.last().pose().set(pose.pose());copy.last().normal().set(pose.normal());this.pose=copy.last();this.light=light;this.overlay=overlay;
    }
    private Matrix4f worldRelativePose;
    void worldPose(Matrix4f pose){worldRelativePose=new Matrix4f(pose);}
    void flush(MultiBufferSource buffers){
        if(pose==null)return;var inverse=new Matrix4f(nativeBody.bind()).invert();
        float walk=host.walkAnimation.position(partial)*.6662F,speed=Math.clamp(host.walkAnimation.speed(partial)*2,0,1),attack=(float)Math.sin(Math.PI*host.getAttackAnim(partial));
        if(host.organActionCore()!=null)attack=Math.max(attack,TissueOrganCycle.cycle(host.organActionCore()).effort(host.organActionElapsed(partial)));
        float yaw=(float)Math.toRadians(net.minecraft.util.Mth.wrapDegrees(host.yHeadRot-host.yBodyRot));
        for(var piece:assembly.pieces()){
            var source=host.tissueInheritance().sources().get(piece.species());if(source==null)continue;
            float growth=TissueChimera.growth(source,host.tissueInheritanceVisualWork(partial));if(growth<=.001F)continue;
            if(piece.role()>=1&&piece.role()<=3&&modules.stream().anyMatch(m->m.species.equals(piece.species())&&piece.name().contains(m.core.id+"_")&&host.organExpression(m.core,partial)>.001F))continue;
            boolean fusion=piece.name().contains("fusion");var visual=visuals.get(piece.species());
            var type=piece.material()>=0&&visual!=null&&piece.material()<visual.materials.size()?visual.materials.get(piece.material()):RenderType.entityCutoutNoCull(TEXTURE);
            var vertices=TissueChimera.pose(remodel(piece,source),growth,walk,speed,attack,host.tickCount+partial,yaw);
            draw(piece,vertices,buffers.getBuffer(type),inverse,fusion?0xff743236:0);
        }
        for(var module:modules){
            float load=module.support?host.tissueInheritance().supportExpression(module.core,host.tissueInheritanceVisualWork(partial)):0;
            float expression=module.support?Math.clamp(load,0,1):host.organExpression(module.core,partial);var source=host.tissueInheritance().sources().get(module.species);
            if(expression<=.001F||source==null)continue;float growth=TissueChimera.growth(source,host.tissueInheritanceVisualWork(partial));if(growth<=.001F)continue;
            float[] vertices=module.mesh.stages()[2].clone();
            TissueEvolution.organ(vertices,module.frame,module.mesh.name(),TissueEvolution.retainStronger(source.authored(),host.tissueEvolution(true,partial)),false);
            var acquired=host.tissueInheritance().organ(module.core);TissueSkin.focus(vertices,module.support?(float)(1+.2*Math.log1p(load)):acquired==null?1:TissueGraftLayout.gain(acquired));
            float time=host.organActionCore()==module.core?host.organActionElapsed(partial):-1;
            TissueFunctionalMotion.apply(vertices,module.frame,module.mesh.name(),module.core,time,0);TissueSkin.rootedScale(vertices,expression);
            if(module.core.digestive()){
                var digestion=host.digestiveVisual();TissueDigestiveMotion.apply(vertices,module.frame,module.core,host.level().getGameTime()+partial,
                        TissueDigestiveMotion.active(module.core,digestion),TissueDigestiveMotion.load(module.core,digestion));
            }
            var points=new ArrayList<TissueMorphogenesis.V>();for(int i=0;i<vertices.length;i+=3){var v=module.bonePose.transformPosition(new Vector3f(vertices[i],vertices[i+1],vertices[i+2]));points.add(assembly.upper().point(new TissueMorphogenesis.V(v.x,v.y,v.z)));}
            var p=module.rest;var moving=new TissueChimera.Piece(p.species(),p.name(),p.role(),p.pivot(),p.root(),points,p.faces(),p.uv(),p.colors(),p.material());
            var articulated=TissueChimera.pose(moving,growth,walk,speed,attack,host.tickCount+partial,yaw);
            if(module.core==TissueCore.TONGUE){
                float[] aligned=new float[articulated.size()*3];for(int i=0;i<articulated.size();i++){var v=articulated.get(i);aligned[i*3]=v.x();aligned[i*3+1]=v.y();aligned[i*3+2]=v.z();}
                TissueTongueRender.aim(host,partial,aligned,new Matrix4f(pose.pose()).scale(1/16F).mul(inverse),worldRelativePose);
                var fitted=new ArrayList<TissueMorphogenesis.V>();for(int i=0;i<aligned.length;i+=3)fitted.add(new TissueMorphogenesis.V(aligned[i],aligned[i+1],aligned[i+2]));articulated=fitted;
            }
            draw(moving,articulated,buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),inverse,-1);
        }pose=null;
    }
    private void draw(TissueChimera.Piece piece,List<TissueMorphogenesis.V> vertices,VertexConsumer out,Matrix4f inverse,int override){
        for(int f=0;f<piece.faces().size();f++){var face=piece.faces().get(f);var uv=piece.uv().get(f);
            var a=vertices.get(face[0]);var b=vertices.get(face[1]);var c=vertices.get(face[2]);var cross=b.sub(a).cross(c.sub(a));if(cross.length()<.000001F)continue;
            var normal=cross.unit();var n=inverse.transformDirection(new Vector3f(normal.x(),normal.y(),normal.z())).normalize();int color=override==0?piece.colors().get(f):override;
            for(int i=0;i<4;i++){var v=vertices.get(face[i]);var local=inverse.transformPosition(new Vector3f(v.x(),v.y(),v.z()));
                out.addVertex(pose,local.x/16,local.y/16,local.z/16).setColor(color).setUv(uv[i*2],uv[i*2+1]).setOverlay(overlay).setLight(light).setNormal(pose,n.x,n.y,n.z);
            }
        }
    }
}
