package com.codex.blacklight.city;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Selects the packaged data representation; a missing archive is never an implicit fallback. */
final class CityGenerationProfile {
    private static final boolean ARCHIVE=load();

    static boolean usesArchive(){return ARCHIVE;}

    private static boolean load(){
        try(var input=CityGenerationProfile.class.getResourceAsStream("/data/blacklight/city/generation.json")){
            // Builds predating the profile descriptor used the complete municipal archive.
            if(input==null)return true;
            var json=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            if(json.get("schema").getAsInt()!=1)throw new IOException("Unsupported city generation profile");
            return switch(json.get("mode").getAsString()){
                case "geographic"->false;
                case "archive"->true;
                default->throw new IOException("Unknown city generation profile");
            };
        }catch(IOException|RuntimeException exception){throw new IllegalStateException("Cannot read city generation profile",exception);}
    }
    private CityGenerationProfile(){}
}
