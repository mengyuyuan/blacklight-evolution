package com.codex.blacklight.combat;

import org.joml.Quaternionf;

/** Small additive life on the source's static idle/guard poses. Attack curves stay untouched. */
public final class EvolvedRestPose {
    private EvolvedRestPose(){}
    public static float[] apply(float[] source,String clip,double ticks,long seed){
        if(source==null||!clip.equals("idle")&&!clip.equals("guard"))return source;
        float[] pose=source.clone();double phase=Math.floorMod(seed,6283)*.001;
        float breath=(float)Math.sin(ticks*.09+phase),shift=(float)Math.sin(ticks*.028+phase);
        rotate(pose,"Character_Root",new Quaternionf().rotationZ(shift*.006F));
        rotate(pose,"Spine_2",new Quaternionf().rotationX(breath*.009F));
        rotate(pose,"Head",new Quaternionf().rotationY((float)Math.sin(ticks*.019+phase)*.008F));
        pose[EvolvedMotions.index("Spine_2")*7+5]-=breath*.10F;
        return pose;
    }
    private static void rotate(float[] pose,String root,Quaternionf delta){
        var bones=EvolvedMotions.bones();int start=EvolvedMotions.index(root);boolean[] child=new boolean[bones.size()];
        for(int i=0;i<bones.size();i++)if(child[i]=i==start||bones.get(i).parent()>=0&&child[bones.get(i).parent()]){
            int o=i*7;var q=new Quaternionf(delta).mul(new Quaternionf(pose[o],pose[o+1],pose[o+2],pose[o+3])).normalize();
            pose[o]=q.x;pose[o+1]=q.y;pose[o+2]=q.z;pose[o+3]=q.w;
        }
    }
}
