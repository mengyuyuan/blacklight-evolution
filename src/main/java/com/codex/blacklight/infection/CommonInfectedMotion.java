package com.codex.blacklight.infection;

/** Authored civilian joints, in radians. Sampled from absolute clocks, never accumulated per frame. */
public final class CommonInfectedMotion {
    public static final int CONTACT_TICK=11, ATTACK_TICKS=26;
    public static int contactTick(int hand){return hand==2?14:CONTACT_TICK;}
    public static int duration(int hand){return hand==2?34:ATTACK_TICKS;}
    public record Pose(float torso,float twist,float head,float armR,float armL,float elbowR,float elbowL,
                       float legR,float legL,float kneeR,float kneeL,float roll,float bob){}
    private CommonInfectedMotion(){}
    /** Authored block-person pose retimed to the source jump/punch/land states. */
    public static Pose leap(float age,float punchAge,float landAge){
        float load=smooth(age/CommonInfectedLeap.LAUNCH_TICK),airborne=smooth((age-CommonInfectedLeap.LAUNCH_TICK)/2);
        float strike=punchAge<0?0:smooth(punchAge/CommonInfectedLeap.PUNCH_BEGIN);
        float land=landAge<0?0:smooth(landAge/CommonInfectedLeap.LAND_TICKS),keep=1-land;
        return new Pose(.25F+(.22F*load+.18F*airborne)*keep,0,-.1375F-.12F*load*keep,
                -.12F+(.35F*load-1.15F*airborne-.7F*strike)*keep,-.08F+(.35F*load-1.15F*airborne-.7F*strike)*keep,
                -.12F+(-.7F*load+.7F*strike)*keep,-.20F+(-.7F*load+.7F*strike)*keep,
                (-.6F*load+.35F*airborne)*keep,(-.6F*load+.75F*airborne)*keep,
                (.8F*load-.55F*airborne)*keep,(.8F*load-.4F*airborne)*keep,0,.6F*load*(1-airborne)*keep);
    }
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
        if(hand==2&&attackAge>=0&&attackAge<duration(hand)){
            float load=smooth(attackAge/9),hit=smooth((attackAge-9)/5),recover=smooth((attackAge-17)/17);
            float keep=(1-recover)*smooth(attackAge/3);
            torso+=(-.22F*load+.62F*hit)*keep;head-=.20F*load*keep;
            float reach=.45F*load-2.05F*hit,elbow=-1.0F*load+.85F*hit;
            armR+=(reach-armR)*keep;armL+=(reach-armL)*keep;
            elbowR+=(elbow-elbowR)*keep;elbowL+=(elbow-elbowL)*keep;
            twist*=1-keep;legR*=1-.8F*keep;legL*=1-.8F*keep;
        }else if(attackAge>=0&&attackAge<ATTACK_TICKS){
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
