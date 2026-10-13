package com.codex.blacklight.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

/** Personal gameplay choices persist independently of learned skills. */
public record SkillSettings(boolean bulletdiveBlockBreaking) {
    public static final SkillSettings DEFAULT=new SkillSettings(true);
    public static final Codec<SkillSettings> CODEC=RecordCodecBuilder.create(instance->instance.group(
            Codec.BOOL.optionalFieldOf("bulletdive_block_breaking",true)
                    .forGetter(SkillSettings::bulletdiveBlockBreaking)
    ).apply(instance,SkillSettings::new));
    public static final StreamCodec<ByteBuf,SkillSettings> STREAM_CODEC=ByteBufCodecs.BOOL
            .map(SkillSettings::new,SkillSettings::bulletdiveBlockBreaking);
}
