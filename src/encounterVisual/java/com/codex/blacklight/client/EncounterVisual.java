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

@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class EncounterVisual {
    private record Scene(EncounterKind kind,EncounterMove move,int phase){boolean radio(){return kind==null&&move==EncounterMove.GUARD;}boolean weak(){return kind!=null&&move==EncounterMove.NONE;}String key(){return kind==null?radio()?"radio":"uav":kind.id+"_"+move.name().toLowerCase(Locale.ROOT)+"_p"+phase;}}
    private static boolean selected(String scene,String filter){
        return filter.equals("monster_style")?List.of("leader_hunter_","juggernaut_","goliath_","flyer_").stream().anyMatch(scene::startsWith):scene.startsWith(filter+"_");
    }
    private static final List<Scene> SCENES=new ArrayList<>();
    static {for(var kind:EncounterKind.values()){if(!kind.enabled())continue;var moves=new HashSet<EncounterMove>();for(int phase=0;phase<(kind==EncounterKind.ALEX_MERCER?3:1);phase++)for(int seq=0;seq<5;seq++)for(double range:new double[]{2,6,10}){
        var move=kind.choose(range,phase,seq);if(moves.add(move))SCENES.add(new Scene(kind,move,phase));
    }}SCENES.add(new Scene(null,EncounterMove.NONE,0));SCENES.add(new Scene(null,EncounterMove.GUARD,0));SCENES.add(new Scene(EncounterKind.GOLIATH,EncounterMove.NONE,0));SCENES.add(new Scene(EncounterKind.ELIZABETH_GREENE,EncounterMove.NONE,0));String filter=System.getProperty("blacklight.test.encounterFilter","");if(!filter.isEmpty())SCENES.removeIf(scene->!selected(scene.key(),filter));}
    private static final String DIRECTORY="D:/CodexCache/creature-combat-qa/client",WORLD="Creature Skills QA Final";
    private static int index,actorId,targetId;private static long start;private static volatile int age;private static volatile boolean ready;private static boolean initialized,queued,done;private static volatile String error;
    private static Path output;private static final Set<String> saved=new HashSet<>();private static final JsonArray frames=new JsonArray();
    private static int duration(){var s=SCENES.get(index);return s.kind==null?110:s.weak()?80:s.move.duration+8;}
    @SubscribeEvent public static void client(TickEvent.ClientTickEvent e){
        if(Boolean.getBoolean("blacklight.test.modelGallery"))return;
        if(e.phase!=TickEvent.Phase.END||System.getProperty("blacklight.test.encounters","").isEmpty()||done)return;var mc=Minecraft.getInstance();
        try{
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().replace('\\','/').equalsIgnoreCase(DIRECTORY))throw new IllegalStateException("Wrong QA directory");
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())&&Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD))){mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;}
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){output=Path.of(System.getProperty("blacklight.test.encounters"));if(!output.toAbsolutePath().toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: only");Files.createDirectories(output);mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(48);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(null);initialized=true;}
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;setup();return;}
            if(ready&&age>duration()+8){ready=false;if(++index>=SCENES.size()){done=true;finish(mc);}else queued=false;}
        }catch(Exception ex){done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Encounter visual QA",ex);try{if(output!=null)Files.writeString(output.resolve("status.txt"),ex.toString());}catch(Exception ignored){}mc.stop();}
    }
    private static void finish(Minecraft mc)throws Exception{String filter=System.getProperty("blacklight.test.encounterFilter","");var combined=new JsonArray();if(!filter.isEmpty()){var old=JsonParser.parseString(Files.readString(output.resolve("evidence.json"))).getAsJsonObject();for(var row:old.getAsJsonArray("frames"))if(!selected(row.getAsJsonObject().get("scene").getAsString(),filter))combined.add(row);}frames.forEach(combined::add);var scenes=new HashSet<String>();for(var row:combined)scenes.add(row.getAsJsonObject().get("scene").getAsString());var result=new JsonObject();result.addProperty("scenes",scenes.size());result.addProperty("lastRerun",filter);result.add("frames",combined);Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));Files.writeString(output.resolve("status.txt"),"Captured real encounter models and contact/recovery frames; review required");mc.stop();}
    private static void setup(){var mc=Minecraft.getInstance();final Scene scene=SCENES.get(index);var server=mc.getSingleplayerServer();server.execute(()->{try{
        var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());var previous=new ArrayList<Entity>();for(var entity:level.getAllEntities())if(entity!=null&&!(entity instanceof net.minecraft.world.entity.player.Player))previous.add(entity);previous.forEach(Entity::discard);
        level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);for(int x=-18;x<19;x++)for(int z=-18;z<24;z++){level.setBlock(new BlockPos(x,80,z),(x%4==0||z%4==0?Blocks.LIGHT_GRAY_CONCRETE:Blocks.WHITE_CONCRETE).defaultBlockState(),2);for(int y=81;y<101;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        p.setGameMode(GameType.SPECTATOR);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
        float height=scene.radio()?2:scene.kind==null?3:scene.kind.height;double range=scene.radio()?12:scene.kind==null?4:scene.weak()?12:scene.move.projectile()?10:scene.move==EncounterMove.POUNCE?6:scene.move==EncounterMove.CHARGE?9:Math.max(2,scene.kind.width*.5+1);
        double far=scene.radio()?5:scene.kind==null?15:height>4?17:scene.move.projectile()||scene.move.drives(15)?11:7;
        Vec3 camera=new Vec3(far,82+height*.5,far*.8),look=new Vec3(0,scene.kind==null&&!scene.radio()?87:81+height*.5,scene.radio()?0:Math.min(range,4)).subtract(camera.add(0,p.getEyeHeight(),0));p.connection.teleport(camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
        Mob actor=scene.radio()?ModEntities.BLACKWATCH_OFFICER.get().create(level):scene.kind==null?EncounterEntities.UAV.get().create(level):EncounterEntities.CREATURES.get(scene.kind).get().create(level);actor.setPos(0,scene.kind==null&&!scene.radio()?84:81,0);actor.setPersistenceRequired();actor.setYRot(0);actor.yBodyRot=0;actor.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);actor.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(.25);if(scene.weak()||actor instanceof MilitaryUav)actor.setNoAi(true);level.addFreshEntity(actor);
        if(scene.phase>0)actor.setHealth(actor.getMaxHealth()*(scene.phase==1?.6F:.25F));
        Mob victim=scene.kind==null?ModEntities.BLACKLIGHT_STALKER.get().create(level):EntityType.COW.create(level);victim.setNoAi(true);victim.setPersistenceRequired();victim.setPos(0,81,range);level.addFreshEntity(victim);victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);victim.setHealth(2000);victim.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        
        int warmup=scene.phase>0?45:25;actorId=actor.getId();targetId=victim.getId();start=level.getGameTime()+warmup;age=-warmup;ready=true;
    }catch(Exception ex){error=ex.toString();}});}
    @SubscribeEvent public static void server(TickEvent.LevelTickEvent e){if(e.phase!=TickEvent.Phase.END||!initialized||!ready||done||e.level.isClientSide)return;var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(server==null||e.level!=server.overworld())return;age=(int)(e.level.getGameTime()-start);var scene=SCENES.get(index);var entity=e.level.getEntity(actorId);var victim=(LivingEntity)e.level.getEntity(targetId);
        if(age==0&&scene.radio()&&entity instanceof MilitarySoldier soldier)soldier.setTarget(victim);
        if(age==0&&entity instanceof MilitaryUav drone)drone.setNoAi(false);
        if(scene.weak()&&entity instanceof EncounterMob actor&&age>=3&&(age-3)%14==0&&(age-3)/14<actor.getParts().length){int slot=(age-3)/14;if(!actor.getParts()[slot].hurt(actor.damageSources().mobAttack(victim),100))error="Weakpoint strike rejected "+slot;}
        if(age==0&&!scene.weak()&&entity instanceof EncounterMob actor){actor.setTarget(null);if(actor.kind().military)actor.setLastHurtByMob(victim);if(!actor.begin(scene.move,victim))error="Failed visual move "+scene.key()+" current="+actor.move();}}
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){if(e.phase!=TickEvent.Phase.END||!initialized||!ready||done||age<0)return;var mc=Minecraft.getInstance();if(mc.level==null)return;var scene=SCENES.get(index);var entity=mc.level.getEntity(actorId);if(entity==null)return;int t=!scene.weak()&&entity instanceof EncounterMob mob?(int)mob.moveAge(0):age;int contact=scene.move.contact;
        boolean repeated=scene.move==EncounterMove.DOUBLE&&t>=23&&t<=27||scene.move==EncounterMove.VOLLEY&&(t>=28&&t<=32||t>=38&&t<=42);
        if(scene.weak()?!Set.of(0,4,16,18,30,32,45,70).contains(t):scene.radio()?!Set.of(0,20,38,60,76,77,90,105).contains(t):scene.kind==null?!Set.of(0,15,30,45,60,90).contains(t):!(repeated||t==0||t==Math.max(1,contact-5)||t>=contact-2&&t<=contact+2||t==scene.move.duration-5||t==scene.move.duration-1))return;
        String name=scene.key()+"-"+String.format(Locale.ROOT,"%03d",t)+".png";if(!saved.add(name))return;
        try{try(var picture=Screenshot.takeScreenshot(mc.getMainRenderTarget())){picture.writeToFile(output.resolve(name));}var row=new JsonObject();row.addProperty("image",name);row.addProperty("scene",scene.key());row.addProperty("age",t);frames.add(row);}catch(Exception ex){error=ex.toString();}
    }
}
