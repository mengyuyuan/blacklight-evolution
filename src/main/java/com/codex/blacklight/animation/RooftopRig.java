package com.codex.blacklight.animation;

import com.google.gson.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Reads the actual rooftop performance. The accessory retains the preview mesh buffers and animation curves. */
public final class RooftopRig {
    public static final String ASSET="/assets/blacklight/models/backspines-organ.glb";
    public static final RooftopRig INSTANCE=load();
    private static RooftopRig load(){try(var s=RooftopRig.class.getResourceAsStream(ASSET)){
        if(s==null)throw new IOException(ASSET);return new RooftopRig(s.readAllBytes());
    }catch(IOException e){throw new IllegalStateException(ASSET,e);}}
    public final int[] parent,mesh;
    private final String[] names;
    private final float[][] tr,qr,sc;
    private final List<Channel> channels=new ArrayList<>();
    private final JsonObject json;private final ByteBuffer bin;
    private final Map<Integer,float[]> cache=new HashMap<>();
    public final int[] quill=new int[6],rib=new int[6];
    private final int[] socketSlot;
    private final int[] valveSide;
    public final int chest;
    private final Matrix4f[] inverseBind;
    private final Vector3f[] restPosition;
    public final Geometry[] geometry;
    public record Geometry(float[] pos,float[] normal,float[] uv,float[] pigment,byte[] vessels){
        public boolean emissive(int vertex){return uv[vertex*2]>=7F/9;}
        public int color(int vertex){
            int i=vertex*3;
            return 0xff000000|Math.round(255*pigment[i])<<16|Math.round(255*pigment[i+1])<<8|Math.round(255*pigment[i+2]);
        }
    }
    private record Channel(int node,String type,float[] times,float[] values){}
    private JsonObject obj(String n,int i){return json.getAsJsonArray(n).get(i).getAsJsonObject();}
    private static int i(JsonObject o,String s){return o.get(s).getAsInt();}
    private static float[] vec(JsonObject o,String k,float... defaults){
        if(o.has(k))for(int i=0;i<defaults.length;i++)defaults[i]=o.getAsJsonArray(k).get(i).getAsFloat();return defaults;
    }
    private float[] read(int id){
        if(cache.containsKey(id))return cache.get(id);var a=obj("accessors",id);var v=obj("bufferViews",i(a,"bufferView"));
        int c=switch(a.get("type").getAsString()){case "VEC2"->2;case "VEC3"->3;case "VEC4"->4;case "MAT4"->16;case "SCALAR"->1;default->throw new IllegalArgumentException("shape");};
        int offset=(v.has("byteOffset")?i(v,"byteOffset"):0)+(a.has("byteOffset")?i(a,"byteOffset"):0),kind=i(a,"componentType");
        int size=switch(kind){case 5126->4;case 5123->2;default->throw new IllegalArgumentException("component "+kind);};
        int stride=v.has("byteStride")?i(v,"byteStride"):size*c;
        var arr=new float[i(a,"count")*c];for(int n=0;n<arr.length;n++){int p=offset+n/c*stride+n%c*size;arr[n]=kind==5126?bin.getFloat(p):Short.toUnsignedInt(bin.getShort(p));}
        cache.put(id,arr);return arr;
    }
    private float[] attr(JsonObject o,String k){return o.has(k)?read(i(o,k)):null;}
    private RooftopRig(byte[] bytes)throws IOException{
        var buf=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if(buf.getInt()!=0x46546c67||buf.getInt()!=2||buf.getInt()!=bytes.length)throw new IOException("GLB header");
        int len=buf.getInt();if(buf.getInt()!=0x4e4f534a)throw new IOException("GLB JSON");
        var doc=new byte[len];buf.get(doc);json=JsonParser.parseString(new String(doc,StandardCharsets.UTF_8)).getAsJsonObject();
        int binary=buf.getInt();if(buf.getInt()!=0x004e4942||binary!=buf.remaining())throw new IOException("GLB BIN");bin=buf.slice().order(ByteOrder.LITTLE_ENDIAN);
        int n=json.getAsJsonArray("nodes").size();parent=new int[n];mesh=new int[n];names=new String[n];Arrays.fill(parent,-1);Arrays.fill(mesh,-1);tr=new float[n][];qr=new float[n][];sc=new float[n][];
        for(int k=0;k<n;k++){var node=obj("nodes",k);names[k]=node.get("name").getAsString();if(node.has("mesh"))mesh[k]=i(node,"mesh");
            if(node.has("children"))for(var child:node.getAsJsonArray("children"))parent[child.getAsInt()]=k;
            tr[k]=vec(node,"translation",0,0,0);qr[k]=vec(node,"rotation",0,0,0,1);sc[k]=vec(node,"scale",1,1,1);}
        var anim=obj("animations",0);
        for(var el:anim.getAsJsonArray("channels")){var ch=el.getAsJsonObject();var target=ch.getAsJsonObject("target");var sm=anim.getAsJsonArray("samplers").get(i(ch,"sampler")).getAsJsonObject();
            if(!"LINEAR".equals(sm.get("interpolation").getAsString()))throw new IOException("interpolation");
            channels.add(new Channel(i(target,"node"),target.get("path").getAsString(),read(i(sm,"input")),read(i(sm,"output"))));}
        for(int k=0;k<6;k++){rib[k]=id("Rib."+k);quill[k]=id("Quill."+k);}chest=id("UpBody");
        socketSlot = new int[n];
        valveSide = new int[n];
        Arrays.fill(socketSlot, -1);
        for (int slot = 0; slot < quill.length; slot++) {
            socketSlot[quill[slot]] = slot;
            socketSlot[id("Sheath." + slot)] = slot;
            for (int sign : new int[]{-1, 1}) {
                int valve = id("Valve." + slot + "." + sign);
                socketSlot[valve] = slot;
                valveSide[valve] = sign;
            }
        }
        inverseBind=new Matrix4f[n];restPosition=new Vector3f[n];
        var bindWorld=new Matrix4f[n];
        for(int k=0;k<n;k++){
            bindWorld[k]=parent[k]<0?new Matrix4f():new Matrix4f(bindWorld[parent[k]]);
            var q=qr[k];bindWorld[k].translate(tr[k][0],tr[k][1],tr[k][2]).rotate(new Quaternionf(q[0],q[1],q[2],q[3])).scale(sc[k][0],sc[k][1],sc[k][2]);
            inverseBind[k]=new Matrix4f(bindWorld[k]).invert();restPosition[k]=bindWorld[k].getTranslation(new Vector3f());
        }
        geometry=new Geometry[json.getAsJsonArray("meshes").size()];
        for(int k=0;k<geometry.length;k++){var prim=obj("meshes",k).getAsJsonArray("primitives").get(0).getAsJsonObject();var a=prim.getAsJsonObject("attributes");
            float[] pos=attr(a,"POSITION"),uv=attr(a,"TEXCOORD_0");
            String name=obj("meshes",k).get("name").getAsString();
            geometry[k]=new Geometry(pos,attr(a,"NORMAL"),uv,attr(a,"COLOR_0"),vesselBands(name,pos,uv));}
    }
    private static byte[] vesselBands(String name,float[] pos,float[] uv){
        byte[] bands=new byte[pos.length/3];
        for(int v=0;v<bands.length;v++){
            int tile=(int)(uv[v*2]*9);
            if(tile<5){bands[v]=-1;continue;}
            float x=Math.abs(pos[v*3]),y=pos[v*3+1];
            float along;
            if(name.startsWith("Scapular root"))along=.30F*y/.24F;
            else if(name.startsWith("Quill"))along=.62F+.38F*y;
            else if(name.startsWith("Valve")||name.equals("Sheath"))along=.46F+.18F*y/.22F;
            else along=.22F+.32F*x/.27F;
            bands[v]=(byte)Math.round(23*Math.max(0,Math.min(1,along)));
        }
        return bands;
    }
    public int id(String key){int idx=Arrays.asList(names).indexOf(key);if(idx<0)throw new IllegalStateException(key);return idx;}
    public int side(int node){
        for(int n=node;n>=0;n=parent[n]){
            for(int i=0;i<6;i++)if(n==rib[i])return i%2;
        }
        return -1;
    }
    public boolean attached(int n){for(int k=parent[n];k>=0;k=parent[k])if(k==chest)return mesh[n]>=0;return false;}
    public Matrix4f[] sample(float t){
        float[][] a=tr.clone(),b=qr.clone(),c=sc.clone();
        for(var ch:channels){float[] ts=ch.times,val=ch.values;int ix=Arrays.binarySearch(ts,t);if(ix<0)ix=-ix-2;ix=Math.max(0,Math.min(ts.length-1,ix));int j=Math.min(ix+1,ts.length-1);
            float u=j==ix?0:Math.max(0,Math.min(1,(t-ts[ix])/(ts[j]-ts[ix])));
            int n=ch.node;if(ch.type.equals("rotation")){int x=ix*4,y=j*4;var q=new Quaternionf(val[x],val[x+1],val[x+2],val[x+3]).slerp(new Quaternionf(val[y],val[y+1],val[y+2],val[y+3]),u);b[n]=new float[]{q.x,q.y,q.z,q.w};}
            else{int x=ix*3,y=j*3;float[] v=new float[3];for(int h=0;h<3;h++)v[h]=val[x+h]+(val[y+h]-val[x+h])*u;(ch.type.equals("scale")?c:a)[n]=v;}}
        var m=new Matrix4f[parent.length];
        for(int n=0;n<m.length;n++){var q=b[n];m[n]=(parent[n]<0?new Matrix4f():new Matrix4f(m[parent[n]])).translate(a[n][0],a[n][1],a[n][2]).rotate(new Quaternionf(q[0],q[1],q[2],q[3])).scale(c[n][0],c[n][1],c[n][2]);}return m;
    }
    // Seconds in the source clip. Anchor every release to the simulation tick that creates its real projectile.
    public static boolean four(String key){return key.startsWith("spines_ground_")&&key.endsWith("_sweep_charged");}
    public static boolean fullBody(String key){return key.startsWith("spines_rack_still")||key.equals("spines_scapula_deploy")
            ||key.startsWith("spines_ground_")&&!key.endsWith("_throw")||key.startsWith("spines_light_")&&!key.contains("_flight_");}
    public static float time(String key,float t){
        if(key.startsWith("spines_rack_"))return t/20;
        if(key.equals("spines_scapula_deploy"))return ramp(t,0,17,0,1.0f);
        if(key.isEmpty()||key.startsWith("spines_scapula"))return 1.0f;
        if(key.startsWith("spines_light_")){
            if(key.endsWith("_lift"))return t<4?ramp(t,0,4,1.69f,1.98f):ramp(t,4,13,1.98f,2.20f);
            if(key.endsWith("_burst")&&t>=5)return t<=10?ramp(t,5,10,1.68f,1.98f):ramp(t,10,18,1.98f,2.2f);
            int at=key.endsWith("_burst")?4:5;return t<at?ramp(t,0,at,1f,1.52f):(t<=9?ramp(t,at,9,1.52f,1.69f):ramp(t,9,14,1.69f,1.74f));}
        if(key.startsWith("spines_air_")){
            if(key.endsWith("_draw"))return ramp(t,0,6,2.20F,3.34F);
            if(key.endsWith("_charged"))return t<=1?ramp(t,0,1,3.34F,3.52F):ramp(t,1,16,3.52F,5.5F);
            if(key.endsWith("_throw"))return t<=1?ramp(t,0,1,1.25F,1.52F):ramp(t,1,16,1.52F,2.22F);
        }
        if(key.endsWith("_draw"))return ramp(t,0,key.startsWith("spines_ground_")?22:10,2.20f,3.34f);
        if(four(key))return t<=5?ramp(t,0,5,3.34f,3.52f):t<=8?ramp(t,5,8,3.52f,3.655f):ramp(t,8,45,3.655f,5.5f);
        if(key.endsWith("_charged"))return t<=5?ramp(t,0,5,3.34f,3.52f):ramp(t,5,16,3.52f,5.5f);
        if(key.endsWith("_throw"))return t<4?ramp(t,0,4,1.25f,1.52f):t<10?ramp(t,4,10,1.52f,1.98f):ramp(t,10,16,1.98f,2.22f);
        return 1;
    }
    private static float ramp(float t,float a,float b,float x,float y){return x+(y-x)*Math.max(0,Math.min(1,(t-a)/(b-a)));}
    public static boolean reflect(String k){if(k.startsWith("spines_rack_"))return k.endsWith("_mirror");if(k.isEmpty()||k.startsWith("spines_scapula"))return false;boolean lead=ScapulaCycle.rightLead(k);return k.endsWith("_lift")?lead:!lead;}
    public static int index(String k,boolean right,int slot,int age){
        int natural=(right!=reflect(k))?0:1;
        if(k.startsWith("spines_ground_")&&k.endsWith("_charged")&&!four(k))return natural+2;
        return four(k)?2+Math.min(3,Math.max(0,age-5)):natural+2*Math.abs(slot);}
    public static Vector3f center(int i){float length=i<2?1.03F:i<4?.95F:.82F;return new Vector3f(0,.45F*length,-.026F*length);}
    /** Release tick of a socket in clip space, including the paired aerial satellites. */
    public static int releaseTick(String action,int slot){
        boolean right=(slot%2==0)!=reflect(action);
        if(four(action))return slot>=2?slot+3:-1;
        int shot=ScapulaCycle.shotTick(action,right);
        if(shot<0)return -1;
        if(action.startsWith("spines_ground_")&&action.endsWith("_charged"))return slot/2==1?shot:-1;
        if(action.startsWith("spines_air_")){
            if(slot<2&&right!=ScapulaCycle.rightLead(action))return -1;
            return slot<4||action.endsWith("_charged")?shot:-1;
        }
        return slot<2?shot:-1;
    }
    public static float refill(double since,int slot){
        if(since<0)return 1;
        float t=(float)((since-1.05-.35*(slot/2))/5.3);
        return ScapulaVolley.ease(t);
    }
    public int socketSlot(int node) { return socketSlot[node]; }

    /** The valve pivots around its hinge; the projectile socket itself stays on the authored track. */
    public Matrix4f refillFrame(Matrix4f frame, int node, float fill, double sinceShot, boolean mirrored) {
        int slot = socketSlot[node];
        if (slot < 0) return frame;
        if (node == quill[slot]) {
            float width = .36F + .64F * (float)Math.sqrt(fill);
            return frame.translate(0, .057F - .084F * (1 - fill), 0).scale(width, fill, width);
        }
        if (fill >= 1 || sinceShot < 0) return frame;
        float open = ScapulaVolley.ease((float)sinceShot / .85F) * (1 - ScapulaVolley.ease((fill - .48F) / .52F));
        int sign = valveSide[node] * (mirrored ? -1 : 1);
        if (sign != 0) return frame.rotateZ(-sign * .31F * open);
        // The back of the sheath stays attached while the advancing quill distends its mouth.
        float swell = 4 * fill * (1 - fill);
        return frame.scale(1 + .17F * swell, 1 - .08F * swell, 1 + .11F * swell);
    }
    public static Matrix4f mirror(Matrix4f x){return new Matrix4f().scale(-1,1,1).mul(x).scale(-1,1,1);}
    /** Ignore the source's disappearance scale on the handoff tick; its launch socket still has full orientation. */
    public Matrix4f frame(Matrix4f[] pose,int n,String key,Matrix4f nativeChest){
        if(nativeChest==null)return reflect(key)?mirror(pose[n]):new Matrix4f(pose[n]);
        var local=new Matrix4f(pose[chest]).invert().mul(pose[n]);
        if(reflect(key))local=mirror(local);
        return new Matrix4f(nativeChest).translate(0,.97F,.018F).mul(local);
    }
    /** Transfer source joint rotations and translations through the ordinary player's bind lengths. */
    public void projectPlayer(Matrix4f[] src,boolean mirrored,Matrix4f[] target){
        var map=mirrored?NativeBind.mirrored:NativeBind.source;
        Matrix4f[] skin=new Matrix4f[target.length];
        for(var bone:NativeBind.skeleton.bones()){
            int i=bone.getId(),driver=map[i];
            Matrix4f delta=new Matrix4f(src[driver]).mul(inverseBind[driver]);
            if(mirrored)delta=mirror(delta);
            var bind=NativeBind.rest[i].getTranslation(new Vector3f());
            Vector3f position;
            if(bone.parent()==null){
                var travel=src[driver].getTranslation(new Vector3f()).sub(restPosition[driver]);
                if(mirrored)travel.x=-travel.x;
                position=new Vector3f(bind).add(travel);
            }
            else{
                int parent=bone.parent().getId(),prior=map[parent];
                position=skin[parent].transformPosition(new Vector3f(bind));
                if(driver!=prior){
                    var sourceBind=restPosition[driver];
                    var expected=new Matrix4f(src[prior]).mul(inverseBind[prior]).transformPosition(new Vector3f(sourceBind));
                    var translation=src[driver].getTranslation(new Vector3f()).sub(expected);
                    if(mirrored)translation.x=-translation.x;
                    position.add(translation);
                }
            }
            skin[i]=delta.setTranslation(position.sub(delta.transformDirection(bind)));
            target[i]=new Matrix4f(skin[i]).mul(NativeBind.rest[i]);
        }
        fitFoot(src,target,false,mirrored);
        fitFoot(src,target,true,mirrored);
    }

    /** Follow the source heel targets with the native player's thigh and shin lengths. */
    private void fitFoot(Matrix4f[] pose,Matrix4f[] target,boolean right,boolean mirrored){
        var sk=NativeBind.skeleton;
        String side=right?"_R":"_L";
        int thigh=sk.searchJointByName("Thigh"+side).getId(),shin=sk.searchJointByName("Leg"+side).getId();
        int sole=sk.searchJointByName("Knee"+side).getId();
        int foot=id(right!=mirrored?"RightFoot":"LeftFoot");
        var soleBind=new Vector3f(right?.125F:-.125F,.052F,0);
        var displacement=pose[foot].getTranslation(new Vector3f()).sub(restPosition[foot]);
        if(mirrored)displacement.x=-displacement.x;
        var ankle=new Vector3f(soleBind).add(displacement);
        solveLeg(target,thigh,shin,soleBind,ankle,false);
        var footRotation=new Matrix4f(pose[foot]).mul(inverseBind[foot]);
        if(mirrored)footRotation=mirror(footRotation);
        footRotation.setTranslation(new Vector3f(ankle).sub(footRotation.transformDirection(new Vector3f(soleBind))));
        target[sole]=footRotation.mul(NativeBind.rest[sole]);
    }
    private static void solveLeg(Matrix4f[] target,int thigh,int shin,Vector3f soleBind,Vector3f ankle,boolean planted){
        var hip=target[thigh].getTranslation(new Vector3f());
        var initialKnee=target[shin].getTranslation(new Vector3f());
        var shinBind=NativeBind.rest[shin].getTranslation(new Vector3f());
        float upper=NativeBind.rest[thigh].getTranslation(new Vector3f()).distance(shinBind),lower=shinBind.distance(soleBind);
        var axis=new Vector3f(ankle).sub(hip);
        float distance=Math.max(.001F,Math.min(upper+lower-.0001F,axis.length()));
        if(axis.lengthSquared()<1e-8F)axis.set(0,-1,0);else axis.normalize();
        ankle.set(hip).fma(distance,axis);
        var pole=planted ? new Vector3f(0,0,-1) : new Vector3f(initialKnee).sub(hip);
        pole.fma(-pole.dot(axis),axis);
        if(pole.lengthSquared()<1e-8F){pole.set(0,0,-1);pole.fma(-pole.dot(axis),axis);}
        float along=(upper*upper-lower*lower+distance*distance)/(2*distance);
        var knee=new Vector3f(hip).fma(along,axis).fma((float)Math.sqrt(Math.max(0,upper*upper-along*along)),pole.normalize());
        var oldShin=new Matrix4f(target[shin]).mul(NativeBind.inverse[shin]).transformDirection(new Vector3f(soleBind).sub(shinBind));
        aimSegment(target[thigh],hip,new Vector3f(initialKnee).sub(hip),new Vector3f(knee).sub(hip));
        aimSegment(target[shin],knee,oldShin,new Vector3f(ankle).sub(knee));
    }
    private static void aimSegment(Matrix4f bone,Vector3f pivot,Vector3f from,Vector3f to){
        var delta=new Quaternionf().rotationTo(from.normalize(),to.normalize());
        bone.setTranslation(0,0,0).rotateLocal(delta).setTranslation(pivot);
    }
    /** The hips follow the step. Attach the firing torso at the gait's waist,
     * then carry its shoulders and arms together so the recoil stays articulated. */
    public static void groundStride(Matrix4f[] shot, Matrix4f[] gait, float weight, boolean standby) {
        if (weight <= 0) return;
        var skeleton = NativeBind.skeleton;
        int root = skeleton.rootJoint.getId(), torso = skeleton.torso.getId();
        var original = shot.clone();
        var shotWaist = new Matrix4f(original[root]).invert().mul(original[torso]);
        var gaitWaist = new Matrix4f(gait[root]).invert().mul(gait[torso]);
        var stepWaist = BoneTransform.fromMatrix(gaitWaist);
        var fireWaist = BoneTransform.fromMatrix(shotWaist);
        if (!standby) stepWaist.rotation().slerp(fireWaist.rotation(), .70F);

        shot[root] = weight >= 1 ? gait[root] : BoneTransform.blend(BoneTransform.fromMatrix(original[root]),
                BoneTransform.fromMatrix(gait[root]), weight).toMatrix();
        shot[torso] = new Matrix4f(shot[root]).mul(BoneTransform.blend(fireWaist, stepWaist, weight).toMatrix());
        var upperShift = new Matrix4f(shot[torso]).mul(new Matrix4f(original[torso]).invert());
        for (var bone : skeleton.bones()) {
            int id = bone.getId();
            if (id == root || id == torso) continue;
            int parentId = bone.parent().getId();
            if (!standby && isUpperBody(bone, skeleton.torso)) {
                shot[id] = new Matrix4f(upperShift).mul(original[id]);
            } else if (weight >= 1) {
                shot[id] = gait[id];
            } else {
                var from = new Matrix4f(original[parentId]).invert().mul(original[id]);
                var to = new Matrix4f(gait[parentId]).invert().mul(gait[id]);
                var local = BoneTransform.blend(BoneTransform.fromMatrix(from), BoneTransform.fromMatrix(to), weight);
                shot[id] = new Matrix4f(shot[parentId]).mul(local.toMatrix());
            }
        }
    }

    private static boolean isUpperBody(BlacklightSkeleton.Bone bone, BlacklightSkeleton.Bone torso) {
        for (var ancestor = bone.parent(); ancestor != null; ancestor = ancestor.parent())
            if (ancestor == torso) return true;
        return false;
    }

    /** Keep the soles on their marks while the animated pelvis shifts weight into each knee. */
    public static void plantGroundShot(Matrix4f[] bones) {
        var skeleton = NativeBind.skeleton;
        int root = skeleton.rootJoint.getId();
        var rest = NativeBind.rest[root];
        var source = bones[root].getTranslation(new Vector3f());
        var shift = new Vector3f(
                net.minecraft.util.Mth.clamp((source.x-rest.m30())*.58F,-.065F,.065F),
                -.055F+net.minecraft.util.Mth.clamp((source.y-rest.m31())*.70F,-.13F,0F),
                net.minecraft.util.Mth.clamp((source.z-rest.m32())*.44F,-.06F,.06F));
        var correction = rest.getTranslation(new Vector3f()).add(shift).sub(source);
        for (int i=0;i<bones.length;i++) bones[i]=new Matrix4f(bones[i]).translateLocal(correction);
        bones[root]=new Matrix4f(rest).translateLocal(shift);
        plantLeg(bones,false,shift);
        plantLeg(bones,true,shift);
    }
    private static void plantLeg(Matrix4f[] bones,boolean right,Vector3f shift) {
        var skeleton=NativeBind.skeleton;
        int thigh=(right?skeleton.thighR:skeleton.thighL).getId();
        int shin=(right?skeleton.legR:skeleton.legL).getId();
        int sole=skeleton.searchJointByName(right?"Knee_R":"Knee_L").getId();
        float side=right?1:-1;
        var soleBind=new Vector3f(side*.125F,.052F,0);
        var anchor=new Vector3f(soleBind).add(side*.17F,0,side*.095F);
        bones[thigh]=new Matrix4f(NativeBind.rest[thigh]).translateLocal(shift);
        bones[shin]=new Matrix4f(NativeBind.rest[shin]).translateLocal(shift);
        solveLeg(bones,thigh,shin,soleBind,anchor,true);
        bones[sole]=new Matrix4f(NativeBind.rest[sole]).translateLocal(anchor.sub(soleBind));
    }
    public static boolean groundShot(String key){
        return key.startsWith("spines_ground_")||key.startsWith("spines_light_")&&!key.contains("_flight_");
    }
    private static final class NativeBind {
        static final BlacklightSkeleton skeleton=BlacklightSkeleton.get();
        static final Matrix4f[] rest=skeleton.getPoseMatrices(new BlacklightPose());
        static final Matrix4f[] inverse=Arrays.stream(rest).map(m->new Matrix4f(m).invert()).toArray(Matrix4f[]::new);
        static final int[] source=new int[rest.length],mirrored=new int[rest.length];
        static {
            for(var b:skeleton.bones()){
                String node=switch(b.getName()){
                    case "Root" -> "DownBody";
                    case "Chest","Torso","neck" -> "UpBody";
                    case "Head" -> "Head";
                    case "Arm_R" -> "RightArm";case "Arm_L" -> "LeftArm";
                    case "Hand_R" -> "RightForeArm";case "Hand_L" -> "LeftForeArm";
                    case "Tool_R" -> "RightHandLocator";case "Tool_L" -> "LeftHandLocator";
                    case "Thigh_R" -> "RightLeg";case "Thigh_L" -> "LeftLeg";
                    case "Leg_R" -> "RightLowerLeg";case "Leg_L" -> "LeftLowerLeg";
                    case "Knee_R", "Elbow_R", "Knee_L", "Elbow_L" -> null;
                    default -> null;
                };
                if(node==null){
                    source[b.getId()]=b.parent()==null?0:source[b.parent().getId()];
                    mirrored[b.getId()]=b.parent()==null?0:mirrored[b.parent().getId()];
                }else{
                    source[b.getId()]=INSTANCE.id(node);
                    String opposite=node.startsWith("Right")?"Left"+node.substring(5):node.startsWith("Left")?"Right"+node.substring(4):node;
                    mirrored[b.getId()]=INSTANCE.id(opposite);
                }
            }
        }
    }
    public Matrix4f exit(String key,float tick,int idx,Matrix4f nativeChest){
        float time=time(key,tick);var sampled=sample(time);
        var m=frame(sampled,parent[quill[idx]],key,nativeChest);return m.translate(0,.057F,0);
    }
}
