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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.tick.*;

/** Incoming contact audit in a hidden, muted disposable client. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class FlyerHandoffVisual {
    private static final String[] NAMES={"claws_absorb","blade_absorb","claws_head_absorb","release_alive","lethal_blade"};
    private static volatile int cameraId; private static final JsonArray timeline=new JsonArray();
    private static WeaponMove move(){return WeaponMove.HAMMER_COMBO_D;}
    private static EncounterMob serverActor,clientActor;private static int heldAt=-1;private static boolean requested;
    private static final JsonArray frames=new JsonArray(),hits=new JsonArray(),presentations=new JsonArray(),attempts=new JsonArray();
    private static final Set<String> saved=new HashSet<>();private static final Set<Long> receipts=new HashSet<>();
    private static volatile boolean ready,done;private static volatile String error;private static volatile int actorId,age,index;
    private static final java.util.List<com.codex.blacklight.client.renderer.ConsumeBladeContact.Sample> bladeSamples=new java.util.ArrayList<>();
    private static boolean initialized,queued;private static int bootstrap,selected=-1;private static long started;private static Path output;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(done||System.getProperty("blacklight.test.flyerHandoff")==null)return;var mc=Minecraft.getInstance();
        try{
            if(!initialized&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&mc.screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupQuestion.experimental").getString())){
                mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();return;
            }
            if(!initialized&&++bootstrap>2400)throw new IllegalStateException("Incoming QA startup timeout");
            var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals("Creature Skills QA Final"))throw new IllegalStateException("Disposable save required");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.flyerHandoff")).toAbsolutePath().normalize();if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("D: required");
                var scene=System.getenv("BLACKLIGHT_QA_HANDOFF_SCENE");if(scene!=null&&!scene.isBlank()){selected=Integer.parseInt(scene);if(selected<0||selected>=NAMES.length)throw new IllegalArgumentException("Unknown handoff scene");index=selected;output=output.resolve("scene-"+selected+"-audit");}
                Files.createDirectories(output);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(50);mc.options.framerateLimit().set(60);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.setScreen(null);initialized=true;Files.writeString(output.resolve("status.txt"),"Flyer handoff frame capture running");
            }
            for(var key:mc.options.keyMappings)key.setDown(false);mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(!queued){queued=true;ready=false;setup();return;}
            if(ready&&age>(index==4?220:280)){ready=false;if(++index>=NAMES.length||selected>=0){done=true;var out=new JsonObject();out.add("frames",frames);out.add("hits",hits);out.add("presentations",presentations);out.add("attempts",attempts);out.add("timeline",timeline);Files.writeString(output.resolve("evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));Files.writeString(output.resolve("status.txt"),"Captured; pending independent frame/contact review");mc.stop();}else queued=false;}
        }catch(Exception ex){fail(ex);}
    }
    private static void fail(Exception ex){error=ex.toString();done=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.error("Flyer handoff visual audit",ex);try{if(output!=null)Files.writeString(output.resolve("status.txt"),error);}catch(Exception ignored){}Minecraft.getInstance().stop();}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e){
        var mc=Minecraft.getInstance();if(!ready||done||mc.level==null)return;
        var actor=mc.level.getEntity(actorId);if(actor==null)actor=clientActor;var camera=mc.level.getEntity(cameraId);if(actor==null||camera==null)return;
        var d=actor.position().add(0,.7,0).subtract(camera.position().add(0,camera.getEyeHeight(),0));
        e.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));e.setPitch((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));e.setRoll(0);
        // Forge's view event rotates the world PoseStack but leaves Camera.rotation unchanged.
        // Billboards need the same view as the screenshot; keep the disposable observer coherent.
        ((com.codex.blacklight.mixin.client.PrototypeCameraAccess)e.getCamera()).blacklight$setRotation(e.getYaw(),e.getPitch(),e.getRoll());
    }
    private static String bone(){return "left_hand";}
    private static HydraContact.Box target(EncounterMob actor){return actor.hurtRegions(0).stream().filter(r->r.bone().equals(bone())).findFirst().orElseThrow().boxes().get(0);}
    private static void setup(){
        var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{
            var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
            for(var entity:java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(Objects::nonNull).toList())if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
            level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
            for(int x=-18;x<=22;x++)for(int z=-16;z<=24;z++){level.setBlock(new BlockPos(x,80,z),Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),2);for(int y=81;y<112;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            ConsumeSystem.cancel(p);clientActor=null;heldAt=-1;requested=false;ConsumeVariant.qaForced=index==2?ConsumeVariant.CLASSIC:ConsumeVariant.DEFAULT;
            var actor=EncounterEntities.CREATURES.get(EncounterKind.FLYER).get().create(level);actor.setPos(0,85,0);actor.setNoAi(false);actor.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(.05);actor.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);actor.setNoGravity(true);actor.setYRot(0);actor.yBodyRot=actor.yBodyRotO=actor.getYRot();level.addFreshEntity(actor);actor.updateParts();
            actor.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(2000);actor.setHealth(index==4?50:300);serverActor=actor;
            p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.getInventory().clearContent();WeaponCombatSystem.cancel(p);
            var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(move().form);state.activate(move().form);p.syncData(ModAttachments.PLAYER_STATE);
            var camera=EntityType.ARMOR_STAND.create(level);camera.setPos(7,88,6);camera.setInvisible(true);camera.setNoGravity(true);level.addFreshEntity(camera);cameraId=camera.getId();p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,12000,0,false,false));
            frameActor(actor,p);
            actorId=actor.getId();stance(actor,p);started=level.getGameTime()+20;age=-20;ready=true;
        }catch(Exception ex){error=ex.toString();}});
    }
    @SubscribeEvent public static void server(LevelTickEvent.Post e){
        if(!initialized||!ready||done||e.getLevel().isClientSide)return;var server=Minecraft.getInstance().getSingleplayerServer();if(server==null||e.getLevel()!=server.overworld())return;
        age=(int)(e.getLevel().getGameTime()-started);var actor=serverActor;if(actor==null){error="Missing flyer";return;}
        var viewer=server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());if(viewer!=null)frameActor(actor,viewer);
        try{
            if(age<20&&viewer!=null)stance(actor,viewer);
            if(viewer!=null)handoff(actor,viewer);
            if(age==20&&viewer!=null){
                var method=WeaponCombatSystem.class.getDeclaredMethod("start",net.minecraft.server.level.ServerPlayer.class,WeaponMove.class,boolean.class,WeaponMove.Input.class,float.class);method.setAccessible(true);method.invoke(null,viewer,move(),false,WeaponMove.Input.ATTACK,0F);
            }
            var entry=new JsonObject();entry.addProperty("scene",index);entry.addProperty("age",age);entry.addProperty("stage",actor.flyerStage());entry.addProperty("stunned",ImpactControl.stunned(actor));entry.addProperty("health",actor.getHealth());entry.addProperty("effects",actor.incomingEffects());entry.add("position",new Gson().toJsonTree(actor.position()));entry.add("reaction",new Gson().toJsonTree(actor.getData(ModAttachments.HIT_REACTION)));entry.add("visual",new Gson().toJsonTree(actor.getData(ModAttachments.CONSUME_VISUAL)));entry.addProperty("removed",actor.isRemoved());entry.addProperty("session",viewer!=null&&ConsumeSystem.active(viewer));entry.addProperty("heldAt",heldAt);timeline.add(entry);
            var row=timeline.get(timeline.size()-1).getAsJsonObject();row.addProperty("physical",actor.flyerPhysics().active());row.addProperty("onGround",actor.onGround());row.addProperty("skinLowest",actor.hurtRegions(0).stream().flatMap(r->r.boxes().stream()).mapToDouble(b->b.bounds.minY).min().orElseThrow());
        }catch(Exception ex){error=ex.toString();}
    }
    private static void handoff(EncounterMob actor,net.minecraft.server.level.ServerPlayer p)throws Exception {
        if(age==65){
            if(!actor.flyerPhysics().active())throw new IllegalStateException("Fixture did not reach downed body");
            var form=index==1||index==4?com.codex.blacklight.power.PowerForm.BLADE:com.codex.blacklight.power.PowerForm.CLAWS;
            var state=p.getData(ModAttachments.PLAYER_STATE);state.unlock(form);state.activate(form);p.syncData(ModAttachments.PLAYER_STATE);
            var at=actor.position();p.connection.teleport(at.x,81,at.z+1.8,180,20);p.getAbilities().flying=false;p.onUpdateAbilities();p.setDeltaMovement(Vec3.ZERO);p.setOnGround(true);p.getData(ModAttachments.MOVEMENT_STATE).setCameraYaw(180);
            if(index==4&&!ConsumeLastChance.used(actor))throw new IllegalStateException("Hammer must naturally spend the last-chance save before the lethal blade");
        }
        if(age==68){
            if(index==4){
                lethalStance(actor,p);
                var method=WeaponCombatSystem.class.getDeclaredMethod("start",net.minecraft.server.level.ServerPlayer.class,WeaponMove.class,boolean.class,WeaponMove.Input.class,float.class);method.setAccessible(true);method.invoke(null,p,WeaponMove.BLADE_COMBO_D,false,WeaponMove.Input.ATTACK,0F);
            }else {ConsumeSystem.request(p);requested=ConsumeSystem.active(p);}
        }
        if(index<4&&ConsumeSystem.holding(p)&&heldAt<0)heldAt=age;
        if(heldAt>=0&&age==heldAt+12){if(index==3)ConsumeSystem.cancel(p);else ConsumeSystem.consume(p);}
    }
    private static void lethalStance(EncounterMob actor,net.minecraft.server.level.ServerPlayer p){
        // Choose a real ground-level sharp sweep through the downed native skin.
        // No damage/cut call is injected: the subsequent weapon session must hit it.
        var move=WeaponMove.BLADE_COMBO_D;var def=LocalWeaponMotion.find(move.id).orElseThrow();
        var original=p.position();Vec3 chosen=null;float facing=180;int best=0;
        for(float yaw:new float[]{180,90,0,-90})for(double r:new double[]{.8,1.1,1.4,1.7})for(double side:new double[]{0,-.4,.4}){
            var forward=Vec3.directionFromRotation(0,yaw);var right=new Vec3(-forward.z,0,forward.x);
            var at=actor.position().subtract(forward.scale(r)).add(right.scale(side));at=new Vec3(at.x,81,at.z);p.setPos(at);
            for(int tick=def.contactStartTick();tick<def.contactEndTick();tick++){
                double to=tick/20.0-def.transitionSeconds(),from=Math.max(0,to-.05);
                var cut=WeaponCutContact.sweep(p,move,from,to,at,yaw,yaw,null).trace(p,actor,actor.position());
                if(cut.contacts().size()>best){best=cut.contacts().size();chosen=at;facing=yaw;}
            }
        }
        p.setPos(original);if(chosen==null)throw new IllegalStateException("No ground blade sweep reaches the downed fixture");
        p.connection.teleport(chosen.x,chosen.y,chosen.z,facing,0);p.getData(ModAttachments.MOVEMENT_STATE).setCameraYaw(facing);
        var row=new JsonObject();row.addProperty("scene",index);row.addProperty("age",age);row.addProperty("fixtureSharpContacts",best);row.addProperty("yaw",facing);row.add("feet",new Gson().toJsonTree(chosen));attempts.add(row);
    }
    private static void stance(EncounterMob actor,net.minecraft.server.level.ServerPlayer p){
        var at=target(actor).center;double lateral=0;
        var eye=at.add(lateral,.65,1.5);p.connection.teleport(eye.x,eye.y-p.getEyeHeight(),eye.z,180,0);p.setDeltaMovement(Vec3.ZERO);p.setOnGround(true);
    }

    private static void frameActor(EncounterMob actor,net.minecraft.server.level.ServerPlayer p){
        var camera=actor.level().getEntity(cameraId);if(camera==null)return;
        var at=actor.position().add(0,.7,0);Vec3 point=at.add(6,1.8,6),look=at.subtract(point.add(0,camera.getEyeHeight(),0));
        camera.moveTo(point.x,point.y,point.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
    }
    @SubscribeEvent public static void hit(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e){
        if(!ready||done||e.getNewDamage()<=0||!(e.getEntity() instanceof EncounterMob actor)||actor.getId()!=actorId)return;
        var contact=actor.incomingContact();if(contact==null){error="Missing receiver contact";return;}
        var row=new JsonObject();row.addProperty("scene",index);row.addProperty("age",age);row.addProperty("tick",actor.level().getGameTime());row.addProperty("damage",e.getNewDamage());row.addProperty("bone",contact.bone());row.add("at",new Gson().toJsonTree(contact.point()));
        row.addProperty("surfaceError",actor.hurtRegions(0).stream().flatMap(r->r.boxes().stream()).mapToDouble(b->b.nearest(contact.point()).distanceTo(contact.point())).min().orElse(999));hits.add(row);
    }
    @SubscribeEvent public static void resetParticles(RenderFrameEvent.Pre e){ParticleProbe.reset();NativeGripProbe.reset();bladeSamples.clear();com.codex.blacklight.client.renderer.ConsumeBladeContact.observer=bladeSamples::add;}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(!initialized||!ready||done)return;var mc=Minecraft.getInstance();if(mc.level==null)return;if(mc.level.getEntity(actorId) instanceof EncounterMob fresh)clientActor=fresh;var actor=clientActor;if(actor==null)return;
        var camera=mc.level.getEntity(cameraId);if(camera!=null)mc.setCameraEntity(camera);
        if(!(mc.getEntityRenderDispatcher().getRenderer(actor) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> renderer))return;
        var model=renderer.getGeoModel();var table=FlyerMotion.table();for(var n:table.bones){var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(n);if(bone!=null)bone.setTrackingMatrices(true);}if(age<0)return;
        final int frameAge=age,scene=index;float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);int sub=frameAge>=62&&frameAge<=76?(int)(partial*3):0;String name=scene+"-"+NAMES[scene]+"-"+String.format(Locale.ROOT,"%03d",frameAge)+(sub==0?"":"-sub"+sub)+".png";if(!saved.add(name))return;
        try{
            try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(output.resolve(name));}
            var row=new JsonObject();row.addProperty("image",name);row.addProperty("scene",scene);row.addProperty("age",frameAge);row.addProperty("partial",partial);row.addProperty("renderTime",(double)mc.level.getGameTime()+partial);row.addProperty("health",actor.getHealth());row.add("visual",new Gson().toJsonTree(actor.getData(ModAttachments.CONSUME_VISUAL)));row.addProperty("removed",actor.isRemoved());row.add("position",new Gson().toJsonTree(actor.position()));
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
        double error=0,lowest=Double.POSITIVE_INFINITY,gripError=Double.POSITIVE_INFINITY,drawnGripError=Double.POSITIVE_INFINITY;int vertices=0,cubes=0;var centers=new JsonObject();var palm=heldPalm(actor,partial);
        var grip=NativeGripProbe.sample(actor.getData(ModAttachments.CONSUME_VISUAL).consumerId());
        // Gecko world-space tracking explicitly removes entityRenderTranslations
        // (see GeoEntityRenderer.renderRecursively). Reapply our outer renderer
        // translation as well as normal camera-relative entity interpolation.
        var origin=FlyerPhysics.hasPose(actor)?FlyerPhysics.origin(actor,partial):actor.getPosition(partial);var offset=origin.subtract(actor.position());if(actor.getData(ModAttachments.CONSUME_VISUAL).active())offset=actor.getPosition(partial).subtract(actor.position()).add(com.codex.blacklight.client.ClientCarryPlacement.consumeOffset(actor,partial));
        var boxes=actor.hurtRegions(partial).stream().flatMap(r->r.boxes().stream()).toList();
        for(var name:FlyerMotion.table().bones){var bone=(software.bernie.geckolib.cache.object.GeoBone)model.getAnimationProcessor().getBone(name);if(bone==null)continue;
            var matrix=new org.joml.Matrix4f(bone.getWorldSpaceMatrix());matrix.m00(matrix.m00()-2).m11(matrix.m11()-2).m22(matrix.m22()-2);
            matrix.translate(-bone.getPivotX()/16,-bone.getPivotY()/16,-bone.getPivotZ()/16);
            Vec3 sum=Vec3.ZERO;int count=0;for(var cube:bone.getCubes()){
                var stack=new com.mojang.blaze3d.vertex.PoseStack();software.bernie.geckolib.util.RenderUtil.translateToPivotPoint(stack,cube);software.bernie.geckolib.util.RenderUtil.rotateMatrixAroundCube(stack,cube);software.bernie.geckolib.util.RenderUtil.translateAwayFromPivotPoint(stack,cube);
                var transform=new org.joml.Matrix4f(matrix).mul(stack.last().pose());cubes++;
                for(var quad:cube.quads())if(quad!=null){var face=new ArrayList<Vec3>();for(var vertex:quad.vertices()){
                    var p=transform.transformPosition(new org.joml.Vector3f(vertex.position()));var point=new Vec3(p.x,p.y,p.z).add(offset);
                    face.add(point);
                    sum=sum.add(point);count++;lowest=Math.min(lowest,point.y);error=Math.max(error,boxes.stream().mapToDouble(b->b.nearest(point).distanceTo(point)).min().orElse(999));vertices++;
                }if(name.equals(actor.getData(ModAttachments.CONSUME_VISUAL).form()==com.codex.blacklight.power.PowerForm.BLADE.ordinal()?"torso":"head"))for(int i=1;i+1<face.size();i++){
                    if(palm!=null)gripError=Math.min(gripError,triangleDistance(palm,face.get(0),face.get(i),face.get(i+1)));
                    if(grip!=null)drawnGripError=Math.min(drawnGripError,triangleDistance(grip.point(),face.get(0),face.get(i),face.get(i+1)));
                }}
            }
            if(count>0)centers.add(name,new Gson().toJsonTree(sum.scale(1.0/count)));
        }
        var out=new JsonObject();out.add("centers",centers);if(Double.isFinite(gripError)){out.addProperty("gripSurfaceDistance",gripError);out.add("palm",new Gson().toJsonTree(palm));}
        if(Double.isFinite(drawnGripError)){
            out.addProperty("drawnGripSurfaceDistance",drawnGripError);out.add("drawnGrip",new Gson().toJsonTree(grip));if(palm!=null)out.addProperty("resamplePalmDifference",palm.distanceTo(grip.point()));
            var query=com.codex.blacklight.client.animation.FlyerCarryPose.contact(actor,grip.point(),partial,actor.getData(ModAttachments.CONSUME_VISUAL).form()==com.codex.blacklight.power.PowerForm.BLADE.ordinal());
            out.addProperty("gripQueryDistance",grip.point().distanceTo(query));out.add("gripQueryPoint",new Gson().toJsonTree(query));
        }
        out.addProperty("skinToReceiverMax",error);out.addProperty("lowest",lowest);out.addProperty("vertices",vertices);out.addProperty("cubes",cubes);return out;
    }
    private static Vec3 heldPalm(EncounterMob actor,float partial){
        var visual=actor.getData(ModAttachments.CONSUME_VISUAL);if(!ConsumeVisual.carrying(visual.stage()))return null;
        if(!(actor.level().getEntity(visual.consumerId()) instanceof LivingEntity owner))return null;
        var form=owner.getData(ModAttachments.PLAYER_STATE).getActivePower();boolean blade=form==com.codex.blacklight.power.PowerForm.BLADE;
        var rig=com.codex.blacklight.animation.BlacklightSkeleton.get();var wrist=rig.searchJointByName("Tool_R");
        var anchor=com.codex.blacklight.client.animation.WeaponModelAnchors.get(form,blade?"bladeGrip_R":"palm_R",new org.joml.Vector3f());
        var endpoint=new org.joml.Matrix4f(wrist.getLocalTransform()).invert().transformPosition(new org.joml.Vector3f(anchor));
        var pose=com.codex.blacklight.client.animation.BlacklightRenderPose.sample(owner,partial);
        if(blade)endpoint=new org.joml.Matrix4f(rig.getBoundTransformFor(pose,wrist)).invert().mul(rig.getBoundTransformFor(pose,rig.handR)).transformPosition(new org.joml.Vector3f(anchor));
        var matrix=new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-com.codex.blacklight.client.animation.BlacklightRenderPose.facing(owner,partial))).scale(.9375F).mul(rig.getBoundTransformFor(pose,wrist));
        var point=matrix.transformPosition(endpoint);return owner.getPosition(partial).add(point.x,point.y,point.z);
    }
    private static double triangleDistance(Vec3 p,Vec3 a,Vec3 b,Vec3 c){
        var ab=b.subtract(a);var ac=c.subtract(a);var ap=p.subtract(a);double d1=ab.dot(ap),d2=ac.dot(ap);if(d1<=0&&d2<=0)return p.distanceTo(a);
        var bp=p.subtract(b);double d3=ab.dot(bp),d4=ac.dot(bp);if(d3>=0&&d4<=d3)return p.distanceTo(b);
        double vc=d1*d4-d3*d2;if(vc<=0&&d1>=0&&d3<=0)return p.distanceTo(a.add(ab.scale(d1/(d1-d3))));
        var cp=p.subtract(c);double d5=ab.dot(cp),d6=ac.dot(cp);if(d6>=0&&d5<=d6)return p.distanceTo(c);
        double vb=d5*d2-d1*d6;if(vb<=0&&d2>=0&&d6<=0)return p.distanceTo(a.add(ac.scale(d2/(d2-d6))));
        double va=d3*d6-d5*d4;if(va<=0&&d4-d3>=0&&d5-d6>=0)return p.distanceTo(b.add(c.subtract(b).scale((d4-d3)/(d4-d3+d5-d6))));
        double sum=va+vb+vc;return p.distanceTo(a.add(ab.scale(vb/sum)).add(ac.scale(vc/sum)));
    }
    private static JsonObject physicalSnapshot(EncounterMob actor,float partial) throws ReflectiveOperationException {
        var out=new JsonObject();out.addProperty("hurtFlash",com.codex.blacklight.client.animation.RagdollDrive.hurtFlash(actor));var f=com.codex.blacklight.client.renderer.AdaptiveRagdoll.class.getDeclaredField("BODIES");f.setAccessible(true);var body=((Map<?,?>)f.get(null)).get(actor);if(body==null)return out;
        var rootKey=field(field(field(body,"root"),"bind"),"key");if(rootKey instanceof software.bernie.geckolib.cache.object.GeoBone rootBone)out.addProperty("rootBone",rootBone.getName());
        var projectionMethod=body.getClass().getDeclaredMethod("connectedPose",float.class);projectionMethod.setAccessible(true);var projected=projectionMethod.invoke(body,partial);var positionMethod=projected.getClass().getDeclaredMethod("positions");positionMethod.setAccessible(true);var projectedPositions=(Vec3[])positionMethod.invoke(projected);
        double rawToDraw=0,queryToDraw=0;int checked=0;var allParts=(List<?>)field(body,"parts");
        for(int i=0;i<allParts.size();i++){var part=allParts.get(i);var drawn=(Vec3)field(part,"renderedPosition");if(drawn==null||(Long)field(part,"renderedAt")!=actor.level().getGameTime()||Math.abs((Float)field(part,"renderedPartial")-partial)>.0001F)continue;
            var raw=((Vec3)field(part,"old")).lerp((Vec3)field(part,"pos"),partial);rawToDraw=Math.max(rawToDraw,raw.distanceTo(drawn));queryToDraw=Math.max(queryToDraw,projectedPositions[i].distanceTo(drawn));checked++;
        }out.addProperty("rawPoseToDrawMax",rawToDraw);out.addProperty("cutQueryToDrawMax",queryToDraw);out.addProperty("cutQueryChecked",checked);
        if(index==1&&age>=120&&age<=150)out.add("sectionBlood",sectionBloodSnapshot(body,actor));
        double distance=0;int samples=0;var boxes=actor.hurtRegions(partial).stream().flatMap(r->r.boxes().stream()).toList();
        if(index==1&&age>=106&&age<=126){
            out.add("drawnBlades",new Gson().toJsonTree(bladeSamples));
            out.add("bladeTarget",new Gson().toJsonTree(com.codex.blacklight.client.animation.RagdollDrive.contact(actor,32,partial)));
            if(actor.level().getEntity(actor.getData(ModAttachments.CONSUME_VISUAL).consumerId()) instanceof LivingEntity owner){
                out.add("ownerPosition",new Gson().toJsonTree(owner.getPosition(partial)));out.addProperty("ownerStage",owner.getData(ModAttachments.GRAB_STAGE));out.addProperty("ownerFacing",com.codex.blacklight.client.animation.BlacklightRenderPose.facing(owner,partial));
                var rig=com.codex.blacklight.animation.BlacklightSkeleton.get();var pose=com.codex.blacklight.client.animation.BlacklightRenderPose.sample(owner,partial);
                var transform=new org.joml.Matrix4f().translate(owner.getPosition(partial).toVector3f()).rotateY((float)Math.toRadians(180-com.codex.blacklight.client.animation.BlacklightRenderPose.facing(owner,partial))).scale(.9375F*owner.getScale()).mul(rig.getBoundTransformFor(pose,rig.handR));
                var predicted=new JsonArray();for(var line:WeaponCutContact.sharpProfiles(com.codex.blacklight.power.PowerForm.BLADE,"Hand_R").values()){var positions=new JsonArray();for(var point:line){var p=transform.transformPosition(point.toVector3f());positions.add(new Gson().toJsonTree(new Vec3(p.x,p.y,p.z)));}predicted.add(positions);}out.add("sampledBladeProfiles",predicted);
            }
            var sections=new JsonArray();
            for(var part:(List<?>)field(body,"parts")){
                var entry=new JsonObject();entry.add("region",new Gson().toJsonTree(field(field(part,"bind"),"region")));
                var boneKey=field(field(part,"bind"),"key");if(boneKey instanceof software.bernie.geckolib.cache.object.GeoBone bone)entry.addProperty("bone",bone.getName());
                for(var name:new String[]{"center","pos","half"})entry.add(name,new Gson().toJsonTree((Vec3)field(part,name)));
                var rotation=(org.joml.Quaternionf)field(part,"rotation");entry.add("rotation",new Gson().toJsonTree(new float[]{rotation.x,rotation.y,rotation.z,rotation.w}));
                var contacts=new JsonObject();for(var contact:((Map<?,?>)field(part,"sectionContacts")).entrySet()){var c=new JsonObject();c.add("point",new Gson().toJsonTree((Vec3)field(contact.getValue(),"point")));c.add("normal",new Gson().toJsonTree((Vec3)field(contact.getValue(),"normal")));contacts.add(contact.getKey().toString(),c);}entry.add("sectionContacts",contacts);
                var boundaries=new JsonArray();for(var boundary:(List<?>)field(part,"boundaries")){var c=new JsonObject();c.addProperty("key",(String)field(boundary,"key"));c.addProperty("sign",(Integer)field(boundary,"sign"));for(var name:new String[]{"point","normal"})c.add(name,new Gson().toJsonTree((Vec3)field(boundary,name)));boundaries.add(c);}entry.add("boundaries",boundaries);
                sections.add(entry);
            }out.add("sectionAudit",sections);out.add("sectionEvents",new Gson().toJsonTree(field(body,"sectionEvents")));
        }
        for(var part:(List<?>)field(body,"parts")){
            var pos=(Vec3)field(part,"renderedPosition");var turn=(org.joml.Quaternionf)field(part,"renderedRotation");if(pos==null||turn==null)continue;var center=(Vec3)field(part,"center");float scale=(Float)field(part,"scale");
            var mesh=(List<?>)field(part,"mesh");for(int i=0;i<mesh.size();i+=4){var vertex=(Vec3)field(mesh.get(i),"p");var v=turn.transform(vertex.subtract(center).scale(scale).toVector3f());var at=pos.add(v.x,v.y,v.z);distance=Math.max(distance,boxes.stream().mapToDouble(b->b.nearest(at).distanceTo(at)).min().orElse(0));samples++;}
        }out.addProperty("parts",((List<?>)field(body,"parts")).size());out.addProperty("joints",((List<?>)field(body,"joints")).size());out.addProperty("skinToReceiverMax",distance);out.addProperty("vertices",samples);out.addProperty("cuts",(Integer)field(body,"appliedCuts"));out.addProperty("components",(Integer)field(body,"components"));return out;
    }
    private static JsonArray sectionBloodSnapshot(Object body,LivingEntity actor)throws ReflectiveOperationException{
        var result=new JsonArray();var blood=com.codex.blacklight.client.renderer.WoundBlood.class;
        var field=blood.getDeclaredField("EMITTERS");field.setAccessible(true);
        var parts=(List<?>)field(body,"parts");var projection=body.getClass().getDeclaredMethod("connectedPose",float.class);projection.setAccessible(true);
        var pose=projection.invoke(body,1F);var positions=pose.getClass().getDeclaredMethod("positions");positions.setAccessible(true);var at=(Vec3[])positions.invoke(pose);
        for(var emitter:(List<?>)field.get(null)){
            if(field(emitter,"entity")!=actor||field(emitter,"sectionKey")==null)continue;
            String key=(String)field(emitter,"sectionKey");int side=(Integer)field(emitter,"sectionSide");
            var anchors=blood.getDeclaredMethod("anchors",emitter.getClass());anchors.setAccessible(true);
            var actual=(List<com.codex.blacklight.client.renderer.WoundBlood.Anchor>)anchors.invoke(null,emitter);
            double max=0;for(var anchor:actual){double gap=Double.POSITIVE_INFINITY;
                for(int i=0;i<parts.size();i++){var part=parts.get(i);boolean matching=false;
                    for(var boundary:(List<?>)field(part,"boundaries"))if(field(boundary,"key").equals(key)&&(Integer)field(boundary,"sign")==side)matching=true;
                    if(!matching)continue;var center=(Vec3)field(part,"center");var rotation=(org.joml.Quaternionf)field(part,"rotation");float scale=(Float)field(part,"scale");
                    var mesh=(List<?>)field(part,"mesh");for(int j=0;j+3<mesh.size();j+=4){if((Integer)field(mesh.get(j),"color")!=0xff70231f)continue;
                        var q=new ArrayList<Vec3>();for(int corner=0;corner<4;corner++){var point=(Vec3)field(mesh.get(j+corner),"p");var v=rotation.transform(point.subtract(center).scale(scale).toVector3f());q.add(at[i].add(v.x,v.y,v.z));}
                        for(int t=1;t<3;t++)if(q.get(t).subtract(q.get(0)).cross(q.get(t+1).subtract(q.get(0))).lengthSqr()>1e-12)gap=Math.min(gap,triangleDistance(anchor.point(),q.get(0),q.get(t),q.get(t+1)));
                    }
                }max=Math.max(max,gap);
            }
            var row=new JsonObject();row.addProperty("key",key);row.addProperty("side",side);row.addProperty("emitted",(Boolean)field(emitter,"emitted"));row.addProperty("anchors",actual.size());if(!actual.isEmpty())row.addProperty("capDistance",max);row.add("samples",new Gson().toJsonTree(actual));result.add(row);
        }return result;
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
