package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import java.util.Map;
import java.util.HashMap;

/** Receiver effects have their own event clock; an actor clip ending does not finish absorption. */
public final class ConsumeReceiverScene {
    public record Profile(String style, float deformSeconds, java.util.List<Float> hitSeconds,
                          float effectSeconds, float finishSeconds, float settleSeconds) {
        public boolean impactAt(int tick) { return hitSeconds.stream().anyMatch(t -> Math.round(t*20)==tick); }
    }
    private static Map<String, Profile> profiles;
    private ConsumeReceiverScene() {}

    public static synchronized Profile profile(PowerForm form, boolean large) {
        if (large) return null;
        if (profiles == null) {
            var loaded = new HashMap<String, Profile>();
            MotionResources.read("consume-receiver-events.data", 16*1024).ifPresent(root -> {
                if (root.get("schema").getAsInt()!=1) throw new IllegalArgumentException("Invalid receiver event schema");
                for (var entry : root.getAsJsonObject("profiles").entrySet()) {
                    var row=entry.getValue().getAsJsonObject();
                    String style=row.get("style").getAsString();
                    float effect=row.get("effectSeconds").getAsFloat(),finish=row.get("finishSeconds").getAsFloat(),settle=row.get("settleSeconds").getAsFloat();
                    float deform=row.get("deformSeconds").getAsFloat();
                    var hits=new java.util.ArrayList<Float>();
                    for(var item:row.getAsJsonArray("hitSeconds")){
                        float time=item.getAsFloat();
                        if(!Float.isFinite(time)||time<0||time>=finish)throw new IllegalArgumentException("Invalid receiver impact");
                        hits.add(time);
                    }
                    if (!java.util.Set.of("SEVER","HEAD_CRUSH","HEAD_IMPACT","LASH","ARM_RIP","CHEST_ABSORB").contains(style)
                            || !Float.isFinite(effect)||!Float.isFinite(finish)||!Float.isFinite(settle)
                            || !Float.isFinite(deform)||deform<0||deform>=finish
                            || effect<0||finish<=effect||finish>20||settle<=0||settle>=finish-Math.max(effect,deform))
                        throw new IllegalArgumentException("Invalid receiver events: "+entry.getKey());
                    loaded.put(entry.getKey(), new Profile(style,deform,java.util.List.copyOf(hits),effect,finish,settle));
                }
            });
            profiles=Map.copyOf(loaded);
        }
        return profiles.get(ConsumeSequence.key(form));
    }

    public static float clipSeconds(PowerForm form) {
        return LocalVisualMotion.find("consume_"+ConsumeSequence.key(form)+"_actor")
                .map(clip -> {
                    var times=clip.animation().getAsJsonArray("animation").get(0).getAsJsonObject().getAsJsonArray("time");
                    return Math.max(.05F,times.get(times.size()-1).getAsFloat());
                }).orElse(1F);
    }
}
