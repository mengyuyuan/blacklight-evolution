package com.codex.blacklight.combat;

import com.codex.blacklight.infection.*;
import org.joml.Quaternionf;

/** Authored special moves on the existing hierarchy; original sampled clips stay unchanged. */
public final class EvolvedVariantPose {
    private EvolvedVariantPose(){}
    public static float[] special(EvolvedSpecialMove move,float t){
        var pose=EvolvedMotions.sample("idle",0,true);
        float wind=EvolvedForm.smooth(t/move.contact),out=1-EvolvedForm.smooth((t-move.retract)/(move.duration-move.retract));
        float strike=EvolvedForm.smooth((t-move.contact+3)/5);
        float power=wind*out;
        switch(move){
            case SLAM->{float arm=(-2.55F*(1-strike)+.35F*strike)*power;
                rotate(pose,"Shoulder_R",arm,0,-.2F*power);rotate(pose,"Shoulder_L",arm,0,.2F*power);
                rotate(pose,"Spine_2",.38F*strike*out,0,0);rotate(pose,"Knee_R",-.25F*power,0,0);rotate(pose,"Knee_L",-.25F*power,0,0);}
            case BASH->{rotate(pose,"Shoulder_L",(-.9F-.55F*strike)*power,0,.20F*power);rotate(pose,"Elbow_L",-.65F*power,0,0);rotate(pose,"Spine_2",.15F*strike*out,0,0);}
            case POUNCE->{rotate(pose,"Spine_2",.32F*power,0,0);rotate(pose,"Shoulder_R",-1.1F*power,0,-.32F*power);rotate(pose,"Shoulder_L",-1.1F*power,0,.32F*power);rotate(pose,"Hip_R",.28F*power,0,0);rotate(pose,"Knee_L",-.4F*power,0,0);}
            case LASH->{rotate(pose,"Shoulder_R",-1.42F*power,.2F*power,0);rotate(pose,"Elbow_R",-.2F*power,0,0);rotate(pose,"Spine_2",0,-.22F*wind*out,0);}
            case DASH->{rotate(pose,"Spine_2",.42F*power,0,0);rotate(pose,"Shoulder_R",.5F*power,0,-.28F*power);rotate(pose,"Shoulder_L",-1.1F*power,0,.18F*power);rotate(pose,"Hip_R",.35F*power,0,0);rotate(pose,"Knee_L",-.55F*power,0,0);}
            case VOLLEY->{rotate(pose,"Shoulder_R",-1.48F*power,0,-.08F*power);rotate(pose,"Elbow_R",-.08F*power,0,0);rotate(pose,"Shoulder_L",-.5F*power,0,.35F*power);
                float recoil=t>=move.contact&&t<move.retract?(float)Math.sin((t-move.contact)*Math.PI/3)*.055F:0;rotate(pose,"Spine_2",-recoil,0,0);}
            case MEND->{rotate(pose,"Shoulder_R",-.55F*power,0,-.45F*power);rotate(pose,"Shoulder_L",-.55F*power,0,.45F*power);rotate(pose,"Elbow_L",-.7F*power,0,0);rotate(pose,"Head",.16F*power,0,0);}
        }return pose;
    }
    public static float[] stance(float[] original,EvolvedArchetype type,String clip){
        if(type==EvolvedArchetype.BLADE||!clip.equals("idle")&&!clip.equals("guard"))return original;
        var pose=original.clone();
        switch(type){
            case STRENGTH->{rotate(pose,"Shoulder_R",.05F,0,-.12F);rotate(pose,"Shoulder_L",.05F,0,.12F);}
            case ARMORED->{if(clip.equals("guard")){rotate(pose,"Shoulder_L",-.75F,0,.12F);rotate(pose,"Elbow_L",-.45F,0,0);}}
            case PREDATOR->{rotate(pose,"Spine_2",.12F,0,0);rotate(pose,"Shoulder_R",-.15F,0,-.18F);rotate(pose,"Shoulder_L",-.15F,0,.18F);}
            case TENDRIL->{rotate(pose,"Shoulder_R",-.12F,0,-.09F);rotate(pose,"Elbow_R",-.16F,0,0);}
            case SKIRMISHER->{rotate(pose,"Spine_2",.08F,0,0);rotate(pose,"Shoulder_R",.15F,0,-.08F);}
            case SENSORY->{rotate(pose,"Elbow_R",-.4F,0,0);}
            case REGENERATOR->{rotate(pose,"Elbow_L",-.35F,0,0);}
            default->{}
        }return pose;
    }
    private static void rotate(float[] pose,String root,float x,float y,float z){
        var delta=new Quaternionf().rotationXYZ(x,y,z);var bones=EvolvedMotions.bones();int start=EvolvedMotions.index(root);var child=new boolean[bones.size()];
        for(int i=0;i<bones.size();i++)if(child[i]=i==start||bones.get(i).parent()>=0&&child[bones.get(i).parent()]){
            int o=i*7;var q=new Quaternionf(delta).mul(new Quaternionf(pose[o],pose[o+1],pose[o+2],pose[o+3])).normalize();pose[o]=q.x;pose[o+1]=q.y;pose[o+2]=q.z;pose[o+3]=q.w;
        }
    }
}
