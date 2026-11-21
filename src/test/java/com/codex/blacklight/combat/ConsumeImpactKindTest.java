package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeImpactKindTest {
    private static final List<PowerForm> FORMS=List.of(PowerForm.NONE,PowerForm.CLAWS,PowerForm.BLADE,
            PowerForm.HAMMERFISTS,PowerForm.WHIPFIST,PowerForm.MUSCLE_MASS);
    private static String small(PowerForm form,int variant,int ordinal) {
        return ConsumeImpactKind.forContact(form,false,ConsumeVisual.CONSUME,variant,ordinal,1);
    }
    @Test void headChestLimbAndStealthActionsHaveIndependentSounds() {
        assertEquals("chest_drive",small(PowerForm.NONE,ConsumeVariant.DEFAULT,0));
        assertEquals("heel_stamp",small(PowerForm.NONE,ConsumeVariant.HEAD_STOMP,0));
        assertEquals("knee_bump",small(PowerForm.NONE,ConsumeVariant.KNEE_HEAD,0));
        var stealth=List.of("neck_snap","rib_squeeze","joint_lock","choke_press","throat_rip");
        for(int i=0;i<stealth.size();i++)assertEquals(stealth.get(i),small(PowerForm.NONE,ConsumeVariant.STEALTH_NECK+i,0));
        assertEquals("claw_shear",small(PowerForm.CLAWS,ConsumeVariant.DEFAULT,0));
        assertEquals("skull_click",small(PowerForm.CLAWS,ConsumeVariant.CLASSIC,0));
        assertEquals("head_drop",small(PowerForm.HAMMERFISTS,ConsumeVariant.DEFAULT,0));
        assertEquals("hammer_press",small(PowerForm.HAMMERFISTS,ConsumeVariant.CLASSIC,0));
        assertEquals("blade_split",small(PowerForm.BLADE,ConsumeVariant.DEFAULT,0));
        assertEquals("arm_wrench",small(PowerForm.MUSCLE_MASS,ConsumeVariant.DEFAULT,0));
        assertEquals("hook_rend",small(PowerForm.WHIPFIST,ConsumeVariant.DEFAULT,0));
    }
    @Test void multipleContactsKeepTheirIndividualTimingAndMaterial() {
        var blows=List.of("beat_jab","beat_hook","beat_cross","beat_drive","beat_crush");
        for(int i=0;i<blows.size();i++)assertEquals(blows.get(i),small(PowerForm.NONE,ConsumeVariant.BEATDOWN,i));
        assertEquals("heart_grab",small(PowerForm.MUSCLE_MASS,ConsumeVariant.CLASSIC,0));
        assertEquals("heart_slap",small(PowerForm.MUSCLE_MASS,ConsumeVariant.CLASSIC,1));
        assertEquals("lash_bite",small(PowerForm.WHIPFIST,ConsumeVariant.CLASSIC,0));
        assertEquals("lash_recoil",small(PowerForm.WHIPFIST,ConsumeVariant.CLASSIC,1));
        assertEquals("hunter_jaw",ConsumeImpactKind.forContact(PowerForm.NONE,true,ConsumeVisual.HUNTER_CONSUME,0,0,2));
        assertEquals("hunter_rend",ConsumeImpactKind.forContact(PowerForm.NONE,true,ConsumeVisual.HUNTER_CONSUME,0,1,2));
    }
    @Test void mountedWeaponsKeepDistinctImpactsAndTheMobileTailUsesTheSameBeatAsTheServer() {
        var mounted=new HashSet<String>();
        for(var form:FORMS) {
            var large=ConsumeImpactKind.forContact(form,true,ConsumeVisual.MOUNT_CONSUME,0,0,1);
            assertTrue(large.startsWith("mount_"));assertTrue(mounted.add(large));
            for(int variant:ConsumeVariant.variants(form)) {
                var receiver=ConsumeReceiverScene.profile(form,false,variant);
                int beats=receiver==null?1:receiver.hitSeconds().size();
                var scene=new ConsumeVisual(15,175,65,form.ordinal(),ConsumeVisual.CONSUME,0,Vec3.ZERO,variant,0);
                for(int i=0;i<beats;i++) {
                    var server=ConsumeImpactKind.forContact(form,false,ConsumeVisual.CONSUME,variant,i,beats);
                    assertEquals(server,ConsumeImpactKind.forContact(scene,i));
                    assertEquals(server,ConsumeImpactKind.forContact(scene.mobileTail(),i));
                }
            }
        }
    }
    private static JsonObject json(String path) throws Exception {
        try(var in=ConsumeImpactKindTest.class.getResourceAsStream("/assets/blacklight/"+path)){
            assertNotNull(in,path);
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void contactsAndSurfacesHaveThreeTakesAndLifecyclePhasesHaveTwo() throws Exception {
        var all=new HashSet<>(Set.of("quill_extract","quill_return","quill_merge","weave"));
        for(var form:FORMS) {
            for(int stage:List.of(ConsumeVisual.CONSUME,ConsumeVisual.MOUNT_CONSUME,ConsumeVisual.HUNTER_CONSUME)) {
                boolean large=stage!=ConsumeVisual.CONSUME;
                for(int variant:ConsumeVariant.variants(form)) {
                    var nativeScene=ConsumeReceiverScene.profile(form,large,variant);
                    int beats=stage==ConsumeVisual.HUNTER_CONSUME?2:nativeScene==null?1:nativeScene.hitSeconds().size();
                    for(int i=0;i<beats;i++)all.add(ConsumeImpactKind.forContact(form,large,stage,variant,i,beats));
                    var phases=new HashSet<String>();
                    for(String phase:List.of("grip","absorb","finish"))
                        assertTrue(phases.add(ConsumeImpactKind.forPhase(form,large,stage,variant,phase)));
                    all.addAll(phases);
                }
            }
        }
        assertEquals(all,ConsumeImpactKind.registeredCues());
        var events=json("sounds.json");var en=json("lang/en_us.json");var zh=json("lang/zh_cn.json");
        all.addAll(List.of("body_land_stone","body_land_wood","body_land_soil","body_land_metal"));
        var landing=events.getAsJsonObject("consume.body_land");
        var fallback=landing.getAsJsonArray("sounds").get(0).getAsJsonObject();
        assertEquals("event",fallback.get("type").getAsString());
        assertEquals("blacklight:consume.body_land_stone",fallback.get("name").getAsString());
        assertTrue(en.has(landing.get("subtitle").getAsString()));
        assertTrue(zh.has(landing.get("subtitle").getAsString()));
        var hashes=new HashSet<String>();
        for(String cue:all) {
            assertTrue(cue.length()<=16,cue);
            var event=events.getAsJsonObject("consume."+cue);assertNotNull(event,cue);
            var sub=event.get("subtitle").getAsString();assertTrue(en.has(sub),sub);assertTrue(zh.has(sub),sub);
            boolean phase=cue.startsWith("grip_")||cue.startsWith("absorb_")||cue.startsWith("finish_");
            var sounds=event.getAsJsonArray("sounds");assertEquals(phase?2:3,sounds.size(),cue);
            for(var sound:sounds) {
                var file=sound.getAsJsonObject().get("name").getAsString().replace("blacklight:","/assets/blacklight/sounds/")+".ogg";
                try(var stream=getClass().getResourceAsStream(file)) {
                    assertNotNull(stream,file);byte[] ogg=stream.readAllBytes();
                    assertEquals("OggS",new String(ogg,0,4,StandardCharsets.US_ASCII));
                    assertTrue(hashes.add(Arrays.toString(MessageDigest.getInstance("SHA-256").digest(ogg))),file);
                }
            }
        }
        assertEquals(174,hashes.size());
    }
}
