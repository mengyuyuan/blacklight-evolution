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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;

/** Opt-in native frames. Hidden, muted, and restricted to the disposable QA save. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class FlyerVisual {
    private static final JsonArray frames=new JsonArray(),events=new JsonArray(),hits=new JsonArray(),presentations=new JsonArray();
    private static final Set<String> saved=new HashSet<>();private static final Set<Long> receipts=new HashSet<>();
    private static volatile boolean ready,done;private static volatile String error;private static volatile int actorId,targetId,age,index;
    private static boolean initialized,queued;private static int bootstrap,stage=-1;private static long started;private static Path output;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;
        if(done||System.getProperty("blacklight.test.flyer")==null)return;var mc=Minecraft.getInstance();
        try{
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())){
                mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;
            }
            if(!initialized&&++bootstrap>2400)throw new IllegalStateException("Flyer startup timeout");
            var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals("Creature Skills QA Final"))throw new IllegalStateException("Disposable QA save required");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.flyer")).toAbsolutePath().normalize();if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: required");
                Files.createDirectories(output);mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(55);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.setScreen(null);initialized=true;Files.writeString(output.resolve("status.txt"),"Flyer capture running");
            }
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;setup();return;}
            if(ready&&age>125){ready=false;if(++index==6){done=true;var result=new JsonObject();result.add("frames",frames);result.add("events",events);result.add("hits",hits);result.add("presentations",presentations);Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));Files.writeString(output.resolve("status.txt"),"Captured; pending independent frame review");mc.stop();}else queued=false;}
        }catch(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Flyer QA",ex);try{if(output!=null)Files.writeString(output.resolve("status.txt"),error);}catch(Exception ignored){}mc.stop();}
    }
    private static void setup(){
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();int scene=index;
        server.execute(()->{try{
            var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
            for(var entity:java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(Objects::nonNull).toList())if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
            level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
            for(int x=-18;x<=22;x++)for(int z=-16;z<=30;z++){
                level.setBlock(new BlockPos(x,80,z),(x%2==0||z%2==0?Blocks.LIGHT_GRAY_CONCRETE:Blocks.WHITE_CONCRETE).defaultBlockState(),2);
                for(int y=81;y<106;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            p.setGameMode(GameType.SPECTATOR);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
            Vec3 camera=scene==1?new Vec3(14,88,2):new Vec3(20,90,9),look=new Vec3(0,84,9).subtract(camera.add(0,p.getEyeHeight(),0));
            p.connection.teleport(camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
            var a=EncounterEntities.CREATURES.get(EncounterKind.FLYER).get().create(level);a.setPos(0,scene==4?81:85,0);a.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(.25);a.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);a.setPersistenceRequired();level.addFreshEntity(a);
            var v=EntityType.COW.create(level);v.setPos(0,81,13);v.setNoAi(true);level.addFreshEntity(v);v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);v.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);v.setHealth(2000);
            actorId=a.getId();targetId=v.getId();stage=-1;started=level.getGameTime()+30;age=-30;ready=true;
        }catch(Exception ex){error=ex.toString();}});
    }
    @SubscribeEvent public static void server(TickEvent.LevelTickEvent e){if(e.phase!=TickEvent.Phase.END)return;
        if(!initialized||!ready||done||e.level.isClientSide)return;var server=Minecraft.getInstance().getSingleplayerServer();if(server==null||e.level!=server.overworld())return;
        age=(int)(e.level.getGameTime()-started);var a=(EncounterMob)e.level.getEntity(actorId);var v=(LivingEntity)e.level.getEntity(targetId);if(a==null||v==null){error="Missing flyer actor";return;}
        if(age==0){if(!a.begin(EncounterMove.DIVE,v))error="Cannot begin dive: "+a.flyerStage()+" ticks="+a.tickCount;}
        if(index==1&&age==18)for(int x=-6;x<=6;x++)for(int y=81;y<=95;y++)e.level.setBlock(new BlockPos(x,y,9),Blocks.DEEPSLATE.defaultBlockState(),2);
        if(index==2&&age==18)v.setPos(v.position().add(7,0,0));
        if(index==3&&age==18)a.hurt(a.damageSources().mobAttack(v),2);
        if(index==5&&age==18){v.setPos(v.position().add(0,5,0));for(int x=-5;x<=5;x++)for(int z=-3;z<=18;z++)e.level.setBlock(new BlockPos(x,82,z),Blocks.STONE.defaultBlockState(),2);}
        if(stage!=a.flyerStage()){stage=a.flyerStage();event("stage",a,v);}
        if(age==120){event("end",a,v);a.setNoAi(true);}
    }
    private static void event(String name,EncounterMob a,LivingEntity v){var r=new JsonObject();r.addProperty("name",name);r.addProperty("scene",index);r.addProperty("age",age);r.addProperty("stage",a.flyerStage());r.addProperty("health",v.getHealth());r.addProperty("hits",a.flight().hits());r.addProperty("collisions",a.flight().collisions());r.add("position",new Gson().toJsonTree(a.position()));events.add(r);}
    @SubscribeEvent public static void hit(com.codex.blacklight.combat.ForgeDamageEvent.Post e){
        if(!ready||done||e.getNewDamage()<=0||!(e.getSource().getEntity() instanceof EncounterMob a)||a.getId()!=actorId)return;
        var r=new JsonObject();r.addProperty("scene",index);r.addProperty("age",age);r.addProperty("damage",e.getNewDamage());r.add("at",new Gson().toJsonTree(a.damageContact()));hits.add(r);
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){if(e.phase!=TickEvent.Phase.END)return;
        if(!initialized||!ready||done)return;var mc=Minecraft.getInstance();if(mc.level==null||!(mc.level.getEntity(actorId) instanceof EncounterMob a))return;
        if(!(mc.getEntityRenderDispatcher().getRenderer(a) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> renderer))return;
        var model=renderer.getGeoModel();var table=FlyerMotion.table();for(var n:table.bones){var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(n);if(bone!=null)bone.setTrackingMatrices(true);}
        int scene=index;float partial=mc.getFrameTime();double clock=(double)mc.level.getGameTime()+partial-started;if(clock<0||clock>119)return;
        String name=scene+"-"+String.format(Locale.ROOT,"%03d",(int)clock)+"-"+(clock>=10&&clock<=80?(int)(partial*2):0)+".png";if(!saved.add(name))return;
        try{
            try(var screen=Screenshot.takeScreenshot(mc.getMainRenderTarget())){screen.writeToFile(output.resolve(name));}
            var row=new JsonObject();row.addProperty("image",name);row.addProperty("scene",scene);row.addProperty("age",clock);row.addProperty("stage",a.flyerStage());row.addProperty("stageAge",a.flyerAge(partial));row.addProperty("pitch",a.getXRot());row.add("position",new Gson().toJsonTree(a.getPosition(partial)));
            double error=0;float yaw=net.minecraft.util.Mth.rotLerp(partial,a.yBodyRotO,a.yBodyRot);var pose=a.flyerPose(partial);
            for(var n:table.bones){var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(n);if(bone==null)continue;
                var matrix=new org.joml.Matrix4f(bone.getWorldSpaceMatrix());matrix.m00(matrix.m00()-2).m11(matrix.m11()-2).m22(matrix.m22()-2);
                // Gecko's tracking matrix explicitly adds entity.position(), not the
                // interpolated render origin. Compare in that same tracking frame.
                var drawn=matrix.transformPosition(new org.joml.Vector3f());var local=table.point(pose,n).rotateY((float)Math.toRadians(180-yaw));var expected=a.position().add(local.x,local.y+.01,local.z);
                error=Math.max(error,expected.distanceTo(new Vec3(drawn.x,drawn.y,drawn.z)));
            }row.addProperty("boneError",error);
            for(var event:hits){var hit=event.getAsJsonObject();if(hit.get("scene").getAsInt()!=scene)continue;var at=hit.getAsJsonObject("at");var p=new Vec3(at.get("x").getAsDouble(),at.get("y").getAsDouble(),at.get("z").getAsDouble());
                double separation=Double.POSITIVE_INFINITY;for(var surface:FlyerContact.body(pose,a.getPosition(partial),yaw))separation=Math.min(separation,surface.box().nearest(p).distanceTo(p));row.addProperty("contactDistance",separation);
            }
            for(var r:com.codex.blacklight.client.renderer.ContactEffectsPresentation.receipts())if(r.tick()>=started&&receipts.add(r.serial())){var receipt=new Gson().toJsonTree(r).getAsJsonObject();receipt.addProperty("scene",scene);receipt.addProperty("firstImage",name);receipt.addProperty("firstFrameAge",clock);receipt.addProperty("contactAge",r.tick()-started);presentations.add(receipt);}
            frames.add(row);
        }catch(Exception ex){error=ex.toString();}
    }
}
