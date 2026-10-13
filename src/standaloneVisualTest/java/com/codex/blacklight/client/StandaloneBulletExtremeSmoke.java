package com.codex.blacklight.client;

import com.codex.blacklight.combat.DevastatorSystem;
import com.codex.blacklight.network.WeaponInputPayload;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opt-in disposable-world acceptance of a real ten-million-block fall.
 * Terrain OFF isolates collision/network physics from a millions-block crater. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class StandaloneBulletExtremeSmoke {
    private static final String PROPERTY="blacklight.test.bulletExtreme";
    private static final Path GAME=Path.of("D:/Blacklight-Standalone/run-native-qa");
    private static final double HEIGHT=9_999_999;
    private static final JsonArray samples=new JsonArray();
    private static Path output;
    private static UUID owner;
    private static int ticks,chargeTicks,tail;
    private static volatile boolean ready,flight,landed,failed;
    private static boolean started,done,oldPause,requested;
    private static CameraType oldCamera;
    private static SkillSettings oldSettings;
    private static volatile double maxSpeed,maxDrop,serverY,actualStep,previousY=Double.NaN;
    private static long firstFlight=-1,lastTick;
    private static final Set<String> shots=new HashSet<>();
    private static int highFrames,highBodyFrames;
    private static volatile int glideTicks;
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void input(ClientTickEvent.Pre event){
        if(started&&!done)Minecraft.getInstance().options.keyJump.setDown(!flight&&!landed);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(done||System.getProperty(PROPERTY,"").isBlank())return;
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();
        if(mc.player==null||server==null)return;
        try{
            if(!started){
                if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(GAME)
                    ||!server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(GAME.resolve("saves/Blacklight Standalone QA v4")))throw new IllegalStateException("Refused non-QA world");
                output=Path.of(System.getProperty(PROPERTY));Files.createDirectories(output);
                owner=mc.player.getUUID();oldPause=mc.options.pauseOnLostFocus;oldCamera=mc.options.getCameraType();
                mc.options.pauseOnLostFocus=false;mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);mc.setScreen(null);
                mc.mouseHandler.releaseMouse();started=true;
                server.execute(()->{
                    var p=server.getPlayerList().getPlayer(owner);
                    DevastatorSystem.cancel(p);p.getData(ModAttachments.MOVEMENT_STATE).reset();
                    var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.activate(PowerForm.NONE);state.addEvolutionPoints(10_000);
                    state.buyMovementUpgrade(MovementUpgrade.GLIDE_1);state.buyCombatSkill(CombatSkill.BULLETDIVE_DROP);
                    p.setData(ModAttachments.PLAYER_STATE,state);p.syncData(ModAttachments.PLAYER_STATE);
                    oldSettings=p.getData(ModAttachments.SKILL_SETTINGS);p.setData(ModAttachments.SKILL_SETTINGS,new SkillSettings(false));p.syncData(ModAttachments.SKILL_SETTINGS);
                    for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)p.serverLevel().setBlockAndUpdate(new BlockPos(x,250,z),Blocks.BEDROCK.defaultBlockState());
                    p.getInventory().clearContent();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();p.setHealth(p.getMaxHealth());
                    p.connection.teleport(.5,251+HEIGHT,.5,0,65);p.setDeltaMovement(Vec3.ZERO);p.setOnGround(false);p.fallDistance=0;
                    p.setData(ModAttachments.GLIDE_UNTIL,0L);p.syncData(ModAttachments.GLIDE_UNTIL);ready=true;
                });return;
            }
            ticks++;
            if(ticks>2200)throw new IllegalStateException("Extreme fall exceeded 110 seconds");
            mc.options.keyJump.setDown(!flight&&!landed);
            if(ready&&mc.player.getY()>HEIGHT*.95&&!flight&&!landed){
                boolean gliding=mc.player.getData(ModAttachments.GLIDE_UNTIL)>mc.level.getGameTime();
                if(ticks%8==0)PacketDistributor.sendToServer(new com.codex.blacklight.network.MovementInputPayload(true,false,0,0,false,false));
                if(requested||gliding&&glideTicks>=4){
                    requested=true;
                    if(chargeTicks++%8==0)PacketDistributor.sendToServer(new WeaponInputPayload(false,true,false,false,false));
                }
            }
            if(flight&&chargeTicks>=0){PacketDistributor.sendToServer(new WeaponInputPayload(false,false,false,false,false));chargeTicks=-1;}
            if(landed&&++tail>40){
                if(maxDrop<HEIGHT*.99||maxSpeed<10_000||actualStep<10_000)throw new IllegalStateException("Extreme fall was capped or failed to reach ground");
                if(com.codex.blacklight.client.camera.ClientDevastatorPostEffect.statistics().failed())throw new IllegalStateException("Flight shader failed");
                if(highFrames<20||highBodyFrames<highFrames*.95)throw new IllegalStateException("High-speed third-person body was culled: "+highBodyFrames+"/"+highFrames);
                finish(mc,null);
            }
            if(ticks%40==0)write(false,null);
        }catch(Exception e){finish(mc,e.toString());}
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public static void server(PlayerTickEvent.Post event){
        if(!started||done||!(event.getEntity() instanceof ServerPlayer p)||!p.getUUID().equals(owner))return;
        var s=DevastatorSystem.snapshot(p);serverY=p.getY();lastTick=p.level().getGameTime();
        glideTicks=p.getData(ModAttachments.MOVEMENT_STATE).mode()==com.codex.blacklight.movement.MovementState.Mode.GLIDE&&p.getData(ModAttachments.GLIDE_UNTIL)>lastTick?glideTicks+1:0;
        if(s!=null&&s.phase()==DevastatorSystem.FLIGHT){
            flight=true;if(firstFlight<0)firstFlight=lastTick;
            maxSpeed=Math.max(maxSpeed,s.peakDown());maxDrop=Math.max(maxDrop,s.fallHeight());
            if(Double.isFinite(previousY))actualStep=Math.max(actualStep,previousY-serverY);
            previousY=serverY;
        }
        if(s!=null&&s.landed()){
            maxDrop=Math.max(maxDrop,s.fallHeight());
            if(!landed&&!p.onGround())failed=true;
            landed=true;
        }
        if(lastTick%20==0||s!=null&&s.phase()==DevastatorSystem.LAND){
            var row=new JsonObject();row.addProperty("tick",lastTick);row.addProperty("y",serverY);row.addProperty("phase",s==null?-1:s.phase());row.addProperty("speed",s==null?0:s.peakDown());row.addProperty("ground",p.onGround());
            synchronized(samples){samples.add(row);}
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(!started||done||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();
        if(flight&&!landed&&maxSpeed>1000){highFrames++;if(com.codex.blacklight.client.renderer.DevastatorEffectRenderer.capturedChest(mc.player.getId())!=null)highBodyFrames++;}
        String name=landed?"landing":maxSpeed>20_000?"extreme":maxSpeed>1000?"fast":maxSpeed>10?"entry":null;
        if(name==null||!shots.add(name))return;
        try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(output.resolve(name+".png"));}
        catch(Exception e){failed=true;}
    }
    private static void finish(Minecraft mc,String error){
        done=true;mc.options.keyJump.setDown(false);mc.options.pauseOnLostFocus=oldPause;if(oldCamera!=null)mc.options.setCameraType(oldCamera);
        write(true,error);
        if(owner!=null&&mc.getSingleplayerServer()!=null)mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(owner);
            if(p!=null){DevastatorSystem.cancel(p);if(oldSettings!=null){p.setData(ModAttachments.SKILL_SETTINGS,oldSettings);p.syncData(ModAttachments.SKILL_SETTINGS);}}
            mc.execute(mc::stop);
        });else mc.stop();
    }
    private static void write(boolean complete,String error){
        if(output==null)return;
        try{
            var report=new JsonObject();report.addProperty("complete",complete);report.addProperty("passed",complete&&error==null&&!failed&&landed);
            report.addProperty("height",HEIGHT);report.addProperty("maxSpeed",maxSpeed);report.addProperty("maxActualServerStep",actualStep);report.addProperty("maxDrop",maxDrop);report.addProperty("landed",landed);
            report.addProperty("flightTicks",firstFlight<0?0:lastTick-firstFlight);report.addProperty("terrainBreaking",false);if(error!=null)report.addProperty("error",error);
            report.addProperty("highSpeedFrames",highFrames);report.addProperty("highSpeedBodyFrames",highBodyFrames);
            synchronized(samples){report.add("serverSamples",samples.deepCopy());}
            Files.writeString(output.resolve("report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception ignored){ }
    }
}
