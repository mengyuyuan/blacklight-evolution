package com.codex.blacklight.combat;

/** Source Patch/SoundTrack values with explicit MC voice-lifecycle boundaries. */
public final class GreeneProjectileAudioState {
    public record Profile(String cue,float minimumDistance,float maximumDistance,float sourceVolume,float minimumPitch,float maximumPitch,float fadeInSeconds,float fadeOutSeconds) {}
    public static final Profile BIOMASS=new Profile("biomass",10,55,1,.9F,1.1F,0,0);
    public static final Profile FIRE=new Profile("fire",2,25,1.404762F,.8F,1.2F,.5F,1);
    // Client mix/voice limits are MC performance and loudness adapters.
    public static final float MIX_GAIN=.28F;
    public static final int MAX_VOICES=12,RETRY_TICKS=20;
    public enum Action { START,KEEP,STOP,IDLE }
    private final Profile profile;
    private boolean requested,releasing,dead;
    private int missingTicks;
    private double age,releaseAge;
    private float releaseGain;
    public GreeneProjectileAudioState(Profile profile){this.profile=profile;}
    public static Profile of(GreeneMissileFlight.Ammo ammo){return switch(ammo){case DEATH_FLOWER,INSECT_SWARM->BIOMASS;default->FIRE;};}
    public Profile profile(){return profile;}
    public boolean dead(){return dead;}
    public float envelope(){
        if(dead)return 0;
        if(releasing)return profile.fadeOutSeconds<=0?0:releaseGain*(float)Math.max(0,1-releaseAge/profile.fadeOutSeconds);
        return profile.fadeInSeconds<=0?1:(float)Math.min(1,age/profile.fadeInSeconds);
    }
    /** Call once per client post-tick, even when paused. No heartbeat retrigger.
     * Identity/world/range invalidation is immediate; final polls release once.
     * Missing backend voices restart only after bounded active-time backoff. */
    public Action tick(boolean valid,boolean finished,boolean paused,boolean audible,boolean backendActive){
        if(!valid){dead=true;return stop();}
        if(dead)return stop();
        if(finished&&!releasing){
            releaseGain=envelope();releasing=true;
            if(!requested||profile.fadeOutSeconds<=0){dead=true;return stop();}
        }
        if(paused)return requested?Action.KEEP:Action.IDLE;
        if(releasing){releaseAge+=.05;if(releaseAge+1e-8>=profile.fadeOutSeconds){dead=true;return stop();}}
        else age+=.05;
        if(!audible)return stop();
        if(!requested){
            if(releasing)return Action.IDLE;
            requested=true;missingTicks=0;return Action.START;
        }
        if(backendActive){missingTicks=0;return Action.KEEP;}
        if(releasing){dead=true;return stop();}
        if(++missingTicks<RETRY_TICKS)return Action.KEEP;
        missingTicks=0;return Action.START;
    }
    private Action stop(){boolean was=requested;requested=false;missingTicks=0;return was?Action.STOP:Action.IDLE;}
    /** Explicit linear MC attenuation between proven source min/max values. */
    public float gain(double distance){
        if(!Double.isFinite(distance)||distance>=profile.maximumDistance)return 0;
        double spatial=distance<=profile.minimumDistance?1:(profile.maximumDistance-distance)/(profile.maximumDistance-profile.minimumDistance);
        return (float)(MIX_GAIN*profile.sourceVolume*envelope()*spatial);
    }
    public float pitch(double unitSample){return profile.minimumPitch+(profile.maximumPitch-profile.minimumPitch)*(float)Math.max(0,Math.min(1,unitSample));}
}
