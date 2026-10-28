package com.codex.blacklight.infection;

import net.minecraft.nbt.CompoundTag;

/** Saved gas exchange and paid circulation. Oxygen is work capacity, never food or free healing. */
public final class TissueVitals {
    public static final int MAX_OXYGEN=100,MAX_SPLEEN=8;
    public enum Route {SEALED,AIR,WATER,GILL,VENT,EXTERNAL,RESERVE}
    public record Profile(double gill,double lung,double airflow,double book,double trachea,double heart,double branchial,double spleen){
        public static final Profile EMPTY=new Profile(0,0,0,0,0,0,0,0);
        public Profile{gill=p(gill);lung=p(lung);airflow=p(airflow);book=p(book);trachea=p(trachea);heart=p(heart);branchial=p(branchial);spleen=p(spleen);}
        private static double p(double x){return Double.isFinite(x)?Math.clamp(x,0,8):0;}
        public boolean active(){return gill+lung+airflow+book+trachea+heart+branchial+spleen>0;}
        public boolean airAdapted(){return lung+airflow+book+trachea>0;}
        public int spleenCapacity(){return spleen>0?Math.min(MAX_SPLEEN,2+(int)Math.ceil(spleen)):0;}
        public int maxAir(int nativeCapacity){return (int)Math.clamp((long)Math.clamp(nativeCapacity,300,24000)+Math.round(420*lung/(1+lung)+120*airflow/(1+airflow)),300,24000);}
    }
    public record Environment(boolean headAir,boolean headWater,boolean ventAir,boolean moving,boolean wetBody,
                              boolean nativeAir,boolean nativeWater,boolean externalProtection,boolean underLoad,double volume,double health){
        public Environment{volume=Double.isFinite(volume)?Math.clamp(volume,.01,100000):1;health=Double.isFinite(health)?Math.clamp(health,0,1):0;}
    }
    public record Breath(boolean canBreathe,int refill,int spent,int airGrant,Route route){}
    public record Recovery(int ticks,int spent){}
    private int oxygen,gillCharge,ventCharge,spleen,spleenWork,clock,flash;
    private float strain;
    private Route route=Route.SEALED;
    private boolean moving,working,wetBody;
    public int oxygen(){return oxygen;}public int spleen(){return spleen;}public int spleenWork(){return spleenWork;}public int flash(){return flash;}
    public Route route(){return route;}
    /** One call per loaded entity breathe tick. Unloading/reloading never advances these counters. */
    public Breath breathe(Profile p,Environment e,int air,int nativeMax,int available){
        int spent=0,refill=0,grant=0;boolean can=false;route=Route.SEALED;moving=e.moving;working=e.underLoad;wetBody=e.wetBody;
        if(flash>0)flash--;
        if(e.headAir&&(e.nativeAir||p.airAdapted())){can=true;route=Route.AIR;refill=4;
            refill+=Math.min(12,(int)Math.ceil(p.lung*2));
            if(e.moving)refill+=Math.min(16,(int)Math.ceil(p.airflow*3));
            if(!e.moving&&!e.wetBody)refill+=Math.min(16,(int)Math.ceil(p.book*3));
        }else if(e.headWater&&e.nativeWater){can=true;route=Route.WATER;refill=4+(int)Math.min(8,p.gill*2);
        }else if(e.headWater&&p.gill>0){
            if(gillCharge==0&&available>0){spent++;gillCharge=200+(int)(20*p.gill);}
            if(gillCharge>0){gillCharge--;can=true;route=Route.GILL;refill=2+(int)Math.min(8,p.gill);}
        }
        if(!can&&e.ventAir&&p.trachea>0){
            if(ventCharge==0&&available>spent){spent++;ventCharge=Math.max(40,(int)(160*trachealEfficiency(p,e)));}
            if(ventCharge>0){ventCharge--;can=true;route=Route.VENT;refill=2;}
        }
        if(!can&&e.externalProtection){can=true;route=Route.EXTERNAL;}
        if((route==Route.WATER||route==Route.GILL)&&p.branchial>0)refill+=Math.min(12,(int)Math.ceil(p.branchial*2));
        boolean exchange=can&&route!=Route.EXTERNAL;
        if(++clock>=10){clock=0;
            int gain=exchange?2:0;
            if(route==Route.AIR){gain+=(int)Math.ceil(p.lung);if(e.moving)gain+=(int)Math.ceil(p.airflow*2);if(!e.moving&&!e.wetBody)gain+=(int)Math.ceil(p.book*2);}
            if(e.ventAir&&exchange)gain+=(int)Math.ceil(p.trachea*trachealEfficiency(p,e));
            if(route==Route.WATER||route==Route.GILL)gain+=(int)Math.ceil(p.branchial*2+p.gill);
            gain=(int)Math.floor(gain*(.4+.6*e.health));
            oxygen=Math.clamp(oxygen+gain-(e.underLoad?2:0)-(e.moving?1:0),0,MAX_OXYGEN);
            if(p.spleen>0&&exchange&&!e.underLoad&&!e.moving&&oxygen>=65&&spleen<p.spleenCapacity()){
                spleenWork=Math.min(200,spleenWork+10);
                if(spleenWork>=200&&available>spent){spent++;oxygen-=30;spleen++;spleenWork=0;}
            }
        }
        if(!can&&air<25&&p.spleen>0&&spleen>0){spleen--;grant=Math.min(p.maxAir(nativeMax)-Math.max(0,air),80);flash=20;route=Route.RESERVE;}
        return new Breath(can,Math.max(0,refill),spent,Math.max(0,grant),route);
    }
    /** Diffusive peripheral supply loses efficiency as the actual body becomes very large. */
    public static double trachealEfficiency(Profile p,Environment e){return 1/(1+Math.max(0,e.volume-4)/(8+6*p.heart));}
    /** Improve only the waiting/recovery period. Source animation frames and contact time are untouched. */
    public Recovery recover(int normal,Profile p,int available,double health){
        normal=Math.max(1,normal);double pumping=Math.max(p.heart,(route==Route.WATER||route==Route.GILL)?p.branchial*.85:0);
        if(pumping<=0||available<=0)return new Recovery(normal,0);
        if(oxygen<18&&p.spleen>0&&spleen>0){spleen--;oxygen=Math.min(MAX_OXYGEN,oxygen+30);flash=20;}
        if(oxygen<18)return new Recovery(normal,0);
        double pressure=(.4+.6*Math.clamp(Double.isFinite(health)?health:0,0,1))*(1-strain/200);
        int result=Math.max(4,(int)Math.round(normal*(1-Math.min(.28,.08+.04*pumping)*pressure)));
        if(result>=normal)return new Recovery(normal,0);
        oxygen-=18;flash=20;return new Recovery(result,1);
    }
    public void damaged(double fraction){if(!Double.isFinite(fraction)||fraction<=0)return;fraction=Math.min(1,fraction);oxygen=Math.max(0,oxygen-(int)Math.ceil(fraction*60));strain=(float)Math.min(100,strain+fraction*80);}
    public void repaired(double paidFraction){if(Double.isFinite(paidFraction)&&paidFraction>0)strain=(float)Math.max(0,strain-Math.min(1,paidFraction));}
    public CompoundTag visual(Profile p){var t=new CompoundTag();t.putInt("Oxygen",oxygen);t.putInt("Spleen",spleen);t.putInt("SpleenCapacity",p.spleenCapacity());t.putInt("Flash",flash);t.putString("Route",route.name());t.putBoolean("Moving",moving);t.putBoolean("Working",working);t.putBoolean("WetBody",wetBody);t.putFloat("Strain",strain);return t;}
    public CompoundTag write(){var t=new CompoundTag();t.putInt("Schema",1);t.putInt("Oxygen",oxygen);t.putInt("GillCharge",gillCharge);t.putInt("VentCharge",ventCharge);t.putInt("Spleen",spleen);t.putInt("SpleenWork",spleenWork);t.putInt("Clock",clock);t.putFloat("Strain",strain);return t;}
    public static TissueVitals read(CompoundTag t){var v=new TissueVitals();v.oxygen=Math.clamp(t.getInt("Oxygen"),0,MAX_OXYGEN);v.gillCharge=Math.clamp(t.getInt("GillCharge"),0,360);v.ventCharge=Math.clamp(t.getInt("VentCharge"),0,160);v.spleen=Math.clamp(t.getInt("Spleen"),0,MAX_SPLEEN);v.spleenWork=Math.clamp(t.getInt("SpleenWork"),0,200);v.clock=Math.clamp(t.getInt("Clock"),0,9);float strain=t.getFloat("Strain");v.strain=Float.isFinite(strain)?Math.clamp(strain,0,100):0;return v;}
}
