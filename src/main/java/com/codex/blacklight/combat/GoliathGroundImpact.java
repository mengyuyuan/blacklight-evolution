package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.entity.EncounterMob;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;

/** Source front-slam cone rays drive the ground feedback and pavement contacts. */
public final class GoliathGroundImpact {
    record Pulse(int atTick,float[] position,float[] direction,float[] up,float minAngle,float maxAngle,
                 float startDistance,float length,int minDecals,int maxDecals,float minDecalScale,float maxDecalScale) {}
    record Ray(Vec3 from,Vec3 to,float feedbackSize) {}
    private record Stamp(long started,int mask,Set<Long> columns) {}
    private static final Pulse[] PULSES;
    private static final float SCALE;
    private static final Map<EncounterMob,Stamp> FIRED=new WeakHashMap<>();
    static {
        try(var in=Objects.requireNonNull(GoliathGroundImpact.class.getResourceAsStream("/assets/blacklight/models/goliath_ground_impact.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var root=JsonParser.parseReader(reader).getAsJsonObject();SCALE=root.get("blocksPerSourceUnit").getAsFloat();
            PULSES=new Gson().fromJson(root.get("pulses"),Pulse[].class);
            if(PULSES.length!=3||!Float.isFinite(SCALE)||SCALE<=0)throw new IllegalStateException("Invalid Goliath ground pulses");
            for(var p:PULSES)if(p.position.length!=3||p.direction.length!=3||p.up.length!=3||p.minAngle<0||p.maxAngle>=90||p.minAngle>p.maxAngle||p.length<=0||p.minDecals<1||p.maxDecals>10||p.minDecals>p.maxDecals)throw new IllegalStateException("Invalid Goliath cone");
        }catch(Exception e){throw new IllegalStateException("Invalid source Goliath ground effect",e);}
    }
    private GoliathGroundImpact(){}
    static int pulseIndex(int age){for(int i=0;i<PULSES.length;i++)if(PULSES[i].atTick==age)return i;return -1;}
    static Pulse pulse(int index){return PULSES[index];}
    static float scale(){return SCALE;}

    /** Call once each committed attack tick, including the ticks after Hit. */
    public static void tick(EncounterMob mob,int age){
        if(!(mob.level() instanceof ServerLevel level)||mob.kind()!=EncounterKind.GOLIATH||mob.move()!=EncounterMove.HAMMER||!mob.isAlive())return;
        int index=pulseIndex(age);if(index<0)return;
        long started=level.getGameTime()-age;var prior=FIRED.get(mob);int mask=prior!=null&&prior.started==started?prior.mask:0;
        if((mask&(1<<index))!=0)return;
        var columns=mask==0?new HashSet<Long>():prior.columns;
        FIRED.put(mob,new Stamp(started,mask|(1<<index),columns));
        var surfaces=new ArrayList<TerrainImpactEffects.Surface>();float size=0;int order=0;
        var rays=rays(index,age,mob.position(),mob.getYRot(),mob.getRandom(),GoliathPresentation.SCALE);
        for(var ray:rays){
            int sample=order++;
            if(!available(level,ray.from,ray.to))continue;
            var hit=level.clip(new ClipContext(ray.from,ray.to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob));
            // A wall/inside hit consumes this ray. Never continue through it.
            if(hit.getType()!=HitResult.Type.BLOCK||hit.isInside()||hit.getDirection()!=Direction.UP)continue;
            var block=level.getBlockState(hit.getBlockPos());
            if(block.getRenderShape()!=RenderShape.MODEL||!block.getFluidState().isEmpty())continue;
            var travel=hit.getLocation().subtract(ray.from);
            surfaces.add(new TerrainImpactEffects.Surface(hit,Math.atan2(travel.z,travel.x),1,sample));
            size=Math.max(size,ray.feedbackSize);
        }
        // One contact packet/sound per batch, with material and positions from
        // those actual rays. No synthetic rings around the boss's feet.
        if(!surfaces.isEmpty()){
            ContactEffects.ground(mob,surfaces.get(0).hit(),size,true,List.copyOf(surfaces));
            GoliathTerrainImpact.ground(mob,surfaces,index,columns,rays.get(0).from());
        }
    }

    static boolean available(ServerLevel level,Vec3 from,Vec3 to){
        // Vanilla voxel traversal extends ray endpoints by a small epsilon.
        // Include that margin so a boundary ray cannot fetch an unchecked chunk.
        var bounds=new AABB(from,to).inflate(.001);var border=level.getWorldBorder();
        if(bounds.minX<border.getMinX()||bounds.maxX>border.getMaxX()||bounds.minZ<border.getMinZ()||bounds.maxZ>border.getMaxZ())return false;
        int minX=BlockPos.containing(bounds.minX,0,0).getX()>>4,maxX=BlockPos.containing(bounds.maxX,0,0).getX()>>4;
        int minZ=BlockPos.containing(0,0,bounds.minZ).getZ()>>4,maxZ=BlockPos.containing(0,0,bounds.maxZ).getZ()>>4;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(level.getChunkSource().getChunkNow(x,z)==null)return false;
        return true;
    }

    static List<Ray> rays(int index,float age,Vec3 origin,float yaw,RandomSource random){
        return rays(index,age,origin,yaw,random,1F);
    }
    private static List<Ray> rays(int index,float age,Vec3 origin,float yaw,RandomSource random,float scale){
        var p=PULSES[index];var table=GoliathMotion.table();var pose=GoliathMotion.attack(EncounterMove.HAMMER,age);
        int bone=Arrays.asList(table.bones).indexOf("source_Motion_Root");
        if(bone<0)throw new IllegalStateException("Missing original Goliath root");
        var matrix=table.transforms(pose)[bone];
        var base=matrix.transformPosition(new Vector3f(-p.position[0]*SCALE,p.position[1]*SCALE,p.position[2]*SCALE));
        var axis=matrix.transformDirection(new Vector3f(-p.direction[0],p.direction[1],p.direction[2])).normalize();
        Vec3 center=world(new Vec3(base.x,base.y,base.z).scale(scale),origin,yaw),forward=worldDirection(new Vec3(axis.x,axis.y,axis.z),yaw).normalize();
        // Source direction and up are antiparallel on these three tracks. The
        // randomized full azimuth is invariant to the orthogonal basis choice.
        Vec3 side=forward.cross(Math.abs(forward.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),other=forward.cross(side).normalize();
        int count=p.minDecals+random.nextInt(p.maxDecals-p.minDecals+1);double phase=random.nextDouble()*Math.PI*2;
        var result=new ArrayList<Ray>(count);
        for(int i=0;i<count;i++){
            double angle=Math.toRadians(p.minAngle+random.nextDouble()*(p.maxAngle-p.minAngle));
            double around=phase+i*Math.PI*2/count+(random.nextDouble()*2-1)*.2;
            var direction=forward.scale(Math.cos(angle)).add(side.scale(Math.sin(angle)*Math.cos(around))).add(other.scale(Math.sin(angle)*Math.sin(around))).normalize();
            var from=center.add(direction.scale(p.startDistance*SCALE*scale));
            float feedback=(p.minDecalScale+random.nextFloat()*(p.maxDecalScale-p.minDecalScale))*SCALE*scale;
            result.add(new Ray(from,from.add(direction.scale(p.length*SCALE*scale)),feedback));
        }
        return List.copyOf(result);
    }
    private static Vec3 world(Vec3 model,Vec3 origin,float yaw){return worldDirection(model,yaw).add(origin);}
    private static Vec3 worldDirection(Vec3 model,float yaw){return model.multiply(-1,1,-1).yRot((float)Math.toRadians(-yaw));}
}
