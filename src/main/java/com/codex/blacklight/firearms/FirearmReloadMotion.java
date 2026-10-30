package com.codex.blacklight.firearms;

/** One non-looping reload clock: release, extract, fetch, insert, charge, shoulder. */
public final class FirearmReloadMotion {
    public record Frame(double lower,double release,double handDown,double handBack,double bolt,
                        double magazineDrop,boolean magazineVisible){}
    private FirearmReloadMotion(){}
    private static double smooth(double a,double b,double x){double t=Math.clamp((x-a)/(b-a),0,1);return t*t*(3-2*t);}
    private static double path(double p,double[] times,double[] values){
        for(int i=1;i<times.length;i++)if(p<=times[i])return values[i-1]+(values[i]-values[i-1])*smooth(times[i-1],times[i],p);
        return values[values.length-1];
    }
    public static Frame sample(double progress){
        double p=Double.isFinite(progress)?Math.clamp(progress,0,1):0;
        double lower=smooth(0,.13,p)*(1-smooth(.83,1,p));
        double release=smooth(.06,.18,p)*(1-smooth(.89,1,p));
        double down=path(p,new double[]{0,.16,.32,.46,.57,.70,.78,.86,1},new double[]{0,.06,.28,.46,.29,.06,-.015,-.015,0});
        double back=path(p,new double[]{0,.16,.32,.46,.57,.70,.78,.86,1},new double[]{0,.14,.18,.25,.20,.14,.10,.22,0});
        double drop=path(p,new double[]{0,.16,.32,.52,.70,1},new double[]{0,0,.34,.34,0,0});
        double bolt=smooth(.77,.82,p)*(1-smooth(.86,.91,p));
        return new Frame(lower,release,down,back,bolt,drop,p<.34||p>=.52);
    }
    public static boolean magazinePart(String name){String id=name.toLowerCase(java.util.Locale.ROOT);return id.contains("magazine")||id.contains("ammo box")||id.contains("ammo_box")||id.contains("drum");}
    public static double recoil(double ticks){
        if(!Double.isFinite(ticks)||ticks<0||ticks>=9)return 0;
        return ticks<.7?smooth(0,.7,ticks):Math.exp(-(ticks-.7)*.57)*(1-smooth(6,9,ticks));
    }
}
