package com.codex.blacklight.client;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.movement.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import com.codex.blacklight.forge.PacketDistributor;
import java.nio.file.*;
import java.util.*;

/** Disposable-world-only acceptance fixture, excluded from normal compilation and JARs. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class WingFlightSmoke {
    private static final boolean ARMOR=Boolean.getBoolean("blacklight.test.wingArmor");
    private static final String RUN="wings11-"+System.currentTimeMillis()+"-"+(ARMOR?"armor":"plain");
    private static int tick;private static boolean initialized,finished;private static int lastFrame=-1;
    private static final Path ROOT=Path.of(System.getProperty("blacklight.test.wingDirectory","run-wing-qa"));
    private static final List<String> samples=new ArrayList<>();
    private static final java.util.concurrent.ExecutorService WRITER=java.util.concurrent.Executors.newFixedThreadPool(3,r->{var t=new Thread(r,"Wing-QA-frames");t.setDaemon(true);return t;});
    private static final java.util.concurrent.atomic.AtomicInteger pending=new java.util.concurrent.atomic.AtomicInteger();
    private static int qaForward,qaSide;private static boolean qaUp,qaDown,qaBoost;
    private static long lastCapture;private static int captured,dropped;
    private static boolean guarded(Minecraft mc){return !Boolean.getBoolean("blacklight.test.wingSonic")&&Boolean.getBoolean("blacklight.test.wings")&&mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(ROOT.resolve("client").toAbsolutePath().normalize());}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(!guarded(mc)||mc.player==null||mc.getSingleplayerServer()==null||finished)return;
        if(!initialized){
            initialized=true;mc.options.pauseOnLostFocus=false;mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);mc.options.hideGui=true;
            mc.options.renderDistance().set(5);mc.options.simulationDistance().set(5);
            var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);if(p==null)return;
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.UTILITY_POWERS,0);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.UTILITY_POWERS);WingFlightSystem.stop(p);
                p.setGameMode(GameType.SURVIVAL);p.getAbilities().flying=false;p.getAbilities().mayfly=false;p.onUpdateAbilities();
                p.serverLevel().setDayTime(6000);
                p.serverLevel().setWeatherParameters(12000,0,false,false);
                for(int x=-40;x<=40;x++)for(int z=-15;z<=130;z++)p.serverLevel().setBlock(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.SOVEREIGN_WINGS);state.unlock(PowerForm.ARMOR);state.activate(PowerForm.NONE);com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,state);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);
                p.teleportTo(0,101,0);p.setYRot(0);p.setXRot(0);

            });return;
        }
        tick++;if(tick<80)return;
        if(tick==100&&ARMOR)PacketDistributor.sendToServer(new PowerSelectPayload(PowerForm.ARMOR.ordinal(),false));
        if(tick==130){
            boolean actualArmor=UtilityPowerSystem.active(mc.player,PowerForm.ARMOR);
            samples.add("armor_verified,"+actualArmor);
            if(actualArmor!=ARMOR)throw new IllegalStateException("Wing QA armor state did not apply");
        }
        if(tick==82)PacketDistributor.sendToServer(new PowerSelectPayload(PowerForm.SOVEREIGN_WINGS.ordinal(),false));
        mc.options.setCameraType(tick>=115&&tick<175||tick>=270&&tick<300||tick>=395&&tick<415||tick>=500&&tick<530?CameraType.THIRD_PERSON_FRONT:CameraType.THIRD_PERSON_BACK);
        qaUp=tick>=240&&tick<290;qaDown=tick>=550&&tick<740;
        qaForward=tick>=135&&tick<215||tick>=320&&tick<455?1:tick>=475&&tick<500?-1:0;
        qaSide=tick>=500&&tick<525?1:0;qaBoost=tick>=175&&tick<215||tick>=365&&tick<455;
        if(tick>=330&&tick<450)com.codex.blacklight.client.camera.PrototypeChaseCamera.mouse(20,0);
        float yaw=com.codex.blacklight.client.camera.PrototypeChaseCamera.ownsView()?com.codex.blacklight.client.camera.PrototypeChaseCamera.controlYaw():mc.player.getYRot();
        mc.player.setXRot(0);
        ClientWingFlight.applyInput(mc,qaForward,qaSide,qaUp,qaDown,qaBoost,yaw);
        if(tick==530){
            var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);if(p==null)return;
                var base=p.blockPosition();
                for(int x=base.getX()-18;x<=base.getX()+18;x++)for(int z=base.getZ()-18;z<=base.getZ()+18;z++)
                    p.serverLevel().setBlock(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
            });
        }
        if(tick==745&&!mc.player.onGround())throw new IllegalStateException("Wing QA did not land on the acceptance platform");
        if(tick%10==0){var visual=com.codex.blacklight.client.animation.WingFlightVisuals.sample(mc.player,1);samples.add("flow,"+tick+","+visual.speed()+","+visual.flow()+","+visual.thrust()+","+visual.brake());}
        if(tick%10==0){var p=mc.player;var s=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WING_FLIGHT);var pose=com.codex.blacklight.client.animation.BlacklightRenderPose.sample(p,.5F);var r=pose.orElseEmpty("Root").rotation().getEulerAnglesXYZ(new org.joml.Vector3f());samples.add(tick+","+s.phase()+","+p.getX()+","+p.getY()+","+p.getZ()+","+p.getDeltaMovement().length()+","+p.onGround()+","+p.getYRot()+","+p.yBodyRot+","+yaw+","+LocomotionVisualState.get(p.getId()).phase()+","+Math.toDegrees(r.x)+","+Math.toDegrees(r.y)+","+Math.toDegrees(r.z)+","+pose.orElseEmpty("Chest").rotation().x+","+pose.orElseEmpty("Arm_R").rotation().x+","+pose.orElseEmpty("Leg_R").rotation().x+","+pose.orElseEmpty("Root").translation().y);}
        if(tick==760)PacketDistributor.sendToServer(new PowerSelectPayload(PowerForm.SOVEREIGN_WINGS.ordinal(),false));
        if(tick==790){
            mc.options.hideGui=false;
            var wheel=new com.codex.blacklight.client.screen.PowerWheelScreen(SkillBindings.find("key.blacklight.cycle_form"));
            mc.setScreen(wheel);wheel.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB,0,0);
            for(int i=0;i<5;i++)wheel.mouseScrolled(0,0,-1);
        }
        if(tick==810){
            WRITER.shutdown();try{WRITER.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}
            samples.add("capture,"+captured+",dropped,"+dropped);
            try{Files.createDirectories(ROOT);Files.write(ROOT.resolve("client-"+RUN+".csv"),samples);}catch(Exception ex){throw new RuntimeException(ex);}
            finished=true;mc.stop();
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void movement(MovementInputUpdateEvent e){
        var mc=Minecraft.getInstance();if(!guarded(mc)||!initialized||finished)return;
        var input=e.getInput();input.forwardImpulse=qaForward;input.leftImpulse=-qaSide;input.jumping=false;input.shiftKeyDown=false;
        input.up=qaForward>0;input.down=qaForward<0;input.left=qaSide<0;input.right=qaSide>0;
    }
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(!guarded(mc)||mc.player==null||tick<80||tick>800)return;
        long now=System.nanoTime();if(now-lastCapture<66_666_666L)return;lastCapture=now;
        if(pending.get()>=6){dropped++;return;}
        lastFrame=tick;
        var image=Screenshot.takeScreenshot(mc.getMainRenderTarget());int frame=captured++;int at=tick;pending.incrementAndGet();
        WRITER.execute(()->{try(image){Files.createDirectories(ROOT.resolve("frames-"+RUN));image.writeToFile(ROOT.resolve("frames-"+RUN+"/frame-"+String.format("%05d",frame)+"-tick-"+String.format("%04d",at)+".png"));}
            catch(Exception ex){throw new RuntimeException(ex);}finally{pending.decrementAndGet();}});
    }
}
