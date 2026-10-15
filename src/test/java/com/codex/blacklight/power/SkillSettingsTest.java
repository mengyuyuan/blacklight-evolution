package com.codex.blacklight.power;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillSettingsTest {
    @Test void existingSavesWithoutPreferencesEnableBlockBreaking() {
        assertTrue(SkillSettings.DEFAULT.bulletdiveBlockBreaking());
        assertFalse(SkillSettings.DEFAULT.shieldBorerBlockBreaking());
        assertTrue(SkillSettings.DEFAULT.prototypeItemPickup());
        assertEquals(SkillSettings.DEFAULT, SkillSettings.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{}")).getOrThrow());
    }

    @Test void disablingBlockBreakingSurvivesSaveAndNetworkRoundTrips() {
        for (boolean enabled : new boolean[]{false, true}) for(boolean borer:new boolean[]{false,true}) for(boolean pickup:new boolean[]{false,true}) {
            var settings = new SkillSettings(enabled,borer,pickup);
            var saved = SkillSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
            assertEquals(settings, SkillSettings.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
            var packet = Unpooled.buffer();
            try {
                SkillSettings.STREAM_CODEC.encode(packet, settings);
                assertEquals(settings, SkillSettings.STREAM_CODEC.decode(packet));
                assertFalse(packet.isReadable(), "round trip must consume the complete preference packet");
            } finally {
                packet.release();
            }
        }
    }

    @Test void oldSavesKeepBulletdiveChoiceButDoNotAutoEnableBorer(){
        var settings=SkillSettings.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"bulletdive_block_breaking\":false}")).getOrThrow();
        assertEquals(new SkillSettings(false,false),settings);
    }

    @Test void independentSettingRequestsNeverOverwriteOtherPreferences(){
        var borer=new com.codex.blacklight.network.SkillSettingsPayload(
                com.codex.blacklight.network.SkillSettingsPayload.Setting.SHIELD_BORER_BLOCKS,true);
        var bullet=new com.codex.blacklight.network.SkillSettingsPayload(false);
        var first=borer.apply(SkillSettings.DEFAULT);
        assertEquals(new SkillSettings(true,true),first);
        assertEquals(new SkillSettings(false,true),bullet.apply(first));
        assertEquals(bullet.apply(first),borer.apply(bullet.apply(SkillSettings.DEFAULT)));
        var pickup=new com.codex.blacklight.network.SkillSettingsPayload(
                com.codex.blacklight.network.SkillSettingsPayload.Setting.PROTOTYPE_ITEM_PICKUP,false);
        assertEquals(new SkillSettings(false,true,false),pickup.apply(bullet.apply(first)));
        assertEquals(pickup.apply(bullet.apply(first)),bullet.apply(borer.apply(pickup.apply(SkillSettings.DEFAULT))));
    }

    @Test void settingRequestsRoundTripWithTheirOwnIdentity(){
        for(var setting:com.codex.blacklight.network.SkillSettingsPayload.Setting.values())for(boolean enabled:new boolean[]{false,true}){
            var expected=new com.codex.blacklight.network.SkillSettingsPayload(setting,enabled);
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
            try{
                com.codex.blacklight.network.SkillSettingsPayload.STREAM_CODEC.encode(buffer,expected);
                assertEquals(expected,com.codex.blacklight.network.SkillSettingsPayload.STREAM_CODEC.decode(buffer));
                assertFalse(buffer.isReadable());
            }finally{buffer.release();}
        }
    }

    @Test void corruptPreferenceCannotSilentlyTurnOffDestruction() {
        assertTrue(SkillSettings.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"bulletdive_block_breaking\":\"invalid\"}")).error().isPresent());
    }
}
