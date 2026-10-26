package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import java.util.*;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

/** Records the live infected renderer's final surfaces, organs, UVs and materials.
 * No ordinary-model reconstruction, GPU capture, recursive ancestry or world entity is involved. */
final class EvolvedDonorCapture implements MultiBufferSource {
    record Result(TissueChimera.Body body,List<RenderType> materials,Map<String,Matrix4f> bindPoses){}
    private static final ThreadLocal<EvolvedDonorCapture> ACTIVE=new ThreadLocal<>();
    private record Key(int role,int material,String mesh,String bone){}
    private static final class Group {
        final List<V> points=new ArrayList<>();final List<int[]> faces=new ArrayList<>();final List<float[]> uv=new ArrayList<>();final List<Integer> colors=new ArrayList<>();
    }
    private final Map<Key,Group> groups=new LinkedHashMap<>();
    private final Map<RenderType,CaptureConsumer> consumers=new LinkedHashMap<>();private final List<RenderType> materials=new ArrayList<>();
    private final Map<ModelPart,Integer> roles;private final Map<ModelPart,Matrix4f> joints=new IdentityHashMap<>();
    private final Map<ModelPart,String> paths=new IdentityHashMap<>();
    private final ProceduralTissueBlueprint.Snapshot skeleton;private final float scale,offset;
    private boolean neutralArms;
    private String mesh="",bone="";private int faceCount,meshRole=-1;
    private EvolvedDonorCapture(EntityModel<?> model,ProceduralTissueBlueprint.Snapshot skeleton,float scale,float offset){this.roles=ConsumeAnatomy.model(model);this.skeleton=skeleton;this.scale=scale;this.offset=offset;skeleton.paths().forEach((path,part)->paths.put(part,path));}
    static void mesh(ModelPart part,String name){var active=ACTIVE.get();if(active!=null){active.mesh=name;active.bone=active.paths.getOrDefault(part,"");active.meshRole=TissueChimera.appendageRole(active.bone,-1);}}
    static void joint(ModelPart part,PoseStack.Pose pose){var active=ACTIVE.get();if(active!=null)active.joints.putIfAbsent(part,new Matrix4f(pose.pose()));}
    static Result capture(ResourceLocation species,EntityModel<?> model,Mob source,TissueInheritance.BodySource descriptor){
        var mc=Minecraft.getInstance();var skeleton=ProceduralTissueBlueprint.snapshot(model);if(skeleton==null||mc.level==null)return null;
        var donor=ModEntities.INFECTED_HOST.get().create(mc.level);if(donor==null)return null;
        long identity=Integer.toUnsignedLong(Objects.hash(species,descriptor.age(),descriptor.genes()));
        donor.setUUID(new UUID(identity,identity^descriptor.variant()));donor.inherit(source);
        var tag=new CompoundTag();donor.saveWithoutId(tag);
        if(!descriptor.appearance().isBlank())try{tag.put("BlacklightHost",TagParser.parseTag(descriptor.appearance()));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException ignored){}
        tag.getCompound("BlacklightHost").putString("Type",species.toString());
        if(!descriptor.nativeCore().isEmpty())tag.getCompound("BlacklightHost").putString("NativeCore",descriptor.nativeCore());
        tag.putLong("BlacklightTissueAge",descriptor.age());tag.putFloat("BlacklightTissueScale",1);
        var genes=new TissueInheritance(descriptor.genes(),0,0,false,null,identity,0);
        var geneticData=TissueInheritanceCodec.write(genes);geneticData.putLong("SampledAt",mc.level.getGameTime());
        tag.put("BlacklightTissueInheritance",geneticData);tag.putInt("BlacklightTissueNutrition",100);
        donor.readAdditionalSaveData(tag);donor.setNoAi(true);donor.setOnGround(true);donor.setYRot(0);donor.yBodyRot=donor.yBodyRotO=donor.yHeadRot=donor.yHeadRotO=0;
        var capture=new EvolvedDonorCapture(model,skeleton,1.12F*donor.tissueBodyScale(),donor.tissueStanceOffset());
        capture.neutralArms=descriptor.plan()==0;
        var previous=ACTIVE.get();ACTIVE.set(capture);
        try{mc.getEntityRenderDispatcher().getRenderer(donor).render(donor,0,0,new PoseStack(),capture,LightTexture.FULL_BRIGHT);return capture.finish();}
        catch(RuntimeException failure){com.codex.blacklight.BlacklightNeoMod.LOGGER.warn("Cannot snapshot evolved donor {}",species,failure);return null;}
        finally{if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
    }
    private V point(float x,float y,float z){return new V(x*16/scale,(1.501F-(y-offset)/scale)*16,-z*16/scale);}
    @Override public VertexConsumer getBuffer(RenderType type){return consumers.computeIfAbsent(type,t->{int id=materials.size();materials.add(t);return new CaptureConsumer(id,t.toString().contains("shadow")||t.isOutline());});}
    private Result finish(){
        for(var consumer:consumers.values())consumer.finishVertex();var parts=new ArrayList<TissueChimera.Part>();var bindPoses=new HashMap<String,Matrix4f>();
        for(var part:skeleton.body().parts()){
            var node=skeleton.paths().get(part.name().substring(0,part.name().lastIndexOf('#')));var posed=joints.get(node);var bind=skeleton.transforms().get(node);
            if(posed!=null&&bind!=null){
                var normalize=new Matrix4f().translate(0,24.016F+offset*16/scale,0).scale(16/scale,-16/scale,-16/scale);
                bindPoses.put(part.name(),normalize.mul(posed).scale(1F/16).mul(new Matrix4f(bind).invert()));
            }
        }
        var neutralized=new HashSet<String>();
        for(var entry:groups.entrySet()){
            var key=entry.getKey();var group=entry.getValue();if(group.points.isEmpty())continue;
            var nativePart=skeleton.body().parts().stream().filter(p->!key.bone.isEmpty()?p.name().startsWith(key.bone+"#"):p.role()==key.role).max(Comparator.comparingDouble(p->{var b=TissueChimera.bounds(p.vertices());return b.span(0)*b.span(1)*b.span(2);})).orElse(null);
            V pivot=nativePart==null?new V(0,0,0):nativePart.pivot();
            if(nativePart!=null&&bindPoses.containsKey(nativePart.name())){var v=bindPoses.get(nativePart.name()).transformPosition(new Vector3f(pivot.x(),pivot.y(),pivot.z()));pivot=new V(v.x,v.y,v.z);}
            List<V> points=List.copyOf(group.points);
            if(neutralArms&&(key.role==2||key.role==3)&&nativePart!=null&&bindPoses.containsKey(nativePart.name())){
                var inverse=new Matrix4f(bindPoses.get(nativePart.name())).invert();
                points=points.stream().map(p->{var v=inverse.transformPosition(new Vector3f(p.x(),p.y(),p.z()));return new V(v.x,v.y,v.z);}).toList();
                pivot=nativePart.pivot();neutralized.add(nativePart.name());
            }
            parts.add(new TissueChimera.Part("evolved_"+key.role+"_"+key.bone.replace('/','_')+"_"+key.mesh+"_"+parts.size(),key.role,pivot,points,List.copyOf(group.faces),List.copyOf(group.uv),List.copyOf(group.colors),key.material));
        }
        for(String name:neutralized)bindPoses.put(name,new Matrix4f());
        if(parts.isEmpty())return null;
        Box trunk=skeleton.anchorBox();var joint=joints.get(skeleton.anchor());
        if(joint!=null){
            var inverse=new Matrix4f(skeleton.bind()).invert();var points=new ArrayList<V>();
            for(int i=0;i<8;i++){
                var v=new Vector3f((i&1)==0?trunk.low().x():trunk.high().x(),(i&2)==0?trunk.low().y():trunk.high().y(),(i&4)==0?trunk.low().z():trunk.high().z());
                inverse.transformPosition(v).div(16);joint.transformPosition(v);points.add(point(v.x,v.y,v.z));
            }trunk=TissueChimera.bounds(points);
        }
        return new Result(new TissueChimera.Body(List.copyOf(parts),TissueChimera.bounds(parts.stream().flatMap(p->p.vertices().stream()).toList()),trunk),List.copyOf(materials),Map.copyOf(bindPoses));
    }
    private final class CaptureConsumer implements VertexConsumer,ConsumeAnatomy.Consumer {
        private final int material;private final boolean ignored;private int role=0,rgba=-1,currentRole;private float x,y,z,u,v;private boolean vertex;private String currentMesh;
        private final List<V> quad=new ArrayList<>();private final float[] uv=new float[8];private Key quadKey;private int color;private String currentBone;
        CaptureConsumer(int material,boolean ignored){this.material=material;this.ignored=ignored;}
        @Override public VertexConsumer addVertex(float x,float y,float z){finishVertex();this.x=x;this.y=y;this.z=z;currentRole=meshRole>=0?meshRole:role;currentMesh=mesh;currentBone=bone;rgba=-1;u=v=0;vertex=true;return this;}
        @Override public void addVertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz){addVertex(x,y,z);rgba=color;this.u=u;this.v=v;finishVertex();}
        void finishVertex(){
            if(!vertex)return;vertex=false;if(ignored||faceCount>=9600)return;
            if(quad.isEmpty()){quadKey=new Key(currentRole,material,currentMesh,currentBone);color=rgba;}
            uv[quad.size()*2]=u;uv[quad.size()*2+1]=v;quad.add(point(x,y,z));
            if(quad.size()==4){var g=groups.computeIfAbsent(quadKey,k->new Group());int first=g.points.size();g.points.addAll(quad);g.faces.add(new int[]{first,first+1,first+2,first+3});g.uv.add(uv.clone());g.colors.add(color);quad.clear();faceCount++;}
        }
        @Override public VertexConsumer setColor(int r,int g,int b,int a){rgba=a<<24|r<<16|g<<8|b;return this;}
        @Override public VertexConsumer setUv(float u,float v){this.u=u;this.v=v;return this;}
        @Override public VertexConsumer setUv1(int a,int b){return this;}
        @Override public VertexConsumer setUv2(int a,int b){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
        @Override public void anatomy(int id){role=id;mesh="";bone="";meshRole=-1;}
        @Override public void anatomy(float[] weights){int best=0;for(int i=1;i<weights.length;i++)if(weights[i]>weights[best])best=i;anatomy(best);}
        @Override public void anatomy(ModelPart part){String path=paths.getOrDefault(part,"");anatomy(TissueChimera.appendageRole(path,roles.getOrDefault(part,0)));bone=path;}
    }
}
