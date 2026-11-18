package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/** Native horizontal Motion_Root curve. Entity collision accepts each step;
 * blocked distance is never accumulated or recovered in a later frame. */
public final class HunterCounterMotion {
    private record Curve(int first,int last,double speed,Vec3[] positions) {
        Vec3 at(double frame){
            double local=Math.max(0,Math.min(last-first,frame-first));int lo=(int)local,hi=Math.min(positions.length-1,lo+1);
            return positions[lo].lerp(positions[hi],local-lo);
        }
    }
    private static final Curve COUNTER,RELEASE;
    private static final double SCALE;
    static {
        try(var in=Objects.requireNonNull(HunterCounterMotion.class.getResourceAsStream("/assets/blacklight/models/hunter_counter_root.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var json=JsonParser.parseReader(reader).getAsJsonObject();SCALE=json.get("sourceUnitBlocks").getAsDouble();
            if(json.get("schema").getAsInt()!=1||json.get("fps").getAsInt()!=30||!Double.isFinite(SCALE)||SCALE<=0)throw new IllegalStateException("Hunter root metadata");
            var clips=json.getAsJsonObject("clips");COUNTER=read(clips.getAsJsonObject("counter"),0,42);RELEASE=read(clips.getAsJsonObject("release"),3,76);
        }catch(Exception e){throw new IllegalStateException("Invalid native Hunter root curve",e);}
    }
    private static Curve read(JsonObject json,int first,int last){
        var values=json.getAsJsonArray("positions");double speed=json.get("speed").getAsDouble();
        if(json.get("firstFrame").getAsInt()!=first||json.get("lastFrame").getAsInt()!=last||values.size()!=last-first+1||!Double.isFinite(speed)||speed<=0)throw new IllegalStateException("Hunter root range");
        var rows=new Vec3[values.size()];
        for(int i=0;i<rows.length;i++){
            var row=values.get(i).getAsJsonArray();if(row.size()!=3)throw new IllegalStateException("Hunter root channels");
            double x=row.get(0).getAsDouble(),y=row.get(1).getAsDouble(),z=row.get(2).getAsDouble();
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalStateException("Hunter root nonfinite point");
            rows[i]=new Vec3(x,y,z);
        }
        return new Curve(first,last,speed,rows);
    }
    public static boolean uses(CreatureAttack move,boolean release){return move==CreatureAttack.COUNTER||move==CreatureAttack.BARRAGE&&release;}
    /** Relative model travel, with independent origins for the two source clips. */
    public static Vec3 offset(CreatureAttack move,boolean release,float age){
        if(!uses(move,release)||!Float.isFinite(age))return Vec3.ZERO;
        Curve curve=move==CreatureAttack.COUNTER?COUNTER:RELEASE;
        float entry=move==CreatureAttack.COUNTER?0:CreatureAttack.COUNTER_BARRAGE_ENTRY_TICK;
        var p=curve.at(curve.first+Math.max(0,age-entry)*1.5*curve.speed).subtract(curve.positions[0]);
        return new Vec3(p.x*SCALE,0,-p.z*SCALE);
    }
    public static Vec3 step(CreatureAttack move,boolean release,float previous,float age,float yaw){
        if(!Float.isFinite(previous)||!Float.isFinite(age)||!Float.isFinite(yaw)||age<=previous)return Vec3.ZERO;
        return offset(move,release,age).subtract(offset(move,release,previous)).yRot((float)Math.toRadians(-yaw));
    }
    private HunterCounterMotion(){}
}
