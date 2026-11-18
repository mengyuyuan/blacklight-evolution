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
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.tick.*;

/** Native Brute contact, ground recovery, collision and spatial-feedback audit in the disposable QA save. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class BruteVisual {
    private static final String[] NAMES={"hammer","ground_slam","charge","charge_dodge","charge_wall","hammer_bars","slam_slab","slam_air","hammer_dodge","slam_interrupted","model_front","model_threequarter","model_side","model_back","model_face","hurt_hammer","hurt_slam","hurt_charge","hurt_slab_recovery","hurt_repeat"};
    private static final JsonArray frames=new JsonArray(),hits=new JsonArray(),sounds=new JsonArray(),timeline=new JsonArray(),presentations=new JsonArray();
    private static final Set<Long> receipts=new HashSet<>();
    private static final Set<String> saved=new HashSet<>();
    private static volatile boolean ready,done;private static volatile String error;private static volatile int actorId,targetId,age,index;
    private static boolean initialized,queued;private static int bootstrap;private static long started;private static Path output;
    private static boolean charging(){return index>=2&&index<=4||index==17;}
    private static MutantMove move(){return charging()?MutantMove.BRUTE_CHARGE:index==0||index==5||index==8||index==15||index==19?MutantMove.BRUTE_HAMMER:MutantMove.BRUTE_SLAM;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(done||System.getProperty("blacklight.test.brute")==null)return;var mc=Minecraft.getInstance();
        try{
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())){
                mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;
            }
            if(!initialized&&++bootstrap>2400)throw new IllegalStateException("Brute QA startup timeout");
            var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals("Creature Skills QA Final"))throw new IllegalStateException("Disposable save required");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.brute")).toAbsolutePath().normalize();if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: required");Files.createDirectories(output);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(50);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.setScreen(null);initialized=true;Files.writeString(output.resolve("status.txt"),"Capturing brute scenes");
            }
            // The saved observer receives Prototype mode after login; its one-time perspective switch must not move this QA camera.
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;setup();return;}
            if(ready&&age>((index>=10&&index<15)?26:82)){ready=false;if(++index>=NAMES.length){done=true;var out=new JsonObject();out.add("frames",frames);out.add("hits",hits);out.add("sounds",sounds);out.add("timeline",timeline);out.add("presentations",presentations);Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));Files.writeString(output.resolve("status.txt"),"Captured; pending review");mc.stop();}else queued=false;}
        }catch(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Brute visual audit",ex);try{if(output!=null)Files.writeString(output.resolve("status.txt"),error);}catch(Exception ignored){}mc.stop();}
    }
    private static void setup(){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{
        var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
        for(var entity:java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(Objects::nonNull).toList())if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
        level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
        for(int x=-18;x<=20;x++)for(int z=-16;z<=22;z++){for(int y=78;y<=80;y++)level.setBlock(new BlockPos(x,y,z),Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),2);for(int y=81;y<105;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        var actor=ModEntities.BLACKLIGHT_BRUTE.get().create(level);actor.setPos(.5,81,.5);actor.setPersistenceRequired();actor.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(.05);actor.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);if((index>=10&&index<15))actor.setNoAi(true);actor.setYRot(0);actor.yBodyRot=actor.yBodyRotO=0;level.addFreshEntity(actor);
        Mob target=EntityType.COW.create(level);double distance=charging()?(index==4?6:5.5):move()==MutantMove.BRUTE_HAMMER?1.65:3;
        target.setPos(actor.position().add(0,0,(index>=10&&index<15)?20:distance));target.setNoAi(true);target.setPersistenceRequired();level.addFreshEntity(target);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setHealth(1000);target.getPersistentData().putLong(com.codex.blacklight.infection.InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
        p.setGameMode(GameType.SPECTATOR);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
        Vec3 offset=charging()?new Vec3(7,1.3,2.5):index==7?new Vec3(7,3.5,1.5):new Vec3(5.7,1.5,1.3),focus=charging()?new Vec3(0,1.3,2.5):index==7?new Vec3(0,3,1):new Vec3(0,1.4,1.2);
        if((index>=10&&index<15)){offset=switch(index){case 10->new Vec3(0,-.2,5.2);case 11->new Vec3(4,-.2,4);case 12->new Vec3(5.4,-.2,0);case 13->new Vec3(0,-.2,-5.4);default->new Vec3(0,1,2.7);};focus=new Vec3(0,index==14?1.95:1.25,0);}
        Vec3 camera=actor.position().add(offset),look=actor.position().add(focus).subtract(camera.add(0,p.getEyeHeight(),0));p.connection.teleport(camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
        actorId=actor.getId();targetId=target.getId();started=level.getGameTime()+20;age=-20;ready=true;
    }catch(Exception ex){error=ex.toString();}});}
    @SubscribeEvent public static void server(LevelTickEvent.Post e){
        if(!initialized||!ready||done||e.getLevel().isClientSide)return;var server=Minecraft.getInstance().getSingleplayerServer();if(server==null||e.getLevel()!=server.overworld())return;
        age=(int)(e.getLevel().getGameTime()-started);var actor=(BlacklightMutant)e.getLevel().getEntity(actorId);var target=(Mob)e.getLevel().getEntity(targetId);if(actor==null||target==null){error="Missing actor";return;}
        if((index<10||index>=15)&&age==0&&!actor.beginAttack(move(),target))error="Cannot begin brute scene="+index+" actor="+actor.position()+" target="+target.position();
        if(age==8&&index==4)for(int x=-3;x<4;x++)for(int y=81;y<88;y++)e.getLevel().setBlock(new BlockPos(x,y,4),Blocks.STONE.defaultBlockState(),2);
        if(age==9&&index==5)for(int x=-3;x<4;x++)for(int y=81;y<88;y++)e.getLevel().setBlock(new BlockPos(x,y,1),Blocks.IRON_BARS.defaultBlockState(),3);
        if(age==12&&(index==6||index==18))for(int x=-1;x<=1;x++)e.getLevel().setBlock(new BlockPos(x,81,1),Blocks.STONE_SLAB.defaultBlockState(),3);
        if(age==14&&index==3||age==10&&index==8)target.setPos(target.position().add(4,0,0));
        if(age==10&&index==7){actor.setPos(actor.position().add(0,4,0));actor.setNoGravity(true);}
        if(age==20&&index==9)actor.setNoAi(true);
        if(age==12&&(index==15||index==19)||age==18&&index==16||age==16&&index==17||age==25&&index==18||index==19&&(age==16||age==20)){
            var attacker=server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());actor.invulnerableTime=0;
            if(actor.hurt(actor.damageSources().playerAttack(attacker),2))WeaponImpact.apply(attacker,actor,index==17?WeaponMove.HAMMER_JAB:WeaponMove.MUSCLE_JAB,new Vec3(0,0,-1),true,0);
            else error="Incoming hit rejected scene="+index;
        }

        var row=new JsonObject();row.addProperty("scene",index);row.addProperty("age",age);row.addProperty("health",target.getHealth());row.addProperty("actorHealth",actor.getHealth());row.addProperty("noAi",actor.isNoAi());row.addProperty("stunned",ImpactControl.stunned(actor));row.add("actor",new Gson().toJsonTree(actor.position()));row.add("groundImpact",new Gson().toJsonTree(actor.groundImpactPoint()));row.addProperty("onGround",actor.onGround());row.addProperty("move",actor.attackMove().name());row.addProperty("poseAge",actor.attackPoseAge(0));row.addProperty("recoil",actor.recoilStart());row.addProperty("walls",actor.wallImpacts());timeline.add(row);
    }
    @SubscribeEvent public static void hurt(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e){
        if(!ready||done||e.getNewDamage()<=0||!(e.getSource().getEntity() instanceof BlacklightMutant actor)||actor.getId()!=actorId)return;
        var contact=actor.bruteContact();if(contact==null){error="Damage without brute contact";return;}
        var row=new JsonObject();row.addProperty("scene",index);row.addProperty("age",actor.attackAge(0));row.addProperty("contactAge",contact.age());row.addProperty("bone",contact.bone());row.addProperty("cube",contact.cube());row.addProperty("damage",e.getNewDamage());row.add("at",new Gson().toJsonTree(contact.point()));row.add("actor",new Gson().toJsonTree(actor.position()));hits.add(row);
    }
    @SubscribeEvent public static void sound(net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition e){
        if(!ready||done||e.getLevel().isClientSide||e.getSound()==null)return;String id=e.getSound().value().getLocation().toString();if(!id.startsWith("blacklight:infection."))return;
        var row=new JsonObject();row.addProperty("scene",index);row.addProperty("age",e.getLevel().getGameTime()-started);row.addProperty("event",id);row.add("at",new Gson().toJsonTree(e.getPosition()));row.addProperty("volume",e.getNewVolume());row.addProperty("pitch",e.getNewPitch());sounds.add(row);
    }
    private static JsonArray mesh;
    private static org.joml.Vector3f localCenter(BruteContact.Surface part)throws Exception{
        if(mesh==null)try(var input=BruteVisual.class.getResourceAsStream("/assets/blacklight/geo/blacklight_brute_mc.geo.json");var reader=new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)){
            mesh=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        }
        for(var item:mesh){var bone=item.getAsJsonObject();if(!bone.get("name").getAsString().equals(part.bone()))continue;
            var cube=bone.getAsJsonArray("cubes").get(part.cube()).getAsJsonObject();var pivot=bone.getAsJsonArray("pivot");var origin=cube.getAsJsonArray("origin");var size=cube.getAsJsonArray("size");float[] values=new float[3];
            for(int i=0;i<3;i++)values[i]=(origin.get(i).getAsFloat()+size.get(i).getAsFloat()/2-pivot.get(i).getAsFloat())/16*(i==0?-1:1);
            return new org.joml.Vector3f(values[0],values[1],values[2]);
        }throw new IllegalStateException("Missing native bone");
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(!initialized||!ready||done)return;var mc=Minecraft.getInstance();if(mc.level==null||!(mc.level.getEntity(actorId) instanceof BlacklightMutant actor))return;
        if(!(mc.getEntityRenderDispatcher().getRenderer(actor) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> renderer))return;
        var processor=renderer.getGeoModel().getAnimationProcessor();boolean tracked=true;
        for(String key:new String[]{"right_arm","left_arm","right_forearm","left_forearm","torso","head"}){var b=(software.bernie.geckolib.cache.object.GeoBone)processor.getBone(key);if(b==null)return;tracked&=b.isTrackingMatrices();b.setTrackingMatrices(true);}if(!tracked||age<0)return;
        if((index>=10&&index<15)&&age!=20)return;int frameAge=age;float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);int sub=frameAge>=10&&frameAge<=56?(int)(partial*3):0;String name=index+"-"+NAMES[index]+"-"+String.format(Locale.ROOT,"%03d",frameAge)+(sub==0?"":"-sub"+sub)+".png";if(!saved.add(name))return;
        try{
            try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(output.resolve(name));}
            var row=new JsonObject();row.addProperty("image",name);row.addProperty("scene",index);row.addProperty("age",frameAge);row.addProperty("partial",partial);row.addProperty("attackAge",actor.attackAge(partial));row.addProperty("cameraDetached",mc.gameRenderer.getMainCamera().isDetached());row.addProperty("poseAge",actor.attackPoseAge(partial));row.addProperty("move",actor.attackMove().name());
            float yaw=net.minecraft.util.Mth.rotLerp(partial,actor.yBodyRotO,actor.yBodyRot);
            var expected=actor.committedAttack()?BruteContact.body(actor.attackPose(partial),actor.getPosition(partial),yaw):List.<BruteContact.Surface>of();
            if(index>=15||index==9){var reaction=com.codex.blacklight.client.animation.BruteHitPose.current(actor);if(reaction!=null)expected=BruteContact.body(reaction,actor.getPosition(partial),yaw);}
            var reactionState=ModAttachments.peekData(actor,ModAttachments.HIT_REACTION,HitReaction.EMPTY);row.addProperty("hitStarted",reactionState.started());row.addProperty("hitWeight",reactionState.weight(actor.level().getGameTime(),partial));
            if(!expected.isEmpty()){
                double maximum=0;var points=new JsonArray();
                for(var part:expected){
                    var bone=(software.bernie.geckolib.cache.object.GeoBone)processor.getBone(part.bone());var matrix=new org.joml.Matrix4f(bone.getWorldSpaceMatrix());matrix.m00(matrix.m00()-2).m11(matrix.m11()-2).m22(matrix.m22()-2);
                    var drawn=matrix.transformPosition(localCenter(part));var actual=new Vec3(drawn.x,drawn.y,drawn.z).subtract(actor.position()).add(actor.getPosition(partial));
                    maximum=Math.max(maximum,actual.distanceTo(part.box().center));var point=new JsonObject();point.addProperty("bone",part.bone());point.addProperty("cube",part.cube());point.add("drawn",new Gson().toJsonTree(actual));point.add("expected",new Gson().toJsonTree(part.box().center));points.add(point);
                }row.addProperty("groundRecovery",actor.groundRecovery());row.addProperty("recoveryElapsed",actor.attackAge(partial)-actor.recoilStart());
                if(index==1||index==6||index>=15){var floor=new AABB(-4,80,-4,4,81,4);var slab=new AABB(-1,81,1,2,81.5,2);row.addProperty("floorOverlap",expected.stream().anyMatch(p->p.box().touches(floor.deflate(.004))));if((index==6||index==18)&&frameAge>=12)row.addProperty("slabOverlap",expected.stream().anyMatch(p->p.box().touches(slab.deflate(.004))));}
                row.addProperty("bodyError",maximum);row.add("parts",points);
                if(index==4){var wall=new AABB(-3,81,4,4,88,5);row.addProperty("wallOverlap",expected.stream().anyMatch(p->p.box().touches(wall.deflate(.005))));}
            }
            for(var receipt:com.codex.blacklight.client.renderer.ContactEffectsPresentation.receipts())if(receipts.add(receipt.serial())){
                var entry=new Gson().toJsonTree(receipt).getAsJsonObject();entry.addProperty("scene",index);entry.addProperty("firstImage",name);
                if(receipt.kind()==1||receipt.kind()==2)for(var item:hits){var hit=item.getAsJsonObject();if(hit.get("scene").getAsInt()!=index||Math.abs(hit.get("age").getAsDouble()-(receipt.tick()-started))>.01)continue;
                    var at=hit.getAsJsonObject("at");var contact=new Vec3(at.get("x").getAsDouble(),at.get("y").getAsDouble(),at.get("z").getAsDouble());double gap=Double.POSITIVE_INFINITY;
                    for(var part:expected)gap=Math.min(gap,part.box().nearest(contact).distanceTo(contact));if(Double.isFinite(gap))entry.addProperty("bodyGapAtPresentation",gap);
                }presentations.add(entry);
            }frames.add(row);
        }catch(Exception ex){error=ex.toString();}
    }
}
