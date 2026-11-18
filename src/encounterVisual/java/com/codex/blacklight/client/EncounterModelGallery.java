package com.codex.blacklight.client;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

/** Isolated muted client gallery; excluded from every distributed JAR. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class EncounterModelGallery {
    private static final String DIRECTORY="D:/CodexCache/creature-combat-qa/client",WORLD="Creature Skills QA Final";
    private static final Path OUT=Path.of("D:/CodexCache/encounter-qa/model-gallery");
    private static final List<String> IDS=new ArrayList<>();
    private static final String FILTER=System.getProperty("blacklight.test.galleryFilter","");
    static{for(var k:EncounterKind.values())if(k.enabled())IDS.add(k.id);IDS.add("recon_uav");IDS.add("supreme_hunter_reference");if(FILTER.equals("monster_style"))IDS.removeIf(id->!Set.of("leader_hunter","juggernaut","goliath","flyer").contains(id));else if(!FILTER.isEmpty())IDS.removeIf(id->!id.equals(FILTER));if(IDS.isEmpty())throw new IllegalArgumentException("Unknown gallery model "+FILTER);}
    private static final String[] VIEWS=FILTER.equals("monster_style")?new String[]{"front","three-quarter","back","style-comparison"}:FILTER.equals("elizabeth_greene")?new String[]{"front","three-quarter","back","detail","style-comparison"}:new String[]{"front","three-quarter","back"};
    private static boolean initialized,queued,done;private static volatile boolean ready;private static volatile String error;
    private static int index,view;private static volatile long start;private static final JsonArray frames=new JsonArray();
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!Boolean.getBoolean("blacklight.test.modelGallery")||done)return;
        var mc=Minecraft.getInstance();try{
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().replace('\\','/').equalsIgnoreCase(DIRECTORY))throw new IllegalStateException("Gallery requires isolated D: world");
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())&&Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD))){mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;}
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){Files.createDirectories(OUT);Files.writeString(OUT.resolve("status.txt"),"Capturing new model gallery");mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(35);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(null);initialized=true;}
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;setup();}
        }catch(Exception ex){fail(mc,ex);}
    }
    private static void setup(){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{
        var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        var previous=new ArrayList<Entity>();for(var entity:level.getAllEntities())if(entity!=null&&!(entity instanceof net.minecraft.world.entity.player.Player))previous.add(entity);previous.forEach(Entity::discard);
        level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
        for(int x=-18;x<=18;x++)for(int z=-18;z<=18;z++){level.setBlock(new BlockPos(x,80,z),(x%4==0||z%4==0?Blocks.LIGHT_GRAY_CONCRETE:Blocks.WHITE_CONCRETE).defaultBlockState(),2);for(int y=81;y<102;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        String id=IDS.get(index);EncounterKind kind=Arrays.stream(EncounterKind.values()).filter(k->k.id.equals(id)).findFirst().orElse(null);
        Mob actor=kind!=null?EncounterEntities.CREATURES.get(kind).get().create(level):id.equals("recon_uav")?EncounterEntities.UAV.get().create(level):ModEntities.SUPREME_HUNTER.get().create(level);
        actor.setNoAi(true);actor.setNoGravity(true);actor.setPersistenceRequired();actor.setPos(0,id.equals("flyer")?82.3:81,0);actor.setYRot(0);actor.setYHeadRot(0);actor.yBodyRot=0;level.addFreshEntity(actor);
        double height=kind!=null?kind.height:id.equals("recon_uav")?1:5.1,width=kind!=null?kind.width:3;
        double far=Math.max(3.8,Math.max(height*1.75,width*2.3));if(kind!=null&&kind.height>3)far=Math.max(height*2.3,width*3.2);if(id.equals("flyer"))far=6.5;if(id.equals("recon_uav"))far=4.7;
        double angle=Math.toRadians(view==0?0:view==1?42:180),targetY=actor.getY()+height*.48;
        if(view==3&&FILTER.equals("elizabeth_greene")){angle=Math.toRadians(-18);far=11;targetY=actor.getY()+height*.75;}
        double centerX=0;
        if(view==4||(view==3&&FILTER.equals("monster_style"))){
            var reference=ModEntities.SUPREME_HUNTER.get().create(level);reference.setNoAi(true);reference.setNoGravity(true);reference.setPersistenceRequired();reference.setPos(8,81,0);reference.setYRot(0);reference.setYHeadRot(0);reference.yBodyRot=0;level.addFreshEntity(reference);
            angle=0;far=27;centerX=2.7;targetY=84.5;
        }
        var eye=new Vec3(centerX+Math.sin(angle)*far,targetY+height*.12,Math.cos(angle)*far);var look=new Vec3(centerX,targetY,0).subtract(eye);
        player.setGameMode(GameType.SPECTATOR);player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
        player.connection.teleport(eye.x,eye.y-player.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
        start=level.getGameTime();ready=true;
    }catch(Exception ex){error=ex.toString();}});}
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!ready||done)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.level.getGameTime()-start<18)return;
        try{String file=IDS.get(index)+"-"+VIEWS[view]+".png";try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve(file));}var row=new JsonObject();row.addProperty("model",IDS.get(index));row.addProperty("view",VIEWS[view]);row.addProperty("image",file);frames.add(row);ready=false;queued=false;if(++view==VIEWS.length){view=0;index++;}if(index==IDS.size()){done=true;Files.writeString(OUT.resolve(FILTER.isEmpty()?"evidence.json":"evidence-"+FILTER+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(frames));Files.writeString(OUT.resolve("status.txt"),"Captured "+IDS.size()+" models, "+frames.size()+" front/three-quarter/back frames; visual review required");mc.stop();}}
        catch(Exception ex){fail(mc,ex);}
    }
    private static void fail(Minecraft mc,Exception ex){done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Model gallery failed",ex);try{Files.writeString(OUT.resolve("status.txt"),ex.toString());}catch(Exception ignored){}mc.stop();}
}
