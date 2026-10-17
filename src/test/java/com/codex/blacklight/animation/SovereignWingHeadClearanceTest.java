package com.codex.blacklight.animation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;
class SovereignWingHeadClearanceTest {
 // BlacklightPlayerModel head is 8px with a .5px hat inflation on each face.
 // Use that rendered 9px envelope plus a numerical margin. The former 9.6px
 // envelope also rejected intentional shoulder tissue below the actual neck.
 static final float HEAD_HALF=4.5F/16+.002F, HEAD_BOTTOM=23.5F/16-.002F, HEAD_TOP=32.5F/16+.002F;
 record Point(String bone,String second,float weight,Vector3f p,String name){}
 static float distance(Vector3f p,List<Vector3f> path){
  float best=Float.MAX_VALUE;
  for(int i=1;i<path.size();i++){
   var a=path.get(i-1);var delta=new Vector3f(path.get(i)).sub(a);
   float t=Math.clamp(new Vector3f(p).sub(a).dot(delta)/Math.max(.000001F,delta.lengthSquared()),0,1);
   best=Math.min(best,p.distance(delta.mul(t).add(a)));
  }
  return best;
 }
 static Point weighted(JsonObject e,Vector3f p,Map<String,List<Vector3f>> paths){
  String first=e.get("_parent").getAsString(),second=first;float weight=0;
  if(e.has("_root_skin")){
   second=e.get("_root_skin").getAsString();first="wing_mount";weight=0;
  }else if(e.has("_shoulder_skin")){
   first=second="wing_mount";weight=0;
  }else if(e.has("_skin")&&e.get("_skin").getAsBoolean()){
   float near=Float.MAX_VALUE,next=Float.MAX_VALUE;String side=first.startsWith("left")?"left":"right";
   for(String candidate:paths.keySet()){
    if(!candidate.startsWith(side)||candidate.endsWith("_root"))continue;float d=distance(p,paths.get(candidate));
    if(d<near){second=first;next=near;first=candidate;near=d;}else if(d<next){second=candidate;next=d;}
   }
   weight=near+next>.00001F?near/(near+next):.5F;
  }
  return new Point(first,second,weight,p,e.get("name").getAsString());
 }
 static Vector3f vec(JsonArray a){return new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());}
 @Test void actualFlightClipsAndSteeringKeepWingSamplesOutsideHead() throws Exception {
 var data=JsonParser.parseReader(new java.io.InputStreamReader(getClass().getResourceAsStream("/assets/blacklight/wing_models/sovereign_wings.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();var points=new ArrayList<Point>();
 var paths=new HashMap<String,List<Vector3f>>();
 for(String pathSet:List.of("skin_paths","root_paths"))for(var entry:data.getAsJsonObject(pathSet).entrySet()){
  var path=new ArrayList<Vector3f>();for(var point:entry.getValue().getAsJsonArray())path.add(vec(point.getAsJsonArray()).div(16));paths.put(entry.getKey(),path);
 }
 for(var el:data.getAsJsonArray("elements")){var e=el.getAsJsonObject();
 if(e.has("vertices")){
  for(var entry:e.getAsJsonObject("vertices").entrySet()){
   var w=e.getAsJsonObject("_weights").getAsJsonArray(entry.getKey());
   points.add(new Point(w.get(0).getAsString(),w.get(1).getAsString(),w.get(2).getAsFloat(),vec(entry.getValue().getAsJsonArray()).div(16),e.get("name").getAsString()));
  }
  continue;
 }
 var from=vec(e.getAsJsonArray("from"));var to=vec(e.getAsJsonArray("to"));var origin=vec(e.getAsJsonArray("origin"));var r=vec(e.getAsJsonArray("rotation")).mul((float)Math.PI/180);var q=new Quaternionf().rotationXYZ(r.x,r.y,r.z);
 for(int i=0;i<9;i++){var p=i==8?new Vector3f(from).lerp(to,.5f):new Vector3f((i&1)==0?from.x:to.x,(i&2)==0?from.y:to.y,(i&4)==0?from.z:to.z);p.sub(origin).rotate(q).add(origin).div(16);points.add(weighted(e,p,paths));}}
 var sk=BlacklightSkeleton.get();var bind=sk.getPoseMatrices(new BlacklightPose());
 {int count=0;float lowestGroundPoint=Float.MAX_VALUE;var offenders=new HashMap<String,Integer>();
 var clips=new ArrayList<>(FlightAnimationClips.names());clips.addAll(List.of("ground","sonic","sonic_mid"));
 for(int variant=0;variant<2;variant++)for(String variantClip:clips)for(int sample=0;sample<24;sample++){
 String clip=variantClip.startsWith("sonic")?"boost":variantClip;
 var body=clip.equals("ground")?new BlacklightPose():FlightAnimationClips.sample(clip,FlightAnimationClips.duration(clip)*sample/24);
 if(!clip.equals("ground"))SovereignFlightMotion.followMomentum(body,new WingFlightPresentation.Frame(1,clip.equals("boost")?1:0,clip.equals("brake")?1:0,variantClip.equals("sonic")?22:variantClip.equals("sonic_mid")?11.57625F:1.25F),1,1);
 var bodyM=sk.getPoseMatrices(body);
 var chest=new Matrix4f(bodyM[sk.chest.getId()]).mul(new Matrix4f(bind[sk.chest.getId()]).invert());
 var headInverse=new Matrix4f(bodyM[sk.head.getId()]).mul(new Matrix4f(bind[sk.head.getId()]).invert()).invert();
 var axis=SovereignWingMotion.strokeAxis(chest.getUnnormalizedRotation(new Quaternionf()));
 for(int steering=-1;steering<=1;steering++)for(int cycle=0;cycle<24;cycle++){
 var pose=SovereignWingMotion.sample(cycle/24.,1,clip.equals("ground")?0:1,clip.equals("boost")?1:0,clip.equals("cruise")||clip.equals("boost")?1:0,steering,clip.equals("takeoff")?1:0,steering,clip.equals("brake")?1:0,clip.equals("ground")?0:1);
 if(variantClip.startsWith("sonic"))pose=SovereignWingMotion.streamline(pose,variantClip.equals("sonic")?1:.5F);
 var frames=new HashMap<String,Matrix4f>();var pivots=new HashMap<String,Vector3f>();
 for(var item:data.getAsJsonArray("bones")){var b=item.getAsJsonObject();String name=b.get("name").getAsString();var pivot=vec(b.getAsJsonArray("pivot")).div(16);var m=new Matrix4f();var delta=new Vector3f(pivot);if(!b.get("parent").isJsonNull()){String parent=b.get("parent").getAsString();m.set(frames.get(parent));delta.sub(pivots.get(parent));}m.translate(delta);var fq=data.getAsJsonObject("fold_poses").getAsJsonObject(variant==0?"left_over":"right_over").getAsJsonArray(name);var folded=new Quaternionf(fq.get(0).getAsFloat(),fq.get(1).getAsFloat(),fq.get(2).getAsFloat(),fq.get(3).getAsFloat());m.rotate(SovereignWingMotion.articulation(name,pose,axis,clip.equals("ground")?0:1,folded));frames.put(name,m);pivots.put(name,pivot);}
 var transforms=new HashMap<String,Matrix4f>();for(String name:frames.keySet())transforms.put(name,new Matrix4f(headInverse).mul(chest).mul(frames.get(name)).translate(new Vector3f(pivots.get(name)).negate()));
 for(String arm:List.of("Arm_L","Arm_R")){int id=sk.searchJointByName(arm).getId();transforms.put(arm,new Matrix4f(headInverse).mul(bodyM[id]).mul(new Matrix4f(bind[id]).invert()));}
 var v=new Vector3f();var other=new Vector3f();for(var point:points){transforms.get(point.bone).transformPosition(point.p,v);if(point.weight>0)v.lerp(transforms.get(point.second).transformPosition(point.p,other),point.weight);if(clip.equals("ground"))lowestGroundPoint=Math.min(lowestGroundPoint,v.y);if(Math.abs(v.x)<HEAD_HALF&&v.y>HEAD_BOTTOM&&v.y<HEAD_TOP&&Math.abs(v.z)<HEAD_HALF){count++;offenders.merge(variantClip+":"+point.name,1,Integer::sum);}}
 }}assertEquals(0,count,"Wing samples enter the head safety box: "+offenders);assertTrue(lowestGroundPoint>.05F,"Folded wing enters ground: "+lowestGroundPoint);}
 }
}
