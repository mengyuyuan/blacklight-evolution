package com.codex.blacklight.resource;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForgeNativeDataCompatibilityTest {
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @Test void bothCitiesUseReal1201BlocksAndLegalProperties() throws Exception {
        for(String city:List.of("futian","los_angeles")){
            var file=Path.of("src/main/resources/data/blacklight/city",city,"map.json");
            try(var reader=Files.newBufferedReader(file)){
                var palette=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("palette");
                assertFalse(palette.isEmpty());
                for(var entry:palette){
                    var node=entry.getAsJsonObject();var id=new ResourceLocation(node.get("Name").getAsString());
                    assertTrue(BuiltInRegistries.BLOCK.containsKey(id),city+" cannot load missing block "+id);
                    var block=BuiltInRegistries.BLOCK.get(id);
                    if(node.has("Properties"))for(var value:node.getAsJsonObject("Properties").entrySet()){
                        var property=block.getStateDefinition().getProperty(value.getKey());assertNotNull(property,city+" "+id+"/"+value.getKey());
                        assertTrue(property.getValue(value.getValue().getAsString()).isPresent(),city+" "+id+" invalid "+value);
                    }
                }
            }
        }
    }
    @Test void vanillaReferencesCannotInvalidateWholeForgeTags() throws Exception {
        for(String kind:List.of("blocks","items","entity_types")){
            Registry<?> registry=switch(kind){case "blocks"->BuiltInRegistries.BLOCK;case "items"->BuiltInRegistries.ITEM;default->BuiltInRegistries.ENTITY_TYPE;};
            for(String namespace:List.of("blacklight","minecraft")){
                var root=Path.of("src/main/resources/data",namespace,"tags",kind);if(!Files.isDirectory(root))continue;
                try(var files=Files.walk(root)){
                    for(var file:files.filter(p->p.toString().endsWith(".json")).toList())try(var reader=Files.newBufferedReader(file)){
                        for(var value:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("values")){
                            String text=value.isJsonPrimitive()?value.getAsString():value.getAsJsonObject().get("id").getAsString();
                            if(text.startsWith("#"))continue;var id=new ResourceLocation(text);
                            boolean required=value.isJsonPrimitive()||!value.getAsJsonObject().has("required")||value.getAsJsonObject().get("required").getAsBoolean();
                            if(required&&id.getNamespace().equals("minecraft"))assertTrue(registry.containsKey(id),file+" required missing "+id);
                        }
                    }
                }
            }
        }
    }
}
