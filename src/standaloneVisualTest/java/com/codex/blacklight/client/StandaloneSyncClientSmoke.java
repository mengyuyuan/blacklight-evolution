package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.camera.ClientDevastatorCamera;
import com.codex.blacklight.client.renderer.BulletFlightVisualRenderer;
import com.codex.blacklight.combat.DevastatorSystem;
import com.codex.blacklight.network.*;
import com.codex.blacklight.power.CombatSkill;
import com.codex.blacklight.registry.ModAttachments;
import com.google.gson.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import com.codex.blacklight.forge.PacketDistributor;

/** Real remote connection and production payloads; never sets a local attachment,
 * invents a damage/landing request or invokes a server helper in the client JVM. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class StandaloneSyncClientSmoke {
    private static final String PROPERTY="blacklight.test.standaloneSync";
    private static final Path CLIENT=Path.of("D:/Blacklight-Standalone/run-sync-client-qa").toAbsolutePath().normalize();
    private static final Path OUTPUT=Path.of("D:/Blacklight-Standalone/sync-dev10-native").toAbsolutePath().normalize();
    private static final JsonArray requests=new JsonArray(),samples=new JsonArray(),settingsAcks=new JsonArray();
    private static final Map<Integer,Set<Integer>> phases=new HashMap<>();
    private static final Map<Integer,Long> sessions=new HashMap<>();
    private static final Set<String> pictures=new HashSet<>(),sentSettings=new HashSet<>();
    private static final Set<Integer> geometryCases=new HashSet<>(),fovCases=new HashSet<>();
    private static final Map<KeyMapping,Boolean> oldKeys=new IdentityHashMap<>();
    private static boolean initialized,completed,failed;
    private static CameraType oldCamera;
    private static boolean oldPause,oldMouse;
    private static long beganNanos;
    private static int ticks,caseIndex=-1;
    private static String stage="CONNECTING",error;
    private static JsonObject serverReport;
    private static boolean specialSent,releaseSent;
    private StandaloneSyncClientSmoke(){}

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void input(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.START)return;
        if(!initialized||completed||failed)return;var mc=Minecraft.getInstance();
        mc.mouseHandler.releaseMouse();mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);mc.options.keyLeft.setDown(false);mc.options.keyRight.setDown(false);mc.options.keySprint.setDown(false);mc.options.keyShift.setDown(false);mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);
        boolean preparing=stage.equals("OFF_READY")||stage.equals("ON_READY");
        var visual=mc.player==null?null:com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.DEVASTATOR_VISUAL);
        mc.options.keyJump.setDown(preparing&&(visual==null||visual.kind()<0||visual.phase()==DevastatorSystem.CHARGE)&&!releaseSent);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(completed||failed||System.getProperty(PROPERTY,"").isBlank())return;var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getConnection()==null)return;
        try{
            if(!initialized){
                require(mc.getSingleplayerServer()==null,"sync fixture requires a separate dedicated server process");
                require(mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(CLIENT)&&Path.of(System.getProperty(PROPERTY)).toAbsolutePath().normalize().equals(OUTPUT),"sync client refused directory/output");
                var address=mc.getConnection().getConnection().getRemoteAddress();
                require(address instanceof InetSocketAddress remote&&remote.getPort()==25568&&remote.getAddress().isLoopbackAddress(),"sync fixture refuses non-loopback server");
                Files.createDirectories(OUTPUT);beganNanos=System.nanoTime();oldCamera=mc.options.getCameraType();oldPause=mc.options.pauseOnLostFocus;oldMouse=mc.mouseHandler.isMouseGrabbed();
                for(var key:List.of(mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyJump,mc.options.keyShift,mc.options.keySprint,mc.options.keyAttack,mc.options.keyUse))oldKeys.put(key,key.isDown());
                mc.options.pauseOnLostFocus=false;mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);mc.mouseHandler.releaseMouse();mc.setScreen(null);ClientWeaponCombat.setFixtureOwnsInput(true);initialized=true;
            }
            require((System.nanoTime()-beganNanos)/1e9<300,"dedicated sync client exceeded five minutes");ticks++;
            var path=OUTPUT.resolve("sync-server-report.json");if(!Files.exists(path))return;
            serverReport=JsonParser.parseString(Files.readString(path)).getAsJsonObject();stage=serverReport.get("stage").getAsString();
            if(stage.equals("FAILED"))throw new IllegalStateException("dedicated server rejected sync test: "+serverReport.get("error"));
            String serverOwner=serverReport.get("owner").getAsString();if(serverOwner.isEmpty())return;
            require(serverOwner.equals(mc.player.getUUID().toString()),"dedicated report belongs to a different real player");
            var state=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.PLAYER_STATE);boolean setting=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.SKILL_SETTINGS).bulletdiveBlockBreaking();
            if(stage.equals("WAIT_OFF"))sendSetting(mc,false,"off");
            else if(stage.equals("WAIT_ON"))sendSetting(mc,true,"on");
            else if(stage.equals("WAIT_FINAL_OFF"))sendSetting(mc,false,"final_off");
            if(stage.equals("OFF_READY")||stage.equals("ON_READY")){
                int selected=serverReport.get("case").getAsInt();boolean expected=selected==1;
                // Attachment packets and the local diagnostic file are independent
                // streams. Wait for actual synchronized grants, not file arrival.
                if(!state.isInfected()||!state.isPrototypeMode()||!state.hasCombatSkill(CombatSkill.BULLETDIVE_DROP)){write();return;}
                if(caseIndex!=selected){caseIndex=selected;specialSent=releaseSent=false;sendWeapon(mc,false,false);}
                if(setting==expected){
                    String ack=expected?"on":"off";if(settingsAcks.asList().stream().noneMatch(x->x.getAsJsonObject().get("stage").getAsString().equals(ack))){
                        var row=new JsonObject();row.addProperty("stage",ack);row.addProperty("tick",mc.level.getGameTime());row.addProperty("clientAttachment",setting);row.addProperty("callerUuid",mc.player.getUUID().toString());settingsAcks.add(row);
                    }
                    var visual=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.DEVASTATOR_VISUAL);
                    if(!specialSent&&visual.kind()<0){
                        // Only genuine production input fields are transmitted.
                        // Space activates glide; the synced glide lease gates RMB.
                        if(ticks%3==0)PacketDistributor.sendToServer(new MovementInputPayload(true,false,0,0,false,false,0));
                        if(com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.GLIDE_UNTIL)>mc.level.getGameTime()){
                            sendWeapon(mc,true,false);specialSent=true;
                        }
                    }else if(visual.kind()==3&&serverReport.get("session").getAsLong()==visual.session()){
                        if(visual.phase()==DevastatorSystem.CHARGE){if(ticks%5==0)sendWeapon(mc,true,false);}
                        else if(!releaseSent){sendWeapon(mc,false,false);releaseSent=true;}
                        phases.computeIfAbsent(selected,x->new HashSet<>()).add(visual.phase());
                        long reportedSession=serverReport.get("session").getAsLong();if(reportedSession==visual.session())sessions.put(selected,visual.session());
                    }
                }
            }
            var visual=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.DEVASTATOR_VISUAL);var row=new JsonObject();row.addProperty("tick",mc.level.getGameTime());row.addProperty("stage",stage);row.addProperty("case",caseIndex);row.addProperty("clientSetting",setting);row.addProperty("serverSetting",serverReport.has("serverSetting")&&serverReport.get("serverSetting").getAsBoolean());row.addProperty("clientSession",visual.session());row.addProperty("serverSession",serverReport.get("session").getAsLong());row.addProperty("kind",visual.kind());row.addProperty("phase",visual.phase());row.addProperty("released",visual.released());row.addProperty("fallHeight",visual.fallHeight());row.addProperty("downSpeed",visual.downSpeed());row.addProperty("actualY",mc.player.getY());row.addProperty("actualOnGround",mc.player.onGround());if(samples.size()<6000)samples.add(row);
            if(stage.equals("COMPLETE")){
                require(serverReport.get("passed").getAsBoolean(),"dedicated server evidence failed");require(!setting,"final saved OFF preference did not synchronize");
                for(int which=0;which<2;which++){
                    var seen=phases.getOrDefault(which,Set.of());require(seen.contains(DevastatorSystem.CHARGE)&&seen.contains(DevastatorSystem.FLIGHT)&&seen.contains(DevastatorSystem.LAND),"client did not observe real synchronized charge/flight/land for "+which);
                    require(sessions.containsKey(which),"client session did not match dedicated server session "+which);require(geometryCases.contains(which)&&fovCases.contains(which),"dedicated flight lacked actual world geometry/FOV "+which);
                }
                completed=true;restore(mc);write();mc.stop();return;
            }
            if(ticks%5==0)write();
        }catch(Throwable problem){failed=true;error=problem.toString();BlacklightNeoMod.LOGGER.error("Dedicated client sync QA failed",problem);restore(mc);write();mc.stop();}
    }
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!initialized||completed||failed)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        try{
            var visual=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.DEVASTATOR_VISUAL);if(visual.kind()!=3||caseIndex<0)return;
            var flight=BulletFlightVisualRenderer.statistics();var fov=ClientDevastatorCamera.statistics();
            if(visual.phase()==DevastatorSystem.FLIGHT){
                if(flight.active()&&flight.airQuads()>0&&flight.vertices()>0)geometryCases.add(caseIndex);
                if(fov.flying()&&fov.outputFov()>fov.inputFov())fovCases.add(caseIndex);
            }
            String shot="case"+caseIndex+"_phase"+visual.phase();
            if(pictures.add(shot)){try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUTPUT.resolve(shot+".png"));}
                var row=new JsonObject();row.addProperty("renderEvidence",true);row.addProperty("frame",flight.frame());row.addProperty("case",caseIndex);row.addProperty("phase",visual.phase());row.addProperty("session",visual.session());row.addProperty("airQuads",flight.airQuads());row.addProperty("vertices",flight.vertices());row.addProperty("worldFovBefore",fov.inputFov());row.addProperty("worldFovAfter",fov.outputFov());samples.add(row);
            }
        }catch(Throwable problem){failed=true;error=problem.toString();restore(mc);write();mc.stop();}
    }
    private static void sendSetting(Minecraft mc,boolean value,String label){
        if(!sentSettings.add(label))return;PacketDistributor.sendToServer(new SkillSettingsPayload(value));
        var row=new JsonObject();row.addProperty("payload","SkillSettingsPayload");row.addProperty("tick",mc.level.getGameTime());row.addProperty("callerUuid",mc.player.getUUID().toString());row.addProperty("label",label);row.addProperty("breakingEnabled",value);row.addProperty("localAttachmentBeforeSend",com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.SKILL_SETTINGS).bulletdiveBlockBreaking());requests.add(row);
    }
    private static void sendWeapon(Minecraft mc,boolean held,boolean cancel){
        PacketDistributor.sendToServer(new WeaponInputPayload(false,held,false,false,cancel));var row=new JsonObject();row.addProperty("payload","WeaponInputPayload");row.addProperty("tick",mc.level.getGameTime());row.addProperty("case",caseIndex);row.addProperty("specialHeld",held);row.addProperty("cancelled",cancel);requests.add(row);
    }
    private static void restore(Minecraft mc){
        if(!initialized)return;ClientWeaponCombat.setFixtureOwnsInput(false);oldKeys.forEach(KeyMapping::setDown);mc.options.setCameraType(oldCamera);mc.options.pauseOnLostFocus=oldPause;if(oldMouse)mc.mouseHandler.grabMouse();else mc.mouseHandler.releaseMouse();
    }
    private static void write(){
        if(!initialized)return;try{var report=new JsonObject();report.addProperty("schema",1);report.addProperty("complete",completed);report.addProperty("passed",completed&&!failed);report.addProperty("separateDedicatedProcess",true);report.addProperty("loopbackPort",25568);report.addProperty("stage",stage);report.addProperty("seconds",(System.nanoTime()-beganNanos)/1e9);report.addProperty("localAttachmentsWritten",false);report.addProperty("payloadScope","boolean settings and physical button inputs only; no position/height/target/damage request");report.add("requests",requests);report.add("settingsAcks",settingsAcks);report.add("clientSamples",samples);if(serverReport!=null)report.add("dedicatedEvidence",serverReport.deepCopy());if(error!=null)report.addProperty("error",error);
            var tmp=OUTPUT.resolve("sync-client.pending.json");Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(report));Files.move(tmp,OUTPUT.resolve("sync-client-report.json"),StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception problem){BlacklightNeoMod.LOGGER.error("Cannot write dedicated sync client report",problem);}
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
