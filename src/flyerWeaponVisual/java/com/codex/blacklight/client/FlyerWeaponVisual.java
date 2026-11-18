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
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;

/** Incoming contact audit in a hidden, muted disposable client. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class FlyerWeaponVisual {
    private static final String[] NAMES={"claw","blade","hammer","claw_finisher_miss","hammer_finisher"};
    private static volatile int cameraId; private static final JsonArray timeline=new JsonArray();
    private static WeaponMove move(){return new WeaponMove[]{WeaponMove.CLAW_AUTO1,WeaponMove.BLADE_CUT,WeaponMove.HAMMER_JAB,WeaponMove.CLAW_AUTO3,WeaponMove.HAMMER_COMBO_D}[index];}
    private static final JsonArray frames=new JsonArray(),hits=new JsonArray(),presentations=new JsonArray(),attempts=new JsonArray();
    private static final Set<String> saved=new HashSet<>();private static final Set<Long> receipts=new HashSet<>();
    private static volatile boolean ready,done;private static volatile String error;private static volatile int actorId,age,index;
    private static boolean initialized,queued;private static int bootstrap;private static long started;private static Path output;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;
        if(done||System.getProperty("blacklight.test.flyerWeapon")==null)return;var mc=Minecraft.getInstance();
        try{
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())){
                mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;
            }
            if(!initialized&&++bootstrap>2400)throw new IllegalStateException("Incoming QA startup timeout");
            var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals("Creature Skills QA Final"))throw new IllegalStateException("Disposable save required");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.flyerWeapon")).toAbsolutePath().normalize();if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: required");Files.createDirectories(output);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(50);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.setScreen(null);initialized=true;Files.writeString(output.resolve("status.txt"),"Flyer weapon frame capture running");
            }
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;setup();return;}
            if(ready&&age>(index==4?130:100)){ready=false;if(++index>=NAMES.length){done=true;var out=new JsonObject();out.add("frames",frames);out.add("hits",hits);out.add("presentations",presentations);out.add("attempts",attempts);out.add("timeline",timeline);Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));Files.writeString(output.resolve("status.txt"),"Captured; pending independent frame/contact review");mc.stop();}else queued=false;}
        }catch(Exception ex){fail(ex);}
    }
    private static void fail(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Flyer weapon visual audit",ex);try{if(output!=null)Files.writeString(output.resolve("status.txt"),error);}catch(Exception ignored){}Minecraft.getInstance().stop();}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e){
        var mc=Minecraft.getInstance();if(!ready||done||mc.level==null)return;
        var actor=mc.level.getEntity(actorId);var camera=mc.level.getEntity(cameraId);if(actor==null||camera==null)return;
        var d=actor.position().add(0,.7,0).subtract(camera.position().add(0,camera.getEyeHeight(),0));
        e.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));e.setPitch((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));e.setRoll(0);
        // Forge's view event rotates the world PoseStack but leaves Camera.rotation unchanged.
        // Billboards need the same view as the screenshot; keep the disposable observer coherent.
        ((com.codex.blacklight.mixin.client.PrototypeCameraAccess)e.getCamera()).blacklight$setRotation(e.getYaw(),e.getPitch());
    }
    private static String bone(){return "left_hand";}
    private static HydraContact.Box target(EncounterMob actor){return actor.hurtRegions(0).stream().filter(r->r.bone().equals(bone())).findFirst().orElseThrow().boxes().get(0);}
    private static void setup(){
        var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{
            var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
            for(var entity:java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(Objects::nonNull).toList())if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
            level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
            for(int x=-18;x<=22;x++)for(int z=-16;z<=24;z++){level.setBlock(new BlockPos(x,80,z),Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),2);for(int y=81;y<112;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            var actor=EncounterEntities.CREATURES.get(EncounterKind.FLYER).get().create(level);actor.setPos(0,85,0);actor.setNoAi(false);actor.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(.05);actor.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);actor.setNoGravity(true);actor.setYRot(0);actor.yBodyRot=actor.yBodyRotO=actor.getYRot();level.addFreshEntity(actor);actor.updateParts();
            actor.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(2000);actor.setHealth(2000);
            p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.getInventory().clearContent();WeaponCombatSystem.cancel(p);
            var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(move().form);state.activate(move().form);ModAttachments.syncData(p,ModAttachments.PLAYER_STATE);
            var camera=EntityType.ARMOR_STAND.create(level);camera.setPos(7,88,6);camera.setInvisible(true);camera.setNoGravity(true);level.addFreshEntity(camera);cameraId=camera.getId();p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
            frameActor(actor,p);
            actorId=actor.getId();stance(actor,p);started=level.getGameTime()+20;age=-20;ready=true;
        }catch(Exception ex){error=ex.toString();}});
    }
    @SubscribeEvent public static void server(TickEvent.LevelTickEvent e){if(e.phase!=TickEvent.Phase.END)return;
        if(!initialized||!ready||done||e.level.isClientSide)return;var server=Minecraft.getInstance().getSingleplayerServer();if(server==null||e.level!=server.overworld())return;
        age=(int)(e.level.getGameTime()-started);var actor=(EncounterMob)e.level.getEntity(actorId);if(actor==null){error="Missing flyer";return;}
        var viewer=server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());if(viewer!=null)frameActor(actor,viewer);
        try{
            if(age<20&&viewer!=null)stance(actor,viewer);
            if(age==20&&viewer!=null){
                var method=WeaponCombatSystem.class.getDeclaredMethod("start",net.minecraft.server.level.ServerPlayer.class,WeaponMove.class,boolean.class,WeaponMove.Input.class,float.class);method.setAccessible(true);method.invoke(null,viewer,move(),false,WeaponMove.Input.ATTACK,0F);
            }
            var entry=new JsonObject();entry.addProperty("scene",index);entry.addProperty("age",age);entry.addProperty("stage",actor.flyerStage());entry.addProperty("stunned",ImpactControl.stunned(actor));entry.addProperty("health",actor.getHealth());entry.addProperty("effects",actor.incomingEffects());entry.add("position",new Gson().toJsonTree(actor.position()));entry.add("reaction",new Gson().toJsonTree(ModAttachments.getData(actor,ModAttachments.HIT_REACTION)));timeline.add(entry);
            var row=timeline.get(timeline.size()-1).getAsJsonObject();row.addProperty("physical",actor.flyerPhysics().active());row.addProperty("onGround",actor.onGround());row.addProperty("skinLowest",actor.hurtRegions(0).stream().flatMap(r->r.boxes().stream()).mapToDouble(b->b.bounds.minY).min().orElseThrow());
        }catch(Exception ex){error=ex.toString();}
    }
    private static void stance(EncounterMob actor,net.minecraft.server.level.ServerPlayer p){
        var at=target(actor).center;double lateral=index==0||index==3?-1.2:index==1?-.6:0;
        var eye=at.add(lateral,.65,1.5);p.connection.teleport(eye.x,eye.y-p.getEyeHeight(),eye.z,180,0);p.setDeltaMovement(Vec3.ZERO);p.setOnGround(true);
    }

    private static void frameActor(EncounterMob actor,net.minecraft.server.level.ServerPlayer p){
        var camera=actor.level().getEntity(cameraId);if(camera==null)return;
        var at=actor.position().add(0,.7,0);Vec3 point=at.add(6,1.8,6),look=at.subtract(point.add(0,camera.getEyeHeight(),0));
        camera.moveTo(point.x,point.y,point.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
    }
    @SubscribeEvent public static void hit(com.codex.blacklight.combat.ForgeDamageEvent.Post e){
        if(!ready||done||e.getNewDamage()<=0||!(e.getEntity() instanceof EncounterMob actor)||actor.getId()!=actorId)return;
        var contact=actor.incomingContact();if(contact==null){error="Missing receiver contact";return;}
        var row=new JsonObject();row.addProperty("scene",index);row.addProperty("age",age);row.addProperty("tick",actor.level().getGameTime());row.addProperty("damage",e.getNewDamage());row.addProperty("bone",contact.bone());row.add("at",new Gson().toJsonTree(contact.point()));
        row.addProperty("surfaceError",actor.hurtRegions(0).stream().flatMap(r->r.boxes().stream()).mapToDouble(b->b.nearest(contact.point()).distanceTo(contact.point())).min().orElse(999));hits.add(row);
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){if(e.phase!=TickEvent.Phase.END){ParticleProbe.reset();return;}
        if(!initialized||!ready||done)return;var mc=Minecraft.getInstance();if(mc.level==null||!(mc.level.getEntity(actorId) instanceof EncounterMob actor))return;
        var camera=mc.level.getEntity(cameraId);if(camera!=null)mc.setCameraEntity(camera);
        if(!(mc.getEntityRenderDispatcher().getRenderer(actor) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> renderer))return;
        var model=renderer.getGeoModel();var table=FlyerMotion.table();for(var n:table.bones){var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(n);if(bone!=null)bone.setTrackingMatrices(true);}if(age<0)return;
        final int frameAge=age,scene=index;float partial=mc.getFrameTime();int sub=frameAge>=18&&frameAge<=30?(int)(partial*3):0;String name=scene+"-"+NAMES[scene]+"-"+String.format(Locale.ROOT,"%03d",frameAge)+(sub==0?"":"-sub"+sub)+".png";if(!saved.add(name))return;
        try{
            try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(output.resolve(name));}
            var row=new JsonObject();row.addProperty("image",name);row.addProperty("scene",scene);row.addProperty("age",frameAge);row.addProperty("partial",partial);row.addProperty("renderTime",(double)mc.level.getGameTime()+partial);row.addProperty("health",actor.getHealth());
            for(var r:com.codex.blacklight.client.renderer.ContactEffectsPresentation.receipts())if(receipts.add(r.serial())){var entry=new Gson().toJsonTree(r).getAsJsonObject();entry.addProperty("scene",index);entry.addProperty("firstImage",name);presentations.add(entry);}
            double poseError=0;float yaw=net.minecraft.util.Mth.rotLerp(partial,actor.yBodyRotO,actor.yBodyRot);var pose=actor.flyerPose(partial);
            for(var n:table.bones){var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(n);if(bone==null)continue;
                var matrix=new org.joml.Matrix4f(bone.getWorldSpaceMatrix());matrix.m00(matrix.m00()-2).m11(matrix.m11()-2).m22(matrix.m22()-2);
                var drawn=matrix.transformPosition(new org.joml.Vector3f());var local=table.point(pose,n).rotateY((float)Math.toRadians(180-yaw));var expected=actor.position().add(local.x,local.y+.01,local.z);
                poseError=Math.max(poseError,expected.distanceTo(new Vec3(drawn.x,drawn.y,drawn.z)));
            }row.addProperty("poseError",poseError);row.addProperty("stage",actor.flyerStage());row.addProperty("ragdoll",com.codex.blacklight.client.renderer.AdaptiveRagdoll.active(actor));row.add("physical",physicalSnapshot(actor,partial));
            row.add("authoredSkin",authoredSkin(actor,model,partial));row.addProperty("serverBody",FlyerPhysics.hasPose(actor));
            row.add("effects",effectSnapshot());row.add("particles",ParticleProbe.snapshot());frames.add(row);
        }catch(Exception ex){error=ex.toString();}
    }
    private static Object field(Object o,String name) throws ReflectiveOperationException {var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    /** Native baked cube vertices transformed by the renderer's tracked bone matrices.
     * This audits the skin actually drawn, independently of the contact mesh loader. */
    private static JsonObject authoredSkin(EncounterMob actor,software.bernie.geckolib.model.GeoModel<?> model,float partial){
        double error=0,lowest=Double.POSITIVE_INFINITY;int vertices=0,cubes=0;
        // Gecko world-space tracking explicitly removes entityRenderTranslations
        // (see GeoEntityRenderer.renderRecursively). Reapply our outer renderer
        // translation as well as normal camera-relative entity interpolation.
        var origin=FlyerPhysics.hasPose(actor)?FlyerPhysics.origin(actor,partial):actor.getPosition(partial);var offset=origin.subtract(actor.position());
        var boxes=actor.hurtRegions(partial).stream().flatMap(r->r.boxes().stream()).toList();
        for(var name:FlyerMotion.table().bones){var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(name);if(bone==null)continue;
            var matrix=new org.joml.Matrix4f(bone.getWorldSpaceMatrix());matrix.m00(matrix.m00()-2).m11(matrix.m11()-2).m22(matrix.m22()-2);
            matrix.translate(-bone.getPivotX()/16,-bone.getPivotY()/16,-bone.getPivotZ()/16);
            for(var cube:bone.getCubes()){
                var stack=new com.mojang.blaze3d.vertex.PoseStack();software.bernie.geckolib.util.RenderUtils.translateToPivotPoint(stack,cube);software.bernie.geckolib.util.RenderUtils.rotateMatrixAroundCube(stack,cube);software.bernie.geckolib.util.RenderUtils.translateAwayFromPivotPoint(stack,cube);
                var transform=new org.joml.Matrix4f(matrix).mul(stack.last().pose());cubes++;
                for(var quad:cube.quads())if(quad!=null)for(var vertex:quad.vertices()){
                    var p=transform.transformPosition(new org.joml.Vector3f(vertex.position()));var point=new Vec3(p.x,p.y,p.z).add(offset);
                    lowest=Math.min(lowest,point.y);error=Math.max(error,boxes.stream().mapToDouble(b->b.nearest(point).distanceTo(point)).min().orElse(999));vertices++;
                }
            }
        }
        var out=new JsonObject();out.addProperty("skinToReceiverMax",error);out.addProperty("lowest",lowest);out.addProperty("vertices",vertices);out.addProperty("cubes",cubes);return out;
    }
    private static JsonObject physicalSnapshot(EncounterMob actor,float partial) throws ReflectiveOperationException {
        var out=new JsonObject();var f=com.codex.blacklight.client.renderer.AdaptiveRagdoll.class.getDeclaredField("BODIES");f.setAccessible(true);var body=((Map<?,?>)f.get(null)).get(actor);if(body==null)return out;
        double distance=0;int samples=0;var boxes=actor.hurtRegions(partial).stream().flatMap(r->r.boxes().stream()).toList();
        for(var part:(List<?>)field(body,"parts")){
            var pos=(Vec3)field(part,"renderedPosition");var turn=(org.joml.Quaternionf)field(part,"renderedRotation");if(pos==null||turn==null)continue;var center=(Vec3)field(part,"center");float scale=(Float)field(part,"scale");
            var mesh=(List<?>)field(part,"mesh");for(int i=0;i<mesh.size();i+=4){var vertex=(Vec3)field(mesh.get(i),"p");var v=turn.transform(vertex.subtract(center).scale(scale).toVector3f());var at=pos.add(v.x,v.y,v.z);distance=Math.max(distance,boxes.stream().mapToDouble(b->b.nearest(at).distanceTo(at)).min().orElse(0));samples++;}
        }out.addProperty("skinToReceiverMax",distance);out.addProperty("vertices",samples);out.addProperty("cuts",(Integer)field(body,"appliedCuts"));out.addProperty("components",(Integer)field(body,"components"));return out;
    }
    private static JsonArray effectSnapshot() throws ReflectiveOperationException {
        var result=new JsonArray();
        var field=com.codex.blacklight.client.renderer.InfectedImpactVisuals.class.getDeclaredField("ACTIVE");field.setAccessible(true);
        for(var impact:(Iterable<?>)field.get(null)){
            var row=new JsonObject();
            for(String name:new String[]{"at","data","anchor"}){
                var f=impact.getClass().getDeclaredField(name);f.setAccessible(true);var value=f.get(impact);
                row.add(name,new Gson().toJsonTree(value));
            }
            result.add(row);
        }
        return result;
    }
}
