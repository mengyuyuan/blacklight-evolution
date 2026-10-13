package com.codex.blacklight.animation;

import com.codex.blacklight.combat.MotionResources;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** All animation data is resolved from Blacklight resources; external mod namespaces are never followed. */
public final class MotionLibrary {
    private record Definition(MatrixMotionClip clip, float transition, float nominalSpeed) {}
    private static final Map<String, Optional<Definition>> CLIPS = new ConcurrentHashMap<>();
    private MotionLibrary() {}

    public static String resolve(PlayerAction action) {
        return switch (action) {
            case IDLE, CLAW_IDLE -> "basic_idle";
            case CLAW_WALK -> "basic_walk";
            case CLAW_SPRINT -> "basic_sprint";
            case CHARGED_JUMP -> "basic_jump_charge";
            case JUMP_RISE -> "basic_jump_rise";
            case JUMP_FALL -> "basic_fall";
            case WALL_RUN -> "basic_wall_run_up";
            case WALL_CLIMB -> "basic_wall_climb_up";
            case WALL_CLING -> "basic_wall_cling";
            case WALL_LEAP -> "basic_wall_leap";
            case WALL_MANTLE -> "basic_wall_mantle";
            case WALL_VAULT -> "basic_wall_vault";
            case WALL_TOP -> "basic_wall_top";
            case PARKOUR_SMALL -> "basic_parkour_small";
            case GLIDE -> "basic_glide_forward";
            case CLAW_AUTO_1 -> "legacy_claw_auto1";
            case CLAW_AUTO_2 -> "legacy_claw_auto2";
            case CLAW_AUTO_3 -> "legacy_claw_auto3";
            case CLAW_GROUNDSPIKE -> "legacy_claw_groundspike";
            case CLAW_SPRINT_ATTACK -> "legacy_claw_sprint_attack";
            case CLAW_DASHING_SLICE -> "legacy_claw_dashing_slice";
            case CONSUME -> "consume_claws_actor";
            case CONSUME_NORMAL -> "consume_normal_actor";
            case CONSUME_HAMMER -> "consume_hammer_actor";
            case CONSUME_BLADE -> "consume_blade_actor";
            case CONSUME_WHIP -> "consume_whip_actor";
            case CONSUME_MUSCLE -> "consume_muscle_actor";
            case CONSUME_LARGE -> "consume_large_actor";
            case CONSUME_LARGE_HAMMER -> "consume_large_hammer_actor";
            case CONSUME_LARGE_MUSCLE -> "consume_large_muscle_actor";
            case CONSUME_LARGE_WHIP -> "consume_large_whip_actor";
            case AIR_DASH -> "basic_air_dash";
            case AIR_DASH_BRAKE -> "basic_air_dash_brake";
            case LAND_MOVING -> "basic_land_moving";
            case LAND_LIGHT -> "basic_land_light";
            case LAND_HEAVY -> "basic_land_heavy";
            case LAND_DEVASTATING -> "basic_land_devastating";
            default -> action.name().toLowerCase(java.util.Locale.ROOT);
        };
    }
    public static String canonical(String id) {
        if (id == null) throw new IllegalArgumentException("Missing motion key");
        if (id.equals("groundspike") || id.equals("ground_spike")) return "legacy_claw_groundspike";
        if (id.equals("charged_jump")) return "basic_jump_charge";
        if (!id.matches("[a-z0-9_]{1,80}")) throw new IllegalArgumentException("Invalid Blacklight motion key: " + id);
        return id;
    }
    public static Optional<MatrixMotionClip> find(String id) { return definition(id).map(Definition::clip); }
    public static MatrixMotionClip clip(String id) { return find(id).orElseThrow(() -> new IllegalStateException("Missing Blacklight motion: " + id)); }
    public static float duration(String id) { return clip(id).duration(); }
    public static float transition(String id) { return definition(id).orElseThrow().transition(); }
    /** Blocks per game tick at the source clip's authored forward/tangent stride. */
    public static float nominalSpeed(String id) { return definition(id).orElseThrow().nominalSpeed(); }
    public static BlacklightPose pose(String id, float seconds, boolean loop) {
        return BlacklightSkeleton.get().localPose(clip(id), seconds, loop);
    }
    public static boolean looping(String id) {
        String key = canonical(id);
        return key.startsWith("basic_idle") || key.equals("basic_walk") || key.equals("basic_run")
                || key.startsWith("basic_sprint") && !key.endsWith("brake")
                || key.equals("basic_fall") || key.startsWith("basic_wall_run")
                || key.startsWith("basic_wall_climb") || key.equals("basic_wall_cling")
                || key.startsWith("basic_glide") || key.endsWith("grab_hold") || key.equals("grab_blade_hold");
    }
    private static Optional<Definition> definition(String id) { return CLIPS.computeIfAbsent(canonical(id), MotionLibrary::read); }
    private static Optional<Definition> read(String id) {
        Optional<JsonObject> data = id.startsWith("legacy_")
                ?resource("/assets/blacklight/animmodels/animations/biped/combat/"+id.substring(7)+".json")
                :MotionResources.read(id+".json",8*1024*1024);
        if (data.isEmpty()&&!id.startsWith("legacy_")) data = resource("/assets/blacklight/authored_motions/" + id + ".json");
        if (data.isEmpty()) data = resource("/assets/blacklight/animmodels/animations/biped/combat/" + id + ".json");
        if (data.isEmpty()) data = resource("/assets/blacklight/animmodels/animations/biped/action/" + id + ".json");
        return data.map(json -> {
            float transition = json.has("blacklight") ? json.getAsJsonObject("blacklight").get("transitionSeconds").getAsFloat() : .06F;
            var matrix = json.has("format") && "ATTRIBUTES".equalsIgnoreCase(json.get("format").getAsString()) ? attributes(json) : json;
            var clip=MatrixMotionClip.parse(matrix);
            float nominal=0;
            if(clip.joints().contains("Coord")&&clip.duration()>0) {
                var start=clip.sample("Coord",0,false).getTranslation(new Vector3f());
                var finish=clip.sample("Coord",clip.duration(),false).getTranslation(new Vector3f());
                nominal=start.distance(finish)*.9375F/(clip.duration()*20);
            }
            return new Definition(clip,transition,nominal);
        });
    }
    private static Optional<JsonObject> resource(String path) {
        try (var input = MotionLibrary.class.getResourceAsStream(path)) {
            if (input == null) return Optional.empty();
            return Optional.of(JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read " + path, e); }
    }
    private static JsonObject attributes(JsonObject source) {
        var result = source.deepCopy(); result.addProperty("format", "MATRIX");
        var skeleton = BlacklightSkeleton.get();
        for (var item : result.getAsJsonArray("animation")) {
            var track = item.getAsJsonObject(); String name = track.get("name").getAsString();
            var frames = new JsonArray();
            for (var frame : track.getAsJsonArray("transform")) {
                var value = frame.getAsJsonObject(); var loc = value.getAsJsonArray("loc");
                var rot = value.getAsJsonArray("rot"); var sca = value.getAsJsonArray("sca");
                var delta = new Matrix4f().translationRotateScale(
                        new Vector3f(loc.get(0).getAsFloat(), loc.get(1).getAsFloat(), loc.get(2).getAsFloat()),
                        new Quaternionf(rot.get(1).getAsFloat(), rot.get(2).getAsFloat(), rot.get(3).getAsFloat(), rot.get(0).getAsFloat()).normalize(),
                        new Vector3f(sca.get(0).getAsFloat(), sca.get(1).getAsFloat(), sca.get(2).getAsFloat()));
                var bone = skeleton.searchJointByName(name);
                var matrix = bone == null ? delta : bone.getLocalTransform().mul(delta);
                if (name.equals("Root")) matrix = new Matrix4f().rotateX((float)Math.PI / 2).mul(matrix);
                var values = new JsonArray();
                for (float number : matrix.transpose().get(new float[16])) values.add(number);
                frames.add(values);
            }
            track.add("transform", frames);
        }
        return result;
    }
    /** Validate required independent runtime assets at setup rather than silently substituting missing actions. */
    public static void requireInstalled() {
        boolean extension = skillExtensionInstalled();
        for (var action : PlayerAction.values()) clip(resolve(action));
        for (var move : com.codex.blacklight.combat.WeaponMove.values()) {
            if (!extension && skillMotion(move.id)) continue;
            clip(move.id);
            com.codex.blacklight.combat.LocalWeaponMotion.find(move.id)
                    .flatMap(com.codex.blacklight.combat.LocalWeaponMotion.Definition::charge)
                    .ifPresent(charge -> clip(charge.animation()));
        }
        for (String key : new String[]{"utility_armor_transform", "utility_armor_idle", "utility_shield_deploy", "utility_shield_hit", "basic_wall_top", "basic_parkour_small"}) clip(key);
        if (extension) for (String key : new String[]{"dev_critical_pain_aim_up","dev_critical_pain_aim_down","dev_air_critical_pain_aim_up","dev_air_critical_pain_aim_down"}) clip(key);
        else com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Base motion profile installed; formal Devastators and Bulletdive require the complete skill extension");
    }
    private static boolean skillMotion(String id) { return id.startsWith("dev_") || id.equals("bulletdive_drop"); }
    /** Absence is a supported baseline; a partially supplied extension is an installation error. */
    public static boolean skillExtensionInstalled() {
        String[] keys={"dev_groundspike","dev_air_groundspike","dev_tendril","dev_air_tendril","dev_critical_pain","dev_air_critical_pain","bulletdive_drop"};
        boolean any=false;
        for (String key:keys) any |= find(key).isPresent();
        if (!any) return false;
        for (String key:keys) { clip(key); clip(key+"_charge"); }
        return true;
    }
}
