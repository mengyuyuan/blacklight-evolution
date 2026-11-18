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
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

/** Only the explicit disposable world; rendering uses the real entity model and synchronized clock. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class CreatureSkillsVisual {
    private static final String DIRECTORY="D:/CodexCache/creature-combat-qa/client",WORLD="Creature Skills QA Final";
    private static final MutantMove[] MOVES=Arrays.stream(MutantMove.values()).filter(m->m!=MutantMove.NONE).toArray(MutantMove[]::new);
    private static final int SCENES=MOVES.length+2;
    private static int duration(){return index<MOVES.length?MOVES[index].ticks:index==MOVES.length?140:60;}
    private static final int FIRST=Integer.getInteger("blacklight.test.creatureFirst",0);
    private static int index=FIRST,actorId=-1,targetId=-1;
    private static volatile int age=-100;
    private static volatile boolean ready;
    private static boolean opened,initialized,queued,done;
    private static volatile String error;
    private static long sceneStarted;
    private static Path output;
    private static final Set<String> saved=new HashSet<>();
    private static final JsonArray frames=new JsonArray();
    private static final List<JsonObject> hits=new java.util.concurrent.CopyOnWriteArrayList<>();
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||System.getProperty("blacklight.test.creatureSkills","").isBlank()||done)return;
        var mc=Minecraft.getInstance();
        try{
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().replace('\\','/').equalsIgnoreCase(DIRECTORY))throw new IllegalStateException("Refused non-QA directory");
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen
                    &&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())
                    &&Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD))){
                // Forge's source-defined first button continues this disposable modded QA world.
                mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;
            }
            if(!opened&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen){
                opened=true;
                if(Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD)))throw new IllegalStateException("Existing QA world requires quickPlayWorld");
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);
                var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_MOBGRIEFING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,net.minecraft.world.Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                    new net.minecraft.world.level.levelgen.WorldOptions(7711L,false,false),r->r.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                    .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals(WORLD))throw new IllegalStateException("Refused non-QA save");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.creatureSkills")).toAbsolutePath().normalize();if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: required");
                Files.createDirectories(output);mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(45);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.setScreen(null);initialized=true;Files.writeString(output.resolve("status.txt"),"Capturing real creature skills in a hidden, muted window");
            }
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;setup();return;}
            if(ready&&age>duration()+6){
                ready=false;
                if(++index>=SCENES){
                    done=true;var result=new JsonObject();result.addProperty("scenes",SCENES-FIRST);result.add("frames",frames);result.add("hits",new Gson().toJsonTree(hits));
                    Files.writeString(output.resolve(FIRST>0?"extra-evidence.json":"evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
                    Files.writeString(output.resolve("status.txt"),"Captured nine mutant moves, three civilian attacks, and hive release; visual review required");mc.stop();
                }else queued=false;
            }
        }catch(Exception failure){fail(failure);}
    }
    private static void fail(Exception failure){error=failure.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Creature action visual audit",failure);try{if(output!=null)Files.writeString(output.resolve("status.txt"),error);}catch(Exception ignored){}Minecraft.getInstance().stop();}
    private static void setup(){
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();final int scene=index;
        server.execute(()->{try{
            var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
            for(var entity:level.getAllEntities())if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
            level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
            for(int x=-14;x<=14;x++)for(int z=-14;z<=20;z++){
                level.setBlock(new BlockPos(x,80,z),(x%4==0||z%4==0?Blocks.LIGHT_GRAY_CONCRETE:Blocks.WHITE_CONCRETE).defaultBlockState(),2);
                for(int y=81;y<91;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            p.setGameMode(GameType.SPECTATOR);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
            var move=scene<MOVES.length?MOVES[scene]:MutantMove.NONE;boolean hive=scene==MOVES.length+1;
            boolean travel=hive||move.projectile()||move==MutantMove.STALKER_POUNCE||move==MutantMove.BRUTE_CHARGE;
            Vec3 camera=travel?new Vec3(8,83,11):new Vec3(5,81.8,5.5),look=new Vec3(0,82.2,travel?3:1).subtract(camera.add(0,p.getEyeHeight(),0));
            p.connection.teleport(camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
            EntityType<? extends Mob> type=scene>=MOVES.length?(hive?ModEntities.BLACKLIGHT_HIVE.get():ModEntities.COMMON_INFECTED.get()):switch(move.species){case "STALKER"->ModEntities.BLACKLIGHT_STALKER.get();case "SPITTER"->ModEntities.BLACKLIGHT_SPITTER.get();default->ModEntities.BLACKLIGHT_BRUTE.get();};
            var actor=type.create(level);actor.setPos(0,81,0);actor.setPersistenceRequired();actor.setNoAi(true);actor.setYRot(0);actor.yBodyRot=0;
            actor.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);actor.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(scene==MOVES.length?28:1);level.addFreshEntity(actor);
            if(hive){
                var sac=(BlacklightHive)actor;var tag=new net.minecraft.nbt.CompoundTag();sac.addAdditionalSaveData(tag);tag.putFloat("BlacklightGrowth",1);tag.putInt("BlacklightWaveCooldown",0);tag.putString("BlacklightBrood","common");sac.readAdditionalSaveData(tag);
                p.setGameMode(GameType.CREATIVE);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,server);
                var outbreak=com.codex.blacklight.infection.OutbreakData.get(server);outbreak.start(level.getGameTime());outbreak.markSeeded(level.dimension().location().toString());
            }
            var victim=EntityType.COW.create(level);double range=scene==MOVES.length?1.3:move.projectile()?9:move==MutantMove.STALKER_POUNCE?5:move==MutantMove.BRUTE_CHARGE?6:2;
            victim.setPos(0,81,range);victim.setNoAi(true);victim.setPersistenceRequired();level.addFreshEntity(victim);
            victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);victim.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);victim.setHealth(1000);
            actorId=actor.getId();targetId=victim.getId();sceneStarted=level.getGameTime()+40;age=-40;ready=true;
        }catch(Exception failure){error=failure.toString();}});
    }
    @SubscribeEvent public static void serverTick(TickEvent.LevelTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!initialized||!ready||done||e.level.isClientSide||index>=SCENES)return;
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(server==null||e.level!=server.overworld())return;
        age=(int)(e.level.getGameTime()-sceneStarted);
        if(age==0){
            var actor=(Mob)e.level.getEntity(actorId);var victim=(LivingEntity)e.level.getEntity(targetId);actor.setNoAi(false);actor.setTarget(victim);
            if(actor instanceof BlacklightMutant mutant&&!mutant.beginAttack(MOVES[index],victim))error="Could not start "+MOVES[index];
        }
    }
    @SubscribeEvent public static void hit(ForgeDamageEvent.Post e){
        if(!ready||done||e.getNewDamage()<=0||!(e.getSource().getEntity() instanceof Mob m)||m.getId()!=actorId)return;
        var row=new JsonObject();row.addProperty("move",index<MOVES.length?MOVES[index].name():"COMMON_"+((BlacklightCommonInfected)m).attackHand());row.addProperty("age",m instanceof BlacklightMutant mutant?mutant.attackAge(0):m.level().getGameTime()-((BlacklightCommonInfected)m).attackStarted());row.addProperty("damage",e.getNewDamage());row.addProperty("target",e.getEntity().getId());hits.add(row);
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!initialized||!ready||done||index>=SCENES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(index>=MOVES.length){extraFrame(mc);return;}
        if(!(mc.level.getEntity(actorId) instanceof BlacklightMutant actor)||!actor.committedAttack())return;
        var move=MOVES[index];int t=(int)actor.attackAge(0);
        int contact=move==MutantMove.STALKER_POUNCE||move==MutantMove.BRUTE_HAMMER?18:move==MutantMove.BRUTE_SLAM?24:move==MutantMove.STALKER_DOUBLE?12:14;
        boolean nearContact=false;for(int k=t-2;k<=t+2;k++)nearContact|=move.contact(k);
        if(!(t==0||t==contact-5||nearContact||t>=contact-2&&t<=contact+2||t==move.ticks-6||t==move.ticks-1))return;
        String name=move.name().toLowerCase(Locale.ROOT)+"-"+String.format(Locale.ROOT,"%03d",t)+".png";if(!saved.add(name))return;
        try{
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(output.resolve(name));}
            var row=new JsonObject();row.addProperty("image",name);row.addProperty("move",move.name());row.addProperty("age",actor.attackAge(mc.getFrameTime()));row.addProperty("x",actor.getX());row.addProperty("z",actor.getZ());
            row.add("pose",new Gson().toJsonTree(CreaturePoseTable.mutant(move.species).sample(move.clip,actor.attackAge(mc.getFrameTime()))));frames.add(row);
        }catch(Exception failure){error=failure.toString();}
    }
    private static void extraFrame(Minecraft mc){
        var actor=mc.level.getEntity(actorId);int t;String key;
        if(actor instanceof BlacklightCommonInfected common){
            if(common.attackStarted()<0)return;t=(int)(mc.level.getGameTime()-common.attackStarted());key="common_"+common.attackHand();
            int contact=com.codex.blacklight.infection.CommonInfectedMotion.contactTick(common.attackHand());
            int duration=com.codex.blacklight.infection.CommonInfectedMotion.duration(common.attackHand());
            if(!(t==0||t==contact-5||t>=contact-2&&t<=contact+2||t==duration-6||t==duration-1))return;
        }else if(actor instanceof BlacklightHive hive){
            t=(int)hive.hatchAge(0);key="hive";if(!Set.of(0,4,7,8,9,20,35,49,59).contains(t))return;
        }else return;
        String name=key+"-"+String.format(Locale.ROOT,"%03d",t)+".png";if(!saved.add(name))return;
        try{
            try(var picture=Screenshot.takeScreenshot(mc.getMainRenderTarget())){picture.writeToFile(output.resolve(name));}
            var row=new JsonObject();row.addProperty("image",name);row.addProperty("move",key);row.addProperty("age",t);
            if(actor instanceof BlacklightHive hive)row.addProperty("waves",hive.waves());
            frames.add(row);
        }catch(Exception failure){error=failure.toString();}
    }
}
