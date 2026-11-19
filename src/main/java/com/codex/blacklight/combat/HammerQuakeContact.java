package com.codex.blacklight.combat;

import com.codex.blacklight.animation.BlacklightSkeleton;
import com.codex.blacklight.animation.MotionLibrary;
import com.google.gson.JsonParser;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The released ground punch fires when its visible fist crosses a solid top face.
 * Charge poses never enter this query. Damage and terrain feedback consume the same hit. */
public final class HammerQuakeContact {
    private record Probe(int bone,Vector3f local) {}
    private static final class Mesh { static final List<Probe> POINTS=load(); }
    private HammerQuakeContact() {}
    public static boolean owns(WeaponMove move){return move==WeaponMove.HAMMER_QUAKE||move==WeaponMove.HAMMER_QUAKE_CHARGED;}
    public static com.codex.blacklight.animation.BlacklightPose projectedPose(ServerPlayer p,WeaponMove move,float seconds,Vec3 feet,float yaw){
        var pose=MotionLibrary.pose(move.id,seconds,false);
        HammerGroundPose.quake(p,pose,move.id,seconds,feet,yaw,WeaponAttackScales.sample(move.id,seconds,false),WeaponAttackScales.sample(move.id,seconds,true));
        return pose;
    }
    private static final class StrikeReach {
        static final double PLAIN=forwardReach(WeaponMove.HAMMER_QUAKE);
        static final double CHARGED=forwardReach(WeaponMove.HAMMER_QUAKE_CHARGED);
        private static double forwardReach(WeaponMove move){
            var motion=LocalWeaponMotion.find(move.id).orElseThrow();
            double reach=0;
            for(int tick=move.start;tick<move.end;tick++){
                float seconds=tick/20F-motion.transitionSeconds();
                var pose=MotionLibrary.pose(move.id,seconds,false);
                var shape=WeaponAttackScales.sample(move.id,seconds,false);
                for(var vertex:HammerGroundPose.fist(pose,shape,false))
                    reach=Math.max(reach,-vertex.z);
            }
            return reach*.9375;
        }
    }
    /** Leave landing space for the enlarged fist when the authored stride approaches a wall. */
    public static Vec3 limitWallAdvance(ServerPlayer player,WeaponMove move,Vec3 step,float yaw){
        var forward=Vec3.directionFromRotation(0,yaw);
        double advance=step.dot(forward);
        if(advance<=0)return step;
        double reach=(move==WeaponMove.HAMMER_QUAKE?StrikeReach.PLAIN:StrikeReach.CHARGED)*player.getScale();
        var start=player.position().add(0,player.getBbHeight()*.8,0);
        var hit=player.level().clip(new ClipContext(start,start.add(forward.scale(reach+advance)),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(hit.getType()!=HitResult.Type.BLOCK)return step;
        double available=Math.max(0,start.distanceTo(hit.getLocation())-reach-.01);
        return available>=advance?step:step.subtract(forward.scale(advance-available));
    }
    /** Find the solid top face touched by the landed combo's enlarged right fist. */
    public static BlockHitResult comboLanding(ServerPlayer p,WeaponMove move,float seconds,float yaw){
        return projectedContact(p,move,seconds,p.position(),yaw,null);
    }
    private static BlockHitResult projectedContact(ServerPlayer p,WeaponMove move,float seconds,Vec3 feet,float yaw,BlockHitResult requested){
        var pose=projectedPose(p,move,seconds,feet,yaw);
        var world=new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).scale(.9375F*p.getScale());
        BlockHitResult result=null;double nearest=Double.POSITIVE_INFINITY;
        for(var local:HammerGroundPose.fist(pose,WeaponAttackScales.sample(move.id,seconds,false),false)){
            var point=world.transformPosition(local);var at=feet.add(point.x,point.y,point.z);
            // The solver stops just above the face. This small contact skin does
            // not allow a fist hovering over a ledge to emit a remote ground hit.
            if(requested!=null&&Math.abs(at.y-requested.getLocation().y)>.065)continue;
            var hit=p.level().clip(new ClipContext(at.add(0,.035,0),at.add(0,-.065,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=Direction.UP||hit.isInside())continue;
            double distance=hit.getLocation().distanceToSqr(requested==null?at:requested.getLocation());
            if(distance<nearest){nearest=distance;result=hit;}
        }
        return result;
    }
    private static List<Probe> load(){
        try(var input=HammerQuakeContact.class.getResourceAsStream("/assets/blacklight/models/form_hammerfists.json")){
            var json=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(input),StandardCharsets.UTF_8)).getAsJsonObject();
            var probes=new ArrayList<Probe>();var rig=BlacklightSkeleton.get();
            // This move's enlarged right fist delivers the slam; the other hand braces.
            for(String name:List.of("Hand_R")){
                var vertices=new LinkedHashSet<Vector3f>();
                for(var face:json.getAsJsonObject("joints").getAsJsonArray(name))for(var value:face.getAsJsonObject().getAsJsonArray("p")){
                    var p=value.getAsJsonArray();vertices.add(new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()));
                }
                // Support points are actual mesh vertices, not corners outside its surface.
                var surface=new LinkedHashSet<Vector3f>();
                for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
                    if(x==0&&y==0&&z==0)continue;
                    var direction=new Vector3f(x,y,z);
                    vertices.stream().max(Comparator.comparingDouble(v->v.dot(direction))).ifPresent(surface::add);
                }
                for(var p:surface)probes.add(new Probe(rig.searchJointByName(name).getId(),p));
            }
            return List.copyOf(probes);
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot load hammer contact mesh",e);}
    }
    private static Vec3[] points(ServerPlayer p,WeaponMove move,double seconds,Vec3 feet,float yaw){
        var rig=BlacklightSkeleton.get();var pose=rig.getPoseMatrices(MotionLibrary.pose(move.id,(float)seconds,false));
        var world=new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).scale(.9375F*p.getScale());
        var result=new Vec3[Mesh.POINTS.size()];int index=0;
        for(var probe:Mesh.POINTS){
            var shape=WeaponAttackScales.sample(move.id,(float)seconds,false);
            var at=new Matrix4f(world).mul(pose[probe.bone]).transformPosition(shape.point(new Vector3f(probe.local)));
            result[index++]=feet.add(at.x,at.y,at.z);
        }
        return result;
    }
    public static BlockHitResult sweep(ServerPlayer p,WeaponMove move,int tick,float offset,Vec3 oldFeet,float oldYaw,float yaw){
        // Startup contains a low supporting fist before the backswing. It is not the strike.
        if(!owns(move)||!p.onGround()||tick<move.start||tick>=move.end)return null;
        var definition=LocalWeaponMotion.find(move.id).orElseThrow();
        double correction=offset-Math.round(offset*20)/20.0;
        double from=Math.max(offset,(tick-1)/20.0-definition.transitionSeconds()+correction);
        double to=Math.max(offset,tick/20.0-definition.transitionSeconds()+correction);
        if(to<=from)return null;
        Vec3[] before=points(p,move,from,oldFeet,oldYaw);
        for(int sample=1;sample<=4;sample++){
            double alpha=sample/4.0;
            var after=points(p,move,from+(to-from)*alpha,oldFeet.lerp(p.position(),alpha),
                    oldYaw+net.minecraft.util.Mth.wrapDegrees(yaw-oldYaw)*(float)alpha);
            BlockHitResult first=null;double earliest=Double.POSITIVE_INFINITY;
            for(int i=0;i<after.length;i++){
                var a=before[i];var b=after[i];if(b.y>=a.y-.0001)continue;
                var hit=p.level().clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=Direction.UP||hit.isInside())continue;
                double fraction=a.distanceToSqr(hit.getLocation())/Math.max(1e-10,a.distanceToSqr(b));
                if(fraction<earliest){earliest=fraction;first=hit;}
            }
            if(first!=null){
                var resolved=projectedContact(p,move,(float)(from+(to-from)*alpha),oldFeet.lerp(p.position(),alpha),
                        oldYaw+net.minecraft.util.Mth.wrapDegrees(yaw-oldYaw)*(float)alpha,first);
                if(resolved!=null)return resolved;
            }
            before=after;
        }
        return null;
    }
}
