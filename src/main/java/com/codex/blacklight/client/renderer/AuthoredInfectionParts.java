package com.codex.blacklight.client.renderer;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.animation.ReceiverModelPose;
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
    private record Triangle(Vector3f a,Vector3f b,Vector3f c,Vector3f normal,int color,Face face) {}
    private record Piece(ModelPart.Cube cube,List<Triangle> skin,List<Triangle> growth,int anatomy) {}
    public record Wound(float u,float v,float width,float height){}
    public static final class Binding {
        private final Map<ModelPart,List<Piece>> pieces=new IdentityHashMap<>();
        private final Map<ModelPart,Pending> pending=new IdentityHashMap<>();
        private final List<String> assignments=new ArrayList<>();
        private final List<Wound> wounds=new ArrayList<>();
        public List<Wound> wounds(){return List.copyOf(wounds);}
        private record Pending(PoseStack.Pose pose,int light,int overlay,int color) {}
        public boolean available(){return !pieces.isEmpty();}
        public List<String> assignments(){return List.copyOf(assignments);}
        public boolean renderSkin(ModelPart part,PoseStack.Pose pose,VertexConsumer out,int light,int overlay,int color){
            var matches=pieces.get(part);if(matches==null)return false;
            Set<ModelPart.Cube> replaced=Collections.newSetFromMap(new IdentityHashMap<>());
            for(var p:matches)if(!p.skin.isEmpty()&&replaced.add(p.cube)) {
                for(var triangle:p.skin)emit(triangle,pose,out,light,overlay,color,true);
            }
            if(replaced.isEmpty())return false;
            for(var cube:((ModelPartChildrenAccessor)(Object)part).blacklight$cubes())
                if(!replaced.contains(cube))cube.compile(pose,out,light,overlay,color);
            capture(part,pose,light,overlay,color);return true;
        }
        public void capture(ModelPart part,PoseStack.Pose pose,int light,int overlay,int color){
            if(!pieces.containsKey(part)||pending.containsKey(part))return;
            PoseStack copy=new PoseStack();copy.last().pose().set(pose.pose());copy.last().normal().set(pose.normal());
            pending.put(part,new Pending(copy.last(),light,overlay,color));
        }
        public void flush(MultiBufferSource buffers){flush(buffers,false,false);}
        public void flush(MultiBufferSource buffers,boolean omitBody,boolean onlyBody){
            if(pending.isEmpty())return;
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            for(var e:pending.entrySet())for(var p:pieces.get(e.getKey())) {
                if(omitBody&&p.anatomy==ConsumeAnatomy.BODY||onlyBody&&p.anatomy!=ConsumeAnatomy.BODY)continue;
                if(out instanceof ConsumeAnatomy.Consumer consumer)consumer.anatomy(p.anatomy);
                var q=e.getValue();
                for(var triangle:p.growth)emit(triangle,q.pose,out,q.light,q.overlay,q.color,false);
            }
            pending.clear();
        }
        public void clear(){pending.clear();}
    }
    private AuthoredInfectionParts(){}
    public static void reload(){SOURCES.clear();BINDINGS.clear();FACES.clear();InfectedWoundTextures.reload();}
    public static Binding bind(String species,EntityModel<?> model){return bind(species,model,false);}
    public static Binding bind(String species,EntityModel<?> model,boolean fur){
        Binding cached=BINDINGS.computeIfAbsent(model,k->new HashMap<>()).computeIfAbsent(species+(fur?"#fur":""),k->build(species,model,fur));
        Binding fresh=new Binding();fresh.pieces.putAll(cached.pieces);fresh.assignments.addAll(cached.assignments);fresh.wounds.addAll(cached.wounds);return fresh;
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
            binding.pieces.computeIfAbsent(target.part,k->new ArrayList<>()).add(new Piece(cube,List.copyOf(skin),List.copyOf(growth),anatomy.getOrDefault(target.part,ConsumeAnatomy.BODY)));
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
    private static void emit(Triangle t,PoseStack.Pose pose,VertexConsumer out,int light,int overlay,int color,boolean skin){
        int rgba=skin?color:(color&0xff000000)|t.color;
        for(int j=0;j<4;j++){
            Vector3f v=j==0?t.a:j==1?t.b:t.c;float u=.5F,w=.5F;
            if(skin){var p=t.face;var a=p.positions[0];var b=p.positions[1];var d=p.positions[3];
                Vector3f ab=new Vector3f(b).sub(a),ad=new Vector3f(d).sub(a),av=new Vector3f(v).sub(a);
                float s=av.dot(ab)/ab.lengthSquared(),r=av.dot(ad)/ad.lengthSquared();u=p.u[0]+s*(p.u[1]-p.u[0])+r*(p.u[3]-p.u[0]);w=p.v[0]+s*(p.v[1]-p.v[0])+r*(p.v[3]-p.v[0]);
            }
            out.addVertex(pose,v.x/16,v.y/16,v.z/16).setColor(rgba).setUv(u,w).setOverlay(overlay).setLight(light).setNormal(pose,t.normal.x,t.normal.y,t.normal.z);
        }
    }
}
