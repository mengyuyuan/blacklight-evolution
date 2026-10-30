package com.codex.blacklight.client.model;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests real authored inputs and production resources, not the documentation copy. */
class BlackwatchReferenceProfileTest {
    private static final String PROFILE="/assets/blacklight/military/reference-rig.json";
    private static final Path ORIGINAL=Path.of("docs","制作资料","参考模型","blackwatch_two_hand_sneak.bbmodel");
    private static final String SOURCE_SHA="4d1b2213f160aa7a5ece31131c77102d654474277b46a6f93c8624cba71b4696";
    private JsonObject profile() throws IOException {
        var stream=getClass().getResourceAsStream(PROFILE);
        assertNotNull(stream,"The BB mapping must exist in production assets, not only in docs");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){return JsonParser.parseReader(reader).getAsJsonObject();}
    }
    private JsonObject original() throws IOException {return JsonParser.parseString(Files.readString(ORIGINAL)).getAsJsonObject();}
    private static String sha(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static JsonObject animation(JsonObject original,String name){
        for(var value:original.getAsJsonArray("animations")){var a=value.getAsJsonObject();if(a.get("name").getAsString().equals(name))return a;}
        throw new AssertionError("Missing source animation "+name);
    }
    @Test void productionProfileNamesTheExactAuthorisedInputAndSeparatesHeavyGunFromLightBody() throws Exception {
        var p=profile();assertEquals(SOURCE_SHA,sha(Files.readAllBytes(ORIGINAL)));assertEquals(SOURCE_SHA,p.get("source_sha256").getAsString());
        var counts=p.getAsJsonObject("counts");assertEquals(5365,counts.get("elements").getAsInt());
        assertEquals(16,counts.get("body_cubes").getAsInt());assertEquals(5349,counts.get("weapon_cubes").getAsInt());
        assertEquals(12,p.getAsJsonArray("bones").size());assertEquals(10,counts.get("textures").getAsInt());
        assertEquals(7,p.getAsJsonObject("animations").size());
    }
    @Test void productionProfileRetainsBothElbowsAndTheCommonCarryParent() throws Exception {
        var bones=new HashMap<String,JsonObject>();for(var b:profile().getAsJsonArray("bones")){var value=b.getAsJsonObject();bones.put(value.get("name").getAsString(),value);}
        assertEquals("Two Hand Carry",bones.get("Left Arm").get("parent").getAsString());
        assertEquals("Two Hand Carry",bones.get("Right Arm").get("parent").getAsString());
        assertEquals("Left Arm",bones.get("Left Arm Forearm").get("parent").getAsString());
        assertEquals("Right Arm",bones.get("Right Arm Forearm").get("parent").getAsString());
        assertEquals("Two Hand Carry",bones.get("Weapon - Left Grip").get("parent").getAsString());
    }
    @Test void productionAnimationChannelsRetainEveryOriginalKeyframeAndItsBone() throws Exception {
        var p=profile();var source=original();int compared=0;
        for(var value:source.getAsJsonArray("animations")){
            var sourceAnimation=value.getAsJsonObject();var exported=p.getAsJsonObject("animations").getAsJsonObject(sourceAnimation.get("name").getAsString());
            assertNotNull(exported);assertEquals(sourceAnimation.get("length").getAsDouble(),exported.get("length").getAsDouble(),1e-9);
            for(var animatorEntry:sourceAnimation.getAsJsonObject("animators").entrySet()){
                var animator=animatorEntry.getValue().getAsJsonObject();String bone=animator.get("name").getAsString();
                var channels=exported.getAsJsonObject("bones").getAsJsonObject(bone);assertNotNull(channels,bone);
                var offsets=new HashMap<String,Integer>();
                for(var key:animator.getAsJsonArray("keyframes")){
                    var k=key.getAsJsonObject();String channel=k.get("channel").getAsString();int index=offsets.getOrDefault(channel,0);offsets.put(channel,index+1);
                    var actual=channels.getAsJsonArray(channel).get(index).getAsJsonObject();
                    assertEquals(k.get("time").getAsDouble(),actual.get("time").getAsDouble(),1e-9);
                    var point=k.getAsJsonArray("data_points").get(0).getAsJsonObject();
                    for(int axis=0;axis<3;axis++)assertEquals(point.get(new String[]{"x","y","z"}[axis]).getAsDouble(),actual.getAsJsonArray("value").get(axis).getAsDouble(),1e-9);
                    compared++;
                }
                for(var count:offsets.entrySet())assertEquals(count.getValue(),channels.getAsJsonArray(count.getKey()).size());
            }
        }
        assertTrue(compared>2000,"Compare all authored channels rather than a nominal animation name");
        assertFalse(p.getAsJsonObject("animations").has("reload"),"The source has no reload; authored reload must have separate provenance");
        assertEquals("blacklight_authored",p.getAsJsonObject("reload").get("source").getAsString());
    }
    @Test void runtimeSamplerUsesTheAuthoredRisePeakAndRecovery() throws Exception {
        Class<?> type;
        try{type=Class.forName("com.codex.blacklight.client.model.BlackwatchReferenceProfile");}
        catch(ClassNotFoundException absent){fail("Production runtime must load and sample the exported BB profile");return;}
        var load=type.getMethod("load");Object p=load.invoke(null);
        var sample=type.getMethod("sample",String.class,String.class,String.class,double.class);
        double[] start=(double[])sample.invoke(p,"shoot_standing","Two Hand Carry","rotation",0d);
        double[] peak=(double[])sample.invoke(p,"shoot_standing","Two Hand Carry","rotation",.083333d);
        double[] end=(double[])sample.invoke(p,"shoot_standing","Two Hand Carry","rotation",.5d);
        assertEquals(0,start[0],1e-7);assertEquals(1.103638,peak[0],1e-5);assertEquals(0,end[0],1e-5);
        assertTrue(peak[0]>((double[])sample.invoke(p,"shoot_standing","Two Hand Carry","rotation",.016667d))[0]);
        assertArrayEquals(end,(double[])sample.invoke(p,"shoot_standing","Two Hand Carry","rotation",.8d),1e-7,"A completed shot does not loop by itself");
    }
    @Test void referenceClothTexelsAreActuallyPresentInTheLiveAtlas() throws Exception {
        var p=profile();var source=original();int copied=0;
        for(var value:p.getAsJsonArray("cloth_regions")){
            var region=value.getAsJsonObject();if(!region.get("mode").getAsString().equals("copy"))continue;
            String encoded=source.getAsJsonArray("textures").get(region.get("source_texture").getAsInt()).getAsJsonObject().get("source").getAsString();
            var sourceImage=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(encoded.substring(encoded.indexOf(',')+1))));
            try(var input=getClass().getResourceAsStream("/"+region.get("atlas").getAsString())){
                assertNotNull(input);var atlas=ImageIO.read(input);var from=region.getAsJsonArray("source_rect");var to=region.getAsJsonArray("target_rect");
                int sx=from.get(0).getAsInt(),sy=from.get(1).getAsInt(),sw=from.get(2).getAsInt(),sh=from.get(3).getAsInt();
                int tx=to.get(0).getAsInt(),ty=to.get(1).getAsInt(),tw=to.get(2).getAsInt(),th=to.get(3).getAsInt();
                for(int y=0;y<th;y++)for(int x=0;x<tw;x++){assertEquals(sourceImage.getRGB(sx+x*sw/tw,sy+y*sh/th),atlas.getRGB(tx+x,ty+y),"Declared cloth reuse must be actual atlas pixels");copied++;}
            }
        }
        assertTrue(copied>=64,"An unused reference PNG or one decorative pixel does not fulfil cloth reuse");
    }
    @Test void uniformFacesRemainTheOriginalHumanFacesAfterClothRetargeting() throws Exception {
        for(String[] value:new String[][]{{"blackwatch","953bb62a004e64a584724ea021162d5813fc23dd1fc80c7e2c77309cc742d7b6"},{"military","3fe7fec9a2ce22426e9f561a292daae617a7419d0331585a74bd4a5dc21246a2"}}){
            try(var stream=getClass().getResourceAsStream("/assets/blacklight/textures/entity/"+value[0]+"_soldier_layered.png")){
                assertNotNull(stream);var image=ImageIO.read(stream);var bytes=new ByteArrayOutputStream();
                for(int y=4;y<36;y++)for(int x=4;x<68;x++){int c=image.getRGB(x,y);bytes.write(c>>16&255);bytes.write(c>>8&255);bytes.write(c&255);bytes.write(c>>>24);}
                assertEquals(value[1],sha(bytes.toByteArray()),"Removing gear must not reveal a mask baked into the bare face");
            }
        }
    }
}
