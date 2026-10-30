package com.codex.blacklight.resource;

import com.codex.blacklight.client.screen.SkillTutorial;
import com.codex.blacklight.power.*;
import com.google.gson.*;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillTutorialResourceTest {
    @Test void everyPurchasableSkillHasTranslatedInstructionsAndAnAnimatedDemo()throws Exception{
        Set<String> ids=new LinkedHashSet<>(List.of("mode","grab","target_lock","disguise","groundspike","claws"));
        for(var u:MovementUpgrade.values())ids.add(u.id());
        for(var u:ClawUpgrade.values())ids.add(u.id());
        for(var u:CombatSkill.values())ids.add(u.id());
        for(var u:GrowthUpgrade.values())ids.add(u.id);
        for(var u:PowerForm.values())if(u!=PowerForm.NONE)ids.add(u.id());
        var zh=json("lang/zh_cn.json");var en=json("lang/en_us.json");var manifest=json("tutorials/manifest.json");
        Set<String> checked=new HashSet<>();
        for(String id:ids){
            var tutorial=SkillTutorial.forSkill(id);
            for(var language:List.of(zh,en)){
                assertTrue(language.has("upgrade.blacklight."+id),id);
                for(String field:List.of("controls","steps")){
                    String key="tutorial.blacklight."+tutorial.instructionFamily()+"."+field;
                    assertTrue(language.has(key),id+": "+key);
                    var matcher=java.util.regex.Pattern.compile("%(\\d+)\\$s").matcher(language.get(key).getAsString());
                    while(matcher.find())assertTrue(Integer.parseInt(matcher.group(1))<=tutorial.bindings().length,"Unresolved binding in "+id);
                }
            }
            assertTrue(manifest.has(tutorial.family()),id);
            if(!checked.add(tutorial.family()))continue;
            try(var stream=resource("tutorials/"+tutorial.family()+".gif");var input=ImageIO.createImageInputStream(stream)){
                var reader=ImageIO.getImageReadersByFormatName("gif").next();
                try{reader.setInput(input);int n=reader.getNumImages(true);
                    assertTrue(n>1&&n<=180);assertEquals(320,reader.getWidth(0));assertEquals(180,reader.getHeight(0));
                    assertEquals(manifest.getAsJsonObject(tutorial.family()).get("frames").getAsInt(),n);
                    assertTrue(reader.read(n-1).getWidth()>0);
                }finally{reader.dispose();}
            }
        }
    }
    @Test void gameplayMessagesDoNotTeachHardCodedButtons()throws Exception{
        for(String language:List.of("zh_cn","en_us")){
            var data=json("lang/"+language+".json");
            for(String id:List.of("infected","prototype_mode_required","survival_locked","consume_no_target","grab_held","grab_mounted","gene_unlocked","identity_captured","disguise_enabled","awakening_guide")){
                String message=data.get("message.blacklight."+id).getAsString();
                assertFalse(message.matches(".*(按 [BEZU]|右键|左键|[Pp]ress [BEZU]|[Rr]ight.click|[Ll]eft.click).*"),id);
            }
        }
    }
    private static InputStream resource(String path){var in=SkillTutorialResourceTest.class.getResourceAsStream("/assets/blacklight/"+path);assertNotNull(in,path);return in;}
    private static JsonObject json(String path)throws Exception{try(var in=new InputStreamReader(resource(path),StandardCharsets.UTF_8)){return JsonParser.parseReader(in).getAsJsonObject();}}
}
