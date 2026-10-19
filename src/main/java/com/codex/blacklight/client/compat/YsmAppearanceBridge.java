package com.codex.blacklight.client.compat;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.client.animation.BlacklightRenderPose;
import com.codex.blacklight.client.model.MercerRig;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.power.UtilityPowerSystem;
import com.codex.blacklight.registry.ModAttachments;
import com.google.gson.JsonParser;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import java.io.InputStreamReader;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Optional official-YSM bridge. No YSM implementation names or assets are distributed here. */
public final class YsmAppearanceBridge {
    private static final String PACK = "alex_mercer_blacklight_bridge";
    private static final String API = "net.okitsu.ysmmapping.api.";
    private static boolean attempted, failed;
    private static Method stateLookup, modelId, disabled, providerGetter, setter, hasher;
    private static YsmPoseFrame pose;
    private static final Map<String,Integer> HASHES = new HashMap<>();
    private static final Map<AbstractClientPlayer,MercerRig.Frame> FRAMES = new WeakHashMap<>();
    private YsmAppearanceBridge() { }

    private static Object symbol(Object snapshot, Class<?> symbols, String name) throws ReflectiveOperationException {
        return snapshot.getClass().getMethod("require", Class.forName(API + "YsmSymbolKey"))
                .invoke(snapshot, symbols.getField(name).get(null));
    }
    private static Method method(Object symbol) throws ReflectiveOperationException {
        var type = symbol.getClass();
        String owner = (String) type.getMethod("binaryOwner").invoke(symbol);
        String name = (String) type.getMethod("name").invoke(symbol);
        String descriptor = (String) type.getMethod("descriptor").invoke(symbol);
        var signature = MethodType.fromMethodDescriptorString(descriptor, YsmAppearanceBridge.class.getClassLoader());
        var result = Class.forName(owner).getDeclaredMethod(name, signature.parameterArray());
        result.setAccessible(true);
        return result;
    }
    private static void initialize() {
        if (attempted) return; attempted = true;
        if (!net.neoforged.fml.ModList.get().isLoaded("yes_steve_model")
                || !net.neoforged.fml.ModList.get().isLoaded("ysm_mapping_api")) return;
        try {
            var symbols = Class.forName(API + "YsmSymbols");
            var keys = new ArrayList<Object>();
            for (String key : new String[]{"PLAYER_STATE_CAPABILITY_CLASS", "PLAYER_STATE_MODEL_ID_GETTER",
                    "PLAYER_STATE_MODEL_DISABLED_GETTER", "PLAYER_STATE_ROAMING_PROVIDER_GETTER",
                    "PLAYER_STATE_ROAMING_VALUE_SETTER", "PLAYER_STATE_ROAMING_NAME_HASHER"}) keys.add(symbols.getField(key).get(null));
            var snapshot = Class.forName(API + "YsmMappingApi").getMethod("resolve", String.class, Collection.class)
                    .invoke(null, BlacklightNeoMod.MOD_ID, keys);
            var state = symbol(snapshot, symbols, "PLAYER_STATE_CAPABILITY_CLASS");
            var stateClass = Class.forName((String) state.getClass().getMethod("binaryName").invoke(state));
            var candidates = Arrays.stream(stateClass.getMethods()).filter(m -> Modifier.isStatic(m.getModifiers())
                    && Arrays.equals(m.getParameterTypes(), new Class<?>[]{Player.class}) && m.getReturnType() == Optional.class).toList();
            if (candidates.size() != 1) throw new IllegalStateException("Ambiguous YSM player-state accessor");
            stateLookup = candidates.getFirst();
            modelId = method(symbol(snapshot, symbols, "PLAYER_STATE_MODEL_ID_GETTER"));
            disabled = method(symbol(snapshot, symbols, "PLAYER_STATE_MODEL_DISABLED_GETTER"));
            providerGetter = method(symbol(snapshot, symbols, "PLAYER_STATE_ROAMING_PROVIDER_GETTER"));
            setter = method(symbol(snapshot, symbols, "PLAYER_STATE_ROAMING_VALUE_SETTER"));
            hasher = method(symbol(snapshot, symbols, "PLAYER_STATE_ROAMING_NAME_HASHER"));
            try (var stream = YsmAppearanceBridge.class.getResourceAsStream("/assets/blacklight/models/mercer_v4.json")) {
                if (stream == null) throw new IllegalStateException("Missing character rig");
                pose = new YsmPoseFrame(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonArray("bones"));
            }
            BlacklightNeoMod.LOGGER.info("Optional YSM appearance bridge initialized; waiting for the dedicated bridge model");
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError unavailable) {
            fail();
        }
    }
    private static void fail() {
        failed = true; FRAMES.clear();
        // Mapping exceptions can contain implementation names. Keep the public log semantic.
        BlacklightNeoMod.LOGGER.warn("YSM appearance bridge unavailable; retaining the normal Blacklight renderer");
    }
    private static void write(Object provider, String name, float value) throws ReflectiveOperationException {
        Integer hash = HASHES.get(name);
        if (hash == null) { hash = ((Number) hasher.invoke(null, name)).intValue(); HASHES.put(name, hash); }
        setter.invoke(provider, hash, Float.valueOf(Float.isFinite(value) ? value : 0));
    }
    public static boolean prepare(AbstractClientPlayer player, float partial) {
        initialize(); FRAMES.remove(player);
        if (pose == null || failed) return false;
        try {
            Object state = ((Optional<?>) stateLookup.invoke(null, player)).orElse(null);
            if (state == null || Boolean.TRUE.equals(disabled.invoke(state))) return false;
            String selected = (String) modelId.invoke(state);
            if (selected == null || !(selected.equals(PACK) || selected.endsWith("/" + PACK))) return false;
            Object provider = providerGetter.invoke(state);
            if (provider == null) return false;
            boolean active = player.getData(ModAttachments.PLAYER_STATE).isPrototypeMode();
            write(provider, "bl_active", active ? 1 : 0);
            write(provider, "bl_armor", active ? UtilityPowerSystem.visualWeight(player, PowerForm.ARMOR, partial) : 0);
            var armor = com.codex.blacklight.client.model.BlockArmorModel.get();
            for (String region : new String[]{"head", "torso", "left_arm", "right_arm", "left_leg", "right_leg"})
                write(provider, "bl_body_" + region, active && armor.covers(player, region, partial) ? 0 : 1);
            if (!active) return false;
            var source = PlayerMotionController.skeleton().getPoseMatrices(BlacklightRenderPose.sample(player, partial));
            boolean grip = player.getData(ModAttachments.GRAB_STAGE) != 0 || com.codex.blacklight.combat.BlockThrowSystem.held(player) != null;
            var frame = pose.frame(source, grip);
            for (var bone : pose.channels(frame)) {
                String prefix = "bl_" + bone.name().toLowerCase(Locale.ROOT);
                write(provider, prefix + "_rx", bone.rotation().x); write(provider, prefix + "_ry", bone.rotation().y); write(provider, prefix + "_rz", bone.rotation().z);
                write(provider, prefix + "_px", bone.position().x); write(provider, prefix + "_py", bone.position().y); write(provider, prefix + "_pz", bone.position().z);
            }
            var morph = com.codex.blacklight.client.renderer.WeaponMorphTracker.sample(player, partial);
            write(provider, "bl_left_arm", morph.coverage(true) >= .72F ? 0 : 1);
            write(provider, "bl_right_arm", morph.coverage(false) >= .72F ? 0 : 1);
            FRAMES.put(player, frame);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { fail(); return false; }
    }
    public static MercerRig.Frame frame(AbstractClientPlayer player) { return FRAMES.get(player); }
    public static boolean active(AbstractClientPlayer player) { return FRAMES.containsKey(player); }
    public static void invalidate() { FRAMES.clear(); }
}
