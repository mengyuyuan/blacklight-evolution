package com.codex.blacklight.network;

import com.codex.blacklight.BlacklightNeoMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Changes only the sending player's preferences, not their unlocks or power. */
public record SkillSettingsPayload(Setting setting,boolean enabled) implements CustomPacketPayload {
    public enum Setting { BULLETDIVE_BLOCKS, SHIELD_BORER_BLOCKS }
    public SkillSettingsPayload(boolean enabled){this(Setting.BULLETDIVE_BLOCKS,enabled);}
    public com.codex.blacklight.power.SkillSettings apply(com.codex.blacklight.power.SkillSettings current){
        return switch(setting){
            case BULLETDIVE_BLOCKS -> current.withBulletdive(enabled);
            case SHIELD_BORER_BLOCKS -> current.withShieldBorer(enabled);
        };
    }
    public static final Type<SkillSettingsPayload> TYPE=new Type<>(
            ResourceLocation.fromNamespaceAndPath(BlacklightNeoMod.MOD_ID,"skill_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SkillSettingsPayload> STREAM_CODEC=
            StreamCodec.of((buffer,payload)->{buffer.writeEnum(payload.setting);buffer.writeBoolean(payload.enabled);},
                    buffer->new SkillSettingsPayload(buffer.readEnum(Setting.class),buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
