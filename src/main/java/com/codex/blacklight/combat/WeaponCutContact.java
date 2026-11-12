package com.codex.blacklight.combat;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.network.RagdollImpactPayload.BladeSpan;
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
 * Authored combat contact authorizes damage; sharp contact separately authorizes cuts. */
public final class WeaponCutContact {
    public enum Kind { NONE, SLICE, PUNCTURE }
    public record Contact(int region,String edge,Vec3 point,Vec3 travel,Vec3 normal,long stroke,List<BladeSpan> path) {
        public Contact {path=List.copyOf(path);}
        public Contact(int region,String edge,Vec3 point,Vec3 travel,Vec3 normal){this(region,edge,point,travel,normal,0,List.of());}
        public Contact path(long id,List<BladeSpan> spans){return new Contact(region,edge,point,travel,normal,id,spans);}
    }
    public record Report(Kind kind,List<Contact> contacts){
        public static final Report NONE=new Report(Kind.NONE,List.of());
        public int severMask(){
            if(kind!=Kind.SLICE)return 0;
            int mask=0;for(var contact:contacts)if(contact.travel().lengthSqr()>.0009)mask|=contact.region();
            return mask&63;
        }
        public Vec3 direction(){Vec3 v=Vec3.ZERO;for(var c:contacts)v=v.add(c.travel());return v.normalize();}
        public Report stroke(long id){return new Report(kind,contacts.stream().map(c->c.path(id,c.path())).toList());}
    }
    record Face(String edge,String bone,Vec3 a,Vec3 b,Vec3 c,Vec3 root,Vec3 tip,List<Vec3> profile,boolean sharp) {
        Face(String edge,String bone,Vec3 a,Vec3 b,Vec3 c,Vec3 root,Vec3 tip,List<Vec3> profile){this(edge,bone,a,b,c,root,tip,profile,true);}
        Face(String edge,String bone,Vec3 a,Vec3 b,Vec3 c,Vec3 root,Vec3 tip){this(edge,bone,a,b,c,root,tip,List.of(root,tip));}
    }
    private record Triangle(String edge,Vec3 a,Vec3 b,Vec3 c,Vec3 root,Vec3 tip,List<Vec3> profile,boolean sharp){
        Vec3 center(){return a.add(b).add(c).scale(1.0/3);}
    }
    private record Frame(double alpha,List<Triangle> faces) {}
    private record Zone(int bit,AABB box) {}
    private static final Map<PowerForm,List<Face>> GEOMETRY=new EnumMap<>(PowerForm.class);
    private static final Map<PowerForm,List<Face>> BLUNT_GEOMETRY=new EnumMap<>(PowerForm.class);
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
    private static List<Zone> zones(LivingEntity v){
        if(!RagdollImpact.legacyBody(v)){
            double r=v.getBbWidth()*.5;
            return List.of(new Zone(32,new AABB(-r,0,-r,r,v.getBbHeight(),r)));
        }
        return v instanceof net.minecraft.world.entity.animal.Pig?PIG_ZONES:ZONES;
    }
    private static double bodyScale(LivingEntity v){return !RagdollImpact.legacyBody(v)||v instanceof net.minecraft.world.entity.animal.Pig?1:v.getBbHeight()/1.95;}

    /** Created once per strike tick, shared across its victims. Nothing here trusts client hit reports. */
    public static Sweep sweep(ServerPlayer player,WeaponMove move,double from,double to,Vec3 oldFeet,float oldYaw,float yaw,WhipCurve whip){
        if(move.quake()||move.groundSpike()||!(move.form==PowerForm.CLAWS||move.form==PowerForm.BLADE||move.form==PowerForm.WHIPFIST))return Sweep.EMPTY;
        var geometry=new ArrayList<>(geometry(move.form));if(geometry.isEmpty()||MotionLibrary.find(move.id).isEmpty())return Sweep.EMPTY;
        if(move.form==PowerForm.CLAWS||move.form==PowerForm.BLADE)geometry.addAll(bluntGeometry(move.form));
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
                    var shape=WeaponAttackScales.sample(move.id,(float)time,face.bone().endsWith("_L"));
                    var actual=new Face(face.edge(),face.bone(),surface(face.a(),move.form,face.bone(),shape),
                            surface(face.b(),move.form,face.bone(),shape),surface(face.c(),move.form,face.bone(),shape),
                            surface(face.root(),move.form,face.bone(),shape),surface(face.tip(),move.form,face.bone(),shape),
                            face.profile().stream().map(v->surface(v,move.form,face.bone(),shape)).toList(),face.sharp());
                    // Damage reach may grow, but an invisible enlargement must never authorize a cut.
                    triangles.add(offset(transform(actual,transform),feet));
                }
            }
            frames.add(new Frame(alpha,List.copyOf(triangles)));
        }
        return new Sweep(kind,List.copyOf(frames),player.level().getGameTime()-1);
    }
    public static final class Sweep {
        private static final Sweep EMPTY=new Sweep(Kind.NONE,List.of(),0);
        private final Kind kind;private final List<Frame> frames;private final long fromTick;
        private Sweep(Kind kind,List<Frame> frames,long fromTick){this.kind=kind;this.frames=frames;this.fromTick=fromTick;}
        public boolean sharp(){return kind!=Kind.NONE;}
        public Kind kind(){return kind;}
        /** Receiver-local ribbon samples, including the exit after the last triangle contact. */
        public List<BladeSpan> path(String edge,LivingEntity victim,Vec3 previousPosition){
            var spans=new ArrayList<BladeSpan>();
            for(var frame:frames)for(var face:frame.faces())if(face.sharp()&&face.edge().equals(edge)){
                Vec3 origin=previousPosition.lerp(victim.position(),frame.alpha());
                float yaw=net.minecraft.util.Mth.rotLerp((float)frame.alpha(),victim.yBodyRotO,victim.yBodyRot);
                spans.add(new BladeSpan(face.profile().stream().map(p->local(p,origin,yaw,1)).toList(),fromTick+frame.alpha(),origin,yaw));break;
            }
            return List.copyOf(spans);
        }
        public Report trace(ServerPlayer attacker,LivingEntity victim,Vec3 previousPosition){
            return trace(attacker,victim,previousPosition,true);
        }
        /** The visible blade's back/flat can hit, but never supplies cutting planes. */
        public Report bluntContact(ServerPlayer attacker,LivingEntity victim,Vec3 previousPosition){
            return trace(attacker,victim,previousPosition,false);
        }
        private Report trace(ServerPlayer attacker,LivingEntity victim,Vec3 previousPosition,boolean sharp){
            if(kind==Kind.NONE||!RagdollImpact.supportedBody(victim))return Report.NONE;
            Map<String,Contact> found=new LinkedHashMap<>();
            double scale=bodyScale(victim);
            for(int i=1;i<frames.size();i++){
                Frame a=frames.get(i-1),b=frames.get(i);
                if(a.faces().size()!=b.faces().size())continue;
                for(int j=0;j<b.faces().size();j++){
                    Triangle first=a.faces().get(j),last=b.faces().get(j);
                    if(last.sharp()!=sharp)continue;
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
                            // A tessellation diagonal is not the long axis of a blade. Use the
                            // full exported sharp part so adjacent faces cannot invent crossing cuts.
                            Vec3 edge=first.tip().lerp(last.tip(),t).subtract(first.root().lerp(last.root(),t));
                            Vec3 normal=edge.cross(travel).normalize();
                            if(normal.lengthSqr()<.5)normal=wb.subtract(wa).cross(wc.subtract(wa)).normalize();
                            found.put(key,new Contact(zone.bit(),last.edge(),point,travel,normal));
                        }
                    }
                }
            }
            return found.isEmpty()?Report.NONE:new Report(sharp?kind:Kind.NONE,found.values().stream().map(c->c.path(0,sharp?path(c.edge(),victim,previousPosition):List.of())).toList());
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
    static synchronized List<Face> geometry(PowerForm form){return GEOMETRY.computeIfAbsent(form,WeaponCutContact::load);}
    /** Local profiles of independent sharpened patches, shared with rendered consume contact. */
    public static Map<String,List<Vec3>> sharpProfiles(PowerForm form,String joint){
        if(form!=PowerForm.CLAWS&&form!=PowerForm.BLADE&&form!=PowerForm.WHIPFIST)return Map.of();
        var profiles=new LinkedHashMap<String,List<Vec3>>();
        for(var face:geometry(form))if(face.bone().equals(joint))profiles.putIfAbsent(face.edge(),face.profile());
        return Collections.unmodifiableMap(profiles);
    }
    static synchronized List<Face> bluntGeometry(PowerForm form){return form==PowerForm.CLAWS||form==PowerForm.BLADE?BLUNT_GEOMETRY.computeIfAbsent(form,f->load(f,false)):List.of();}
    private static List<Face> load(PowerForm form){
        return load(form,true);
    }
    private static List<Face> load(PowerForm form,boolean sharp){
        String file=form==PowerForm.CLAWS?"claw_blender":form==PowerForm.BLADE?"form_blade":"whip_tip";
        String path="/assets/blacklight/models/"+file+".json";
        try(var input=WeaponCutContact.class.getResourceAsStream(path)){
            if(input==null)throw new IllegalStateException("Missing cutting geometry: "+path);
            var json=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            // ClawArmModel applies this approved display shape while loading.
            // Derive profiles from those same vertices, so render barycentric
            // bindings include the long blade instead of silently losing it.
            if(form==PowerForm.BLADE&&!json.has("approvedProportions"))for(var element:json.getAsJsonObject("joints").getAsJsonArray("Hand_R")){
                var positions=element.getAsJsonObject().getAsJsonArray("p");
                for(int i=0;i<positions.size();i++){var p=positions.get(i).getAsJsonArray();var shaped=com.codex.blacklight.client.model.BladeShape.point(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat());
                    var value=new JsonArray();value.add((float)shaped.x);value.add((float)shaped.y);value.add((float)shaped.z);positions.set(i,value);}
            }
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
                if(sharp){
                    add(result,name,joint,faces,start,count,false);
                    if(form==PowerForm.CLAWS)add(result,name.replace("_R","_L"),joint.replace("_R","_L"),faces,start,count,true);
                }else{
                    addBlunt(result,name,joint,faces,start,count,false);
                    if(form==PowerForm.CLAWS)addBlunt(result,name.replace("_R","_L"),joint.replace("_R","_L"),faces,start,count,true);
                }
            }
            if(result.isEmpty())throw new IllegalStateException("No weapon surfaces in "+path);
            return List.copyOf(result);
        }catch(IOException error){throw new IllegalStateException("Cannot read cutting geometry",error);}
    }
    private static void addBlunt(List<Face> out,String name,String bone,JsonArray faces,int start,int count,boolean mirror){
        for(int i=start;i<start+count;i++){
            var face=faces.get(i).getAsJsonObject();var color=face.getAsJsonArray("c");
            if(color.get(0).getAsInt()==156&&color.get(1).getAsInt()==166&&color.get(2).getAsInt()==157)continue;
            var p=face.getAsJsonArray("p");var a=read(p,0,mirror);var b=read(p,1,mirror);var c=read(p,2,mirror);
            out.add(new Face(name+" [blunt]",bone,a,b,c,a,b,List.of(a,b),false));
        }
    }
    private static void add(List<Face> out,String name,String bone,JsonArray faces,int start,int count,boolean mirror){
        var selected=new ArrayList<Face>();
        for(int i=start;i<start+count;i++){
            var face=faces.get(i).getAsJsonObject();var color=face.getAsJsonArray("c");
            // The exported edge material identifies sharpened faces; no palm, spine or decorative spurs.
            if(color.get(0).getAsInt()!=156||color.get(1).getAsInt()!=166||color.get(2).getAsInt()!=157)continue;
            var p=face.getAsJsonArray("p");var a=read(p,0,mirror);var b=read(p,1,mirror);var c=read(p,2,mirror);
            selected.add(new Face(name,bone,a,b,c,a,b));
        }
        var groups=connectedSharpFaces(selected);
        for(int index=0;index<groups.size();index++){
            String edge=groups.size()==1?name:name+" [edge "+(index+1)+"]";
            addAxis(out,edge,bone,groups.get(index));
        }
    }
    /** Sharpened surfaces separated by a blunt hilt/spine cannot form one cutting span. */
    private static List<List<Face>> connectedSharpFaces(List<Face> faces){
        var result=new ArrayList<List<Face>>();var remaining=new LinkedHashSet<>(faces);
        while(!remaining.isEmpty()){
            var group=new ArrayList<Face>();var queue=new ArrayDeque<Face>();queue.add(remaining.iterator().next());
            while(!queue.isEmpty()){
                var face=queue.removeFirst();if(!remaining.remove(face))continue;group.add(face);
                for(var other:remaining){int shared=0;
                    for(var a:List.of(face.a(),face.b(),face.c()))if(List.of(other.a(),other.b(),other.c()).stream().anyMatch(b->a.distanceToSqr(b)<1e-12))shared++;
                    if(shared>=2)queue.add(other);
                }
            }result.add(group);
        }return result;
    }
    private static void addAxis(List<Face> out,String name,String bone,List<Face> faces){
        var vertices=new ArrayList<Vec3>();for(var face:faces){vertices.add(face.a());vertices.add(face.b());vertices.add(face.c());}
        if(vertices.isEmpty())return;
        Vec3 root=vertices.getFirst(),tip=root;double longest=0;
        for(int i=0;i<vertices.size();i++)for(int j=i+1;j<vertices.size();j++){
            double length=vertices.get(i).distanceToSqr(vertices.get(j));if(length>longest){longest=length;root=vertices.get(i);tip=vertices.get(j);}
        }
        var axis=tip.subtract(root).normalize();double low=root.dot(axis),high=tip.dot(axis),band=(high-low)*.12;
        Vec3 lowMean=Vec3.ZERO,highMean=Vec3.ZERO;int lows=0,highs=0;
        for(var p:vertices){double projection=p.dot(axis);if(projection<=low+band){lowMean=lowMean.add(p);lows++;}if(projection>=high-band){highMean=highMean.add(p);highs++;}}
        if(lows>0&&highs>0){root=lowMean.scale(1.0/lows);tip=highMean.scale(1.0/highs);root=root.add(axis.scale(low-root.dot(axis)));tip=tip.add(axis.scale(high-tip.dot(axis)));}
        // Averaging the two narrow end bands can slightly change the axis. Refit
        // its endpoints against that final direction, not the preceding diagonal.
        axis=tip.subtract(root).normalize();low=Double.POSITIVE_INFINITY;high=Double.NEGATIVE_INFINITY;
        for(var p:vertices){low=Math.min(low,p.dot(axis));high=Math.max(high,p.dot(axis));}
        root=root.add(axis.scale(low-root.dot(axis)));tip=tip.add(axis.scale(high-tip.dot(axis)));
        var profile=sharpProfile(faces,axis,low,high);
        for(var f:faces)out.add(new Face(name,bone,f.a(),f.b(),f.c(),profile.getFirst(),profile.getLast(),profile));
    }
    private static List<Vec3> sharpProfile(List<Face> faces,Vec3 axis,double low,double high){
        var profile=new ArrayList<Vec3>();
        for(int index=0;index<com.codex.blacklight.network.RagdollImpactPayload.MAX_BLADE_POINTS;index++){
            double section=low+(high-low)*index/(com.codex.blacklight.network.RagdollImpactPayload.MAX_BLADE_POINTS-1);
            var points=new ArrayList<Vec3>();
            for(var face:faces){var vertices=List.of(face.a(),face.b(),face.c());
                for(int i=0;i<3;i++){
                    var a=vertices.get(i);var b=vertices.get((i+1)%3);double da=a.dot(axis)-section,db=b.dot(axis)-section;
                    Vec3 point=Math.abs(da)<1e-7?a:da*db<0?a.lerp(b,da/(da-db)):null;
                    if(point!=null&&points.stream().noneMatch(v->v.distanceToSqr(point)<1e-12))points.add(point);
                }
            }
            if(points.isEmpty())throw new IllegalStateException("Sharp profile section misses its own mesh");
            Vec3 center=Vec3.ZERO;for(var point:points)center=center.add(point);center=center.scale(1.0/points.size());
            // Stay inside the sharpened bevel. A concave cross-section must not
            // bridge empty space just because its samples average to that point.
            var probe=new AABB(center,center).inflate(.02);boolean near=false;
            for(var face:faces)if(triangleBox(face.a(),face.b(),face.c(),probe)){near=true;break;}
            if(!near){Vec3 mean=center;center=points.stream().min(Comparator.comparingDouble(p->p.distanceToSqr(mean))).orElseThrow();}
            profile.add(center);
        }return List.copyOf(profile);
    }
    private static Vec3 read(JsonArray p,int i,boolean mirror){var v=p.get(i).getAsJsonArray();return new Vec3(v.get(0).getAsFloat()*(mirror?-1:1),v.get(1).getAsFloat(),v.get(2).getAsFloat());}
    private static Vector3f vector(Vec3 p){return new Vector3f((float)p.x,(float)p.y,(float)p.z);}
    static Vec3 surface(Vec3 point,PowerForm form,String bone,WeaponAttackScales.Shape shape){
        if(!bone.startsWith("Hand_"))return point;
        var p=shape.point(vector(point));return new Vec3(p.x,p.y,p.z);
    }
    private static Vec3 transform(Vec3 p,Matrix4f matrix){var v=matrix.transformPosition(vector(p));return new Vec3(v.x,v.y,v.z);}
    private static Triangle transform(Face f,Matrix4f m){return new Triangle(f.edge(),transform(f.a(),m),transform(f.b(),m),transform(f.c(),m),transform(f.root(),m),transform(f.tip(),m),f.profile().stream().map(p->transform(p,m)).toList(),f.sharp());}
    private static Triangle offset(Triangle t,Vec3 p){return new Triangle(t.edge(),t.a().add(p),t.b().add(p),t.c().add(p),t.root().add(p),t.tip().add(p),t.profile().stream().map(v->v.add(p)).toList(),t.sharp());}
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
