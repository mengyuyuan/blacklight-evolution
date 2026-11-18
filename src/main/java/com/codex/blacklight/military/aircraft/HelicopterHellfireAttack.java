package com.codex.blacklight.military.aircraft;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** Source HELLFIRE HOVER sequence, quantized to 20 Hz; movement selection stays external. */
public final class HelicopterHellfireAttack {
    public enum Stage { STRAFE, WAIT, AIM, FIRE, RECOVER }
    public static final int INITIAL_AVOID=300,REPEAT_AVOID=200,REEVALUATE=100,PULSE=4;
    private Stage stage=Stage.STRAFE;
    private int avoid=INITIAL_AVOID,remaining,afterWait,hoverAge;
    private boolean fired;
    public Stage stage(){return stage;}
    public boolean hovering(){return stage!=Stage.STRAFE;}
    public boolean ready(){return !hovering()&&avoid==0;}
    public boolean wantsShot(){return stage==Stage.FIRE&&!fired;}
    public int avoidTicks(){return avoid;}
    public int hoverAge(){return hoverAge;}
    public void reset(){stage=Stage.STRAFE;avoid=INITIAL_AVOID;remaining=afterWait=hoverAge=0;fired=false;}
    public void abort(){stage=Stage.STRAFE;remaining=afterWait=hoverAge=0;fired=false;}
    public boolean begin(int before,int after){
        if(!ready())return false;
        if(before<20||before>40||after<20||after>40)throw new IllegalArgumentException("Source waits are 1-2 seconds");
        avoid=REPEAT_AVOID;remaining=before;afterWait=after;hoverAge=0;fired=false;stage=Stage.WAIT;return true;
    }
    public void tick(boolean visible,boolean aligned){
        if(avoid>0)avoid--;
        if(!hovering())return;
        // Source outer Opportunity allows a new tactic after five seconds. The MC
        // controller bounds a stalled aim there and reacquires after lost sight.
        if(!visible||++hoverAge>=REEVALUATE){abort();return;}
        switch(stage){
            case WAIT -> {if(--remaining<=0){remaining=0;stage=Stage.AIM;}}
            case AIM -> {if(aligned){stage=Stage.FIRE;remaining=PULSE;}}
            case FIRE -> {if(--remaining<=0){stage=Stage.RECOVER;remaining=afterWait;}}
            case RECOVER -> {if(--remaining<=0)abort();}
            default -> {}
        }
    }
    public void shot(){if(wantsShot())fired=true;}
    /** Original Is3D=false compares 1 - normalized XZ dot <= .1, not .1 radians. */
    public static boolean aimed(Vec3 forward,Vec3 targetDelta){
        if(!HelicopterFlight.finite(forward)||!HelicopterFlight.finite(targetDelta))return false;
        Vec3 a=forward.multiply(1,0,1),b=targetDelta.multiply(1,0,1);
        return a.lengthSqr()>1e-8&&b.lengthSqr()>1e-8&&1-a.normalize().dot(b.normalize())<=.10000000149011612;
    }
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putInt("Version",1);tag.putString("Stage",stage.name());tag.putInt("Avoid",avoid);tag.putInt("Remaining",remaining);tag.putInt("AfterWait",afterWait);tag.putInt("HoverAge",hoverAge);tag.putBoolean("Fired",fired);return tag;
    }
    public void load(CompoundTag tag){
        reset();if(tag.getInt("Version")!=1||!tag.contains("Stage",Tag.TAG_STRING))return;
        for(String key:new String[]{"Avoid","Remaining","AfterWait","HoverAge"})if(!tag.contains(key,Tag.TAG_INT))return;
        Stage next;try{next=Stage.valueOf(tag.getString("Stage"));}catch(IllegalArgumentException ex){return;}
        int a=tag.getInt("Avoid"),r=tag.getInt("Remaining"),w=tag.getInt("AfterWait"),h=tag.getInt("HoverAge");
        if(a<0||a>INITIAL_AVOID||r<0||r>40||h<0||h>=REEVALUATE)return;
        if(next!=Stage.STRAFE&&(a>REPEAT_AVOID||w<20||w>40||next==Stage.FIRE&&(r<1||r>PULSE)||next==Stage.AIM&&r!=0||next!=Stage.AIM&&r<1))return;
        if(next==Stage.STRAFE&&(r!=0||w!=0||h!=0))return;
        stage=next;avoid=a;remaining=r;afterWait=w;hoverAge=h;fired=tag.getBoolean("Fired");
    }
}
