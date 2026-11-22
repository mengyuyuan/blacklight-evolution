package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Performance of the human torso and the spider's centre of mass, shared by render and contact. */
public final class SpiderGesture {
    private SpiderGesture(){}
    static float ease(float t,float a,float b){
        float x=Mth.clamp((t-a)/(b-a),0,1);
        return x*x*(3-2*x);
    }
    static float phrase(float t,float a,float b,float c,float d){
        return ease(t,a,b)*(1-ease(t,c,d));
    }
    private static float breathing(EncounterMob b,float partial){
        double phase=(b.level().getGameTime()%24000+partial)*Math.PI*2/80;
        return (float)(.72*Math.sin(phase)+.28*Math.sin(phase*2-.4));
    }
    public static Matrix4f balance(EncounterMob b,float partial,Matrix4f m){
        if(!b.spider.live())return m;
        var a=b.spider.actions;float t=a.age(partial);
        int lost=b.spider.brokenMask(),foot=b.spiderData().getInt("Foot");
        var support=b.spider.footing.balance(partial);
        float roll=support.roll(),pitch=support.pitch(),sink=support.lower()+.062F*breathing(b,partial);
        float toward=foot<4?1F:-1F;
        float lateral=0, forward=0, twist=0;
        switch(a.move()){
            case HEAVY_STAMP -> {
                float rise=phrase(t,0,12,13,18),land=phrase(t,13,18,20,27),pry=phrase(t,47,56,58,68);
                roll+=toward*(.18F*rise-.21F*land+.12F*pry);
                pitch+=.15F*rise-.21F*land+.14F*pry;sink+=.42F*rise-.9F*land;
                lateral+=toward*(-.7F*rise+.88F*land);forward+=1.05F*land-.55F*pry;
            }
            case FLESH_VOLLEY -> {
                float load=phrase(t,0,12,32,42);
                float kick=phrase(t,15,18,19,24)+phrase(t,24,27,28,33)+phrase(t,33,36,37,42);
                pitch+=.16F*load-.15F*kick;forward+=.48F*load-.72F*kick;sink-=.21F*load;
            }
            case STAB -> {
                float draw=phrase(t,1,10,12,17),thrust=phrase(t,12,18,21,29);
                float pry=phrase(t,22,26,28,36);
                lateral=toward*(-.55F*draw+.64F*thrust);
                forward=-.76F*draw+1.05F*thrust-.25F*pry;
                roll+=toward*(.12F*draw-.13F*thrust);pitch+=.10F*draw-.18F*thrust+.09F*pry;
                sink-=.24F*draw+.42F*thrust;twist=toward*(.13F*draw-.095F*thrust);
            }
            case ORAL_SCISSORS -> {
                float plant=phrase(t,0,14,17,28),cross=phrase(t,16,26,28,42);
                pitch+=.17F*plant-.19F*cross;twist+=.27F*plant-.42F*cross;
                sink-=.36F*plant;forward+=.86F*cross;
            }
            case POD_CAST -> {
                float load=phrase(t,0,13,16,20),recoil=phrase(t,16,19,22,33);
                pitch+=.20F*load-.22F*recoil;forward+=.45F*load-.86F*recoil;
                sink-=.30F*load;roll+=.075F*recoil;
            }
            case SWEEP -> {
                float wind=phrase(t,0,12,13,23),carry=phrase(t,14,27,29,42);
                float stop=phrase(t,27,32,33,43);
                lateral=toward*(-.75F*wind+.93F*carry-.33F*stop);
                twist=-toward*(.44F*wind-.49F*carry+.12F*stop);
                forward=.52F*carry;roll+=toward*(.12F*wind-.16F*carry+.07F*stop);
                sink-=.38F*wind+.32F*carry+.27F*stop;
            }
            case SLAM -> {
                var s=b.spiderData();
                float reach=a.crouch(partial),lift=s.getBoolean("Gripped")?phrase(t,19,25,27,30):0;
                float drop=s.getBoolean("Gripped")?phrase(t,27,30,32,38):0;
                float over=s.getBoolean("Missed")?phrase(t,18,23,26,38):0;
                // Rear feet bear the pelvis; the loaded front suspension sinks and rises twice.
                sink+=-2.45F*reach+1.03F*lift-.29F*drop-.22F*over;
                pitch+=-.46F*reach+.13F*lift-.10F*drop-.07F*over;
                forward=.94F*reach-.48F*lift+.40F*over;
                lateral=.42F*phrase(t,0,9,10,17)-.26F*lift;
                twist=.13F*phrase(t,1,10,12,18)-.14F*lift;
            }
            case PINCER -> {
                float open=phrase(t,0,10,12,19),lunge=phrase(t,10,18,21,28);
                float bite=phrase(t,20,27,29,41),miss=b.spiderData().getBoolean("Missed")?phrase(t,18,24,28,43):0;
                forward=-.95F*open+2.05F*lunge+1.13F*bite+.43F*miss;
                sink+=.45F*open-1.0F*lunge-1.18F*bite-.44F*miss;
                pitch+=.16F*open-.27F*lunge-.21F*bite;
            }
            case SWALLOW -> {
                float arch=phrase(t,0,16,43,52);
                sink+=.55F*arch;pitch+=.23F*arch;
                lateral=toward*.25F*phrase(t,15,24,30,43);
            }
            case MARCH -> {
                var state=b.spiderData();boolean blocked=state.contains("RushStopped");
                float time=blocked?Math.min(t,state.getInt("RushStopped")):t;
                float one=phrase(time,1,11,13,23),two=phrase(time,18,28,30,40);
                float down=phrase(time,14,21,22,28)+phrase(time,31,38,39,48);
                float fade=blocked?1-ease(t-time,0,13):1;
                lateral+=toward*.94F*(two-one)*fade;roll+=toward*.19F*(one-two)*fade;
                sink+=(.42F*(one+two)-.90F*down)*fade;
                pitch+=(.17F*(one+two)-.22F*down)*fade;forward+=.83F*down*fade;
            }
            case SPIKES -> {pitch+=.15F*phrase(t,2,14,17,28)-.09F*phrase(t,15,21,34,48);roll+=.08F*phrase(t,3,17,33,47);}
            case POUNCE -> {
                float coil=phrase(t,0,5,6,12),push=phrase(t,13,17,18,23);
                float dive=phrase(t,18,26,28,34),land=phrase(t,26,30,31,41);
                float rebound=phrase(t,32,36,37,46);
                pitch+=.20F*coil+.24F*push-.29F*dive+.10F*rebound;
                sink-=.68F*coil+.35F*push+.95F*land-.23F*rebound;
                roll+=toward*(.10F*push-.13F*land+.04F*rebound);
            }
            case RECOIL -> {pitch+=.22F*phrase(t,-2,5,9,29);sink-=.16F*phrase(t,0,7,12,28);}
            case CATALYZE -> {roll-=.065F*phrase(t,2,12,27,37);}
            default -> {}
        }
        if(b.spider.feeding()){
            var s=b.spiderData();float elapsed=b.level().getGameTime()-s.getLong("MealStart")+partial;
            float delay=s.getLong("DragAt")-s.getLong("MealStart");
            float bite=s.getLong("SwallowAt")-s.getLong("MealStart");
            float strain=phrase(elapsed,0,delay,Math.max(delay+1,bite-4),bite+2);
            float side=s.getBoolean("PreyLeft")?1:-1;roll+=side*strain*.043F;sink-=strain*.13F;
            if(s.getBoolean("HeavyPrey")){
                float tug=phrase(elapsed,delay-1,delay+4,delay+7,delay+16);
                m.translate(side*.42F*tug,0,-.26F*tug);roll+=side*.08F*tug;pitch+=.13F*tug;
            }
        }
        if(b.spiderData().getBoolean("Struck") && (a.move()==SpiderActions.Move.STAB || a.move()==SpiderActions.Move.SWEEP)){
            float dt=t-b.spiderData().getInt("ContactAge");
            float check=phrase(dt,0,2,3,8);
            forward-=.28F*check;twist+=toward*.09F*check;roll+=toward*.05F*check;
        }
        return m.translate(lateral+support.across(),sink,-forward+support.along()).translate(.55F,4,0)
                .rotateY(twist).rotateZ(roll).rotateX(pitch).translate(-.55F,-4,0);
    }

    public static Quaternionf upper(EncounterMob b,String bone,float partial){
        if(!b.spider.live() || bone.startsWith("fang_") || SpiderAnatomy.leg(bone)>=0 || bone.equals("carapace") || bone.equals("spider"))return null;
        var a=b.spider.actions;var move=a.move();float t=a.age(partial);float x=0,y=0,z=0;
        float breath=breathing(b,partial);
        boolean striking=move==SpiderActions.Move.STAB||move==SpiderActions.Move.SWEEP||move==SpiderActions.Move.PINCER;
        float side=b.spiderData().getInt("Foot")<4?1:-1;
        boolean raised=bone.endsWith("raised");
        if(bone.equals("thorax")){
            x=.026F*breath;
            if(move==SpiderActions.Move.SLAM){
                float lift=b.spiderData().getBoolean("Gripped")?phrase(t,19,26,27,30):0;
                x-=1.89F*a.crouch(partial)-.36F*lift;
                z-=.12F*phrase(t,4,14,20,33);y+=.18F*lift;
            }
            if(move==SpiderActions.Move.SPIKES){x+=.33F*phrase(t,2,13,16,25)-.21F*phrase(t,16,22,31,48);y-=.37F*phrase(t,2,14,28,47);}
            if(striking){y+=side*(.24F*phrase(t,0,10,12,21)-.27F*phrase(t,14,22,26,41));}
            if(move==SpiderActions.Move.CATALYZE){x+=.22F*phrase(t,2,15,26,37);y+=.25F*phrase(t,1,12,27,38);}
            if(move==SpiderActions.Move.SWALLOW)x-=.26F*phrase(t,0,16,40,51);
            if(move==SpiderActions.Move.MARCH){
                y+=side*(.36F*phrase(t,1,10,14,23)-.40F*phrase(t,18,27,31,42));
                x-=.20F*phrase(t,11,20,38,52);
            }
            if(move==SpiderActions.Move.RECOIL)x-=.36F*phrase(t,-2,4,8,30);
        }else if(bone.equals("veiled_head")||bone.equals("crest")||bone.equals("streamer")){
            float lag=bone.equals("veiled_head")?0:bone.equals("crest")?2.4F:5.5F;
            float phase=t-lag;
            x=.025F*breath;y=.032F*(float)Math.sin((b.level().getGameTime()%600+partial-lag)*Math.PI/60);
            if(move==SpiderActions.Move.CATALYZE){
                x+=(bone.equals("streamer")?.77F:.24F)*phrase(phase,-6,10,23,35);
                var host=b.level().getEntity(b.spiderData().getInt("ActionId"));
                if(host!=null){
                    var to=host.getPosition(partial).subtract(b.getPosition(partial));
                    float facing=(float)Math.toDegrees(Math.atan2(-to.x,to.z));
                    float steer=(float)Math.toRadians(Mth.clamp(Mth.wrapDegrees(facing-b.skinYaw(partial)),-68,68));
                    y-=steer*(bone.equals("veiled_head")?.55F:.24F)*phrase(phase,-4,9,24,36);
                }
                y-=.16F*phrase(phase,22,26,29,36);
            }
            if(striking)y+=side*(.29F*phrase(phase,-3,6,10,18)-.20F*phrase(phase,12,20,24,39));
            if(move==SpiderActions.Move.SLAM)x-=.22F*phrase(phase,1,10,16,30);
            if(move==SpiderActions.Move.SWALLOW)x+=.36F*phrase(phase,-2,11,43,51);
        }else if(bone.startsWith("arm_")||bone.startsWith("elbow_")||bone.startsWith("wrist_")){
            float lag=bone.startsWith("arm_")?0:bone.startsWith("elbow_")?2:4;
            float phase=t-lag;
            x=.026F*breath*(raised?1:-1);
            if(move==SpiderActions.Move.SPIKES){
                if(raised){x+=.81F*phrase(phase,0,11,14,24)-.56F*phrase(phase,12,20,31,44);y+=.74F*phrase(phase,3,18,26,44);z+=.37F*phrase(phase,4,15,19,37);}
                else{x-=.52F*phrase(phase,0,10,32,46);z+=.29F*phrase(phase,0,12,33,46);}
            }
            if(move==SpiderActions.Move.SLAM && raised){
                x-=.76F*phrase(phase,0,12,15,25);z+=.60F*phrase(phase,0,13,16,28);
                x+=.44F*phrase(phase,17,25,27,37);y-=.42F*phrase(phase,20,29,32,40);
            }
            if(striking)z+=(raised?-.24F:.19F)*side*phrase(phase,0,10,21,37);
            if(move==SpiderActions.Move.CATALYZE)x+=(raised?.24F:-.17F)*phrase(phase,1,13,24,36);
            if(move==SpiderActions.Move.SWALLOW)z+=(raised?.44F:-.38F)*phrase(phase,0,15,39,53);
            if(move==SpiderActions.Move.REST && Integer.bitCount(b.spider.brokenMask())>=6){
                float clock=(float)((b.level().getGameTime()%36+partial)*Math.PI/18)+(raised?1.45F:0);
                x+=.44F*(float)Math.sin(clock);z+=(raised?1:-1)*.18F*(float)Math.cos(clock);
            }
        }else return null;
        boolean torso=bone.equals("thorax"),arm=bone.startsWith("arm_")||bone.startsWith("elbow_")||bone.startsWith("wrist_");
        if(move==SpiderActions.Move.ORAL_SCISSORS){
            float wind=phrase(t,0,12,16,25),cross=phrase(t,16,26,30,43);
            if(torso){x-=.40F*wind;y+=.42F*wind-.64F*cross;}
            else if(arm){z+=(raised?-.57F:.44F)*wind;x+=(raised?-.52F:.36F)*cross;}
            else y+=.29F*wind-.37F*cross;
        }
        if(move==SpiderActions.Move.POD_CAST){
            float inhale=phrase(t,1,13,16,21),kick=phrase(t,16,19,21,33);
            if(torso){x+=.46F*inhale-.72F*kick;}
            else if(arm){z+=(raised?1:-1)*(.48F*inhale-.32F*kick);x-=.38F*kick;}
            else x+=.30F*inhale-.48F*kick;
        }
        return new Quaternionf().rotateZYX(z,y,x);
    }
}
