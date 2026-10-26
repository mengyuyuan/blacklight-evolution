package com.codex.blacklight.infection;

/** The same phase clock drives server contact and client articulation. Units are simulation ticks. */
public final class TissueOrganCycle {
    public record Cycle(int windup,int contact,int retract,int end,int cooldown,int cost) {
        public float effort(float tick){
            if(tick<0||tick>=end)return 0;
            if(tick<windup)return smooth(tick/Math.max(1,windup));
            if(tick<contact)return 1;
            return 1-smooth((tick-contact)/Math.max(1,end-contact));
        }
    }
    private TissueOrganCycle(){}
    public static Cycle cycle(TissueCore core){return switch(core){
        case TENDON->new Cycle(14,22,30,42,65,3);
        case HYDRAULIC->new Cycle(10,14,20,30,55,2);
        case HYDROSTAT->new Cycle(8,15,28,40,70,3);
        case BLADE->new Cycle(11,16,23,34,55,3);
        case CHELA->new Cycle(14,17,30,44,75,4);
        case HORN->new Cycle(16,17,34,50,100,5);
        case PHARYNX->new Cycle(28,44,60,72,100,2);
        case SADDLE->new Cycle(24,28,36,50,90,4);
        case CLUB->new Cycle(22,30,44,62,100,5);
        case TONGUE->new Cycle(12,20,38,50,85,4);
        case CNIDOCYTE->new Cycle(10,15,24,36,90,3);
        default->new Cycle(8,12,20,28,46,1);
    };}
    public static float smooth(float t){t=Math.clamp(t,0,1);return t*t*(3-2*t);}
    public static double boundedPower(double power){return Double.isFinite(power)?Math.clamp(power,0,8):0;}
    public static double launchSpeed(TissueCore core,double power){double p=boundedPower(power);return switch(core){
        case TENDON->.48+.055*p;case HYDRAULIC->.22+.025*p;case HORN->.54+.045*p;default->0;
    };}
    public static float knockback(TissueCore core,double power){return (float)(switch(core){
        case HYDRAULIC->.45;case HORN,SADDLE->.8;case CLUB->1.1;default->.15;
    }+.06*boundedPower(power));}
    public static float reach(TissueCore core){return switch(core){case HYDROSTAT->2.4F;case BLADE->1.4F;case CHELA->1.1F;case HYDRAULIC->1.2F;case TONGUE->6F;case SADDLE,CLUB,CNIDOCYTE->1.6F;default->.7F;};}
    public static boolean range(TissueCore core,double gap){return switch(core){
        case TENDON->gap>=1.2&&gap<=6;case HYDRAULIC->gap>=0&&gap<=2.8;
        case HORN->gap>=3&&gap<=12;case TONGUE->gap>=1.2&&gap<=reach(core);
        case HYDROSTAT,BLADE,CHELA,SADDLE,CLUB,CNIDOCYTE->gap>=0&&gap<=reach(core);default->false;
    };}
}
