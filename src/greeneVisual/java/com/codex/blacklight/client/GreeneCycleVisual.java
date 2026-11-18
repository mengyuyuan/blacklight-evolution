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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Opt-in native arrow and all eight battle phases, rendered-skin and pick-proxy
 * audit in a disposable world. No fixed exposure duration or armor tier. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class GreeneCycleVisual {
    private static final String DIRECTORY="D:/CodexCache/creature-combat-qa/neo-client",WORLD="Creature Skills QA Final";
    private static final JsonArray frames=new JsonArray(),attempts=new JsonArray(),states=new JsonArray();
    private static final Set<String> saved=new HashSet<>();
    private static volatile boolean ready,done,sceneComplete;private static volatile String error;
    private static volatile int actorId,age,scene,bodyShotAt=-1;private static boolean initialized,queued;private static int bootstrap;
    private static int seenStages,lastStage=-1;private static boolean bodyHitVerified;
    private static long started;private static float beforeHit;private static Path output;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(System.getProperty("blacklight.test.greeneCycle","").isBlank()||done)return;var mc=Minecraft.getInstance();
        try{
            if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().replace('\\','/').equalsIgnoreCase(DIRECTORY))throw new IllegalStateException("QA directory only");
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())){mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;}
            if(!initialized&&++bootstrap>2400)throw new IllegalStateException("QA startup timeout");
            var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals(WORLD))throw new IllegalStateException("QA save only");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){output=Path.of(System.getProperty("blacklight.test.greeneCycle")).toAbsolutePath().normalize();if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: required");Files.createDirectories(output);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(45);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.setScreen(null);initialized=true;Files.writeString(output.resolve("status.txt"),"Running native Greene cycles");}
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;setup();return;}
            if(ready&&sceneComplete){ready=false;if(++scene<2){queued=false;return;}done=true;writeEvidence();Files.writeString(output.resolve("status.txt"),"Captured two native-arrow eight-stage cycles; inspect geometry and frames");mc.stop();}
        }catch(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Greene native audit",ex);try{if(output!=null){writeEvidence();Files.writeString(output.resolve("status.txt"),error);}}catch(Exception ignored){}mc.stop();}
    }
    private static void writeEvidence() throws Exception {var result=new JsonObject();result.addProperty("schema",2);result.add("frames",frames);result.add("attempts",attempts);result.add("states",states);result.addProperty("error",error);Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}
    private static void setup(){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{
        var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
        for(var entity:java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(Objects::nonNull).toList())if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
        level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
        for(int x=-22;x<=22;x++)for(int z=-22;z<=26;z++){level.setBlock(new BlockPos(x,80,z),((x%4==0||z%4==0)?Blocks.GRAY_CONCRETE:Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState(),2);for(int y=81;y<102;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        var actor=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(level);
        // Explicit existing encounter fixture; fresh opening has its own regression.
        var standing=actor.greeneBattle().save();standing.putBoolean("OpeningPending",false);actor.greeneBattle().load(standing);
        actor.setPos(0,81,0);actor.goalSelector.removeAllGoals(g->true);actor.targetSelector.removeAllGoals(g->true);actor.setPersistenceRequired();actor.setYRot(scene==0?0:127);actor.yBodyRot=actor.yBodyRotO=actor.getYRot();
        // Brood spawning is audited separately; keep this scene's rays and
        // camera clear without suppressing the mother's real battle stages.
        actor.restrictTo(actor.blockPosition(),10);level.addFreshEntity(actor);actor.updateParts();
        p.setGameMode(GameType.SPECTATOR);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
        Vec3 camera=new Vec3(16,87,20),look=new Vec3(0,84,1).subtract(camera.add(0,p.getEyeHeight(),0));p.connection.teleport(camera.x,camera.y,camera.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
        actorId=actor.getId();started=level.getGameTime()+30;age=-30;seenStages=0;lastStage=-1;bodyShotAt=-1;bodyHitVerified=false;sceneComplete=false;ready=true;
    }catch(Exception ex){error=ex.toString();}});}
    @SubscribeEvent public static void server(LevelTickEvent.Post e){
        if(!initialized||!ready||done||e.getLevel().isClientSide)return;var server=Minecraft.getInstance().getSingleplayerServer();if(server==null||e.getLevel()!=server.overworld())return;
        age=(int)(e.getLevel().getGameTime()-started);if(!(e.getLevel().getEntity(actorId) instanceof EncounterMob actor)){error="Missing Greene";return;}
        try{
            var cycle=actor.greeneBattle();int stage=cycle.stage();float local=cycle.age(0);boolean entered=stage!=lastStage,finished=false;
            if(age>3400)throw new IllegalStateException("Eight-stage Greene cycle did not finish: stage="+stage+" local="+local);
            if(actor.position().multiply(1,0,1).length()>.01)throw new IllegalStateException("Greene horizontal origin drift: "+actor.position());
            double depth=GreeneBattleCycle.SOURCE_DEPTH*GreeneMotion.table().sourceUnitBlocks();
            if(actor.getY()<81-depth-.05||actor.getY()>81.05)throw new IllegalStateException("Greene left the authored source Move range: "+actor.position());
            if(age==10||age==35||age==60)shoot(actor,"weak_"+((age-10)/25),(actor.greeneSegmentMaximum()+1)/6F*1.25F);
            if(age==20||age==45||age==70){int slot=(age-20)/25;if(!actor.broken(slot))throw new IllegalStateException("Native arrow did not break appendage "+slot);}
            if(stage==GreeneBattleCycle.DOWN&&local>=25&&bodyShotAt<0){beforeHit=actor.getHealth();bodyShotAt=age;shoot(actor,"head",3);}
            if(bodyShotAt>=0&&age==bodyShotAt+10){
                if(!(actor.getHealth()<beforeHit&&stage==GreeneBattleCycle.DOWN&&local>=35))throw new IllegalStateException("Native body hit missing or down clock reset");
                bodyHitVerified=true;
            }
            if(entered){
                if(stage>=GreeneBattleCycle.COLLAPSING&&stage<=GreeneBattleCycle.UNDERGROUND&&mask(actor)!=7)throw new IllegalStateException("Shield restored before emergence: "+stage);
                if(stage==GreeneBattleCycle.UNDERGROUND&&actor.getY()>81-depth+.05)throw new IllegalStateException("Underground state without real downward movement");
                if(stage==GreeneBattleCycle.EMERGE_WAIT&&(mask(actor)!=0||cycle.armourWave()!=2))throw new IllegalStateException("Next-wave shield was not restored at emergence entry");
                seenStages|=1<<stage;lastStage=stage;
            }
            if(stage==GreeneBattleCycle.UNDERGROUND||stage==GreeneBattleCycle.EMERGE_WAIT||stage==GreeneBattleCycle.SUBMERGING&&local>=16){
                if(cycle.targetable()||actor.hurtRegions(0).stream().anyMatch(r->!r.boxes().isEmpty()))throw new IllegalStateException("Underground body retains receiving geometry");
            }
            if(stage==GreeneBattleCycle.EMERGING&&cycle.targetable()!=(local>=GreeneBattleCycle.EMERGE_TARGET_TICKS))throw new IllegalStateException("Emergence targetability gate disagrees with source movement end");
            if(stage==GreeneBattleCycle.GUARDED&&(seenStages&(1<<GreeneBattleCycle.EMERGING))!=0&&local>=20){
                if(seenStages!=255||!bodyHitVerified||mask(actor)!=0||cycle.controlsRoot()||Math.abs(actor.getY()-81)>.05)throw new IllegalStateException("Incomplete eight-stage recovery");
                finished=true;
            }
            if(age>=0&&(age%10==0||entered)){var row=new JsonObject();row.addProperty("scene",scene);row.addProperty("age",age);row.addProperty("cycleAge",actor.exposedAge(0));row.addProperty("stage",stage);row.addProperty("stageAge",local);row.addProperty("health",actor.getHealth());row.addProperty("mask",mask(actor));
                row.addProperty("waveCount",cycle.waveCount());row.addProperty("armourWave",cycle.armourWave());row.addProperty("segmentMaximum",actor.greeneSegmentMaximum());row.addProperty("targetable",cycle.targetable());row.addProperty("canAttack",cycle.canAttack());row.add("position",new Gson().toJsonTree(actor.position()));states.add(row);}
            // Publish completion only after the final server evidence row.
            if(finished)sceneComplete=true;
        }catch(Exception ex){error=ex.toString();}
    }
    private static int mask(EncounterMob actor){return (actor.broken(0)?1:0)|(actor.broken(1)?2:0)|(actor.broken(2)?4:0);}
    private static void shoot(EncounterMob actor,String bone,float damage){
        var region=actor.hurtRegions(1).stream().filter(r->r.bone().equals(bone)).findFirst().orElseThrow();var box=region.boxes().stream().max(Comparator.comparingDouble(b->b.center.distanceToSqr(actor.position()))).orElseThrow();
        Vec3 at=box.center,from=at.add(at.subtract(actor.position()).normalize().scale(5));
        var arrow=EntityType.ARROW.create(actor.level());arrow.setPos(from);arrow.setNoGravity(true);arrow.setDeltaMovement(at.subtract(from).normalize().scale(6));arrow.setBaseDamage(damage);actor.level().addFreshEntity(arrow);
        var row=new JsonObject();row.addProperty("scene",scene);row.addProperty("age",age);row.addProperty("bone",bone);row.addProperty("arrowBaseDamage",damage);row.addProperty("segmentMaximum",actor.greeneSegmentMaximum());row.add("from",new Gson().toJsonTree(from));row.add("to",new Gson().toJsonTree(at));attempts.add(row);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(!initialized||!ready||done)return;var mc=Minecraft.getInstance();if(mc.level==null||!(mc.level.getEntity(actorId) instanceof EncounterMob actor))return;
        if(!(mc.getEntityRenderDispatcher().getRenderer(actor) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> renderer))return;var model=renderer.getGeoModel();
        for(String name:GreeneMotion.table().bones){var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(name);if(bone!=null)bone.setTrackingMatrices(true);}
        int t=age,stage=actor.greeneStage();boolean firstStage=!saved.contains(scene+"-stage-"+stage);
        int interval=stage==GreeneBattleCycle.DOWN||stage==GreeneBattleCycle.UNDERGROUND?20:4;
        if(t<0||!firstStage&&t%interval!=0&&!(bodyShotAt>=0&&t>=bodyShotAt&&t<=bodyShotAt+15))return;
        String name=scene+"-"+String.format(Locale.ROOT,"%04d",t)+".png";if(!saved.add(name))return;saved.add(scene+"-stage-"+stage);
        try{float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);var row=new JsonObject();row.addProperty("image",name);row.addProperty("scene",scene);row.addProperty("age",t);row.addProperty("cycleAge",actor.exposedAge(partial));row.addProperty("stage",stage);row.addProperty("stageAge",actor.greeneBattle().age(partial));row.addProperty("mask",mask(actor));row.addProperty("health",actor.getHealth());
            row.addProperty("targetable",actor.greeneBattle().targetable());row.add("position",new Gson().toJsonTree(actor.getPosition(partial)));row.add("geometry",skin(actor,model,partial));if(firstStage)matrices(actor,model,partial);try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(output.resolve(name));}frames.add(row);
        }catch(Exception ex){error=ex.toString();}
    }
    private static void matrices(EncounterMob actor,software.bernie.geckolib.model.GeoModel<?> model,float partial) throws Exception {
        var table=GreeneMotion.table();var pose=GreeneMotion.pose(actor,partial);var transforms=table.transforms(pose);var rows=new JsonArray();
        for(int i=0;i<table.bones.length;i++){var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(table.bones[i]);if(bone==null||bone.isHidden())continue;
            var actual=new org.joml.Matrix4f(bone.getWorldSpaceMatrix());actual.m00(actual.m00()-2).m11(actual.m11()-2).m22(actual.m22()-2);actual.translate(-bone.getPivotX()/16,-bone.getPivotY()/16,-bone.getPivotZ()/16);
            var expected=new org.joml.Matrix4f().translate((float)actor.getX(),(float)actor.getY()+.01F,(float)actor.getZ()).rotateY((float)Math.toRadians(180-net.minecraft.util.Mth.rotLerp(partial,actor.yBodyRotO,actor.yBodyRot))).mul(transforms[i]);
            var row=new JsonObject();row.addProperty("bone",table.bones[i]);row.add("actual",new Gson().toJsonTree(actual.get(new float[16])));row.add("expected",new Gson().toJsonTree(expected.get(new float[16])));row.add("pose",new Gson().toJsonTree(pose[i]));row.add("rotation",new Gson().toJsonTree(new float[]{bone.getRotX(),bone.getRotY(),bone.getRotZ()}));rows.add(row);
        }Files.writeString(output.resolve("matrices-"+scene+"-stage-"+actor.greeneStage()+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(rows));
    }
    private static JsonObject skin(EncounterMob actor,software.bernie.geckolib.model.GeoModel<?> model,float partial){
        double error=0,lowest=Double.POSITIVE_INFINITY;String errorBone="",lowestBone="";int vertices=0,cubes=0,hidden=0;float yaw=net.minecraft.util.Mth.rotLerp(partial,actor.yBodyRotO,actor.yBodyRot);
        // The real broken mask hides limbs until next-wave emergence. Source
        // surfaces can still render while targetability disables all receivers.
        var surfaces=GreeneContact.surfaces(GreeneMotion.pose(actor,partial),actor.position(),yaw,mask(actor));
        for(String name:GreeneMotion.table().bones){var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(name);if(bone==null)continue;if(bone.isHidden()){hidden++;continue;}
            var boxes=surfaces.stream().filter(s->s.bone().equals(name)).map(GreeneContact.Surface::box).toList();
            var matrix=new org.joml.Matrix4f(bone.getWorldSpaceMatrix());matrix.m00(matrix.m00()-2).m11(matrix.m11()-2).m22(matrix.m22()-2);matrix.translate(-bone.getPivotX()/16,-bone.getPivotY()/16,-bone.getPivotZ()/16);
            for(var cube:bone.getCubes()){var stack=new com.mojang.blaze3d.vertex.PoseStack();software.bernie.geckolib.util.RenderUtil.translateToPivotPoint(stack,cube);software.bernie.geckolib.util.RenderUtil.rotateMatrixAroundCube(stack,cube);software.bernie.geckolib.util.RenderUtil.translateAwayFromPivotPoint(stack,cube);var transform=new org.joml.Matrix4f(matrix).mul(stack.last().pose());cubes++;
                // Match matrices(): Gecko's tracked world matrix and these
                // surfaces both use entity.position(), not the render origin.
                for(var quad:cube.quads())if(quad!=null)for(var vertex:quad.vertices()){var p=transform.transformPosition(new org.joml.Vector3f(vertex.position()));var point=new Vec3(p.x,p.y,p.z);if(point.y<lowest){lowest=point.y;lowestBone=name;}double distance=boxes.stream().mapToDouble(b->b.nearest(point).distanceTo(point)).min().orElse(999);if(distance>error){error=distance;errorBone=name;}vertices++;}
            }
        }var row=new JsonObject();row.addProperty("skinToSourceSurfaceMax",error);row.addProperty("receivingBoxes",actor.hurtRegions(partial).stream().mapToInt(r->r.boxes().size()).sum());row.addProperty("errorBone",errorBone);row.addProperty("lowest",lowest);row.addProperty("lowestBone",lowestBone);row.addProperty("vertices",vertices);row.addProperty("cubes",cubes);row.addProperty("hidden",hidden);return row;
    }
}
