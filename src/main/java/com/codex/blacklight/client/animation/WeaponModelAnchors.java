package com.codex.blacklight.client.animation;

import com.codex.blacklight.power.PowerForm;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Vector3f;

/** Contact IK reads the same exported landmarks as the visible weapon mesh. */
public final class WeaponModelAnchors {
    private static final Map<PowerForm,Map<String,Vector3f>> CACHE=new EnumMap<>(PowerForm.class);
    private WeaponModelAnchors(){}
    public static Vector3f get(PowerForm form,String name,Vector3f fallback){
        if(!WeaponMorphTiming.weapon(form))return new Vector3f(fallback);
        var anchors=CACHE.computeIfAbsent(form,f->{
            Map<String,Vector3f> result=new HashMap<>();
            String file=f==PowerForm.CLAWS?"claw_blender":"form_"+f.id();
            try(var in=WeaponModelAnchors.class.getResourceAsStream("/assets/blacklight/models/"+file+".json")){
                if(in==null)return result;
                var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
                if(root.has("anchors"))for(var entry:root.getAsJsonObject("anchors").entrySet()){
                    var p=entry.getValue().getAsJsonArray();result.put(entry.getKey(),new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()));
                }
                return result;
            }catch(java.io.IOException error){throw new IllegalStateException("Cannot load weapon contacts: "+file,error);}
        });
        return new Vector3f(anchors.getOrDefault(name,fallback));
    }
}
