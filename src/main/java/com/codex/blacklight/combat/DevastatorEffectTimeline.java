package com.codex.blacklight.combat;

/** Source tissue clocks, separate from the authoritative damage lease.
 * Only the velocity and named retract event are recovered; contact dwell and
 * controller-driven graveyard teardown remain explicit Minecraft scheduling. */
public final class DevastatorEffectTimeline {
    public static final double WORLD_SCALE=.8176232451;
    public static final double TENDRIL_RETRACT_START=7D/3, TENDRIL_RETRACT_END=10D/3;
    public static final double GRAVEYARD_DESCEND=.2;
    public static final double PAIN_CONTACT_DWELL=.20;
    private DevastatorEffectTimeline() { }
    public static double smooth(double value) {double t=Math.clamp(value,0,1);return t*t*(3-2*t);}
    public static double strand(double age,double launch,double reverseAt,double distance,double forward,double reverse) {
        if(!Double.isFinite(age+launch+reverseAt+distance+forward+reverse)||distance<=0||forward<=0||reverse<=0)return 0;
        if(age<=launch)return 0;
        if(age<reverseAt)return Math.clamp((age-launch)*forward/distance,0,1);
        double formed=Math.clamp((reverseAt-launch)*forward/distance,0,1);
        return Math.clamp(formed-(age-reverseAt)*reverse/distance,0,1);
    }
    /** Positive occupancy drives translation into the soil, never height scaling. */
    public static double spike(double age,double launch,double rise,double reverseAt) {
        if(!Double.isFinite(age+launch+rise+reverseAt)||rise<=0)return 0;
        if(age<=launch)return 0;
        double formed=smooth((Math.min(age,reverseAt)-launch)/rise);
        return age<reverseAt?formed:formed*(1-smooth((age-reverseAt)/GRAVEYARD_DESCEND));
    }
    public static double visibleEnd(int kind,double effectStart,double effectEnd) {
        // Residual mist outlives the body recovery, without extending damage.
        if(kind==3)return Math.max(effectEnd,effectStart+3.3);
        if(kind==1)return Math.max(effectEnd,effectStart-.4+TENDRIL_RETRACT_END);
        // Tissue can return through 4.2s; the distortion emitter starts at
        // 2/3s, emits for 7/3s and has a separate configured 2s fade.
        if(kind==2)return Math.max(effectEnd,effectStart+5);
        return effectEnd;
    }
    /** Presentation thresholds use the server's actual fall, never crater radius. */
    public record Impact(double clusterRadius,double clusterHeight,boolean mist,double mistGain) { }
    public static Impact impact(double height){
        double h=Double.isFinite(height)?Math.max(0,height):0;
        double root=Math.sqrt(h);
        return new Impact(Math.min(6,.45+root*.28),Math.min(5,.45+root*.22),h>50,
                h>50?.3+.7*(1-Math.exp(-(h-50)/40)):0);
    }
    public static double impactClusterOpacity(double age){
        if(!Double.isFinite(age)||age<0||age>=.8)return 0;
        return smooth(age/.045)*(1-smooth((age-.12)/.68));
    }
    public static double impactMistOpacity(double age,double delay){
        double local=age-delay;
        if(!Double.isFinite(local)||local<0||local>=3)return 0;
        return Math.min(1,local/.09)*Math.pow(Math.max(0,1-local/3),1.3);
    }
    /** Source RadialBlur is a screen-space lens envelope, not rotation lock. */
    public record Lens(double burst,double retract,double flight) {
        public boolean active(){return burst>1e-5||retract>1e-5||flight>1e-5;}
    }
    public static Lens lens(int kind,int phase,double age,double effectStart,double fallHeight,double downSpeed) {
        double burst=0,retract=0,flight=0;
        if(kind==1) {
            double attack=effectStart-.4;
            burst=pulse(age-effectStart,.5);
            double r=age-attack-TENDRIL_RETRACT_START;
            if(r>=0&&r<1)retract=.3*(1-smooth(r));
        }else if(kind==0&&phase==3||kind==0&&phase==1) {
            burst=Math.max(pulse(age-effectStart,.5),.2*pulse(age-effectStart-1D/6,.5));
        }else if(kind==2) {
            // FIG critical/effects effect163500 is distortionObject004, local
            // 0..2.333333, spawned by the actor at .666667. Its native shader
            // amplitude is unknown; the .35 screen response is MC presentation.
            double local=age-effectStart-2D/3;
            if(local>=0&&local<7D/3)burst=.35*smooth(local/.12);
            else if(local>=7D/3&&local<13D/3)burst=.35*(1-smooth((local-7D/3)/2));
        }else if(kind==3) {
            if(phase==2)flight=flight(fallHeight,downSpeed,age).strength();
            else if(phase==3)burst=pulse(age-effectStart,.5);
        }
        return new Lens(burst,retract,flight);
    }
    public static double flightStrength(double height,double speed) {
        if(!Double.isFinite(height+speed))return 0;
        // speed is Minecraft blocks/tick. The previous /12 response barely
        // moved at a normal 1-3 blocks/tick dive. Optics may saturate; physics
        // and terrain energy continue to use the full actual values.
        return Math.clamp((1-Math.exp(-Math.max(0,height)/20))*.35
                +(1-Math.exp(-Math.max(0,speed)/2.4))*.65,0,1);
    }
    public record Flight(double strength,double tangentGain,double streakLength,double airAlpha,double shock) {
        public boolean active(){return strength>1e-5;}
    }
    /** Source flight fades in .1s; cycle zoom .6 ramps over 2.5s, without ReAim.
     * The short-dive pressure response and visible air widths are MC adaptation.
     * Long falls keep their presentation until real landing, not a timed impact. */
    public static Flight flight(double height,double speed,double releaseAge) {
        if(!Double.isFinite(height+speed+releaseAge)||releaseAge<0)return new Flight(0,0,0,0,0);
        double seconds=Math.max(0,releaseAge-1D/6);
        double response=flightStrength(height,speed),onset=smooth(seconds/.1),strength=response*onset;
        double sourceZoom=smooth(seconds/2.5);
        double gain=strength*(.22+(1D/.6-1-.22)*sourceZoom);
        double shockAge=releaseAge-1D/30;
        double shock=shockAge<0?0:smooth(shockAge/.1)*(1-smooth((releaseAge-2D/3)/.5));
        return new Flight(strength,gain,.55+3.2*strength,strength*(.08+.18*strength),shock);
    }
    /** The hand projection uses a different event and never calls this mapping. */
    public static double flightFov(double original,double tangentGain,double userEffectScale) {
        if(!Double.isFinite(original+tangentGain+userEffectScale)||tangentGain<=0||userEffectScale<=0)return original;
        double safe=Math.clamp(original,1,175);
        double tangent=Math.tan(Math.toRadians(safe)*.5)*(1+Math.clamp(tangentGain,0,2D/3)*Math.clamp(userEffectScale,0,1));
        return Math.max(original,Math.min(175,Math.toDegrees(2*Math.atan(tangent))));
    }
    private static double pulse(double age,double duration) {
        return age>=0&&age<duration?1-smooth(age/duration):0;
    }
}
