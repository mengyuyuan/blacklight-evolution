package com.codex.blacklight.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.codex.blacklight.forge.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/** Personal preferences, with strict DFU 6 decoding and old-save defaults. */
public record SkillSettings(boolean bulletdiveBlockBreaking,boolean shieldBorerBlockBreaking) {
    public SkillSettings(boolean bulletdiveBlockBreaking){this(bulletdiveBlockBreaking,false);}
    public static final SkillSettings DEFAULT=new SkillSettings(true,false);
    private static <T> DataResult<Boolean> read(com.mojang.serialization.DynamicOps<T> ops,
            com.mojang.serialization.MapLike<T> input,String name,boolean fallback){
        T value=input.get(name);
        if(value==null)return DataResult.success(fallback);
        if(ops.getStringValue(value).result().isPresent())return DataResult.error(()->"Expected a boolean for "+name);
        return Codec.BOOL.parse(ops,value);
    }
    public static final Codec<SkillSettings> CODEC=new com.mojang.serialization.MapCodec<SkillSettings>() {
        @Override public <T> DataResult<SkillSettings> decode(com.mojang.serialization.DynamicOps<T> ops,com.mojang.serialization.MapLike<T> input){
            return read(ops,input,"bulletdive_block_breaking",true).flatMap(bullet->
                read(ops,input,"shield_borer_block_breaking",false).map(shield->new SkillSettings(bullet,shield)));
        }
        @Override public <T> com.mojang.serialization.RecordBuilder<T> encode(SkillSettings value,
                com.mojang.serialization.DynamicOps<T> ops,com.mojang.serialization.RecordBuilder<T> prefix){
            return prefix.add("bulletdive_block_breaking",ops.createBoolean(value.bulletdiveBlockBreaking()))
                    .add("shield_borer_block_breaking",ops.createBoolean(value.shieldBorerBlockBreaking()));
        }
        @Override public <T> java.util.stream.Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops){
            return java.util.stream.Stream.of(ops.createString("bulletdive_block_breaking"),ops.createString("shield_borer_block_breaking"));
        }
    }.codec();
    public static final StreamCodec<FriendlyByteBuf,SkillSettings> STREAM_CODEC=StreamCodec.of(
        (b,s)->{b.writeBoolean(s.bulletdiveBlockBreaking());b.writeBoolean(s.shieldBorerBlockBreaking());},
        b->new SkillSettings(b.readBoolean(),b.readBoolean()));
    public SkillSettings withBulletdive(boolean enabled){return new SkillSettings(enabled,shieldBorerBlockBreaking);}
    public SkillSettings withShieldBorer(boolean enabled){return new SkillSettings(bulletdiveBlockBreaking,enabled);}
}
