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
        float favor=(Integer.bitCount(lost&15)-Integer.bitCount(lost>>>4))*.016F;
        float roll=favor, pitch=0, sink=-.043F*Integer.bitCount(lost)+.062F*breathing(b,partial);
        float toward=foot<4?1F:-1F;
        switch(a.move()){
            case STAB,SWEEP,PINCER,SWALLOW -> {
                float hit=a.move()==SpiderActions.Move.SWEEP?22:a.move()==SpiderActions.Move.SWALLOW?24:18;
                float load=phrase(t,0,hit*.55F,hit*.65F,hit);
                float drive=phrase(t,hit*.62F,hit+1,hit+5,hit+19);
                roll+=toward*(.076F*load-.057F*drive);
                pitch+=.09F*load-.12F*drive;sink-=.19F*load+.24F*drive;
            }
            case SPIKES -> {pitch+=.15F*phrase(t,2,14,17,28)-.09F*phrase(t,15,21,34,48);roll+=.08F*phrase(t,3,17,33,47);}
            case POUNCE -> {pitch+=.13F*phrase(t,0,9,15,21)-.14F*phrase(t,18,25,31,43);sink-=.44F*phrase(t,24,30,32,45);}
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
        return m.translate(0,sink,0).translate(.55F,4,0).rotateZ(roll).rotateX(pitch).translate(-.55F,-4,0);
    }

    public static Quaternionf upper(EncounterMob b,String bone,float partial){
        if(!b.spider.live() || bone.startsWith("fang_") || SpiderAnatomy.leg(bone)>=0 || bone.equals("carapace") || bone.equals("spider"))return null;
        var a=b.spider.actions;var move=a.move();float t=a.age(partial);float x=0,y=0,z=0;
        float breath=breathing(b,partial);
        boolean striking=move==SpiderActions.Move.STAB||move==SpiderActions.Move.SWEEP||move==SpiderActions.Move.PINCER;
        float side=b.spiderData().getInt("Foot")<4?1:-1;
        boolean raised=bone.endsWith("raised");
        if(bone.equals("thorax")){
            x=move==SpiderActions.Move.SLAM?-1.52F*a.crouch(partial):.026F*breath;
            if(move==SpiderActions.Move.SPIKES){x+=.33F*phrase(t,2,13,16,25)-.21F*phrase(t,16,22,31,48);y-=.37F*phrase(t,2,14,28,47);}
            if(striking){y+=side*(.24F*phrase(t,0,10,12,21)-.27F*phrase(t,14,22,26,41));}
            if(move==SpiderActions.Move.CATALYZE){x+=.22F*phrase(t,2,15,26,37);y+=.25F*phrase(t,1,12,27,38);}
            if(move==SpiderActions.Move.SWALLOW)x+=.32F*phrase(t,0,16,40,51);
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
            if(move==SpiderActions.Move.SLAM && raised){x-=.74F*phrase(phase,-2,11,28,37);z+=.46F*phrase(phase,-2,12,28,37);}
            if(striking)z+=(raised?-.24F:.19F)*side*phrase(phase,0,10,21,37);
            if(move==SpiderActions.Move.CATALYZE)x+=(raised?.24F:-.17F)*phrase(phase,1,13,24,36);
            if(move==SpiderActions.Move.SWALLOW)z+=(raised?.44F:-.38F)*phrase(phase,0,15,39,53);
        }else return null;
        return new Quaternionf().rotateZYX(z,y,x);
    }
}
