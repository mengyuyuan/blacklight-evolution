package com.codex.blacklight.combat;

import com.codex.blacklight.entity.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import java.util.*;

/** The underground spider owns its limb injuries and meals throughout its first health bar. */
public final class SpiderPhase {
    public static final String TUMOR="blacklight.greene_tumor";
    private final EncounterMob boss;
    private long cachedTick=Long.MIN_VALUE;
    private float cachedPartial, cachedYaw;
    private Vec3 cachedOrigin;
    private CompoundTag cachedState;
    private List<HydraContact.Region> cachedSkin;
    public final SpiderActions actions;
    public final SpiderFooting footing;
    public SpiderPhase(EncounterMob boss){this.boss=boss;actions=new SpiderActions(boss);footing=new SpiderFooting(boss);}
    private CompoundTag data(){return boss.spiderData();}
    public boolean first(){return boss.greeneOutbreak() && boss.greeneBattle().armourWave()==1;}
    public boolean live(){return first() && boss.greeneStage()==GreeneBattleCycle.GUARDED && boss.isAlive();}
    public int brokenMask(){return data().getInt("Lost");}
    public boolean broken(int limb){return limb>=0 && (brokenMask() & (1<<limb))!=0;}
    public int strike(int limb){return data().getInt("Strike"+limb);}
    public boolean severed(int limb){return broken(limb) && (strike(limb)==FleshStrike.EDGE.id || strike(limb)==FleshStrike.CLAW.id);}
    public boolean exposedTendon(int leg){
        if(leg<0 || broken(leg))return false;
        if(Math.abs(tilt(0))>.22F)return true;
        int foot=data().getInt("Foot");float t=actions.age(0);
        return switch(actions.move()){
            case STAB,SWEEP,POUNCE -> leg==foot && t>=18 && t<=40;
            case PINCER,SWALLOW -> leg%4==foot%4 && t>=16 && t<=40;
            case RECOIL -> true;
            default -> false;
        };
    }
    public boolean sealed(){return data().getInt("Meals")>=3 || data().getInt("Oral")>=2;}
    public CompoundTag save(){
        var t=data().copy();t.putLong("SavedClock",now());return t;
    }
    public void load(CompoundTag t){
        if(t.contains("SavedClock")){
            long delta=now()-t.getLong("SavedClock");t.remove("SavedClock");
            for(var key:java.util.List.of("NextMeal","MealStart","DragAt","SwallowAt","DownUntil","NextMove","Start","FaceTick","LastTumor","OralAt","Swallowed","BlastAt"))
                if(t.contains(key))t.putLong(key,t.getLong(key)+delta);
            for(String leg:t.getCompound("Footing").getAllKeys()){
                var step=t.getCompound("Footing").getCompound(leg);step.putLong("At",step.getLong("At")+delta);
            }
            for(int i=0;i<8;i++)for(String prefix:List.of("Break","Flinch")){
                String key=prefix+i;if(t.contains(key))t.putLong(key,t.getLong(key)+delta);
            }
        }
        boss.spiderData(t);
    }
    public boolean feeding(){return live() && data().hasUUID("Prey") && !sealed();}
    private long now(){return boss.level().getGameTime();}
    public float tilt(float partial){
        long end=data().getLong("DownUntil");
        float remaining=end-now()-partial;
        if(remaining<=0 || !live())return 0;
        return data().getInt("DownSide")*Mth.clamp(Math.min((80-remaining)/9F,remaining/14F),0,1);
    }
    public List<HydraContact.Region> skin(Vec3 origin,float partial){
        float yaw=boss.skinYaw(partial);
        if(cachedTick!=now() || cachedPartial!=partial || cachedYaw!=yaw || !origin.equals(cachedOrigin) || cachedState!=data()){
            cachedSkin=SpiderAnatomy.regions(boss,origin,partial);
            cachedTick=now();cachedPartial=partial;cachedYaw=yaw;cachedOrigin=origin;cachedState=data();
        }
        return cachedSkin;
    }
    public float fold(String joint,float partial){
        int limb=SpiderAnatomy.leg(joint);
        if(limb<0 || !broken(limb))return 0;
        float age=Math.max(0,now()-data().getLong("Break"+limb)+partial);
        var kind=FleshStrike.byId(strike(limb));
        float swing=switch(kind){
            case LASH -> SpiderGesture.ease(age,2,9)-.34F*SpiderGesture.phrase(age,0,2,2,6);
            case HAMMER -> SpiderGesture.ease(age,-.4F,3);
            case MUSCLE -> SpiderGesture.ease(age,0,6);
            default -> SpiderGesture.ease(age,0,5);
        };
        float recoil=(float)Math.sin(age*.52)*Math.max(0,1-age/18);
        float direction=(limb<4?1F:-1F)*data().getFloat("Side"+limb);
        float angle=switch(FleshStrike.byId(strike(limb))){
            case EDGE,CLAW -> .22F;
            case HAMMER -> .95F;
            case LASH -> -.78F;
            case MUSCLE -> -.94F;
            case QUILL -> .55F;
            default -> .68F;
        };
        return direction*(angle*swing+.24F*recoil)*(joint.startsWith("tibia")?1F:.38F);
    }
    private final SpiderLegPose.Pair[] legPose=new SpiderLegPose.Pair[8];
    private final boolean[] sampled=new boolean[8];
    private CompoundTag poseState;
    private float posePartial,poseYaw;
    private long poseTick=Long.MIN_VALUE;
    private Vec3 poseOrigin;
    private static final String ARM="arm_lowered", ELBOW="elbow_lowered", WRIST="wrist_lowered";
    private static final Vec3 PALM=new Vec3(-1.52,6.75,-1.33);
    public Vec3 palm(float partial){
        var matrix=jointMatrix(SpiderAnatomy.root(boss,partial),WRIST,partial);
        return boss.getPosition(partial).add(new Vec3(matrix.transformPosition(PALM.toVector3f())));
    }
    public org.joml.Quaternionf legRotation(String joint,float partial){
        if(actions.move()==SpiderActions.Move.SLAM && (joint.equals(ARM) || joint.equals(ELBOW))){
            var parent=jointMatrix(SpiderAnatomy.root(boss,partial),"thorax",partial);
            var local=new Vec3(parent.invert().transformPosition(actions.handTarget(partial).subtract(boss.getPosition(partial)).toVector3f()));
            var pose=SpiderLegPose.solve(SpiderAnatomy.PIVOTS.get(ARM),SpiderAnatomy.PIVOTS.get(ELBOW),PALM,PALM.lerp(local,actions.crouch(partial)));
            return joint.equals(ARM)?pose.hip():pose.knee();
        }
        int limb=SpiderAnatomy.leg(joint);
        if(limb<0)return SpiderIdle.mix(boss,joint,partial,SpiderGesture.upper(boss,joint,partial));
        var origin=boss.position();float yaw=boss.skinYaw(partial);
        if(now()!=poseTick || data()!=poseState || posePartial!=partial || poseYaw!=yaw || !origin.equals(poseOrigin)){
            Arrays.fill(sampled,false);poseState=data();poseTick=now();posePartial=partial;poseYaw=yaw;poseOrigin=origin;
        }
        if(!sampled[limb]){legPose[limb]=SpiderLegPose.action(boss,limb,partial);sampled[limb]=true;}
        var pair=legPose[limb];return pair==null?null:joint.startsWith("coxa")?pair.hip():pair.knee();
    }
    private Matrix4f applyJoint(Matrix4f m,String joint,float partial){
        var rotation=legRotation(joint,partial);
        if(rotation!=null){
            var p=SpiderAnatomy.PIVOTS.get(joint).toVector3f();
            return m.translate(p).rotate(rotation).translate(-p.x,-p.y,-p.z);
        }
        float angle=fold(joint,partial);
        float flex=flex(joint,partial);
        if(angle!=0 || flex!=0){
            var p=SpiderAnatomy.PIVOTS.get(joint).toVector3f();
            m.translate(p).rotateZ(angle).rotateX(flex).translate(-p.x,-p.y,-p.z);
        }
        return m;
    }
    public float flex(String joint,float partial){
        if(joint.startsWith("fang_") && data().getInt("Oral")>0){
            int i=Integer.parseInt(joint.substring(5));
            if(i==0 || i==10 || data().getInt("Oral")==2 && i==4)return .92F;
        }
        if(joint.startsWith("fang_") && feeding()){
            int index=Integer.parseInt(joint.substring(5));
            float age=now()-data().getLong("MealStart")+partial;
            float until=data().getLong("SwallowAt")-now()-partial;
            return (index<5?-.63F:.54F)*SpiderGesture.ease(age,index*.27F,index*.27F+5F)
                    *(.73F+.27F*SpiderGesture.ease(until,0,4));
        }
        if(live() && joint.startsWith("fang_") && data().contains("Swallowed")){
            float t=now()-data().getLong("Swallowed")+partial;
            if(t<10)return (joint.charAt(5)=='1'?-.14F:.16F)*SpiderGesture.phrase(t,-.5F,2,3,10);
        }
        return actions.flex(joint,partial);
    }
    public Matrix4f jointMatrix(Matrix4f root,String joint,float partial){
        var chain=new ArrayDeque<String>();
        for(String name=joint;name!=null;name=SpiderAnatomy.PARENTS.get(name))chain.push(name);
        var m=new Matrix4f(root);
        for(String name:chain)applyJoint(m,name,partial);
        return m;
    }
    public void contact(int id,DamageSource source,float amount,HydraHurtContact.Contact hit){
        if(id==SpiderAnatomy.CROWN && actions.move()==SpiderActions.Move.CATALYZE){SpiderFoley.play(boss,hit.point(),SpiderFoley.Cue.TEAR,1.1F);actions.cancel();return;}
        if(id>=SpiderAnatomy.FIRST_ROOT && id<SpiderAnatomy.FIRST_LEG && feeding()){SpiderFoley.play(boss,hit.point(),SpiderFoley.Cue.TEAR,1.1F);SpiderFoley.strands(boss,hit.point(),hit.normal(),5);interruptMeal();return;}
        int leg=SpiderAnatomy.legForRegion(id);
        if(leg<0 || broken(leg))return;
        var t=data().copy();
        float damage=amount*com.codex.blacklight.power.CombatScale.units(boss);
        float progress=t.getFloat("Wear"+leg)+damage/boss.getMaxHealth()*(SpiderAnatomy.tendon(id) && exposedTendon(leg)?2.6F:1F);
        t.putFloat("Wear"+leg,progress);
        if(progress<.075F && amount>0 && (!t.contains("Flinch"+leg) || now()-t.getLong("Flinch"+leg)>7)){
            t.putLong("Flinch"+leg,now());
            t.putFloat("Recoil"+leg,Math.min(1F,.24F+damage/boss.getMaxHealth()*18F));
        }
        if(progress>=.075F){

            var kind=hit.strike();
            if(kind==FleshStrike.GENERIC){
                kind=source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)?FleshStrike.QUILL:switch(hit.style()){
                    case CRUSH,BURST -> FleshStrike.HAMMER;
                    case PIERCE -> FleshStrike.QUILL;
                    case WHIP -> FleshStrike.LASH;
                    default -> source.getEntity() instanceof Player p && p.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem?FleshStrike.EDGE:FleshStrike.FIST;
                };
            }
            if(kind==FleshStrike.EDGE || kind==FleshStrike.CLAW)GreeneSpider.detach(boss,leg,hit.normal().scale(-1));
            if(actions.move()!=SpiderActions.Move.REST)actions.cancel();
            if(feeding())interruptMeal();
            t=data().copy();t.putFloat("Wear"+leg,progress);t.putInt("Lost",brokenMask()|(1<<leg));
            t.putInt("Strike"+leg,kind.id);t.putLong("Break"+leg,now());
            var normal=hit.normal().yRot((float)Math.toRadians(boss.skinYaw(0)-180));
            t.putFloat("Side"+leg,normal.x<0?-1:1);
            t.putInt("DownSide",leg<4?-1:1);t.putLong("DownUntil",now()+80);
            GreeneFeedback.broken(boss,hit.point());
            SpiderFoley.play(boss,hit.point(),switch(kind){
                case EDGE,CLAW,LASH -> SpiderFoley.Cue.TEAR;
                case QUILL -> SpiderFoley.Cue.NEEDLE;
                default -> SpiderFoley.Cue.FRACTURE;
            },1.65F);
            SpiderFoley.strands(boss,hit.point(),hit.normal().scale(-1),8);
            BossImpactFeedback.contact(boss,source,hit.point(),BossImpactFeedback.Contact.BREAK,amount);
        }
        boss.spiderData(t);
    }
    public static boolean evolved(Mob mob){return mob instanceof BlacklightInfectedHost host && host.isEvolved();}
    public static boolean edible(Mob mob){
        return mob instanceof BlacklightCommonInfected || mob instanceof BlacklightInfectedHost || mob instanceof BlacklightHunter
            || mob instanceof BlacklightMutant || mob instanceof BlacklightHydra;
    }
    public static boolean implant(Mob mob){
        if(!edible(mob) || !mob.isAlive() || evolved(mob) && mob.getHealth()>mob.getMaxHealth()*.35F)return false;
        if(!mob.getPersistentData().getBoolean(TUMOR)){
            mob.getPersistentData().putBoolean(TUMOR,true);
            mob.addTag(TUMOR);
            mob.playSound(net.minecraft.sounds.SoundEvents.SLIME_SQUISH,.8F,.7F);
        }
        return true;
    }
    public Vec3 mouth(float partial){
        return SpiderAnatomy.point(boss,partial,new Vec3(1.04,4.12,-2.52));
    }
    /** The deforming inner lip has an open elliptical channel. The collision boxes around
     * the individual teeth are deliberately conservative outside this aperture. */
    public record Aperture(Vec3 center,Vec3 normal,double fraction){}
    public Aperture aperture(Vec3 from,Vec3 to,Vec3 half,float partial){
        var root=SpiderAnatomy.root(boss,partial);var inverse=new Matrix4f(root).invert();var origin=boss.getPosition(partial);
        var a=new Vec3(inverse.transformPosition(from.subtract(origin).toVector3f()));
        var b=new Vec3(inverse.transformPosition(to.subtract(origin).toVector3f()));var v=b.subtract(a);
        if(v.z<=1e-5 || a.z> -2.36 || v.z*v.z<v.lengthSqr()*.18)return null;
        double plane=-2.77;
        double t=(plane-a.z)/v.z;if(t<-.2)return null;
        var hit=a.add(v.scale(t));double width=1.32-half.x*.37,height=1.84-half.y*.40;
        if(width<=.1 || height<=.1 || Math.pow((hit.x-1.04)/width,2)+Math.pow((hit.y-4.12)/height,2)>1)return null;
        var normal=new Vec3(root.transformDirection(new org.joml.Vector3f(0,0,-1))).normalize();
        return new Aperture(from.lerp(to,Math.max(0,t)),normal,Math.max(0,t));
    }
    public Vec3 visibleMouth(Player player,float partial,double range){
        var eye=player.getEyePosition();var goal=mouth(partial);
        if(eye.distanceToSqr(goal)>range*range || !CombatTargeting.clearRay(player,eye,goal))return null;
        var gap=aperture(eye,goal,Vec3.ZERO,partial);
        if(gap==null || gap.fraction()>1)return null;
        var hit=HydraHurtContact.ray(skin(boss.getPosition(partial),partial),eye,goal,com.codex.blacklight.infection.InfectedAttackStyle.PIERCE);
        if(hit==null || hit.point().distanceToSqr(gap.center())<2.3*2.3 || eye.distanceToSqr(hit.point())>=eye.distanceToSqr(gap.center()))return goal;
        return null;
    }
    private Vec3 lip(int slot,float partial){
        double side=data().getBoolean("PreyLeft")?-1:1;
        if(slot==2)side=-side;
        return SpiderAnatomy.point(boss,partial,new Vec3(1.04+side*(slot==1?1.05:1.48),4.12+(slot==1?1.68:-.20),-2.98));
    }
    public List<Vec3> rootPath(int slot,float partial){
        if(slot<0){
            if(actions.move()!=SpiderActions.Move.CATALYZE)return List.of();
            var needle=new Vec3(-7.35793,11.25851,3.34375);
            var matrix=jointMatrix(SpiderAnatomy.root(boss,partial),"streamer",partial);
            var base=boss.getPosition(partial).add(new Vec3(matrix.transformPosition(needle.toVector3f())));
            var target=boss.level().getEntity(data().getInt("ActionId"));
            if(!(target instanceof Mob host))return List.of();
            var end=host.getPosition(partial).add(0,host.getBbHeight()*.66,0);
            double t=actions.age(partial),extension=Mth.clamp((t-5)/19,0,1)*Mth.clamp((38-t)/10,0,1);
            if(extension<=0)return List.of();
            var points=new ArrayList<Vec3>(13);
            for(int i=0;i<=12;i++){
                double u=i/12D*extension;
                var lateral=new Vec3(end.z-base.z,0,base.x-end.x).normalize();
                double crest=Math.sin(Math.PI*u);
                points.add(base.lerp(end,u).add(lateral.scale(crest*(.62*Math.sin(5*u-t*.14)))).add(0,crest*(2.8+.35*Math.cos(9*u-t*.23)),0));
            }
            return points;
        }
        if(!feeding())return List.of();
        var mob=boss.level().getEntity(data().getInt("PreyId"));
        if(!(mob instanceof Mob prey) || slot>=(prey instanceof BlacklightHydra?3:1))return List.of();
        var a=lip(slot,partial);
        var b=prey.getPosition(partial).add(0,prey.getBbHeight()*.52,0);
        double side=slot==1?1:slot==2?-1:0;
        var cross=new Vec3(b.z-a.z,0,a.x-b.x).normalize();
        float age=now()-data().getLong("MealStart")+partial;
        float drag=data().getLong("DragAt")-data().getLong("MealStart");
        double extension=SpiderGesture.ease(age+1,0,drag*.69F);
        double taut=SpiderGesture.ease(age,drag*.65F,drag+4);
        var points=new ArrayList<Vec3>(13);
        for(int i=0;i<=12;i++){
            double u=i/12D*extension,arch=Math.sin(u*Math.PI);
            double wave=Math.sin(u*8-age*.34+slot)*(.22+.59*(1-taut));
            var curl=cross.scale(arch*(side*1.5+wave));
            double sag=arch*(1.8*(1-taut)-Math.min(1.2,.08*a.distanceTo(b))*taut);
            points.add(a.lerp(b,u).add(curl).add(0,sag,0));
        }
        return points;
    }
    public void addRootBoxes(int slot,float partial,List<HydraContact.Box> boxes){
        var path=rootPath(slot,partial);
        for(int i=1;i<path.size();i++){
            var a=path.get(i-1);var b=path.get(i);var delta=b.subtract(a);
            if(delta.lengthSqr()<.0001)continue;
            var z=delta.normalize();var x=z.cross(Math.abs(z.y)>.94?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
            boxes.add(HydraContact.oriented(a.lerp(b,.5),new Vec3(.24,.24,delta.length()*.5+.06),x,z.cross(x),z));
        }
    }
    private void interruptMeal(){
        var t=data().copy();t.remove("Prey");t.remove("PreyId");t.putLong("NextMeal",now()+45);boss.spiderData(t);
    }
    private record ThrownMeal(Mob prey,DamageSource source,double time){}
    private static final class Arrivals {
        Mob meal;
        final List<ThrownMeal> offers=new ArrayList<>();
    }
    private static final Map<ServerLevel,Map<SpiderPhase,Arrivals>> pending=new WeakHashMap<>();
    private Arrivals arrival(ServerLevel level){
        return pending.computeIfAbsent(level,k->new LinkedHashMap<>()).computeIfAbsent(this,k->new Arrivals());
    }
    public static void settle(ServerLevel level){
        var mouths=pending.remove(level);if(mouths==null)return;
        mouths.forEach((spider,arrivals)->{
            if(!spider.live())return;
            arrivals.offers.sort(Comparator.comparingDouble(ThrownMeal::time).thenComparingInt(o->o.prey().getId()));
            for(var hit:arrivals.offers)if(hit.prey().isAlive() && spider.live()){
                spider.thrownContact(hit.prey(),SpiderAnatomy.MOUTH,hit.source());
            }
            var food=arrivals.meal;
            if(food!=null && food.isAlive() && spider.feeding() && spider.data().getUUID("Prey").equals(food.getUUID()))
                spider.swallow(food);
        });
    }
    public static Vec3 tumorSite(int i){
        return new Vec3(i==0?1.64:i==1?.32:1.04,i==2?3.72:4.56,i==2?-1.25:-2.21);
    }
    private void swallow(Mob mob){
        var t=data().copy();boolean marked=mob.getPersistentData().getBoolean(TUMOR);
        float fraction=mealSize(mob);mob.discard();t.remove("Prey");t.remove("PreyId");
        t.putLong("Swallowed",now());
        if(marked){t.putInt("Meals",Math.min(3,t.getInt("Meals")+1));t.putLong("LastTumor",now());}
        boolean again=fraction==.02F && !t.getBoolean("QuickMeal");
        t.putBoolean("QuickMeal",again);t.putLong("NextMeal",now()+(again?6:100));boss.spiderData(t);
        boss.heal(fraction*boss.getMaxHealth()/(float)com.codex.blacklight.power.CombatScale.units(boss));SpiderFoley.play(boss,mouth(1),SpiderFoley.Cue.GULP,1.6F);

    }
    public boolean receiveThrown(Mob prey,int part,DamageSource source,double time){
        if(!live() || !evolved(prey) || !prey.isAlive() || !prey.getPersistentData().getBoolean(TUMOR))return false;
        if(part==SpiderAnatomy.MOUTH && feeding() && boss.level() instanceof ServerLevel level){
            arrival(level).offers.add(new ThrownMeal(prey,source,time));return true;
        }
        return thrownContact(prey,part,source);
    }
    private boolean thrownContact(Mob prey,int part,DamageSource source){
        if(!live() || !evolved(prey) || !prey.isAlive() || !prey.getPersistentData().getBoolean(TUMOR))return false;
        var at=prey.position();prey.discard();
        SpiderFoley.play(boss,at,SpiderFoley.Cue.RUPTURE,1.6F);
        if(part==SpiderAnatomy.MOUTH && feeding()){
            interruptMeal();var t=data().copy();t.putInt("Oral",Math.min(2,t.getInt("Oral")+1));t.putLong("OralAt",now());boss.spiderData(t);
            GreeneFeedback.broken(boss,mouth(1));
        }else{
            var region=skin(boss.position(),1).get(part);
            if(!region.boxes().isEmpty())at=region.boxes().get(0).nearest(at);
            GreeneFeedback.broken(boss,at);
            var local=new Vec3(SpiderAnatomy.root(boss,1).invert().transformPosition(at.subtract(boss.position()).toVector3f()));
            var st=data().copy();st.putInt("BlastPart",part);st.putDouble("WoundX",local.x);st.putDouble("WoundY",local.y);st.putDouble("WoundZ",local.z);
            st.putLong("BlastAt",now());boss.spiderData(st);
            var contact=new HydraHurtContact.Contact(region.bone(),at,new Vec3(0,1,0),com.codex.blacklight.infection.InfectedAttackStyle.BURST);
            boss.hurtAt(source,boss.getMaxHealth()*.13F/(float)com.codex.blacklight.power.CombatScale.units(boss),contact);
        }
        return true;
    }
    public void tick(){
        if(!live() || !(boss.level() instanceof ServerLevel level) || boss.isNoAi())return;
        if(data().getInt("Meals")==3 && data().contains("LastTumor")){
            long since=now()-data().getLong("LastTumor");
            if(since>=12 && since<=22 && (since-12)%5==0){
                int slot=(int)(since-12)/5;var at=SpiderAnatomy.point(boss,1,tumorSite(slot));
                SpiderFoley.play(boss,at,SpiderFoley.Cue.RUPTURE,.98F+slot*.23F);
                SpiderFoley.strands(boss,at,mouth(1).subtract(at),6);
                if(slot==2)GreeneFeedback.broken(boss,at);
            }
        }
        footing.tick(level);
        actions.tick(level);
        if(actions.move()!=SpiderActions.Move.REST || sealed())return;
        if(feeding()){

            var entity=level.getEntity(data().getUUID("Prey"));
            if(entity!=null && entity.getId()!=data().getInt("PreyId")){var sync=data().copy();sync.putInt("PreyId",entity.getId());boss.spiderData(sync);}
            if(!(entity instanceof Mob mob) || !mob.isAlive() || ConsumeControl.protectedEntity(mob)){interruptMeal();return;}
            var mouth=mouth(1);
            // The jaws hold the prey beside the open throat until the closing bite.
            int lastBite=mob instanceof BlacklightHydra?7:3;
            var target=now()<data().getLong("SwallowAt")-lastBite?lip(0,1):mouth;
            var delta=target.subtract(mob.getBoundingBox().getCenter());
            var barrier=level.clip(new ClipContext(mob.getBoundingBox().getCenter(),mouth,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob));
            if(barrier.getType()!=HitResult.Type.MISS || now()-data().getLong("MealStart")>130){interruptMeal();return;}
            long dragAge=now()-data().getLong("DragAt");
            if(dragAge<0)return;
            if(dragAge==0){
                SpiderFoley.play(boss,mob.getBoundingBox().getCenter(),SpiderFoley.Cue.STRAIN,mob instanceof BlacklightHydra?1.65F:1F);
                SpiderFoley.strands(boss,mob.getBoundingBox().getCenter(),delta,4);
            }
            mob.getNavigation().stop();
            mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;mob.hurtMarked=true;
            if(delta.length()>.52){
                var before=mob.position();
                double ramp=.53+.47*SpiderGesture.ease(dragAge,0,5);
                double resistance=mob instanceof BlacklightHydra?.87+.13*Math.cos(dragAge*.63):1;
                var movement=delta.normalize().scale(Math.min(1.45*ramp*resistance,delta.length()));
                if(delta.horizontalDistanceSqr()>.15){
                    float face=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
                    mob.yBodyRot=Mth.approachDegrees(mob.yBodyRot,face,6);
                }
                mob.move(net.minecraft.world.entity.MoverType.SELF,movement);
                if(mob.position().distanceToSqr(before)<.008){interruptMeal();return;}
                return;
            }
            if(now()<data().getLong("SwallowAt"))return;
            arrival(level).meal=mob;
            return;
        }
        if(boss.tickCount%5!=0 || now()<data().getLong("NextMeal") || boss.getHealth()>=boss.getMaxHealth() || tilt(0)!=0)return;
        var mouth=mouth(1);
        var candidates=level.getEntitiesOfClass(Mob.class,boss.getBoundingBox().inflate(28),m->edible(m) && m.isAlive() && !ConsumeControl.protectedEntity(m) && !ConsumeSystem.inFlight(m) && !QuillHarvest.protectedPet(m));
        candidates.sort(Comparator.comparingDouble(m->m.distanceToSqr(mouth)));
        for(var mob:candidates){
            if(level.clip(new ClipContext(mouth,mob.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss)).getType()!=HitResult.Type.MISS)continue;
            boolean quick=mealSize(mob)==.02F && mob.distanceToSqr(mouth)<12*12;
            var t=data().copy();t.putUUID("Prey",mob.getUUID());t.putInt("PreyId",mob.getId());
            var local=new Vec3(SpiderAnatomy.root(boss,1).invert().transformPosition(mob.getBoundingBox().getCenter().subtract(boss.position()).toVector3f()));
            t.putBoolean("HeavyPrey",mob instanceof BlacklightHydra);t.putBoolean("PreyLeft",local.x<1.04);t.putFloat("MealYaw",boss.skinYaw(1));
            t.putLong("MealStart",now());t.putLong("DragAt",now()+(quick?7:29));t.putLong("SwallowAt",now()+(quick?24:50));boss.spiderData(t);
            SpiderFoley.play(boss,mouth,SpiderFoley.Cue.INHALE,1.7F);break;
        }
    }
    private static float mealSize(Mob mob){
        if(mob instanceof BlacklightHydra)return .12F;
        if(mob instanceof BlacklightCommonInfected || mob instanceof BlacklightInfectedHost && !evolved(mob))return .02F;
        return .08F;
    }
}
