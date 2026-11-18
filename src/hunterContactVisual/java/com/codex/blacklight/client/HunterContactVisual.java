package com.codex.blacklight.client;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.BlacklightHunter;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

/** Opt-in, hidden, muted frame audit restricted to the existing disposable QA save. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HunterContactVisual {
    private static final String DIRECTORY="D:/CodexCache/creature-combat-qa/client",WORLD="Creature Skills QA Final";
    private static final CreatureAttack[] MOVES={CreatureAttack.SWIPE,CreatureAttack.BARRAGE,CreatureAttack.SLAM,CreatureAttack.LEAP,CreatureAttack.DODGE,CreatureAttack.SWIPE};
    private static volatile boolean ready;
    private static boolean initialized,queued,done;
    private static int index,actorId,targetId;
    private static volatile int age=-100;
    private static long started;
    private static volatile String error;
    private static Path output;
    private static final Set<String> saved=new HashSet<>();
    private static final JsonArray frames=new JsonArray();
    private static final JsonArray presentations=new JsonArray();
    private static final Set<Long> receipts=new HashSet<>();
    private static final List<JsonObject> hits=new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<JsonObject> groundHits=new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean groundRecorded;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END||System.getProperty("blacklight.test.hunterContact","").isBlank()||done)return;
        var mc=Minecraft.getInstance();
        try{
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().replace('\\','/').equalsIgnoreCase(DIRECTORY))throw new IllegalStateException("QA directory only");
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())){
                mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;
            }
            var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals(WORLD))throw new IllegalStateException("QA save only");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.hunterContact")).toAbsolutePath().normalize();
                if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: required");
                Files.createDirectories(output);mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(45);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.setScreen(null);initialized=true;
                Files.writeString(output.resolve("status.txt"),"Capturing Hunter clips, actual rendered wrist transforms and accepted damage");
            }
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;setup();return;}
            if(ready&&age>MOVES[index].ticks+8){ready=false;
                if(++index>=MOVES.length){
                    done=true;var result=new JsonObject();result.addProperty("scenes",MOVES.length);result.add("frames",frames);result.add("presentations",presentations);result.add("hits",new Gson().toJsonTree(hits));result.add("groundHits",new Gson().toJsonTree(groundHits));
                    Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
                    Files.writeString(output.resolve("status.txt"),"Captured; inspect per-frame wrist agreement, telegraph/contact/recovery and miss scene");mc.stop();
                }else queued=false;
            }
        }catch(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Hunter frame audit",ex);try{if(output!=null)Files.writeString(output.resolve("status.txt"),error);}catch(Exception ignored){}mc.stop();}
    }
    private static void setup(){
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();final int scene=index;
        server.execute(()->{try{
            var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
            for(var entity:level.getAllEntities())if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
            level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
            for(int x=-10;x<=12;x++)for(int z=-10;z<=18;z++){
                level.setBlock(new BlockPos(x,80,z),(x%2==0||z%2==0?Blocks.LIGHT_GRAY_CONCRETE:Blocks.WHITE_CONCRETE).defaultBlockState(),2);
                for(int y=81;y<92;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            p.setGameMode(GameType.SPECTATOR);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
            boolean leap=MOVES[scene]==CreatureAttack.LEAP;
            Vec3 camera=leap?new Vec3(6,83,8):new Vec3(4.4,81.6,4.8),look=new Vec3(0,82.2,leap?2.5:.6).subtract(camera.add(0,p.getEyeHeight(),0));
            p.connection.teleport(camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
            var actor=ModEntities.BLACKLIGHT_HUNTER.get().create(level);actor.setPos(0,81,0);actor.setPersistenceRequired();actor.setYRot(0);actor.yBodyRot=0;
            actor.goalSelector.removeAllGoals(g->true);actor.targetSelector.removeAllGoals(g->true);level.addFreshEntity(actor);
            var victim=EntityType.COW.create(level);victim.setPos(0,81,scene==5?3.3:leap?5:1.5);victim.setNoAi(true);victim.setPersistenceRequired();level.addFreshEntity(victim);
            victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);victim.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);victim.setHealth(1000);
            actorId=actor.getId();targetId=victim.getId();started=level.getGameTime()+30;age=-30;groundRecorded=false;ready=true;
        }catch(Exception ex){error=ex.toString();}});
    }
    @SubscribeEvent public static void server(TickEvent.LevelTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!initialized||!ready||done||e.level.isClientSide)return;
        var server=Minecraft.getInstance().getSingleplayerServer();if(server==null||e.level!=server.overworld())return;
        age=(int)(e.level.getGameTime()-started);
        if(age==0){var actor=(BlacklightHunter)e.level.getEntity(actorId);var victim=(LivingEntity)e.level.getEntity(targetId);
            if(!actor.beginAttack(MOVES[index],victim))error="Cannot begin "+MOVES[index];}
        if(!groundRecorded&&e.level.getEntity(actorId) instanceof BlacklightHunter actor&&actor.groundImpactPoint()!=null){
            groundRecorded=true;var row=new JsonObject();row.addProperty("scene",index);row.addProperty("age",actor.attackAge(0));
            row.add("contact",new Gson().toJsonTree(actor.groundImpactPoint()));groundHits.add(row);
        }
    }
    @SubscribeEvent public static void hit(ForgeDamageEvent.Post e){
        if(!ready||done||e.getNewDamage()<=0||!(e.getSource().getEntity() instanceof BlacklightHunter actor)||actor.getId()!=actorId)return;
        var row=new JsonObject();row.addProperty("scene",index);row.addProperty("move",MOVES[index].name());row.addProperty("age",actor.attackAge(0));row.addProperty("damage",e.getNewDamage());
        row.add("contact",new Gson().toJsonTree(actor.damageContact()));hits.add(row);
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!initialized||!ready||done)return;
        var mc=Minecraft.getInstance();if(mc.level==null||!(mc.level.getEntity(actorId) instanceof BlacklightHunter actor))return;
        if(!(mc.getEntityRenderDispatcher().getRenderer(actor) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> renderer))return;
        var model=renderer.getGeoModel();for(String hand:new String[]{"right_forearm","left_forearm"}){var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(hand);if(bone!=null)bone.setTrackingMatrices(true);}
        if(!actor.committedAttack())return;int t=(int)actor.attackAge(0);
        float renderAge=actor.attackAge(mc.getFrameTime());
        int sub=renderAge>=13&&renderAge<18?(int)(mc.getFrameTime()*3):0;
        String name=index+"-"+MOVES[index].name().toLowerCase(Locale.ROOT)+"-"+String.format(Locale.ROOT,"%03d",t)+(sub==0?"":"-sub"+sub)+".png";if(!saved.add(name))return;
        try{
            try(var screenshot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){screenshot.writeToFile(output.resolve(name));}
            float partial=mc.getFrameTime(),clock=actor.attackAge(partial);var row=new JsonObject();row.addProperty("image",name);row.addProperty("scene",index);row.addProperty("age",clock);
            row.addProperty("serverAge",age);row.addProperty("clientWorldClock",ContactEffectsQueue.renderTime(mc.level.getGameTime(),partial));
            row.addProperty("debris",com.codex.blacklight.client.renderer.ContactTerrainDebris.activeCount());
            for(var r:com.codex.blacklight.client.renderer.ContactEffectsPresentation.receipts())if(receipts.add(r.serial())){
                var receipt=new Gson().toJsonTree(r).getAsJsonObject();receipt.addProperty("scene",index);receipt.addProperty("firstImage",name);receipt.addProperty("firstObservedPoseAge",clock);receipt.addProperty("contactAge",r.tick()-started);presentations.add(receipt);
            }
            for(boolean left:new boolean[]{false,true}){
                var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(left?"left_forearm":"right_forearm");if(bone==null)continue;
                // Gecko 4.8.4 translateMatrix adds an identity matrix twice while
                // recording world space. Undo those two diagonal additions only in
                // this diagnostic copy; translation and the actual render basis stay intact.
                var matrix=new org.joml.Matrix4f(bone.getWorldSpaceMatrix());
                matrix.m00(matrix.m00()-2).m11(matrix.m11()-2).m22(matrix.m22()-2);
                var actual=matrix.transformPosition(new org.joml.Vector3f(((left?-1:1)*12.5F-bone.getPivotX())/16,(2-bone.getPivotY())/16,(-15.5F-bone.getPivotZ())/16));
                var hand=HunterContactPose.hand(MOVES[index],clock,left,actor.position(),actor.yBodyRot);
                row.add(left?"actualLeft":"actualRight",new Gson().toJsonTree(new Vec3(actual.x,actual.y,actual.z)));
                row.addProperty(left?"leftError":"rightError",hand.palm().distanceTo(new Vec3(actual.x,actual.y,actual.z)));
                row.add(left?"leftPalm":"rightPalm",new Gson().toJsonTree(hand.palm()));
            }
            frames.add(row);
        }catch(Exception ex){error=ex.toString();}
    }
}
