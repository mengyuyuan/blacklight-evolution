package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Real local Player, real interaction/control packets and framebuffer evidence in the disposable QA world. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HelicopterPilotVisual {
    private static Path output;private static boolean initialized,done,requested,verified;private static int ticks,manual;
    private static volatile int craftId=-1;private static volatile String error;private static volatile boolean serverVerified;
    private static String frame="";private static double maximumHeight=81;private static float maximumYaw;
    private static final Set<String> captured=new LinkedHashSet<>(),sounds=new TreeSet<>();private static final JsonArray frames=new JsonArray();
    private static final List<UUID> crewIds=new ArrayList<>();private static UUID originalCraft;
    @SubscribeEvent public static void sound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event){
        if(initialized&&(event.getName().startsWith("vehicle.helicopter")||event.getName().equals("vehicle.coaxial")||event.getName().equals("vehicle.rocket")))sounds.add(event.getName());
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void tick(ClientTickEvent.Post event){
        if(done||System.getProperty("blacklight.test.helicopterPilot","").isBlank())return;
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
        try{
            if(!mc.gameDirectory.toPath().toAbsolutePath().toString().replace('\\','/').startsWith("D:/CodexCache/creature-combat-qa/")
                    ||!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals("Creature Skills QA Final"))throw new IllegalStateException("Refused non-QA world");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.helicopterPilot")).toAbsolutePath().normalize();if(!output.startsWith(Path.of("D:/CodexCache")))throw new IllegalStateException("D: required");Files.createDirectories(output);Files.writeString(output.resolve("status.txt"),"RUNNING");initialized=true;
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.fov().set(65);mc.options.framerateLimit().set(30);mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);mc.setScreen(null);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);var id=mc.player.getUUID();
                server.execute(()->{try{
                    var level=server.overworld();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.setDayTime(6000);level.setWeatherParameters(12000,0,false,false);
                    for(var entity:level.getEntitiesOfClass(Entity.class,new AABB(-48,75,-40,72,125,64),e->!(e instanceof net.minecraft.world.entity.player.Player)))entity.discard();
                    for(int x=-36;x<=56;x++)for(int z=-20;z<=48;z++){
                        level.setBlock(new BlockPos(x,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                        for(int y=81;y<=110;y++)if(!level.isEmptyBlock(new BlockPos(x,y,z)))level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
                    }
                    var p=server.getPlayerList().getPlayer(id);p.stopRiding();p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.containerMenu.broadcastChanges();
                    var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);p.syncData(ModAttachments.PLAYER_STATE);
                    p.setHealth(p.getMaxHealth());p.connection.teleport(-4,81,2.1,-90,0);
                    var craft=ModEntities.BLACKWATCH_HELICOPTER.get().create(level);craft.setPos(0,81,0);level.addFreshEntity(craft);craft.hurt(level.damageSources().generic(),17);originalCraft=craft.getUUID();
                    for(int i=0;i<6;i++){
                        var soldier=ModEntities.BLACKWATCH_SOLDIER.get().create(level);soldier.setPos(craft.position());soldier.setNoAi(true);soldier.setAutomaticUnitJoin(false);soldier.setPersistenceRequired();soldier.initializeNativeLoadout("pilot-visual:"+i);soldier.getPersistentData().putBoolean("HelicopterTroop",i>=2);
                        level.addFreshEntity(soldier);if(!soldier.startRiding(craft,true))throw new IllegalStateException("Crew boarding failed");crewIds.add(soldier.getUUID());
                    }
                    craftId=craft.getId();
                }catch(Exception failure){error=failure.toString();}});return;
            }
            ticks++;mc.getToasts().clear();mc.mouseHandler.releaseMouse();for(var key:mc.options.keyMappings)key.setDown(false);
            if(ticks>650)throw new IllegalStateException("Player workflow timed out");
            if(!(mc.level.getEntity(craftId) instanceof BlackwatchHelicopter craft))return;
            if(!requested&&ticks>65){requested=true;mc.gameMode.interact(mc.player,craft,net.minecraft.world.InteractionHand.MAIN_HAND);}
            var hijack=craft.pilot().hijack();frame="";
            boolean mercer=hijack.active()&&hijack.ticks()>=35&&hijack.ticks()<48||manual>=27&&manual<38;
            if(com.codex.blacklight.client.tuning.MercerAppearance.active(mc.player)!=mercer)com.codex.blacklight.client.tuning.MercerAppearance.toggle();
            if(hijack.active()){
                int t=hijack.ticks();frame=t>=40&&t<48?"MERCER_PULL":t>=65?"ENTRY":t>=55?"EJECT":t>=31?"PULL":t>=12?"LATCH":"";
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }else if(craft.pilot().slot(mc.player.getUUID())==0&&mc.player.getVehicle()==craft){
                manual++;mc.options.setCameraType(manual>=125&&manual<=148||manual>=250?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_BACK);
                float yaw=manual>=125&&manual<=148||manual>=250?-craft.getYRot():150,pitch=manual>=125&&manual<=148||manual>=250?20:22;
                mc.player.setYRot(yaw);mc.player.yRotO=yaw;mc.player.setXRot(pitch);mc.player.xRotO=pitch;
                if(manual>=40&&manual<70)mc.options.keyJump.setDown(true);
                if(manual>=76&&manual<114)mc.options.keyUp.setDown(true);if(manual>=76&&manual<92)mc.options.keyLeft.setDown(true);
                if(manual>=125&&manual<141){mc.options.keyAttack.setDown(true);mc.options.keyUse.setDown(true);}
                if(manual>=160&&manual<236)mc.options.keySprint.setDown(true);
                maximumHeight=Math.max(maximumHeight,craft.getY());maximumYaw=Math.max(maximumYaw,Math.abs(craft.getYRot()));
                frame=manual>=32&&manual<38?"MERCER_SEATED":manual>=255?"COCKPIT_LANDED":manual>=240?"LANDED":manual>=175?"DESCENT":manual>=132?"COCKPIT_FIRE":manual>=105?"THIRD_PERSON":manual>=85?"TURN":manual>=60?"ASCENT":manual>=25?"SEATED":"";
                if(manual>=270&&!verified){
                    verified=true;var playerId=mc.player.getUUID();server.execute(()->{try{
                        var c=(BlackwatchHelicopter)server.overworld().getEntity(craftId);var p=server.getPlayerList().getPlayer(playerId);
                        if(c==null||!originalCraft.equals(c.getUUID())||p.getVehicle()!=c||c.pilot().slot(p.getUUID())!=0||c.health()!=343||c.combat().rounds()>=240||c.combat().rockets()>=8||Math.abs(c.getY()-81)>.02||!c.safeLanding(c.position()))throw new IllegalStateException("Actual server flight/ammo/landing proof failed: "+(c==null?"missing":c.position()+" hp="+c.health()+" rounds="+c.combat().rounds()+" rockets="+c.combat().rockets()));
                        for(UUID id:crewIds){var e=server.overworld().getEntity(id);if(e==null||e.isPassenger())throw new IllegalStateException("Original extracted crew missing");}
                        serverVerified=true;
                    }catch(Exception failure){error=failure.toString();}});
                }
            }
            var row=new JsonObject();row.addProperty("tick",ticks);row.addProperty("manualTick",manual);row.addProperty("phase",craft.phase().name());row.addProperty("hijackTick",hijack.ticks());row.addProperty("y",craft.getY());row.addProperty("yaw",craft.getYRot());row.addProperty("rounds",craft.controlData().getInt("Rounds"));row.addProperty("rockets",craft.controlData().getInt("Rockets"));row.addProperty("seat",craft.pilot().slot(mc.player.getUUID()));frames.add(row);
            if(serverVerified){
                if(maximumHeight<86||maximumYaw<20||!captured.containsAll(List.of("LATCH","PULL","MERCER_PULL","EJECT","ENTRY","SEATED","MERCER_SEATED","ASCENT","TURN","THIRD_PERSON","COCKPIT_FIRE","DESCENT","LANDED","COCKPIT_LANDED"))||!sounds.containsAll(List.of("vehicle.helicopter_door","vehicle.helicopter_rotor","vehicle.helicopter_turbine","vehicle.coaxial","vehicle.rocket")))throw new IllegalStateException("Missing native motion/render/audio evidence: "+captured+" / "+sounds+" / height="+maximumHeight+" yaw="+maximumYaw);
                Files.writeString(output.resolve("frames.json"),new GsonBuilder().setPrettyPrinting().create().toJson(frames));Files.writeString(output.resolve("sounds.json"),new Gson().toJson(sounds));
                Files.writeString(output.resolve("status.txt"),"PASS: real player interaction, production keyboard packets, timed capture, finite fire and landing; visual review required");done=true;mc.stop();
            }
        }catch(Exception failure){done=true;failure.printStackTrace();try{if(output!=null){Files.writeString(output.resolve("status.txt"),"FAIL: "+failure);Files.writeString(output.resolve("frames.json"),new Gson().toJson(frames));}}catch(Exception ignored){}mc.stop();}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){
        if(!initialized||done||frame.isEmpty()||captured.contains(frame))return;
        try(var shot=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){shot.writeToFile(output.resolve(frame+".png"));captured.add(frame);}catch(Exception failure){error=failure.toString();}
    }
}
