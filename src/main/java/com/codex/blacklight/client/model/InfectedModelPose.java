package com.codex.blacklight.client.model;

import com.codex.blacklight.client.animation.ConsumeVictimPose;
import com.codex.blacklight.client.renderer.ConsumeAnatomy;
import com.codex.blacklight.combat.ConsumePlacementTrack;
import com.codex.blacklight.combat.ConsumeReaction;
import com.codex.blacklight.combat.ConsumeSequence;
import com.codex.blacklight.combat.GrappleMotion;
import com.codex.blacklight.combat.HitReaction;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/** Socket-local poses for the authored infected meshes. No pose state survives an entity render. */
public final class InfectedModelPose {
    private static final float PI = (float)Math.PI;
    private InfectedModelPose() {}

    /** The feeding clock is server-authoritative and shared with the lifted receiver. */
    public static boolean devourerFeeding(com.codex.blacklight.entity.BlacklightInfectedHost host,
            String bone,float partial,PoseStack stack) {
        if(host.devourerIdentity()&&!host.giantBoss()) {
            float age=host.tickCount+partial;
            float pulse=(float)Math.sin(age*.09F);
            float fed=host.feedingStarted()<0?0:(float)Math.sin(Math.PI*ease((host.level().getGameTime()-host.feedingStarted()+partial-96)/40F));
            if(bone.equals("chest"))rotate(stack,.055F+pulse*.018F-fed*.025F,0,0);
            else if(bone.equals("head"))rotate(stack,.18F+pulse*.012F-fed*.06F,0,0);
            else if(bone.startsWith("upper_arm_"))rotate(stack,.08F,0,side(bone)*.035F);
            else if(bone.startsWith("forearm_"))rotate(stack,.12F,0,0);
            return true;
        }
        if(host.feedingStarted()<0)return false;
        float age=host.level().getGameTime()-host.feedingStarted()+partial;
        if(age<0||age>=96||!host.speciesId().equals("minecraft:iron_golem"))return false;
        float reach=ease(age/18),lift=ease((age-18)/36),close=ease((age-52)/24);
        float release=1-ease((age-80)/16),side=side(bone);
        float squeeze=age>=54&&age<78?sin((age-54)*1.15F)*.035F:0;
        if(bone.equals("chest"))rotate(stack,(-.1F*reach+.075F*close)*release,0,0);
        else if(bone.equals("head"))rotate(stack,(.22F*reach-.30F*lift)*release,0,0);
        else if(bone.startsWith("upper_arm_"))rotate(stack,(.72F*reach+.24F*lift)*release,
                -side*.12F*close*release,-side*(.24F*reach+.10F*close+squeeze)*release);
        else if(bone.startsWith("forearm_"))rotate(stack,(.20F*reach+.40F*lift+.13F*close)*release,0,0);
        else if(bone.startsWith("hand_"))rotate(stack,(-.12F*lift+.08F*close)*release,side*.10F*close*release,0);
        else if(bone.equals("pelvis"))stack.translate(0,-.35F*reach*release,0);
        else if(bone.startsWith("thigh_"))rotate(stack,.025F*reach*release,0,side*.018F*reach*release);
        else if(bone.startsWith("shin_"))rotate(stack,-.045F*reach*release,0,0);
        // All remaining bones keep their authored local pose while this paired scene owns it.
        return true;
    }
    private static float ease(float value){float t=unit(value);return t*t*(3-2*t);}
    /** Authored giant bodies use the same network action clock as their native proxies. */
    public static void nativeAction(String action,String bone,float age,PoseStack stack){
        if(age<0||action.isEmpty())return;
        float load=action.equals("SONIC")?ease(age/25)*(1-ease((age-32)/18)):ease(age/16)*(1-ease((age-20)/15));
        if(action.equals("SONIC")){
            if(bone.equals("chest"))rotate(stack,-.25F*load,0,0);
            else if(bone.startsWith("upper_arm_"))rotate(stack,-.4F*load,0,side(bone)*.28F*load);
            else if(bone.equals("head"))rotate(stack,-.12F*load,0,0);
        }else if(action.equals("WITHER")||action.equals("DRAGON")){
            if(bone.equals("chest"))rotate(stack,.10F*load,0,0);
            else if(bone.startsWith("jaw"))rotate(stack,-.5F*load,0,0);
            else if(bone.startsWith("head")||bone.startsWith("neck"))rotate(stack,-.08F*load,0,0);
        }
    }

    /** Called between translate(pivot) and translate(-pivot). Front is -Z; Y is up.
     * age is ticks, phase is radians, speed/attack are 0..1, look angles are degrees.
     * A parent bone already moves its children, so articulated children only add local bend. */
    public static void transform(String species, String bone, float age, float phase, float speed,
            float attack, float headYaw, float headPitch, boolean flying, PoseStack stack) {
        species = path(species);
        speed = unit(speed); attack = unit(attack);
        float strike = sin(PI * (float)Math.sqrt(attack));
        float windup = sin(PI * attack);
        float idle = sin(age * .075F);
        float side = side(bone);
        if (boss(species)) {
            bossPose(species, bone, age, phase, speed, strike, windup, headYaw, headPitch, flying, stack);
            return;
        }
        if (bone.equals("root")) return;
        if (bone.equals("head")) {
            // These groups are facial surface details, not independent skulls.
            if (faceOnly(species)) return;
            if (species.equals("shulker")) stack.translate(0, -.5F * (1 - strike), 0);
            float look = switch(species) {
                case "ghast", "slime", "magma_cube", "blaze", "breeze" -> 0;
                case "cod", "salmon", "tropical_fish", "tadpole" -> .22F;
                default -> .7F;
            };
            look(stack, headYaw, headPitch, look);
            rotate(stack, (quadruped(species) ? -.38F : -.16F) * strike, 0, 0);
            return;
        }
        if (bone.equals("body")) {
            float lean = humanoid(species) ? -.12F : -.08F;
            rotate(stack, lean * strike, humanoid(species) ? .16F * windup : 0,
                    (aquatic(species) ? .018F : .008F) * idle);
            if (species.equals("slime") || species.equals("magma_cube")) {
                float squash = sin(phase * 2) * speed * .065F + strike * .09F;
                stack.scale(1 + squash * .5F, 1 - squash, 1 + squash * .5F);
            }
            return;
        }
        if (bone.startsWith("wing_")) {
            float rate = switch(species) {case "bee" -> 2.1F; case "bat" -> .85F; case "phantom" -> .23F; default -> .55F;};
            float amplitude = flying ? (species.equals("bee") ? .62F : .5F) : .045F;
            float flap = sin(age * rate) * amplitude;
            // The bat/fairy membranes were authored vertically; these flap around Y.
            if (species.equals("bat") || species.equals("allay") || species.equals("vex"))
                rotate(stack, 0, side * (flap + .08F), 0);
            else rotate(stack, 0, 0, side * (flap + .08F * strike));
            return;
        }
        if (bone.startsWith("fin_")) {
            rotate(stack, .12F * sin(age * .35F), side * (.12F + sin(age * .27F) * .15F), 0);
            return;
        }
        if (bone.equals("tail")) {
            float wave = sin(age * (.18F + speed * .28F)) * (.09F + speed * .28F);
            if (species.equals("dolphin")) rotate(stack, wave, 0, 0);
            else rotate(stack, 0, wave, 0);
            return;
        }
        if (bone.equals("dorsal")) { rotate(stack, 0, 0, sin(age * .28F) * .055F); return; }
        if (bone.equals("neck")) { rotate(stack, -.16F * strike + sin(phase) * speed * .035F, 0, 0); return; }
        if (bone.startsWith("tentacle_") || (squid(species) && bone.startsWith("arm_"))) {
            int index = lastNumber(bone);
            float radial = index * (squid(species) ? PI / 4 : PI * 2 / 9);
            float curl = sin(age * (.11F + speed * .12F) + index * .58F) * (.13F + speed * .16F) + strike * .22F;
            rotate(stack, cos(radial) * curl, 0, sin(radial) * curl);
            return;
        }
        if (bone.startsWith("rod_")) {
            String[] fields = bone.split("_");
            int row = Integer.parseInt(fields[1]), col = Integer.parseInt(fields[2]);
            rotate(stack, sin(age * .14F + col) * .12F, 0, cos(age * .14F + col) * .12F);
            stack.translate(0, sin(age * .1F + col + row) * .025F, 0);
            return;
        }
        if (bone.startsWith("wind_")) { rotate(stack, 0, age * (.055F + lastNumber(bone) * .018F), 0); return; }
        if (bone.startsWith("layer_")) {
            stack.translate(0, Math.max(0, sin(phase)) * speed * .025F * lastNumber(bone), 0);
            return;
        }
        if (bone.startsWith("shell_")) {
            if (bone.equals("shell_upper")) {
                // The authored shell is fully open; close it around the lowered core at rest.
                stack.translate(0, -.625F * (1 - strike), 0);
                rotate(stack, 0, windup * .2F, 0);
            }
            return;
        }
        if (bone.startsWith("segment_")) {
            int index = lastNumber(bone);
            rotate(stack, 0, sin(phase * 1.6F - index * .7F) * speed * .16F, 0);
            return;
        }
        if (bone.equals("abdomen")) { rotate(stack, 0, sin(phase) * speed * .035F, 0); return; }
        if (arthropod(species) && bone.startsWith("leg_")) {
            int index = lastNumber(bone);
            float cycle = phase * 1.5F + index * PI * .72F + (side > 0 ? PI : 0);
            rotate(stack, 0, sin(cycle) * speed * .32F, side * Math.max(0, cos(cycle)) * speed * .18F);
            return;
        }
        if (bone.startsWith("paddle_")) {
            rotate(stack, 0, side * sin(phase) * speed * .12F, 0);
            return;
        }
        if (bone.startsWith("front_") || bone.startsWith("hind_") || species.equals("creeper")) {
            boolean front = bone.startsWith("front_") || species.equals("creeper") && bone.endsWith("_-4");
            boolean hop = species.equals("rabbit") || species.equals("frog");
            float cycle = phase + ((side > 0) ^ front ? PI : 0);
            float stride = hop ? sin(phase) * (front ? -.8F : 1.0F) : cos(cycle) * .68F;
            float pitch = speed * stride + (front ? .28F : -.08F) * strike;
            if (flying && (species.equals("turtle") || species.equals("axolotl") || species.equals("frog")))
                rotate(stack, pitch * .45F, side * sin(phase) * speed * .4F, 0);
            else rotate(stack, pitch, 0, 0);
            return;
        }
        if (bone.equals("arms")) { rotate(stack, strike * .82F, -.12F * windup, 0); return; }
        if (bone.startsWith("arm_")) {
            float armWalk = cos(phase + (side < 0 ? PI : 0)) * speed * .48F;
            float reach = side < 0 ? 1.35F * strike : .55F * windup;
            if (species.equals("snow_golem")) reach *= .5F;
            rotate(stack, armWalk * (1 - windup * .65F) + reach, -side * windup * .18F,
                    side * (.025F * idle + .16F * windup));
            return;
        }
        if (bone.startsWith("leg_")) {
            float walk = cos(phase + (side > 0 ? PI : 0)) * speed * .72F;
            rotate(stack, walk, 0, 0);
        }
    }

    private static void bossPose(String species, String bone, float age, float phase, float speed,
            float strike, float windup, float yaw, float pitch, boolean flying, PoseStack stack) {
        float side = side(bone);
        float breath = sin(age * .035F);
        if (bone.equals("root")) return;
        if (species.equals("warden") || species.equals("iron_golem")) {
            if (bone.equals("chest")) rotate(stack, -.018F * breath - .085F * strike, .035F * windup, 0);
            else if (bone.equals("head")) look(stack, yaw, pitch, .35F);
            else if (bone.startsWith("upper_arm_"))
                rotate(stack, cos(phase + (side < 0 ? PI : 0)) * speed * .18F + strike * (side < 0 ? .7F : .42F), 0, side * windup * .04F);
            else if (bone.startsWith("forearm_")) rotate(stack, .08F * windup, 0, 0);
            else if (bone.startsWith("hand_")) rotate(stack, -.05F * windup, 0, 0);
            else if (bone.startsWith("thigh_")) rotate(stack, cos(phase + (side > 0 ? PI : 0)) * speed * .20F, 0, 0);
            else if (bone.startsWith("shin_")) rotate(stack, -Math.max(0, sin(phase + (side > 0 ? PI : 0))) * speed * .15F, 0, 0);
            else if (bone.startsWith("foot_")) rotate(stack, Math.max(0, sin(phase + (side > 0 ? PI : 0))) * speed * .08F, 0, 0);
            else if (bone.startsWith("sensor_")) rotate(stack, .015F * breath, side * .02F * sin(age * .07F), 0);
            return;
        }
        if (species.equals("wither")) {
            if (bone.equals("chest")) rotate(stack, breath * .012F - strike * .045F, 0, 0);
            else if (bone.equals("head")) look(stack, yaw, pitch, .6F);
            else if (bone.startsWith("head_")) {
                look(stack, yaw, pitch, .18F);
                rotate(stack, 0, side * .04F * sin(age * .047F + side), 0);
            } else if (bone.startsWith("jaw")) rotate(stack, -.12F * strike, 0, 0);
            else if (bone.startsWith("neck_")) rotate(stack, 0, side * .012F * breath, 0);
            else if (bone.startsWith("rib_cage_")) rotate(stack, 0, side * .012F * breath, side * .016F * windup);
            else if (bone.equals("spine")) rotate(stack, .024F * breath, 0, 0);
            else if (bone.startsWith("tendril_")) rotate(stack, .06F * sin(age * .055F + side), side * .025F * breath, 0);
            return;
        }
        if (bone.equals("chest")) rotate(stack, -.016F * breath - .035F * strike, 0, 0);
        else if (bone.equals("abdomen")) rotate(stack, .012F * breath, 0, 0);
        else if (bone.startsWith("neck_")) {
            look(stack, yaw, pitch, .09F);
            rotate(stack, -.03F * strike, 0, 0);
        } else if (bone.equals("head")) look(stack, yaw, pitch, .28F);
        else if (bone.equals("jaw")) rotate(stack, -.20F * strike - .025F * (1 + breath), 0, 0);
        else if (bone.startsWith("wing_")) {
            float flap = sin(age * .075F) * (flying ? .16F : .018F);
            if (bone.endsWith("_outer")) rotate(stack, 0, side * .018F * breath, -side * flap * .3F);
            else if (!bone.contains("_finger_")) rotate(stack, 0, 0, side * flap);
        } else if (bone.startsWith("tail_")) {
            rotate(stack, .01F * sin(age * .035F - lastNumber(bone) * .5F), .022F * sin(age * .045F - lastNumber(bone) * .6F), 0);
        } else if (bone.startsWith("leg_")) {
            boolean front = bone.startsWith("leg_0_");
            float stride = cos(phase + ((side > 0) ^ front ? PI : 0)) * speed * .18F;
            if (bone.endsWith("_shin")) rotate(stack, flying ? -.13F : -Math.max(0, stride) * .55F, 0, 0);
            else if (!bone.endsWith("_foot")) rotate(stack, flying ? .09F : stride, 0, 0);
        }
    }

    /** Anatomical IDs are shared with the existing weapon-specific fracture/absorption renderer. */
    public static int anatomy(String species, String bone) {
        species = path(species);
        if (bone == null) return ConsumeAnatomy.BODY;
        float side = side(bone);
        if (bone.contains("head") || bone.startsWith("jaw") || bone.startsWith("sensor_")) return ConsumeAnatomy.HEAD;
        if (bone.startsWith("tail") || bone.startsWith("tendril_")) return ConsumeAnatomy.TAIL;
        if (bone.equals("arms")) return ConsumeAnatomy.RIGHT_ARM;
        if (bone.startsWith("tentacle_") || squid(species) && bone.startsWith("arm_")) return 2 + lastNumber(bone) % 4;
        if (species.equals("ender_dragon") && bone.startsWith("leg_"))
            return bone.startsWith("leg_0_") ? arm(side) : leg(side);
        if (species.equals("creeper") && bone.startsWith("leg_")) return bone.endsWith("_-4") ? arm(side) : leg(side);
        if (bone.startsWith("paddle_")) return bone.endsWith("_-5") ? arm(side) : leg(side);
        if (arthropod(species) && bone.startsWith("leg_")) return lastNumber(bone) < 2 ? arm(side) : leg(side);
        if (bone.startsWith("front_") || bone.startsWith("wing_") || bone.startsWith("fin_")
                || bone.startsWith("arm_") || bone.startsWith("upper_arm_") || bone.startsWith("forearm_") || bone.startsWith("hand_")) return arm(side);
        if (bone.startsWith("hind_") || bone.startsWith("leg_") || bone.startsWith("thigh_")
                || bone.startsWith("shin_") || bone.startsWith("foot_")) return leg(side);
        return ConsumeAnatomy.BODY;
    }

    /** Projection is sampled once per entity, never once per triangle or limb. */
    public record ReceiverState(boolean active, boolean rigid, boolean mounted, double[] projection,
            ConsumeReaction.Pose reaction, float hitWeight, float hitBend, float hitTwist) {}

    public static ReceiverState receiverState(LivingEntity entity, float partial) {
        var visual = entity.getData(ModAttachments.CONSUME_VISUAL);
        boolean active = visual.active();
        double[] projection = active && (visual.consuming() || GrappleMotion.active(visual))
                ? ConsumePlacementTrack.projection(GrappleMotion.sceneKey(entity, visual),
                        GrappleMotion.sceneProgress(entity, visual, partial)).orElse(null) : null;
        var hit = entity.getData(ModAttachments.HIT_REACTION);
        float weight = active ? 0 : hit.weight(entity.level().getGameTime(), partial);
        float bend = (hit.kind() == HitReaction.SLAM ? .45F : -.18F) * weight;
        float twist = sin((float)Math.toRadians(hit.yaw() - entity.yBodyRot)) * .2F * weight;
        return new ReceiverState(active, ConsumePlacementTrack.rigidReceiver(entity),
                ConsumeSequence.mountedStage(visual.stage()), projection,
                active ? ConsumeVictimPose.reaction(entity, partial) : null, weight, bend, twist);
    }

    /** Returns true when a paired scene owns the pose and locomotion must be skipped.
     * Root displacement belongs to ConsumeVictimPose.body/HitBodyPose, not this method. */
    public static boolean receiver(String species, String bone, String parent, ReceiverState state, PoseStack stack) {
        species = path(species);
        if (state.active()) {
            Quaternionf desired = receiverDelta(species, bone, state);
            Quaternionf parentDelta = parent == null ? new Quaternionf() : receiverDelta(species, parent, state);
            stack.mulPose(parentDelta.invert().mul(desired));
            return true;
        }
        if (state.hitWeight() > 0) {
            if (faceOnly(species) && bone.equals("head")) return false;
            // Only the primary anatomical socket recoils; children inherit it once.
            int part = anatomy(species, bone);
            if (parent != null && !parent.equals("root") && anatomy(species, parent) == part) return false;
            float limit = boss(species) ? .18F : 1;
            if (part == ConsumeAnatomy.HEAD) rotate(stack, -state.hitBend() * .6F * limit, 0, 0);
            else if (part == ConsumeAnatomy.BODY && !bone.equals("root")) rotate(stack, state.hitBend() * limit, state.hitTwist() * limit, 0);
            else if (part == ConsumeAnatomy.RIGHT_ARM || part == ConsumeAnatomy.LEFT_ARM)
                rotate(stack, state.hitWeight() * .24F * limit, 0, side(bone) * state.hitWeight() * .08F * limit);
        }
        return false;
    }

    private static Quaternionf receiverDelta(String species, String bone, ReceiverState state) {
        if (bone == null || bone.equals("root")) return new Quaternionf();
        int part = anatomy(species, bone);
        if (faceOnly(species) && part == ConsumeAnatomy.HEAD) part = ConsumeAnatomy.BODY;
        if (bone.equals("neck") || bone.startsWith("neck_")) part = ConsumeAnatomy.HEAD;
        if (state.projection() != null) {
            int joint = switch(part) {case ConsumeAnatomy.HEAD -> 0; case ConsumeAnatomy.RIGHT_ARM -> 2;
                case ConsumeAnatomy.LEFT_ARM -> 3; case ConsumeAnatomy.RIGHT_LEG -> 4;
                case ConsumeAnatomy.LEFT_LEG -> 5; default -> 1;};
            Quaternionf delta = ConsumePlacementTrack.quaternion(state.projection(), 7 + joint * 4);
            if (state.rigid()) delta = ConsumePlacementTrack.quaternion(state.projection(), 11).invert().mul(delta);
            float weight = state.mounted() ? (part == ConsumeAnatomy.RIGHT_LEG || part == ConsumeAnatomy.LEFT_LEG ? 0 : .6F) : 1;
            delta = new Quaternionf().slerp(delta, weight);
            // Receiver projections use vanilla's down-Y model frame; the mesh is Y-up.
            return delta.set(delta.x, -delta.y, -delta.z, delta.w);
        }
        var p = state.reaction();
        float side = side(bone);
        float x = 0, y = 0, z = 0;
        switch(part) {
            case ConsumeAnatomy.HEAD -> { x = p.head(); y = -p.twist(); z = -p.twist() * .5F; }
            case ConsumeAnatomy.RIGHT_ARM, ConsumeAnatomy.LEFT_ARM -> { x = p.brace() * 1.35F; y = side * .15F; z = -side * (.2F + p.brace() * .2F); }
            case ConsumeAnatomy.RIGHT_LEG, ConsumeAnatomy.LEFT_LEG -> { x = side * p.kick() * .48F; z = side * .09F; }
            default -> { x = p.bend() * .18F; y = -p.twist() * .28F; }
        }
        float weight = state.mounted() ? .22F : 1;
        return new Quaternionf().rotationXYZ(x * weight, y * weight, z * weight);
    }

    private static boolean boss(String s) { return s.equals("warden") || s.equals("iron_golem") || s.equals("ender_dragon") || s.equals("wither"); }
    private static boolean faceOnly(String s) {
        return switch(s) {case "ghast", "strider", "slime", "magma_cube", "guardian", "elder_guardian", "pufferfish" -> true; default -> false;};
    }
    private static String path(String species) { int colon = species.indexOf(':'); return colon < 0 ? species : species.substring(colon + 1); }
    private static boolean squid(String s) { return s.equals("squid") || s.equals("glow_squid"); }
    private static boolean arthropod(String s) { return s.equals("spider") || s.equals("cave_spider") || s.equals("bee") || s.equals("silverfish") || s.equals("endermite"); }
    private static boolean aquatic(String s) {
        return switch(s) {case "cod", "salmon", "tropical_fish", "pufferfish", "squid", "glow_squid", "guardian", "elder_guardian", "dolphin", "tadpole" -> true; default -> false;};
    }
    private static boolean quadruped(String s) {
        return switch(s) {case "cow", "mooshroom", "pig", "sheep", "wolf", "cat", "ocelot", "fox", "panda", "polar_bear", "horse", "donkey", "mule", "skeleton_horse", "zombie_horse", "llama", "trader_llama", "camel", "goat", "hoglin", "zoglin", "ravager", "sniffer", "rabbit", "frog", "axolotl", "armadillo", "turtle" -> true; default -> false;};
    }
    private static boolean humanoid(String s) {
        return switch(s) {case "zombie", "zombie_villager", "husk", "drowned", "giant", "skeleton", "stray", "bogged", "wither_skeleton", "piglin", "piglin_brute", "zombified_piglin", "villager", "wandering_trader", "evoker", "illusioner", "witch", "pillager", "vindicator", "enderman" -> true; default -> false;};
    }
    private static int arm(float side) { return side < 0 ? ConsumeAnatomy.RIGHT_ARM : ConsumeAnatomy.LEFT_ARM; }
    private static int leg(float side) { return side < 0 ? ConsumeAnatomy.RIGHT_LEG : ConsumeAnatomy.LEFT_LEG; }
    private static float side(String bone) {
        if (bone.startsWith("leg_0_") || (bone.startsWith("leg_1_") && (bone.contains("_shin") || bone.contains("_foot")))) {
            String[] fields = bone.split("_");
            if (fields.length >= 3) return fields[2].equals("-1") ? -1 : 1;
        }
        if (bone.contains("_-1") || bone.contains("_-2") || bone.contains("right") || bone.endsWith("_R")) return -1;
        return 1;
    }
    private static int lastNumber(String name) {
        try { return Math.abs(Integer.parseInt(name.substring(name.lastIndexOf('_') + 1))); }
        catch (NumberFormatException ignored) { return 0; }
    }
    private static float unit(float value) { return Float.isFinite(value) ? Math.clamp(value, 0, 1) : 0; }
    private static float sin(float v) { return (float)Math.sin(v); }
    private static float cos(float v) { return (float)Math.cos(v); }
    private static void look(PoseStack stack, float yaw, float pitch, float weight) {
        rotate(stack, (float)Math.toRadians(-Math.clamp(pitch, -65, 65)) * weight,
                (float)Math.toRadians(-Math.clamp(yaw, -75, 75)) * weight, 0);
    }
    private static void rotate(PoseStack stack, float x, float y, float z) {
        if (z != 0) stack.mulPose(Axis.ZP.rotation(z));
        if (y != 0) stack.mulPose(Axis.YP.rotation(y));
        if (x != 0) stack.mulPose(Axis.XP.rotation(x));
    }
}
