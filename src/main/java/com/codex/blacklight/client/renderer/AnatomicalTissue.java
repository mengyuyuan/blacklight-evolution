package com.codex.blacklight.client.renderer;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.animation.ReceiverModelPose;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.mixin.client.ModelPartChildrenAccessor;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.joml.Quaternionf;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Generated tissue shape keys fitted to native joints; an authored cow is an opt-in reference.
 * Shared definitions are immutable;
 * matrices and growth belong to this render only. Native UVs survive body reconstruction. */
final class AnatomicalTissue {
    private static final ResourceLocation MATERIAL = ResourceLocation.fromNamespaceAndPath("blacklight", "textures/entity/tissue_anatomy.png");
    private static final Map<String, Optional<JsonObject>> SOURCES = new HashMap<>();
    private static final Map<EntityModel<?>, Map<String, List<Region>>> CACHE = new WeakHashMap<>();
    private record ModuleKey(TissueCore core,int plan,boolean fallback,int side){}
    private static final Map<TissueMorphogenesis.Bone,Map<ModuleKey,List<Mesh>>> MODULES=new WeakHashMap<>();
    private record Face(float[] position, float[] uv, Vector3f normal) {}
    private record Mesh(String name, boolean skin, int phase, float[][] pivots, float[][] parentPivots, String motion, Vector3f axis,
                        float[][] stages, float[][] faces, float[][] uv) {}
    private record Region(String name, ModelPart part, ModelPart.Cube cube, int anatomy, List<Mesh> meshes, float[][] outlet,TissueMorphogenesis.Bone frame,boolean groundSupport) {}
    private record Pending(Region region, PoseStack.Pose pose, int light, int overlay, int color) {}
    private final BlacklightInfectedHost host;
    private List<Region> regions;
    private record Graft(Mesh previous,float progress,float gain,TissueCore core,TissueMorphogenesis.Bone motionFrame){
        Graft(Mesh previous,float progress,float gain,TissueCore core){this(previous,progress,gain,core,null);}
    }
    private final Map<Mesh,Graft> grafts=new IdentityHashMap<>();
    private final Map<Mesh,Float> retiring=new IdentityHashMap<>();
    private record Resolved(List<Region> source,long revision,List<Region> regions,Map<Mesh,Graft> grafts){}
    private static final Map<UUID,Resolved> MATURE_GRAFTS=new LinkedHashMap<>(32,.75F,true){
        @Override protected boolean removeEldestEntry(Map.Entry<UUID,Resolved> entry){return size()>64;}
    };
    private final Map<Region, Pending> pending = new IdentityHashMap<>();
    private final ResourceLocation skin;
    private final long seed;
    private final float growth, partial,overgrowth;
    private final boolean procedural,organsOnly;
    private final TissueEvolution.State evolution;
    private final TissueMotion.State[] motionStates=new TissueMotion.State[4];
    private org.joml.Matrix4f worldRelativePose;
    private final net.minecraft.nbt.CompoundTag digestiveState;
    private final TissueDigestion.Profile digestiveProfile;
    void worldPose(org.joml.Matrix4f pose){worldRelativePose=new org.joml.Matrix4f(pose);}

    private AnatomicalTissue(BlacklightInfectedHost host, List<Region> regions, ResourceLocation skin, float partial,boolean procedural,boolean organsOnly) {
        this.host = host; this.regions = regions; this.skin = skin; this.partial = partial;
        digestiveState=host.digestiveVisual();
        digestiveProfile=host.digestiveProfile();
        seed = TissueOrgans.seed(host.getUUID()); growth = organsOnly?TissueEvolution.attachmentGrowth(host.tissueAge()):TissueGrowth.progress(host.tissueAge());
        this.procedural=procedural;this.organsOnly=organsOnly;
        evolution=organsOnly?ChimeraTissue.receiverEvolution(host,true,partial):procedural?ChimeraTissue.receiverEvolution(host,false,partial):TissueEvolution.sample(seed,0);
        overgrowth=procedural&&!organsOnly?TissueGrowth.overgrowth(host.tissueAge()):0;
        float elapsed=host.tissueStrikeElapsed(partial);
        float melee=elapsed<0?(float)Math.sin(Math.PI*host.getAttackAnim(partial)):0;
        for(int phase=0;phase<motionStates.length;phase++)motionStates[phase]=TissueMotion.sample(seed,
                host.level().getGameTime()+partial,TissueAnatomy.local(seed,growth,phase),melee,elapsed);
    }
    static void reload() { SOURCES.clear(); CACHE.clear(); MATURE_GRAFTS.clear(); MODULES.clear(); ChimeraTissue.reload(); EvolvedBodyRenderer.reload(); }
    static TissueChimera.Body donorBody(EntityModel<?> model,TissueInheritance.BodySource source){
        var nativeBody=ProceduralTissueBlueprint.snapshot(model);var core=TissueCore.byId(source.nativeCore());if(nativeBody==null||core==null)return null;
        var regions=build("",model,false,true,TissueCore.visualSeed(core,source.variant()),false);var result=new ArrayList<TissueChimera.Part>();
        for(var region:regions){var matrix=nativeBody.transforms().get(region.part);if(matrix==null||region.frame==null)continue;
            var points=new ArrayList<TissueMorphogenesis.V>();var faces=new ArrayList<int[]>();var colors=new ArrayList<Integer>();
            for(var mesh:region.meshes){
                if(!core.legacy()&&!mesh.skin&&Arrays.stream(TissueCore.values()).filter(TissueCore::legacy).anyMatch(c->TissueGraftLayout.owns(c,mesh.name)))continue;
                float g=TissueAnatomy.local(source.variant(),TissueGrowth.progress(source.age()),mesh.phase);float[] vertices=new float[mesh.stages[0].length];
                for(int i=0;i<vertices.length;i++)vertices[i]=TissueAnatomy.coordinate(mesh.stages,i,g,TissueGrowth.overgrowth(source.age()));
                TissueEvolution.organ(vertices,region.frame,mesh.name,source.procedural(),region.groundSupport);
                for(var face:mesh.faces){int material=(int)face[4];
                    int start=points.size();for(int j=0;j<4;j++){int i=(int)face[j]*3;var v=matrix.transformPosition(new Vector3f(vertices[i],vertices[i+1],vertices[i+2]));points.add(new TissueMorphogenesis.V(v.x,v.y,v.z));}
                    faces.add(new int[]{start,start+1,start+2,start+3});colors.add(0xff000000|switch(material){case 2,5->0x73272d;case 3,6->0x251f22;default->0x443033;});
                }
            }
            if(!points.isEmpty())result.add(new TissueChimera.Part("evolved_"+region.name,region.anatomy,new TissueMorphogenesis.V(matrix.m30(),matrix.m31(),matrix.m32()),List.copyOf(points),List.copyOf(faces),Collections.nCopies(faces.size(),new float[]{0,0,1,0,1,1,0,1}),List.copyOf(colors)));
        }
        return result.isEmpty()?null:new TissueChimera.Body(List.copyOf(result),TissueChimera.bounds(result.stream().flatMap(p->p.vertices().stream()).toList()),nativeBody.anchorBox());
    }
    static AnatomicalTissue bind(BlacklightInfectedHost host, EntityModel<?> model, ResourceLocation skin, float partial) {
        return bind(host,model,skin,partial,false);
    }
    private static AnatomicalTissue bind(BlacklightInfectedHost host,EntityModel<?> model,ResourceLocation skin,float partial,boolean coat) {
        return bind(host,model,skin,partial,coat,false);
    }
    static AnatomicalTissue grafts(BlacklightInfectedHost host,EntityModel<?> model,ResourceLocation skin,float partial){
        return host.tissueAge()<=12000?null:bind(host,model,skin,partial,false,true);
    }
    private static AnatomicalTissue bind(BlacklightInfectedHost host,EntityModel<?> model,ResourceLocation skin,float partial,boolean coat,boolean organsOnly) {
        if(host.hasMultipartBody())return null;
        boolean authored=!organsOnly&&!coat&&Boolean.getBoolean("blacklight.authoredCowTissue")&&host.speciesId().equals("minecraft:cow")&&!host.isBaby();
        int variant=TissueCore.visualSeed(host.nativeTissueCore(),TissueOrgans.seed(host.getUUID()));
        String key=authored?host.speciesId()+(TissueOrgans.dominantArm(TissueOrgans.seed(host.getUUID()))==ConsumeAnatomy.RIGHT_ARM?"#right":"#left")
                :"procedural#"+variant+(coat?"#coat":"");
        var regions=CACHE.computeIfAbsent(model,k->new HashMap<>()).computeIfAbsent(key,k->build(host.speciesId(),model,authored&&k.endsWith("#right"),!authored,variant,coat));
        if(regions.isEmpty())return null;
        var result=new AnatomicalTissue(host,regions,skin,partial,!authored,organsOnly);
        if(!coat)result.inherit(model);
        return result;
    }
    /** Replace each named organ once, interpolating from its current surface to the donor's profile.
     * Cached profiles are shared and immutable; this entity never edits a native model or another host. */
    private void inherit(EntityModel<?> model){
        var genome=host.tissueInheritance();TissueCore nativeCore=host.nativeTissueCore();
        if(genome.organs().isEmpty()&&nativeCore.legacy())return;
        boolean mature=(nativeCore.legacy()||TissueEvolution.attachmentGrowth(host.tissueAge())>=.999F)&&genome.organs().values().stream().allMatch(TissueInheritance.Organ::mature);
        var original=regions;
        var cached=mature?MATURE_GRAFTS.get(host.getUUID()):null;
        if(cached!=null&&cached.source==original&&cached.revision==genome.revision()){
            regions=cached.regions;grafts.putAll(cached.grafts);return;
        }
        if(!nativeCore.legacy()){
            var stripped=new ArrayList<Region>();for(var r:regions){
                var meshes=r.meshes.stream().filter(m->m.skin||TissueEvolution.supportingMesh(nativeCore,m.name)||java.util.Arrays.stream(TissueCore.values()).filter(TissueCore::legacy).noneMatch(c->TissueGraftLayout.owns(c,m.name))).toList();
                stripped.add(new Region(r.name,r.part,r.cube,r.anatomy,meshes,r.outlet,r.frame,r.groundSupport));
            }regions=List.copyOf(stripped);
        }
        for(var entry:genome.organs().entrySet()){
            TissueCore core=entry.getKey();var organ=entry.getValue();
            if(!core.legacy()||core==nativeCore||core==TissueCore.MUSCLE)continue;
            float progress=TissueGraftLayout.growth(organ,host.tissueInheritanceVisualWork(partial));if(progress<=.0001F)continue;
            int variant=TissueCore.visualSeed(core,organ.variant());String key="procedural#"+variant+"#donor"+organ.donorPlan();
            var donors=CACHE.computeIfAbsent(model,k->new HashMap<>()).computeIfAbsent(key,k->build(host.speciesId(),model,false,true,variant,false,organ.donorPlan()));
            var resolved=new ArrayList<Region>();
            for(var region:regions){
                var donor=donors.stream().filter(r->r.part==region.part&&r.cube==region.cube).findFirst().orElse(null);
                if(donor==null){resolved.add(region);continue;}
                var meshes=new LinkedHashMap<String,Mesh>();region.meshes.forEach(mesh->meshes.put(TissueGraftLayout.key(core,mesh.name),mesh));
                var donorKeys=new HashSet<String>();
                for(var mesh:donor.meshes)if(TissueGraftLayout.owns(core,mesh.name)){
                    // A donor's digits replace the receiving digit set, even if one profile calls them blades.
                    String meshKey=TissueGraftLayout.key(core,mesh.name);donorKeys.add(meshKey);Mesh previous=meshes.get(meshKey);
                    meshes.put(meshKey,mesh);grafts.put(mesh,new Graft(previous,progress,TissueGraftLayout.gain(organ),core));
                }
                for(var mesh:region.meshes)if(TissueGraftLayout.owns(core,mesh.name)&&!donorKeys.contains(TissueGraftLayout.key(core,mesh.name))){
                    if(progress>=.999F)meshes.remove(TissueGraftLayout.key(core,mesh.name));
                    else grafts.put(mesh,new Graft(null,1-progress,1,nativeCore));
                }
                resolved.add(new Region(region.name,region.part,region.cube,region.anatomy,List.copyOf(meshes.values()),region.outlet,region.frame,region.groundSupport));
            }
            regions=List.copyOf(resolved);
        }
        var layout=TissueOrganLayout.fit(regions.stream().map(Region::frame).filter(Objects::nonNull).toList(),seed);
        var expressed=java.util.EnumSet.noneOf(TissueCore.class);
        for(var core:genome.organs().keySet())if(!core.legacy())expressed.add(core);
        if(host.knownNativeTissueCore()!=null&&!host.knownNativeTissueCore().legacy())expressed.add(host.knownNativeTissueCore());
        var functional=new ArrayList<Region>();int faceBudget=2400-regions.stream().flatMap(r->r.meshes.stream()).mapToInt(m->m.faces.length).sum();
        for(var r:regions){var meshes=new ArrayList<>(r.meshes);
            float replaced=0;
            if(r.frame!=null)for(var weapon:new TissueCore[]{TissueCore.BLADE,TissueCore.CHELA,TissueCore.HYDROSTAT,TissueCore.CLUB})
                if(!ChimeraTissue.routed(host,weapon)&&(layout.mounted(weapon,r.frame)||layout.fallback(weapon,r.frame)))replaced=Math.max(replaced,host.organExpression(weapon,partial));
            if(replaced>.0001F){
                for(var iterator=meshes.iterator();iterator.hasNext();){var mesh=iterator.next();if(mesh.skin||!TissueOrganLayout.oldDistalKit(mesh.name))continue;
                    if(replaced>=.999F){iterator.remove();grafts.remove(mesh);faceBudget+=mesh.faces.length;}
                    else retiring.put(mesh,1-replaced);
                }
            }
            if(r.frame!=null)for(var core:expressed){
                if(ChimeraTissue.routed(host,core))continue;
                float expression=host.organExpression(core,partial);if(expression<=.0001F)continue;
                boolean fallback=layout.fallback(core,r.frame);
                if(!fallback&&!layout.mounted(core,r.frame))continue;
                var modules=MODULES.computeIfAbsent(r.frame,k->new HashMap<>()).computeIfAbsent(new ModuleKey(core,host.nativeTissuePlan(),fallback,fallback?TissueOrganLayout.bladeSide(seed):0),k->{
                    var source=fallback?TissueMorphogenesis.functionalFallback(core,r.frame,host.nativeTissuePlan(),seed):TissueMorphogenesis.functional(core,r.frame,host.nativeTissuePlan(),seed);
                    var result=new ArrayList<Mesh>();for(var m:source)
                        result.add(new Mesh(m.name(),false,m.phase(),m.pivots(),null,m.motion(),new Vector3f(m.axis()),m.stages(),m.faces(),new float[m.faces().length][8]));
                    return List.copyOf(result);
                });
                var acquired=genome.organ(core);float gain=acquired==null?1:TissueGraftLayout.gain(acquired);
                var motionFrame=fallback?TissueMorphogenesis.functionalMount(core,r.frame,seed):r.frame;
                for(var mesh:modules)if(faceBudget>=mesh.faces.length){faceBudget-=mesh.faces.length;meshes.add(mesh);grafts.put(mesh,new Graft(null,expression,gain,core,motionFrame));}
            }
            functional.add(new Region(r.name,r.part,r.cube,r.anatomy,List.copyOf(meshes),r.outlet,r.frame,r.groundSupport));
        }regions=List.copyOf(functional);
        if(mature)MATURE_GRAFTS.put(host.getUUID(),new Resolved(original,genome.revision(),regions,Map.copyOf(grafts)));
    }
    AnatomicalTissue coat(EntityModel<?> model,ResourceLocation texture){return bind(host,model,texture,partial,true);}
    boolean owns(ModelPart part){return regions.stream().anyMatch(r->r.part==part);}
    Vector3f tendrilRoot(ModelPart part){for(var r:regions)if(r.part==part&&r.outlet!=null)return sample(r.outlet,TissueAnatomy.local(seed,growth,0),r);return null;}
    boolean procedural(){return procedural;}
    Vector3f tentacleRoot(ModelPart part,float side,float front) {
        for(var r:regions)if(r.part==part&&r.frame!=null) {
            var b=r.frame;var box=b.box();
            var point=box.centre().add(b.right().mul((Math.abs(b.right().x())*box.span(0)+Math.abs(b.right().y())*box.span(1)+Math.abs(b.right().z())*box.span(2))*.43F*side))
                    .add(b.forward().mul((Math.abs(b.forward().x())*box.span(0)+Math.abs(b.forward().y())*box.span(1)+Math.abs(b.forward().z())*box.span(2))*.32F*front));
            point=TissueEvolution.develop(b,point,evolution,r.groundSupport);
            return new Vector3f(point.x(),point.y(),point.z());
        }
        return null;
    }
    /** Replace only the selected cube, preserving udder, horns, equipment and child parts. */
    boolean renderSkin(ModelPart part, PoseStack.Pose pose, VertexConsumer out, int light, int overlay, int color) {
        var selected=regions.stream().filter(r->r.part==part).toList();
        if(selected.isEmpty())return false;
        PoseStack.Pose posed = pose(selected.getFirst(), pose);
        for (var cube : ((ModelPartChildrenAccessor)(Object)part).blacklight$cubes())
            if(selected.stream().noneMatch(r->r.cube==cube&&r.meshes.stream().anyMatch(Mesh::skin)))cube.compile(posed, out, light, overlay, color);
        for(var region:selected)pending.putIfAbsent(region,new Pending(region,pose(region,pose),light,overlay,color));
        return true;
    }
    void capture(ModelPart part, PoseStack.Pose pose, int light, int overlay, int color) {
        for (var r : regions) if (r.part == part) pending.putIfAbsent(r, new Pending(r, pose(r, pose), light, overlay, color));
    }
    private PoseStack.Pose pose(Region r, PoseStack.Pose original) {
        if(organsOnly)return original; // The dedicated body already folded this limb once.
        var stack = new PoseStack(); stack.last().pose().set(original.pose()); stack.last().normal().set(original.normal());
        if(procedural) {
            if(r.groundSupport&&r.frame!=null) {
                var box=r.frame.box();int axis=box.span(0)>box.span(1)&&box.span(0)>box.span(2)?0:box.span(1)>box.span(2)?1:2;
                float fold=1-.65F*host.tissueTentacles();
                stack.scale(axis==0?fold:1,axis==1?fold:1,axis==2?fold:1);
            }
            return stack.last();
        }
        if (r.name.equals("head")) {
            float headGrowth = TissueAnatomy.local(seed, growth, 0);
            stack.translate(0, 3 * headGrowth / 16, -2.6F * headGrowth / 16);
        } else if (r.name.contains("front")) {
            int phase = r.name.startsWith("dominant") ? 1 : 2;
            float g = TissueAnatomy.local(seed, growth, phase);
            // Move the rotation centre into the growing shoulder; preserve the native walk clock.
            float shoulder = TissueAnatomy.coordinate(0,-3.5F,-7,g);
            stack.mulPose(Axis.XP.rotation(-r.part.xRot));
            stack.translate(0, shoulder / 16, 0);
            float strike = TissueOrgans.extension(host.tissueStrikeElapsed(partial));
            float melee=host.tissueStrikeElapsed(partial)<0?(float)Math.sin(Math.PI*host.getAttackAnim(partial)):0;
            stack.mulPose(Axis.XP.rotation(r.part.xRot * (1 - .38F * g) - .16F * strike * g
                    -(r.name.startsWith("dominant")?.85F:.24F)*melee*g));
            stack.translate(0, -shoulder / 16, 0);
        }
        return stack.last();
    }
    void flush(MultiBufferSource buffers) {
        if (pending.isEmpty()) return;
        // Emit complete passes. Requesting another buffer may end the previous builder.
        var nativeOut = buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
        if(!organsOnly)for (var p : pending.values()) for (var m : p.region.meshes) if (m.skin) draw(p, m, nativeOut, false);
        var tissueOut = buffers.getBuffer(RenderType.entityCutoutNoCull(MATERIAL));
        for (var p : pending.values()) for (var m : p.region.meshes) draw(p, m, tissueOut, true);
        pending.clear();
    }
    private void draw(Pending p, Mesh mesh, VertexConsumer out, boolean tissue) {
        var graft=grafts.get(mesh);
        boolean mandible=organsOnly&&mesh.name.equals("mandible")&&(host.nativeTissueCore()==TissueCore.JAW||graft!=null&&graft.core==TissueCore.JAW);
        if(organsOnly&&((mesh.skin&&!mandible)||mesh.name.equals("tendril_gland")))return;
        if (out instanceof ConsumeAnatomy.Consumer c) c.anatomy(p.region.anatomy);
        EvolvedDonorCapture.mesh(p.region.part,mesh.name);
        float g = TissueAnatomy.local(seed, growth, mesh.phase);
        if (tissue && g <= 0) return;
        int size = mesh.stages[0].length;
        float[] vertices = new float[size];
        for (int i = 0; i < size; i++) vertices[i] = TissueAnatomy.coordinate(mesh.stages,i,g,overgrowth);
        if(graft!=null){
            // The mandible, palate and teeth share the head's deformation field.
            // Scaling each around a separate root would detach teeth from the enlarged jaw.
            float donorGain=graft.core==TissueCore.JAW?1:(1+(TissueSkin.organGain(graft.core,mesh.name)-1)*g)*graft.gain;
            TissueSkin.focus(vertices,donorGain);
            if(graft.previous!=null&&graft.previous.stages[0].length==size){
                float[] previous=new float[size];
                for(int i=0;i<size;i++)previous[i]=TissueAnatomy.coordinate(graft.previous.stages,i,g,overgrowth);
                if(organsOnly)TissueSkin.focus(previous,1+(TissueSkin.organGain(host.nativeTissueCore(),graft.previous.name)-1)*g);
                for(int i=0;i<size;i++){
                    vertices[i]=previous[i]+(vertices[i]-previous[i])*graft.progress;
                }
            }else{
                // New ribs/digits emerge from a buried root; no instant fully-sized attachment.
                TissueSkin.rootedScale(vertices,graft.progress);
            }
        }else if(organsOnly)TissueSkin.focus(vertices,1+(TissueSkin.organGain(host.nativeTissueCore(),mesh.name)-1)*g);
        if(retiring.containsKey(mesh))TissueSkin.rootedScale(vertices,retiring.get(mesh));
        var graftShift=new TissueMorphogenesis.V(0,0,0);
        if(organsOnly&&p.region.frame!=null){
            var root=new TissueMorphogenesis.V(0,0,0);for(int i=0;i<24;i+=3)root=root.add(new TissueMorphogenesis.V(vertices[i],vertices[i+1],vertices[i+2]));
            graftShift=TissueSilhouette.attachmentShift(p.region.frame,root.mul(.125F));
            TissueSilhouette.graft(vertices,graftShift);
        }
        if(procedural&&p.region.frame!=null)TissueEvolution.organ(vertices,p.region.frame,mesh.name,evolution,p.region.groundSupport);
        if(procedural)TissueDigestiveMotion.surface(vertices,p.region.frame,digestiveProfile,digestiveState,host.level().getGameTime()+partial);
        if(graft!=null&&!graft.core.legacy()){
            float time=host.organActionCore()==graft.core?host.organActionElapsed(partial):-1;
            float feeding=host.cannibalStarted()<0?0:Math.clamp((host.level().getGameTime()-host.cannibalStarted()+partial-TissueCompetition.COVER_TICKS)/(host.cannibalDuration()-TissueCompetition.COVER_TICKS),0,1);
            TissueFunctionalMotion.apply(vertices,graft.motionFrame==null?p.region.frame:graft.motionFrame,mesh.name,graft.core,time,feeding);
            if(graft.core.digestive())TissueDigestiveMotion.apply(vertices,graft.motionFrame==null?p.region.frame:graft.motionFrame,graft.core,
                    host.level().getGameTime()+partial,TissueDigestiveMotion.active(graft.core,digestiveState),TissueDigestiveMotion.load(graft.core,digestiveState));
        }
        float elapsed=host.tissueStrikeElapsed(partial);
        float melee=elapsed<0?(float)Math.sin(Math.PI*host.getAttackAnim(partial)):0;
        var motion=motionStates[mesh.phase>=0&&mesh.phase<4?mesh.phase:0];
        if(procedural&&(mesh.name.startsWith("tissue_")||mesh.name.equals("tendril_gland")))
            TissueMotion.flex(vertices,8,motion.pulse(),mesh.skin);
        float widthVariation=mesh.phase==1||mesh.phase==2?1+(TissueGrowth.sample(seed,520+mesh.phase)-.5F)*.12F*g:1;
        if(widthVariation!=1)for(int i=0;i<size;i+=3)vertices[i]*=widthVariation;
        var posed = new PoseStack(); posed.last().pose().set(p.pose.pose()); posed.last().normal().set(p.pose.normal());
        if (mesh.motion.equals("jaw")||mesh.motion.equals("hook")||mesh.motion.equals("split")||mesh.motion.equals("toe")) {
            // This is a head-local hinge, independent of the forelimb gait. It
            // inherits the real look pose but cannot pull the skull or neck apart.
            float strike = TissueOrgans.extension(host.tissueStrikeElapsed(partial));
            float breath = .5F + .5F * (float)Math.sin((host.tickCount + partial) * .11F
                    + TissueGrowth.sample(seed, 531) * Math.PI * 2);
            float angle=procedural?switch(mesh.motion) {
                case "jaw"->motion.jaw();case "split"->motion.split();case "toe"->motion.toe();default->motion.hook();
            }:g*(mesh.motion.equals("jaw")?.018F+.025F*breath+.38F*melee+.23F*strike:.20F*melee+.08F*strike);
            var pivot = sample(mesh.pivots, g,p.region,graftShift);
            pivot.x*=widthVariation;
            posed.translate(pivot.x / 16, pivot.y / 16, pivot.z / 16);
            posed.mulPose(new Quaternionf().rotationAxis(angle,mesh.axis.x,mesh.axis.y,mesh.axis.z));
            posed.translate(-pivot.x / 16, -pivot.y / 16, -pivot.z / 16);
        } else if (!mesh.motion.isEmpty()) {
            float bend = Math.max(0, -p.region.part.xRot) * .6F * g;
            float strike = TissueOrgans.extension(host.tissueStrikeElapsed(partial));
            if (mesh.parentPivots != null) {
                var parentPivot=sample(mesh.parentPivots,g,p.region,graftShift);
                parentPivot.x*=widthVariation;
                // Digits inherit the forearm, then curl at their own knuckles.
                posed.translate(parentPivot.x/16,parentPivot.y/16,parentPivot.z/16);
                posed.mulPose(Axis.XP.rotation(bend+.12F*strike*g));
                posed.translate(-parentPivot.x/16,-parentPivot.y/16,-parentPivot.z/16);
            }
            bend=mesh.motion.equals("digit")?bend*-.45F-.24F*strike*g:bend+.12F*strike*g;
            var pivot=sample(mesh.pivots,g,p.region,graftShift);
            pivot.x*=widthVariation;
            posed.translate(pivot.x / 16, pivot.y / 16, pivot.z / 16);
            posed.mulPose(Axis.XP.rotation(bend));
            posed.translate(-pivot.x / 16, -pivot.y / 16, -pivot.z / 16);
        }
        if(graft!=null&&graft.core==TissueCore.TONGUE)
            TissueTongueRender.aim(host,partial,vertices,new org.joml.Matrix4f(posed.last().pose()).scale(1/16F),worldRelativePose);
        for (int f = 0; f < mesh.faces.length; f++) {
            float[] face = mesh.faces[f]; int material = mandible?Math.max(1,(int)face[4]):(int)face[4];
            if (tissue && material == 0) continue;
            float shift=(TissueGrowth.sample(seed,mesh.name.hashCode()*31+f/3)-.5F)*.08F;
            float start=face[5]<0?face[5]:Math.clamp(face[5]+shift,0,.98F);
            float end=Math.clamp(face[6]+shift,start+.01F,1);
            if (!tissue && material > 0 && TissueAnatomy.reveal(g, start, end) >= 1) continue;
            float reveal = tissue ? TissueAnatomy.reveal(g, start, end) : 1;
            if (reveal <= 0) continue;
            Vector3f[] points = new Vector3f[4]; Vector3f centre = new Vector3f();
            for (int j = 0; j < 4; j++) { int i = (int)face[j] * 3; points[j] = new Vector3f(vertices[i], vertices[i+1], vertices[i+2]); centre.add(points[j]); }
            centre.mul(.25F);
            Vector3f normal = new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0]));
            if (normal.lengthSquared() < 1e-10F) continue; normal.normalize();
            // The base and cover share shape keys, so there is no switch to a different silhouette.
            for (int j = 0; j < 4; j++) {
                Vector3f v = points[j];
                if (tissue && mesh.skin) v = new Vector3f(centre).lerp(v, reveal).fma(.025F, normal);
                float u, w;
                if (!tissue) { u=mesh.uv[f][j*2]; w=mesh.uv[f][j*2+1]; }
                else {
                    // Atlas tiles carry coarse fibres and plate edges at native pixel density.
                    int tile=material-1;
                    float spanU=Math.clamp(points[0].distance(points[1]), 1, 15), spanV=Math.clamp(points[0].distance(points[3]), 1, 15);
                    int uvKey=mesh.name.hashCode()*31+f/8;
                    float offsetU=TissueGrowth.sample(seed,uvKey)*(15-spanU);
                    float offsetV=TissueGrowth.sample(seed,uvKey+711)*(15-spanV);
                    u=(tile*16+.5F+offsetU+(j==1||j==2?spanU:0))/128;
                    w=(.5F+offsetV+(j>=2?spanV:0))/32;
                }
                // Native dye/tint belongs to surviving skin, not to new flesh.
                // Preserve alpha so invisibility/fades remain owned by the caller.
                out.addVertex(posed.last(),v.x/16,v.y/16,v.z/16).setColor(tissue?p.color|0x00ffffff:p.color)
                        .setUv(u,w).setOverlay(p.overlay).setLight(p.light).setNormal(posed.last(),normal.x,normal.y,normal.z);
            }
        }
    }
    private static List<Region> build(String species, EntityModel<?> model, boolean mirror,boolean procedural,int variant,boolean coat) {
        return build(species,model,mirror,procedural,variant,coat,-1);
    }
    private static List<Region> build(String species,EntityModel<?> model,boolean mirror,boolean procedural,int variant,boolean coat,int donorPlan) {
        Optional<JsonObject> source;Map<String,ModelPart> parts=new HashMap<>();
        if(procedural){
            try {
                var generated=ProceduralTissueBlueprint.generate(model,variant,coat,donorPlan);source=Optional.of(generated.source());parts.putAll(generated.parts());
            } catch(Exception ex){BlacklightNeoMod.LOGGER.error("Cannot generate tissue for model {}",model.getClass().getName(),ex);return List.of();}
        } else {
            source=SOURCES.computeIfAbsent(species,AnatomicalTissue::load);if(source.isEmpty())return List.of();
            ModelPart root=ReceiverModelPose.root(model);if(root==null)return List.of();collect(root,"root",parts);
        }
        List<Region> result=new ArrayList<>();
        try {
            if(source.get().get("format").getAsInt()!=1)throw new IllegalArgumentException("Unsupported anatomy format");
            int totalFaces=0;
            for(var entry:source.get().getAsJsonArray("regions")) {
                JsonObject region=entry.getAsJsonObject(); String name=region.get("name").getAsString();
                String target=region.get("part").getAsString();
                if(mirror)target=target.replace("left","TEMP").replace("right","left").replace("TEMP","right");
                var part=parts.get(target); if(part==null) return List.of();
                var cubes=((ModelPartChildrenAccessor)(Object)part).blacklight$cubes();
                int slot=region.has("cubeSlot")?region.get("cubeSlot").getAsInt():-1;
                if(slot>=cubes.size())throw new IllegalArgumentException("Invalid source cube slot");
                var cube=slot>=0?cubes.get(slot):cubes.stream().max(Comparator.comparingDouble(c->(c.maxX-c.minX)*(c.maxY-c.minY)*(c.maxZ-c.minZ))).orElseThrow();
                float[] reference=floats(region.getAsJsonArray("reference"));
                if(reference.length!=6)throw new IllegalArgumentException("Invalid reference bounds");
                for(int i=0;i<3;i++)if(!Float.isFinite(reference[i])||!Float.isFinite(reference[i+3])||reference[i+3]<=reference[i])throw new IllegalArgumentException("Empty reference bounds");
                float[] low={cube.minX,cube.minY,cube.minZ}, high={cube.maxX,cube.maxY,cube.maxZ};
                List<Mesh> meshes=new ArrayList<>();
                for(var item:region.getAsJsonArray("meshes")) {
                    var m=item.getAsJsonObject();int count=m.getAsJsonArray("stages").size();
                    if(count!=3&&count!=5)throw new IllegalArgumentException("Invalid number of shape keys");
                    float[][] stages=new float[count][];
                    for(int stage=0;stage<count;stage++) {
                        stages[stage]=floats(m.getAsJsonArray("stages").get(stage).getAsJsonArray());
                        if(stages[stage].length%3!=0||stages[stage].length>12288||stage>0&&stages[stage].length!=stages[0].length)throw new IllegalArgumentException("Invalid shape keys");
                        for(int i=0;i<stages[stage].length;i++) {
                            int axis=i%3;float v=stages[stage][i];if(!Float.isFinite(v)||Math.abs(v)>80)throw new IllegalArgumentException("Invalid vertex");
                            if(mirror&&axis==0)v=-v;
                            stages[stage][i]=low[axis]+(v-reference[axis])*(high[axis]-low[axis])/(reference[axis+3]-reference[axis]);
                        }
                    }
                    var fs=m.getAsJsonArray("faces"); totalFaces+=fs.size(); if(totalFaces>1800)throw new IllegalArgumentException("Anatomy face budget exceeded");
                    float[][] faces=new float[fs.size()][],uv=new float[fs.size()][];
                    List<Face> nativeFaces=nativeFaces(cube);
                    for(int i=0;i<faces.length;i++) {
                        faces[i]=floats(fs.get(i).getAsJsonArray());if(faces[i].length!=7)throw new IllegalArgumentException("Invalid face");
                        for(int j=0;j<4;j++)if(faces[i][j]<0||faces[i][j]>=stages[0].length/3||faces[i][j]!=(int)faces[i][j])throw new IllegalArgumentException("Invalid index");
                        if(faces[i][4]<0||faces[i][4]>7||faces[i][4]!=(int)faces[i][4]||!Float.isFinite(faces[i][5])||!Float.isFinite(faces[i][6])||faces[i][6]<=faces[i][5])throw new IllegalArgumentException("Invalid material or reveal interval");
                        if(mirror){float a=faces[i][1];faces[i][1]=faces[i][3];faces[i][3]=a;}
                        uv[i]=nativeUv(faces[i],stages[0],stages[2],nativeFaces);
                    }
                    float[][] pivots=pivots(m,"pivots","pivot",reference,low,high,mirror);
                    float[][] parents=m.has("parentPivots")||m.has("parentPivot")?pivots(m,"parentPivots","parentPivot",reference,low,high,mirror):null;
                    Vector3f axis=new Vector3f(1,0,0);
                    if(m.has("axis")){float[] values=floats(m.getAsJsonArray("axis"));if(values.length!=3)throw new IllegalArgumentException("Invalid motion axis");axis.set(values);if(!axis.isFinite()||axis.lengthSquared()<1e-8F)throw new IllegalArgumentException("Invalid motion axis");axis.normalize();}
                    meshes.add(new Mesh(m.get("name").getAsString(),m.has("skin")&&m.get("skin").getAsBoolean(),m.get("phase").getAsInt(),pivots,parents,m.has("motion")?m.get("motion").getAsString():"",axis,stages,faces,uv));
                }
                float[][] outlet=region.has("outlet")?pivots(region,"outlet","outletFallback",reference,low,high,mirror):null;
                var frame=region.has("frame")?new Gson().fromJson(region.get("frame"),TissueMorphogenesis.Bone.class):null;
                result.add(new Region(name,part,cube,region.has("anatomy")?region.get("anatomy").getAsInt():ConsumeAnatomy.named(target,ConsumeAnatomy.BODY),List.copyOf(meshes),outlet,frame,region.has("groundSupport")&&region.get("groundSupport").getAsBoolean()));
            }
            BlacklightNeoMod.LOGGER.info("Bound {} tissue growth {}: {} regions, {} source faces, variant={}, coat={}",procedural?"procedural":"authored",species,result.size(),totalFaces,variant,coat);
            return List.copyOf(result);
        } catch(Exception ex){BlacklightNeoMod.LOGGER.error("Cannot bind tissue anatomy {}",species,ex);return List.of();}
    }
    private static float[] floats(JsonArray a){float[] values=new float[a.size()];for(int i=0;i<values.length;i++)values[i]=a.get(i).getAsFloat();return values;}
    private static float[][] pivots(JsonObject m,String keys,String fallback,float[] reference,float[] low,float[] high,boolean mirror){
        int count=m.has(keys)?m.getAsJsonArray(keys).size():3;
        if(count!=3&&count!=5)throw new IllegalArgumentException("Invalid number of joint keys");
        float[][] result=new float[count][];
        for(int k=0;k<count;k++){
            result[k]=m.has(keys)?floats(m.getAsJsonArray(keys).get(k).getAsJsonArray()):m.has(fallback)?floats(m.getAsJsonArray(fallback)):new float[3];
            if(result[k].length!=3)throw new IllegalArgumentException("Invalid joint pivot");
            for(float value:result[k])if(!Float.isFinite(value)||Math.abs(value)>80)throw new IllegalArgumentException("Invalid joint coordinate");
            for(int axis=0;axis<3;axis++){float v=mirror&&axis==0?-result[k][axis]:result[k][axis];result[k][axis]=low[axis]+(v-reference[axis])*(high[axis]-low[axis])/(reference[axis+3]-reference[axis]);}
        }return result;
    }
    private Vector3f sample(float[][] keys,float g,Region region){
        return sample(keys,g,region,new TissueMorphogenesis.V(0,0,0));
    }
    private Vector3f sample(float[][] keys,float g,Region region,TissueMorphogenesis.V shift){
        var point=new TissueMorphogenesis.V(TissueAnatomy.coordinate(keys,0,g,overgrowth),TissueAnatomy.coordinate(keys,1,g,overgrowth),TissueAnatomy.coordinate(keys,2,g,overgrowth)).add(shift);
        if(procedural&&region.frame!=null)point=TissueEvolution.develop(region.frame,point,evolution,region.groundSupport);
        return new Vector3f(point.x(),point.y(),point.z());
    }
    private static void collect(ModelPart p,String name,Map<String,ModelPart> parts){parts.put(name,p);((ModelPartChildrenAccessor)(Object)p).blacklight$children().forEach((n,c)->collect(c,n,parts));}
    private static Optional<JsonObject> load(String species) {
        var id=ResourceLocation.tryParse(species);if(id==null)return Optional.empty();
        var r=Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("blacklight","tissue_blueprints/"+id.getPath()+".json"));
        if(r.isEmpty())return Optional.empty();
        try(var reader=new InputStreamReader(r.get().open(),StandardCharsets.UTF_8)){return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());}
        catch(Exception ex){BlacklightNeoMod.LOGGER.error("Cannot load tissue anatomy {}",species,ex);return Optional.empty();}
    }
    private static List<Face> nativeFaces(ModelPart.Cube cube) {
        List<Face> result=new ArrayList<>();
        cube.compile(new PoseStack().last(),new VertexConsumer(){
            float[] positions=new float[12],uv=new float[8];int index;
            @Override public void addVertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz){
                positions[index*3]=x*16;positions[index*3+1]=y*16;positions[index*3+2]=z*16;uv[index*2]=u;uv[index*2+1]=v;
                if(++index==4){result.add(new Face(positions,uv,new Vector3f(nx,ny,nz)));index=0;positions=new float[12];uv=new float[8];}
            }
            @Override public VertexConsumer addVertex(float x,float y,float z){throw new UnsupportedOperationException();}
            @Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
            @Override public VertexConsumer setUv(float u,float v){return this;}
            @Override public VertexConsumer setUv1(int u,int v){return this;}
            @Override public VertexConsumer setUv2(int u,int v){return this;}
            @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
        },0,0,-1);return result;
    }
    private static Vector3f point(float[] vertices,int index){return new Vector3f(vertices[index*3],vertices[index*3+1],vertices[index*3+2]);}
    private static float[] nativeUv(float[] face,float[] vertices,float[] mature,List<Face> nativeFaces) {
        Vector3f a=point(vertices,(int)face[0]),b=point(vertices,(int)face[1]),c=point(vertices,(int)face[2]);
        Vector3f normal=new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        // Original bevels collapse onto native box edges. Project their UVs
        // using the mature outward normal, rather than always choosing +Y.
        if(normal.lengthSquared()<1e-8)normal=point(mature,(int)face[1]).sub(point(mature,(int)face[0]))
                .cross(point(mature,(int)face[2]).sub(point(mature,(int)face[0])));
        if(normal.lengthSquared()<1e-8)normal.set(0,1,0);else normal.normalize();
        Vector3f projectionNormal=normal;
        Face match=nativeFaces.stream().max(Comparator.comparingDouble(f->projectionNormal.dot(f.normal))).orElseThrow();
        Vector3f origin=point(match.position,0),ab=point(match.position,1).sub(origin),ad=point(match.position,3).sub(origin);
        float[] uv=new float[8];
        for(int j=0;j<4;j++) {
            var v=point(vertices,(int)face[j]).sub(origin);float s=Math.clamp(v.dot(ab)/ab.lengthSquared(),0,1),t=Math.clamp(v.dot(ad)/ad.lengthSquared(),0,1);
            uv[j*2]=match.uv[0]+s*(match.uv[2]-match.uv[0])+t*(match.uv[6]-match.uv[0]);
            uv[j*2+1]=match.uv[1]+s*(match.uv[3]-match.uv[1])+t*(match.uv[7]-match.uv[1]);
        }
        return uv;
    }
}
