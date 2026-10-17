package com.codex.blacklight.animation;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Retargeted Rokoko capture, sampled independently of the Minecraft renderer. */
public final class FlightAnimationClips {
    private record Clip(float duration, boolean loop, List<BlacklightPose> frames) {}
    private static final Map<String,Clip> CLIPS=load();
    private static Map<String,Clip> load() {
        try(var stream=FlightAnimationClips.class.getResourceAsStream("/assets/blacklight/animation/sovereign_flight.json")) {
            if(stream==null)throw new IllegalStateException("Missing flight animation clips");
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            var result=new LinkedHashMap<String,Clip>();
            for(var entry:json.getAsJsonObject("clips").entrySet()) {
                var clip=entry.getValue().getAsJsonObject();var frames=new ArrayList<BlacklightPose>();
                for(var value:clip.getAsJsonArray("frames")) {
                    var pose=new BlacklightPose();
                    for(var bone:value.getAsJsonObject().entrySet()) {
                        var t=bone.getValue().getAsJsonObject();var p=t.getAsJsonArray("p");var q=t.getAsJsonArray("q");
                        var translation=new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat());
                        var rotation=new Quaternionf(q.get(0).getAsFloat(),q.get(1).getAsFloat(),q.get(2).getAsFloat(),q.get(3).getAsFloat());
                        if(!translation.isFinite()||!rotation.isFinite()||rotation.lengthSquared()<.99F)throw new IllegalStateException("Invalid flight transform");
                        pose.getJointTransformData().put(bone.getKey(),new BoneTransform(translation,rotation.normalize(),new Vector3f(1)));
                    }
                    frames.add(pose);
                }
                float duration=clip.get("duration").getAsFloat();
                if(!Float.isFinite(duration)||duration<=0||frames.size()<2)throw new IllegalStateException("Invalid flight duration");
                result.put(entry.getKey(),new Clip(duration,clip.get("loop").getAsBoolean(),List.copyOf(frames)));
            }
            return Map.copyOf(result);
        }catch(java.io.IOException ex){throw new IllegalStateException("Cannot load flight capture",ex);}
    }
    public static Set<String> names(){return CLIPS.keySet();}
    public static float duration(String name){return CLIPS.get(name).duration;}
    public static BlacklightPose sample(String name,double seconds) {
        var c=CLIPS.get(name);if(c==null)throw new IllegalArgumentException("Unknown flight clip: "+name);
        if(!Double.isFinite(seconds))throw new IllegalArgumentException("Non-finite flight time");
        double t=Math.max(0,seconds);t=c.loop?t%c.duration:Math.min(t,c.duration);
        double index=t/c.duration*(c.frames.size()-1);int left=Math.min((int)index,c.frames.size()-1),right=Math.min(left+1,c.frames.size()-1);
        var pose=BlacklightPose.interpolatePose(c.frames.get(left),c.frames.get(right),(float)(index-left));
        if(c.loop) {
            // Keep captured follow-through, damping only whole-actor wandering.
            // The fixed wing mount follows this chest; it does not require a frozen body.
            float pitch=switch(name){case "cruise"->-76;case "boost"->-82;default->6;};
            var captured=new Quaternionf(pose.orElseEmpty("Root").rotation());
            pose.orElseEmpty("Root").rotation().rotationX((float)Math.toRadians(pitch)).slerp(captured,.35F);
            if(name.equals("hover")) {
                // The source's nearly straight hanging legs read as standing in
                // a blocky silhouette. Keep the capture, but establish a clear
                // suspended pose before adding the small wingbeat response.
                for(String side:new String[]{"R","L"}) {
                    pose.orElseEmpty("Thigh_"+side).rotation().rotateX((float)Math.toRadians(side.equals("R")?12:7));
                    pose.orElseEmpty("Leg_"+side).rotation().rotateX((float)Math.toRadians(-18));
                    pose.orElseEmpty("Arm_"+side).rotation().rotateZ((float)Math.toRadians(side.equals("R")?-8:8));
                }
            }
        }
        // A cuboid head cannot use the capture's neck yaw/roll or pivot through
        // the shoulders. Retain forward sight during prone flight, rotating
        // around the rear neck edge so the lower hat surface clears the roots.
        var head=pose.orElseEmpty("Head");
        float pitch=head.rotation().getEulerAnglesXYZ(new Vector3f()).x;
        head.rotation().rotationX(com.codex.blacklight.forge.WingMath.clamp(pitch,0,.85F));
        var neckEdge=new Vector3f(0,0,1.5F/16);
        head.translation().set(neckEdge).sub(new Vector3f(neckEdge).rotate(head.rotation()));
        return pose;
    }
    private FlightAnimationClips(){}
}
