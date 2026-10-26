package com.codex.blacklight.infection;

import java.util.*;
import net.minecraft.nbt.*;

/** Bounded, saved food processing. Input is real food; time alone never creates a meal. */
public final class TissueDigestion {
    public static final int MAX_PORTIONS=12,MAX_FAT=600,MAX_ENZYMES=8;
    public enum Kind {
        MEAT("meat",200),SOFT("soft",160),FIBRE("fibre",480),FERMENT("ferment",1600);
        public final String key;public final int work;
        Kind(String key,int work){this.key=key;this.work=work;}
        static Kind byKey(String key){for(var k:values())if(k.key.equals(key))return k;return null;}
    }
    public record Profile(double gizzard,double ferment,double absorption,double pancreas,double crop,double fat){
        public Profile{gizzard=p(gizzard);ferment=p(ferment);absorption=p(absorption);pancreas=p(pancreas);crop=p(crop);fat=p(fat);}
        private static double p(double x){return Double.isFinite(x)?Math.clamp(x,0,8):0;}
        public int capacity(){return 2+(int)Math.min(10,Math.ceil(crop*1.25));}
        public int fatCapacity(){return fat>0?(int)Math.min(MAX_FAT,120+60*fat):0;}
        public boolean digestive(){return gizzard+ferment+absorption+pancreas+crop+fat>0;}
        public boolean accepts(Kind kind){return kind!=null&&switch(kind){case FIBRE->gizzard>0;case FERMENT->ferment>0;default->true;};}
    }
    private record Portion(String kind,int value,double work,boolean primed){
        Kind type(){return Kind.byKey(kind);}
        Portion progress(double elapsed,boolean enzyme){return new Portion(kind,value,Math.max(0,work-elapsed),enzyme);}
    }
    public record Step(int reserve,int completed,boolean working){}
    private final List<Portion> portions=new ArrayList<>();
    private int fat,enzymes;private double absorptionRemainder;private boolean working,fatWorking,enzymeWorking;private String workingKind="";
    public int queued(){return portions.size();}public int fat(){return fat;}public int enzymes(){return enzymes;}
    public double remainingWork(){return portions.stream().mapToDouble(Portion::work).sum();}
    public boolean working(){return working;}
    public boolean offer(Kind kind,int nutrition,Profile p){
        if(!p.accepts(kind)||nutrition<=0||portions.size()>=p.capacity()||portions.size()>=MAX_PORTIONS)return false;
        portions.add(new Portion(kind.key,Math.clamp(nutrition,1,64),kind.work,false));return true;
    }
    /** Overflow becomes actual lipid tissue only while its organ is functional. */
    public int deposit(int reserve,int amount,Profile p){
        reserve=TissueMetabolism.clamp(reserve);int offered=Math.max(0,amount),base=Math.min(offered,TissueMetabolism.MAX_RESERVE-reserve);
        reserve+=base;int extra=Math.min(offered-base,Math.max(0,p.fatCapacity()-fat));fat+=extra;fatWorking|=extra>0;return reserve;
    }
    private int room(int reserve,Profile p){return TissueMetabolism.MAX_RESERVE-reserve+Math.max(0,p.fatCapacity()-fat);}
    private static double rate(Portion food,Profile p){
        if(!p.accepts(food.type()))return 0;
        return switch(food.type()){
            case FIBRE->1+.15*p.gizzard;
            case FERMENT->.5+.15*p.ferment+.05*p.gizzard;
            case MEAT->food.primed?1+.2*p.pancreas:1;
            case SOFT->food.primed?1+.08*p.pancreas:1;
        };
    }
    public Step advance(int ticks,int reserve,Profile p){
        reserve=TissueMetabolism.clamp(reserve);working=false;fatWorking=false;enzymeWorking=false;workingKind="";if(ticks<=0)return new Step(reserve,0,false);
        if(p.fat>0&&fat>0&&reserve<TissueMetabolism.MAX_RESERVE/5){
            int release=Math.min(fat,1+(int)Math.ceil(p.fat));reserve+=release;fat-=release;fatWorking=release>0;
        }
        double budget=Math.clamp(ticks,1,200);int completed=0;
        // At most 12 portions. Unavailable future food stays saved; it cannot turn into a generic meal.
        for(int i=0;i<portions.size()&&budget>0;){
            var food=portions.get(i);double speed=rate(food,p);if(speed==0){i++;continue;}
            double exact=food.value*(1+.5*p.absorption/(2+p.absorption))+absorptionRemainder;
            int reward=(int)Math.floor(exact);
            if(room(reserve,p)<reward){i++;continue;}
            if(!food.primed&&p.pancreas>0&&(food.type()==Kind.MEAT||food.type()==Kind.SOFT)){
                if(enzymes==0&&reserve>0){reserve--;enzymes=Math.min(MAX_ENZYMES,3+(int)Math.floor(p.pancreas/2));}
                if(enzymes>0){enzymes--;food=food.progress(0,true);speed=rate(food,p);}
            }
            double consumed=Math.min(budget,food.work/speed);budget-=consumed;working|=consumed>0;
            if(consumed>0){workingKind=food.kind;enzymeWorking|=food.primed;}
            food=food.progress(consumed*speed,food.primed);portions.set(i,food);
            if(food.work>1e-7)break;
            // Commit only after all processing and enough real storage space exist.
            reserve=deposit(reserve,reward,p);absorptionRemainder=exact-reward;portions.remove(i);completed++;
        }
        return new Step(reserve,completed,working);
    }
    public CompoundTag visual(Profile p){
        var t=new CompoundTag();t.putInt("Queued",queued());t.putInt("Capacity",p.capacity());t.putBoolean("Working",working);
        t.putInt("Fat",fat);t.putInt("FatCapacity",p.fatCapacity());t.putInt("Enzymes",enzymes);t.putBoolean("FatWorking",fatWorking);t.putBoolean("EnzymeWorking",enzymeWorking);t.putString("WorkingKind",workingKind);return t;
    }
    public CompoundTag write(){
        var t=new CompoundTag();t.putInt("Schema",1);t.putInt("Fat",fat);t.putInt("Enzymes",enzymes);t.putDouble("AbsorptionRemainder",absorptionRemainder);
        var list=new ListTag();for(var food:portions){var f=new CompoundTag();f.putString("Kind",food.kind);f.putInt("Value",food.value);f.putDouble("Work",food.work);f.putBoolean("Primed",food.primed);list.add(f);}t.put("Portions",list);return t;
    }
    public static TissueDigestion read(CompoundTag t){
        var d=new TissueDigestion();d.fat=Math.clamp(t.getInt("Fat"),0,MAX_FAT);d.enzymes=Math.clamp(t.getInt("Enzymes"),0,MAX_ENZYMES);
        double remainder=t.getDouble("AbsorptionRemainder");d.absorptionRemainder=Double.isFinite(remainder)?Math.clamp(remainder,0,.999999999):0;
        var list=t.getList("Portions",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(MAX_PORTIONS,list.size());i++){
            var f=list.getCompound(i);String kind=f.getString("Kind");double work=f.getDouble("Work");
            if(kind.isBlank()||kind.length()>64||!Double.isFinite(work)||f.getInt("Value")<=0)continue;
            var type=Kind.byKey(kind);d.portions.add(new Portion(kind,Math.clamp(f.getInt("Value"),1,64),Math.clamp(work,0,type==null?1600:type.work),f.getBoolean("Primed")));
        }return d;
    }
}
