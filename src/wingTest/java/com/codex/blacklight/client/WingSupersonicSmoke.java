package com.codex.blacklight.client;

import com.codex.blacklight.movement.*;
import com.codex.blacklight.network.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import com.codex.blacklight.forge.PacketDistributor;
import java.nio.file.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Disposable single-player route: real inputs, upgrade packets, collision and server telemetry. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class WingSupersonicSmoke {
    static final Path ROOT=Path.of(System.getProperty("blacklight.test.wingDirectory","run-wing-qa"));
    static final String RUN="wings12-sonic-"+System.currentTimeMillis();
    static final ConcurrentLinkedQueue<String> ROWS=new ConcurrentLinkedQueue<>();
    static final boolean CLIMB=Boolean.getBoolean("blacklight.test.wingSonicClimb");
    static int lastRendered=-1;
    static int tick,forward;static boolean initialized,finished,up,boost;static double maxSpeed;
    static volatile boolean prepared;
    static volatile int wall=Integer.MAX_VALUE;static volatile boolean collision,serverSonic;static int captured;
    static boolean guarded(Minecraft mc){return Boolean.getBoolean("blacklight.test.wingSonic")&&mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(ROOT.resolve("client").toAbsolutePath().normalize());}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(!guarded(mc)||mc.player==null||mc.getSingleplayerServer()==null||finished)return;
        var uuid=mc.player.getUUID();var integrated=mc.getSingleplayerServer();
        if(!initialized){
            initialized=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(12);mc.options.simulationDistance().set(12);
            mc.options.broadcastOptions();
            mc.options.hideGui=false;
            integrated.execute(()->{
                var p=integrated.getPlayerList().getPlayer(uuid);if(p==null)return;
                verifyNetwork(p.serverLevel());
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.UTILITY_POWERS,0);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.UTILITY_POWERS);WingFlightSystem.stop(p);
                p.setGameMode(GameType.SURVIVAL);p.getAbilities().flying=false;p.getAbilities().mayfly=false;p.onUpdateAbilities();
                var s=new BlacklightState();s.infect();s.setPrototypeMode(true);s.unlock(PowerForm.SOVEREIGN_WINGS);s.activate(PowerForm.NONE);s.addEvolutionPoints(10000);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,s);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);
                p.serverLevel().setDayTime(6000);p.serverLevel().setWeatherParameters(12000,0,false,false);
                for(int x=-147;x<=-141;x++)for(int z=-3;z<=3;z++)p.serverLevel().setBlock(new BlockPos(x,200,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                p.teleportTo(-144,201,0);p.setDeltaMovement(0,0,0);p.setYRot(0);p.setXRot(0);prepared=true;
            });return;
        }
        if(!prepared)return;
        tick++;
        if(tick==80){
            for(String id:new String[]{"wing_speed_1","wing_speed_2","wing_speed_3"})PacketDistributor.sendToServer(new UpgradePurchasePayload(id));
            PacketDistributor.sendToServer(new PowerSelectPayload(PowerForm.SOVEREIGN_WINGS.ordinal(),false));
        }
        if(tick<90)return;
        up=tick<125||(CLIMB&&tick>=140&&tick<260);forward=tick>=140&&tick<(CLIMB?260:360)?1:0;boost=forward==1;
        mc.player.setXRot(CLIMB?-75:0);
        mc.options.setCameraType(tick>=185&&tick<=235?CameraType.THIRD_PERSON_FRONT:CameraType.THIRD_PERSON_BACK);
        ClientWingFlight.applyInput(mc,forward,0,up,false,boost,0);
        double speed=mc.player.getDeltaMovement().length();maxSpeed=Math.max(maxSpeed,speed);
        if(tick==220)inspectSonic(mc,integrated,uuid);
        if(tick%5==0){
            int at=tick;var p=mc.player;
            ROWS.add("client,"+at+","+p.getX()+","+p.getY()+","+p.getZ()+","+speed+","+WingFlightSystem.tier(p));
            integrated.execute(()->{
                var server=integrated.getPlayerList().getPlayer(uuid);if(server==null)return;
                double velocity=server.getDeltaMovement().length();if(velocity>=WingFlightTuning.SONIC_SPEED)serverSonic=true;
                ROWS.add("server,"+at+","+server.getX()+","+server.getY()+","+server.getZ()+","+velocity+","+WingFlightSystem.tier(server));
                if(at>=320&&wall!=Integer.MAX_VALUE&&server.getZ()<wall&&wall-server.getZ()<1)collision=true;
            });
        }
        if(tick==300&&!CLIMB)integrated.execute(()->{
            var p=integrated.getPlayerList().getPlayer(uuid);if(p==null)return;
            wall=p.blockPosition().getZ()+8;int y=p.blockPosition().getY();
            int px=p.blockPosition().getX();
            if(p.serverLevel().getChunkSource().getChunkNow(px>>4,wall>>4)==null){ROWS.add("wall,unloaded-skipped");wall=Integer.MAX_VALUE;return;}
            for(int x=px-5;x<=px+5;x++)for(int h=y-4;h<=y+8;h++)p.serverLevel().setBlock(new BlockPos(x,h,wall),Blocks.IRON_BLOCK.defaultBlockState(),3);
            ROWS.add("wall,"+wall);
        });
        if(tick==(CLIMB?300:400)){
            finished=true;integrated.execute(()->mc.execute(()->{
                try{Files.createDirectories(ROOT);ROWS.add("result,max="+maxSpeed+",serverSonic="+serverSonic+",collision="+collision+",stopped="+(speed<.001));Files.write(ROOT.resolve(RUN+".csv"),ROWS);}catch(Exception e){throw new RuntimeException(e);}
                mc.stop();
            }));
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void movement(MovementInputUpdateEvent e){
        if(!guarded(Minecraft.getInstance())||!initialized||finished)return;
        var i=e.getInput();i.forwardImpulse=forward;i.leftImpulse=0;i.jumping=false;i.shiftKeyDown=false;i.up=forward>0;i.down=i.left=i.right=false;
        var p=Minecraft.getInstance().player;p.setXRot(CLIMB?-75:0);p.setYRot(0);
        com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).setCameraYaw(0);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void sideCamera(ViewportEvent.ComputeCameraAngles event){
        var mc=Minecraft.getInstance();if(!guarded(mc)||mc.player==null||tick<185||tick>235)return;
        var focus=mc.player.getPosition((float)event.getPartialTick()).add(0,2,1);
        var position=focus.add(10,3,-3);var aim=focus.subtract(position);
        var access=(com.codex.blacklight.mixin.client.PrototypeCameraAccess)event.getCamera();
        access.blacklight$setPosition(position);
        event.setYaw((float)Math.toDegrees(Math.atan2(-aim.x,aim.z)));
        event.setPitch((float)-Math.toDegrees(Math.atan2(aim.y,aim.horizontalDistance())));event.setRoll(0);
    }
    @SubscribeEvent public static void rendered(RenderPlayerEvent.Pre e){if(e.getEntity()==Minecraft.getInstance().player)lastRendered=tick;}
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(!guarded(mc)||mc.player==null||finished)return;
        int shot=CLIMB?(tick>=190&&tick<=225?tick-189:tick>=280?37:0):(tick>=370?8:tick>=330?7:tick>=280?6:tick>=230?5:tick>=220?4:tick>=210?3:tick>=200?2:tick>=190?1:0);
        if(shot<=captured)return;captured=shot;ROWS.add("frame,"+tick+",playerRendered="+(lastRendered==tick));
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(ROOT.resolve(RUN+"-"+shot+".png"));}catch(Exception ex){throw new RuntimeException(ex);}
    }
    private static void inspectSonic(Minecraft mc,net.minecraft.client.server.IntegratedServer integrated,java.util.UUID uuid){
        try{
            var tracks=com.codex.blacklight.client.renderer.WingFlightEffects.class.getDeclaredField("TRACKS");tracks.setAccessible(true);
            var track=((java.util.Map<?,?>)tracks.get(null)).get(mc.player);
            var shock=track.getClass().getDeclaredField("shockBorn");shock.setAccessible(true);
            ROWS.add("effects,clientShock="+(shock.getDouble(track)>0));
        }catch(Exception ex){throw new RuntimeException(ex);}
        integrated.execute(()->{try{
            var inputs=WingFlightSystem.class.getDeclaredField("INPUTS");inputs.setAccessible(true);
            var control=((java.util.Map<?,?>)inputs.get(null)).get(integrated.getPlayerList().getPlayer(uuid));
            var sonic=control.getClass().getDeclaredField("sonic");sonic.setAccessible(true);
            var gate=sonic.get(control);var last=gate.getClass().getDeclaredField("last");last.setAccessible(true);
            ROWS.add("effects,serverBoom="+(last.getLong(gate)>0));
        }catch(Exception ex){throw new RuntimeException(ex);}});
    }
    /** Real inbound handler, a non-owner identity and a packet sink (no socket required). */
    private static void verifyNetwork(net.minecraft.server.level.ServerLevel level){
        try{
            for(int x=3;x<=5;x++)for(int z=-1;z<=9;z++)level.getChunk(x,z);
            for(int count=1;count<=5;count++){
                var p=networkPlayer(level,64);
                for(int packet=1;packet<=count;packet++)p.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(64,300,22*packet,false));
                if(Math.abs(p.getZ()-22*count)>.001)throw new AssertionError("Authorized packet batch rejected: "+count+" at "+p.position());
                p.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(64,300,p.getZ()+1000,false));
                if(p.getZ()>22*count+.001)throw new AssertionError("Over-budget teleport accepted");
                ROWS.add("network,batch="+count+",accepted=true,teleportRejected=true");
                p.getTextFilter().leave();level.removePlayerImmediately(p,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
            for(int y=300;y<304;y++)level.setBlock(new BlockPos(80,y,10),Blocks.IRON_BLOCK.defaultBlockState(),3);
            var p=networkPlayer(level,80);
            p.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(80,300,22,false));
            if(p.getZ()>10)throw new AssertionError("Server accepted a wall-crossing move");
            ROWS.add("network,wallRejected=true");p.getTextFilter().leave();level.removePlayerImmediately(p,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }catch(Exception ex){throw new RuntimeException("Supersonic inbound packet validation failed",ex);}
    }
    private static net.minecraft.server.level.ServerPlayer networkPlayer(net.minecraft.server.level.ServerLevel level,double x)throws Exception{
        var profile=new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"SonicPacketQA");
        var p=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,profile);
        var sink=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener callback){}
        };
        var listener=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),sink,p){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener callback){}
        };
        p.setGameMode(GameType.SURVIVAL);p.setPos(x,300,0);p.setDeltaMovement(0,0,22);
        var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.SOVEREIGN_WINGS);state.addEvolutionPoints(10000);
        for(var upgrade:new MovementUpgrade[]{MovementUpgrade.WING_SPEED_1,MovementUpgrade.WING_SPEED_2,MovementUpgrade.WING_SPEED_3})state.buyMovementUpgrade(upgrade);
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,state);com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.UTILITY_POWERS,PowerForm.SOVEREIGN_WINGS.bit());
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.WING_FLIGHT,new WingFlightState(WingFlightState.BOOST,level.getGameTime(),0,level.getGameTime(),true));
        level.addNewPlayer(p);
        listener.resetPosition();
        var tickField=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("tickCount");tickField.setAccessible(true);tickField.setInt(listener,1);
        return p;
    }
}
