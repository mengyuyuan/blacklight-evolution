import com.codex.blacklight.combat.PairedMotionClock;
import com.google.gson.JsonParser;
import java.nio.file.*;

/** Runs the production one-shot clock against the exported source metadata. */
public final class AerialPhaseProbe {
    private static int checks;
    private static void check(boolean condition){checks++;if(!condition)throw new AssertionError("check "+checks);}
    public static void main(String[] args)throws Exception{
        for(String key:new String[]{"carry_air","air_throw"}){
            var clip=JsonParser.parseString(Files.readString(Path.of(args[0],"art/motion-profile/consume_"+key+"_actor.json"))).getAsJsonObject();
            var meta=clip.getAsJsonObject("blacklight");
            var times=clip.getAsJsonArray("animation").get(0).getAsJsonObject().getAsJsonArray("time");
            float end=times.get(times.size()-1).getAsFloat(),transition=meta.get("transitionSeconds").getAsFloat();
            int duration=meta.get("durationTicks").getAsInt();float previous=0;
            for(int tick=-3;tick<duration*6;tick++){
                float p=PairedMotionClock.progress(tick,.5F,transition,end);
                check(Float.isFinite(p)&&p>=previous&&p<=1);previous=p;
            }
            check(PairedMotionClock.progress(0,0,transition,end)==0);
            check(PairedMotionClock.progress(duration*3,0,transition,end)==1);
            check(PairedMotionClock.progress(duration*3+1,0,transition,end)==1);
            if(key.equals("air_throw")){
                var source=clip.getAsJsonObject("source");int release=source.get("releaseTick").getAsInt();
                check(release>0&&release<duration);
                float frame=source.get("releaseFrame").getAsFloat(),fps=source.get("fps").getAsFloat();
                float at=PairedMotionClock.progress(release,0,transition,end);
                check(Math.abs(at-frame/fps/end)<.0001F);
                check(PairedMotionClock.progress(release-1,0,transition,end)<at&&at<1);
            }
        }
        System.out.println("PASS: "+checks+" production paired-clock checks; airborne hold never loops");
    }
}
