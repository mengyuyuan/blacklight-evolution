package com.codex.blacklight.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

/** Personal gameplay choices persist independently of learned skills. */
public record SkillSettings(boolean bulletdiveBlockBreaking,boolean shieldBorerBlockBreaking) {
    public SkillSettings(boolean bulletdiveBlockBreaking){this(bulletdiveBlockBreaking,false);}
    public static final SkillSettings DEFAULT=new SkillSettings(true,false);
    public static final Codec<SkillSettings> CODEC=RecordCodecBuilder.create(instance->instance.group(
            Codec.BOOL.optionalFieldOf("bulletdive_block_breaking",true)
                    .forGetter(SkillSettings::bulletdiveBlockBreaking),
            Codec.BOOL.optionalFieldOf("shield_borer_block_breaking",false)
                    .forGetter(SkillSettings::shieldBorerBlockBreaking)
    ).apply(instance,SkillSettings::new));
    public static final StreamCodec<ByteBuf,SkillSettings> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.BOOL,SkillSettings::bulletdiveBlockBreaking,
            ByteBufCodecs.BOOL,SkillSettings::shieldBorerBlockBreaking,SkillSettings::new);
    public SkillSettings withBulletdive(boolean enabled){return new SkillSettings(enabled,shieldBorerBlockBreaking);}
    public SkillSettings withShieldBorer(boolean enabled){return new SkillSettings(bulletdiveBlockBreaking,enabled);}
}
