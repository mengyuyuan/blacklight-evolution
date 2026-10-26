package com.codex.blacklight.client.renderer;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.animation.ReceiverModelPose;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.mixin.client.ModelPartChildrenAccessor;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Approved species-specific geometry, fitted once to the native rest skeleton.
 * Native UVs, child details and all actual animated ModelPart matrices are retained. */
public final class AuthoredInfectionParts {
    private static final ResourceLocation TISSUE=ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/tissue_anatomy.png");
    private static final Map<String,Optional<JsonObject>> SOURCES=new HashMap<>();
    private static final Map<EntityModel<?>,Map<String,Binding>> BINDINGS=new WeakHashMap<>();
    private record NativePart(String name,ModelPart part,Matrix4f rest,ModelPart.Cube cube) {}
    private record Face(Vector3f[] positions,float[] u,float[] v,Vector3f normal) {}
    private static final Map<ModelPart.Cube,List<Face>> FACES=new WeakHashMap<>();
    private static List<Face> faces(ModelPart.Cube cube){return FACES.computeIfAbsent(cube,AuthoredInfectionParts::captureFaces);}
    private static List<Face> captureFaces(ModelPart.Cube cube){
        var result=new ArrayList<Face>();
        cube.compile(new PoseStack().last(),new VertexConsumer(){
            Vector3f[] positions=new Vector3f[4];float[] us=new float[4],vs=new float[4];int index;
            @Override public void addVertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz){
                positions[index]=new Vector3f(x*16,y*16,z*16);us[index]=u;vs[index]=v;
                if(++index==4){result.add(new Face(positions,us,vs,new Vector3f(nx,ny,nz)));positions=new Vector3f[4];us=new float[4];vs=new float[4];index=0;}
            }
            @Override public VertexConsumer addVertex(float x,float y,float z){throw new UnsupportedOperationException("Cube must supply complete vertices");}
            @Override public VertexConsumer setColor(int r,int g,int b,int a){return this;}
            @Override public VertexConsumer setUv(float u,float v){return this;}
            @Override public VertexConsumer setUv1(int u,int v){return this;}
            @Override public VertexConsumer setUv2(int u,int v){return this;}
            @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
        },0,0,-1);
        return result;
    }
    private record Triangle(Vector3f a,Vector3f b,Vector3f c,Vector3f normal,int color,Face face,Vector3f uvA,Vector3f uvB,Vector3f uvC) {
        Triangle(Vector3f a,Vector3f b,Vector3f c,Vector3f normal,int color,Face face){this(a,b,c,normal,color,face,a,b,c);}
    }
    private record Piece(ModelPart.Cube cube,List<Triangle> skin,List<Triangle> growth,int anatomy) {}
    public record Wound(float u,float v,float width,float height){}
    public static final class Binding {
        private final Map<ModelPart,List<Piece>> pieces=new IdentityHashMap<>();
        private final Map<ModelPart,Pending> pending=new IdentityHashMap<>();
        private final List<String> assignments=new ArrayList<>();
        private final List<Wound> wounds=new ArrayList<>();
        private final Map<ModelPart,TissueMorphogenesis.Bone> frames=new IdentityHashMap<>();
        private float growth=1;
        private long growthSeed;
        private boolean staged;
        private long age;
        private float tentacles;
        private TissueEvolution.State evolution=TissueEvolution.sample(0,0);
        private TissueDigestion.Profile digestiveProfile=new TissueDigestion.Profile(0,0,0,0,0,0);
        private net.minecraft.nbt.CompoundTag digestiveState=new net.minecraft.nbt.CompoundTag();
        private float digestiveTime;
        public void growth(float value,long seed){growth=Math.max(0,Math.min(1,value));growthSeed=seed;staged=true;}
        public void evolution(long age,float tentacles){this.age=age;this.tentacles=tentacles;evolution=TissueEvolution.authoredState(growthSeed,age);}
        public void evolution(long age,float tentacles,TissueEvolution.State inherited){this.age=age;this.tentacles=tentacles;evolution=inherited;}
        void digestion(TissueDigestion.Profile profile,net.minecraft.nbt.CompoundTag state,float time){digestiveProfile=profile;digestiveState=state;digestiveTime=time;}
        void digestion(Binding other){digestion(other.digestiveProfile,other.digestiveState,other.digestiveTime);}
        long age(){return age;}
        float tentacles(){return tentacles;}
        TissueEvolution.State development(){return evolution;}
        Vector3f developRoot(ModelPart part,Vector3f point){var frame=frames.get(part);if(frame==null)return point;var p=develop(point,frame,evolution);float[] v={p.x,p.y,p.z};TissueDigestiveMotion.surface(v,frame,digestiveProfile,digestiveState,digestiveTime);return new Vector3f(v[0],v[1],v[2]);}
        private Triangle digest(Triangle triangle,TissueMorphogenesis.Bone frame){
            if(!digestiveProfile.digestive()||frame==null||frame.role()!=0&&frame.role()!=1)return triangle;
            float[] v={triangle.a.x,triangle.a.y,triangle.a.z,triangle.b.x,triangle.b.y,triangle.b.z,triangle.c.x,triangle.c.y,triangle.c.z};
            TissueDigestiveMotion.surface(v,frame,digestiveProfile,digestiveState,digestiveTime);
            var a=new Vector3f(v[0],v[1],v[2]);var b=new Vector3f(v[3],v[4],v[5]);var c=new Vector3f(v[6],v[7],v[8]);
            var n=new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));if(n.lengthSquared()>1e-8F)n.normalize();else n.set(triangle.normal);
            return new Triangle(a,b,c,n,triangle.color,triangle.face,triangle.uvA,triangle.uvB,triangle.uvC);
        }
        public List<Wound> wounds(){return List.copyOf(wounds);}
        private record Pending(PoseStack.Pose pose,int light,int overlay,int color) {}
        public boolean available(){return !pieces.isEmpty();}
        public List<String> assignments(){return List.copyOf(assignments);}
        public boolean renderSkin(ModelPart part,PoseStack.Pose pose,VertexConsumer out,int light,int overlay,int color){
            var matches=pieces.get(part);if(matches==null)return false;
            var posed=pose(part,pose);
            Set<ModelPart.Cube> replaced=Collections.newSetFromMap(new IdentityHashMap<>());
            for(var p:matches)if(!p.skin.isEmpty()&&replaced.add(p.cube)) {
                if(age<TissueGrowth.ANCIENT_TICKS) {
                    int tint=TissueSkin.tint(color,growthSeed,p.anatomy,TissueEvolution.attachmentGrowth(age));
                    for(var triangle:p.skin)emit(digest(develop(triangle,frames.get(part),evolution),frames.get(part)),posed,out,light,overlay,tint,true);
                }
            }
            if(replaced.isEmpty())return false;
            for(var cube:((ModelPartChildrenAccessor)(Object)part).blacklight$cubes())
                if(!replaced.contains(cube))cube.compile(posed,out,light,overlay,color);
            capture(part,pose,light,overlay,color);return true;
        }
        public void capture(ModelPart part,PoseStack.Pose pose,int light,int overlay,int color){
            if(!pieces.containsKey(part)||pending.containsKey(part))return;
            pose=pose(part,pose);
            PoseStack copy=new PoseStack();copy.last().pose().set(pose.pose());copy.last().normal().set(pose.normal());
            pending.put(part,new Pending(copy.last(),light,overlay,color));
        }
        public void flush(MultiBufferSource buffers){flush(buffers,false,false);}
        public void flush(MultiBufferSource buffers,boolean omitBody,boolean onlyBody){
            if(pending.isEmpty())return;
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            for(var e:pending.entrySet())for(var p:pieces.get(e.getKey())) {
                if(omitBody&&p.anatomy==ConsumeAnatomy.BODY||onlyBody&&p.anatomy!=ConsumeAnatomy.BODY)continue;
                if(out instanceof ConsumeAnatomy.Consumer consumer)consumer.anatomy(e.getKey());
                EvolvedDonorCapture.mesh(e.getKey(),"authored_growth");
                var q=e.getValue();
                float fraction=growth;
                if(staged){
                    float variation=com.codex.blacklight.infection.TissueGrowth.sample(growthSeed,p.anatomy);
                    float delay=p.anatomy==ConsumeAnatomy.BODY?0:.08F+variation*.12F;
                    fraction=Math.max(.07F,Math.min(1,(growth-delay)/(1-delay)))*(.86F+.14F*variation);
                }
                for(var triangle:p.growth)emit(digest(develop(grow(triangle,p.cube,fraction),frames.get(e.getKey()),evolution),frames.get(e.getKey())),q.pose,out,q.light,q.overlay,q.color,false);
            }
            // Use the exact same developed vertices for the tissue cover. A
            // thin normal offset avoids z-fighting without adding body volume.
            var tissue=buffers.getBuffer(RenderType.entityCutoutNoCull(TISSUE));
            for(var entry:pending.entrySet())for(var piece:pieces.get(entry.getKey())) {
                if(omitBody&&piece.anatomy==ConsumeAnatomy.BODY||onlyBody&&piece.anatomy!=ConsumeAnatomy.BODY)continue;
                var posed=entry.getValue();var frame=frames.get(entry.getKey());
                if(tissue instanceof ConsumeAnatomy.Consumer consumer)consumer.anatomy(entry.getKey());
                EvolvedDonorCapture.mesh(entry.getKey(),"authored_skin");
                for(var triangle:piece.skin) {
                    if(TissueEvolution.attachmentGrowth(age)>0)emitTissue(triangle,digest(develop(triangle,frame,evolution),frame),frame,posed.pose,tissue,posed.light,posed.overlay,posed.color,growthSeed,age,piece.anatomy);
                }
            }
            pending.clear();
        }
        public void clear(){pending.clear();}
        PoseStack.Pose pose(ModelPart part,PoseStack.Pose original){
            var frame=frames.get(part);if(tentacles<=0||frame==null||!support(frame))return original;
            var box=frame.box();int axis=box.span(0)>box.span(1)&&box.span(0)>box.span(2)?0:box.span(1)>box.span(2)?1:2;
            float fold=1-.65F*tentacles;var stack=new PoseStack();stack.last().pose().set(original.pose());stack.last().normal().set(original.normal());
            stack.scale(axis==0?fold:1,axis==1?fold:1,axis==2?fold:1);return stack.last();
        }
    }
    private AuthoredInfectionParts(){}
    /** Snapshot the already-remodelled donor body, using the same meshes and development field as its live renderer. */
    static TissueChimera.Body donorBody(String species,EntityModel<?> model,TissueInheritance.BodySource source){
        var binding=bind(species,model);var nativeBody=ProceduralTissueBlueprint.snapshot(model);
        if(!binding.available()||nativeBody==null)return null;
        var result=new ArrayList<TissueChimera.Part>();
        for(var entry:binding.pieces.entrySet()){
            var matrix=nativeBody.transforms().get(entry.getKey());if(matrix==null)continue;
            for(var piece:entry.getValue()){
                // Mature hosts use a reconstructed whole-body surface under their exposed organs.
                // Export both layers: exporting growth alone loses chest/upper arms and shifts the waist.
                var triangles=new ArrayList<Triangle>();triangles.addAll(piece.skin);triangles.addAll(piece.growth);
                var points=new ArrayList<TissueMorphogenesis.V>();var faces=new ArrayList<int[]>();var colors=new ArrayList<Integer>();
                for(var triangle:triangles){
                    var evolved=develop(triangle,binding.frames.get(entry.getKey()),source.authored());
                    int first=points.size();for(var v:List.of(evolved.a,evolved.b,evolved.c))points.add(value(matrix.transformPosition(new Vector3f(v))));
                    faces.add(new int[]{first,first+1,first+2,first+2});colors.add(piece.skin.contains(triangle)?0xff4a2c31:0xff000000|evolved.color);
                }
                if(points.isEmpty())continue;
                var pivot=value(matrix.transformPosition(new Vector3f()));
                result.add(new TissueChimera.Part("evolved_"+piece.anatomy+"_"+result.size(),piece.anatomy,pivot,List.copyOf(points),List.copyOf(faces),Collections.nCopies(faces.size(),new float[]{0,0,1,0,1,1,0,1}),List.copyOf(colors)));
            }
        }
        return result.isEmpty()?null:new TissueChimera.Body(List.copyOf(result),TissueChimera.bounds(result.stream().flatMap(p->p.vertices().stream()).toList()),nativeBody.anchorBox());
    }
    public static void reload(){SOURCES.clear();BINDINGS.clear();FACES.clear();InfectedWoundTextures.reload();AnatomicalTissue.reload();}
    public static Binding bind(String species,EntityModel<?> model){return bind(species,model,false);}
    public static Binding bind(String species,EntityModel<?> model,boolean fur){
        Binding cached=BINDINGS.computeIfAbsent(model,k->new HashMap<>()).computeIfAbsent(species+(fur?"#fur":""),k->build(species,model,fur));
        Binding fresh=new Binding();fresh.pieces.putAll(cached.pieces);fresh.frames.putAll(cached.frames);fresh.assignments.addAll(cached.assignments);fresh.wounds.addAll(cached.wounds);return fresh;
    }
    private static Optional<JsonObject> load(String species){
        ResourceLocation id=ResourceLocation.tryParse(species);
        if(id==null||!id.getNamespace().equals("minecraft"))return Optional.empty();
        var resource=Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("blacklight","infection_parts/"+id.getPath()+".json"));
        if(resource.isEmpty())return Optional.empty();
        try(var r=new InputStreamReader(resource.get().open(),StandardCharsets.UTF_8)){return Optional.of(JsonParser.parseReader(r).getAsJsonObject());}
        catch(Exception e){BlacklightNeoMod.LOGGER.error("Cannot load approved infection parts for {}",species,e);return Optional.empty();}
    }
    private static Binding build(String species,EntityModel<?> model,boolean fur){
        Binding binding=new Binding();var source=SOURCES.computeIfAbsent(species,AuthoredInfectionParts::load);
        if(source.isEmpty())return binding;
        List<NativePart> parts=new ArrayList<>();Set<ModelPart> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        ModelPart root=ReceiverModelPose.root(model);
        if(root!=null)collect(root,"root",new Matrix4f(),parts,seen);
        if(model instanceof HumanoidModel<?> h){
            collect(h.body,"body",new Matrix4f(),parts,seen);collect(h.head,"head",new Matrix4f(),parts,seen);
            collect(h.rightArm,"right_arm",new Matrix4f(),parts,seen);collect(h.leftArm,"left_arm",new Matrix4f(),parts,seen);
            collect(h.rightLeg,"right_leg",new Matrix4f(),parts,seen);collect(h.leftLeg,"left_leg",new Matrix4f(),parts,seen);
        }
        if(parts.isEmpty())return binding;
        Map<ModelPart,Integer> anatomy=ConsumeAnatomy.model(model);
        var frames=new ArrayList<TissueMorphogenesis.Bone>();var frameParts=new HashMap<String,ModelPart>();
        for(int i=0;i<parts.size();i++){
            var p=parts.get(i);var cube=p.cube;String key=p.name+"#"+i;var inverse=new Matrix4f(p.rest).invert();
            var centre=new Matrix4f(p.rest).transformPosition(new Vector3f((cube.minX+cube.maxX)*.5F,(cube.minY+cube.maxY)*.5F,(cube.minZ+cube.maxZ)*.5F));
            frames.add(new TissueMorphogenesis.Bone(key,anatomy.getOrDefault(p.part,ConsumeAnatomy.BODY),
                    new TissueMorphogenesis.Box(new TissueMorphogenesis.V(cube.minX,cube.minY,cube.minZ),new TissueMorphogenesis.V(cube.maxX,cube.maxY,cube.maxZ)),
                    direction(inverse,0,-1,0),direction(inverse,0,0,-1),direction(inverse,1,0,0),value(centre)));
            frameParts.put(key,p.part);
        }
        for(var frame:TissueSilhouette.bind(frames))binding.frames.put(frameParts.get(frame.path()),frame);
        Map<NativePart,List<JsonObject>> grouped=new LinkedHashMap<>();
        for(var entry:source.get().getAsJsonArray("bones")){
            var bone=entry.getAsJsonObject().deepCopy();
            if(fur&&bone.has("furReference")&&!bone.get("furReference").isJsonNull()){
                bone.add("reference",bone.get("furReference"));bone.add("skin",bone.get("furSkin"));
            }
            var target=choose(species,bone.get("name").getAsString(),parts,bone.getAsJsonObject("reference"));
            if(target!=null&&(species.endsWith(":llama")||species.endsWith(":trader_llama"))&&target.name.equals("head")){
                var cubes=((ModelPartChildrenAccessor)(Object)target.part).blacklight$cubes();
                int index=bone.get("name").getAsString().equals("neck")?1:0;
                if(cubes.size()>index)target=new NativePart(target.name,target.part,target.rest,cubes.get(index));
            }
            if(target!=null)grouped.computeIfAbsent(target,k->new ArrayList<>()).add(bone);
        }
        for(var group:grouped.entrySet())for(var bone:group.getValue()){

            String name=bone.get("name").getAsString();NativePart target=group.getKey();
            JsonObject main=group.getValue().stream().filter(b->!b.getAsJsonObject("skin").getAsJsonArray("triangles").isEmpty()).max(Comparator.comparingDouble(b->volume(b.getAsJsonObject("reference")))).orElse(null);
            Matrix4f inverse=new Matrix4f(target.rest).invert();
            var ref=main!=null?main.getAsJsonObject("reference"):combinedReference(group.getValue());Vector3f low=new Vector3f(Float.POSITIVE_INFINITY),high=new Vector3f(Float.NEGATIVE_INFINITY);
            for(int i=0;i<8;i++){
                var lo=ref.getAsJsonArray("min");var hi=ref.getAsJsonArray("max");
                Vector3f v=nativePoint((i&1)==0?lo.get(0).getAsFloat():hi.get(0).getAsFloat(),(i&2)==0?lo.get(1).getAsFloat():hi.get(1).getAsFloat(),(i&4)==0?lo.get(2).getAsFloat():hi.get(2).getAsFloat());
                inverse.transformPosition(v);low.min(v);high.max(v);
            }
            var cube=target.cube;
            Vector3f nativeLow=new Vector3f(Float.POSITIVE_INFINITY),nativeHigh=new Vector3f(Float.NEGATIVE_INFINITY);
            for(var face:faces(cube))for(var vertex:face.positions){nativeLow.min(vertex);nativeHigh.max(vertex);}
            if(species.endsWith(":magma_cube")&&(name.equals("body")||name.equals("head"))){
                // The source uses six stacked slices; Minecraft uses eight. Fit the
                // nucleus and emerging tissue to the whole stack, not a one-pixel slice.
                low.set(-7,8,-7);high.set(7,23,7);
                nativeLow.set(-4,16,-4);nativeHigh.set(4,24,4);
            }
            Vector3f srcSpan=new Vector3f(high).sub(low),span=new Vector3f(nativeHigh).sub(nativeLow);
            float uniform=Float.POSITIVE_INFINITY;
            for(int i=0;i<3;i++)if(srcSpan.get(i)>.001F&&span.get(i)>.05F)uniform=Math.min(uniform,span.get(i)/srcSpan.get(i));
            if(!Float.isFinite(uniform))uniform=1;
            Vector3f ratio=new Vector3f();
            for(int i=0;i<3;i++)ratio.setComponent(i,srcSpan.get(i)>.001F&&span.get(i)>.05F?span.get(i)/srcSpan.get(i):uniform);
            Vector3f centre=new Vector3f(nativeLow).add(nativeHigh).mul(.5F),srcCentre=new Vector3f(low).add(high).mul(.5F);
            List<Triangle> skin=new ArrayList<>(),growth=new ArrayList<>();
            if(bone==main)triangles(bone.getAsJsonObject("skin"),inverse,ratio,srcCentre,centre,cube,true,skin,growth);
            if(skin.isEmpty())growth.clear(); // No matching native surface: retain its original cube.
            triangles(bone.getAsJsonObject("growth"),inverse,ratio,srcCentre,centre,cube,false,skin,growth);
            var frame=binding.frames.get(target.part);var sectioned=new ArrayList<Triangle>();
            for(var triangle:skin)for(var section:TissueSkin.sections(frame,value(triangle.a),value(triangle.b),value(triangle.c)))
                sectioned.add(new Triangle(vector(section.a()),vector(section.b()),vector(section.c()),triangle.normal,triangle.color,triangle.face));
            binding.pieces.computeIfAbsent(target.part,k->new ArrayList<>()).add(new Piece(cube,List.copyOf(sectioned),List.copyOf(growth),anatomy.getOrDefault(target.part,ConsumeAnatomy.BODY)));
            binding.assignments.add(name+" -> "+target.name+" ("+growth.size()+" growth triangles)");
        }
        for(var list:binding.pieces.values())for(var piece:list)if(!piece.skin.isEmpty()){
            for(var face:faces(piece.cube)){
                List<Triangle> tris=piece.skin.stream().filter(t->t.face==face).toList();if(tris.isEmpty())continue;
                float u0=Float.POSITIVE_INFINITY,v0=u0,u1=Float.NEGATIVE_INFINITY,v1=u1;
                for(int i=0;i<4;i++){u0=Math.min(u0,face.u[i]);u1=Math.max(u1,face.u[i]);v0=Math.min(v0,face.v[i]);v1=Math.max(v1,face.v[i]);}
                List<Wound> gaps=new ArrayList<>();
                for(int x=0;x<12;x++)for(int y=0;y<12;y++){
                    float u=u0+(x+.5F)*(u1-u0)/12,v=v0+(y+.5F)*(v1-v0)/12;
                    boolean covered=false;
                    for(var t:tris){float[] a=uv(t.a,face),b=uv(t.b,face),c=uv(t.c,face);if(inside(u,v,a,b,c)){covered=true;break;}}
                    if(!covered)gaps.add(new Wound(u,v,(u1-u0)/12,(v1-v0)/12));
                }
                if(gaps.size()<100)binding.wounds.addAll(gaps);
            }
        }
        BlacklightNeoMod.LOGGER.info("Bound authored infection growth {}: {} native parts, {} source assignments, coat={}",species,binding.pieces.size(),binding.assignments.size(),fur);
        return binding;
    }
    private static boolean inside(float x,float y,float[] a,float[] b,float[] c){
        float d=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1]);if(Math.abs(d)<1e-10)return false;
        float p=((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/d,q=((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/d;
        return p>=-.0001F&&q>=-.0001F&&p+q<=1.0001F;
    }
    private static float[] uv(Vector3f point,Face face){
        Vector3f a=face.positions[0],ab=new Vector3f(face.positions[1]).sub(a),ad=new Vector3f(face.positions[3]).sub(a),av=new Vector3f(point).sub(a);
        float s=av.dot(ab)/ab.lengthSquared(),t=av.dot(ad)/ad.lengthSquared();
        return new float[]{face.u[0]+s*(face.u[1]-face.u[0])+t*(face.u[3]-face.u[0]),face.v[0]+s*(face.v[1]-face.v[0])+t*(face.v[3]-face.v[0])};
    }
    private static double volume(JsonObject ref){var lo=ref.getAsJsonArray("min");var hi=ref.getAsJsonArray("max");return (hi.get(0).getAsDouble()-lo.get(0).getAsDouble())*(hi.get(1).getAsDouble()-lo.get(1).getAsDouble())*(hi.get(2).getAsDouble()-lo.get(2).getAsDouble());}
    private static JsonObject combinedReference(List<JsonObject> bones){
        double[] low={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},high={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(var bone:bones){var r=bone.getAsJsonObject("reference");for(int i=0;i<3;i++){low[i]=Math.min(low[i],r.getAsJsonArray("min").get(i).getAsDouble());high[i]=Math.max(high[i],r.getAsJsonArray("max").get(i).getAsDouble());}}
        var ref=new JsonObject();var lo=new JsonArray();var hi=new JsonArray();for(int i=0;i<3;i++){lo.add(low[i]);hi.add(high[i]);}ref.add("min",lo);ref.add("max",hi);return ref;
    }
    private static float snap(float angle){return Math.round(angle/(float)(Math.PI*.5))*(float)(Math.PI*.5);}
    private static void collect(ModelPart part,String name,Matrix4f parent,List<NativePart> result,Set<ModelPart> seen){
        if(!seen.add(part))return;
        var pose=part.getInitialPose();Matrix4f rest=new Matrix4f(parent).translate(pose.x,pose.y,pose.z).rotateZYX(snap(pose.zRot),snap(pose.yRot),snap(pose.xRot));
        var cubes=((ModelPartChildrenAccessor)(Object)part).blacklight$cubes();
        var best=cubes.stream().max(Comparator.comparingDouble(c->Math.max(.05,c.maxX-c.minX)*Math.max(.05,c.maxY-c.minY)*Math.max(.05,c.maxZ-c.minZ))).orElse(null);
        if(best!=null)result.add(new NativePart(name,part,rest,best));
        ((ModelPartChildrenAccessor)(Object)part).blacklight$children().forEach((n,c)->collect(c,n,rest,result,seen));
    }
    private static NativePart choose(String species,String bone,List<NativePart> parts,JsonObject reference){
        List<String> names=new ArrayList<>();
        if(bone.equals("body"))names.addAll(species.contains("guardian")?List.of("head"):
                species.endsWith(":magma_cube")?List.of("inside_cube"):List.of("body","upper_body","cube","bone"));
        else if(bone.equals("head"))names.addAll(species.contains("guardian")?List.of("eye"):
                species.endsWith(":bee")?List.of("body"):species.endsWith(":slime")?List.of("cube"):
                species.endsWith(":magma_cube")?List.of("inside_cube"):List.of("head","head_parts"));
        else if(bone.equals("abdomen"))names.addAll(List.of("body1","body","abdomen"));
        else if(bone.equals("neck"))names.addAll(List.of("head_parts","neck","head"));
        else if(bone.equals("shell_upper"))names.addAll(List.of("lid","body"));
        else if(bone.equals("shell_lower"))names.addAll(List.of("base","body"));
        else if(bone.equals("tail"))names.addAll(List.of("tail","tail0","tail_fin"));
        else if(bone.equals("arms"))names.add("arms");
        else if(bone.startsWith("segment_"))names.add("body"+bone.substring(8));
        else if(bone.startsWith("rod_")){String[] n=bone.split("_");names.add("part"+(Integer.parseInt(n[1])*4+Integer.parseInt(n[2])));}
        else if(bone.startsWith("layer_"))names.add("cube"+(7-Math.round(Integer.parseInt(bone.substring(6))*7/5F)));
        else if(bone.startsWith("tentacle_")||species.contains("squid")&&bone.startsWith("arm_"))names.add("tentacle"+bone.substring(bone.lastIndexOf('_')+1));
        else {
            boolean positive=bone.endsWith("_1")||bone.startsWith("leg_1_");String side=positive?"right":"left";
            if(bone.startsWith("wing_"))names.add(side+"_wing");
            else if(bone.startsWith("fin_"))names.addAll(List.of(side+"_fin",side+"_blue_fin"));
            else if(bone.startsWith("front_"))names.addAll(List.of(side+"_front_leg",side+"_arm"));
            else if(bone.startsWith("hind_"))names.addAll(List.of(side+"_hind_leg",side+"_leg"));
            else if(bone.startsWith("arm_"))names.add(side+"_arm");
            else if(bone.startsWith("leg_")&&bone.split("_").length==3){
                int n=Integer.parseInt(bone.substring(bone.lastIndexOf('_')+1));
                if(species.endsWith(":bee"))names.add(List.of("front_legs","middle_legs","back_legs").get(Math.clamp(n,0,2)));
                else names.add(side+List.of("_front_leg","_middle_front_leg","_middle_hind_leg","_hind_leg").get(Math.clamp(n,0,3)));
            }else if(bone.startsWith("leg_"))names.add(side+"_leg");
            else names.add(bone);
        }
        for(String name:names)for(var part:parts)if(part.name.equals(name))return part;
        // Nonstandard species bones are matched to native rest sockets, not moving world positions.
        var lo=reference.getAsJsonArray("min");var hi=reference.getAsJsonArray("max");
        Vector3f source=nativePoint((lo.get(0).getAsFloat()+hi.get(0).getAsFloat())*.5F,(lo.get(1).getAsFloat()+hi.get(1).getAsFloat())*.5F,(lo.get(2).getAsFloat()+hi.get(2).getAsFloat())*.5F);
        return parts.stream().filter(p->!p.name.contains("saddle")&&!p.name.contains("baby")&&!p.name.equals("hat"))
                .min(Comparator.comparingDouble(p->{var c=p.cube;return new Matrix4f(p.rest).transformPosition(new Vector3f((c.minX+c.maxX)*.5F,(c.minY+c.maxY)*.5F,(c.minZ+c.maxZ)*.5F)).distanceSquared(source);})).orElse(null);
    }
    private static Vector3f nativePoint(float x,float y,float z){return new Vector3f(-x*16,24-y*16,z*16);}
    private static TissueMorphogenesis.V value(Vector3f p){return new TissueMorphogenesis.V(p.x,p.y,p.z);}
    private static Vector3f vector(TissueMorphogenesis.V p){return new Vector3f(p.x(),p.y(),p.z());}
    private static TissueMorphogenesis.V direction(Matrix4f inverse,float x,float y,float z){return value(inverse.transformDirection(new Vector3f(x,y,z)).normalize());}
    private static boolean support(TissueMorphogenesis.Bone frame){
        return frame.role()==ConsumeAnatomy.RIGHT_LEG||frame.role()==ConsumeAnatomy.LEFT_LEG
                ||frame.path().contains("leg")||frame.path().contains("foot");
    }
    private static Vector3f develop(Vector3f p,TissueMorphogenesis.Bone frame,TissueEvolution.State state){
        var result=TissueEvolution.develop(frame,value(p),state,support(frame));return new Vector3f(result.x(),result.y(),result.z());
    }
    private static Triangle develop(Triangle triangle,TissueMorphogenesis.Bone frame,TissueEvolution.State state){
        if(frame==null||!state.active())return triangle;
        Vector3f a=develop(triangle.a,frame,state),b=develop(triangle.b,frame,state),c=develop(triangle.c,frame,state);
        Vector3f normal=new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        if(normal.lengthSquared()>1e-8F)normal.normalize();else normal.set(triangle.normal);
        return new Triangle(a,b,c,normal,triangle.color,triangle.face,triangle.uvA,triangle.uvB,triangle.uvC);
    }
    private static void triangles(JsonObject data,Matrix4f inverse,Vector3f ratio,Vector3f srcCentre,Vector3f centre,ModelPart.Cube cube,boolean base,List<Triangle> skin,List<Triangle> growth){
        var vs=data.getAsJsonArray("vertices");List<Vector3f> vertices=new ArrayList<>();
        for(int i=0;i<vs.size();i+=3){Vector3f p=nativePoint(vs.get(i).getAsFloat(),vs.get(i+1).getAsFloat(),vs.get(i+2).getAsFloat());inverse.transformPosition(p);p.sub(srcCentre).mul(ratio).add(centre);vertices.add(p);}
        var ts=data.getAsJsonArray("triangles");
        for(int i=0;i<ts.size();i+=4){
            Vector3f a=vertices.get(ts.get(i).getAsInt()),b=vertices.get(ts.get(i+1).getAsInt()),c=vertices.get(ts.get(i+2).getAsInt());
            Vector3f normal=new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));if(normal.lengthSquared()<1e-10F)continue;normal.normalize();
            Face face=base?face(cube,a,b,c):null;
            int color=base&&face==null?0x5e262a:ts.get(i+3).getAsInt();
            var triangle=new Triangle(a,b,c,normal,color,face);
            if(face!=null)skin.add(triangle);else growth.add(triangle);
        }
    }
    private static Face face(ModelPart.Cube cube,Vector3f a,Vector3f b,Vector3f c){
        for(var p:faces(cube)){
            var origin=p.positions[0];
            if(Math.abs(new Vector3f(a).sub(origin).dot(p.normal))<.015F&&Math.abs(new Vector3f(b).sub(origin).dot(p.normal))<.015F&&Math.abs(new Vector3f(c).sub(origin).dot(p.normal))<.015F)return p;
        }
        return null;
    }
    private static Triangle grow(Triangle t,ModelPart.Cube cube,float amount){
        if(amount>=1)return t;
        Vector3f a=grow(t.a,cube,amount),b=grow(t.b,cube,amount),c=grow(t.c,cube,amount);
        Vector3f n=new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        if(n.lengthSquared()>1e-8F)n.normalize();else n.set(t.normal);
        return new Triangle(a,b,c,n,t.color,t.face,t.uvA,t.uvB,t.uvC);
    }
    private static Vector3f grow(Vector3f v,ModelPart.Cube cube,float amount){
        // Interior wound surfaces stay in place; only protrusions emerge from their own root surface.
        Vector3f root=new Vector3f(Math.max(cube.minX,Math.min(cube.maxX,v.x)),
                Math.max(cube.minY,Math.min(cube.maxY,v.y)),Math.max(cube.minZ,Math.min(cube.maxZ,v.z)));
        return root.lerp(v,amount);
    }
    private static void emit(Triangle t,PoseStack.Pose pose,VertexConsumer out,int light,int overlay,int color,boolean skin){
        int rgba=skin?color:(color&0xff000000)|t.color;
        for(int j=0;j<4;j++){
            Vector3f v=j==0?t.a:j==1?t.b:t.c;float u=.5F,w=.5F;
            if(skin){var p=t.face;var a=p.positions[0];var b=p.positions[1];var d=p.positions[3];
                Vector3f source=j==0?t.uvA:j==1?t.uvB:t.uvC;
                Vector3f ab=new Vector3f(b).sub(a),ad=new Vector3f(d).sub(a),av=new Vector3f(source).sub(a);
                float s=av.dot(ab)/ab.lengthSquared(),r=av.dot(ad)/ad.lengthSquared();u=p.u[0]+s*(p.u[1]-p.u[0])+r*(p.u[3]-p.u[0]);w=p.v[0]+s*(p.v[1]-p.v[0])+r*(p.v[3]-p.v[0]);
            }
            out.addVertex(pose,v.x/16,v.y/16,v.z/16).setColor(rgba).setUv(u,w).setOverlay(overlay).setLight(light).setNormal(pose,t.normal.x,t.normal.y,t.normal.z);
        }
    }
    private static void emitTissue(Triangle original,Triangle triangle,TissueMorphogenesis.Bone frame,PoseStack.Pose pose,VertexConsumer out,int light,int overlay,int color,long seed,long age,int role) {
        List<TissueSkin.Vertex> polygon=null;
        if(age<TissueGrowth.ANCIENT_TICKS) {
            polygon=TissueSkin.cover(frame,new TissueSkin.Triangle(value(original.a),value(original.b),value(original.c)),
                    new TissueSkin.Triangle(value(triangle.a),value(triangle.b),value(triangle.c)),seed,age,role);
            if(polygon.size()<3)return;
        }
        int tile=role==ConsumeAnatomy.BODY?0:role==ConsumeAnatomy.HEAD?1:2;
        int normalAxis=Math.abs(original.normal.x)>Math.abs(original.normal.y)&&Math.abs(original.normal.x)>Math.abs(original.normal.z)?0:Math.abs(original.normal.y)>Math.abs(original.normal.z)?1:2;
        int horizontal=(normalAxis+1)%3,vertical=(normalAxis+2)%3;var bounds=frame.box();
        for(int i=0;i<4;i++) {
            Vector3f point,rest;
            if(polygon==null){point=i==0?triangle.a:i==1?triangle.b:triangle.c;rest=i==0?original.a:i==1?original.b:original.c;}
            else {var source=polygon.get(Math.min(i,polygon.size()-1));point=vector(source.position());rest=vector(source.rest());}
            float u=(tile*16+1+Math.clamp((rest.get(horizontal)-bounds.low().get(horizontal))/bounds.span(horizontal),0,1)*13)/128F;
            float v=(1+Math.clamp((rest.get(vertical)-bounds.low().get(vertical))/bounds.span(vertical),0,1)*13)/32F;
            out.addVertex(pose,(point.x+.012F*triangle.normal.x)/16,(point.y+.012F*triangle.normal.y)/16,(point.z+.012F*triangle.normal.z)/16).setColor(color|0x00ffffff).setUv(u,v).setOverlay(overlay).setLight(light)
                    .setNormal(pose,triangle.normal.x,triangle.normal.y,triangle.normal.z);
        }
    }
}
