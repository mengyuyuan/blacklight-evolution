package com.codex.blacklight.client;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.EncounterEntities;
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
public final class JuggernautContactVisual {
    private static final String DIRECTORY="D:/CodexCache/creature-combat-qa/client",WORLD="Creature Skills QA Final";
    private static final boolean TERRAIN=Boolean.getBoolean("blacklight.test.terrainFeedback");
    private static final EncounterMove[] MOVES=TERRAIN?new EncounterMove[]{EncounterMove.HAMMER,EncounterMove.SLAM,EncounterMove.GUARD,EncounterMove.GUARD,EncounterMove.HAMMER,EncounterMove.HAMMER,EncounterMove.SLAM,EncounterMove.HAMMER}:new EncounterMove[]{EncounterMove.HAMMER,EncounterMove.SLAM,EncounterMove.GUARD,EncounterMove.GUARD,EncounterMove.HAMMER,EncounterMove.HAMMER};
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
        if(e.phase!=TickEvent.Phase.END||System.getProperty("blacklight.test.juggernautContact","").isBlank()||done)return;
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
                output=Path.of(System.getProperty("blacklight.test.juggernautContact")).toAbsolutePath().normalize();
                if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: required");
                Files.createDirectories(output);mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(45);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.setScreen(null);initialized=true;
                Files.writeString(output.resolve("status.txt"),"Capturing Juggernaut clips, actual rendered wrist transforms and accepted damage");
            }
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;setup();return;}
            if(ready&&age>SourceBossAction.get(EncounterKind.JUGGERNAUT,MOVES[index]).duration()+(index==3?75:0)+8){ready=false;
                if(++index>=MOVES.length){
                    done=true;var result=new JsonObject();result.addProperty("scenes",MOVES.length);result.add("frames",frames);result.add("presentations",presentations);result.add("hits",new Gson().toJsonTree(hits));result.add("groundHits",new Gson().toJsonTree(groundHits));
                    Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
                    Files.writeString(output.resolve("status.txt"),"Captured; inspect per-frame wrist agreement, telegraph/contact/recovery and miss scene");mc.stop();
                }else queued=false;
            }
        }catch(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Juggernaut frame audit",ex);try{if(output!=null)Files.writeString(output.resolve("status.txt"),error);}catch(Exception ignored){}mc.stop();}
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
            if(TERRAIN&&scene==6){
                for(int x=-10;x<=12;x++)for(int z=-10;z<=18;z++)level.setBlock(new BlockPos(x,80,z),(x<0?Blocks.DIRT:x<3?Blocks.STONE:Blocks.STONE_SLAB).defaultBlockState(),2);
                for(int z=-10;z<=18;z++)for(int y=81;y<=84;y++)level.setBlock(new BlockPos(-4,y,z),Blocks.STONE_BRICKS.defaultBlockState(),2);
            }
            p.setGameMode(GameType.SPECTATOR);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
            Vec3 camera=new Vec3(6.5,83,7),look=new Vec3(0,83,1).subtract(camera.add(0,p.getEyeHeight(),0));
            p.connection.teleport(camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
            var actor=EncounterEntities.CREATURES.get(EncounterKind.JUGGERNAUT).get().create(level);actor.setPos(0,81,0);actor.setPersistenceRequired();actor.setYRot(0);actor.yBodyRot=0;
            actor.goalSelector.removeAllGoals(g->true);actor.targetSelector.removeAllGoals(g->true);level.addFreshEntity(actor);
            var victim=EntityType.COW.create(level);victim.setPos(0,81,3);victim.setNoAi(true);victim.setPersistenceRequired();level.addFreshEntity(victim);
            victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);victim.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);victim.setHealth(1000);
            if(TERRAIN&&scene==7)victim.setInvulnerable(true);
            actorId=actor.getId();targetId=victim.getId();started=level.getGameTime()+30;age=-30;groundRecorded=false;ready=true;
        }catch(Exception ex){error=ex.toString();}});
    }
    @SubscribeEvent public static void server(TickEvent.LevelTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!initialized||!ready||done||e.level.isClientSide)return;
        var server=Minecraft.getInstance().getSingleplayerServer();if(server==null||e.level!=server.overworld())return;
        age=(int)(e.level.getGameTime()-started);
        if(age==0){var actor=(EncounterMob)e.level.getEntity(actorId);var victim=(LivingEntity)e.level.getEntity(targetId);
            if(!actor.begin(MOVES[index],victim))error="Cannot begin "+MOVES[index];}
        var actor=(EncounterMob)e.level.getEntity(actorId);var victim=(LivingEntity)e.level.getEntity(targetId);
        if(actor==null||victim==null)return;
        if(index==4&&age==12)victim.setPos(5,81,3);
        if(index==5&&age==9)actor.hurt(actor.damageSources().mobAttack(victim),1);
        if((index==2||index==3)&&age==16)actor.hurt(actor.damageSources().mobAttack(victim),index==3?40:10);
        if(index==3&&age==28)actor.hurt(actor.damageSources().mobAttack(victim),40);
        if(!groundRecorded&&actor.groundImpacts()>0){groundRecorded=true;var row=new JsonObject();row.addProperty("scene",index);row.addProperty("age",actor.lastGroundAge());groundHits.add(row);}
    }

    @SubscribeEvent public static void hit(ForgeDamageEvent.Post e){
        if(!ready||done||e.getNewDamage()<=0||!(e.getSource().getEntity() instanceof EncounterMob actor)||actor.getId()!=actorId)return;
        var row=new JsonObject();row.addProperty("scene",index);row.addProperty("move",MOVES[index].name());row.addProperty("age",actor.moveAge(0));row.addProperty("damage",e.getNewDamage());
        row.add("contact",new Gson().toJsonTree(actor.damageContact()));hits.add(row);
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!initialized||!ready||done)return;
        var mc=Minecraft.getInstance();if(mc.level==null||!(mc.level.getEntity(actorId) instanceof EncounterMob actor))return;
        if(!(mc.getEntityRenderDispatcher().getRenderer(actor) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> renderer))return;
        var model=renderer.getGeoModel();for(String hand:new String[]{"right_hand","left_hand"}){var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(hand);if(bone!=null)bone.setTrackingMatrices(true);}
        if(!actor.sourcePoseCommitted())return;int t=age;
        float renderAge=actor.moveAge(mc.getFrameTime());
        int sub=TERRAIN&&(renderAge>=15&&renderAge<19||renderAge>=40&&renderAge<45)?(int)(mc.getFrameTime()*3):0;
        String name=index+"-"+MOVES[index].name().toLowerCase(Locale.ROOT)+"-"+String.format(Locale.ROOT,"%03d",t)+(sub==0?"":"-sub"+sub)+".png";if(!saved.add(name))return;
        try{
            try(var screenshot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){screenshot.writeToFile(output.resolve(name));}
            float partial=mc.getFrameTime(),clock=actor.moveAge(partial);var row=new JsonObject();row.addProperty("image",name);row.addProperty("scene",index);row.addProperty("age",clock);
            row.addProperty("serverAge",t);row.addProperty("clientWorldClock",ContactEffectsQueue.renderTime(mc.level.getGameTime(),partial));
            if(TERRAIN){auditEffects(row,mc);
                for(var r:com.codex.blacklight.client.renderer.ContactEffectsPresentation.receipts())if(receipts.add(r.serial())){
                    var receipt=new Gson().toJsonTree(r).getAsJsonObject();receipt.addProperty("scene",index);receipt.addProperty("firstImage",name);receipt.addProperty("firstObservedPoseAge",clock);receipt.addProperty("contactAge",r.tick()-started);presentations.add(receipt);
                }
            }
            if(JuggernautContact.uses(actor.move()))for(boolean left:new boolean[]{false,true}){
                var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(left?"left_hand":"right_hand");if(bone==null)continue;
                // Gecko 4.8.4 translateMatrix adds an identity matrix twice while
                // recording world space. Undo those two diagonal additions only in
                // this diagnostic copy; translation and the actual render basis stay intact.
                var matrix=new org.joml.Matrix4f(bone.getWorldSpaceMatrix());
                matrix.m00(matrix.m00()-2).m11(matrix.m11()-2).m22(matrix.m22()-2);
                var actual=matrix.transformPosition(new org.joml.Vector3f(((left?-1:1)*24.84F-bone.getPivotX())/16,(11.34F-bone.getPivotY())/16,(-5.22F-bone.getPivotZ())/16));
                var hand=JuggernautContact.fist(MOVES[index],clock,left,actor.position(),actor.yBodyRot);
                row.add(left?"actualLeft":"actualRight",new Gson().toJsonTree(new Vec3(actual.x,actual.y,actual.z)));
                row.addProperty(left?"leftError":"rightError",hand.center().distanceTo(new Vec3(actual.x,actual.y,actual.z)));
                row.add(left?"leftPalm":"rightPalm",new Gson().toJsonTree(hand.center()));
            }
            frames.add(row);
        }catch(Exception ex){error=ex.toString();}
    }
    private static Object field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static Object component(Object record,String name)throws Exception{var m=record.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(record);}
    private static void auditEffects(JsonObject row,Minecraft mc)throws Exception{
        var bursts=(Collection<?>)field(com.codex.blacklight.client.renderer.CombatImpactRenderer.class,"BURSTS");int ground=0,body=0,cells=0,behind=0;
        for(var burst:bursts){var packet=(com.codex.blacklight.network.CombatImpactPayload)component(burst,"event");if(packet.ground())ground++;else body++;}
        for(var wave:(Collection<?>)field(com.codex.blacklight.client.renderer.TerrainRipple.class,"WAVES"))for(var cell:(Collection<?>)component(wave,"cells")){
            cells++;if(((BlockPos)component(cell,"pos")).getX()<-4)behind++;
        }
        int tissue=((Collection<?>)field(com.codex.blacklight.client.renderer.InfectedImpactVisuals.class,"ACTIVE")).size();
        int debris=com.codex.blacklight.client.renderer.ContactTerrainDebris.activeCount();for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.Display.BlockDisplay)debris++;
        row.addProperty("groundBursts",ground);row.addProperty("bodyBursts",body);row.addProperty("terrainCells",cells);row.addProperty("behindWallCells",behind);row.addProperty("infectedImpacts",tissue);row.addProperty("debris",debris);
    }
}
