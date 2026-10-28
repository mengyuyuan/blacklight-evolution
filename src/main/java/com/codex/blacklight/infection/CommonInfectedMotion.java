package com.codex.blacklight.infection;

/** Authored civilian joints, in radians. Sampled from absolute clocks, never accumulated per frame. */
public final class CommonInfectedMotion {
    public static final int CONTACT_TICK=11, ATTACK_TICKS=26;
    public record Pose(float torso,float twist,float head,float armR,float armL,float elbowR,float elbowL,
                       float legR,float legL,float kneeR,float kneeL,float roll,float bob){}
    private CommonInfectedMotion(){}
    public static int appearance(long most,long least){return Math.floorMod((int)(most^(most>>>32)^least^(least>>>32)),4);}
    public static Pose sample(double age,float phase,float movement,boolean chasing,float attackAge,int hand){
        float speed=Math.clamp(movement,0,1),stride=(float)Math.cos(phase*.6662F);
        float breath=(float)Math.sin(age*.08)*.012F,weight=chasing?.25F:.11F;
        float torso=weight+breath,twist=stride*speed*.065F,head=-weight*.55F;
        float armR=-.12F-stride*speed*.75F,armL=-.08F+stride*speed*.75F;
        float elbowR=-.12F-speed*.60F,elbowL=-.20F-speed*.48F;
        float legR=stride*speed*.95F,legL=-legR;
        float kneeR=Math.max(0,-stride)*speed*.5F,kneeL=Math.max(0,stride)*speed*.5F;
        float roll=(float)Math.sin(age*.043)*.018F;
        float bob=(1-(float)Math.cos(phase*1.3324F))*speed*.25F;
        if(attackAge>=0&&attackAge<ATTACK_TICKS){
            float load=smooth(attackAge/7),hit=smooth((attackAge-7)/4),recover=smooth((attackAge-13)/13);
            float keep=(1-recover)*smooth(attackAge/3),reach=.75F*load-2.35F*hit;
            torso+=( -.14F*load+.48F*hit)*keep;twist+=(hand==0?1:-1)*(.34F*load-.60F*hit)*keep;
            if(hand==0){armR+=(reach-armR)*keep;elbowR+=(-.90F*load+.72F*hit-elbowR)*keep;armL+=(-.45F-armL)*keep;}
            else{armL+=(reach-armL)*keep;elbowL+=(-.90F*load+.72F*hit-elbowL)*keep;armR+=(-.45F-armR)*keep;}
            legR*=1-.65F*keep;legL*=1-.65F*keep;head-=.12F*load*keep;
        }
        return new Pose(torso,twist,head,armR,armL,elbowR,elbowL,legR,legL,kneeR,kneeL,roll,bob);
    }
    private static float smooth(float t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
}
