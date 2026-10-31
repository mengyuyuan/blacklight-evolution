package com.codex.blacklight.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.codex.blacklight.forge.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/** Personal preferences, with strict DFU 6 decoding and defaults for older saves. */
public record SkillSettings(boolean bulletdiveBlockBreaking,boolean shieldBorerBlockBreaking,
        boolean prototypeItemPickup,int wingSpeedPercent,boolean precisionWings) {
    public SkillSettings {wingSpeedPercent=Math.max(10,Math.min(100,wingSpeedPercent));}
    public SkillSettings(boolean bullet,boolean shield,boolean pickup,int speed){this(bullet,shield,pickup,speed,false);}
    public SkillSettings(boolean bullet,boolean shield,boolean pickup){this(bullet,shield,pickup,100,false);}
    public SkillSettings(boolean bullet,boolean shield){this(bullet,shield,true);}
    public SkillSettings(boolean bullet){this(bullet,false);}
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
                read(ops,input,"shield_borer_block_breaking",false).flatMap(shield->
                    read(ops,input,"prototype_item_pickup",true).flatMap(pickup->
                        read(ops,input,"precision_wings",false).flatMap(precision->{
                            T speed=input.get("wing_speed_percent");
                            return (speed==null?DataResult.success(100):Codec.intRange(10,100).parse(ops,speed))
                                    .map(percent->new SkillSettings(bullet,shield,pickup,percent,precision));
                        }))));
        }
        @Override public <T> com.mojang.serialization.RecordBuilder<T> encode(SkillSettings value,
                com.mojang.serialization.DynamicOps<T> ops,com.mojang.serialization.RecordBuilder<T> prefix){
            return prefix.add("bulletdive_block_breaking",ops.createBoolean(value.bulletdiveBlockBreaking()))
                    .add("shield_borer_block_breaking",ops.createBoolean(value.shieldBorerBlockBreaking()))
                    .add("prototype_item_pickup",ops.createBoolean(value.prototypeItemPickup()))
                    .add("wing_speed_percent",ops.createInt(value.wingSpeedPercent()))
                    .add("precision_wings",ops.createBoolean(value.precisionWings()));
        }
        @Override public <T> java.util.stream.Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops){
            return java.util.stream.Stream.of("bulletdive_block_breaking","shield_borer_block_breaking",
                    "prototype_item_pickup","wing_speed_percent","precision_wings").map(ops::createString);
        }
    }.codec();
    public static final StreamCodec<FriendlyByteBuf,SkillSettings> STREAM_CODEC=StreamCodec.of(
        (b,s)->{b.writeBoolean(s.bulletdiveBlockBreaking());b.writeBoolean(s.shieldBorerBlockBreaking());
            b.writeBoolean(s.prototypeItemPickup());b.writeVarInt(s.wingSpeedPercent());b.writeBoolean(s.precisionWings());},
        b->new SkillSettings(b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readBoolean()));
    public SkillSettings withWingSpeed(int percent){return new SkillSettings(bulletdiveBlockBreaking,shieldBorerBlockBreaking,prototypeItemPickup,percent,precisionWings);}
    public SkillSettings withBulletdive(boolean enabled){return new SkillSettings(enabled,shieldBorerBlockBreaking,prototypeItemPickup,wingSpeedPercent,precisionWings);}
    public SkillSettings withShieldBorer(boolean enabled){return new SkillSettings(bulletdiveBlockBreaking,enabled,prototypeItemPickup,wingSpeedPercent,precisionWings);}
    public SkillSettings withPrototypeItemPickup(boolean enabled){return new SkillSettings(bulletdiveBlockBreaking,shieldBorerBlockBreaking,enabled,wingSpeedPercent,precisionWings);}
    public SkillSettings withPrecisionWings(boolean enabled){return new SkillSettings(bulletdiveBlockBreaking,shieldBorerBlockBreaking,prototypeItemPickup,wingSpeedPercent,enabled);}
}
