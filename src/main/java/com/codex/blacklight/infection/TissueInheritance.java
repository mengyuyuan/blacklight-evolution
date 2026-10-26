package com.codex.blacklight.infection;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Fixed-size genetic inventory. Age growth is derived separately and is never added twice.
 * Numbers describe integrated tissue, not player research ranks or arbitrary entity health. */
public final class TissueInheritance {
    public static final int SCHEMA=5, BLADE=1, CLAWS=2, TENDRIL=4;
    public static final long MIN_ACQUIRED_ORGANS=6;
    public static final double DIGEST_WORK=3600, NEW_MATURITY_WORK=3600;
    public boolean readyToHunt(long age){
        return age>=TissueGrowth.ANCIENT_TICKS&&organs.values().stream().allMatch(o->o.mature()&&o.pending()==0)
                &&sources.values().stream().allMatch(s->s.work()==0);
    }
    /** Separate provenance: donors sharing an organ still retain distinct body identities. */
    public record BodySource(int plan,long variant,double work,long portions,long age,String nativeCore,TissueEvolution.State authored,TissueEvolution.State procedural,Map<TissueCore,Organ> genes,String appearance) {
        public BodySource(int plan,long variant,double work,long portions,long age,String nativeCore,TissueEvolution.State authored,TissueEvolution.State procedural){this(plan,variant,work,portions,age,nativeCore,authored,procedural,Map.of(),"");}
        public BodySource(int plan,long variant,double work,long portions){this(plan,variant,work,portions,0,"",new TissueEvolution.State(0,0,0,0,0,0),new TissueEvolution.State(0,0,0,0,0,0));}
        public BodySource {plan=Math.clamp(plan,-1,2);work=Math.clamp(number(work),0,NEW_MATURITY_WORK);portions=Math.max(1,portions);age=TissueGrowth.clamp(age);nativeCore=validSpecies(nativeCore)?nativeCore:"";authored=clean(authored);procedural=clean(procedural);genes=genes==null?Map.of():Map.copyOf(genes);appearance=appearance==null||appearance.length()>32768?"":appearance;}
        BodySource digest(double elapsed){return new BodySource(plan,variant,Math.max(0,work-elapsed),portions,age,nativeCore,authored,procedural,genes,appearance);}
        BodySource receive(){return new BodySource(plan,variant,work,portions==Long.MAX_VALUE?portions:portions+1,age,nativeCore,authored,procedural,genes,appearance);}
        BodySource forming(){return new BodySource(plan,variant,NEW_MATURITY_WORK,portions,age,nativeCore,authored,procedural,genes,appearance);}
        private static float trait(float f){return Float.isFinite(f)?Math.clamp(f,0,8):0;}
        private static TissueEvolution.State clean(TissueEvolution.State s){
            if(s==null)return new TissueEvolution.State(0,0,0,0,0,0);
            var a=s.aggregate();return new TissueEvolution.State(Math.max(0,s.generation()),trait(s.muscle()),trait(s.carapace()),trait(s.jaw()),trait(s.grasp()),trait(s.crest()),
                    new TissueAggregate.Form(Math.min(2,trait(a.amount())),Math.min(1,trait(a.radial())),Math.min(1,trait(a.quadruped())),Math.min(1,trait(a.upright())),Float.isFinite(a.handedness())?Math.clamp(a.handedness(),-1,1):0));
        }
    }
    public record Organ(double integrated,double pending,double work,double maturityWork,int weapons,long variant,int donorPlan) {
        public Organ(double integrated,double pending,double work,double maturityWork,int weapons,long variant){this(integrated,pending,work,maturityWork,weapons,variant,-1);}
        public Organ {
            integrated=number(integrated);pending=number(pending);
            work=Math.clamp(number(work),0,DIGEST_WORK);
            maturityWork=Math.clamp(number(maturityWork),0,NEW_MATURITY_WORK);
            weapons&=BLADE|CLAWS|TENDRIL;
            donorPlan=Math.clamp(donorPlan,-1,2);
            if(pending==0)work=0;
        }
        public double maturity(){return 1-.8*maturityWork/NEW_MATURITY_WORK;}
        public boolean mature(){return maturityWork==0;}
        Organ receive(double gain,int donatedWeapons){
            return new Organ(integrated,add(pending,gain),gain>0?DIGEST_WORK:work,maturityWork,weapons|donatedWeapons,variant,donorPlan);
        }
        Organ digest(double elapsed){
            double used=Math.min(work,elapsed),gain=work==0?pending:pending*(used/work);
            double remaining=work-used;
            return new Organ(add(integrated,gain),remaining==0?0:Math.max(0,pending-gain),remaining,
                    Math.max(0,maturityWork-elapsed),weapons,variant,donorPlan);
        }
    }
    public record Rules(double fusion,double strong,long meals,double points) {
        public static final Rules DEFAULT=new Rules(.35,300,6,1000);
        public Rules { fusion=Math.clamp(number(fusion),0,1);strong=Math.max(1,number(strong));meals=Math.max(1,meals);points=Math.max(1,number(points)); }
    }
    public static final TissueInheritance EMPTY=new TissueInheritance(Map.of(),0,0,false,null,0);
    private final Map<TissueCore,Organ> organs;
    private final Map<String,Organ> dormant;
    private final Map<String,BodySource> sources;
    private final long meals,revision,acquiredOrgans;
    private final double points;
    private final boolean qualified;
    private final UUID lastOperation;
    public TissueInheritance(Map<TissueCore,Organ> organs,long meals,double points,boolean qualified,UUID lastOperation,long revision){
        this(organs,meals,points,qualified,lastOperation,revision,legacyAcquired(organs,meals));
    }
    public TissueInheritance(Map<TissueCore,Organ> organs,long meals,double points,boolean qualified,UUID lastOperation,long revision,long acquiredOrgans){
        this(organs,meals,points,qualified,lastOperation,revision,acquiredOrgans,Map.of());
    }
    public TissueInheritance(Map<TissueCore,Organ> organs,long meals,double points,boolean qualified,UUID lastOperation,long revision,long acquiredOrgans,Map<String,Organ> dormant){
        this(organs,meals,points,qualified,lastOperation,revision,acquiredOrgans,dormant,Map.of());
    }
    public TissueInheritance(Map<TissueCore,Organ> organs,long meals,double points,boolean qualified,UUID lastOperation,long revision,long acquiredOrgans,Map<String,Organ> dormant,Map<String,BodySource> sources){
        var copy=new EnumMap<TissueCore,Organ>(TissueCore.class);
        organs.forEach((core,organ)->{if(core!=null&&organ!=null)copy.put(core,organ);});
        this.organs=Map.copyOf(copy);this.dormant=Map.copyOf(dormant);this.meals=Math.max(0,meals);this.points=number(points);
        var bodies=new java.util.TreeMap<String,BodySource>();sources.forEach((id,source)->{if(validSpecies(id)&&source!=null)bodies.put(id,source);});this.sources=Map.copyOf(bodies);
        this.acquiredOrgans=Math.max(0,acquiredOrgans);
        this.qualified=qualified&&this.acquiredOrgans>=MIN_ACQUIRED_ORGANS;this.lastOperation=lastOperation;this.revision=Math.max(0,revision);
    }
    /** Old previews did not retain source counts. Only known positive categories
     * are a defensible lower bound; do not manufacture six from an ancestry total. */
    private static long legacyAcquired(Map<TissueCore,Organ> organs,long meals){
        return Math.min(Math.max(0,meals),organs.values().stream().filter(o->o!=null&&(o.integrated()>0||o.pending()>0)).count());
    }
    public Map<TissueCore,Organ> organs(){return organs;}
    /** Unavailable module IDs survive saves without gaining phantom functions or rewards. */
    public Map<String,Organ> dormant(){return dormant;}
    public Map<String,BodySource> sources(){return sources;}
    public static boolean validSpecies(String id){return id!=null&&id.length()<=256&&id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+");}
    public static long legacyAcquiredCount(Map<TissueCore,Organ> organs,long meals){return legacyAcquired(organs,meals);}
    public Organ organ(TissueCore core){return organs.get(core);}
    public long meals(){return meals;}
    public long acquiredOrgans(){return acquiredOrgans;}
    public double points(){return points;}
    public boolean qualified(){return qualified;}
    public UUID lastOperation(){return lastOperation;}
    public long revision(){return revision;}
    private long nextRevision(){return revision==Long.MAX_VALUE?revision:revision+1;}
    public static double nativeStrength(long age){return 100*Math.log1p(Math.max(0,age)/72000D)/Math.log(2);}
    public double strength(TissueCore core,TissueCore nativeCore,long age){
        var organ=organs.get(core);
        return add(core==nativeCore?nativeStrength(age):0,organ==null?0:organ.integrated());
    }
    public double maturity(TissueCore core,TissueCore nativeCore,long age){
        return core==nativeCore?TissueGrowth.progress(age):organs.containsKey(core)?organs.get(core).maturity():0;
    }
    public int weapons(long seed,long age){
        int result=nativeWeapons(seed,age);
        for(var organ:organs.values())if(organ.mature()&&organ.integrated()>0)result|=organ.weapons();
        return result;
    }
    public static int nativeWeapons(long seed,long age){
        return nativeWeapons(TissueCore.forSeed(seed),seed,age);
    }
    public static int nativeWeapons(TissueCore core,long seed,long age){
        if(age<TissueGrowth.MATURITY_TICKS)return 0;
        if(!core.legacy())return switch(core){case BLADE->BLADE;case CHELA->CLAWS;case HYDROSTAT->TENDRIL;default->0;};
        // These are structures actually present in the current native organ vocabulary.
        return TENDRIL|(TissueOrgans.count(seed)==5?BLADE:0)|(TissueCore.forSeed(seed)==TissueCore.JAW?CLAWS:0);
    }
    public static double fusionGain(double current,double prey,double coefficient){
        current=number(current);prey=number(prey);
        return number(Math.clamp(number(coefficient),0,1)*prey/(1+current/Math.max(prey,100)));
    }
    /** Called only after an owned live capture's real death has been accepted. */
    public TissueInheritance inherit(UUID operation,TissueInheritance prey,long selfSeed,long age,long preySeed,long preyAge,Rules rules){
        return inherit(operation,prey,selfSeed,age,preySeed,preyAge,rules,-1);
    }
    public TissueInheritance inherit(UUID operation,TissueInheritance prey,long selfSeed,long age,long preySeed,long preyAge,Rules rules,int donorPlan){
        return inherit(operation,prey,selfSeed,age,preySeed,preyAge,rules,donorPlan,TissueCore.forSeed(selfSeed),TissueCore.forSeed(preySeed));
    }
    public TissueInheritance inherit(UUID operation,TissueInheritance prey,long selfSeed,long age,long preySeed,long preyAge,Rules rules,int donorPlan,TissueCore nativeCore,TissueCore preyCore){
        return inherit(operation,prey,selfSeed,age,preySeed,preyAge,rules,donorPlan,nativeCore,preyCore,"");
    }
    public TissueInheritance inherit(UUID operation,TissueInheritance prey,long selfSeed,long age,long preySeed,long preyAge,Rules rules,int donorPlan,TissueCore nativeCore,TissueCore preyCore,String donorSpecies){
        return inherit(operation,prey,selfSeed,age,preySeed,preyAge,rules,donorPlan,nativeCore,preyCore,donorSpecies,"");
    }
    public TissueInheritance inherit(UUID operation,TissueInheritance prey,long selfSeed,long age,long preySeed,long preyAge,Rules rules,int donorPlan,TissueCore nativeCore,TissueCore preyCore,String donorSpecies,String donorAppearance){
        if(operation==null||operation.equals(lastOperation)||preyAge<TissueGrowth.MATURITY_TICKS)return this;
        var result=new EnumMap<TissueCore,Organ>(TissueCore.class);result.putAll(organs);
        double awarded=0;long collected=0;
        for(var core:TissueCore.values()){
            double offered=prey.strength(core,preyCore,preyAge)*prey.maturity(core,preyCore,preyAge);
            Organ existing=result.get(core);
            double committed=add(strength(core,nativeCore,age),existing==null?0:existing.pending());
            double gain=fusionGain(committed,offered,rules.fusion());
            if(gain<=0)continue;
            // A weapon is inherited from a donor that owns its physical structure, never from an armour score.
            int weapons=core==preyCore?nativeWeapons(preyCore,preySeed,preyAge):0;
            var donor=prey.organs.get(core);if(donor!=null&&donor.mature())weapons|=donor.weapons();
            if(core==preyCore||donor!=null&&donor.mature())collected++;
            boolean nativeDonor=core==preyCore||donor==null;
            if(existing==null)existing=new Organ(0,0,0,core==nativeCore?0:NEW_MATURITY_WORK,0,
                    nativeDonor?TissueGrowth.mix(preySeed^core.ordinal()):donor.variant(),nativeDonor?donorPlan:donor.donorPlan());
            result.put(core,existing.receive(gain,weapons));awarded=add(awarded,gain);
        }
        long total=acquiredOrgans>Long.MAX_VALUE-collected?Long.MAX_VALUE:acquiredOrgans+collected;
        var bodies=new java.util.TreeMap<>(sources);
        if(awarded>0){
            if(validSpecies(donorSpecies))bodies.compute(donorSpecies,(id,before)->{
                if(before!=null)return before.receive();
                var base=new TissueEvolution.State(0,0,0,0,0,0);
                var authored=prey.appearance(preyCore==null?base:TissueEvolution.nativeState(preyCore,preySeed,preyAge,true),preyCore);
                var procedural=prey.appearance(preyCore==null?base:TissueEvolution.nativeState(preyCore,preySeed,preyAge,false),preyCore);
                return new BodySource(donorPlan,preySeed,NEW_MATURITY_WORK,1,preyAge,preyCore==null?"":preyCore.key(),authored,procedural,prey.organs,donorAppearance);
            });
            prey.sources.forEach((id,source)->bodies.putIfAbsent(id,source.forming()));
        }
        return new TissueInheritance(result,meals==Long.MAX_VALUE?meals:meals+1,add(points,awarded),qualified,operation,nextRevision(),total,dormant,bodies);
    }
    /** Only loaded simulation time advances digestion. No offline age catch-up. */
    public TissueInheritance digest(long elapsed,boolean supplied,long seed,long age,Rules rules){
        return digest(elapsed,supplied,seed,age,rules,TissueCore.forSeed(seed));
    }
    public TissueInheritance digest(long elapsed,boolean supplied,long seed,long age,Rules rules,TissueCore nativeCore){
        if(elapsed<=0)return latch(nativeCore,age,rules);
        double work=Math.min(DIGEST_WORK,Math.min(elapsed,DIGEST_WORK*5)*(supplied?1:.2));
        var result=new EnumMap<TissueCore,Organ>(TissueCore.class);boolean changed=false;
        for(var entry:organs.entrySet()){
            var before=entry.getValue();var after=before.digest(work);result.put(entry.getKey(),after);changed|=!before.equals(after);
        }
        var bodies=new java.util.TreeMap<String,BodySource>();for(var entry:sources.entrySet()){
            var after=entry.getValue().digest(work);bodies.put(entry.getKey(),after);changed|=!after.equals(entry.getValue());
        }
        return (changed?new TissueInheritance(result,meals,points,qualified,lastOperation,nextRevision(),acquiredOrgans,dormant,bodies):this).latch(nativeCore,age,rules);
    }
    private TissueInheritance latch(TissueCore nativeCore,long age,Rules rules){
        if(qualified||acquiredOrgans<MIN_ACQUIRED_ORGANS||meals<rules.meals()||points<rules.points())return this;
        for(var core:new TissueCore[]{TissueCore.MUSCLE,TissueCore.CARAPACE,TissueCore.JAW,TissueCore.GRASP})
            if(matureStructuralStrength(core,nativeCore,age)<rules.strong())return this;
        return new TissueInheritance(organs,meals,points,true,lastOperation,nextRevision(),acquiredOrgans,dormant,sources);
    }
    private double matureStructuralStrength(TissueCore role,TissueCore nativeCore,long age){
        double result=0;
        for(var core:TissueCore.values())if(core.structuralBias()==role&&maturity(core,nativeCore,age)>=1)
            result=add(result,strength(core,nativeCore,age));
        return result;
    }
    /** Paid development of already integrated organs. Food never manufactures
     * donor identities, assimilation points or accepted consumption counts. */
    public TissueInheritance cultivate(long paidTicks,TissueCore nativeCore,long age,Rules rules){
        if(paidTicks<=0||organs.isEmpty())return this;
        var result=new EnumMap<TissueCore,Organ>(TissueCore.class);boolean changed=false;
        double work=Math.min(paidTicks,TissueMetabolism.CULTIVATION_BATCH)/24000D;
        for(var entry:organs.entrySet()){
            var organ=entry.getValue();
            if(organ.mature()&&organ.pending()==0&&organ.integrated()>0){
                double strength=add(organ.integrated(),20*work);
                var grown=new Organ(strength,0,0,0,organ.weapons(),organ.variant(),organ.donorPlan());
                result.put(entry.getKey(),grown);changed|=!grown.equals(organ);
            }else result.put(entry.getKey(),organ);
        }
        return (changed?new TissueInheritance(result,meals,points,qualified,lastOperation,nextRevision(),acquiredOrgans,dormant,sources):this).latch(nativeCore,age,rules);
    }
    /** Added attributes use acquired strength only; native-age modifiers already exist. */
    public TissueEvolution.Combat combat(){
        double muscle=level(TissueCore.MUSCLE),armour=level(TissueCore.CARAPACE),jaw=level(TissueCore.JAW),crest=level(TissueCore.CREST);
        return new TissueEvolution.Combat(.18*muscle+.15*armour,.08*muscle+.10*jaw,1.5*armour,.15*-Math.expm1(-.2*crest));
    }
    private double level(TissueCore core){var organ=organs.get(core);return organ==null?0:Math.log1p(organ.integrated()/100)/Math.log(2);}
    public float scaleFactor(){
        double strongest=0,total=0;for(var organ:organs.values()){
            double load=Math.log1p(organ.integrated()/100);strongest=Math.max(strongest,load);total+=load;
        }
        // Broad whole-body support accompanies local organs; height never grows alone.
        return (float)(1+Math.max(.08*strongest,.035*total));
    }
    /** Smooth, bounded anatomy weights. The inventory itself is not capped at three ranks. */
    public TissueEvolution.State appearance(TissueEvolution.State nativeState){
        return appearance(nativeState,null);
    }
    public TissueEvolution.State appearance(TissueEvolution.State nativeState,TissueCore nativeCore){
        return appearance(nativeState,nativeCore,0);
    }
    public TissueEvolution.State appearance(TissueEvolution.State nativeState,TissueCore nativeCore,double visualWork){
        float muscle=nativeState.muscle(),carapace=nativeState.carapace(),jaw=nativeState.jaw(),grasp=nativeState.grasp(),crest=nativeState.crest();
        float addedMuscle=0,addedCarapace=0,addedJaw=0,addedGrasp=0,addedCrest=0;
        float peakMuscle=0,peakCarapace=0,peakJaw=0,peakGrasp=0,peakCrest=0;
        for(var entry:organs.entrySet())if(!entry.getKey().legacy()&&entry.getKey()!=nativeCore){
            var shape=TissueEvolution.nativeState(entry.getKey(),entry.getValue().variant(),TissueGrowth.ANCIENT_TICKS,false);
            var organ=entry.getValue();
            float formation=TissueGraftLayout.growth(organ,visualWork);
            peakMuscle=Math.max(peakMuscle,shape.muscle()*formation);peakCarapace=Math.max(peakCarapace,shape.carapace()*formation);
            peakJaw=Math.max(peakJaw,shape.jaw()*formation);peakGrasp=Math.max(peakGrasp,shape.grasp()*formation);peakCrest=Math.max(peakCrest,shape.crest()*formation);
            // Every mature organ needs additional muscle, armour and circulation.
            // A later, weaker profile must not disappear behind the first per-axis maximum.
            float g=TissueGraftLayout.growth(organ,visualWork)*.54F*supportInvestment(organ);
            addedMuscle+=shape.muscle()*g;addedCarapace+=shape.carapace()*g;addedJaw+=shape.jaw()*g;
            addedGrasp+=shape.grasp()*g;addedCrest+=shape.crest()*g;
        }
        muscle=Math.max(muscle+support(addedMuscle),peakMuscle);carapace=Math.max(carapace+support(addedCarapace),peakCarapace);
        jaw=Math.max(jaw+support(addedJaw),peakJaw);grasp=Math.max(grasp+support(addedGrasp),peakGrasp);crest=Math.max(crest+support(addedCrest),peakCrest);
        return new TissueEvolution.State(nativeState.generation(),blend(muscle,TissueCore.MUSCLE,nativeCore,visualWork),
                blend(carapace,TissueCore.CARAPACE,nativeCore,visualWork),blend(jaw,TissueCore.JAW,nativeCore,visualWork),
                blend(grasp,TissueCore.GRASP,nativeCore,visualWork),blend(crest,TissueCore.CREST,nativeCore,visualWork),
                TissueAggregate.sample(this,nativeCore,visualWork));
    }
    private static float support(float load){return (float)(1.7*Math.log1p(load/1.7));}
    private static float supportInvestment(Organ organ){
        // Repeated acquisitions of an existing category continue developing its
        // support anatomy as well; they do not need a new slot to stay visible.
        return (float)(.8+.3*Math.log1p(organ.integrated()/100+organ.pending()/100));
    }
    /** Distributed support structures develop with all the organs they serve. */
    public float supportExpression(TissueCore structural,double visualWork){
        float load=0;for(var entry:organs.entrySet()){
            TissueCore core=entry.getKey();boolean contributes=core==structural||switch(structural){
                case CARAPACE->TissueEvolution.supportingMesh(core,"scapular_plate_1");
                case CREST->TissueEvolution.supportingMesh(core,"shoulder_crest_1");
                case JAW->core==TissueCore.PHARYNX;default->false;
            };
            if(contributes)load+=TissueGraftLayout.growth(entry.getValue(),visualWork)*(core==structural?1:.35F)*supportInvestment(entry.getValue());
        }return load;
    }
    private float blend(float base,TissueCore core,TissueCore nativeCore,double visualWork){
        var organ=organs.get(core);
        double integration=organ==null?0:organ.integrated()+(organ.work()>0?organ.pending()*Math.min(organ.work(),Math.clamp(visualWork,0,20))/organ.work():0);
        double strength=Math.log1p(integration/100)/Math.log(2);double gradual=base+Math.max(0,1.65-base)*-Math.expm1(-.95*strength);
        if(organ==null||core==nativeCore)return (float)gradual;
        // Acquiring a new organ establishes a distinct whole structure, not a barely visible stat tint.
        // Its early visible formation and its three-minute combat integration are separate clocks.
        double target=switch(core){case MUSCLE->2;case CARAPACE,JAW->2.4;case GRASP->2.3;case CREST->1.8;default->1.5;};
        target=Math.max(base,target+.3*-Math.expm1(-.25*strength));
        return (float)Math.max(gradual,base+(target-base)*TissueGraftLayout.growth(organ,visualWork));
    }
    public static boolean weakened(double health,double maximum){return Double.isFinite(health)&&Double.isFinite(maximum)&&maximum>0&&health>0&&health/maximum<.15;}
    public static boolean sizeFits(double width,double height,double preyWidth,double preyHeight){
        if(!Double.isFinite(width)||!Double.isFinite(height)||!Double.isFinite(preyWidth)||!Double.isFinite(preyHeight)
                ||width<=0||height<=0||preyWidth<=0||preyHeight<=0)return false;
        // Flat, wide species must not be rejected merely for being wider than an upright predator.
        return Math.max(preyWidth,preyHeight)<=Math.max(width,height)*2.5&&preyWidth*preyWidth*preyHeight<=width*width*height*8;
    }
    private static double number(double value){return Double.isFinite(value)?Math.max(0,value):0;}
    private static double add(double a,double b){return a>Double.MAX_VALUE-b?Double.MAX_VALUE:a+b;}
}
