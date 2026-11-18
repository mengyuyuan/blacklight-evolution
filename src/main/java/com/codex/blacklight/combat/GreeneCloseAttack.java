package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** The independent P1 RightBumper/Slug branch, not a player grab or missile.
 * Native sources and explicit mixer/20 Hz adaptations are recorded in
 * art/encounters/greene-close-attack-source.json. No entity callbacks occur here. */
public final class GreeneCloseAttack {
    public static final float STEP=.05F, RANGE=25F, COOLDOWN_SECONDS=2F, RESET_DISTANCE=17F;
    public static final float MIDDLE=1F, TAIL=1.3333300352096558F, FPS=24F, TAIL_SPEED=.800000011920929F;
    public static final float DURATION=TAIL+41F/(FPS*TAIL_SPEED);
    public enum Side {
        LEFT("close_left","Slug_02",1), RIGHT("close_right","Slug_01",0);
        private final String clip,chain;private final int segment;
        Side(String clip,String chain,int segment){this.clip=clip;this.chain=chain;this.segment=segment;}
        public String clip(){return clip;}
        public String joint(){return "native_"+chain+"_Spine_5";}
        public int segment(){return segment;}
        boolean ownsBone(String bone){return bone.startsWith("native_"+chain+"_");}
    }
    /** All are current-position overlaps (UseShapecast=false), in authored
     * order. Values below are source units/damage, not Minecraft damage. */
    public enum Track {
        TIP(TAIL,1.5666699409484863F,4,500,.20000000298023224F,"919001ca3cc2c137","None",
            new float[]{1500,5000,-3000},new float[]{1500,5000,-3000}),
        INNER(1.5666699409484863F,1.7333300113677979F,5,500,0,"5da4043435eaeeac","Crush",
            new float[]{1500,4000,-2000},new float[]{2000,4000,-2000}),
        OUTER(1.7333300113677979F,1.899999976158142F,7,200,0,"5da4043435eaeeac","Crush",
            new float[]{1500,3000,-1500},new float[]{1500,3000,-1500});
        private final float begin,end,radius,damage,friendly;
        private final String hitType,damageType;private final float[] impulseMin,impulseMax;
        Track(float begin,float end,float radius,float damage,float friendly,String hitType,String damageType,float[] min,float[] max){
            this.begin=begin;this.end=end;this.radius=radius;this.damage=damage;this.friendly=friendly;
            this.hitType=hitType;this.damageType=damageType;impulseMin=min;impulseMax=max;
        }
        public float begin(){return begin;}public float end(){return end;}public float radius(){return radius;}
        public float damage(){return damage;}public float friendlyFire(){return friendly;}
        public int collideWith(){return 0xc030;}public boolean useShapecast(){return false;}
        public String hitType(){return hitType;}public String damageType(){return damageType;}
        public float[] impulseMin(){return impulseMin.clone();}public float[] impulseMax(){return impulseMax.clone();}
        public float[] offset(){return this==TIP?new float[]{-2,0,0}:new float[]{0,0,-6};}
    }
    /** Poll clock precedes this update's advance, exactly as the native Hit
     * phase. Consume this stable list before clearing receipts at activeAfter=0.
     * An expiring track remains in this poll; isCurrent detects reentrant load,
     * clear, begin or another advance. finished is the MC clip-completion gate. */
    public record Frame(Side side,List<Track> tracks,float elapsedSeconds,int activeAfter,boolean finished,long revision){
        public Frame{tracks=List.copyOf(tracks);}
    }
    private static final Track[] ORDER=Track.values();
    private final boolean[] active=new boolean[3],done=new boolean[3];
    private final float[] remaining=new float[3],priorDelta=new float[3];
    private Side side;private float seconds;private long revision;private Frame frame;
    public GreeneCloseAttack(){clear();}
    public void begin(Side selected){
        clear();side=Objects.requireNonNull(selected);
        for(var track:ORDER){int i=track.ordinal();done[i]=false;remaining[i]=track.end()-track.begin();}
    }
    public void clear(){side=null;seconds=0;frame=null;revision++;Arrays.fill(active,false);Arrays.fill(done,true);Arrays.fill(remaining,0);Arrays.fill(priorDelta,0);}
    public boolean active(){return side!=null;}
    public Side side(){return side;}
    public float seconds(){return seconds;}
    public int activeCount(){int n=0;for(boolean value:active)if(value)n++;return n;}
    public boolean isCurrent(Frame expected){return expected!=null&&expected==frame&&expected.revision()==revision;}
    public Frame advance(){
        float pollTime=seconds;Side polledSide=side;var hits=new ArrayList<Track>();revision++;
        if(side!=null){
            for(var track:ORDER)if(active[track.ordinal()])hits.add(track);
            for(var track:hits){int i=track.ordinal();remaining[i]-=priorDelta[i];if(remaining[i]<0){active[i]=false;done[i]=true;}}
            seconds+=STEP;
            for(var track:ORDER){int i=track.ordinal();if(done[i]||seconds<track.begin())continue;
                if(active[i])priorDelta[i]=STEP;else{active[i]=true;priorDelta[i]=seconds-track.begin();}}
            if(seconds>=DURATION){side=null;Arrays.fill(active,false);Arrays.fill(done,true);}
        }
        frame=new Frame(polledSide,hits,pollTime,activeCount(),polledSide!=null&&side==null,revision);return frame;
    }
    /** The AI branch tests OR; the actual left AND right motion nodes both
     * serialize Segment1=true. Keep that source asymmetry, not a corrected pair. */
    public static boolean aiEligible(double sourceXz,boolean segment1,boolean segment2){return Double.isFinite(sourceXz)&&sourceXz>=0&&sourceXz<RANGE&&(segment1||segment2);}
    public static boolean motionEligible(boolean segment1){return segment1;}
    /** Random10/Random10 after their identical motion eligibility. */
    public static Side choose(float unitRandom){if(!Float.isFinite(unitRandom)||unitRandom<0||unitRandom>=1)throw new IllegalArgumentException("Expected [0,1)");return unitRandom<.5F?Side.LEFT:Side.RIGHT;}
    public static float sourceFrame(float ageTicks){
        float t=Math.max(0,ageTicks/20F);
        if(t<MIDDLE)return t*30;
        if(t<TAIL)return 30+(t-MIDDLE)*4/(TAIL-MIDDLE);
        return Math.min(75,34+(t-TAIL)*FPS*TAIL_SPEED);
    }
    private static final class Clips {
        static final Map<String,SourceBossMotion.Clip> VALUES=load();
        private static Map<String,SourceBossMotion.Clip> load(){
            String path="/assets/blacklight/models/greene_close_source.json";
            try(var in=Objects.requireNonNull(GreeneCloseAttack.class.getResourceAsStream(path));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
                var root=JsonParser.parseReader(reader).getAsJsonObject();var gson=new Gson();var table=GreeneMotion.table();
                if(!Arrays.equals(table.bones,gson.fromJson(root.get("bones"),String[].class))
                        ||!Arrays.equals(table.parents,gson.fromJson(root.get("parents"),int[].class))
                        ||!Arrays.deepEquals(table.pivots,gson.fromJson(root.get("pivots"),float[][].class))
                        ||Math.abs(root.get("pixelsPerSourceUnit").getAsDouble()*GreenePresentation.SCALE/16-table.sourceUnitBlocks())>1e-6)
                    throw new IllegalStateException("Close attack rig differs from shared Greene rig");
                var result=new HashMap<String,SourceBossMotion.Clip>();
                for(var side:Side.values()){
                    var c=root.getAsJsonObject("clips").getAsJsonObject(side.clip);var rows=gson.fromJson(c.get("frames"),float[][][].class);
                    if(c.get("fps").getAsFloat()!=FPS||rows.length!=76)throw new IllegalStateException("Invalid Slug source clip");
                    for(var row:rows){if(row.length!=table.bones.length)throw new IllegalStateException("Incomplete Slug pose");
                        for(var bone:row){if(bone.length!=6&&bone.length!=9)throw new IllegalStateException("Incomplete Slug transform");for(float v:bone)if(!Float.isFinite(v))throw new IllegalStateException("Invalid Slug transform");}}
                    result.put(side.clip,new SourceBossMotion.Clip(c.get("source").getAsString(),FPS,c.get("seconds").getAsFloat(),rows));
                }return Map.copyOf(result);
            }catch(Exception e){throw new IllegalStateException("Invalid Greene close animation",e);}
        }
    }
    /** Exact source sample before the MC animation mixer adaptation. */
    public static float[][] sample(Side side,float sourceFrame){
        if(side==null||!Float.isFinite(sourceFrame))throw new IllegalArgumentException("Invalid Slug sample");
        var clip=Clips.VALUES.get(side.clip);float at=Math.max(0,Math.min(75,sourceFrame));int lo=(int)at,hi=Math.min(75,lo+1);
        var pose=new float[clip.frames()[lo].length][];
        for(int i=0;i<pose.length;i++)pose[i]=SourceBossMotion.blend(clip.frames()[lo][i],clip.frames()[hi][i],at-lo);
        return GreeneMotion.compensate(pose);
    }
    /** Both render and contact MUST use this same result. Native FIG spawns
     * shoot and close concurrently. The selected Slug partition overlays a
     * shooting base; otherwise close supplies the body pose. Partition masking
     * and fade envelopes are explicit MC mixer adapters, not recovered native
     * priority comparison or exact blending. No caller-owned rows are mutated. */
    public static float[][] pose(float[][] base,Side side,float ageTicks,boolean shooting){
        var out=new float[base.length][];for(int i=0;i<base.length;i++)out[i]=base[i].clone();
        if(side==null||!Float.isFinite(ageTicks)||ageTicks<0||ageTicks/20F>=DURATION)return out;
        var current=sample(side,sourceFrame(ageTicks));float t=ageTicks/20F;
        float boundary=t>=TAIL?TAIL:t>=MIDDLE?MIDDLE:0;
        float blend=boundary==0?.33333298563957214F:.06666669994592667F;
        if(boundary>0&&t-boundary<blend){var previous=sample(side,boundary==MIDDLE?30:34);
            for(int i=0;i<current.length;i++)current[i]=SourceBossMotion.blend(previous[i],current[i],Math.max(0,(t-boundary)/blend));}
        float weight=Math.min(1,Math.min(t/.33333298563957214F,(DURATION-t)/.16666699945926666F));
        var names=GreeneMotion.table().bones;
        for(int i=0;i<out.length;i++)if(!shooting||side.ownsBone(names[i]))out[i]=SourceBossMotion.blend(out[i],current[i],Math.max(0,weight));
        return GreeneMotion.compensate(out);
    }
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putInt("Version",1);tag.putInt("Side",side==null?-1:side.ordinal());tag.putFloat("Seconds",seconds);
        for(var t:ORDER){int i=t.ordinal();var row=new CompoundTag();row.putBoolean("Active",active[i]);row.putBoolean("Done",done[i]);row.putFloat("Remaining",remaining[i]);row.putFloat("PriorDelta",priorDelta[i]);tag.put(t.name(),row);}return tag;
    }
    /** Missing/corrupt snapshots fail closed and never backfill old contacts. */
    public void load(CompoundTag tag){
        clear();if(tag==null||tag.getInt("Version")!=1||!tag.contains("Side",Tag.TAG_INT)||!tag.contains("Seconds",Tag.TAG_FLOAT))return;
        int s=tag.getInt("Side");float time=tag.getFloat("Seconds");
        if(s<0||s>=Side.values().length||!Float.isFinite(time)||time<0||time>=DURATION)return;
        for(var t:ORDER){
            if(!tag.contains(t.name(),Tag.TAG_COMPOUND))return;var row=tag.getCompound(t.name());
            if(!row.contains("Active",Tag.TAG_BYTE)||!row.contains("Done",Tag.TAG_BYTE)||!row.contains("Remaining",Tag.TAG_FLOAT)||!row.contains("PriorDelta",Tag.TAG_FLOAT))return;
            boolean a=row.getBoolean("Active"),d=row.getBoolean("Done");float r=row.getFloat("Remaining"),delta=row.getFloat("PriorDelta");
            if(!Float.isFinite(r)||!Float.isFinite(delta)||r< -STEP||r>t.end()-t.begin()||delta<0||delta>STEP
                    ||a&&(d||r<0||time<t.begin())||!a&&!d&&(time>=t.begin()||r!=t.end()-t.begin()||delta!=0))return;
        }
        side=Side.values()[s];seconds=time;
        for(var t:ORDER){var row=tag.getCompound(t.name());int i=t.ordinal();active[i]=row.getBoolean("Active");done[i]=row.getBoolean("Done");remaining[i]=row.getFloat("Remaining");priorDelta[i]=row.getFloat("PriorDelta");}
    }
}
