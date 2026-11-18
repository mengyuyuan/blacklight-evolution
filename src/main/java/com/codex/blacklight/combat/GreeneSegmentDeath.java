package com.codex.blacklight.combat;

import net.minecraft.nbt.CompoundTag;

/** P1 segment_corpse_death: copied drawable pose -> frame20..66 fitted to
 * 1.53333s -> frame66..95 at .6 speed -> remove. There is no Motion_Root
 * channel: the original Slug joints themselves retract below the fixed root.
 * A single observed 20Hz timeline is the MC scheduling adaptation. */
public final class GreeneSegmentDeath {
    public static final String CLIP="segment_death";
    public static final float FIRST_SECONDS=1.533329963684082F,BLEND_SECONDS=.2666670083999634F,TAIL_SPEED=.6000000238418579F;
    public static final int FIRST_FRAME=20,JOIN_FRAME=66,LAST_FRAME=95;
    private GreeneSegmentDeath(){}
    public static double seconds(){return FIRST_SECONDS+(LAST_FRAME-JOIN_FRAME)/(30D*TAIL_SPEED);}
    public static int ticks(){return (int)Math.ceil(seconds()*20);}
    public static float frame(float age){
        double seconds=Math.max(0,age)/20D;
        return (float)Math.min(LAST_FRAME,seconds<FIRST_SECONDS?FIRST_FRAME+(JOIN_FRAME-FIRST_FRAME)*seconds/FIRST_SECONDS
                :JOIN_FRAME+(seconds-FIRST_SECONDS)*30D*TAIL_SPEED);
    }
    public static float[][] pose(float[][] copied,float age){
        if(copied==null)return GreeneMotion.sample(CLIP,frame(age)*20/30F,false);
        if(age<=0)return copy(copied);
        var target=GreeneMotion.sample(CLIP,frame(age)*20/30F,false);
        float weight=Math.min(1,age/(20*BLEND_SECONDS));
        if(weight<1)for(int i=0;i<target.length;i++)target[i]=SourceBossMotion.blend(copied[i],target[i],weight);
        return GreeneMotion.compensate(target);
    }
    public static float[][] copy(float[][] pose){var result=new float[pose.length][];for(int i=0;i<pose.length;i++)result[i]=pose[i].clone();return result;}
    /** Store named channels rather than old array indices, so added helper
     * bones cannot silently reinterpret a saved corpse's copied drawable. */
    public static CompoundTag savePose(float[][] pose){
        var tag=new CompoundTag();var table=GreeneMotion.table();
        if(pose.length!=table.bones.length)throw new IllegalArgumentException("Incomplete Greene pose");
        for(int i=0;i<pose.length;i++){
            int[] row=new int[9];
            for(int axis=0;axis<9;axis++){
                float value=axis<pose[i].length?pose[i][axis]:axis>=6?1:0;
                if(!Float.isFinite(value))throw new IllegalArgumentException("Nonfinite Greene pose");
                row[axis]=Float.floatToIntBits(value);
            }
            tag.putIntArray(table.bones[i],row);
        }
        return tag;
    }
    public static float[][] loadPose(CompoundTag tag){
        var table=GreeneMotion.table();float[][] result=new float[table.bones.length][9];
        for(int i=0;i<result.length;i++){
            var row=tag.getIntArray(table.bones[i]);if(row.length!=9)return null;
            for(int axis=0;axis<9;axis++){
                float value=Float.intBitsToFloat(row[axis]);
                if(!Float.isFinite(value)||axis>=6&&value<=0)return null;
                result[i][axis]=value;
            }
        }
        return result;
    }
}
