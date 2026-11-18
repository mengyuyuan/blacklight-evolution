package com.codex.blacklight.client;

import com.codex.blacklight.animation.P1MilitaryMotion;
import com.codex.blacklight.client.model.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.*;
import com.codex.blacklight.registry.ModEntities;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Actual movement, support call, incoming projectiles and riding; no scripted client pose or NPC teleport loop. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class MilitaryMotionVisual {
    private static final String[] CASES={"walk","run","radio","hit","seat_interrupt"};
    private static Path output;private static boolean initialized,done;private static int clientTicks,captureTick=-1,renderedTick=-1;
    private static volatile int actorId=-1,scenario=-1,age=-30;private static volatile String error,completed;
    private static JsonObject rendered;private static String renderedCase;
    private static final JsonArray captures=new JsonArray(),serverRows=new JsonArray();private static final Set<String> sounds=new TreeSet<>();
    // Integrated-server-owned state.
    private static MilitarySoldier actor;private static LivingEntity target;private static BlackwatchHelicopter craft;
    private static final List<Entity> entities=new ArrayList<>();private static final Map<Long,Boolean> forced=new HashMap<>();
    private static long lastTick=Long.MIN_VALUE;private static Vec3 start;private static int arrows;private static boolean radioSeen,hitSeen,seated;
    private static void require(boolean test,String message){if(!test)throw new IllegalStateException(message);}
    private static int duration(){return scenario<2?140:scenario==4?100:150;}
    private static void cleanup(ServerLevel level){
        entities.forEach(Entity::discard);entities.clear();
        for(var old:level.getEntitiesOfClass(Entity.class,new AABB(-40,75,-40,40,125,40),e->!(e instanceof net.minecraft.world.entity.player.Player)))old.discard();
    }
    private static void prepare(ServerLevel level){
        for(int cx=-3;cx<=3;cx++)for(int cz=-3;cz<=3;cz++){long key=ChunkPos.asLong(cx,cz);forced.put(key,level.getForcedChunks().contains(key));level.setChunkForced(cx,cz,true);}
        cleanup(level);
        for(int x=-32;x<=32;x++)for(int z=-32;z<=32;z++){level.setBlock(new BlockPos(x,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);for(int y=81;y<116;y++){var p=new BlockPos(x,y,z);if(!level.isEmptyBlock(p))level.setBlock(p,Blocks.AIR.defaultBlockState(),2);}}
        scenario=0;setup(level);
    }
    private static void setup(ServerLevel level){
        cleanup(level);age=-30;radioSeen=hitSeen=seated=false;arrows=0;target=null;craft=null;
        actor=(scenario==2||scenario==4?ModEntities.MILITARY_SOLDIER:ModEntities.BLACKWATCH_SOLDIER).get().create(level);
        actor.setPos(0,81,0);actor.setPersistenceRequired();actor.setNoAi(true);actor.setAutomaticUnitJoin(false);actor.initializeWeapon();
        actor.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80);actor.setHealth(80);actor.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        if(scenario==2||scenario==4)actor.setCommandRole(MilitaryRole.OFFICER);
        if(scenario<2)actor.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(scenario==0?.25:.80);
        level.addFreshEntity(actor);entities.add(actor);actor.adoptFieldPost();actorId=actor.getId();start=actor.position();
        if(scenario==2||scenario==4){var foe=ModEntities.COMMON_INFECTED.get().create(level);foe.setPos(0,81,12);foe.setNoAi(true);foe.setPersistenceRequired();foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);foe.setHealth(2000);level.addFreshEntity(foe);entities.add(foe);target=foe;}
        if(scenario==3){var foe=EntityType.SKELETON.create(level);foe.setPos(0,81,10);foe.setNoAi(true);foe.setPersistenceRequired();foe.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));foe.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);foe.setHealth(2000);level.addFreshEntity(foe);entities.add(foe);target=foe;}
    }
    private static void advance(ServerLevel level){
        if(error!=null||completed!=null||lastTick==level.getGameTime())return;lastTick=level.getGameTime();age++;
        if(age==0){
            if(scenario!=3)actor.setNoAi(false);
            if(scenario<2){var id=UUID.randomUUID();var origin=actor.position();require(MilitaryOrganizationSystem.transaction(level,org->org.create(id,actor.faction(),MilitaryUnitType.TACTICAL_SQUAD,"motion-"+id,1,new MilitaryOrder.Point(origin.x,origin.y,origin.z))&&org.assign(id,actor.getUUID(),actor.rank())&&org.order(id,new MilitaryOrder(MilitaryOrder.Mode.MOVE,new MilitaryOrder.Point(0,81,28)))),"real movement order");actor.setUnitId(id);MilitaryOrganizationSystem.manual(level,id);}
            else if(scenario==2||scenario==4)actor.setTarget(target);
        }
        if(age<0)return;require(actor.isAlive(),"actual soldier survives presentation fixture");
        if(scenario==3&&(age==15||age==27||age==39)){
            var bounds=target.getBoundingBox().inflate(3);var old=level.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,bounds);
            ((Skeleton)target).performRangedAttack(actor,1);
            var fresh=level.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,bounds,a->a.getOwner()==target&&!old.contains(a));
            require(fresh.size()==1,"one real skeleton arrow per shot");
            // Remove random spread from this presentation fixture, retaining the real arrow and its vanilla collision/damage.
            var arrow=fresh.get(0);double dx=actor.getX()-arrow.getX(),dz=actor.getZ()-arrow.getZ(),dy=actor.getY(1d/3)-arrow.getY();
            arrow.shoot(dx,dy+Math.hypot(dx,dz)*.2,dz,1.6F,0);arrows++;
        }
        if(scenario==3&&actor.hitStarted()>=0)actor.setNoAi(false);
        if(scenario==4&&age==60){craft=ModEntities.BLACKWATCH_HELICOPTER.get().create(level);craft.setPos(actor.position());level.addFreshEntity(craft);entities.add(craft);require(actor.startRiding(craft,true),"real boarding interrupts ground presentation");}
        radioSeen|=actor.radioStarted()>=0;hitSeen|=actor.hitStarted()>=0&&actor.getLastHurtByMob()==target;seated|=actor.isPassenger();
        var row=new JsonObject();row.addProperty("case",CASES[scenario]);row.addProperty("age",age);row.addProperty("worldTick",level.getGameTime());row.addProperty("id",actor.getUUID().toString());row.addProperty("x",actor.getX());row.addProperty("y",actor.getY());row.addProperty("z",actor.getZ());row.addProperty("radioStarted",actor.radioStarted());row.addProperty("hitStarted",actor.hitStarted());row.addProperty("health",actor.getHealth());row.addProperty("ammo",actor.magazineAmmo());row.addProperty("seated",actor.isPassenger());row.addProperty("hitFromFixtureTarget",hitSeen);row.addProperty("firedArrows",arrows);serverRows.add(row);
        if(age<duration())return;
        if(scenario<2)require(actor.position().distanceTo(start)>3,"actual order moves actor in "+CASES[scenario]);
        if(scenario==2)require(radioSeen&&actor.radioStarted()<0,"real support call starts and finishes");
        if(scenario==3)require(hitSeen&&actor.getHealth()<80&&arrows==3,"real incoming arrow triggers synchronized hit clock");
        if(scenario==4)require(radioSeen&&seated,"radio-to-vehicle interruption uses same entity");
        if(++scenario<CASES.length){setup(level);return;}
        cleanup(level);forced.forEach((key,value)->{var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,value);});completed=new Gson().toJson(serverRows);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(done||System.getProperty("blacklight.test.militaryMotion","").isBlank())return;var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
        try{
            require(mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().replace('\\','/').startsWith("D:/CodexCache/creature-combat-qa/")&&server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals("Creature Skills QA Final"),"disposable D: world required");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.militaryMotion"));require(output.toAbsolutePath().startsWith(Path.of("D:/CodexCache")),"D: output required");Files.createDirectories(output);Files.writeString(output.resolve("status.txt"),"RUNNING");
                require((P1MilitaryMotion.local()!=null)==Boolean.getBoolean("blacklight.test.expectPrivateMotion"),"explicit source/fallback configuration must match actual loader");
                initialized=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(48);mc.options.framerateLimit().set(30);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(null);var id=mc.player.getUUID();
                server.execute(()->{try{var level=server.overworld();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.setDayTime(6000);level.setWeatherParameters(12000,0,false,false);server.getPlayerList().getPlayer(id).setGameMode(GameType.SPECTATOR);prepare(level);}catch(Exception failure){error=failure.toString();}});return;
            }
            require(++clientTicks<2200,"native motion fixture timeout");mc.getToasts().clear();mc.mouseHandler.releaseMouse();for(var key:mc.options.keyMappings)key.setDown(false);
            if(completed!=null){
                Files.writeString(output.resolve("server.json"),completed);Files.writeString(output.resolve("frames.json"),new GsonBuilder().setPrettyPrinting().create().toJson(captures));Files.writeString(output.resolve("sounds.json"),new Gson().toJson(sounds));
                for(var name:CASES)require(captures.asList().stream().filter(v->v.getAsJsonObject().get("case").getAsString().equals(name)).count()>=8,"missing rendered frames: "+name);
                Files.writeString(output.resolve("status.txt"),"PASS: actual walking, running, radio, incoming-arrow hit and vehicle interruption; review required");done=true;mc.stop();return;
            }
            if(mc.level.getEntity(actorId) instanceof MilitarySoldier a){var camera=a.position().add(5.3,3,5.3);var focus=a.position().add(0,1.1,0);var look=focus.subtract(camera.add(0,mc.player.getEyeHeight(),0));float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z)),pitch=(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance()));mc.player.setPos(camera);mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.yRotO=yaw;mc.player.xRotO=pitch;var id=mc.player.getUUID();server.execute(()->{var p=server.getPlayerList().getPlayer(id);if(p!=null)p.setPos(camera);});}
            server.execute(()->{try{advance(server.overworld());}catch(Exception failure){error=failure.toString();}});
        }catch(Exception failure){done=true;failure.printStackTrace();try{if(output!=null){Files.writeString(output.resolve("status.txt"),"FAIL: "+failure);Files.writeString(output.resolve("frames.json"),new Gson().toJson(captures));Files.writeString(output.resolve("server.json"),new Gson().toJson(serverRows));}}catch(Exception ignored){}mc.stop();}
    }
    private static org.joml.Vector3f point(float x,float y,float z,ModelPart... parts){var pose=new com.mojang.blaze3d.vertex.PoseStack();for(var part:parts)part.translateAndRotate(pose);return pose.last().pose().transformPosition(new org.joml.Vector3f(x/16,y/16,z/16)).mul(16);}
    private static JsonArray vector(org.joml.Vector3f value){var a=new JsonArray();a.add(value.x);a.add(value.y);a.add(value.z);return a;}
    @SubscribeEvent public static void drawn(RenderLivingEvent.Post<?,?> event){
        if(!initialized||done||age<0||scenario<0||scenario>=CASES.length||event.getEntity().getId()!=actorId||!(event.getRenderer().getModel() instanceof BlackwatchModel model))return;
        try{
            var a=(MilitarySoldier)event.getEntity();var mapField=BlackwatchModel.class.getDeclaredField("actors");mapField.setAccessible(true);var state=((Map<?,?>)mapField.get(model)).get(a);require(state!=null,"actual per-actor renderer state");
            var field=state.getClass().getDeclaredField("gait");field.setAccessible(true);var gait=(BlackwatchGait)field.get(state);
            var rootField=BlackwatchModel.class.getDeclaredField("modelRoot");rootField.setAccessible(true);var root=(ModelPart)rootField.get(model);var body=root.getChild("body");var carry=body.getChild("carry");
            var row=new JsonObject();row.addProperty("case",CASES[scenario]);row.addProperty("age",age);row.addProperty("worldTick",a.level().getGameTime());row.addProperty("actor",a.getUUID().toString());row.addProperty("radioStarted",a.radioStarted());row.addProperty("hitStarted",a.hitStarted());row.addProperty("seated",a.isPassenger());row.addProperty("phase",gait.phase());row.addProperty("movementWeight",gait.weight());row.addProperty("running",gait.running());row.addProperty("gunVisible",carry.visible);
            var main=root.getChild(a.getMainArm()==HumanoidArm.LEFT?"left_arm":"right_arm");var support=root.getChild(a.getMainArm()==HumanoidArm.LEFT?"right_arm":"left_arm");var hand=point(0,6,0,support,support.getChild("forearm"));
            row.addProperty("mainGripError",point(0,6,0,main,main.getChild("forearm")).distance(point(0,0,0,body,carry,carry.getChild("main_grip"))));row.addProperty("supportGripDistance",hand.distance(point(0,0,0,body,carry,carry.getChild("support_grip"))));row.addProperty("earDistance",hand.distance(point(a.getMainArm()==HumanoidArm.LEFT?-4.7F:4.7F,-3.5F,-.4F,root.getChild("head"))));row.add("head",vector(point(0,-4,0,root.getChild("head"))));
            for(String side:List.of("right_leg","left_leg")){var thigh=root.getChild(side);var shin=thigh.getChild("shin");var boot=shin.getChild("foot");row.add(side,vector(point(0,0,0,thigh,shin,boot)));row.addProperty(side+"_pitch",thigh.xRot);row.addProperty(side+"_knee",shin.xRot);
                float sole=-Float.MAX_VALUE;for(float x:new float[]{-2.1F,2.1F})for(float z:new float[]{-2.55F,2.2F})sole=Math.max(sole,point(x,1.05F,z,thigh,shin,boot).y);row.addProperty(side+"_lowestSole",sole);
            }
            if(scenario==3&&a.hitStarted()>=0&&!a.isPassenger())require(row.getAsJsonArray("head").get(1).getAsDouble()<8,"standing hit must not fold the torso to the ground while legs keep walking");
            if(!a.isPassenger())require(row.get("right_leg_lowestSole").getAsDouble()<24.065&&row.get("left_leg_lowestSole").getAsDouble()<24.065,"complete soles stay above the actor support plane");
            rendered=row;renderedCase=CASES[scenario];renderedTick=clientTicks;
        }catch(Exception failure){error=failure.toString();}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){
        if(!initialized||done||rendered==null||renderedTick!=clientTicks||clientTicks%4!=0||captureTick==clientTicks)return;
        try(var shot=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){String name=renderedCase+"-"+String.format(Locale.ROOT,"%04d",captures.size())+".png";shot.writeToFile(output.resolve(name));rendered.addProperty("image",name);captures.add(rendered);rendered=null;captureTick=clientTicks;}catch(Exception failure){error=failure.toString();}
    }
    @SubscribeEvent public static void sound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event){if(initialized&&(event.getName().startsWith("firearm.")||event.getName().startsWith("vehicle.")||event.getName().startsWith("entity.arrow")||event.getName().equals("entity.skeleton.shoot")||event.getName().equals("block.stone.step")))sounds.add(event.getName());}
}
