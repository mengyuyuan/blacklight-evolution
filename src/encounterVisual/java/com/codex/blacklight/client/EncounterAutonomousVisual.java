package com.codex.blacklight.client;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.registry.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

/** Disposable client QA. Never calls begin(), sets a target, or animates an actor itself. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class EncounterAutonomousVisual {
    private record Scene(EncounterKind kind,int phase){String key(){return kind.id+"_p"+phase;}}
    private static final List<Scene> SCENES=new ArrayList<>();
    private static final boolean TRAVEL=System.getProperty("blacklight.test.autonomousFilter","").equals("travel");
    private static final boolean SOURCE=System.getProperty("blacklight.test.autonomousFilter","").equals("source");
    static {for(var kind:EncounterKind.values())if(kind.enabled())SCENES.add(new Scene(kind,0));if(SOURCE)SCENES.removeIf(scene->!Set.of(EncounterKind.ELIZABETH_GREENE,EncounterKind.LEADER_HUNTER).contains(scene.kind));if(TRAVEL)SCENES.removeIf(scene->!Set.of(EncounterKind.LEADER_HUNTER,EncounterKind.BRAWLER,EncounterKind.FLYER,EncounterKind.SUPER_SOLDIER).contains(scene.kind));}
    private static final Path OUT=Path.of("D:/CodexCache/encounter-qa/autonomous");
    private static final String DIRECTORY="D:/CodexCache/creature-combat-qa/client";
    private static int index,actorId,targetId,tabFrames;private static long start;
    private static boolean initialized,queued,done,tabQueued;private static volatile boolean ready;
    private static volatile String error;private static final JsonArray frames=new JsonArray();
    private static final Set<String> saved=new HashSet<>();
    @SubscribeEvent public static void client(TickEvent.ClientTickEvent e){
        if(!Boolean.getBoolean("blacklight.test.autonomous")||e.phase!=TickEvent.Phase.END||done)return;
        var mc=Minecraft.getInstance();
        try{
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().replace('\\','/').equalsIgnoreCase(DIRECTORY))throw new IllegalStateException("Wrong QA directory");
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())&&Files.exists(mc.gameDirectory.toPath().resolve("saves/Creature Skills QA Final"))){
                mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;
            }
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){Files.createDirectories(OUT);mc.options.pauseOnLostFocus=false;mc.options.hideGui=!SOURCE;mc.options.fov().set(48);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(null);initialized=true;}
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(index>=SCENES.size()){
                if(!tabQueued){tabQueued=true;mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).setGameMode(GameType.CREATIVE));return;}
                if(mc.gameMode.hasInfiniteItems()&&mc.screen==null){
                    mc.options.hideGui=false;
                    var screen=new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player,mc.level.enabledFeatures(),mc.options.operatorItemsTab().get());mc.setScreen(screen);
                    var tab=net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(new net.minecraft.resources.ResourceLocation("blacklight","encounters"));
                    var method=screen.getClass().getDeclaredMethod("selectTab",net.minecraft.world.item.CreativeModeTab.class);method.setAccessible(true);method.invoke(screen,tab);
                }
                return;
            }
            if(!queued){queued=true;setup();return;}
            if(ready&&SOURCE&&mc.level.getGameTime()-start==100&&saved.add("hud-damage-"+index)){
                mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().overworld();if(level.getEntity(actorId) instanceof EncounterMob boss)boss.hurt(boss.damageSources().generic(),boss.getMaxHealth()*.15F/(boss.kind()==EncounterKind.ELIZABETH_GREENE?.08F:1));});
            }
            if(ready&&SOURCE&&SCENES.get(index).kind==EncounterKind.ELIZABETH_GREENE){
                int elapsed=(int)(mc.level.getGameTime()-start);
                for(int slot=0;slot<3;slot++)if(elapsed==370+slot*12&&saved.add("weakpoint-strike-"+slot)){
                    int part=slot;mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().overworld();if(level.getEntity(actorId) instanceof EncounterMob boss&&level.getEntity(targetId) instanceof LivingEntity attacker)boss.getParts()[part].hurt(boss.damageSources().mobAttack(attacker),100);});
                }
            }
            if(ready&&mc.level.getGameTime()-start>(SOURCE?(SCENES.get(index).kind==EncounterKind.ELIZABETH_GREENE?710:360):180)){ready=false;queued=false;index++;}
        }catch(Exception ex){fail(ex);}
    }
    private static void setup(){var mc=Minecraft.getInstance();var scene=SCENES.get(index);mc.getSingleplayerServer().execute(()->{try{
        var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        var old=new ArrayList<Entity>();for(var entity:level.getAllEntities())if(!(entity instanceof net.minecraft.world.entity.player.Player))old.add(entity);old.forEach(Entity::discard);
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
        for(int x=-18;x<19;x++)for(int z=-18;z<24;z++){level.setBlock(new BlockPos(x,80,z),(x%4==0||z%4==0?Blocks.LIGHT_GRAY_CONCRETE:Blocks.WHITE_CONCRETE).defaultBlockState(),2);for(int y=81;y<101;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        player.setGameMode(GameType.SPECTATOR);player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
        double range=SOURCE&&scene.kind==EncounterKind.LEADER_HUNTER?2:scene.kind==EncounterKind.JUGGERNAUT||scene.kind==EncounterKind.GOLIATH?9:scene.kind==EncounterKind.FLYER||scene.kind==EncounterKind.ELIZABETH_GREENE?10:5;
        double far=scene.kind.height>4?(SOURCE?13:18):11;
        Vec3 eye=new Vec3(far,83+scene.kind.height*.6,range*.5+far*.65),look=new Vec3(0,81+scene.kind.height*.5,range*.5).subtract(eye);
        player.connection.teleport(eye.x,eye.y-player.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
        var actor=EncounterEntities.CREATURES.get(scene.kind).get().create(level);actor.setPos(0,81,0);actor.setPersistenceRequired();actor.setYRot(0);actor.yBodyRot=0;level.addFreshEntity(actor);
        if(scene.phase>0)actor.setHealth(actor.getMaxHealth()*(scene.phase==1?.6F:.25F));
        var target=EntityType.COW.create(level);target.setPos(0,81,range);target.setNoAi(true);target.setPersistenceRequired();target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);target.setHealth(2000);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);level.addFreshEntity(target);
        // Ordinary animals aren't military enemies. Simulate an aggressor without choosing an AI move.
        if(scene.kind.military)actor.setLastHurtByMob(target);
        actorId=actor.getId();targetId=target.getId();start=level.getGameTime();ready=true;
    }catch(Exception ex){error=ex.toString();}});}
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!initialized||done)return;var mc=Minecraft.getInstance();
        try{
            if(index>=SCENES.size()){
                if(mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen&&++tabFrames>=12){
                    save("all-blacklight-eggs.png",mc);
                    var combined=new JsonArray();if(TRAVEL||SOURCE){var old=JsonParser.parseString(Files.readString(OUT.resolve("evidence.json"))).getAsJsonObject();for(var row:old.getAsJsonArray("frames"))if(SCENES.stream().noneMatch(scene->scene.key().equals(row.getAsJsonObject().get("scene").getAsString())))combined.add(row);}frames.forEach(combined::add);
                    var result=new JsonObject();result.addProperty("scenes",15);result.addProperty("lastRerun",SOURCE?"source":TRAVEL?"travel":"all");result.addProperty("eggCount",EncounterEntities.allSpawnEggs().size());result.add("frames",combined);
                    Files.writeString(OUT.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));Files.writeString(OUT.resolve("status.txt"),"Autonomous AI scenes captured; no begin()/target/pose injection. Review required.");done=true;mc.stop();
                }return;
            }
            if(!ready||mc.level==null||!(mc.level.getEntity(actorId) instanceof EncounterMob actor))return;
            int elapsed=(int)(mc.level.getGameTime()-start),tick=(int)actor.moveAge(0);var action=actor.move();
            boolean contact=action!=EncounterMove.NONE&&(tick==actor.actionContact(action)-5||tick>=actor.actionContact(action)-2&&tick<=actor.actionContact(action)+2||tick==actor.actionDuration(action)-5);
            if(SOURCE&&SourceBossAction.get(actor.kind(),action)!=null&&tick%3==0)contact=true;
            if(SOURCE&&actor.exposed()){tick=(int)actor.exposedAge(0);if(tick%5==0)contact=true;}
            if(!contact&&elapsed!=15&&elapsed!=45&&elapsed!=90&&elapsed!=150)return;
            String state=actor.exposed()?"EXPOSED":action.name();
            String name=SCENES.get(index).key()+"-"+state.toLowerCase(Locale.ROOT)+"-"+String.format(Locale.ROOT,"%03d",elapsed)+".png";
            if(!saved.add(name))return;save(name,mc);
            var row=new JsonObject();row.addProperty("image",name);row.addProperty("scene",SCENES.get(index).key());row.addProperty("move",state);row.addProperty("moveTick",tick);row.addProperty("elapsed",elapsed);row.addProperty("x",actor.getX());row.addProperty("y",actor.getY());row.addProperty("z",actor.getZ());
            row.addProperty("actorHealth",actor.getHealth());row.addProperty("actorMaxHealth",actor.getMaxHealth());
            if(mc.level.getEntity(targetId) instanceof LivingEntity victim)row.addProperty("victimHealth",victim.getHealth());
            if(SOURCE&&mc.getEntityRenderDispatcher().getRenderer(actor) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> geo){
                var bones=new JsonObject();for(String boneName:new String[]{"root","torso","head","right_hand"}){var b=geo.getGeoModel().getAnimationProcessor().getBone(boneName);if(b!=null)bones.add(boneName,new Gson().toJsonTree(new float[]{b.getPivotX(),b.getPivotY(),b.getPivotZ(),b.getRotX(),b.getRotY(),b.getRotZ(),b.getPosX(),b.getPosY(),b.getPosZ()}));}row.add("renderedBones",bones);
            }
            frames.add(row);
        }catch(Exception ex){fail(ex);}
    }
    private static void save(String name,Minecraft mc)throws Exception{try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve(name));}}
    private static void fail(Exception ex){done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Autonomous visual QA",ex);try{Files.writeString(OUT.resolve("status.txt"),ex.toString());}catch(Exception ignored){}Minecraft.getInstance().stop();}
}
