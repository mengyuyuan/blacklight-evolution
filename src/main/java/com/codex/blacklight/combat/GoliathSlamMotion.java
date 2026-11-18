package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterKind;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/** The front-slam's original horizontal Motion_Root, delivered once through
 * entity movement. Source Y and final native support/physics remain separate. */
public final class GoliathSlamMotion {
    private static final Vec3[] ROOTS=load();
    private GoliathSlamMotion() {}
    private static SourceBossAction action(){return Objects.requireNonNull(SourceBossAction.get(EncounterKind.GOLIATH,EncounterMove.HAMMER));}
    private static Vec3[] load(){
        try(var in=Objects.requireNonNull(GoliathSlamMotion.class.getResourceAsStream("/assets/blacklight/models/goliath_slam_root.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var json=JsonParser.parseReader(reader).getAsJsonObject();double scale=json.get("blocksPerSourceUnit").getAsDouble();
            var points=json.getAsJsonArray("rootPositionsSource");
            if(json.get("fps").getAsInt()!=30||points.size()!=89||!Double.isFinite(scale)||scale<=0
                    ||Math.abs(scale-GoliathMotion.table().sourceUnitBlocks())>1e-7
                    ||!json.get("animation").getAsString().equals(action().animation))throw new IllegalStateException("Front-slam root/source clock mismatch");
            var roots=new Vec3[points.size()];
            for(int i=0;i<roots.length;i++){
                var p=points.get(i).getAsJsonArray();double x=p.get(0).getAsDouble(),y=p.get(1).getAsDouble(),z=p.get(2).getAsDouble();
                if(p.size()!=3||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(y-6)>1e-7)
                    throw new IllegalStateException("Unexpected original front-slam root at "+i);
                roots[i]=new Vec3(x*scale,0,-z*scale);
            }
            return roots;
        }catch(Exception ex){throw new IllegalStateException("Invalid original Goliath front-slam root",ex);}
    }
    /** Original displacement relative to frame0 in actor-yaw0 coordinates.
     * This is a sampling reference, never a destination to chase after a wall. */
    public static Vec3 sourceOffset(float age){
        if(!Float.isFinite(age))throw new IllegalArgumentException("Finite action age required");
        var action=action();float frame=action.sourceTick(Math.max(0,Math.min(age,action.duration())))*action.fps/20;
        int lo=Math.max(0,Math.min((int)frame,ROOTS.length-1)),hi=Math.min(lo+1,ROOTS.length-1);
        return ROOTS[lo].lerp(ROOTS[hi],Math.max(0,Math.min(1,frame-lo))).subtract(ROOTS[0]);
    }
    /** Per-action receipt. A skipped update consumes the gap without moving;
     * repeated ages, cancellation, reload and a fresh action cannot repay it. */
    public static final class State {
        private boolean active;
        private int lastAge;
        public void begin(){active=true;lastAge=0;}
        public void cancel(){active=false;lastAge=0;}
        public Vec3 advance(int age,float yaw){
            if(!active||age<=lastAge)return Vec3.ZERO;
            if(!Float.isFinite(yaw)||age>=action().duration()){cancel();return Vec3.ZERO;}
            int previous=lastAge;lastAge=age;
            if(age!=previous+1)return Vec3.ZERO;
            return sourceOffset(age).subtract(sourceOffset(previous)).yRot((float)Math.toRadians(-yaw));
        }
    }
}
