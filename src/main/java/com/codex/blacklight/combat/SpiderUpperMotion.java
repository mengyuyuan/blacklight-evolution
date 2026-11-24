package com.codex.blacklight.combat;

import org.joml.Quaternionf;

/** Small rotations in the bind frame of each joint, timed to the actual strike and release ticks. */
final class SpiderUpperMotion {
    private SpiderUpperMotion() {}

    private static float hold(float time, float start, float peak, float release, float end) {
        return SpiderGesture.phrase(time, start, peak, release, end);
    }

    // The last two shells of the fan leave four ticks apart; the latter is the heavier kick.
    static float volleyKick(float t) {
        return hold(t,13,15,16,20) + .84F*hold(t,20,22,23,28) + hold(t,27,29,30,36)
            + .78F*hold(t,44,46,47,50) + 1.14F*hold(t,48,50,51,57);
    }

    static Quaternionf apply(SpiderActions.Move move, String bone, float age, float side, Quaternionf base) {
        boolean chest=bone.equals("thorax"), head=bone.equals("veiled_head");
        boolean crest=bone.equals("crest"), veil=bone.equals("streamer"), crown=crest || veil;
        boolean shoulder=bone.startsWith("arm_"), elbow=bone.startsWith("elbow_"), wrist=bone.startsWith("wrist_");
        boolean arm=shoulder || elbow || wrist, high=bone.endsWith("raised");
        float delay=veil?4.2F:crest?2.35F:wrist?2.7F:elbow?1.35F:head?.7F:0;
        float t=age-delay;
        float x=0,y=0,z=0;
        switch(move) {
            case HEAVY_STAMP, STAB, MARCH -> {
                boolean heavy=move==SpiderActions.Move.HEAVY_STAMP;
                boolean march=move==SpiderActions.Move.MARCH;
                float gather=hold(t,0,10,13,18);
                float catchLoad=hold(t,13,heavy?18:march?21:18,heavy?22:23,31);
                if(march){
                    gather-=hold(t,18,28,30,38);
                    catchLoad-=hold(t,30,38,40,51);
                }
                float tear=heavy?hold(t,43,54,56,68):0;
                float turn=.32F*gather-.40F*catchLoad+.26F*tear;
                if(chest){z=side*turn*.58F;x=.075F*gather-.13F*catchLoad;}
                if(head || crown){y=-side*turn*(head?.56F:.41F);x=(crown?.65F:.32F)*(.29F*catchLoad-.23F*tear);}
                if(arm){
                    // Ballast arm opens while the other folds against the chest.
                    boolean ballast=high==(side>0);
                    if(shoulder){z=(ballast?.67F:-.37F)*turn;y=(ballast?.24F:-.62F)*turn;}
                    else if(elbow)x=(ballast?-.67F:.85F)*turn;
                    else y=(ballast?.60F:-.54F)*turn;
                }
            }
            case FLESH_VOLLEY, MOUTH_BEAM -> {
                boolean beam=move==SpiderActions.Move.MOUTH_BEAM;
                float intake=beam?hold(t,1,25,28,35):hold(t,0,11,14,21)+.90F*hold(t,32,42,44,48);
                float fire=beam?hold(t,30,34,73,85):volleyKick(t);
                float vent=beam?hold(t,76,80,82,92):hold(t,50,53,54,59);
                if(chest){x=.21F*intake-.31F*fire+.11F*vent;}
                if(head){x=-.13F*intake+.21F*fire-.18F*vent;}
                if(crown){x=-.24F*intake+.34F*fire-.27F*vent;z=(crest?.06F:-.10F)*vent;}
                if(shoulder){z=(high?1:-1)*(.31F*intake+.15F*fire-.21F*vent);y=(high?.29F:-.19F)*intake;}
                if(elbow)x=(high?-.37F:.31F)*intake+(high?.23F:-.16F)*fire;
                if(wrist)y=(high?.32F:-.42F)*intake-(high?.22F:-.15F)*fire;
            }
            case POUNCE, RECOIL -> {
                boolean leap=move==SpiderActions.Move.POUNCE;
                float fold=leap?hold(t,2,10,15,21):0;
                float catchLoad=leap?hold(t,21,29,32,42):hold(t,0,4,7,16);
                float regain=leap?hold(t,32,37,39,47):hold(t,9,16,19,30);
                if(chest)x=-.16F*fold+.23F*catchLoad-.11F*regain;
                if(head || crown)x=.23F*fold-.28F*catchLoad+.18F*regain;
                if(shoulder){z=(high?1:-1)*(-.28F*fold+.46F*catchLoad-.16F*regain);x=-.20F*fold;}
                if(elbow)x=(high?.42F:-.31F)*fold+(high?-.39F:.45F)*catchLoad;
                if(wrist)z=(high?-.47F:.29F)*catchLoad;
            }
            case SPIKES -> {
                // Palm turns down first; the fingers lead along the fissure as each row emerges.
                float lead=hold(t,2,13,15,21),press=hold(t,14,18,28,38);
                float trace=hold(t,18,32,34,41);
                if(head || crown)y=.26F*lead-.37F*trace;
                if(high && wrist){x=.48F*lead-.67F*press;y=.39F*trace;}
                if(high && elbow)y=-.24F*lead+.34F*trace;
                if(!high && shoulder)z=-.22F*press;
            }
            case SWEEP, ORAL_SCISSORS -> {
                float brake=hold(t,23,29,31,41);
                if(head || crown)y=side*(head?.29F:.40F)*brake;
                if(wrist)y=(high?.44F:-.32F)*side*brake;
            }
            case PINCER, SWALLOW, SLAM -> {
                float catchAt=move==SpiderActions.Move.SWALLOW?24:18;
                float holdPrey=hold(t,catchAt-5,catchAt,catchAt+4,catchAt+15);
                if(head || crown)x=(head?-.22F:.27F)*holdPrey;
                if(wrist && high){y=.39F*holdPrey;z=-.20F*holdPrey;}
            }
            case CATALYZE, POD_CAST -> {
                boolean needle=move==SpiderActions.Move.CATALYZE;
                float release=needle?hold(t,23,28,29,37):hold(t,13,17,19,32);
                if(crown){x=(veil?-.39F:-.22F)*release;y=.22F*release;}
                if(elbow && !high)x=.33F*release;
                if(wrist && high)z=-.28F*release;
            }
            default -> {return base;}
        }
        return base.rotateZYX(z,y,x);
    }
}
