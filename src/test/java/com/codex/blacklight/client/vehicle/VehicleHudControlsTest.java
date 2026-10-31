package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.client.VehicleHud;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import static org.junit.jupiter.api.Assertions.*;

class VehicleHudControlsTest {
    private static KeyMapping mapping(String name,int code){return new KeyMapping(name,InputConstants.Type.KEYSYM,code,"key.categories.movement");}
    private static KeyMapping[] remapped(){return new KeyMapping[]{mapping("key.forward",GLFW.GLFW_KEY_I),mapping("key.back",GLFW.GLFW_KEY_K),
        mapping("key.left",GLFW.GLFW_KEY_J),mapping("key.right",GLFW.GLFW_KEY_L),mapping("key.jump",GLFW.GLFW_KEY_P),mapping("key.sneak",GLFW.GLFW_KEY_LEFT_CONTROL),
        mapping("key.blacklight.vehicle_next_seat",GLFW.GLFW_KEY_F23),mapping("key.blacklight.vehicle_next_ammo",GLFW.GLFW_KEY_F24)};}
    private static void language(String locale,Runnable run)throws Exception{
        Language old=Language.getInstance();var map=new HashMap<String,String>();
        for(String path:new String[]{"assets/minecraft/lang/en_us.json","assets/blacklight/lang/"+locale+".json"}){
            try(var stream=VehicleHudControlsTest.class.getClassLoader().getResourceAsStream(path)){
                assertNotNull(stream,"real resource missing: "+path);
                JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e->map.put(e.getKey(),e.getValue().getAsString()));
            }
        }
        Language.inject(new Language(){
            @Override public String getOrDefault(String key,String fallback){return map.getOrDefault(key,fallback);}
            @Override public boolean has(String key){return map.containsKey(key);}
            @Override public boolean isDefaultRightToLeft(){return false;}
            @Override public FormattedCharSequence getVisualOrder(FormattedText text){return FormattedCharSequence.forward(text.getString(),Style.EMPTY);}
        });
        try{run.run();}finally{Language.inject(old);}
    }
    @Test void actualRemappedKeysAreRenderedInsteadOfDefaultControlNames()throws Exception{
        language("en_us",()->{
            var keys=remapped();String text=VehicleHud.controlHints(keys).getString();
            for(var key:keys)assertTrue(text.contains(key.getTranslatedKeyMessage().getString()),"HUD must show actual bound key "+key.getName()+": "+text);
            assertFalse(text.contains("Shift"),"Left Control must not be described as Shift");
            assertFalse(text.contains("Bind seat/ammo"),"bound seat/ammo must not be described as unbound");
        });
    }
    @Test void nextRenderUsesChangedLiveBinding()throws Exception{
        language("en_us",()->{
            var keys=remapped();String before=VehicleHud.controlHints(keys).getString();
            keys[5].setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_RIGHT_ALT));
            String after=VehicleHud.controlHints(keys).getString();
            assertNotEquals(before,after,"HUD must not cache the previous sneak binding");
            assertTrue(after.contains(keys[5].getTranslatedKeyMessage().getString()),"HUD uses the current native KeyMapping label");
        });
    }
    @Test void onlyUnknownBindingsAreShownAsUnbound()throws Exception{
        language("en_us",()->{
            var keys=remapped();keys[7].setKey(InputConstants.UNKNOWN);
            String label=net.minecraft.network.chat.Component.translatable("tutorial.blacklight.unbound").getString();
            String text=VehicleHud.controlHints(keys).getString();
            assertEquals(1,text.split(java.util.regex.Pattern.quote(label),-1).length-1,"only ammo is unbound");
            assertTrue(text.contains(keys[6].getTranslatedKeyMessage().getString()),"seat remains F23");
        });
    }
    @Test void chineseControlsKeepTheirMeaningAndUseTheSameLiveKeyMappings()throws Exception{
        language("zh_cn",()->{
            var keys=remapped();String text=VehicleHud.controlHints(keys).getString();
            assertTrue(text.contains("驾驶")&&text.contains("刹车")&&text.contains("下车")&&text.contains("换座")&&text.contains("选弹"));
            assertTrue(text.contains(keys[5].getTranslatedKeyMessage().getString()),"Chinese HUD must use current native sneak binding");
            assertFalse(text.contains("Shift"));assertFalse(text.contains("请在按键设置中绑定"),"already bound keys should be displayed");
        });
    }
}
