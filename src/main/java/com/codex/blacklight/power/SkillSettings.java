package com.codex.blacklight.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

/** Personal gameplay choices persist independently of learned skills. */
public record SkillSettings(boolean bulletdiveBlockBreaking,boolean shieldBorerBlockBreaking,boolean prototypeItemPickup,int wingSpeedPercent) {
    public SkillSettings { wingSpeedPercent=Math.max(10,Math.min(100,wingSpeedPercent)); }
    public SkillSettings(boolean bulletdiveBlockBreaking,boolean shieldBorerBlockBreaking,boolean prototypeItemPickup){this(bulletdiveBlockBreaking,shieldBorerBlockBreaking,prototypeItemPickup,100);}
    public SkillSettings(boolean bulletdiveBlockBreaking,boolean shieldBorerBlockBreaking){this(bulletdiveBlockBreaking,shieldBorerBlockBreaking,true);}
    public SkillSettings(boolean bulletdiveBlockBreaking){this(bulletdiveBlockBreaking,false);}
    public static final SkillSettings DEFAULT=new SkillSettings(true,false);
    public static final Codec<SkillSettings> CODEC=RecordCodecBuilder.create(instance->instance.group(
            Codec.BOOL.optionalFieldOf("bulletdive_block_breaking",true)
                    .forGetter(SkillSettings::bulletdiveBlockBreaking),
            Codec.BOOL.optionalFieldOf("shield_borer_block_breaking",false)
                    .forGetter(SkillSettings::shieldBorerBlockBreaking),
            Codec.BOOL.optionalFieldOf("prototype_item_pickup",true)
                    .forGetter(SkillSettings::prototypeItemPickup),
            Codec.intRange(10,100).optionalFieldOf("wing_speed_percent",100).forGetter(SkillSettings::wingSpeedPercent)
    ).apply(instance,SkillSettings::new));
    public static final StreamCodec<ByteBuf,SkillSettings> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.BOOL,SkillSettings::bulletdiveBlockBreaking,
            ByteBufCodecs.BOOL,SkillSettings::shieldBorerBlockBreaking,
            ByteBufCodecs.BOOL,SkillSettings::prototypeItemPickup,
            ByteBufCodecs.VAR_INT,SkillSettings::wingSpeedPercent,SkillSettings::new);
    public SkillSettings withWingSpeed(int percent){return new SkillSettings(bulletdiveBlockBreaking,shieldBorerBlockBreaking,prototypeItemPickup,percent);}
    public SkillSettings withBulletdive(boolean enabled){return new SkillSettings(enabled,shieldBorerBlockBreaking,prototypeItemPickup,wingSpeedPercent);}
    public SkillSettings withShieldBorer(boolean enabled){return new SkillSettings(bulletdiveBlockBreaking,enabled,prototypeItemPickup,wingSpeedPercent);}
    public SkillSettings withPrototypeItemPickup(boolean enabled){return new SkillSettings(bulletdiveBlockBreaking,shieldBorerBlockBreaking,enabled,wingSpeedPercent);}
}
