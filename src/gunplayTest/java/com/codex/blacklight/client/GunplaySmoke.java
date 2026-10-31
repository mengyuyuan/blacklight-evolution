package com.codex.blacklight.client;

import com.codex.blacklight.firearms.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.client.renderer.GunplayVisuals;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;

/** Private isolated real-client/framebuffer run. No desktop input or existing save is touched. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class GunplaySmoke {
    private static final String SAVE="Gunplay private visual QA";
    private static boolean creating,hidden,setup,done;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static int ticks,npc=-1,tank=-1;
    private static int targetTank=-1;
    private static volatile float meleeBefore;
    private static volatile boolean meleeVerified;
    private static boolean mechanical(){return Boolean.getBoolean("blacklight.test.mechanical");}
    private static long lastReceipt;
    private static final ArrayDeque<String> captures=new ArrayDeque<>();
    private static final JsonArray frames=new JsonArray();
    private static Path output;
    private static boolean enabled(){return Boolean.getBoolean("blacklight.test.gunplay");}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!enabled()||done)return;var mc=Minecraft.getInstance();
        try{
            var game=mc.gameDirectory.toPath().toAbsolutePath().normalize();
            if(!game.getFileName().toString().startsWith("run-gunplay-"))throw new IllegalStateException("Refuse non-QA directory: "+game);
            output=game.resolve("gunplay-evidence");Files.createDirectories(output);
            if(!hidden&&mc.getWindow()!=null){org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());hidden=true;mc.options.pauseOnLostFocus=false;}
            if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.screen.onClose();return;}
            if(!creating&&mc.screen instanceof TitleScreen){
                if(Files.exists(game.resolve("saves").resolve(SAVE)))throw new IllegalStateException("Use a fresh QA directory");
                creating=true;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(120);
                mc.createWorldOpenFlows().createFreshLevel(SAVE,new LevelSettings(SAVE,GameType.CREATIVE,false,net.minecraft.world.Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(92516L,false,false),
                        r->r.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());return;
            }
            var server=mc.getSingleplayerServer();if(server==null||mc.level==null||mc.player==null)return;
            if(!server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString().equals(SAVE))throw new IllegalStateException("Wrong save");
            if(failure!=null)throw new IllegalStateException(failure);
            if(!setup){setup=true;mc.setScreen(null);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=false;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                var uuid=mc.player.getUUID();server.execute(()->{try{
                    var l=server.overworld();l.setDayTime(6000);l.setWeatherParameters(12000,0,false,false);l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                    for(int x=-20;x<=20;x++)for(int z=-12;z<=48;z++)l.setBlock(new BlockPos(x,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                    for(int x=-12;x<=12;x++)for(int y=81;y<=88;y++)l.setBlock(new BlockPos(x,y,mechanical()?42:24),Blocks.STONE_BRICKS.defaultBlockState(),2);
                    var p=server.getPlayerList().getPlayer(uuid);p.teleportTo(0,81,-4);p.setYRot(0);p.setXRot(0);p.getInventory().clearContent();
                    var rifle=FirearmsRegistry.weapon(WeaponCatalog.RIFLE.id()).getDefaultInstance();FirearmItem.store(rifle,new AmmoState(15,0,0));p.setItemInHand(InteractionHand.MAIN_HAND,rifle);p.getInventory().setItem(2,new ItemStack(FirearmsRegistry.ammo("rifle_round"),64));p.containerMenu.broadcastChanges();
                    var s=ModEntities.BLACKWATCH_SOLDIER.get().create(l);s.setNoAi(true);s.setAutomaticUnitJoin(false);s.initializeNativeLoadout("visual-rifle");var sr=FirearmsRegistry.weapon(WeaponCatalog.RIFLE.id()).getDefaultInstance();FirearmItem.store(sr,new AmmoState(30,0,0));s.setItemSlot(EquipmentSlot.MAINHAND,sr);s.moveTo(4,81,5,180,0);l.addFreshEntity(s);npc=s.getId();
                    if(mechanical()){var v=ModEntities.BLACKWATCH_TANK.get().create(l);v.moveTo(0,81,26,0,0);l.addFreshEntity(v);targetTank=v.getId();}ready=true;
                }catch(Throwable t){failure=t;}});return;
            }
            if(!ready)return;ticks++;mc.getToasts().clear();
            if(ticks<90){mc.player.setYRot(0);mc.player.setXRot(0);}
            if(ticks==20)captures.add("rifle_idle");
            if(ticks==30)KeyMapping.click(FirearmControls.RELOAD.getKey());
            for(int sample:new int[]{36,42,48,54,60,66,72})if(ticks==sample)captures.add("rifle_reload_"+sample);
            if(ticks==78&&FirearmItem.state(mc.player.getMainHandItem(),0).magazine()!=30)throw new IllegalStateException("Actual reload key did not reload held gun");
            if(mechanical()&&ticks==79)lock(mc);
            if(ticks==82){var id=mc.player.getUUID();server.execute(()->{var p=server.getPlayerList().getPlayer(id);((FirearmItem)p.getMainHandItem().getItem()).use(p.level(),p,InteractionHand.MAIN_HAND);});}
            if(ticks==84||ticks==85)captures.add("rifle_shot_"+ticks);
            if(mechanical()&&ticks==90){if(ClientTargetLock.entityTarget()==null||ClientTargetLock.entityTarget().getId()!=targetTank)throw new IllegalStateException("Actual Tab handler/network did not lock the tank: active="+ClientTargetLock.active()+" requested="+ClientTargetLock.requestedId()+" ack="+ClientTargetLock.acknowledgementTrace()+" synced="+com.codex.blacklight.registry.ModAttachments.getData(mc.player, com.codex.blacklight.registry.ModAttachments.TARGET_LOCK_ID));captures.add("rifle_tank_lock_hud");}
            if(ticks==96){mc.options.hideGui=true;var id=mc.player.getUUID();server.execute(()->{var p=server.getPlayerList().getPlayer(id);p.teleportTo(4,81,0);p.setYRot(0);p.setXRot(0);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                    var s=(MilitarySoldier)server.overworld().getEntity(npc);s.setYRot(180);s.setYBodyRot(180);s.setYHeadRot(180);FirearmReloadSystem.begin(s.getMainHandItem(),new AmmoState(30,0,server.overworld().getGameTime()+40),server.overworld().getGameTime());});}
            if(ticks>=100&&ticks<150){mc.player.setYRot(0);mc.player.setXRot(0);}
            for(int sample:new int[]{102,110,118,126,136})if(ticks==sample)captures.add("soldier_reload_"+sample);
            if(ticks==148){var id=mc.player.getUUID();server.execute(()->{try{var p=server.getPlayerList().getPlayer(id);var v=ModEntities.BLACKWATCH_TANK.get().create(p.level());v.moveTo(0,81,9,0,0);v.inventory().issueInitialLoadout();p.serverLevel().addFreshEntity(v);tank=v.getId();p.startRiding(v);}catch(Throwable t){failure=t;}});mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);}
            if(ticks==170){var id=mc.player.getUUID();server.execute(()->{try{var p=server.getPlayerList().getPlayer(id);var v=(MilitaryVehicle)p.serverLevel().getEntity(tank);if(!com.codex.blacklight.military.vehicle.VehicleAttackService.fire(v,p,"tank_he"))throw new IllegalStateException("Actual cannon fire rejected");}catch(Throwable t){failure=t;}});}
            if(mechanical()&&ticks==160){mc.options.hideGui=false;lock(mc);}
            for(int sample:new int[]{171,173,176,180,186})if(ticks==sample)captures.add("tank_shot_"+sample);
            if(ticks==205){mc.options.hideGui=false;mc.options.setCameraType(CameraType.FIRST_PERSON);var id=mc.player.getUUID();server.execute(()->{var p=server.getPlayerList().getPlayer(id);p.stopRiding();p.teleportTo(0,81,0);p.getInventory().clearContent();var gun=FirearmsRegistry.weapon(WeaponCatalog.RIFLE.id()).getDefaultInstance();p.setItemInHand(InteractionHand.MAIN_HAND,gun);((FirearmItem)gun.getItem()).reload(p,InteractionHand.MAIN_HAND);});}
            if(ticks==212)captures.add("rifle_empty_ammo_hint");
            if(mechanical()&&ticks==208)KeyMapping.click(FirearmControls.RELOAD.getKey());
            if(mechanical()&&ticks==221){ClientTargetLock.clear();var id=mc.player.getUUID();server.execute(()->{var p=server.getPlayerList().getPlayer(id);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);var state=com.codex.blacklight.registry.ModAttachments.getData(p, com.codex.blacklight.registry.ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);com.codex.blacklight.registry.ModAttachments.syncData(p, com.codex.blacklight.registry.ModAttachments.PLAYER_STATE);});}
            if(mechanical()&&ticks==235)lock(mc);
            if(mechanical()&&ticks==243){if(!(ClientTargetLock.entityTarget() instanceof MilitaryVehicle))throw new IllegalStateException("Prototype empty-hand Tab failed on tank");captures.add("prototype_tank_lock");}
            if(mechanical()&&ticks==255){
                ClientWeaponCombat.setFixtureOwnsInput(true);var id=mc.player.getUUID();server.execute(()->{
                    var p=server.getPlayerList().getPlayer(id);p.setGameMode(GameType.SURVIVAL);p.teleportTo(0,81,20.5);p.setYRot(0);p.setXRot(0);
                    var v=(MilitaryVehicle)p.serverLevel().getEntity(targetTank);meleeBefore=v.health();
                });
            }
            if(mechanical()&&ticks==280){
                if(!ClientWeaponCombat.active()||!(ClientTargetLock.entityTarget() instanceof MilitaryVehicle))throw new IllegalStateException("Client melee control not active with vehicle lock");
                com.codex.blacklight.forge.PacketDistributor.sendToServer(new com.codex.blacklight.network.WeaponInputPayload(true,false));
            }
            if(mechanical()&&ticks==286)com.codex.blacklight.forge.PacketDistributor.sendToServer(new com.codex.blacklight.network.WeaponInputPayload(false,false));
            if(mechanical()&&ticks==340){var id=mc.player.getUUID();server.execute(()->{try{
                var p=server.getPlayerList().getPlayer(id);var v=(MilitaryVehicle)p.serverLevel().getEntity(targetTank);
                if(v==null||v.health()>=meleeBefore)throw new IllegalStateException("Client weapon input did not damage actual vehicle");
                System.out.println("MECHANICAL_CLIENT_MELEE_PASS from="+meleeBefore+" to="+v.health());meleeVerified=true;
            }catch(Throwable t){failure=t;}});captures.add("prototype_melee_contact");}
            if(ticks>=(mechanical()?355:220)){if(GunplayVisuals.received()<3||mechanical()&&!meleeVerified)throw new IllegalStateException("Real client feedback/melee verification missing");finish(null);}
        }catch(Throwable t){finish(t);}
    }
    private static void lock(Minecraft mc){
        ClientTargetLock.sync();var target=mc.level.getEntity(targetTank);if(target==null)throw new IllegalStateException("Tank not tracked on client");
        mc.hitResult=new net.minecraft.world.phys.EntityHitResult(target);
        ClientClawEvents.onKey(new InputEvent.Key(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB,0,org.lwjgl.glfw.GLFW.GLFW_PRESS,0));
    }
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!enabled()||done)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        if(GunplayVisuals.received()>lastReceipt){lastReceipt=GunplayVisuals.received();captures.addFirst("feedback_"+lastReceipt+"_"+ticks);}
        if(captures.isEmpty())return;
        try{String name=captures.removeFirst();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(output.resolve(name+".png"));}
            var row=new JsonObject();row.addProperty("image",name+".png");row.addProperty("tick",ticks);row.addProperty("feedbackReceived",GunplayVisuals.received());
            row.addProperty("worldTime",mc.level.getGameTime());
            var active=GunplayVisuals.class.getDeclaredField("ACTIVE");active.setAccessible(true);var effects=new JsonArray();
            for(var fx:(Iterable<?>)active.get(null)){var data=fx.getClass().getDeclaredMethod("data");var at=fx.getClass().getDeclaredMethod("at");data.setAccessible(true);at.setAccessible(true);var r=new JsonObject();r.addProperty("receipt",data.invoke(fx).toString());r.addProperty("at",((Number)at.invoke(fx)).doubleValue());effects.add(r);}row.add("effects",effects);
            row.addProperty("reloadKey",FirearmControls.RELOAD.getTranslatedKeyMessage().getString());frames.add(row);
        }catch(Throwable t){finish(t);}
    }
    private static void finish(Throwable t){if(done)return;done=true;try{var report=new JsonObject();report.addProperty("passed",t==null);report.addProperty("error",t==null?"":t.toString());report.add("frames",frames);Files.writeString(output.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception ignored){}Minecraft.getInstance().stop();}
    private GunplaySmoke(){}
}
