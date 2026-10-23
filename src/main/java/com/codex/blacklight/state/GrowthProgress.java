package com.codex.blacklight.state;
import com.codex.blacklight.power.GrowthUpgrade;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
public record GrowthProgress(int upgrades,int absorbed) {
    public GrowthProgress {upgrades&=GrowthUpgrade.knownMask();absorbed=Math.clamp(absorbed,0,1000000);}
    public static final GrowthProgress EMPTY=new GrowthProgress(0,0);
    public static final Codec<GrowthProgress> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.optionalFieldOf("upgrades",0).forGetter(GrowthProgress::upgrades),Codec.INT.optionalFieldOf("absorbed",0).forGetter(GrowthProgress::absorbed)).apply(i,GrowthProgress::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,GrowthProgress> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,GrowthProgress::upgrades,ByteBufCodecs.VAR_INT,GrowthProgress::absorbed,GrowthProgress::new);
    public boolean owns(GrowthUpgrade g){return (upgrades&g.bit)!=0;}
    public GrowthProgress buy(GrowthUpgrade g){return new GrowthProgress(upgrades|g.bit,absorbed);}
    public GrowthProgress consume(){return new GrowthProgress(upgrades,absorbed+1);}
}
