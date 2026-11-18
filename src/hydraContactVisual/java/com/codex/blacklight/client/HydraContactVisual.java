package com.codex.blacklight.client;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.BlacklightHydra;
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

/** Opt-in hidden/muted disposable-save audit. No user input, desktop capture or real save. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HydraContactVisual {
    private static final String WORLD="Creature Skills QA Final";
    private static final int[] MOVES={3,9,10,9,3,3,3,3};
    private static final String[] NAMES={"slam","sweep","side","miss","air","wall","slab_small","cancel"};
    private static final String[] JOINTS={"Head","Claw_L","full_rig_body3"};
    private static final Vec3[] LOCAL={new Vec3(0,93/16.0,-28/16.0),new Vec3(-25/16.0,105/16.0,-40/16.0),new Vec3(0,42.85/16,1.4/16)};
    private static final JsonArray frames=new JsonArray(),hits=new JsonArray(),ground=new JsonArray(),presentations=new JsonArray(),obstacles=new JsonArray();
    private static final Set<String> saved=new HashSet<>();private static final Set<Long> receipts=new HashSet<>();
    private static volatile boolean ready,done;private static volatile String error;private static volatile int actorId,targetId,age,index;
    private static boolean initialized,queued;private static int bootstrap,groundCount,wallCount;private static long started;private static Path output;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(done||System.getProperty("blacklight.test.hydraContact")==null)return;var mc=Minecraft.getInstance();
        try{
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())){
                mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;
            }
            if(!initialized&&++bootstrap>2400)throw new IllegalStateException("Hydra QA startup timeout");
            var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals(WORLD))throw new IllegalStateException("Disposable QA save required");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.hydraContact")).toAbsolutePath().normalize();if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: required");
                Files.createDirectories(output);mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(50);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.setScreen(null);initialized=true;Files.writeString(output.resolve("status.txt"),"Hydra frame capture running");
            }
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;setup();return;}
            if(ready&&age>HydraCombatAction.get(MOVES[index]).duration+12){ready=false;
                if(++index>=MOVES.length){
                    done=true;var result=new JsonObject();result.addProperty("scenes",MOVES.length);result.add("frames",frames);result.add("hits",hits);result.add("ground",ground);result.add("presentations",presentations);result.add("obstacles",obstacles);
                    Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));Files.writeString(output.resolve("status.txt"),"Captured; pending independent frame/contact review");mc.stop();
                }else queued=false;
            }
        }catch(Exception ex){fail(ex);}
    }
    private static void fail(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Hydra contact visual audit",ex);try{if(output!=null)Files.writeString(output.resolve("status.txt"),error);}catch(Exception ignored){}Minecraft.getInstance().stop();}
    private static void setup(){
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();int scene=index;
        server.execute(()->{try{
            var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
            for(var entity:level.getAllEntities())if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
            level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
            for(int x=-18;x<=22;x++)for(int z=-16;z<=24;z++){
                level.setBlock(new BlockPos(x,80,z),(scene==6?Blocks.SMOOTH_STONE_SLAB:x%2==0||z%2==0?Blocks.LIGHT_GRAY_CONCRETE:Blocks.WHITE_CONCRETE).defaultBlockState(),2);
                for(int y=81;y<108;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            double floor=scene==6?80.5:81,air=scene==4?12:0;float size=scene==6?1.6F:2;
            p.setGameMode(GameType.SPECTATOR);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
            Vec3 camera=new Vec3(scene==5?32:20,floor+(scene==5?12:10)+air,scene==5?2:26),look=new Vec3(0,floor+(scene==5?7:4)+air,5).subtract(camera.add(0,p.getEyeHeight(),0));
            p.connection.teleport(camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
            var actor=ModEntities.BLACKLIGHT_HYDRA.get().create(level);actor.setBodySize(size);actor.setPos(0,floor+air,0);actor.setNoAi(true);actor.setNoGravity(true);actor.setYRot(0);actor.yBodyRot=0;level.addFreshEntity(actor);
            var victim=EntityType.COW.create(level);victim.setPos(0,floor+air,scene==6?7:9);victim.setNoAi(true);victim.setNoGravity(true);level.addFreshEntity(victim);
            victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);victim.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);victim.setHealth(2000);
            actorId=actor.getId();targetId=victim.getId();started=level.getGameTime()+30;age=-30;groundCount=0;wallCount=0;ready=true;
        }catch(Exception ex){error=ex.toString();}});
    }
    @SubscribeEvent public static void server(LevelTickEvent.Post e){
        if(!initialized||!ready||done||e.getLevel().isClientSide)return;var server=Minecraft.getInstance().getSingleplayerServer();if(server==null||e.getLevel()!=server.overworld())return;
        age=(int)(e.getLevel().getGameTime()-started);var actor=(BlacklightHydra)e.getLevel().getEntity(actorId);var victim=(LivingEntity)e.getLevel().getEntity(targetId);if(actor==null||victim==null){error="Missing Hydra QA entity";return;}
        if(age==0){actor.setNoAi(false);if(!actor.beginCombat(victim,MOVES[index]))error="Cannot begin Hydra "+NAMES[index];}
        if(index==1&&age==22||index==2&&age==37){var b=HydraContact.boxes(MOVES[index],index==1?30:43,index==1?"Claw_L":"Head",actor.position(),actor.getYRot(),actor.visualScale()).get(0);victim.setPos(b.center.add(0,-.6,0));victim.setDeltaMovement(Vec3.ZERO);}
        if(index==3&&age==22)victim.setPos(actor.position().add(0,0,2));
        if(index==5&&age==35)for(int x=-18;x<=22;x++)for(int y=81;y<102;y++)e.getLevel().setBlock(new BlockPos(x,y,6),Blocks.STONE.defaultBlockState(),2);
        if(index==7&&age==40){actor.cancelCombat();actor.setNoAi(true);}
        if(actor.wallImpacts()>wallCount){wallCount=actor.wallImpacts();var row=new JsonObject();row.addProperty("scene",index);row.addProperty("age",age);row.addProperty("stop",actor.recoilStart());obstacles.add(row);}
        if(actor.groundImpacts()>groundCount){groundCount=actor.groundImpacts();var row=new JsonObject();row.addProperty("scene",index);row.addProperty("age",actor.lastGroundAge());row.add("at",new Gson().toJsonTree(actor.groundImpactPoint()));ground.add(row);}
        if(age>HydraCombatAction.get(MOVES[index]).duration)actor.setNoAi(true);
    }
    @SubscribeEvent public static void hit(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e){
        if(!ready||done||e.getNewDamage()<=0||!(e.getSource().getEntity() instanceof BlacklightHydra actor)||actor.getId()!=actorId)return;
        var row=new JsonObject();row.addProperty("scene",index);row.addProperty("age",actor.combatAge(0));row.addProperty("damage",e.getNewDamage());row.addProperty("style",actor.damageStyle().name());row.add("at",new Gson().toJsonTree(actor.damageContact()));hits.add(row);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(!initialized||!ready||done)return;var mc=Minecraft.getInstance();if(mc.level==null||!(mc.level.getEntity(actorId) instanceof BlacklightHydra actor))return;
        if(!(mc.getEntityRenderDispatcher().getRenderer(actor) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> renderer))return;var model=renderer.getGeoModel();
        for(String joint:JOINTS){var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(HydraContact.bone(joint));if(bone!=null)bone.setTrackingMatrices(true);}
        if(age<0)return;
        float partial=mc.getTimer().getGameTimeDeltaPartialTick(false),clock=actor.combatAge(partial);int t=(int)clock;
        boolean contact=MOVES[index]==9?clock>=24&&clock<=34:MOVES[index]==10?clock>=40&&clock<=60:clock>=44&&clock<=66;
        int sub=contact?(int)(partial*3):0;String name=index+"-"+NAMES[index]+"-"+String.format(Locale.ROOT,"%03d",t)+(sub==0?"":"-sub"+sub)+".png";if(!saved.add(name))return;
        try{
            try(var screenshot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){screenshot.writeToFile(output.resolve(name));}
            var row=new JsonObject();row.addProperty("image",name);row.addProperty("scene",index);row.addProperty("age",clock);row.addProperty("serverAge",age);row.addProperty("clientWorldClock",ContactEffectsQueue.renderTime(mc.level.getGameTime(),partial));
            float renderYaw=net.minecraft.util.Mth.rotLerp(partial,actor.yBodyRotO,actor.yBodyRot);
            row.addProperty("poseAge",actor.combatPoseAge(partial));row.addProperty("action",actor.action());row.addProperty("recoilStart",actor.recoilStart());
            if(index==5&&clock>=36){
                var wall=new net.minecraft.world.phys.AABB(-18,81,6,23,102,7).deflate(.002);
                row.addProperty("wallOverlap",HydraContact.body(actor.motionPose(partial),actor.position(),renderYaw,actor.visualScale()).stream().filter(b->b.touches(wall)).count());
                row.addProperty("unconstrainedWallOverlap",HydraContact.body(com.codex.blacklight.city.HydraMotion.INSTANCE.sample(3,clock),actor.position(),renderYaw,actor.visualScale()).stream().filter(b->b.touches(wall)).count());
            }
            row.addProperty("debris",com.codex.blacklight.client.renderer.ContactTerrainDebris.activeCount());row.addProperty("yaw",renderYaw);
            for(var r:com.codex.blacklight.client.renderer.ContactEffectsPresentation.receipts())if(receipts.add(r.serial())){var receipt=new Gson().toJsonTree(r).getAsJsonObject();receipt.addProperty("scene",index);receipt.addProperty("firstImage",name);receipt.addProperty("firstObservedPoseAge",clock);receipt.addProperty("contactAge",r.tick()-started);presentations.add(receipt);}
            for(int j=0;j<JOINTS.length;j++){
                var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(HydraContact.bone(JOINTS[j]));if(bone==null)continue;
                var matrix=new org.joml.Matrix4f(bone.getWorldSpaceMatrix());matrix.m00(matrix.m00()-2).m11(matrix.m11()-2).m22(matrix.m22()-2);
                var p=LOCAL[j];var actual=matrix.transformPosition(new org.joml.Vector3f((float)(p.x-bone.getPivotX()/16),(float)(p.y-bone.getPivotY()/16),(float)(p.z-bone.getPivotZ()/16)));
                // Gecko preRender saves entityRenderTranslations after this renderer's
                // outer scale. Inverting it for tracked world space cancels that scale.
                // Reapply the unchanged outer render scale about the entity origin.
                Vec3 drawn=actor.position().add(new Vec3(actual.x,actual.y,actual.z).subtract(actor.position()).scale(actor.visualScale()));
                var expected=HydraContact.boxes(actor.motionPose(partial),JOINTS[j],actor.position(),renderYaw,actor.visualScale()).get(0).center;
                row.addProperty(JOINTS[j]+"Error",expected.distanceTo(drawn));row.add(JOINTS[j]+"Expected",new Gson().toJsonTree(expected));row.add(JOINTS[j]+"Actual",new Gson().toJsonTree(drawn));
            }frames.add(row);
        }catch(Exception ex){error=ex.toString();}
    }
}
