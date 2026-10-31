package com.codex.blacklight.client.animation;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McWeaponAssetTest {
    private JsonObject read(String name) throws Exception {
        try(var in=getClass().getResourceAsStream("/assets/blacklight/models/"+name+".json")) {
            assertNotNull(in);return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void shippedWeaponsHaveUsableFacesAndGlowingVeinTexels() throws Exception {
        for(String name:new String[]{"claw_blender","form_blade","form_hammerfists","form_whipfist","form_muscle_mass"}) {
            var model=read(name);
            var glow=ImageIO.read(getClass().getResource("/assets/blacklight/"+model.get("emissiveTexture").getAsString()));
            int glowing=0;
            for(var joint:model.getAsJsonObject("joints").entrySet())for(var item:joint.getValue().getAsJsonArray()) {
                var face=item.getAsJsonObject();var p=face.getAsJsonArray("p");
                var a=point(p.get(0));var b=point(p.get(1));var c=point(p.get(2));
                assertTrue(b.sub(a,new org.joml.Vector3f()).cross(c.sub(a,new org.joml.Vector3f())).length()>1E-8,name);
                for(var n:face.getAsJsonArray("n"))assertEquals(1,point(n).length(),1E-5,name);
                for(var texel:face.getAsJsonArray("uv")) {
                    float u=texel.getAsJsonArray().get(0).getAsFloat(),v=texel.getAsJsonArray().get(1).getAsFloat();
                    assertTrue(u>=0&&u<1&&v>=0&&v<1,name);
                    if(face.get("emissive").getAsBoolean()){
                        assertTrue((glow.getRGB((int)(u*glow.getWidth()),(int)(v*glow.getHeight()))>>>24)>0,name);glowing++;
                    }
                }
            }
            assertTrue(glowing>50,name);
            boolean pair=!name.equals("form_blade")&&!name.equals("form_whipfist");
            assertEquals(pair,model.getAsJsonObject("joints").has("Hand_L"),name);
        }
        assertEquals(6,read("form_blade").get("designRevision").getAsInt());
    }
    @Test void extendedWhipUsesTheApprovedHookInsteadOfTheOldTip() throws Exception {
        var mesh=read("form_whipfist");var tip=read("whip_tip");
        assertEquals(mesh.get("source"),tip.get("source"));
        var anchor=point(mesh.getAsJsonObject("anchors").get("tether_R"));
        var part=mesh.getAsJsonObject("parts").getAsJsonObject("Forward main hook / Hand_R");
        var first=mesh.getAsJsonObject("joints").getAsJsonArray("Hand_R").get(part.get("start").getAsInt()).getAsJsonObject();
        var traveling=tip.getAsJsonObject("joints").getAsJsonArray("Hand_R").get(0).getAsJsonObject();
        for(int i=0;i<3;i++)assertTrue(point(first.getAsJsonArray("p").get(i)).sub(anchor).distance(point(traveling.getAsJsonArray("p").get(i)))<1E-6);
    }
    private org.joml.Vector3f point(JsonElement e){var a=e.getAsJsonArray();return new org.joml.Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());}
    @Test void rearBladeTipClearsTheShoulderAcrossAttackAndChargePoses() throws Exception {
        var rig=com.codex.blacklight.animation.BlacklightSkeleton.get();
        var mesh=read("form_blade");var tip=point(mesh.getAsJsonObject("anchors").get("reverseTip_R"));
        for(String key:new String[]{"basic_idle_blade","basic_jump_charge","blade_cut","blade_cut_charge","blade_return","blade_return_charge","blade_combo_c","blade_combo_d"}){
            var clip=com.codex.blacklight.animation.MotionLibrary.find(key).orElseThrow();
            float min=Float.MAX_VALUE;
            for(float t=0;t<=clip.duration();t+=.02F){
                var pose=rig.getPoseMatrices(com.codex.blacklight.animation.MotionLibrary.pose(key,t,false));
                var world=pose[rig.handR.getId()].transformPosition(new org.joml.Vector3f(tip));
                var shoulder=pose[rig.armR.getId()].getTranslation(new org.joml.Vector3f());
                min=Math.min(min,world.distance(shoulder));
            }
            assertTrue(min>.24F,key+" rear tip clips shoulder: "+min);
        }
    }
}
