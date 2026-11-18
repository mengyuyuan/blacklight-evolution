package com.codex.blacklight.military.aircraft;

/** P1 Helicopters patch controls, with explicitly separate Minecraft flight inputs.
 * Original recordings are not distributed. See helicopter-original-audio-source.json. */
public final class HelicopterAudioState {
    public record Profile(float rotorVolume,float engineVolume,float jetVolume,
                          float rotorLow,float rotorHigh,float engineLow,float engineHigh,float crossfadeMs) {}
    // Patch EnvelopeData Y is converted by 0x1086ce30 from [0,1] to [.5,2].
    public static final Profile BLACKHAWK=new Profile(.7738090158F,.6607149839F,.4464290142F,
            .9191180021F,1.1838230193F,.7727269903F,1.0965905190F,2500);
    public static final Profile APACHE=new Profile(.75F,.2976189852F,.25F,
            .9191180021F,1.1397064924F,.8068175092F,.9943174869F,5000);
    public static final float SMOOTH_UP_MS=3500,SMOOTH_DOWN_MS=7500;
    public static final float STARTUP_SECONDS=13.722993F,WINDDOWN_SECONDS=12.400182F;
    public enum Cue { NONE,STARTUP,WINDDOWN }
    public record Mix(float rotorVolume,float engineVolume,float jetVolume,
                      float rotorPitch,float enginePitch,Cue cue) {}
    public record Controls(float velocity,float throttle) {}
    private final Profile profile;
    private boolean initialized,running;
    private float gain,rotorPitch=.5F,enginePitch=.5F;

    public HelicopterAudioState(Profile profile){this.profile=profile;}

    /** Called once per unpaused client tick, not once for every sound voice. */
    public Mix tick(boolean powered,float rotor,float velocity,float throttle,float milliseconds){
        float dt=Float.isFinite(milliseconds)?Math.max(0,milliseconds):0;
        Cue cue=Cue.NONE;
        if(!initialized){
            initialized=true;running=powered;
            // Entering audible range of an already running aircraft must not restart its ignition.
            if(powered&&finiteUnit(rotor)<.99F)cue=Cue.STARTUP;
        }else if(powered!=running){running=powered;cue=powered?Cue.STARTUP:Cue.WINDDOWN;}
        // P1 supplies xfade_ms to the voice fades. Linear gain is the MC mixer
        // adapter; the source voice mixer's exact envelope has not been decoded.
        gain=approach(gain,powered?1:0,dt,profile.crossfadeMs);
        rotorPitch=smoothPitch(rotorPitch,lerp(profile.rotorLow,profile.rotorHigh,finiteUnit(velocity)),dt);
        enginePitch=smoothPitch(enginePitch,lerp(profile.engineLow,profile.engineHigh,finiteUnit(throttle)),dt);
        return new Mix(gain*profile.rotorVolume,gain*profile.engineVolume,gain*profile.jetVolume,rotorPitch,enginePitch,cue);
    }

    public boolean silent(){return !running&&gain<=0;}

    /** Native 0x104a5f60 limits pitch change by dt / smooth_ms, not exponential interpolation. */
    public static float smoothPitch(float current,float target,float milliseconds){
        return approach(current,target,milliseconds,target>current?SMOOTH_UP_MS:SMOOTH_DOWN_MS);
    }
    private static float approach(float current,float target,float dt,float duration){
        if(!Float.isFinite(dt)||dt<=0)return current;
        float step=dt/duration;
        return current<target?Math.min(target,current+step):Math.max(target,current-step);
    }
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    private static float finiteUnit(float value){return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;}

    /** MC adapter: use completed server movement and applied velocity change.
     * This port has no turbine solver, so throttle is actual speed/acceleration/
     * upward-motion load under the synchronized rotor supply, never target distance.
     * Quantization bounds network churn; source patch curves remain unquantized. */
    public static Controls controls(boolean powered,float rotor,double distance,double acceleration,double rise){
        if(!powered||!Double.isFinite(distance)||!Double.isFinite(acceleration)||!Double.isFinite(rise))return new Controls(0,0);
        float speed=finiteUnit((float)(Math.abs(distance)/HelicopterFlight.MAX_SPEED));
        float load=finiteUnit((float)Math.max(speed,Math.max(Math.abs(acceleration)/HelicopterFlight.ACCELERATION,Math.max(0,rise)/.35)));
        return new Controls(Math.round(speed*255)/255F,Math.round(load*finiteUnit(rotor)*255)/255F);
    }
}
