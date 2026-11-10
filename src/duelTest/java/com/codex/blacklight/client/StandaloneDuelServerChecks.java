package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Dedicated server, two real remote clients ("Dev" and "Duel"). The clients only send production
 * C2S packets; this class places players, keeps them alive and records what the server published. */
@EventBusSubscriber(modid="blacklight",value=Dist.DEDICATED_SERVER)
public final class StandaloneDuelServerChecks {
    static final String PROPERTY="blacklight.test.standaloneDuel";
    private static final Path OUTPUT=Path.of(System.getProperty(PROPERTY,"missing-duel-output")).toAbsolutePath().normalize();
    /** TRADE: both swing at each other. CONSUME_A/B: one consumes a villager while the other attacks it. */
    private record Case(String name,int ticks,String consumer){}
    private static final List<Case> CASES=List.of(new Case("trade",200,null),new Case("consume_dev",170,"Dev"),new Case("consume_duel",170,"Duel"));
    private static final double X=0.5,Y=201,Z=0.5;
    private static int caseIndex=-1,stageTicks,waitTicks;
    private static String stage="WAIT";
    private static boolean built,done;
    private static Mob victim;
    private static final JsonArray results=new JsonArray(),violations=new JsonArray(),trace=new JsonArray();
    private static final Map<String,Track> tracks=new HashMap<>();
    private static final class Track{
        long lastHit=-1,lastRevision=-1;int reactions,flinchCommitted,actionStarts,lockTicks,maxLock,attackTicks;
        boolean consumeSeen,consumeEnded,consumeCancelled;int consumeHitsTaken,flinchConsuming,hitsTaken;float healthLost;
    }
    private StandaloneDuelServerChecks(){}
    private static int haltIn=-1;
    private static boolean active(){return !System.getProperty(PROPERTY,"").isBlank()&&!done;}
    /** Clients write their reports on DONE; give them five seconds before the server stops. */
    @SubscribeEvent public static void halt(ServerTickEvent.Post event){if(haltIn>0&&--haltIn==0)event.getServer().halt(false);}
    private static ServerPlayer player(MinecraftServer s,String name){return s.getPlayerList().getPlayerByName(name);}
    private static void say(MinecraftServer s,String text){s.getPlayerList().broadcastSystemMessage(Component.literal("[duel] "+text),false);}
    private static void violation(String text){violations.add(text);BlacklightNeoMod.LOGGER.warn("Duel QA violation: {}",text);}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!active())return;var server=event.getServer();
        try{
            var a=player(server,"Dev");var b=player(server,"Duel");
            if(stage.equals("WAIT")){
                if(a==null||b==null){if(++waitTicks>20*240){violation("both clients never joined");finish(server);}return;}
                // Both clients finish chunk meshing and renderer warmup before the first case.
                if(++waitTicks<20*12)return;
                nextCase(server,a,b);return;
            }
            if(a==null||b==null){violation("a client left during "+stage);finish(server);return;}
            stageTicks++;
            var c=CASES.get(caseIndex);
            for(var p:List.of(a,b)){p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(20);}
            if(stage.equals("SETUP")){
                if(stageTicks==2)place(server,a,b,c);
                if(stageTicks>=30){stage="RUN";stageTicks=0;say(server,"RUN "+c.name()+" "+(c.consumer()==null?"-":c.consumer()));}
                return;
            }
            if(stage.equals("RUN")){
                observe(a,c);observe(b,c);
                // Knockback separates the duelists; keep the attacker in reach so every case keeps trading blows.
                if(c.consumer()==null&&stageTicks%20==0&&a.distanceTo(b)>3.2)place(server,a,b,c);
                if(c.consumer()!=null){
                    ServerPlayer consumer=c.consumer().equals("Dev")?a:b,attacker=consumer==a?b:a;
                    var ct=tracks.get(c.consumer());
                    // Until the grab lands, hold the scene still: the consumer stays in reach of the victim
                    // and the attacker waits out of range, so only the grab itself is under test.
                    if(!ct.consumeSeen){
                        if(Math.hypot(consumer.getX()-X,consumer.getZ()-Z)>.3)teleport(consumer,X,Z,0);
                        if(attacker.distanceTo(consumer)<5)park(attacker);
                    }else if(stageTicks%10==0&&attacker.distanceTo(consumer)>3.0)placeAttacker(consumer,attacker);
                    // Clients send GRAB on run tick 5, 25, 45...; record what that request sees one tick later.
                    if(stageTicks%20==7&&trace.size()<12){
                        var m=consumer.getData(ModAttachments.MOVEMENT_STATE);var h=consumer.getData(ModAttachments.HIT_REACTION);
                        trace.add(c.name()+" t"+stageTicks+" claw="+consumer.getData(ModAttachments.CLAW_COMBAT).action()+" weaponBusy="+WeaponCombatSystem.busy(consumer)
                                +" charge="+m.chargeTicks()+" dash="+m.dashTicks()+" wall="+m.wallTraversal().attached()+" ground="+consumer.onGround()
                                +" hit="+(h.duration()>0&&consumer.level().getGameTime()<h.started()+h.duration())+" active="+ConsumeSystem.active(consumer)
                                +" victim="+(victim==null?"none":String.format(java.util.Locale.ROOT,"%.2f hp=%.1f",consumer.distanceTo(victim),victim.getHealth()))
                                +" yaw="+Math.round(consumer.getYRot())+" "+grabProbe(consumer));
                    }
                }
                if(stageTicks>=c.ticks())endCase(server,a,b,c);
            }
        }catch(Exception e){violation("server exception: "+e);BlacklightNeoMod.LOGGER.error("Duel QA",e);finish(server);}
    }
    private static void nextCase(MinecraftServer server,ServerPlayer a,ServerPlayer b){
        caseIndex++;
        if(caseIndex>=CASES.size()){finish(server);return;}
        tracks.clear();tracks.put("Dev",new Track());tracks.put("Duel",new Track());
        stage="SETUP";stageTicks=0;say(server,"SETUP "+CASES.get(caseIndex).name());
    }
    private static void place(MinecraftServer server,ServerPlayer a,ServerPlayer b,Case c){
        ServerLevel level=server.overworld();
        if(!built){
            level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
            for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){
                level.setBlockAndUpdate(new BlockPos(x,200,z),((x+z)%5==0?Blocks.WHITE_CONCRETE:Blocks.GRAY_CONCRETE).defaultBlockState());
                for(int y=201;y<=206;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }
            built=true;
        }
        for(var p:List.of(a,b)){
            ConsumeSystem.cancel(p);
            var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.CLAWS);state.activate(PowerForm.CLAWS);state.addBiomass(1000);
            p.setData(ModAttachments.PLAYER_STATE,state);p.syncData(ModAttachments.PLAYER_STATE);
            p.setData(ModAttachments.HIT_REACTION,HitReaction.EMPTY);p.syncData(ModAttachments.HIT_REACTION);
            p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.removeAllEffects();
            CombatTargeting.requestLock(p,-1);
        }
        if(victim!=null){victim.discard();victim=null;}
        if(c.consumer()==null){
            teleport(a,X,Z,0);teleport(b,X,Z+2.2,180);
        }else{
            ServerPlayer consumer=c.consumer().equals("Dev")?a:b,attacker=consumer==a?b:a;
            teleport(consumer,X,Z,0);
            // Logs who removes the victim; a discarded victim makes every grab report "no valid target".
            victim=new net.minecraft.world.entity.npc.Villager(EntityType.VILLAGER,level){
                @Override public void remove(RemovalReason reason){
                    if(!isRemoved()&&victim==this&&!done){var where=new Throwable("victim removed: "+reason);BlacklightNeoMod.LOGGER.warn("Duel QA",where);
                        if(trace.size()<30)trace.add("victim removed "+reason+" in "+stage+" t"+stageTicks+" by "+java.util.Arrays.stream(where.getStackTrace()).skip(1).limit(6).map(StackTraceElement::toString).toList());}
                    super.remove(reason);
                }
            };
            victim.moveTo(X,Y,Z+1.7,180,0);victim.setNoAi(true);victim.setPersistenceRequired();level.addFreshEntity(victim);
            park(attacker);
        }
    }
    /** Each condition ConsumeSystem.request checks for a direct grab, evaluated on the live victim. */
    private static String grabProbe(ServerPlayer p){
        if(victim==null)return "";
        try{
            var cs=ConsumeSystem.class;
            var aim=cs.getDeclaredMethod("grabAim",ServerPlayer.class,Mob.class);aim.setAccessible(true);
            var sight=cs.getDeclaredMethod("hasGrabSight",ServerPlayer.class,Mob.class);sight.setAccessible(true);
            var reach=cs.getDeclaredMethod("grabInReach",ServerPlayer.class,Mob.class,double.class);reach.setAccessible(true);
            var near=p.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,p.getBoundingBox().inflate(4),e->e!=p&&!(e instanceof ServerPlayer)).stream()
                    .map(e->e.getType().toShortString()+"#"+e.getId()+(e==victim?"(victim)":"")).toList();
            return "removed="+victim.getRemovalReason()+" alive="+victim.isAlive()+" invulnerable="+victim.isInvulnerable()+" near="+near
                    +" visual="+ModAttachments.peekData(victim,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY).active()
                    +" camYaw="+Math.round(com.codex.blacklight.movement.TraversalSteering.cameraYaw(p))+" eligible="+ConsumeSystem.eligible(victim)
                    +" selectable="+CombatTargeting.selectable(p,victim)+" reach="+reach.invoke(null,p,victim,3.0)+" sight="+sight.invoke(null,p,victim)
                    +" aim="+String.format(java.util.Locale.ROOT,"%.2f",(double)aim.invoke(null,p,victim))+" health="+ConsumeSystem.healthReady(p,victim)
                    +" prototype="+p.getData(ModAttachments.PLAYER_STATE).isPrototypeMode()+" form="+p.getData(ModAttachments.PLAYER_STATE).getActivePower();
        }catch(ReflectiveOperationException e){return "probe "+e;}
    }
    private static void park(ServerPlayer attacker){teleport(attacker,X-6,Z-1,-90);}
    /** Behind-left of the consumer, facing it: the attacker's swings land on the consumer, not the victim. */
    private static void placeAttacker(ServerPlayer consumer,ServerPlayer attacker){teleport(attacker,X-1.4,Z-1.4,-45);}
    private static void teleport(ServerPlayer p,double x,double z,float yaw){
        p.connection.teleport(x,Y,z,yaw,0);p.setDeltaMovement(Vec3.ZERO);p.setYHeadRot(yaw);p.yBodyRot=yaw;
        p.getData(ModAttachments.MOVEMENT_STATE).setCameraYaw(yaw);
    }
    private static void observe(ServerPlayer p,Case c){
        var t=tracks.get(p.getGameProfile().getName());long now=p.level().getGameTime();
        var hit=p.getData(ModAttachments.HIT_REACTION);
        boolean committed=WeaponCombatSystem.busy(p)||ConsumeSystem.active(p)||com.codex.blacklight.power.BiomassSystem.active(p)
                ||p.getData(ModAttachments.CLAW_COMBAT).action()!=ClawCombatState.Action.IDLE;
        if(p.getData(ModAttachments.CLAW_COMBAT).action()!=ClawCombatState.Action.IDLE)t.attackTicks++;
        if(hit.duration()>0&&hit.started()!=t.lastHit&&hit.started()>=now-1){
            t.lastHit=hit.started();t.reactions++;
            // The reaction was published this tick; committed is the state the impact saw.
            var at=IMPACT.get(p.getUUID());boolean wasCommitted=at!=null&&at.time()==hit.started()?at.committed():committedBefore(p);
            if(wasCommitted){t.flinchCommitted++;if(trace.size()<24)trace.add(c.name()+" flinch on "+p.getGameProfile().getName()+" t"+stageTicks+" at impact: "+(at==null?"no damage event":at.detail()));}
            if(ConsumeSystem.active(p))t.flinchConsuming++;
        }
        boolean locked=hit.duration()>0&&now<hit.started()+hit.duration();
        t.lockTicks=locked?t.lockTicks+1:0;t.maxLock=Math.max(t.maxLock,t.lockTicks);
        var action=p.getData(ModAttachments.PLAYER_ACTION);
        if(!action.clipId().isEmpty()&&action.revision()!=t.lastRevision){t.lastRevision=action.revision();t.actionStarts++;}
        if(c.consumer()!=null&&c.consumer().equals(p.getGameProfile().getName())){
            boolean session=ConsumeSystem.active(p);
            if(session)t.consumeSeen=true;
            if(t.consumeSeen&&!session&&!t.consumeEnded){t.consumeEnded=true;t.consumeCancelled=victim!=null&&victim.isAlive();}
        }
        COMMITTED_PREVIOUS.put(p.getUUID(),committed);
    }
    private static final Map<UUID,Boolean> COMMITTED_PREVIOUS=new HashMap<>();
    private record Impact(long time,boolean committed,String detail){}
    private static final Map<UUID,Impact> IMPACT=new HashMap<>();
    /** The state the impact actually saw, captured when the damage arrives rather than a tick late. */
    @SubscribeEvent public static void incoming(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event){
        if(!active()||!(event.getEntity() instanceof ServerPlayer p))return;
        var track=stage.equals("RUN")?tracks.get(p.getGameProfile().getName()):null;
        if(track!=null){track.hitsTaken++;if(ConsumeSystem.active(p))track.consumeHitsTaken++;}
        boolean committed=WeaponCombatSystem.busy(p)||ConsumeSystem.active(p)||com.codex.blacklight.power.BiomassSystem.active(p)
                ||p.getData(ModAttachments.CLAW_COMBAT).action()!=ClawCombatState.Action.IDLE;
        var action=p.getData(ModAttachments.PLAYER_ACTION);
        IMPACT.put(p.getUUID(),new Impact(p.level().getGameTime(),committed,"claw="+p.getData(ModAttachments.CLAW_COMBAT).action()
                +" clip="+action.clipId()+" clipActive="+action.active(p.level().getGameTime())+" consume="+ConsumeSystem.active(p)));
    }
    private static boolean committedBefore(ServerPlayer p){return COMMITTED_PREVIOUS.getOrDefault(p.getUUID(),false);}
    private static void endCase(MinecraftServer server,ServerPlayer a,ServerPlayer b,Case c){
        var r=new JsonObject();r.addProperty("case",c.name());
        for(var name:List.of("Dev","Duel")){
            var t=tracks.get(name);var o=new JsonObject();
            o.addProperty("reactions",t.reactions);o.addProperty("flinchWhileCommitted",t.flinchCommitted);o.addProperty("flinchWhileConsuming",t.flinchConsuming);
            o.addProperty("maxLockTicks",t.maxLock);o.addProperty("hitsTaken",t.hitsTaken);o.addProperty("hitsTakenWhileConsuming",t.consumeHitsTaken);o.addProperty("actionStarts",t.actionStarts);o.addProperty("attackTicks",t.attackTicks);
            o.addProperty("consumeSeen",t.consumeSeen);o.addProperty("consumeEnded",t.consumeEnded);o.addProperty("consumeCancelled",t.consumeCancelled);
            r.add(name,o);
            if(t.flinchCommitted>0)violation(c.name()+": "+name+" was flinched "+t.flinchCommitted+" times during its own committed action");
            if(t.flinchConsuming>0)violation(c.name()+": "+name+" was flinched "+t.flinchConsuming+" times while consuming");
            if(t.maxLock>PlayerPoise.HEAVY_TICKS)violation(c.name()+": "+name+" stayed in hit reaction "+t.maxLock+" ticks in a row");
        }
        if(c.consumer()==null){
            var ta=tracks.get("Dev");var tb=tracks.get("Duel");
            if(ta.attackTicks<40||tb.attackTicks<40)violation("trade: a player could not keep attacking (Dev "+ta.attackTicks+", Duel "+tb.attackTicks+" attack ticks)");
            if(ta.hitsTaken+tb.hitsTaken==0)violation("trade: no hit ever landed between the players");
        }else{
            var t=tracks.get(c.consumer());var other=tracks.get(c.consumer().equals("Dev")?"Duel":"Dev");
            if(!t.consumeSeen)violation(c.name()+": consume never started");
            else if(!t.consumeEnded)violation(c.name()+": consume never finished");
            else if(t.consumeCancelled)violation(c.name()+": consume was interrupted");
            if(other.attackTicks<20)violation(c.name()+": attacker never attacked");
            else if(t.consumeSeen&&t.consumeHitsTaken==0)violation(c.name()+": the consumer was never hit while consuming, so interruption was not tested");
        }
        results.add(r);say(server,"END "+c.name());
        nextCase(server,a,b);
    }
    private static void finish(MinecraftServer server){
        done=true;
        try{
            Files.createDirectories(OUTPUT);var report=new JsonObject();
            report.addProperty("source","dedicated server, two real remote clients sending production ClawActionPayload packets");
            report.addProperty("passed",violations.isEmpty()&&results.size()==CASES.size());
            report.add("results",results);report.add("violations",violations);report.add("trace",trace);
            Files.writeString(OUTPUT.resolve("duel-server.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception e){BlacklightNeoMod.LOGGER.error("Duel QA report",e);}
        say(server,"DONE");haltIn=100;
    }
}