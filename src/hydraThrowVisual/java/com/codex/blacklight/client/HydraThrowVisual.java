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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.tick.*;

/** Opt-in native framebuffer capture, hidden/muted, disposable world only. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HydraThrowVisual {
    private static final JsonArray frames=new JsonArray(),events=new JsonArray(),hits=new JsonArray(),presentations=new JsonArray();
    private static final Set<String> saved=new HashSet<>();private static final Set<Long> receipts=new HashSet<>();
    private static volatile boolean ready,done;private static volatile String error;private static volatile int actorId,targetId,projectileId=-1,age,index;
    private static boolean initialized,queued,released;private static int bootstrap,groundCount;private static long started;private static Path output;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(done||System.getProperty("blacklight.test.hydraThrow")==null)return;var mc=Minecraft.getInstance();
        try{
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())){
                mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;
            }
            if(!initialized&&++bootstrap>2400)throw new IllegalStateException("Hydra throw startup timeout");
            var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals("Creature Skills QA Final"))throw new IllegalStateException("Disposable QA save required");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.hydraThrow")).toAbsolutePath().normalize();if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: required");
                Files.createDirectories(output);mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(60);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.setScreen(null);initialized=true;Files.writeString(output.resolve("status.txt"),"Hydra throw capture running");
            }
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;setup();return;}
            if(ready&&age>175){ready=false;if(++index==3){done=true;var result=new JsonObject();result.add("frames",frames);result.add("events",events);result.add("hits",hits);result.add("presentations",presentations);Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));Files.writeString(output.resolve("status.txt"),"Captured; pending independent frame review");mc.stop();}else queued=false;}
        }catch(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Hydra throw QA",ex);try{if(output!=null)Files.writeString(output.resolve("status.txt"),error);}catch(Exception ignored){}mc.stop();}
    }
    private static void setup(){
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();int scene=index;
        server.execute(()->{try{
            var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
            for(var entity:level.getAllEntities())if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
            level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
            for(int x=-18;x<=22;x++)for(int z=-16;z<=34;z++){
                level.setBlock(new BlockPos(x,80,z),(x%2==0||z%2==0?Blocks.LIGHT_GRAY_CONCRETE:Blocks.WHITE_CONCRETE).defaultBlockState(),2);
                for(int y=81;y<110;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            p.setGameMode(GameType.SPECTATOR);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
            Vec3 camera=new Vec3(26,97,32),look=new Vec3(0,87,11).subtract(camera.add(0,p.getEyeHeight(),0));
            p.connection.teleport(camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
            var a=ModEntities.BLACKLIGHT_HYDRA.get().create(level);a.setBodySize(scene==2?1.6F:2);a.setPos(0,81,0);a.setNoAi(true);a.setNoGravity(true);level.addFreshEntity(a);
            var v=EntityType.COW.create(level);v.setPos(0,81,21);v.setNoAi(true);v.setNoGravity(true);level.addFreshEntity(v);v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);v.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);v.setHealth(2000);
            actorId=a.getId();targetId=v.getId();projectileId=-1;released=false;groundCount=0;started=level.getGameTime()+30;age=-30;ready=true;
        }catch(Exception ex){error=ex.toString();}});
    }
    @SubscribeEvent public static void server(LevelTickEvent.Post e){
        if(!initialized||!ready||done||e.getLevel().isClientSide)return;var server=Minecraft.getInstance().getSingleplayerServer();if(server==null||e.getLevel()!=server.overworld())return;
        age=(int)(e.getLevel().getGameTime()-started);var a=(BlacklightHydra)e.getLevel().getEntity(actorId);var v=(LivingEntity)e.getLevel().getEntity(targetId);if(a==null||v==null){error="Missing throw actor";return;}
        if(age==0){a.setNoAi(false);if(!a.beginThrow(v))error="Cannot begin throw";}
        var cargo=a.combatCargo();
        if(a.groundImpacts()>groundCount){groundCount=a.groundImpacts();event("pickup_ground",a,cargo);}
        if(cargo!=null&&projectileId<0){projectileId=cargo.getId();event("attach",a,cargo);}
        if(cargo!=null&&cargo.released()&&!released){released=true;event("release",a,cargo);}
        if(index==1&&age==128)for(int x=-5;x<=5;x++)for(int y=81;y<=96;y++)e.getLevel().setBlock(new BlockPos(x,y,17),Blocks.DEEPSLATE.defaultBlockState(),2);
        if(index==2&&age==90){a.cancelCombat();a.setNoAi(true);event("cancel",a,cargo);}
        if(age==170){event("end",a,cargo);a.setNoAi(true);}
    }
    private static void event(String name,BlacklightHydra a,HydraDebris p){var r=new JsonObject();r.addProperty("name",name);r.addProperty("scene",index);r.addProperty("age",age);r.addProperty("action",a.action());r.addProperty("actionAge",a.combatAge(0));r.addProperty("health",((LivingEntity)a.level().getEntity(targetId)).getHealth());r.addProperty("recoil",a.recoilStart());if(p!=null)r.add("position",new Gson().toJsonTree(p.position()));events.add(r);}
    @SubscribeEvent public static void hit(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e){
        if(!ready||done||e.getNewDamage()<=0||!(e.getSource().getEntity() instanceof BlacklightHydra a)||a.getId()!=actorId)return;
        var r=new JsonObject();r.addProperty("scene",index);r.addProperty("age",age);r.addProperty("damage",e.getNewDamage());r.add("at",new Gson().toJsonTree(a.damageContact()));hits.add(r);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(!initialized||!ready||done)return;var mc=Minecraft.getInstance();if(mc.level==null||!(mc.level.getEntity(actorId) instanceof BlacklightHydra a))return;
        if(!(mc.getEntityRenderDispatcher().getRenderer(a) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> renderer))return;
        var model=renderer.getGeoModel();for(var n:List.of("claw_left","claw_right")){var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(n);if(bone!=null)bone.setTrackingMatrices(true);}
        int scene=index;float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);double clock=(double)mc.level.getGameTime()+partial-started;if(clock<0||clock>174)return;
        String name=scene+"-"+String.format(Locale.ROOT,"%03d",(int)clock)+"-"+(clock>=120&&clock<=156?(int)(partial*3):0)+".png";if(!saved.add(name))return;
        try{
            try(var screen=Screenshot.takeScreenshot(mc.getMainRenderTarget())){screen.writeToFile(output.resolve(name));}
            var row=new JsonObject();row.addProperty("image",name);row.addProperty("scene",scene);row.addProperty("age",clock);row.addProperty("action",a.action());row.addProperty("actionAge",a.combatAge(partial));row.addProperty("recoil",a.recoilStart());
            Vec3 drawn=Vec3.ZERO;
            for(int side=0;side<2;side++){
                var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(side==0?"claw_left":"claw_right");
                var matrix=new org.joml.Matrix4f(bone.getWorldSpaceMatrix());matrix.m00(matrix.m00()-2).m11(matrix.m11()-2).m22(matrix.m22()-2);
                var point=matrix.transformPosition(new org.joml.Vector3f((side==0?-24:24)/16F-bone.getPivotX()/16,106/16F-bone.getPivotY()/16,-40/16F-bone.getPivotZ()/16));
                drawn=drawn.add(a.position().add(new Vec3(point.x,point.y,point.z).subtract(a.position()).scale(a.visualScale())).scale(.5));
            }
            row.addProperty("gripError",a.combatGrip(partial).distanceTo(drawn));
            if(mc.level.getEntity(projectileId) instanceof HydraDebris p){row.addProperty("released",p.released());row.addProperty("flightAge",p.flightAge(partial));row.add("cargo",new Gson().toJsonTree(p.center(partial)));if(!p.released())row.addProperty("cargoGripError",p.center(partial).distanceTo(drawn));}
            for(var r:com.codex.blacklight.client.renderer.ContactEffectsPresentation.receipts())if(receipts.add(r.serial())){var receipt=new Gson().toJsonTree(r).getAsJsonObject();receipt.addProperty("scene",scene);receipt.addProperty("firstImage",name);receipt.addProperty("firstFrameAge",clock);receipt.addProperty("contactAge",r.tick()-started);presentations.add(receipt);}
            frames.add(row);
        }catch(Exception ex){error=ex.toString();}
    }
}
