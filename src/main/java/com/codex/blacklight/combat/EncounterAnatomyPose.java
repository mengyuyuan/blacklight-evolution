package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterKind;

/** Authored motion for the r6 monster anatomy, sampled on the server move clock.
 * These are anatomical adaptations, not decoded original-game monster clips. */
public final class EncounterAnatomyPose {
    public record Joint(float x,float y,float z) {
        public static final Joint ZERO=new Joint(0,0,0);
    }
    public record Pose(Joint torso,Joint head,Joint rightArm,Joint leftArm,
                       Joint rightForearm,Joint leftForearm,Joint rightHand,Joint leftHand,float jaw) {}
    private record Stroke(float wind,float contact) {}
    public static boolean supports(EncounterKind kind){
        return kind==EncounterKind.LEADER_HUNTER||kind==EncounterKind.JUGGERNAUT||kind==EncounterKind.GOLIATH;
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    private static float envelope(float age,float start,float hold,float end){
        return smooth((age-start)/5)*(1-smooth((age-hold)/Math.max(1,end-hold)));
    }
    private static Stroke stroke(float age,float start,float peak,float contact,float end){
        float strike=smooth((age-peak)/(contact-peak));
        return new Stroke(smooth((age-start)/(peak-start))*(1-strike),
            strike*(1-smooth((age-contact-2)/Math.max(1,end-contact-2))));
    }
    private static Joint j(float x,float y,float z){return new Joint(x,y,z);}
    public static Pose sample(EncounterKind kind,EncounterMove move,float age){
        Joint torso=Joint.ZERO,head=Joint.ZERO,right=Joint.ZERO,left=Joint.ZERO;
        Joint rf=Joint.ZERO,lf=Joint.ZERO,rh=Joint.ZERO,lh=Joint.ZERO;float jaw=0;
        if(!supports(kind))return new Pose(torso,head,right,left,rf,lf,rh,lh,jaw);
        float c=move.contact;
        Stroke s=c>0?stroke(age,0,Math.max(1,c-5),c,move.duration):new Stroke(0,0);
        float w=s.wind,h=s.contact;
        switch(move){
            case CLAW,DOUBLE->{
                Stroke first=move==EncounterMove.DOUBLE?stroke(age,0,8,12,22):s;
                Stroke second=move==EncounterMove.DOUBLE?stroke(age,15,21,25,move.duration):new Stroke(0,0);
                float rw=first.wind,rs=first.contact,lw=second.wind,ls=second.contact;
                torso=j(-.12F*(rs+ls),.24F*(rw-lw)-.3F*(rs-ls),0);
                right=j(.45F*rw+.9F*rs,-.7F*rw+.65F*rs,-.2F*rw);
                left=j(.45F*lw+.9F*ls,.7F*lw-.65F*ls,.2F*lw);
                rf=j(.3F*rw+.1F*rs,0,0);lf=j(.3F*lw+.1F*ls,0,0);
                head=j(.08F*(rs+ls),-.12F*(rw-lw),0);jaw=.16F*(rs+ls);
            }
            case HAMMER,SLAM,ERUPTION->{
                boolean giant=kind==EncounterKind.GOLIATH;
                // Giant fist is on the RIGHT of this model. The other hand balances it.
                torso=j(.12F*w-(giant?.7F:.55F)*h,0,0);
                right=j(2.55F*w+(giant?.45F:.65F)*h,0,-.06F*w);
                left=giant?j(.45F*w+.15F*h,0,.08F*w):j(right.x,0,.06F*w);
                rf=j(.28F*w-.1F*h,0,0);lf=giant?j(.15F*w,0,0):rf;
                rh=j(.12F*h,0,0);lh=giant?Joint.ZERO:rh;
                head=j(-.12F*w+.35F*h,0,0);jaw=.22F*h;
            }
            case CHARGE->{
                float drive=envelope(age,0,32,move.duration);
                torso=j(-.3F*drive,0,0);head=j(.22F*drive,0,0);
                right=j(.8F*drive,-.1F*drive,0);left=j(.65F*drive,.1F*drive,0);
                rf=lf=j(.35F*drive,0,0);jaw=.12F*drive;
            }
            case POUNCE->{
                float flight=envelope(age,5,move.contact,move.duration);
                torso=j(-.26F*flight,0,0);head=j(.2F*flight,0,0);
                right=j(.75F*flight,0,-.08F*flight);left=j(.75F*flight,0,.08F*flight);
                rf=lf=j(.16F*flight,0,0);jaw=.2F*flight;
            }
            case HOWL->{
                float roar=envelope(age,0,move.contact+5,move.duration);
                torso=j(.14F*roar,0,0);head=j(.32F*roar,0,0);
                right=j(.35F*roar,0,-.12F*roar);left=j(.35F*roar,0,.12F*roar);jaw=.4F*roar;
            }
            case GUARD->{
                float guard=envelope(age,0,move.duration-5,move.duration);
                torso=j(-.12F*guard,0,0);head=j(-.1F*guard,0,0);
                right=j(1.05F*guard,.22F*guard,0);left=j(1.05F*guard,-.22F*guard,0);
                rf=lf=j(.7F*guard,0,0);
            }
            default->{}
        }
        return new Pose(torso,head,right,left,rf,lf,rh,lh,jaw);
    }
    private EncounterAnatomyPose(){}
}
