package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.combat.ConsumeSystem;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.combat.HitReaction;
import com.codex.blacklight.network.ClawActionPayload;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.*;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Opt-in real-client consume QA on the disposable QA save. Each case grabs and consumes a real
 * target through the client payload path while optional attackers hit, explode next to and
 * stagger the actor. Measures, per rendered frame, whether actor/victim/camera move smoothly
 * (no tick-rate stepping, no snaps, no hitches) and whether anything cancelled the scene.
 */
@EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT)
public final class StandaloneConsumeSmoke {
    private static final String PROPERTY="blacklight.test.standaloneConsume";
    private record Case(String name,PowerForm form,String victim,boolean pressure,int variant){
        Case(String name,PowerForm form,String victim,boolean pressure){this(name,form,victim,pressure,com.codex.blacklight.combat.ConsumeVariant.DEFAULT);}
    }
    private static final List<Case> CASES=List.of(
            new Case("claws_villager",PowerForm.CLAWS,"villager",false),
            new Case("claws_villager_pressure",PowerForm.CLAWS,"villager",true),
            // CLASSIC is the head-crush scene (DEFAULT is the sweep with the camera punch-in).
            new Case("claws_head_villager_pressure",PowerForm.CLAWS,"villager",true,com.codex.blacklight.combat.ConsumeVariant.CLASSIC),
            new Case("whip_classic_villager_pressure",PowerForm.WHIPFIST,"villager",true,com.codex.blacklight.combat.ConsumeVariant.CLASSIC),
            new Case("bare_villager_pressure",PowerForm.NONE,"villager",true),
            new Case("hammer_zombie_pressure",PowerForm.HAMMERFISTS,"zombie",true),
            new Case("blade_villager_pressure",PowerForm.BLADE,"villager",true),
            new Case("whip_villager_pressure",PowerForm.WHIPFIST,"villager",true),
            new Case("claws_ravager",PowerForm.CLAWS,"ravager",false),
            new Case("claws_ravager_pressure",PowerForm.CLAWS,"ravager",true));
    private enum Stage{SETUP,GRAB,HOLD,CONSUME,RUN,SETTLE,DRAIN,DONE}
    private static Stage stage=Stage.SETUP;
    private static int caseIndex,stageTicks,clientTick;
    private static boolean initialized,disabled,completed,capturePending;
    private static volatile boolean serverReady;
    private static volatile int victimId=-1;
    private static Path output;
    private static final JsonArray results=new JsonArray(),violations=new JsonArray();
    private static final List<JsonObject> frames=new ArrayList<>(),ticks=new ArrayList<>();
    private static final Map<KeyMapping,com.mojang.blaze3d.platform.InputConstants.Key> savedBindings=new IdentityHashMap<>();
    private static ThreadPoolExecutor writer;
    private static final AtomicInteger submitted=new AtomicInteger(),written=new AtomicInteger();
    private static volatile String imageError;
    private static String captureName;
    private static long lastFrameNanos;
    private static int frameIndex;
    // Server observations for the current case (server thread writes, client reads after SETTLE).
    private static volatile boolean sessionSeen,committedSeen,ended,cancelled,victimDied;
    private static volatile int endStage=-1,committedTicks,flinchesWhileCommitted,flinchesWhileGrappling,pressureHits,explosions;
    private static volatile double maxCaptureDrift;
    private static long lastReaction=Long.MIN_VALUE;
    private static volatile long serverMaxTickNanos;
    private static final List<Integer> spawned=Collections.synchronizedList(new ArrayList<>());

    private static boolean active(){return initialized&&!disabled&&!completed;}
    private static Case current(){return CASES.get(Math.min(caseIndex,CASES.size()-1));}
    private static void violation(String text){violations.add(current().name()+": "+text);BlacklightNeoMod.LOGGER.error("Consume QA: {}",text);}

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(ClientTickEvent.Post event){
        if(disabled||completed||System.getProperty(PROPERTY,"").isBlank())return;
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();
        if(server==null||mc.player==null||mc.level==null)return;
        var save=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
        if(!save.getFileName().toString().equals("Blacklight Standalone QA v4")
                ||!mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("run-native-qa")){
            disabled=true;BlacklightNeoMod.LOGGER.error("Consume QA refused non-QA save {}",save);return;
        }
        try{
            if(!initialized){
                output=Path.of(System.getProperty(PROPERTY)).toAbsolutePath().normalize();Files.createDirectories(output);
                for(var key:List.of(mc.options.keyUp,mc.options.keySprint,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,
                        mc.options.keyJump,mc.options.keyShift,mc.options.keyAttack,mc.options.keyUse,mc.options.keyTogglePerspective)){
                    savedBindings.put(key,key.getKey());key.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);key.setDown(false);
                }
                KeyMapping.resetMapping();
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.setScreen(null);mc.mouseHandler.releaseMouse();mc.getWindow().setTitle("Blacklight · 吸收 QA（请勿操作测试窗口）");
                writer=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),r->{var t=new Thread(r,"consume-qa-png");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
                initialized=true;setup(mc,server);return;
            }
            if(imageError!=null)throw new IllegalStateException("image write failed: "+imageError);
            clientTick++;
            if(stage==Stage.DRAIN){if(writer.isTerminated()){stage=Stage.DONE;finish(mc);}return;}
            if(!serverReady)return;
            stageTicks++;recordTick(mc);
            var p=mc.player;int grab=p.getData(ModAttachments.GRAB_STAGE);
            switch(stage){
                // Right after world load the first frames run at 8-15 fps (chunk meshing, shader warmup), which
                // turns authored camera punch-ins into apparent snaps. Start once frame pacing has settled.
                case SETUP->{if(stageTicks>=12&&(warmFrames()||stageTicks>=200))advance(Stage.GRAB);}
                case GRAB->{
                    // Victims flee from the pressure zombies and the first case starts right after login,
                    // so put this case's victim back in front of the actor before every grab request.
                    if(stageTicks==1||!sessionSeen&&stageTicks%20==1){faceVictim(mc);placeVictim(server);PacketDistributor.sendToServer(new ClawActionPayload(ClawActionPayload.Action.GRAB));}
                    if(ConsumeVisual.holding(grab))advance(Stage.HOLD);
                    else if(stageTicks>90){violation("never reached HOLD (grab stage "+grab+", session="+sessionSeen+")");endCase(mc,server);}
                }
                case HOLD->{if(stageTicks>=12){PacketDistributor.sendToServer(new ClawActionPayload(ClawActionPayload.Action.CONSUME));advance(Stage.CONSUME);}}
                case CONSUME->{
                    if(ConsumeVisual.consumeStage(grab))advance(Stage.RUN);
                    else if(stageTicks>20){violation("consume never committed (grab stage "+grab+")");endCase(mc,server);}
                }
                case RUN->{
                    if(stageTicks==10||stageTicks==30||stageTicks==50)captureName=current().name()+"_"+stageTicks+".png";
                    if(ended){advance(Stage.SETTLE);}
                    else if(stageTicks>220){violation("scene still running after 220 ticks");endCase(mc,server);}
                }
                case SETTLE->{if(stageTicks>=15)endCase(mc,server);}
                default->{}
            }
        }catch(Exception e){fail(e);}
    }
    private static void advance(Stage next){stage=next;stageTicks=0;}
    /** Client side of the placement: body and chase camera both look down +Z at the victim. */
    private static void faceVictim(Minecraft mc){
        var p=mc.player;p.setYRot(0);p.yRotO=0;p.setXRot(0);p.xRotO=0;p.setYHeadRot(0);p.yBodyRot=0;
        try{var reset=com.codex.blacklight.client.camera.PrototypeChaseCamera.class.getDeclaredMethod("reset");reset.setAccessible(true);reset.invoke(null);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("chase camera reset unavailable",e);}
        p.getData(ModAttachments.MOVEMENT_STATE).setCameraYaw(0);
    }
    /** Queued on the server thread ahead of the grab packet, so the grab always sees it. */
    private static void placeVictim(net.minecraft.client.server.IntegratedServer server){
        int id=victimId;var c=current();double distance=c.victim().equals("ravager")?2.3:c.form()==PowerForm.WHIPFIST?5:1.7;
        server.execute(()->{
            var players=server.getPlayerList().getPlayers();if(players.isEmpty())return;var p=players.get(0);
            // Leftovers saved by an earlier run load with their chunk, after setup's sweep.
            List<Integer> own;synchronized(spawned){own=List.copyOf(spawned);}
            for(var e:p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new net.minecraft.world.phys.AABB(16,240,16,64,270,64),x->!(x instanceof net.minecraft.world.entity.player.Player)&&!own.contains(x.getId())))e.discard();
            if(!(p.serverLevel().getEntity(id) instanceof Mob victim)||!victim.isAlive()){BlacklightNeoMod.LOGGER.info("Consume QA victim {} missing",id);return;}
            p.connection.teleport(40.5,251,36.5,0,0);
            // Grab aim follows the reported camera yaw; the saved world can leave the orbit facing elsewhere.
            p.getData(ModAttachments.MOVEMENT_STATE).setCameraYaw(0);
            victim.moveTo(40.5,251,36.5+distance,180,0);victim.setYHeadRot(180);victim.yBodyRot=180;
            victim.setDeltaMovement(Vec3.ZERO);victim.getNavigation().stop();
            BlacklightNeoMod.LOGGER.info("Consume QA actor pos={} yaw={} camera={} pitch={} ground={} claw={} lock={} terrain={} move={} active={} busy={}",p.position(),p.getYRot(),com.codex.blacklight.movement.TraversalSteering.cameraYaw(p),p.getXRot(),p.onGround(),p.getData(ModAttachments.CLAW_COMBAT).action(),com.codex.blacklight.combat.CombatTargeting.locked(p),com.codex.blacklight.combat.BlockThrowSystem.canUse(p)&&com.codex.blacklight.combat.TerrainGrabSelection.targetsTerrain(p),p.getData(ModAttachments.MOVEMENT_STATE),ConsumeSystem.active(p),com.codex.blacklight.combat.WeaponCombatSystem.busy(p));
            BlacklightNeoMod.LOGGER.info("Consume QA victim {} alive={} eligible={} selectable={} tissue={} claimant={} visual={} invisible={} allied={} infected={}",victim.getType(),victim.isAlive(),ConsumeSystem.eligible(victim),com.codex.blacklight.combat.CombatTargeting.selectable(p,victim),com.codex.blacklight.infection.TissueCompetition.controls(victim),com.codex.blacklight.power.BiomassSystem.claimant(victim),com.codex.blacklight.registry.ModAttachments.peekData(victim,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY).active(),victim.isInvisibleTo(p),p.isAlliedTo(victim),com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(victim));
        });
    }
    private static void endCase(Minecraft mc,net.minecraft.client.server.IntegratedServer server){
        analyse();
        caseIndex++;
        if(caseIndex>=CASES.size()){stage=Stage.DRAIN;writer.shutdown();return;}
        setup(mc,server);
    }

    private static void setup(Minecraft mc,net.minecraft.client.server.IntegratedServer server){
        serverReady=false;victimId=-1;stage=Stage.SETUP;stageTicks=0;frames.clear();ticks.clear();
        sessionSeen=committedSeen=ended=cancelled=victimDied=false;endStage=-1;committedTicks=flinchesWhileCommitted=flinchesWhileGrappling=pressureHits=explosions=0;maxCaptureDrift=0;serverMaxTickNanos=0;
        var c=current();var uuid=mc.player.getUUID();
        server.execute(()->{
            try{
                ServerLevel level=server.overworld();level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                synchronized(spawned){for(int id:spawned){var e=level.getEntity(id);if(e!=null)e.discard();}spawned.clear();}
                // Leftovers from an earlier run share this pad; every case starts with only its own victim.
                for(var e:level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new net.minecraft.world.phys.AABB(16,240,16,64,270,64),x->!(x instanceof net.minecraft.world.entity.player.Player)))e.discard();
                for(int x=24;x<=56;x++)for(int z=24;z<=56;z++){
                    level.setBlockAndUpdate(new BlockPos(x,250,z),((x+z)%6==0?Blocks.WHITE_CONCRETE:Blocks.GRAY_CONCRETE).defaultBlockState());
                    for(int y=251;y<=258;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                var p=server.getPlayerList().getPlayer(uuid);if(p==null)throw new IllegalStateException("QA actor missing");
                ConsumeSystem.cancel(p);
                var state=new BlacklightState();state.infect();state.setPrototypeMode(true);
                for(var form:new PowerForm[]{PowerForm.NONE,PowerForm.CLAWS,PowerForm.HAMMERFISTS,PowerForm.BLADE,PowerForm.WHIPFIST})state.unlock(form);
                state.activate(c.form());state.addBiomass(1000);
                com.codex.blacklight.combat.ConsumeVariant.qaForced=c.variant();
                p.setData(ModAttachments.PLAYER_STATE,state);p.syncData(ModAttachments.PLAYER_STATE);
                p.setData(ModAttachments.WEAPON_VISUAL,com.codex.blacklight.combat.WeaponVisual.EMPTY);p.syncData(ModAttachments.WEAPON_VISUAL);
                p.setData(ModAttachments.HIT_REACTION,HitReaction.EMPTY);p.syncData(ModAttachments.HIT_REACTION);
                com.codex.blacklight.animation.NativePlayerActionBackend.clear(p);com.codex.blacklight.combat.CombatTargeting.requestLock(p,-1);
                p.getInventory().clearContent();p.setGameMode(GameType.SURVIVAL);p.getAbilities().flying=false;p.onUpdateAbilities();
                p.setHealth(p.getMaxHealth());p.removeAllEffects();
                p.connection.teleport(40.5,251,36.5,0,0);p.setDeltaMovement(Vec3.ZERO);p.setSprinting(false);
                boolean whip=c.form()==PowerForm.WHIPFIST;
                Mob victim=switch(c.victim()){
                    case "zombie"->EntityType.ZOMBIE.create(level);
                    case "ravager"->EntityType.RAVAGER.create(level);
                    default->EntityType.VILLAGER.create(level);
                };
                if(victim==null)throw new IllegalStateException("victim type missing");
                double distance=c.victim().equals("ravager")?2.3:whip?5:1.7;
                victim.moveTo(40.5,251,36.5+distance,180,0);victim.setYHeadRot(180);victim.yBodyRot=180;
                victim.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.LEATHER_HELMET));victim.setPersistenceRequired();
                level.addFreshEntity(victim);victim.setHealth(Math.max(1,victim.getMaxHealth()*.05F));
                spawned.add(victim.getId());victimId=victim.getId();
                if(c.pressure()){
                    double[][] around={{-1.6,-.6},{1.6,-.6},{-.9,-1.7},{.9,-1.7},{0,-2.2}};
                    for(var o:around){
                        var z=EntityType.ZOMBIE.create(level);if(z==null)continue;
                        z.moveTo(40.5+o[0],251,36.5+o[1],0,0);z.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.LEATHER_HELMET));z.setPersistenceRequired();
                        level.addFreshEntity(z);z.setTarget(p);spawned.add(z.getId());
                    }
                }
                serverReady=true;
            }catch(Exception e){fail(e);}
        });
    }

    /** Server observation and attack pressure. Runs on the integrated server thread. */
    @SubscribeEvent public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event){
        if(!active()||!serverReady||caseIndex>=CASES.size())return;
        long started=System.nanoTime();
        var server=event.getServer();var players=server.getPlayerList().getPlayers();if(players.isEmpty())return;
        ServerPlayer p=players.get(0);ServerLevel level=p.serverLevel();
        p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(20);
        boolean session=ConsumeSystem.active(p);int grab=p.getData(ModAttachments.GRAB_STAGE);
        boolean committed=ConsumeSystem.committed(p);var victim=level.getEntity(victimId);
        if(session&&!sessionSeen){sessionSeen=true;}
        if(session&&ConsumeVisual.acquiring(grab)&&victim!=null){
            // CAPTURE and WHIP_PULL are not allowed to drift the actor; record how far it moved.
        }
        if(committed){committedSeen=true;committedTicks++;}
        var hit=p.getData(ModAttachments.HIT_REACTION);long now=level.getGameTime();
        if(hit.duration()>0&&hit.started()!=lastReaction){lastReaction=hit.started();if(session&&hit.started()>=now-1){if(committed)flinchesWhileCommitted++;else flinchesWhileGrappling++;}}
        if(sessionSeen&&!session&&!ended){
            ended=true;endStage=grab;victimDied=victim==null||!victim.isAlive();cancelled=!victimDied;
        }
        var c=current();
        if(c.pressure()&&session){
            int age=ConsumeSystem.sessionAge(p);
            for(int id:List.copyOf(spawned)){
                if(id==victimId||!(level.getEntity(id) instanceof Mob z)||!z.isAlive())continue;
                z.setTarget(p);
                if(age%7==id%7&&z.distanceToSqr(p)<9){z.swing(net.minecraft.world.InteractionHand.MAIN_HAND);if(z.doHurtTarget(p))pressureHits++;}
            }
            if(committed&&(age==12||age==30)){
                level.explode(null,p.getX(),p.getY()+.3,p.getZ()-2.2,2.2F,Level.ExplosionInteraction.NONE);explosions++;
            }
            if(committed&&age==20)com.codex.blacklight.combat.SupremeHunterStagger.apply(p,30,0);
        }
        serverMaxTickNanos=Math.max(serverMaxTickNanos,System.nanoTime()-started);
    }

    private static void recordTick(Minecraft mc){
        var p=mc.player;var row=new JsonObject();
        row.addProperty("case",current().name());row.addProperty("stage",stage.name());row.addProperty("stageTick",stageTicks);row.addProperty("tick",clientTick);
        row.addProperty("grabStage",p.getData(ModAttachments.GRAB_STAGE));row.addProperty("consuming",p.getData(ModAttachments.CONSUMING));
        var action=p.getData(ModAttachments.PLAYER_ACTION);row.addProperty("action",action.clipId());row.addProperty("actionSeconds",action.seconds(mc.level.getGameTime()));
        row.add("pos",vec(p.position()));
        var hit=p.getData(ModAttachments.HIT_REACTION);row.addProperty("hitWeight",hit.weight(mc.level.getGameTime(),0));
        ticks.add(row);
    }
    private static final ArrayDeque<Double> RECENT_DT=new ArrayDeque<>();
    private static boolean warmFrames(){return RECENT_DT.size()>=30&&RECENT_DT.stream().mapToDouble(Double::doubleValue).max().orElse(1e9)<50;}
    private static JsonArray vec(Vec3 v){var a=new JsonArray();a.add(v.x);a.add(v.y);a.add(v.z);return a;}

    @SubscribeEvent public static void frame(RenderLevelStageEvent event){
        if(!active()||!serverReady||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();LocalPlayer p=mc.player;if(p==null||mc.level==null)return;
        long nanos=System.nanoTime();double dt=lastFrameNanos==0?0:(nanos-lastFrameNanos)/1e6;lastFrameNanos=nanos;
        if(stage==Stage.SETUP){RECENT_DT.addLast(dt);while(RECENT_DT.size()>30)RECENT_DT.removeFirst();}else RECENT_DT.clear();
        if(stage!=Stage.HOLD&&stage!=Stage.CONSUME&&stage!=Stage.RUN&&stage!=Stage.GRAB)return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var row=new JsonObject();row.addProperty("frame",frameIndex++);row.addProperty("stage",stage.name());row.addProperty("stageTick",stageTicks);row.addProperty("tick",clientTick);
        row.addProperty("partial",partial);row.addProperty("dtMs",dt);row.addProperty("grabStage",p.getData(ModAttachments.GRAB_STAGE));
        row.add("actor",vec(p.getPosition(partial)));
        row.addProperty("actorYaw",net.minecraft.util.Mth.rotLerp(partial,p.yBodyRotO,p.yBodyRot));
        row.add("camera",vec(event.getCamera().getPosition()));
        var victim=mc.level.getEntity(victimId);
        if(victim instanceof LivingEntity living&&living.isAlive()){
            var offset=ClientCarryPlacement.consumeOffset(living,partial);
            row.add("victim",vec(living.getPosition(partial).add(offset)));row.add("victimRaw",vec(living.getPosition(partial)));row.add("victimOffset",vec(offset));
            var scene=living.getData(ModAttachments.CONSUME_VISUAL);
            // A cleared scene reads as progress 0; that is the hand-off to recovery, not a rewind.
            if(scene.active())row.addProperty("sceneProgress",scene.motionProgress(mc.level.getGameTime(),partial));
        }
        row.addProperty("recovery",p.getData(ModAttachments.CONSUME_RECOVERY).active());
        row.addProperty("predicting",com.codex.blacklight.client.ClientConsumeActor.predicting());
        var action=p.getData(ModAttachments.PLAYER_ACTION);row.addProperty("action",action.clipId());row.addProperty("actionSeconds",action.seconds(mc.level.getGameTime()+partial));
        row.addProperty("hitWeight",p.getData(ModAttachments.HIT_REACTION).weight(mc.level.getGameTime(),partial));
        frames.add(row);
        if(captureName!=null&&writer.getQueue().remainingCapacity()>0)capturePending=true;
    }
    @SubscribeEvent public static void afterFrame(RenderFrameEvent.Post event){
        if(!active()||!capturePending)return;capturePending=false;var name=captureName;captureName=null;
        try{
            var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
            try{writer.execute(()->{try(image){image.writeToFile(output.resolve(name));written.incrementAndGet();}catch(Exception e){imageError=e.toString();}});submitted.incrementAndGet();}
            catch(RejectedExecutionException e){image.close();}
        }catch(Exception e){fail(e);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void humanMouse(InputEvent.MouseButton.Pre event){if(active()){event.setCanceled(true);Minecraft.getInstance().mouseHandler.releaseMouse();}}

    /** Per tick, the largest frame move divided by that tick's travel, averaged over moving ticks.
     * Smooth motion at n frames per tick is about 1/n; motion applied once per tick is near 1. */
    private static double[] stepping(List<JsonObject> rows,String key){
        double total=0,maxJump=0,concentration=0;int moving=0,lastTick=Integer.MIN_VALUE;double tickTotal=0,tickMax=0;Vec3 last=null;
        for(var r:rows){
            if(!r.has(key))continue;var a=r.getAsJsonArray(key);var v=new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
            int t=r.get("tick").getAsInt();
            if(t!=lastTick){if(tickTotal>.01){concentration+=tickMax/tickTotal;moving++;}tickTotal=0;tickMax=0;lastTick=t;}
            // A snap is distance per rendered frame at 60 fps: a slow 43 ms frame legitimately covers
            // 2.6 frames of a fast authored camera push, which is not a discontinuity.
            if(last!=null){double d=v.distanceTo(last);total+=d;tickTotal+=d;tickMax=Math.max(tickMax,d);
                double frames=Math.max(1,r.get("dtMs").getAsDouble()/16.667);maxJump=Math.max(maxJump,d/frames);}
            last=v;
        }
        if(tickTotal>.01){concentration+=tickMax/tickTotal;moving++;}
        return new double[]{moving==0?0:concentration/moving,total,maxJump};
    }
    private static void analyse(){
        var c=current();var result=new JsonObject();result.addProperty("case",c.name());
        var committed=frames.stream().filter(r->r.get("stage").getAsString().equals("RUN")&&ConsumeVisual.consumeStage(r.get("grabStage").getAsInt())).toList();
        var holding=frames.stream().filter(r->r.get("stage").getAsString().equals("HOLD")||r.get("stage").getAsString().equals("GRAB")).toList();
        result.addProperty("sessionSeen",sessionSeen);result.addProperty("committedSeen",committedSeen);result.addProperty("ended",ended);
        result.addProperty("cancelled",cancelled);result.addProperty("victimDied",victimDied);result.addProperty("endStage",endStage);
        result.addProperty("serverCommittedTicks",committedTicks);result.addProperty("flinchesWhileCommitted",flinchesWhileCommitted);
        result.addProperty("flinchesWhileGrappling",flinchesWhileGrappling);result.addProperty("pressureHits",pressureHits);result.addProperty("explosions",explosions);
        result.addProperty("committedFrames",committed.size());result.addProperty("serverQaTickMaxMs",serverMaxTickNanos/1e6);
        double maxDt=committed.stream().mapToDouble(r->r.get("dtMs").getAsDouble()).max().orElse(0);
        long hitches=committed.stream().filter(r->r.get("dtMs").getAsDouble()>100).count();
        result.addProperty("maxFrameMs",maxDt);result.addProperty("hitchFrames",hitches);
        int distinctTicks=(int)committed.stream().mapToInt(r->r.get("tick").getAsInt()).distinct().count();
        double framesPerTick=distinctTicks==0?0:committed.size()/(double)distinctTicks;result.addProperty("framesPerTick",framesPerTick);
        for(String key:new String[]{"actor","victim","camera"}){
            var s=stepping(committed,key);var o=new JsonObject();o.addProperty("tickConcentration",s[0]);o.addProperty("path",s[1]);o.addProperty("maxFrameJump",s[2]);result.add(key,o);
            // Stepped motion concentrates (nearly) all travel on the first frame of each tick.
            if(framesPerTick>=2.5&&s[1]>.15&&s[0]>Math.min(.8,1.9/framesPerTick))violation(key+" moves in tick steps while consuming: tick concentration "+String.format(Locale.ROOT,"%.2f",s[0])+" at "+String.format(Locale.ROOT,"%.1f",framesPerTick)+" frames/tick");
            if(s[2]>.6)violation(key+" snapped "+String.format(Locale.ROOT,"%.2f",s[2])+" blocks per 60 fps frame while consuming");
        }
        var hs=stepping(holding,"victim");result.addProperty("holdVictimTickConcentration",hs[0]);
        // The actor's action clip must play once without restarting.
        String clip=null;double lastSeconds=-1;int restarts=0;var clips=new LinkedHashSet<String>();
        for(var r:committed){String a=r.get("action").getAsString();double s=r.get("actionSeconds").getAsDouble();clips.add(a);
            if(a.equals(clip)&&s+1e-4<lastSeconds)restarts++;clip=a;lastSeconds=s;}
        // The rendered scene samples sceneProgress; it must never run backwards (time-sync rewinds).
        double lastProgress=-1;int progressRewinds=0;
        for(var r:committed){if(!r.has("sceneProgress"))continue;double sp=r.get("sceneProgress").getAsDouble();if(sp+1e-4<lastProgress)progressRewinds++;lastProgress=sp;}
        result.addProperty("sceneProgressRewinds",progressRewinds);
        if(progressRewinds>0)violation("scene clock ran backwards "+progressRewinds+" times while consuming");
        // A frame with a grab stage but neither a victim scene nor a recovery copy renders the
        // actor in its idle pose for one frame: the end-of-consume packet-order gap.
        long poseGaps=committed.stream().filter(r->r.get("grabStage").getAsInt()!=ConsumeVisual.NONE&&!r.has("sceneProgress")&&!r.get("recovery").getAsBoolean()).count();
        result.addProperty("poseGapFrames",poseGaps);
        if(poseGaps>0)violation("actor had no scene or recovery pose for "+poseGaps+" frames while consuming");
        var clipArray=new JsonArray();clips.forEach(clipArray::add);result.add("committedClips",clipArray);result.addProperty("clipRestarts",restarts);
        double maxHit=committed.stream().mapToDouble(r->r.get("hitWeight").getAsDouble()).max().orElse(0);result.addProperty("maxHitWeightCommitted",maxHit);
        if(!sessionSeen)violation("grab never started");
        else if(!committedSeen)violation("consume never committed");
        else{
            if(cancelled)violation("consume was interrupted (ended in stage "+endStage+" with the victim alive)");
            if(!ended)violation("consume never ended");
            if(flinchesWhileCommitted>0||maxHit>.01)violation("actor flinched during the committed consume ("+flinchesWhileCommitted+" reactions, weight "+maxHit+")");
            if(flinchesWhileGrappling>0)violation("actor flinched while grappling ("+flinchesWhileGrappling+")");
            if(clips.size()>1)violation("committed consume switched clips: "+clips);
            if(restarts>0)violation("consume clip restarted "+restarts+" times");
            if(hitches>0)violation(hitches+" hitch frames over 100 ms while consuming (max "+String.format(Locale.ROOT,"%.0f",maxDt)+" ms)");
            if(c.pressure()&&pressureHits==0)violation("pressure case never landed an attack");
        }
        results.add(result);
        try{
            var dump=new JsonObject();var f=new JsonArray();frames.forEach(f::add);var t=new JsonArray();ticks.forEach(t::add);dump.add("frames",f);dump.add("ticks",t);
            Files.writeString(output.resolve(c.name()+"-frames.json"),new Gson().toJson(dump));
        }catch(Exception e){BlacklightNeoMod.LOGGER.error("Consume QA frames",e);}
        BlacklightNeoMod.LOGGER.info("Consume QA case {}: {}",c.name(),result);
    }
    private static void restore(Minecraft mc){
        savedBindings.forEach(KeyMapping::setKey);KeyMapping.resetMapping();mc.updateTitle();
    }
    private static void finish(Minecraft mc){
        completed=true;restore(mc);write(true,null);mc.stop();
    }
    private static void fail(Exception e){
        if(disabled)return;disabled=true;BlacklightNeoMod.LOGGER.error("Consume QA failed",e);
        if(writer!=null)writer.shutdown();
        var mc=Minecraft.getInstance();mc.execute(()->{restore(mc);write(false,e.toString());mc.stop();});
    }
    private static synchronized void write(boolean complete,String error){
        if(output==null)return;
        try{
            var report=new JsonObject();report.addProperty("complete",complete);report.addProperty("passed",complete&&violations.isEmpty());
            report.addProperty("source","real client ClawActionPayload grab/consume, vanilla zombie melee, explosions and Supreme Hunter stagger; per-frame render positions");
            report.addProperty("images",written.get());report.add("results",results);report.add("violations",violations);if(error!=null)report.addProperty("error",error);
            Files.writeString(output.resolve("consume-report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception e){BlacklightNeoMod.LOGGER.error("Cannot write consume QA report",e);}
    }
    private StandaloneConsumeSmoke(){}
}
