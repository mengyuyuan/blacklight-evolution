package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Articulates the four exported digits and their wrist and elbow on both sides of the paired rig. */
final class GoddessGesture {
    private static final int NEAR=5, FAR=19;

    static float beat(float t,float begin,float peak,float hold,float end) {
        return GoddessRush.ease(t,begin,peak)*(1-GoddessRush.ease(t,hold,end));
    }

    private static void chain(Matrix4f[] bones,int from,int last,Matrix4f turn) {
        for(int j=from;j<=last;j++)bones[j].mulLocal(turn);
    }

    private static void turn(Matrix4f[] bones,int from,int last,float pitch,float yaw,float roll) {
        if(Math.abs(pitch)+Math.abs(yaw)+Math.abs(roll)<.0001F)return;
        var at=bones[from].getTranslation(new Vector3f());
        var m=new Matrix4f().translate(at).rotateYXZ(yaw,pitch,roll).translate(-at.x,-at.y,-at.z);
        chain(bones,from,last,m);
    }

    /** Bend through successive knuckles about the posed palm's transverse axis.
     * Moving the descendants with the parent preserves the lengths all the way to the tips. */
    static void digits(Matrix4f[] bones,int arm,float index,float middle,float ring,float thumb) {
        if(Math.abs(index)+Math.abs(middle)+Math.abs(ring)+Math.abs(thumb)<.001F)return;
        var axis=bones[arm+9].getTranslation(new Vector3f()).sub(bones[arm+3].getTranslation(new Vector3f()));
        if(axis.lengthSquared()<1e-6F)return;
        axis.normalize();
        var at=new Vector3f();
        var bend=new Matrix4f();
        for(int digit=0;digit<4;digit++){
            float amount=switch(digit){case 0->index;case 1->middle;case 2->ring;default->thumb;};
            if(Math.abs(amount)<.001F)continue;
            int base=arm+3+digit*3,end=Math.min(arm+13,base+2);
            for(int j=base;j<=end;j++){
                bones[j].getTranslation(at);
                float angle=amount*(j==base?.48F:.64F)*(digit==3?-.76F:1);
                bend.translation(at).rotate(angle,axis).translate(-at.x,-at.y,-at.z);
                chain(bones,j,end,bend);
            }
        }
    }

    private static void arm(Matrix4f[] b,int root,float lift,float sweep,float open,
                            float elbow,float twist,float wrist) {
        int sign=root==NEAR?1:-1,end=root+13;
        turn(b,root,end,lift,sign*sweep,sign*open);
        turn(b,root+1,end,elbow,sign*twist,sign*twist*.25F);
        turn(b,root+2,end,wrist,sign*(-.34F*twist),sign*(.27F*elbow));
    }

    static void cast(EncounterMob boss,Matrix4f[] bones,float t,float wind,float hit,float hold) {
        var move=boss.goddess.attacks.move();
        int mask=boss.goddess.removed();
        for(int root=NEAR;root<=FAR;root+=FAR-NEAR){
            if(!GoddessRig.visible(boss,root))continue;
            boolean near=root==NEAR,oneArm=(mask&3)!=0;
            float lead=near||oneArm?1:.28F;
            switch(move){
                case VACUOLE -> {
                    // The gaze fixes the centre; one wrist measures its circumference, then pinches closed.
                    arm(bones,root,-.63F*wind*lead+.16F*hit, .48F*hold*lead,.46F*wind*lead,
                            -.85F*hold*lead,.44F*hold*lead,.57F*hit-.25F*wind);
                    digits(bones,root,.80F*hold*lead,.13F*hold,.27F*hold,.98F*hold*lead);
                }
                case NEEDLE_RAIN -> {
                    float release=0;
                    for(int row=0;row<5;row++){
                        if(!oneArm && (row%2==0)!=near)continue;
                        float fire=12+6*row;
                        release+=beat(t,fire-4,fire,fire+.4F,fire+4);
                    }
                    arm(bones,root,-.48F*wind-.16F*release,.62F*release,.6F*wind,
                            -.61F*wind+.70F*release,.23F*release,-.67F*release);
                    digits(bones,root,-.29F*release,-.20F*release,.73F*release,.54F*release);
                }
                case WALL_SPEARS -> {
                    float rear=near?1:.72F;
                    arm(bones,root,-.84F*wind+.34F*hit,-.57F*wind+.76F*hold,.87F*wind-.53F*hold,
                            -.74F*wind*rear+.65F*hit,-.44F*wind+.55F*hold,.66F*hit-.36F*hold);
                    digits(bones,root,.09F*hold,.39F*hold,.74F*hold,.89F*hold);
                }
                case BLOOD_SURF, SEEKING_ORBS -> {
                    boolean wave=move==GoddessAttacks.Move.BLOOD_SURF;
                    float handover=GoddessRush.ease(t,wave?18:15,wave?27:22);
                    float drive=oneArm?1:near?1-handover:handover;
                    float recoil=wave?.85F:.43F;
                    arm(bones,root,-(.26F+.85F*drive)*wind+recoil*hit*drive,
                            drive*(.46F*wind-.92F*hit),(.22F+.59F*drive)*wind-.40F*hit*drive,
                            drive*(-.82F*wind+.55F*hit),drive*(.56F*wind-.43F*hit),drive*(-.5F*wind+.76F*hit));
                    float curl=drive*(wind-.34F*hit);
                    digits(bones,root,curl*.46F,curl*.72F,curl*.94F,curl*.57F);
                }
                case TENDRIL_CRUSH -> {
                    arm(bones,root,-.76F*wind-.31F*hold,.58F*wind-.88F*hold,.90F*wind-.77F*hold,
                            -.34F*wind-.70F*hold,.80F*hold,-.52F*hold+.34F*hit);
                    digits(bones,root,hold,beat(t,21,30,35,48),beat(t,19,28,35,48),beat(t,25,32,36,48));
                }
                case PACK_POUNCE -> {
                    arm(bones,root,-.91F*wind*lead-.67F*hit*lead,.56F*wind-.9F*hit*lead,
                            (.10F+lead*.55F)*wind,-.83F*wind*lead+.57F*hit*lead,.42F*wind,-.45F*hit);
                    digits(bones,root,.66F*wind-.39F*hit,.33F*wind+.78F*hit,
                            .55F*wind+.9F*hit,.42F*hit);
                }
                default -> {}
            }
        }
    }

    static void terrain(EncounterMob boss,Matrix4f[] b,float t,float wind,float hit) {
        for(int root=NEAR;root<=FAR;root+=FAR-NEAR){
            if(!GoddessRig.visible(boss,root))continue;
            boolean lead=root==NEAR || (boss.goddess.removed()&1)!=0;
            switch(boss.goddess.attacks.move()){
                case SONIC_CRY -> {
                    // Pull away from the ribs on the exhale. Keep both hands below the lips.
                    arm(b,root,-.55F*wind+.25F*hit,.43F*wind,-.44F*wind+.69F*hit,
                            -.72F*wind+.41F*hit,lead?.29F*hit:.14F*wind,.38F*hit);
                    digits(b,root,.37F*wind-.15F*hit,.48F*wind,.64F*wind,.48F*wind);
                }
                case SPIKE_FIELD -> {
                    float stamp=0;
                    // Weight the first row of each four-row bank. Terrain still erupts every three ticks.
                    for(int row=0;row<GoddessTerrainCast.ROWS;row+=4){
                        float at=GoddessTerrainCast.eruption(row);
                        stamp+=beat(t,at-4,at,at+1,at+4);
                    }
                    float drive=lead?1:.25F;
                    arm(b,root,-.82F*wind+.53F*hit*drive,.39F*wind,.52F*wind-.38F*hit,
                            -.67F*wind+.68F*stamp*drive,.42F*hit*drive,.61F*stamp*drive);
                    digits(b,root,-.36F*stamp*drive,.16F*hit,.66F*hit,.78F*hit);
                }
                case INFECTION_WAVE -> {
                    float opening=lead?hit:beat(t,24,29,37,63);
                    arm(b,root,-.48F*wind+.36F*opening,-.83F*wind+.87F*opening,.86F*wind-1.11F*opening,
                            -.85F*wind+.60F*opening,-.53F*wind+.48F*opening,.59F*opening);
                    digits(b,root,.78F*wind-.22F*opening,.83F*wind,.97F*wind,.70F*wind);
                }
                default -> {}
            }
        }
    }

    static void secondary(EncounterMob boss,Matrix4f[] b,float partial,float frame) {
        var fight=boss.goddess;
        if(frame>0){
            grounded(boss,b,partial);
            return;
        }
        var attacks=fight.attacks;
        var move=attacks.move();
        float t=attacks.age(partial);
        int first=(fight.removed()&1)==0?NEAR:FAR,other=first==NEAR?FAR:NEAR;
        switch(move){
            case REST, BREATHER -> {
                float engage=GoddessRush.ease(t,0,9);
                float head=Mth.rotLerp(partial,boss.yHeadRotO,boss.yHeadRot);
                float gaze=Mth.clamp(Mth.wrapDegrees(head-fight.facing(partial)),-32,32)*.011F*engage;
                float breath=(float)Math.sin((boss.tickCount+partial)*.067)*engage;
                var neck=b[0].getTranslation(new Vector3f());
                var orient=new Matrix4f().translate(neck).rotateY(-gaze).translate(-neck.x,-neck.y,-neck.z);
                b[0].mulLocal(orient);b[33].mulLocal(orient);b[34].mulLocal(orient);
                turn(b,3,4,.035F*breath,gaze*.27F,0);
                turn(b,34,34,-.044F*breath,gaze*.41F,0);
                if(move==GoddessAttacks.Move.BREATHER && GoddessRig.visible(boss,first)){
                    float lower=beat(t,0,13,26,40);
                    arm(b,first,.30F*lower,0,-.23F*lower,-.44F*lower,.17F*lower,.26F*lower);
                    digits(b,first,-.21F*lower,-.14F*lower,.15F*lower,.11F*lower);
                }
            }
            case CALL, STRONG_CALL -> {
                boolean strong=move==GoddessAttacks.Move.STRONG_CALL;
                for(int root=NEAR;root<=FAR;root+=FAR-NEAR){
                    if(!GoddessRig.visible(boss,root))continue;
                    float beckon=0;
                    for(int i=0;i<(strong?6:4);i++){
                        float signal=GoddessAttacks.CALL_FIRST+i*GoddessAttacks.CALL_INTERVAL;
                        boolean leading=strong && (fight.removed()&3)==0 ? (root==NEAR)==(i<3) : root==first;
                        float weight=leading?1:.25F;
                        beckon+=weight*beat(t,signal-5,signal,signal+1,signal+6);
                    }
                    turn(b,root+1,root+13,-.64F*beckon,(root==NEAR?1:-1)*.52F*beckon,0);
                    digits(b,root,.66F*beckon,.78F*beckon,.36F*beckon,.12F*beckon);
                }
            }
            case HAND -> {
                if(!GoddessRig.visible(boss,first) || !attacks.hand.visible())return;
                float clock=attacks.hand.melee()?attacks.hand.controlTime(partial):attacks.hand.gestureAge(partial);
                if(attacks.hand.melee()){
                    if(GoddessRig.visible(boss,other)){
                        float brace=GoddessPalmCombo.lift(clock)*(1-GoddessPalmCombo.recover(clock));
                        arm(b,other,-.34F*brace,.31F*brace,.46F*brace,-.36F*brace,0,.22F*brace);
                    }
                }else{
                    int pattern=attacks.hand.playingPattern();
                    if(pattern<0)return;
                    float close=(float)GoddessHandPose.bend(pattern,2,clock);
                    float fire=switch(pattern){
                        case 0->beat(clock,10,13,14,19)+beat(clock,36,39,40,46);
                        case 1->beat(clock,13,18,38,49);
                        case 2->beat(clock,32,38,39,50);
                        default->beat(clock,30,40,42,50);
                    };
                    arm(b,first,-.43F*close+.30F*fire,.28F*close-.45F*fire,.37F*close,
                            -.64F*close+.52F*fire,.4F*fire,.52F*fire-.26F*close);
                    digits(b,first,(float)GoddessHandPose.bend(pattern,1,clock)*.76F,close*.76F,
                            (float)GoddessHandPose.bend(pattern,3,clock)*.76F,close*.42F);
                }
            }
            case RUSH -> {
                int free=attacks.rush.arm()==1?FAR:NEAR;
                float gather=attacks.rush.backhand()?beat(t,1,10,16,30):beat(t,1,23,35,60);
                if(GoddessRig.visible(boss,free))
                    arm(b,free,.47F*gather,.30F*gather,.73F*gather,-.39F*gather,.22F*gather,.14F*gather);
                turn(b,4,4,.24F*gather,0,.12F*gather);
            }
            case STRUGGLE -> {
                var q=attacks.struggle;
                float tension=q.closure(partial)*GoddessRush.ease(t,0,12);
                int free=q.hair()?first:other;
                if(GoddessRig.visible(boss,free)){
                    arm(b,free,-.52F*tension,.38F*tension,.49F*tension,-.59F*tension,.31F*tension,.19F*tension);
                    digits(b,free,.85F*tension,.74F*tension,.58F*tension,.71F*tension);
                }
            }
            case TENDRIL_ERUPTION -> {
                float charge=beat(t,2,23,26,33),up=beat(t,27,32,43,66);
                for(int root=NEAR;root<=FAR;root+=FAR-NEAR){
                    if(!GoddessRig.visible(boss,root))continue;
                    turn(b,root+1,root+13,-.69F*charge+.85F*up,(root==NEAR?1:-1)*.40F*charge,0);
                    digits(b,root,.84F*charge-.28F*up,.73F*charge,.64F*charge,.49F*charge);
                }
            }
            default -> {}
        }
    }

    private static void grounded(EncounterMob boss,Matrix4f[] b,float partial) {
        var fight=boss.goddess;
        float t=fight.age(partial);
        // Protect the authored contact chain and head. Only the opposite, weight-bearing
        // fingers strain before impact, on the ground and before the first extraction pull.
        int support=fight.part()==GoddessVitals.RIGHT_ARM?NEAR:FAR;
        if(!GoddessRig.visible(boss,support))return;
        float pressure=switch(fight.action()){
            case STAGGER->beat(fight.lowered(partial),0,.45F,.65F,1)*.7F;
            case FALLING->beat(t,1,17,26,38);
            case DOWN,GRIPPED->.13F*(1-(float)Math.cos((boss.tickCount+partial)*.16))*(fight.action()==GoddessEncounter.Action.DOWN?GoddessRush.ease(t,0,7):1);
            case EXTRACTING->.13F*(1-(float)Math.cos((boss.tickCount+partial)*.16))*(1-GoddessRush.ease(t,0,10));
            case RISING->beat(t,9,15,23,44)*.57F;
            default->0;
        };
        digits(b,support,pressure*.41F,pressure*.70F,pressure*.78F,pressure*.55F);
    }

    private GoddessGesture(){}
}

