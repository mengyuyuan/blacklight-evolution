package com.codex.blacklight.client;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.tick.*;

/** Native mesh/socket, release, impact/audio and retreat audit in the disposable QA save. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class SpitterVisual {
    private static final String[] NAMES={"shot","volley","yaw_volley","wall","nozzle_blocked","retreat","cliff"};
    private static final JsonArray frames=new JsonArray(),shots=new JsonArray(),sounds=new JsonArray(),timeline=new JsonArray();
    private static final Set<String> saved=new HashSet<>();
    private static volatile boolean ready,done;private static volatile String error;private static volatile int actorId,targetId,age,index;
    private static boolean initialized,queued;private static int bootstrap;private static long started;private static Path output;
    private static MutantMove move(){return index>=5?MutantMove.SPITTER_RETREAT:index==1||index==2?MutantMove.SPITTER_VOLLEY:MutantMove.SPITTER_SHOT;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(done||System.getProperty("blacklight.test.spitter")==null)return;var mc=Minecraft.getInstance();
        try{
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())){
                mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;
            }
            if(!initialized&&++bootstrap>2400)throw new IllegalStateException("Spitter QA startup timeout");
            var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals("Creature Skills QA Final"))throw new IllegalStateException("Disposable save required");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.spitter")).toAbsolutePath().normalize();if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: required");Files.createDirectories(output);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(50);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.setScreen(null);initialized=true;Files.writeString(output.resolve("status.txt"),"Capturing spitter scenes");
            }
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;setup();return;}
            if(ready&&age>62){ready=false;if(++index>=NAMES.length){done=true;var out=new JsonObject();out.add("frames",frames);out.add("shots",shots);out.add("sounds",sounds);out.add("timeline",timeline);Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));Files.writeString(output.resolve("status.txt"),"Captured; pending review");mc.stop();}else queued=false;}
        }catch(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Spitter visual audit",ex);try{if(output!=null)Files.writeString(output.resolve("status.txt"),error);}catch(Exception ignored){}mc.stop();}
    }
    private static void setup(){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{
        var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
        for(var entity:java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(Objects::nonNull).toList())if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
        level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
        for(int x=-18;x<=20;x++)for(int z=-16;z<=22;z++){for(int y=78;y<=80;y++)level.setBlock(new BlockPos(x,y,z),Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),2);for(int y=81;y<105;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        var actor=ModEntities.BLACKLIGHT_SPITTER.get().create(level);actor.setPos(.5,81,.5);actor.setPersistenceRequired();actor.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(.05);actor.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);actor.setYRot(index==2?90:0);actor.yBodyRot=actor.yBodyRotO=actor.getYRot();level.addFreshEntity(actor);
        var target=EntityType.COW.create(level);target.setPos(actor.position().add(index==2?-9:0,0,index==2?0:index>=5?2:9));target.setNoAi(true);target.setPersistenceRequired();level.addFreshEntity(target);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setHealth(1000);
        p.setGameMode(GameType.SPECTATOR);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
        Vec3 offset=index==2?new Vec3(-4,.6,8):index<4?new Vec3(8,.6,4.5):index==4?new Vec3(3,.3,2.8):new Vec3(4,.6,-.8);
        Vec3 focus=index==2?new Vec3(-4,1.2,0):index<4?new Vec3(0,1.2,4.5):new Vec3(0,1.1,index==4?.5:-1.2);
        Vec3 camera=actor.position().add(offset),look=actor.position().add(focus).subtract(camera.add(0,p.getEyeHeight(),0));p.connection.teleport(camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
        actorId=actor.getId();targetId=target.getId();started=level.getGameTime()+20;age=-20;ready=true;
    }catch(Exception ex){error=ex.toString();}});}
    @SubscribeEvent public static void server(LevelTickEvent.Post e){
        if(!initialized||!ready||done||e.getLevel().isClientSide)return;var server=Minecraft.getInstance().getSingleplayerServer();if(server==null||e.getLevel()!=server.overworld())return;
        age=(int)(e.getLevel().getGameTime()-started);var actor=(BlacklightMutant)e.getLevel().getEntity(actorId);var target=(Mob)e.getLevel().getEntity(targetId);if(actor==null||target==null){error="Missing actor";return;}
        if(age==0&&!actor.beginAttack(move(),target))error="Cannot begin spitter move";
        if(age==5&&index==3)for(int x=-2;x<4;x++)for(int y=81;y<85;y++)e.getLevel().setBlock(new BlockPos(x,y,4),Blocks.STONE.defaultBlockState(),2);
        if(age==5&&index==4){var socket=SpitterMouth.sample(move(),14,actor.position(),0);e.getLevel().setBlock(BlockPos.containing(socket.throat().lerp(socket.lip(),.65)),Blocks.STONE.defaultBlockState(),2);}
        if(age==0&&index==6)for(int x=-4;x<5;x++)for(int z=-6;z<0;z++)for(int y=78;y<=80;y++)e.getLevel().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
        var row=new JsonObject();row.addProperty("scene",index);row.addProperty("age",age);row.addProperty("health",target.getHealth());row.add("actor",new Gson().toJsonTree(actor.position()));row.addProperty("move",actor.attackMove().name());timeline.add(row);
    }
    @SubscribeEvent public static void join(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e){
        if(!ready||done||e.getLevel().isClientSide||!(e.getEntity() instanceof BlacklightSpit spit)||!(spit.getOwner() instanceof BlacklightMutant m)||m.getId()!=actorId)return;
        var row=new JsonObject();row.addProperty("scene",index);row.addProperty("age",m.attackAge(0));row.add("at",new Gson().toJsonTree(spit.position()));row.add("velocity",new Gson().toJsonTree(spit.getDeltaMovement()));row.addProperty("mouthError",spit.position().distanceTo(SpitterMouth.sample(m.attackMove(),m.attackAge(0),m.position(),m.getYRot()).lip()));shots.add(row);
    }
    @SubscribeEvent public static void sound(net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition e){
        if(!ready||done||e.getLevel().isClientSide||e.getSound()==null)return;String id=e.getSound().value().getLocation().toString();if(!id.startsWith("blacklight:infection."))return;
        var row=new JsonObject();row.addProperty("scene",index);row.addProperty("age",e.getLevel().getGameTime()-started);row.addProperty("event",id);row.add("at",new Gson().toJsonTree(e.getPosition()));row.addProperty("volume",e.getNewVolume());row.addProperty("pitch",e.getNewPitch());sounds.add(row);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(!initialized||!ready||done)return;var mc=Minecraft.getInstance();if(mc.level==null||!(mc.level.getEntity(actorId) instanceof BlacklightMutant actor))return;
        if(!(mc.getEntityRenderDispatcher().getRenderer(actor) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> renderer))return;
        var bone=(software.bernie.geckolib.cache.object.GeoBone)renderer.getGeoModel().getAnimationProcessor().getBone("head");if(bone==null)return;boolean tracked=bone.isTrackingMatrices();bone.setTrackingMatrices(true);if(!tracked||age<0)return;
        float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);int sub=age>=10&&age<=36?(int)(partial*3):0;String name=index+"-"+NAMES[index]+"-"+String.format(Locale.ROOT,"%03d",age)+(sub==0?"":"-sub"+sub)+".png";if(!saved.add(name))return;
        try{
            try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(output.resolve(name));}
            var row=new JsonObject();row.addProperty("image",name);row.addProperty("scene",index);row.addProperty("age",age);row.addProperty("partial",partial);row.addProperty("attackAge",actor.attackAge(partial));row.addProperty("move",actor.attackMove().name());
            if(actor.committedAttack()){
                var matrix=new org.joml.Matrix4f(bone.getWorldSpaceMatrix());matrix.m00(matrix.m00()-2).m11(matrix.m11()-2).m22(matrix.m22()-2);
                var drawn=matrix.transformPosition(new org.joml.Vector3f(0,-2/16F,-10/16F));
                // Gecko's tracked matrix adds Entity.position(), not the interpolated draw origin.
                var actual=new Vec3(drawn.x,drawn.y,drawn.z).subtract(actor.position()).add(actor.getPosition(partial));
                float yaw=net.minecraft.util.Mth.rotLerp(partial,actor.yBodyRotO,actor.yBodyRot);var expected=SpitterMouth.sample(actor.attackMove(),actor.attackAge(partial),actor.getPosition(partial),yaw).lip();
                row.add("drawnMouth",new Gson().toJsonTree(actual));row.add("expectedMouth",new Gson().toJsonTree(expected));row.addProperty("mouthError",actual.distanceTo(expected));
            }
            var projectiles=new JsonArray();for(var entity:mc.level.entitiesForRendering())if(entity instanceof BlacklightSpit){var p=new JsonObject();p.add("at",new Gson().toJsonTree(entity.getPosition(partial)));p.addProperty("id",entity.getId());projectiles.add(p);}row.add("projectiles",projectiles);frames.add(row);
        }catch(Exception ex){error=ex.toString();}
    }
}
