package com.codex.blacklight.state;

import com.codex.blacklight.infection.TissueCore;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;

/** Highest verified specimen and purchased rank for each organ. No per-victim history grows here. */
public record OrganResearch(int samples,int upgrades) {
    public static final OrganResearch EMPTY=new OrganResearch(0,0);
    public OrganResearch {
        samples&=1023;upgrades&=1023;
        for(var core:TissueCore.values()){
            int shift=core.ordinal()*2,known=(samples>>shift)&3,bought=(upgrades>>shift)&3;
            if(bought>known)upgrades=(upgrades&~(3<<shift))|(known<<shift);
        }
    }
    public static final Codec<OrganResearch> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.INT.optionalFieldOf("samples",0).forGetter(OrganResearch::samples),
            Codec.INT.optionalFieldOf("upgrades",0).forGetter(OrganResearch::upgrades)).apply(i,OrganResearch::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,OrganResearch> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,OrganResearch::samples,ByteBufCodecs.VAR_INT,OrganResearch::upgrades,OrganResearch::new);
    public int sample(TissueCore core){return samples>>(core.ordinal()*2)&3;}
    public int rank(TissueCore core){return upgrades>>(core.ordinal()*2)&3;}
    public OrganResearch discover(TissueCore core,int quality){
        int shift=core.ordinal()*2,next=Math.max(sample(core),Math.clamp(quality,0,3));
        return new OrganResearch((samples&~(3<<shift))|(next<<shift),upgrades);
    }
    public boolean canBuy(TissueCore core){return rank(core)<sample(core);}
    public OrganResearch buy(TissueCore core){return canBuy(core)?new OrganResearch(samples,upgrades+(1<<(core.ordinal()*2))):this;}
}
