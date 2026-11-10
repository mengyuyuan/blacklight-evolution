package com.codex.blacklight.combat;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.lang.Math;

/** Sharp faces from the shipped visible mesh, sampled on the authoritative attack clock.
 * Broad combat bounds collect candidates; sharp contact authorizes damage and cuts. */
public final class WeaponCutContact {
    public enum Kind { NONE, SLICE, PUNCTURE }
    public record Contact(int region,String edge,Vec3 point,Vec3 travel,Vec3 normal) {}
    public record Report(Kind kind,List<Contact> contacts){
        public static final Report NONE=new Report(Kind.NONE,List.of());
        public int severMask(){
            if(kind!=Kind.SLICE)return 0;
            int mask=0;for(var contact:contacts)if(contact.travel().lengthSqr()>.0009)mask|=contact.region();
            return mask&63;
        }
        public Vec3 direction(){Vec3 v=Vec3.ZERO;for(var c:contacts)v=v.add(c.travel());return v.normalize();}
    }
    private record Face(String edge,String bone,Vec3 a,Vec3 b,Vec3 c) {}
    private record Triangle(String edge,Vec3 a,Vec3 b,Vec3 c){
        Vec3 center(){return a.add(b).add(c).scale(1.0/3);}
    }
    private record Frame(double alpha,List<Triangle> faces) {}
    private record Zone(int bit,AABB box) {}
    private static final Map<PowerForm,List<Face>> GEOMETRY=new EnumMap<>(PowerForm.class);
    private static final List<Zone> ZONES=List.of(
            new Zone(1,new AABB(-.25,1.50,-.25,.25,2.02,.25)),
            new Zone(2,new AABB(.25,.72,-.15,.51,1.52,.15)),
            new Zone(4,new AABB(-.51,.72,-.15,-.25,1.52,.15)),
            new Zone(8,new AABB(.005,0,-.15,.25,.76,.15)),
            new Zone(16,new AABB(-.25,0,-.15,-.005,.76,.15)),
            new Zone(32,new AABB(-.25,.76,-.15,.25,1.50,.15)));
    private WeaponCutContact() {}
    // Adult PigModel, including its rotated horizontal torso and short legs. Never a scaled human.
    private static final List<Zone> PIG_ZONES=List.of(
            new Zone(1,new AABB(-.25,.501,-.938,.25,1.001,-.375)),
            new Zone(2,new AABB(.0625,.001,-.4375,.3125,.376,-.1875)),
            new Zone(4,new AABB(-.3125,.001,-.4375,-.0625,.376,-.1875)),
            new Zone(8,new AABB(.0625,.001,.3125,.3125,.376,.5625)),
            new Zone(16,new AABB(-.3125,.001,.3125,-.0625,.376,.5625)),
            new Zone(32,new AABB(-.3125,.376,-.5,.3125,.876,.5)));
    private static List<Zone> zones(LivingEntity v){return v instanceof net.minecraft.world.entity.animal.Pig?PIG_ZONES:ZONES;}
    private static double bodyScale(LivingEntity v){return v instanceof net.minecraft.world.entity.animal.Pig?1:v.getBbHeight()/1.95;}

    /** Created once per strike tick, shared across its victims. Nothing here trusts client hit reports. */
    public static Sweep sweep(ServerPlayer player,WeaponMove move,double from,double to,Vec3 oldFeet,float oldYaw,float yaw,WhipCurve whip){
        if(move.quake()||move.groundSpike()||!(move.form==PowerForm.CLAWS||move.form==PowerForm.BLADE||move.form==PowerForm.WHIPFIST))return Sweep.EMPTY;
        var geometry=geometry(move.form);if(geometry.isEmpty()||MotionLibrary.find(move.id).isEmpty())return Sweep.EMPTY;
        Kind kind=move.uncharged()==WeaponMove.WHIP_LONGSHOT||move.uncharged()==WeaponMove.WHIP_LUNGE?Kind.PUNCTURE:Kind.SLICE;
        // Eight intervals per tick plus authored whip reversals. No future/recovery frames.
        var alphas=new TreeSet<Double>();for(int i=0;i<=8;i++)alphas.add(i/8.0);
        if(whip!=null&&to>from)for(var sample:whip.samples())if(sample.time()>from&&sample.time()<to)alphas.add((sample.time()-from)/(to-from));
        var frames=new ArrayList<Frame>();
        for(double alpha:alphas){
            double time=from+(to-from)*alpha;
            Vec3 feet=oldFeet.lerp(player.position(),alpha);float facing=oldYaw+net.minecraft.util.Mth.wrapDegrees(yaw-oldYaw)*(float)alpha;
            var triangles=new ArrayList<Triangle>();
            if(whip!=null){
                var points=WhipCurveContact.worldPoints(whip,time,feet,facing,player);
                var visible=WhipCurveContact.visiblePoints(points,player.level(),player);
                // A wall truncating the curve must not manufacture a cutting tip at the wall.
                if(visible.getLast().distanceToSqr(points.getLast())<1e-8&&visible.size()==points.size()){
                    Vec3 tip=points.getLast(),axis=tip.subtract(points.get(points.size()-2)).normalize();
                    if(axis.lengthSqr()>.1){
                        var transform=new Matrix4f().rotate(new Quaternionf().rotationTo(new Vector3f(0,1,0),vector(axis)));
                        for(var face:geometry)triangles.add(offset(transform(face,transform),tip));
                    }
                }
            }else{
                var skeleton=BlacklightSkeleton.get();
                var visual=player.getData(ModAttachments.WEAPON_VISUAL);
                if(visual.spec()==move&&visual.attackPose()>=0)time=visual.attackPose();
                var matrices=skeleton.getPoseMatrices(MotionLibrary.pose(move.id,(float)time,false));
                var world=new Matrix4f().rotateY((float)Math.toRadians(180-facing)).scale(.9375F*player.getScale());
                Map<String,Matrix4f> transforms=new HashMap<>();
                for(var face:geometry){
                    var transform=transforms.computeIfAbsent(face.bone(),bone->new Matrix4f(world).mul(matrices[skeleton.searchJointByName(bone).getId()]));
                    var triangle=offset(transform(face,transform),feet);
                    // Match the shared combat reach progression rather than client-only appearance tuning.
                    triangles.add(new Triangle(triangle.edge(),reach(player,feet,triangle.a()),reach(player,feet,triangle.b()),reach(player,feet,triangle.c())));
                }
            }
            frames.add(new Frame(alpha,List.copyOf(triangles)));
        }
        return new Sweep(kind,List.copyOf(frames));
    }
    public static final class Sweep {
        private static final Sweep EMPTY=new Sweep(Kind.NONE,List.of());
        private final Kind kind;private final List<Frame> frames;
        private Sweep(Kind kind,List<Frame> frames){this.kind=kind;this.frames=frames;}
        public boolean sharp(){return kind!=Kind.NONE;}
        public Report trace(ServerPlayer attacker,LivingEntity victim,Vec3 previousPosition){
            if(kind==Kind.NONE||!RagdollImpact.supportedBody(victim))return Report.NONE;
            Map<String,Contact> found=new LinkedHashMap<>();
            double scale=bodyScale(victim);
            for(int i=1;i<frames.size();i++){
                Frame a=frames.get(i-1),b=frames.get(i);
                if(a.faces().size()!=b.faces().size())continue;
                for(int j=0;j<b.faces().size();j++){
                    Triangle first=a.faces().get(j),last=b.faces().get(j);
                    Vec3 travel=last.center().subtract(first.center()).scale(1/Math.max(.001,b.alpha()-a.alpha()));
                    if(travel.lengthSqr()<.0009)continue;
                    // Subdivide fast tip travel; sample the moving sharp triangles, not a swept AABB.
                    int steps=Math.clamp((int)Math.ceil(Math.max(first.a().distanceTo(last.a()),Math.max(first.b().distanceTo(last.b()),first.c().distanceTo(last.c())))/.04),1,32);
                    for(int step=0;step<=steps;step++){
                        double t=step/(double)steps,alpha=a.alpha()+(b.alpha()-a.alpha())*t;
                        Vec3 origin=previousPosition.lerp(victim.position(),alpha);
                        float facing=net.minecraft.util.Mth.rotLerp((float)alpha,victim.yBodyRotO,victim.yBodyRot);
                        Vec3 wa=first.a().lerp(last.a(),t),wb=first.b().lerp(last.b(),t),wc=first.c().lerp(last.c(),t);
                        Vec3 la=local(wa,origin,facing,scale),lb=local(wb,origin,facing,scale),lc=local(wc,origin,facing,scale);
                        for(var zone:zones(victim)){
                            String key=zone.bit()+":"+last.edge();if(found.containsKey(key)||!triangleBox(la,lb,lc,zone.box().inflate(.025)))continue;
                            Vec3 localPoint=intersectionCenter(la,lb,lc,zone.box().inflate(.025));
                            Vec3 point=localPoint.scale(scale).yRot((float)Math.toRadians(180-facing)).add(origin);
                            if(!CombatTargeting.clearRay(attacker,attacker.getEyePosition(),point))continue;
                            Vec3 edge=wb.subtract(wa);
                            if(wc.distanceToSqr(wa)>edge.lengthSqr())edge=wc.subtract(wa);
                            if(wc.distanceToSqr(wb)>edge.lengthSqr())edge=wc.subtract(wb);
                            Vec3 normal=edge.cross(travel).normalize();
                            if(normal.lengthSqr()<.5)normal=wb.subtract(wa).cross(wc.subtract(wa)).normalize();
                            found.put(key,new Contact(zone.bit(),last.edge(),point,travel,normal));
                        }
                    }
                }
            }
            return found.isEmpty()?Report.NONE:new Report(kind,List.copyOf(found.values()));
        }
    }
    /** Ordinary items have no shared animated mesh: use the actual aimed contact, never a fixed limb. */
    public static Report ordinary(ServerPlayer player,LivingEntity victim){
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getLookAngle().scale(4));
        Vec3 hit=victim.getBoundingBox().inflate(.04).clip(eye,end).orElse(null);
        if(hit==null||!CombatTargeting.clearRay(player,eye,hit))return Report.NONE;
        Vec3 p=local(hit,victim.position(),victim.yBodyRot,bodyScale(victim));
        for(var zone:zones(victim))if(zone.box().inflate(.06).contains(p))return new Report(Kind.SLICE,List.of(new Contact(zone.bit(),"item-edge",hit,player.getLookAngle().scale(.25),new Vec3(0,1,0))));
        return Report.NONE;
    }
    /** Accepted blunt contact gets a local wound, but can never authorize a slice. */
    public static Report impact(ServerPlayer player,LivingEntity victim,Vec3 direction){
        if(!RagdollImpact.supportedBody(victim))return Report.NONE;
        Vec3 eye=player.getEyePosition(),center=victim.getBoundingBox().getCenter();
        Vec3 aimed=victim.getBoundingBox().clip(eye,eye.add(player.getLookAngle().scale(4))).orElse(null);
        Vec3 hit=aimed==null?victim.getBoundingBox().clip(eye,center).orElse(center):aimed;
        Vec3 point=local(hit,victim.position(),victim.yBodyRot,bodyScale(victim));
        Zone nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(var zone:zones(victim)){double d=closest(point,zone.box()).distanceToSqr(point);if(d<distance){distance=d;nearest=zone;}}
        if(nearest==null)return Report.NONE;
        Vec3 skin=closest(point,nearest.box()).scale(bodyScale(victim)).yRot((float)Math.toRadians(180-victim.yBodyRot)).add(victim.position());
        Vec3 travel=direction.lengthSqr()>.001?direction.normalize().scale(.25):center.subtract(eye).normalize().scale(.25);
        return new Report(Kind.NONE,List.of(new Contact(nearest.bit(),"impact",skin,travel,Vec3.ZERO)));
    }
    /** Entry surface of the touched anatomical box, in body-local metres, for blood attachment. */
    public static Vec3 woundPoint(LivingEntity victim,Contact contact){
        double scale=bodyScale(victim);float angle=(float)Math.toRadians(victim.yBodyRot-180);
        Vec3 p=local(contact.point(),victim.position(),victim.yBodyRot,scale);
        Vec3 travel=contact.travel().yRot(angle).normalize();
        for(var zone:zones(victim))if(zone.bit()==contact.region()){
            Vec3 inside=closest(p,zone.box().deflate(.001));
            return zone.box().clip(inside.subtract(travel.scale(4)),inside).orElse(inside).scale(scale);
        }
        return p.scale(scale);
    }
    private static synchronized List<Face> geometry(PowerForm form){return GEOMETRY.computeIfAbsent(form,WeaponCutContact::load);}
    private static List<Face> load(PowerForm form){
        String file=form==PowerForm.CLAWS?"claw_blender":form==PowerForm.BLADE?"form_blade":"whip_tip";
        String path="/assets/blacklight/models/"+file+".json";
        try(var input=WeaponCutContact.class.getResourceAsStream(path)){
            if(input==null)throw new IllegalStateException("Missing cutting geometry: "+path);
            var json=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            var result=new ArrayList<Face>();
            if(form==PowerForm.WHIPFIST){
                var faces=json.getAsJsonObject("joints").getAsJsonArray("Hand_R");
                add(result,"hook-tip","Hand_R",faces,0,faces.size(),false);
            }else for(var entry:json.getAsJsonObject("parts").entrySet()){
                String name=entry.getKey();
                boolean cutting=form==PowerForm.CLAWS?(name.contains("long blade /")||name.startsWith("Thumb short blade /")):name.startsWith("Continuous sculpted blade /");
                if(!cutting)continue;
                var part=entry.getValue().getAsJsonObject();String joint=part.get("joint").getAsString();
                var faces=json.getAsJsonObject("joints").getAsJsonArray(joint);
                int start=part.get("start").getAsInt(),count=part.get("count").getAsInt();
                add(result,name,joint,faces,start,count,false);
                if(form==PowerForm.CLAWS)add(result,name.replace("_R","_L"),joint.replace("_R","_L"),faces,start,count,true);
            }
            if(result.isEmpty())throw new IllegalStateException("No sharp surfaces in "+path);
            return List.copyOf(result);
        }catch(IOException error){throw new IllegalStateException("Cannot read cutting geometry",error);}
    }
    private static void add(List<Face> out,String name,String bone,JsonArray faces,int start,int count,boolean mirror){
        for(int i=start;i<start+count;i++){
            var face=faces.get(i).getAsJsonObject();var color=face.getAsJsonArray("c");
            // The exported edge material identifies sharpened faces; no palm, spine or decorative spurs.
            if(color.get(0).getAsInt()!=156||color.get(1).getAsInt()!=166||color.get(2).getAsInt()!=157)continue;
            var p=face.getAsJsonArray("p");out.add(new Face(name,bone,read(p,0,mirror),read(p,1,mirror),read(p,2,mirror)));
        }
    }
    private static Vec3 read(JsonArray p,int i,boolean mirror){var v=p.get(i).getAsJsonArray();return new Vec3(v.get(0).getAsDouble()*(mirror?-1:1),v.get(1).getAsDouble(),v.get(2).getAsDouble());}
    private static Vector3f vector(Vec3 p){return new Vector3f((float)p.x,(float)p.y,(float)p.z);}
    private static Vec3 transform(Vec3 p,Matrix4f matrix){var v=matrix.transformPosition(vector(p));return new Vec3(v.x,v.y,v.z);}
    private static Triangle transform(Face f,Matrix4f m){return new Triangle(f.edge(),transform(f.a(),m),transform(f.b(),m),transform(f.c(),m));}
    private static Triangle offset(Triangle t,Vec3 p){return new Triangle(t.edge(),t.a().add(p),t.b().add(p),t.c().add(p));}
    private static Vec3 reach(ServerPlayer p,Vec3 origin,Vec3 point){var d=point.subtract(origin);return origin.add(d.x*AttackReach.scale(p),d.y,d.z*AttackReach.scale(p));}
    private static Vec3 local(Vec3 p,Vec3 origin,float yaw,double scale){return p.subtract(origin).yRot((float)Math.toRadians(yaw-180)).scale(1/scale);}
    private static Vec3 closest(Vec3 p,AABB b){return new Vec3(Math.clamp(p.x,b.minX,b.maxX),Math.clamp(p.y,b.minY,b.maxY),Math.clamp(p.z,b.minZ,b.maxZ));}
    private static Vec3 intersectionCenter(Vec3 a,Vec3 b,Vec3 c,AABB box){
        List<Vec3> polygon=List.of(a,b,c);
        for(int axis=0;axis<3;axis++)for(int side=0;side<2;side++){
            final int index=axis;double boundary=side==0?box.min(axis==0?net.minecraft.core.Direction.Axis.X:axis==1?net.minecraft.core.Direction.Axis.Y:net.minecraft.core.Direction.Axis.Z):box.max(axis==0?net.minecraft.core.Direction.Axis.X:axis==1?net.minecraft.core.Direction.Axis.Y:net.minecraft.core.Direction.Axis.Z);
            int sign=side==0?1:-1;var result=new ArrayList<Vec3>();if(polygon.isEmpty())break;
            Vec3 prev=polygon.getLast();double before=(component(prev,index)-boundary)*sign;
            for(Vec3 next:polygon){double after=(component(next,index)-boundary)*sign;
                if((before>=0)!=(after>=0))result.add(prev.lerp(next,before/(before-after)));
                if(after>=0)result.add(next);prev=next;before=after;
            }polygon=result;
        }
        if(polygon.isEmpty())return closest(a.add(b).add(c).scale(1.0/3),box);
        Vec3 sum=Vec3.ZERO;for(Vec3 p:polygon)sum=sum.add(p);return sum.scale(1.0/polygon.size());
    }
    private static double component(Vec3 p,int axis){return axis==0?p.x:axis==1?p.y:p.z;}

    /** Triangle/AABB separating-axis test: box normals, triangle normal, and nine edge cross axes. */
    static boolean triangleBox(Vec3 a,Vec3 b,Vec3 c,AABB box){
        if(Math.min(a.x,Math.min(b.x,c.x))>box.maxX||Math.max(a.x,Math.max(b.x,c.x))<box.minX
                ||Math.min(a.y,Math.min(b.y,c.y))>box.maxY||Math.max(a.y,Math.max(b.y,c.y))<box.minY
                ||Math.min(a.z,Math.min(b.z,c.z))>box.maxZ||Math.max(a.z,Math.max(b.z,c.z))<box.minZ)return false;
        Vec3 center=box.getCenter(),half=new Vec3(box.getXsize()*.5,box.getYsize()*.5,box.getZsize()*.5);
        a=a.subtract(center);b=b.subtract(center);c=c.subtract(center);
        Vec3[] axes={new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)};
        for(var axis:axes)if(separated(a,b,c,axis,half))return false;
        Vec3[] edges={b.subtract(a),c.subtract(b),a.subtract(c)};
        if(separated(a,b,c,edges[0].cross(edges[1]),half))return false;
        for(var edge:edges)for(var axis:axes)if(separated(a,b,c,edge.cross(axis),half))return false;
        return true;
    }
    private static boolean separated(Vec3 a,Vec3 b,Vec3 c,Vec3 axis,Vec3 half){
        if(axis.lengthSqr()<1e-14)return false;
        double x=a.dot(axis),y=b.dot(axis),z=c.dot(axis),r=half.x*Math.abs(axis.x)+half.y*Math.abs(axis.y)+half.z*Math.abs(axis.z);
        return Math.min(x,Math.min(y,z))>r+1e-8||Math.max(x,Math.max(y,z))<-r-1e-8;
    }
}
