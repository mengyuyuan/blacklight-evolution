package com.codex.blacklight.client;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.ModEntities;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;


/** Incoming contact audit in a hidden, muted disposable client. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HydraHurtVisual {
    private static final String[] NAMES={"head_small","head_large_yaw","horn_hit","miss","wall","immune"};
    private static final JsonArray frames=new JsonArray(),hits=new JsonArray(),presentations=new JsonArray(),attempts=new JsonArray();
    private static final Set<String> saved=new HashSet<>();private static final Set<Long> receipts=new HashSet<>();
    private static volatile boolean ready,done;private static volatile String error;private static volatile int actorId,age,index;
    private static boolean initialized,queued;private static int bootstrap;private static long started;private static Path output;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;
        if(done||System.getProperty("blacklight.test.hydraHurt")==null)return;var mc=Minecraft.getInstance();
        try{
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())){
                mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;
            }
            if(!initialized&&++bootstrap>2400)throw new IllegalStateException("Incoming QA startup timeout");
            var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals("Creature Skills QA Final"))throw new IllegalStateException("Disposable save required");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.hydraHurt")).toAbsolutePath().normalize();if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: required");Files.createDirectories(output);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(50);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.setScreen(null);initialized=true;Files.writeString(output.resolve("status.txt"),"Incoming Hydra frame capture running");
            }
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;setup();return;}
            if(ready&&age>55){ready=false;if(++index>=NAMES.length){done=true;var out=new JsonObject();out.add("frames",frames);out.add("hits",hits);out.add("presentations",presentations);out.add("attempts",attempts);Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));Files.writeString(output.resolve("status.txt"),"Captured; pending independent frame/contact review");mc.stop();}else queued=false;}
        }catch(Exception ex){fail(ex);}
    }
    private static void fail(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Hydra incoming visual audit",ex);try{if(output!=null)Files.writeString(output.resolve("status.txt"),error);}catch(Exception ignored){}Minecraft.getInstance().stop();}
    private static String bone(){return index==2?"claw_left":"head";}
    private static HydraContact.Box target(BlacklightHydra actor){return actor.hurtRegions(0).stream().filter(r->r.bone().equals(bone())).findFirst().orElseThrow().boxes().get(0);}
    private static void setup(){
        var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{
            var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
            for(var entity:level.getAllEntities())if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
            level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
            for(int x=-18;x<=22;x++)for(int z=-16;z<=24;z++){level.setBlock(new BlockPos(x,80,z),Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),2);for(int y=81;y<112;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            var actor=ModEntities.BLACKLIGHT_HYDRA.get().create(level);actor.setBodySize(index==1?2.4F:1.6F);actor.setPos(0,81,0);actor.setNoAi(true);actor.setNoGravity(true);actor.setYRot(index==1?75:0);actor.yBodyRot=actor.yBodyRotO=actor.getYRot();level.addFreshEntity(actor);actor.updateParts();
            if(index==5)actor.setInvulnerable(true);
            p.setGameMode(GameType.SPECTATOR);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
            frameActor(actor,p);
            actorId=actor.getId();started=level.getGameTime()+20;age=-20;ready=true;
        }catch(Exception ex){error=ex.toString();}});
    }
    @SubscribeEvent public static void server(TickEvent.LevelTickEvent e){if(e.phase!=TickEvent.Phase.END)return;
        if(!initialized||!ready||done||e.level.isClientSide)return;var server=Minecraft.getInstance().getSingleplayerServer();if(server==null||e.level!=server.overworld())return;
        age=(int)(e.level.getGameTime()-started);var actor=(BlacklightHydra)e.level.getEntity(actorId);if(actor==null){error="Missing Hydra";return;}
        var viewer=server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());if(viewer!=null)frameActor(actor,viewer);
        if(age==20){
            var at=target(actor).center;var from=at.add(5,0,0);if(index==3){at=at.add(0,5,0);from=from.add(0,5,0);}
            if(index==4)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)e.level.setBlock(BlockPos.containing(from.lerp(at,.35)).offset(0,y,z),Blocks.STONE.defaultBlockState(),2);
            var arrow=EntityType.ARROW.create(e.level);arrow.setPos(from);arrow.setNoGravity(true);arrow.setDeltaMovement(at.subtract(from).normalize().scale(1.5));arrow.setBaseDamage(2);e.level.addFreshEntity(arrow);
        }
    }
    private static void frameActor(BlacklightHydra actor,net.minecraft.server.level.ServerPlayer p){
        var at=target(actor).center;Vec3 camera=at.add(8,.7,3.5),look=at.subtract(camera.add(0,p.getEyeHeight(),0));
        p.connection.teleport(camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
    }
    @SubscribeEvent public static void hit(com.codex.blacklight.combat.ForgeDamageEvent.Post e){
        if(!ready||done||e.getNewDamage()<=0||!(e.getEntity() instanceof BlacklightHydra actor)||actor.getId()!=actorId)return;
        var contact=actor.incomingContact();if(contact==null){error="Missing receiver contact";return;}
        var row=new JsonObject();row.addProperty("scene",index);row.addProperty("age",age);row.addProperty("tick",actor.level().getGameTime());row.addProperty("damage",e.getNewDamage());row.addProperty("bone",contact.bone());row.add("at",new Gson().toJsonTree(contact.point()));
        row.addProperty("surfaceError",actor.hurtRegions(0).stream().flatMap(r->r.boxes().stream()).mapToDouble(b->b.nearest(contact.point()).distanceTo(contact.point())).min().orElse(999));hits.add(row);
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){if(e.phase!=TickEvent.Phase.END)return;
        if(!initialized||!ready||done)return;var mc=Minecraft.getInstance();if(mc.level==null||!(mc.level.getEntity(actorId) instanceof BlacklightHydra actor))return;
        if(!(mc.getEntityRenderDispatcher().getRenderer(actor) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> renderer))return;
        var model=renderer.getGeoModel();var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone("head");if(bone!=null)bone.setTrackingMatrices(true);if(age<0)return;
        final int frameAge=age,scene=index;float partial=e.renderTickTime;int sub=frameAge>=18&&frameAge<=30?(int)(partial*3):0;String name=scene+"-"+NAMES[scene]+"-"+String.format(Locale.ROOT,"%03d",frameAge)+(sub==0?"":"-sub"+sub)+".png";if(!saved.add(name))return;
        try{
            try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(output.resolve(name));}
            var row=new JsonObject();row.addProperty("image",name);row.addProperty("scene",scene);row.addProperty("age",frameAge);row.addProperty("partial",partial);row.addProperty("renderTime",(double)mc.level.getGameTime()+partial);row.addProperty("health",actor.getHealth());
            for(var r:com.codex.blacklight.client.renderer.ContactEffectsPresentation.receipts())if(receipts.add(r.serial())){var entry=new Gson().toJsonTree(r).getAsJsonObject();entry.addProperty("scene",index);entry.addProperty("firstImage",name);presentations.add(entry);}
            if(bone!=null){var matrix=new org.joml.Matrix4f(bone.getWorldSpaceMatrix());matrix.m00(matrix.m00()-2).m11(matrix.m11()-2).m22(matrix.m22()-2);var actual=matrix.transformPosition(new org.joml.Vector3f(-bone.getPivotX()/16,93/16F-bone.getPivotY()/16,-28/16F-bone.getPivotZ()/16));
                var drawn=actor.position().add(new Vec3(actual.x,actual.y,actual.z).subtract(actor.position()).scale(actor.visualScale()));
                var expected=HydraContact.boxes(actor.motionPose(partial),"Head",actor.position(),net.minecraft.util.Mth.rotLerp(partial,actor.yBodyRotO,actor.yBodyRot),actor.visualScale()).get(0).center;
                row.addProperty("poseError",expected.distanceTo(drawn));
            }row.add("effects",effectSnapshot());frames.add(row);
        }catch(Exception ex){error=ex.toString();}
    }
    private static JsonArray effectSnapshot() throws ReflectiveOperationException {
        var result=new JsonArray();
        var field=com.codex.blacklight.client.renderer.InfectedImpactVisuals.class.getDeclaredField("ACTIVE");field.setAccessible(true);
        for(var impact:(Iterable<?>)field.get(null)){
            var row=new JsonObject();
            for(String name:new String[]{"at","data","anchor"}){
                var f=impact.getClass().getDeclaredField(name);f.setAccessible(true);var value=f.get(impact);
                row.add(name,new Gson().toJsonTree(value));
            }
            result.add(row);
        }
        return result;
    }
}
