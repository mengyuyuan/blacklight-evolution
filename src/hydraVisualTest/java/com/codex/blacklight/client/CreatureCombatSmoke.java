package com.codex.blacklight.client;

import com.codex.blacklight.combat.CreatureAttack;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.movement.PrototypeMovementSystem;
import com.codex.blacklight.registry.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.*;
import java.nio.file.*;
import java.util.*;

/** Opt-in visual QA in an isolated new world. No mouse input or existing saves. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class CreatureCombatSmoke {
    private static final String WORLD="Creature combat QA "+System.currentTimeMillis();
    private static final Path OUT=Path.of("combat-frames-"+System.currentTimeMillis());
    private static boolean creating,setup,done;
    private static int tick,lastFrame=-1;
    private static volatile int cameraId=-1;
    private static BlacklightHunter hunter;
    private static BlacklightHydra hydra;
    private static Mob target;
    private static final List<String> audit=new java.util.concurrent.CopyOnWriteArrayList<>();
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!Boolean.getBoolean("blacklight.creatureCombatVisual"))return;
        var mc=Minecraft.getInstance();
        if(!creating&&mc.screen instanceof TitleScreen){
            creating=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);
            mc.mouseHandler.releaseMouse();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                new WorldOptions(7711L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
        }
        if(done||mc.player==null||mc.level==null||mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals(WORLD))return;
        mc.mouseHandler.releaseMouse();mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);
        if(cameraId>=0&&mc.level.getEntity(cameraId)!=null)mc.setCameraEntity(mc.level.getEntity(cameraId));
        if(!setup){setup=true;server(mc,p->{
            var l=p.serverLevel();l.setDayTime(6000);l.setWeatherParameters(6000,0,false,false);
            l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
            for(int x=-18;x<=25;x++)for(int z=-18;z<=22;z++)l.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState());
            p.teleportTo(l,10,101,12,180,0);
            hunter=ModEntities.BLACKLIGHT_HUNTER.get().create(l);hunter.moveTo(10,101,0);hunter.setNoAi(true);l.addFreshEntity(hunter);
            target=EntityType.COW.create(l);target.moveTo(10,101,2.5);target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);l.addFreshEntity(target);
            hydra=ModEntities.BLACKLIGHT_HYDRA.get().create(l);hydra.moveTo(-5,101,0);hydra.setNoAi(true);l.addFreshEntity(hydra);
            camera(p,14,103,6,10,102,0);audit.add("world="+WORLD);
        });}
        tick++;
        int t=tick;
        if(t==40||t==100||t==180||t==250||t==330)server(mc,p->{
            // A bitten cow can be replaced by an infected entity. Never reuse
            // that removed target, otherwise later actions silently fail QA.
            if(target!=null)target.discard();
            for(var infected:p.serverLevel().getEntitiesOfClass(BlacklightInfectedHost.class,new net.minecraft.world.phys.AABB(-20,100,-20,30,110,25)))infected.discard();
            target=EntityType.COW.create(p.serverLevel());target.moveTo(10,101,2.5);target.setNoAi(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);p.serverLevel().addFreshEntity(target);
            hunter.moveTo(10,101,t==250?-4:0);hunter.setDeltaMovement(0,0,0);hunter.setOnGround(true);hunter.setNoAi(false);
            var move=switch(t){case 40->CreatureAttack.SWIPE;case 100->CreatureAttack.BARRAGE;case 180->CreatureAttack.SLAM;case 250->CreatureAttack.LEAP;default->CreatureAttack.DODGE;};
            audit.add("tick="+t+" move="+move+" started="+hunter.beginAttack(move,target));
        });
        if(t==80||t==160||t==235||t==310||t==370)server(mc,p->{hunter.setNoAi(true);audit.add("tick="+t+" targetHealth="+target.getHealth());});
        if(t==370&&Boolean.getBoolean("blacklight.hunterRigOnly")){
            server(mc,p->{hunter.discard();target.discard();});tick=809;
        }
        if(t==390)server(mc,p->{
            hunter.discard();target.discard();p.setGameMode(GameType.SURVIVAL);p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);p.setHealth(1000);
            p.teleportTo(p.serverLevel(),-5,101,3,180,0);camera(p,13,112,23,-5,107,0);
        });
        if(t==410||t==600||t==690)server(mc,p->{
            p.teleportTo(p.serverLevel(),-5,101,3,180,0);hydra.setNoAi(false);
            int action=t==410?3:t==600?9:10;audit.add("tick="+t+" hydra="+action+" started="+hydra.beginCombat(p,action));
        });
        if(t==565||t==650||t==780)server(mc,p->{hydra.setNoAi(true);audit.add("tick="+t+" playerHealth="+p.getHealth());});
        if(t==810)server(mc,p->{hydra.discard();p.setGameMode(GameType.SURVIVAL);p.teleportTo(p.serverLevel(),10,101,0,0,0);
            for(var infected:p.serverLevel().getEntitiesOfClass(BlacklightInfectedHost.class,new net.minecraft.world.phys.AABB(-20,100,-20,30,110,25)))infected.discard();
            var s=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);s.infect();s.setPrototypeMode(true);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);
            camera(p,13,102.3,5,10,101.8,0);
        });
        if(t==840||t==890)server(mc,p->{
            p.setOnGround(true);com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).setCameraYaw(0);
            PrototypeMovementSystem.acceptInput(p,false,true,t==890?1:0,t==840?1:0,false,false);
            audit.add("tick="+t+" hop="+com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_ACTION).clipId()+" velocity="+p.getDeltaMovement());
        });
        if(t==940){done=true;write("result.txt",String.join("\n",audit));mc.stop();}
    }
    private static void server(Minecraft mc,java.util.function.Consumer<ServerPlayer> action){
        var id=mc.player.getUUID();var s=mc.getSingleplayerServer();s.execute(()->{var p=s.getPlayerList().getPlayer(id);if(p!=null)action.accept(p);});
    }
    private static void camera(ServerPlayer p,double x,double y,double z,double tx,double ty,double tz){
        var l=p.serverLevel();var c=EntityType.ARMOR_STAND.create(l);c.setInvisible(true);c.setNoGravity(true);
        float yaw=(float)Math.toDegrees(Math.atan2(-(tx-x),tz-z));float pitch=(float)-Math.toDegrees(Math.atan2(ty-y,Math.hypot(tx-x,tz-z)));
        c.moveTo(x,y,z,yaw,pitch);c.yHeadRot=yaw;c.yBodyRot=yaw;l.addFreshEntity(c);cameraId=c.getId();
    }
    private static void write(String name,String text){try{Files.createDirectories(OUT);Files.writeString(OUT.resolve(name),text);}catch(Exception e){throw new RuntimeException(e);}}
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!Boolean.getBoolean("blacklight.creatureCombatVisual")||done||!setup||tick<35||lastFrame==tick||tick%4!=0)return;
        lastFrame=tick;try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){
            Files.createDirectories(OUT);image.writeToFile(OUT.resolve(String.format("tick-%04d.png",tick)));
            if(hunter!=null&&Minecraft.getInstance().level.getEntity(hunter.getId()) instanceof BlacklightHunter visible){
                var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(visible);
                if(renderer instanceof com.codex.blacklight.client.renderer.BlacklightHunterRenderer r){
                    var values=new LinkedHashMap<String,Object>();
                    values.put("attack",visible.attackMove().name());values.put("age",visible.attackAge(0));
                    var bones=new LinkedHashMap<String,float[]>();
                    for(String name:com.codex.blacklight.combat.CreaturePoseTable.HUNTER.bones){
                        var b=r.getGeoModel().getAnimationProcessor().getBone(name);
                        bones.put(name,new float[]{b.getRotX(),b.getRotY(),b.getRotZ(),b.getPosX(),b.getPosY(),b.getPosZ(),b.getPivotX(),b.getPivotY(),b.getPivotZ()});
                    }
                    values.put("bones",bones);write(String.format("tick-%04d.json",tick),new com.google.gson.Gson().toJson(values));
                }
            }
        }catch(Exception x){throw new RuntimeException(x);}
    }
}
