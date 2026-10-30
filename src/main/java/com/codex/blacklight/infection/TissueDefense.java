package com.codex.blacklight.infection;

import net.minecraft.nbt.CompoundTag;

/** Directional, finite living armour. Integrity repairs only with loaded work and real food. */
public final class TissueDefense {
    public record Profile(double osteoderm,double nacre){public boolean active(){return power(osteoderm)>0||power(nacre)>0;}}
    public record Decision(float damage,float boneBlocked,float shellBlocked){}
    private float bone=1,shell=1;private int quiet,repair;private long hitAt=-1;private TissueCore hitCore;
    private static double power(double n){return Double.isFinite(n)?com.codex.blacklight.forge.WingMath.clamp(n,0,8):0;}
    private static float fraction(float n){return Float.isFinite(n)?com.codex.blacklight.forge.WingMath.clamp(n,0,1):0;}
    public float integrity(TissueCore core){return core==TissueCore.OSTEODERM?bone:core==TissueCore.NACRE?shell:1;}
    public Decision reduce(Profile p,float input,boolean physical,boolean piercing,double front,double height){
        if(!Float.isFinite(input)||input<=0||!physical||!Double.isFinite(front+height)||height<.3||height>.82)return new Decision(input,0,0);
        float a=0,b=0;double level=power(p.osteoderm);
        if(level>0&&front<.45)a=(float)(input*bone*(piercing?Math.min(.60,.30+.035*level):Math.min(.22,.10+.015*level)));
        level=power(p.nacre);
        if(level>0&&front>=.5)b=(float)((input-a)*shell*(piercing?Math.min(.68,.36+.04*level):Math.min(.25,.10+.02*level)));
        return new Decision(Math.max(input*.25F,input-a-b),a,b);
    }
    public void accepted(Decision d,float maximum,long now){
        quiet=0;repair=0;if(!Float.isFinite(maximum)||maximum<=0)return;
        bone=fraction(bone-d.boneBlocked/maximum*2);shell=fraction(shell-d.shellBlocked/maximum*2.5F);
        if(d.boneBlocked>0||d.shellBlocked>0){hitAt=now;hitCore=d.boneBlocked>0?TissueCore.OSTEODERM:TissueCore.NACRE;}
    }
    public int advance(Profile p,int food,boolean resting){
        quiet=Math.min(200,quiet+1);if(!resting||quiet<160){repair=0;return 0;}
        if(++repair<20)return 0;repair=0;int spent=0;
        if(food>spent&&power(p.osteoderm)>0&&bone<.9999F){bone=Math.min(1,bone+(float)(.025+.005*power(p.osteoderm)));spent++;}
        if(food>spent&&power(p.nacre)>0&&shell<.9999F){shell=Math.min(1,shell+(float)(.025+.005*power(p.nacre)));spent++;}
        return spent;
    }
    public CompoundTag write(){var t=new CompoundTag();t.putFloat("Bone",bone);t.putFloat("Shell",shell);t.putInt("Quiet",quiet);t.putInt("Repair",repair);return t;}
    public static TissueDefense read(CompoundTag t){var d=new TissueDefense();if(t.contains("Bone"))d.bone=fraction(t.getFloat("Bone"));if(t.contains("Shell"))d.shell=fraction(t.getFloat("Shell"));d.quiet=com.codex.blacklight.forge.WingMath.clamp(t.getInt("Quiet"),0,200);d.repair=com.codex.blacklight.forge.WingMath.clamp(t.getInt("Repair"),0,19);return d;}
    public CompoundTag visual(Profile p){var t=new CompoundTag();if(!p.active())return t;t.putFloat("Bone",bone);t.putFloat("Shell",shell);if(hitAt>=0&&hitCore!=null){t.putLong("HitAt",hitAt);t.putString("HitCore",hitCore.key());}return t;}
    public static void deform(float[] v,TissueCore core,CompoundTag state,double now){
        if(state.isEmpty()||v.length<48)return;float integrity=state.contains(core==TissueCore.OSTEODERM?"Bone":"Shell")?fraction(state.getFloat(core==TissueCore.OSTEODERM?"Bone":"Shell")):1;
        float recoil=0;if(core.key().equals(state.getString("HitCore"))){double elapsed=now-state.getLong("HitAt");if(elapsed>=0&&elapsed<10)recoil=(float)Math.sin(Math.PI*elapsed/10)*.07F;}
        float x=0,y=0,z=0;for(int i=0;i<24;i+=3){x+=v[i]/8;y+=v[i+1]/8;z+=v[i+2]/8;}
        float scale=.7F+.3F*integrity-recoil;for(int i=24;i<v.length;i+=3){v[i]=x+(v[i]-x)*scale;v[i+1]=y+(v[i+1]-y)*scale;v[i+2]=z+(v[i+2]-z)*scale;}
    }
}
