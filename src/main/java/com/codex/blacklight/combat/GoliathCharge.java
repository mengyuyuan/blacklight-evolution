package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterKind;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** P2 bullrush phases, native onEnd predicates, root travel and Hit spheres.
 * Phase boundaries are quantized to 20Hz; contact precedes the onEnd decision. */
public final class GoliathCharge {
    public record Sphere(String joint,Vec3 center,double radius) {}
    public record Snapshot(int phase,float phaseStartAge) {
        public static final Snapshot INACTIVE=new Snapshot(-1,0);
    }
    /** Minecraft movement policy, not a recovered P2 stun duration. Source
     * windup retreats and recovery also extracts root travel; clipping those
     * phases must not manufacture a successful charging impact. */
    public enum Obstruction { NONE, CLIP, CANCEL, EXPOSE }
    public static Obstruction obstruction(Snapshot snapshot,boolean spaceAvailable,boolean horizontalBlocked){
        if(!spaceAvailable||phase(snapshot)==null)return Obstruction.CANCEL;
        if(!horizontalBlocked)return Obstruction.NONE;
        return snapshot.phase==3||snapshot.phase==4?Obstruction.EXPOSE:Obstruction.CLIP;
    }
    /** Original Player is a committed Player in MC multiplayer. Script inputs
     * default to zero; they must not be invented from boss health or distance. */
    public record Inputs(boolean playerAvailable,double actorY,double playerY,boolean playerSupported,
                         double targetAngleDegrees,int angleOverride,int forceCycle,int aggroDisabled) {}
    private record Phase(int id,String clip,float from,float to,float speed,float seconds,float blendIn,float turn,double radius,int previous) {
        int ticks(){return (int)Math.ceil(seconds*20-.00001F);}
        float frame(float age){return Math.min(to,from+Math.max(0,age)*1.5F*speed);}
    }
    private record Data(String[] chain,Vec3[] roots,Phase[] phases,Map<String,Vec3[]> curves) {}
    private static final Data DATA=load();
    private GoliathCharge() {}
    private static Data load(){
        try(var in=Objects.requireNonNull(GoliathCharge.class.getResourceAsStream("/assets/blacklight/models/goliath_charge.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var object=JsonParser.parseReader(reader).getAsJsonObject();
            var chain=new Gson().fromJson(object.get("chain"),String[].class);
            var values=object.getAsJsonArray("rootPositions");var roots=new Vec3[values.size()];
            for(int i=0;i<roots.length;i++){var p=values.get(i).getAsJsonArray();roots[i]=new Vec3(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());}
            if(!Arrays.equals(chain,new String[]{"Elbow_L","Chest_L_End","Forarm_L","Wrist_L"})||roots.length!=151||object.get("nativeSphereSpacingMultiplier").getAsInt()!=2)
                throw new IllegalStateException("Bullrush sphere-chain assumptions changed");
            var names=Arrays.asList(GoliathMotion.table().bones);
            for(String joint:chain)if(!names.contains("source_"+joint))throw new IllegalStateException("Missing bullrush joint "+joint);
            var phases=new Gson().fromJson(object.get("phases"),Phase[].class);
            var curves=new HashMap<String,Vec3[]>();
            for(var entry:object.getAsJsonObject("rootCurves").entrySet()){
                var array=entry.getValue().getAsJsonArray();var points=new Vec3[array.size()];
                for(int i=0;i<points.length;i++){var p=array.get(i).getAsJsonArray();points[i]=new Vec3(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());}
                curves.put(entry.getKey(),points);
            }
            if(phases.length!=13||object.get("cycleSecondsLimit").getAsFloat()!=2.5F||object.get("targetArcDegrees").getAsFloat()!=120)
                throw new IllegalStateException("Bullrush FIG control changed");
            for(int i=0;i<phases.length;i++)if(phases[i].id!=i||!curves.containsKey(phases[i].clip)||!GoliathMotion.table().has(phases[i].clip))
                throw new IllegalStateException("Invalid bullrush phase "+i);
            return new Data(chain,roots,phases,Map.copyOf(curves));
        }catch(Exception ex){throw new IllegalStateException("Invalid source bullrush",ex);}
    }
    public static boolean uses(EncounterKind kind,EncounterMove move){return kind==EncounterKind.GOLIATH&&move==EncounterMove.CHARGE;}
    /** Committed attack/exit finishes after its target dies; do not chase a new
     * target or cancel the native recovery merely because the old one left. */
    public static boolean requiresTarget(Snapshot snapshot){return snapshot!=null&&snapshot.phase>=0&&snapshot.phase<4;}
    public static SourceBossAction action(){return Objects.requireNonNull(SourceBossAction.get(EncounterKind.GOLIATH,EncounterMove.CHARGE));}
    /** Per-actor lifecycle. Never advance again or decide onEnd before the
     * current snapshot has delivered its last valid contact sample. */
    public static final class State {
        private int phase=-1,lastAge,cycleStartAge,playerHits;
        private float phaseStartAge;
        private boolean boundary;
        public void begin(){phase=0;lastAge=0;phaseStartAge=0;cycleStartAge=-1;playerHits=0;boundary=false;}
        public void cancel(){phase=-1;boundary=false;playerHits=0;}
        public boolean done(){return phase<0;}
        public Snapshot snapshot(){return done()?Snapshot.INACTIVE:new Snapshot(phase,phaseStartAge);}
        public void playerHit(){if(!done()&&playerHits<Integer.MAX_VALUE)playerHits++;}
        public Vec3 advance(int age,Inputs inputs){
            Objects.requireNonNull(inputs);
            if(done()||age<=lastAge)return Vec3.ZERO;
            // A caller must finish the contact pass before another age arrives.
            if(boundary)throw new IllegalStateException("Bullrush afterContact was not delivered");
            var current=DATA.phases[phase];
            var delta=phaseRoot(current,age-phaseStartAge).subtract(phaseRoot(current,lastAge-phaseStartAge));
            lastAge=age;boundary=age-phaseStartAge>=current.ticks();return delta;
        }
        public void afterContact(Inputs inputs){
            Objects.requireNonNull(inputs);
            if(done()||!boundary)return;
            int next=switch(phase){
                case 0,1,2 -> phase+1;
                case 3 -> continueCycle(inputs,playerHits,(lastAge-cycleStartAge)/20F)?3:4;
                case 4 -> inputs.aggroDisabled>0||inputs.forceCycle>0?5:6;
                case 6,7,8,9,10,11 -> phase+1;
                default -> -1;
            };
            if(next==3&&cycleStartAge<0)cycleStartAge=lastAge;
            phase=next;phaseStartAge=lastAge;boundary=false;
        }
    }
    public static boolean continueCycle(Inputs inputs,int playerHits,float timerSeconds){
        if(inputs.forceCycle>0)return true; // Separate source Sequence434, not subject to the normal timer.
        if(!inputs.playerAvailable||playerHits!=0||!(timerSeconds<2.5F))return false;
        double bearing=inputs.targetAngleDegrees%360;if(bearing>180)bearing-=360;if(bearing< -180)bearing+=360;
        return (Math.abs(bearing)<=60||inputs.angleOverride>0)
            &&(inputs.actorY-inputs.playerY<0||!inputs.playerSupported);
    }
    private static Phase phase(Snapshot snapshot){return snapshot!=null&&snapshot.phase>=0&&snapshot.phase<DATA.phases.length?DATA.phases[snapshot.phase]:null;}
    private static Vec3 phaseRoot(Phase phase,float elapsed){return interpolate(DATA.curves.get(phase.clip),phase.frame(elapsed));}
    private static Vec3 interpolate(Vec3[] roots,float frame){
        int lo=Math.max(0,Math.min((int)frame,roots.length-1)),hi=Math.min(lo+1,roots.length-1);
        return roots[lo].lerp(roots[hi],Math.max(0,Math.min(1,frame-lo)));
    }
    public static float turnStep(Snapshot snapshot,float age){var phase=phase(snapshot);return phase==null?0:phase.turn/20;}
    public static float sourceFrame(Snapshot snapshot,float age){var phase=phase(snapshot);return phase==null?0:phase.frame(age-snapshot.phaseStartAge);}
    public static float[][] pose(Snapshot snapshot,float age){
        var phase=phase(snapshot);if(phase==null)return GoliathMotion.sample("idle",0,false);
        float elapsed=Math.max(0,age-snapshot.phaseStartAge),frame=phase.frame(elapsed);
        var table=GoliathMotion.table();var pose=table.sample(phase.clip,frame/30*20,false);
        if(phase.blendIn>0&&elapsed<phase.blendIn*20){
            // Every enabled successor has a fixed predecessor endpoint. CYCLE
            // has native zero blendIn; no client-only previous-pose state.
            var prior=DATA.phases[phase.previous];
            var before=phase.id==0?table.sample("idle",0,false):table.sample(prior.clip,prior.to/30*20,false);
            for(int i=0;i<pose.length;i++)pose[i]=SourceBossMotion.blend(before[i],pose[i],elapsed/(phase.blendIn*20));
        }
        // Normal successor58 has its own source continuation. Its collision
        // windows are GoliathTaunt, never the bullrush body/sphere fallback.
        return GoliathMotion.compensate(pose);
    }
    public static List<Sphere> spheres(Snapshot snapshot,float age,Vec3 origin,float yaw){
        return spheres(snapshot,age,origin,yaw,1F);
    }
    public static List<Sphere> spheres(Snapshot snapshot,float age,Vec3 origin,float yaw,float scale){
        var phase=phase(snapshot);return phase==null||phase.radius==0?List.of():spheres(pose(snapshot,age),phase.radius*scale,origin,yaw,scale);
    }
    private static Vec3 root(float age){
        float frame=action().sourceTick(Math.max(0,Math.min(age,action().seconds*20)))*action().fps/20;
        int lo=Math.max(0,Math.min((int)frame,DATA.roots.length-1)),hi=Math.min(lo+1,DATA.roots.length-1);
        return DATA.roots[lo].lerp(DATA.roots[hi],Math.max(0,Math.min(1,frame-lo)));
    }
    /** Call once for each age, including windup/release. MC move collision clips
     * this request; never accumulate an obstructed displacement for a later tick. */
    public static Vec3 step(int age,float yaw){
        if(age<=0||age>=action().duration())return Vec3.ZERO;
        return root(age).subtract(root(age-1)).yRot((float)Math.toRadians(-yaw));
    }
    public static List<Sphere> spheres(float age,Vec3 origin,float yaw){
        double radius=0;
        for(var window:action().windows)if(age>=Math.ceil(window.begin()*20-.001F)&&age<=Math.floor(window.end()*20+.001F))radius=Math.max(radius,window.radius());
        if(radius==0)return List.of();
        return spheres(GoliathMotion.attack(EncounterMove.CHARGE,age),radius,origin,yaw,1F);
    }
    private static List<Sphere> spheres(float[][] pose,double radius,Vec3 origin,float yaw,float scale){
        var table=GoliathMotion.table();var transforms=table.transforms(pose);
        var names=Arrays.asList(table.bones);var base=new Matrix4f().scale(scale).rotateY((float)Math.toRadians(180-yaw));
        var result=new ArrayList<Sphere>();
        // Native indices 34..37 include Chest_L_End, not only arm descendants.
        // This is an index interval, not a skeleton hierarchy traversal.
        for(String joint:DATA.chain){
            var p=new Matrix4f(base).mul(transforms[names.indexOf("source_"+joint)]).transformPosition(new Vector3f());
            result.add(new Sphere(joint,origin.add(p.x,p.y+.01,p.z),radius));
        }
        for(int i=1;i<DATA.chain.length;i++){
            var a=result.get(i-1);var b=result.get(i);var delta=b.center.subtract(a.center);double distance=delta.length();
            int count=Math.max(0,(int)Math.ceil((distance-2*radius)/(2*radius)));
            if(count==0)continue;
            double gap=(distance-2*radius)/count;var direction=delta.scale(1/distance);
            for(int j=0;j<count;j++)result.add(new Sphere(a.joint+"->"+b.joint+":"+j,a.center.add(direction.scale(radius+gap*(j+.5))),radius));
        }
        return List.copyOf(result);
    }
    /** Empty contact must not fall back to the body bounding box. */
    public static AABB bounds(List<Sphere> spheres){
        if(spheres.isEmpty())return null;
        AABB bounds=null;
        for(var sphere:spheres){var b=new AABB(sphere.center,sphere.center).inflate(sphere.radius);bounds=bounds==null?b:bounds.minmax(b);}
        return GoliathAttackBounds.includingTouch(bounds);
    }
    public static Vec3 contact(Sphere sphere,AABB target){
        var hit=ProjectileSphereContact.sweep(sphere.center,sphere.center,sphere.radius,target);return hit==null?null:hit.point();
    }
    public static Vec3 contact(Sphere sphere,HydraContact.Box target){
        var hit=ProjectileSphereContact.sweep(sphere.center,sphere.center,sphere.radius,target);return hit==null?null:hit.point();
    }
}
