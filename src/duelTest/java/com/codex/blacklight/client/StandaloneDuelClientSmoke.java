package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.client.animation.NativeHitPose;
import com.codex.blacklight.client.model.BlacklightPlayerModel;
import com.codex.blacklight.client.renderer.BlacklightPlayerRenderer;
import com.codex.blacklight.client.renderer.StandalonePlayerRendering;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.network.ClawActionPayload;
import com.codex.blacklight.registry.ModAttachments;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** One of two real remote clients. Sends only production ClawActionPayload packets and records how
 * this client renders the other player: its action clock, the flinch weight NativeHitPose would draw,
 * and the actual rendered hand matrix of the other player's native model. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class StandaloneDuelClientSmoke {
    private static final String PROPERTY="blacklight.test.standaloneDuel";
    private static String stage="WAIT",caseName="",consumer="-";
    private static int runTicks,holdTicks,frame;
    private static boolean initialized,consumeSent,written;
    private static final JsonArray cases=new JsonArray(),violations=new JsonArray();
    private static CaseLog log;
    private static java.lang.reflect.Field renderers,renderedPlayer;
    private static final class CaseLog{
        final String name;final Set<Long> revisions=new HashSet<>();int frames,remoteFrames,visibleFlinchFrames,flinchWhileRemoteConsuming,
                flinchOverNewerAction,maxVisibleFlinchRun,run,matrixFrames;long lastRevision=-1;
        double[] actionStartHand;double actionMaxHand;final List<Double> actionHandTravel=new ArrayList<>();int actionFrames,hiddenFrames;String actionClip="";final List<String> actionClips=new ArrayList<>();
        final List<String> messages=new ArrayList<>();final List<Integer> grabStages=new ArrayList<>();
        CaseLog(String name){this.name=name;}
    }
    private StandaloneDuelClientSmoke(){}
    private static boolean active(){return !System.getProperty(PROPERTY,"").isBlank()&&!written;}
    private static String me(){return Minecraft.getInstance().getUser().getName();}
    private static void violation(String text){violations.add(text);BlacklightNeoMod.LOGGER.warn("Duel QA client {} violation: {}",me(),text);}

    @SubscribeEvent public static void chat(ClientChatReceivedEvent event){
        if(!active())return;String text=event.getMessage().getString();
        if(!text.startsWith("[duel] ")){
            // Action-bar feedback is the only place a rejected grab explains itself.
            if(log!=null&&log.messages.size()<20)log.messages.add(runTicks+": "+text);
            BlacklightNeoMod.LOGGER.info("Duel QA client {} message: {}",me(),text);return;
        }
        event.setCanceled(true);var parts=text.substring(7).split(" ");
        switch(parts[0]){
            case "SETUP"->{stage="SETUP";caseName=parts[1];}
            case "RUN"->{stage="RUN";caseName=parts[1];consumer=parts[2];runTicks=0;holdTicks=0;consumeSent=false;log=new CaseLog(caseName);}
            case "END"->{if(log!=null)close(log);log=null;stage="SETUP";}
            case "DONE"->write();
            default->{}
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!active())return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        try{
            if(!initialized){
                renderers=StandalonePlayerRendering.class.getDeclaredField("RENDERERS");renderers.setAccessible(true);
                renderedPlayer=BlacklightPlayerModel.class.getDeclaredField("renderedPlayer");renderedPlayer.setAccessible(true);
                mc.options.pauseOnLostFocus=false;initialized=true;
                mc.getWindow().setTitle("Blacklight duel QA "+me()+" (do not touch)");
            }
            // First person: this client draws only the other player's body, so the native model's
            // last rendered player is the one under test.
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if(mc.screen!=null&&!(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen))mc.setScreen(null);
            mc.mouseHandler.releaseMouse();
            for(var key:List.of(mc.options.keyAttack,mc.options.keyUse,mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyJump,mc.options.keySprint))key.setDown(false);
            if(!stage.equals("RUN"))return;
            runTicks++;
            var p=mc.player;Player other=other(mc);
            boolean consuming=consumer.equals(me()),attacking=!consuming;
            if(consuming){
                var target=mc.level.getEntitiesOfClass(Villager.class,p.getBoundingBox().inflate(4)).stream().findFirst().orElse(null);
                int grab=p.getData(ModAttachments.GRAB_STAGE);
                if(log!=null&&(log.grabStages.isEmpty()||log.grabStages.get(log.grabStages.size()-1)!=grab))log.grabStages.add(grab);
                if(!consumeSent&&target!=null)face(p,target);
                // A second GRAB during capture is the player cancelling; only press while idle.
                if(!consumeSent&&grab==ConsumeVisual.NONE&&!p.getData(ModAttachments.CONSUMING)&&runTicks%20==5)PacketDistributor.sendToServer(new ClawActionPayload(ClawActionPayload.Action.GRAB));
                if(ConsumeVisual.holding(grab))holdTicks++;
                if(!consumeSent&&holdTicks>=12){PacketDistributor.sendToServer(new ClawActionPayload(ClawActionPayload.Action.CONSUME));consumeSent=true;}
            }
            // Consume cases test interruption: the attacker joins once the grab has actually begun.
            if(attacking&&other!=null&&(consumer.equals("-")||other.getData(ModAttachments.CONSUMING))){
                face(p,other);
                // A real player mashing the attack key: one press every four ticks keeps the combo buffered.
                if(runTicks%4==0)PacketDistributor.sendToServer(new ClawActionPayload(ClawActionPayload.Action.ATTACK));
            }
            if(runTicks==60)Screenshot.grab(mc.gameDirectory,"duel_"+me()+"_"+caseName+".png",mc.getMainRenderTarget(),c->{});
        }catch(Exception e){violation("client exception: "+e);BlacklightNeoMod.LOGGER.error("Duel QA client",e);}
    }
    private static Player other(Minecraft mc){
        for(var pl:mc.level.players())if(pl!=mc.player)return pl;return null;
    }
    private static void face(net.minecraft.client.player.LocalPlayer p,net.minecraft.world.entity.Entity target){
        double dx=target.getX()-p.getX(),dz=target.getZ()-p.getZ();float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
        p.setYRot(yaw);p.yRotO=yaw;p.setXRot(0);p.xRotO=0;p.setYHeadRot(yaw);p.yBodyRot=yaw;
        p.getData(ModAttachments.MOVEMENT_STATE).setCameraYaw(yaw);
    }

    @SubscribeEvent public static void frame(RenderLevelStageEvent event){
        if(!active()||log==null||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        try{
            Player other=other(mc);log.frames++;frame++;if(other==null)return;log.remoteFrames++;
            float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
            var action=other.getData(ModAttachments.PLAYER_ACTION);var hit=other.getData(ModAttachments.HIT_REACTION);
            boolean actionActive=!action.clipId().isEmpty()&&action.active(PlayerMotionController.actionTime(other,partial));
            float visible=NativeHitPose.visibleWeight(other,partial);
            boolean remoteConsuming=other.getData(ModAttachments.CONSUMING)&&other.getData(ModAttachments.GRAB_STAGE)!=ConsumeVisual.NONE;
            if(!action.clipId().isEmpty())log.revisions.add(action.revision());
            if(visible>0){log.visibleFlinchFrames++;log.run++;log.maxVisibleFlinchRun=Math.max(log.maxVisibleFlinchRun,log.run);}else log.run=0;
            if(visible>0&&remoteConsuming)log.flinchWhileRemoteConsuming++;
            if(visible>0&&actionActive&&action.started()>=hit.started())log.flinchOverNewerAction++;
            // Actual rendered hand of the other player's native model during each of its actions.
            // An off-screen player is culled, so its model keeps the last drawn pose; only sample visible frames.
            if(!event.getFrustum().isVisible(other.getBoundingBoxForCulling())){log.hiddenFrames++;closeAction(log);return;}
            double[] hand=hand(mc,other);
            if(hand!=null)log.matrixFrames++;
            if(actionActive&&visible<=0&&hand!=null){
                if(action.revision()!=log.lastRevision){closeAction(log);log.lastRevision=action.revision();log.actionClip=action.clipId();log.actionStartHand=hand;log.actionMaxHand=0;log.actionFrames=0;}
                log.actionFrames++;
                double dx=hand[0]-log.actionStartHand[0],dy=hand[1]-log.actionStartHand[1],dz=hand[2]-log.actionStartHand[2];
                log.actionMaxHand=Math.max(log.actionMaxHand,Math.sqrt(dx*dx+dy*dy+dz*dz));
            }else if(!actionActive)closeAction(log);
        }catch(Exception e){violation("frame exception: "+e);}
    }
    private static void closeAction(CaseLog l){
        if(l.actionStartHand!=null&&l.actionFrames>=6){l.actionHandTravel.add(l.actionMaxHand);l.actionClips.add(l.actionClip);}
        l.actionStartHand=null;l.lastRevision=-1;
    }
    private static double[] hand(Minecraft mc,Player other) throws ReflectiveOperationException{
        if(!(other instanceof net.minecraft.client.player.AbstractClientPlayer client))return null;
        // A frame can arrive before the first client tick ran the setup.
        if(renderers==null){
            renderers=StandalonePlayerRendering.class.getDeclaredField("RENDERERS");renderers.setAccessible(true);
            renderedPlayer=BlacklightPlayerModel.class.getDeclaredField("renderedPlayer");renderedPlayer.setAccessible(true);
        }
        var renderer=((Map<?,?>)renderers.get(null)).get(client.getSkin().model());
        if(!(renderer instanceof BlacklightPlayerRenderer nativeRenderer)||!(nativeRenderer.getModel() instanceof BlacklightPlayerModel model))return null;
        if(renderedPlayer.get(model)!=other)return null;
        var matrices=model.matrices();if(matrices==null)return null;
        var bone=PlayerMotionController.skeleton().searchJointByName("Tool_R");if(bone==null||bone.getId()>=matrices.length)return null;
        var m=matrices[bone.getId()];return new double[]{m.m30(),m.m31(),m.m32()};
    }
    private static void close(CaseLog l){
        closeAction(l);
        var o=new JsonObject();o.addProperty("case",l.name);o.addProperty("frames",l.frames);o.addProperty("remoteFrames",l.remoteFrames);
        o.addProperty("matrixFrames",l.matrixFrames);o.addProperty("hiddenFrames",l.hiddenFrames);
        var m=new JsonArray();l.messages.forEach(m::add);o.add("messages",m);var g=new JsonArray();l.grabStages.forEach(g::add);o.add("grabStages",g);o.addProperty("remoteActionStartsSeen",l.revisions.size());
        o.addProperty("visibleFlinchFrames",l.visibleFlinchFrames);o.addProperty("maxVisibleFlinchRunFrames",l.maxVisibleFlinchRun);
        o.addProperty("flinchWhileRemoteConsuming",l.flinchWhileRemoteConsuming);o.addProperty("flinchOverNewerAction",l.flinchOverNewerAction);
        var travel=new JsonArray();l.actionHandTravel.forEach(travel::add);o.add("remoteActionHandTravel",travel);
        var clips=new JsonArray();l.actionClips.forEach(clips::add);o.add("remoteActionClips",clips);
        // Idle and locomotion clips are meant to hold the hand still; only authored actions must move it.
        long authored=java.util.stream.IntStream.range(0,l.actionHandTravel.size()).filter(i->!l.actionClips.get(i).startsWith("basic_")).count();
        long still=java.util.stream.IntStream.range(0,l.actionHandTravel.size()).filter(i->!l.actionClips.get(i).startsWith("basic_")&&l.actionHandTravel.get(i)<.08).count();o.addProperty("remoteActionsWithoutMotion",still);
        cases.add(o);
        if(l.remoteFrames<50)violation(l.name+": other player was not visible");
        if(l.matrixFrames<(l.remoteFrames-l.hiddenFrames)/2)violation(l.name+": other player's native model was not rendered ("+l.matrixFrames+"/"+l.remoteFrames+")");
        if(l.flinchWhileRemoteConsuming>0)violation(l.name+": other player's consume was drawn with a flinch for "+l.flinchWhileRemoteConsuming+" frames");
        if(l.flinchOverNewerAction>0)violation(l.name+": a flinch covered the other player's newer action for "+l.flinchOverNewerAction+" frames");
        if(authored>0&&still*5>authored)violation(l.name+": "+still+"/"+authored+" of the other player's actions rendered with a frozen hand");
    }
    private static void write(){
        if(written)return;written=true;
        try{
            var out=Path.of(System.getProperty(PROPERTY)).toAbsolutePath().normalize();Files.createDirectories(out);
            var r=new JsonObject();r.addProperty("client",me());r.addProperty("passed",violations.isEmpty()&&cases.size()==3);
            r.add("cases",cases);r.add("violations",violations);
            Files.writeString(out.resolve("duel-client-"+me()+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));
        }catch(Exception e){BlacklightNeoMod.LOGGER.error("Duel QA client report",e);}
        Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());
    }
}