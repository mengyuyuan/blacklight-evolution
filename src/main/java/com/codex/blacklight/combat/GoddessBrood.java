package com.codex.blacklight.combat;

import com.codex.blacklight.entity.BlacklightCommonInfected;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Count live arrivals from the entire cave before admitting the next rank. */
final class GoddessBrood {
    private final EncounterMob boss;
    GoddessBrood(EncounterMob boss){this.boss=boss;}
    private long nextCall,nextStrongCall;
    private int callId=-1,commonRemaining,evolvedRemaining,evolvedCap=4;
    private int rearCommon,rearEvolved,leaderSequence=-1,leaderId=-1;
    private UUID leader;
    private boolean issued;
    boolean issued(int sequence){return callId==sequence && issued;}
    private void nominate(Mob mob){
        if(leader!=null || !SpiderPhase.reservedForThrow(mob))return;
        leader=mob.getUUID();leaderId=mob.getId();boss.goddess.publish();
    }
    boolean orderBroken(ServerLevel level,int sequence){
        if(leaderSequence!=sequence || leader==null)return false;
        var entity=level.getEntity(leader);
        if(entity instanceof Mob mob && mob.isAlive() && !ImpactControl.knockedDown(mob) && available(mob)){
            if(mob.getId()!=leaderId){leaderId=mob.getId();boss.goddess.publish();}
            return false;
        }
        for(var m:population(level)){
            if(ours(m) && available(m) && m.getPersistentData().getInt("GoddessOrder")==sequence)
                pacify(m,level.getGameTime()+25);
        }
        for(var organ:level.getEntitiesOfClass(com.codex.blacklight.entity.GoddessEmplacement.class,
                boss.getBoundingBox().inflate(120),e->e.fromOrder(boss,sequence)))organ.discard();
        SpiderFoley.play(boss,entity==null?boss.getEyePosition():entity.position(),SpiderFoley.Cue.TEAR,1.35F);
        return true;
    }
    private static int ordinaryCap(boolean strong){return strong?24:16;}
    private static int eliteCap(boolean strong){return strong?6:4;}
    boolean ready(boolean strong){return boss.level().getGameTime()>=(strong?nextStrongCall:nextCall);}
    void saveCall(net.minecraft.nbt.CompoundTag t){
        t.putLong("NextCall",nextCall);t.putLong("NextStrongCall",nextStrongCall);
        t.putInt("CallId",callId);t.putInt("CommonRemaining",commonRemaining);
        t.putBoolean("CallIssued",issued);
        t.putInt("LeaderSequence",leaderSequence);t.putInt("LeaderId",leaderId);
        if(leader!=null)t.putUUID("WaveLeader",leader);
        t.putInt("RearCommon",rearCommon);t.putInt("RearEvolved",rearEvolved);
        t.putInt("EvolvedRemaining",evolvedRemaining);t.putInt("EvolvedCap",evolvedCap);
    }
    void loadCall(net.minecraft.nbt.CompoundTag t){
        nextCall=t.getLong("NextCall");nextStrongCall=t.getLong("NextStrongCall");
        callId=t.contains("CallId")?t.getInt("CallId"):-1;
        commonRemaining=Math.max(0,Math.min(24,t.getInt("CommonRemaining")));
        evolvedRemaining=Math.max(0,Math.min(6,t.getInt("EvolvedRemaining")));
        issued=t.contains("CallIssued")?t.getBoolean("CallIssued"):t.getBoolean("Paid");
        leaderSequence=t.contains("LeaderSequence")?t.getInt("LeaderSequence"):-1;
        leader=t.hasUUID("WaveLeader")?t.getUUID("WaveLeader"):null;
        leaderId=-1;
        rearCommon=t.contains("RearCommon")?Math.max(0,Math.min(commonRemaining,t.getInt("RearCommon"))):commonRemaining/2;
        rearEvolved=t.contains("RearEvolved")?Math.max(0,Math.min(evolvedRemaining,t.getInt("RearEvolved"))):evolvedRemaining/2;
        evolvedCap=t.getInt("EvolvedCap")==6?6:4;
    }
    static boolean canEvolve(ServerLevel level,BlacklightInfectedHost host){
        var mothers=level.getEntitiesOfClass(EncounterMob.class,host.getBoundingBox().inflate(95),b->b.goddess.active()
                && b.getBoundingBox().inflate(83).contains(host.position()));
        for(var mother:mothers){
            var brood=mother.goddess.attacks.brood;
            int cap=switch(mother.goddess.attacks.move()){
                case STRONG_CALL -> 6;
                case CALL -> 4;
                default -> brood.evolvedCap;
            };
            if(level.getEntitiesOfClass(BlacklightInfectedHost.class,mother.getBoundingBox().inflate(83),
                    m->m.isAlive() && !QuillHarvest.protectedPet(m) && SpiderPhase.reservedForThrow(m)).size()>=cap)return false;
        }
        return true;
    }
    void signal(ServerLevel level,ServerPlayer p,boolean strong){
        Vec3 crown=boss.hurtRegions(0).get(11).bounds().getCenter();
        leader=null;leaderId=-1;leaderSequence=boss.goddess.attacks.sequence();
        int ordinary=0,elite=0;
        for(var m:population(level)){
            if(!available(m) || m.isNoAi() || m.distanceToSqr(p)>55*55 || !m.hasLineOfSight(p))continue;
            if(SpiderPhase.reservedForThrow(m)){if(elite>=eliteCap(strong))continue;elite++;}
            else {if(ordinary>=ordinaryCap(strong))continue;ordinary++;}
            nominate(m);
            pacify(m,level.getGameTime());
            m.getPersistentData().putLong("GoddessReleaseAt",level.getGameTime()+18);
            m.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,crown);
            SpiderFoley.strands(boss,m.getEyePosition(),new Vec3(0,.9,0),2);
        }
        SpiderFoley.play(boss,crown,strong?SpiderFoley.Cue.RUPTURE:SpiderFoley.Cue.STRAIN,strong?1.5F:1.1F);
    }
    private List<Mob> population(ServerLevel level){
        return level.getEntitiesOfClass(Mob.class,boss.getBoundingBox().inflate(83),
            m -> m.isAlive() && SpiderPhase.edible(m) && !QuillHarvest.protectedPet(m));
    }
    private boolean ours(Mob m){
        var t=m.getPersistentData();
        return t.hasUUID("EncounterOwner") && t.getUUID("EncounterOwner").equals(boss.getUUID());
    }
    private boolean available(Mob m){
        return !ConsumeControl.protectedEntity(m) && !ConsumeSystem.inFlight(m)
                && (!m.getPersistentData().hasUUID("EncounterOwner") || ours(m));
    }
    private Vec3 floor(ServerLevel level,Vec3 point){
        var h=level.clip(new ClipContext(point.add(0,7,0),point.add(0,-50,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
        if(h.getType()!=HitResult.Type.BLOCK || h.getDirection()!=net.minecraft.core.Direction.UP || !level.getFluidState(h.getBlockPos().above()).isEmpty()) return null;
        return h.getLocation().add(0,.01,0);
    }
    private Mob spawn(ServerLevel level,ServerPlayer p,boolean elite,boolean food,int seed,double heading){
        double radius=food?4.0:15+Math.floorMod(seed,11);
        Vec3 start=food?p.position():boss.position().lerp(p.position(),.55);
        Vec3 destination=food?null:floor(level,p.position());
        if(!food && destination==null)return null;
        for(int i=0;i<12;i++){
            double a=food?seed*.81+i*2.40:heading+Math.sin(seed*.81+i*2.40)*.80;
            var feet=floor(level,start.add(radius*Math.cos(a),0,radius*Math.sin(a)));
            if(feet==null || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(feet)))continue;
            Mob m=elite?ModEntities.INFECTED_HOST.get().create(level):ModEntities.COMMON_INFECTED.get().create(level);
            if(m==null) return null;
            m.moveTo(feet.x,feet.y,feet.z,(float)Math.toDegrees(a),0);
            if(!level.noCollision(m) || level.containsAnyLiquid(m.getBoundingBox()))continue;
            m.setOnGround(true);
            if(food && !walkingRoute(p,m))continue;
            if(!food){
                var path=m.getNavigation().createPath(net.minecraft.core.BlockPos.containing(destination),1);
                if(path==null || !path.canReach())continue;
            }
            m.getPersistentData().putUUID("EncounterOwner",boss.getUUID());
            if(!level.addFreshEntity(m))return null;
            if(food){
                if(!ConsumeSystem.healthReady(p,m))m.setHealth(Math.max(1,m.getMaxHealth()*.20F));
                if(!ConsumeSystem.eligible(m) || !ConsumeSystem.healthReady(p,m)){m.discard();continue;}
            }
            if(elite && !((BlacklightInfectedHost)m).beginGreeneEvolution()){m.discard();return null;}
            if(!food)m.setTarget(p);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE,feet.x,feet.y+.7,feet.z,9,.32,.45,.32,.03);
            return m;
        }
        return null;
    }
    boolean reinforce(ServerLevel level,ServerPlayer target,boolean strong,int rank,int sequence){
        var brood=population(level);
        int eliteCount=0,ordinary=0;
        for(var m:brood){if(SpiderPhase.reservedForThrow(m))eliteCount++;else ordinary++;}
        int eliteCap=eliteCap(strong),ordinaryCap=ordinaryCap(strong);
        int participants=(int)level.players().stream().filter(p->p.isAlive() && !p.isCreative() && !p.isSpectator()
                && p.distanceToSqr(boss)<100*100).count();
        ordinaryCap=Math.max(0,ordinaryCap-Math.min(ordinaryCap,participants));
        if(callId!=sequence){
            callId=sequence;issued=false;
            commonRemaining=Math.max(0,ordinaryCap-ordinary);
            evolvedRemaining=Math.max(0,eliteCap-eliteCount);
            rearCommon=strong?commonRemaining/2:0;
            rearEvolved=strong?evolvedRemaining/2:0;
        }
        double approach=Math.atan2(target.getZ()-boss.getZ(),target.getX()-boss.getX())+Math.PI*.55;
        if(strong && rank>=3)approach+=Math.PI;
        boolean any=false;
        int orderedCommon=0,orderedElite=0;
        for(var m:brood){
            if(!ours(m) || m.getPersistentData().getInt("GoddessOrder")!=sequence)continue;
            if(SpiderPhase.reservedForThrow(m))orderedElite++;else orderedCommon++;
        }
        for(var m:brood){
            if(!available(m) || m.getPersistentData().getInt("GoddessOrder")==sequence
                    || ours(m) && m.getTarget()==target && m.getPersistentData().contains("GoddessOrder"))continue;
            if(m.distanceToSqr(target)>55*55 || !m.hasLineOfSight(target))continue;
            if(m.isNoAi() && !m.getPersistentData().contains("GoddessReleaseAt"))continue;
            if(SpiderPhase.reservedForThrow(m)){if(orderedElite>=eliteCap)continue;orderedElite++;}
            else {if(orderedCommon>=ordinaryCap)continue;orderedCommon++;}
            m.getPersistentData().putUUID("EncounterOwner",boss.getUUID());
            m.getPersistentData().putInt("GoddessOrder",sequence);
            m.setTarget(target);
            nominate(m);
            any=true;
        }
        int seed=rank+sequence*17;
        boolean front=strong && rank<3;
        int availableCommon=Math.max(0,commonRemaining-(front?rearCommon:0));
        int ranksLeft=(strong?(front?3:6):4)-rank;
        int batch=Math.min(4,(availableCommon+ranksLeft-1)/ranksLeft);
        for(int i=0;i<batch && ordinary<ordinaryCap;i++) {
            var m=spawn(level,target,false,false,seed+i,approach);
            if(m!=null){ordinary++;commonRemaining--;any=true;m.getPersistentData().putInt("GoddessOrder",sequence);}
        }
        if(eliteCount<eliteCap && evolvedRemaining>(front?rearEvolved:0)){
            var m=spawn(level,target,true,false,seed+11,approach);
            if(m!=null){nominate(m);evolvedRemaining--;any=true;m.getPersistentData().putInt("GoddessOrder",sequence);}
        }
        if(any){
            issued=true;
            if(ready(strong)){
                if(strong)nextStrongCall=level.getGameTime()+900;
                else nextCall=level.getGameTime()+360;
            }
            evolvedCap=eliteCap;
            SpiderFoley.play(boss,boss.position().add(0,5,0),SpiderFoley.Cue.SHED,1.1F);
        }
        return any;
    }
    // The offer is bounded even if no walkable surface exists. An uptake already in progress
    // keeps its normal settlement and release grace.
    static final int SEARCH_TICKS=30, OFFER_TICKS=48;
    private static boolean offering(int age,int readyAt) {
        return readyAt<0 ? age<=SEARCH_TICKS : age-readyAt<OFFER_TICKS && age<SEARCH_TICKS+OFFER_TICKS;
    }
    private static final class Meal {
        UUID id;
        int readyAt=-1,holdAt=-1,settledAt=-1;
        boolean claimed,spent;
    }
    private final Map<UUID,Meal> meals=new HashMap<>();
    void clearMeals(){meals.clear();}
    net.minecraft.nbt.ListTag saveMeals(){
        var tags=new net.minecraft.nbt.ListTag();
        for(var entry:meals.entrySet()){
            var m=entry.getValue();
            var t=new net.minecraft.nbt.CompoundTag();
            t.putUUID("Player",entry.getKey());
            if(m.id!=null)t.putUUID("Prey",m.id);
            t.putInt("Ready",m.readyAt);t.putInt("Held",m.holdAt);t.putInt("Settled",m.settledAt);
            t.putBoolean("Claimed",m.claimed);t.putBoolean("Spent",m.spent);
            tags.add(t);
        }
        return tags;
    }
    void loadMeals(net.minecraft.nbt.ListTag tags){
        clearMeals();
        for(int i=0;i<tags.size();i++){
            var t=tags.getCompound(i);
            if(!t.hasUUID("Player"))continue;
            var m=new Meal();
            m.id=t.hasUUID("Prey")?t.getUUID("Prey"):null;
            m.readyAt=t.getInt("Ready");m.holdAt=t.getInt("Held");m.settledAt=t.getInt("Settled");
            m.claimed=t.getBoolean("Claimed");m.spent=t.getBoolean("Spent");
            meals.put(t.getUUID("Player"),m);
        }
    }
    /** The normal settlement already paid. This only starts the victim's release grace. */
    static void settled(ServerPlayer player,Mob prey){
        var t=prey.getPersistentData();
        if(!t.hasUUID("GoddessMealOwner"))return;
        if(!(player.serverLevel().getEntity(t.getUUID("GoddessMealOwner")) instanceof EncounterMob mother))return;
        var attacks=mother.goddess.attacks;
        if(!mother.goddess.active() || attacks.move()!=GoddessAttacks.Move.BREATHER)return;
        var m=attacks.brood.meals.get(player.getUUID());
        if(m!=null && m.claimed && !m.spent && prey.getUUID().equals(m.id)){
            m.settledAt=(int)attacks.age(0);m.spent=true;
        }
    }
    private boolean reachableMeal(ServerPlayer p,Mob m){
        if(!(m instanceof BlacklightCommonInfected) || !available(m) || !ConsumeSystem.eligible(m)
                || !ConsumeSystem.healthReady(p,m))return false;
        return walkingRoute(p,m);
    }
    private boolean walkingRoute(ServerPlayer p,Mob m){
        if(m.distanceToSqr(p)>49 || !m.hasLineOfSight(p))return false;
        if(m.distanceToSqr(p)<6.25 && Math.abs(m.getY()-p.getY())<1.4)return true;
        var path=m.getNavigation().createPath(p,1);
        if(path==null || !path.canReach() || path.getNodeCount()>13)return false;
        var end=path.getEndNode();
        return end!=null && p.position().distanceToSqr(new Vec3(end.x+.5,end.y,end.z+.5))<=6.25;
    }
    private boolean pending(ServerLevel level,ServerPlayer player,Meal m,int age){
        Mob prey=m.id!=null && level.getEntity(m.id) instanceof Mob mob ? mob:null;
        int stage=prey==null?ConsumeVisual.NONE:ConsumeSystem.mealStage(player,prey);
        if(m.settledAt>=0)return age<m.settledAt+15;
        if(m.spent)return false;
        if(stage==ConsumeVisual.NONE){
            if(m.claimed)m.spent=true;
            return false;
        }
        if(m.readyAt<0 || !m.claimed && !offering(age,m.readyAt))return false;
        m.claimed=true;
        if(ConsumeVisual.acquiring(stage) || ConsumeVisual.consumeStage(stage)){
            var visual=com.codex.blacklight.registry.ModAttachments.peekData(prey,
                    com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY);
            if(visual.consumerId()==player.getId() && level.getGameTime()-visual.started()
                    <= visual.duration()+Math.max(0,visual.landingDelay())+15L)return true;
            // A stalled/disconnected actor cannot keep the entire encounter in recovery.
            m.spent=true;
            return false;
        }
        if(ConsumeVisual.holding(stage)){
            if(m.holdAt<0)m.holdAt=age;
            if(age-m.holdAt<15)return true;
        }
        m.spent=true;
        return false;
    }
    boolean rest(ServerLevel level,int age){
        var players=level.players().stream().filter(p->p.isAlive() && !p.isSpectator() && !p.isCreative()
                && p.distanceToSqr(boss)<=100*100).toList();
        meals.keySet().removeIf(id->players.stream().noneMatch(p->p.getUUID().equals(id)));
        boolean refresh=age==1 || age%10==0;
        var brood=refresh?population(level):List.<Mob>of();
        long now=level.getGameTime();
        if(refresh)for(var m:brood)if(ours(m) && available(m))pacify(m,now);
        Set<UUID> assigned=new HashSet<>();
        for(var m:meals.values())if(m.id!=null)assigned.add(m.id);
        boolean finished=true;
        for(var p:players){
            var m=meals.computeIfAbsent(p.getUUID(),id->new Meal());
            boolean busy=pending(level,p,m,age);
            boolean wounded=p.getHealth()<p.getMaxHealth();
            if(refresh && wounded && !m.claimed && !m.spent && offering(age,m.readyAt)){
                Mob prey=m.id!=null && level.getEntity(m.id) instanceof Mob old && reachableMeal(p,old)?old:null;
                if(prey==null){
                    if(m.id!=null)assigned.remove(m.id);
                    m.id=null;
                    prey=brood.stream().filter(candidate->!assigned.contains(candidate.getUUID()) && reachableMeal(p,candidate))
                            .min(Comparator.comparingDouble(candidate->Math.abs(p.distanceTo(candidate)-5))).orElse(null);
                    if(prey==null && brood.stream().filter(candidate->!SpiderPhase.reservedForThrow(candidate)).count()<24){
                        prey=spawn(level,p,false,true,p.getId()+age/10,0);
                        if(prey!=null)brood.add(prey);
                    }
                    if(prey!=null && reachableMeal(p,prey)){
                        m.id=prey.getUUID();
                        prey.getPersistentData().putUUID("GoddessMealOwner",boss.getUUID());
                        assigned.add(m.id);
                        if(m.readyAt<0)m.readyAt=age;
                        SpiderFoley.play(boss,prey.position(),SpiderFoley.Cue.BRACE,.9F);
                    }
                }
                if(prey!=null && m.id!=null)pacify(prey,now);
            }
            if(busy || wounded && !m.spent && offering(age,m.readyAt))finished=false;
        }
        return finished && age>=16;
    }
    private static void pacify(Mob mob,long now){
        var t=mob.getPersistentData();
        if(mob.isNoAi()&&!t.contains("GoddessReleaseAt"))return;
        mob.setTarget(null);mob.getNavigation().stop();mob.setNoAi(true);
        t.putLong("GoddessReleaseAt",now+15);
    }
    public static void release(Mob mob){
        var t=mob.getPersistentData();
        if(t.contains("GoddessReleaseAt") && mob.level().getGameTime()>=t.getLong("GoddessReleaseAt")){
            t.remove("GoddessReleaseAt");
            t.remove("PounceCaller");
            if(!ConsumeControl.protectedEntity(mob))mob.setNoAi(false);
            else t.putLong("GoddessReleaseAt",mob.level().getGameTime()+5);
        }
    }
}
