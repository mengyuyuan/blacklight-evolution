package com.codex.blacklight.animation;

/** Velocity-driven presentation. Speed is blocks/tick; time is seconds. */
public final class WingFlightPresentation {
    public record Frame(float flow,float thrust,float brake,float speed) {
        public float streamline(){return com.codex.blacklight.movement.WingFlightTuning.streamline(speed);}
        public float mach(){return com.codex.blacklight.movement.WingFlightTuning.mach(speed);}
        public static final Frame STILL=new Frame(0,0,0,0);
        public static Frame mix(Frame a,Frame b,float t){
            t=com.codex.blacklight.forge.WingMath.clamp(t,0,1);
            return new Frame(lerp(a.flow,b.flow,t),lerp(a.thrust,b.thrust,t),lerp(a.brake,b.brake,t),lerp(a.speed,b.speed,t));
        }
    }
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    public static float flow(float speed){return SovereignFlightMotion.smooth((speed-.22F)/1.03F);}
    public static final class Controller {
        private double time=Double.NaN;
        private float velocity;
        private Frame frame=Frame.STILL;
        public Frame update(double now,float speed,boolean flying,boolean boost){
            if(!Double.isFinite(now))return frame;
            speed=Float.isFinite(speed)?com.codex.blacklight.forge.WingMath.clamp(speed,0,24F):0;
            if(Double.isNaN(time)){time=now;velocity=speed;return frame;}
            if(now<=time)return frame;
            double elapsed=now-time;time=now;
            // A teleport/reconnect is not an acceleration impulse.
            if(elapsed>.5){velocity=speed;frame=Frame.STILL;return frame;}
            float dt=(float)elapsed,acceleration=(speed-velocity)/dt;velocity=speed;
            float thrust=flying?com.codex.blacklight.forge.WingMath.clamp(Math.max(0,acceleration)*.30F+(boost?.35F:0),0,1):0;
            float brake=flying?com.codex.blacklight.forge.WingMath.clamp(-acceleration*.35F,0,1):0;
            float a=1-(float)Math.exp(-dt*8);
            frame=Frame.mix(frame,new Frame(flying?flow(speed):0,thrust,brake,flying?speed:0),a);
            return frame;
        }
    }
    private WingFlightPresentation(){}
}
