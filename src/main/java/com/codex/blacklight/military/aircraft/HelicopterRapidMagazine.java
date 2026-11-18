package com.codex.blacklight.military.aircraft;

import net.minecraft.nbt.CompoundTag;

/** Two actual weapon-instance stores, using the original Blackwatch player profile. */
public final class HelicopterRapidMagazine {
    public static final int CAPACITY=75,SHOT_WAIT=5;
    private int left=CAPACITY,right=CAPACITY,next=-1,wait,shots;
    public int left(){return left;}public int right(){return right;}public int shots(){return shots;}
    /** MC replenishment preserves pod alternation, shot counter and firing cooldown. */
    public int add(int offered){
        int taken=Math.min(Math.max(0,offered),CAPACITY*2-left-right);
        for(int i=0;i<taken;i++){if(left<=right&&left<CAPACITY||right==CAPACITY)left++;else right++;}
        return taken;
    }
    public void clear(){left=right=0;}
    public void tick(){if(wait>0)wait--;}
    public int readySide(){if(wait>0||left+right==0)return 0;return next<0?(left>0?-1:1):(right>0?1:-1);}
    public void fired(int side){
        if(side==0||side!=readySide())throw new IllegalStateException("Rapid pod unavailable");
        if(side<0)left--;else right--;next=-side;wait=SHOT_WAIT;if(shots<Integer.MAX_VALUE)shots++;
    }
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putInt("Version",1);tag.putInt("Left",left);tag.putInt("Right",right);tag.putInt("Next",next);tag.putInt("Wait",wait);tag.putInt("Shots",shots);return tag;
    }
    public void load(CompoundTag tag){
        // Pre-rapid aircraft receive the newly introduced stores once; present but
        // malformed state must never refill an exhausted magazine.
        left=right=CAPACITY;next=-1;wait=shots=0;if(tag.isEmpty())return;
        if(tag.getInt("Version")!=1||!tag.contains("Left",3)||!tag.contains("Right",3)||!tag.contains("Wait",3)){left=right=0;return;}
        left=Math.max(0,Math.min(CAPACITY,tag.getInt("Left")));right=Math.max(0,Math.min(CAPACITY,tag.getInt("Right")));
        next=tag.getInt("Next")>0?1:-1;wait=Math.max(0,Math.min(SHOT_WAIT,tag.getInt("Wait")));shots=Math.max(0,tag.getInt("Shots"));
    }
}
