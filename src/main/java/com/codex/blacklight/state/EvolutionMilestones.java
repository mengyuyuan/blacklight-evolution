package com.codex.blacklight.state;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;

/** Earned qualifications survive later catalog, threshold and city additions. */
public record EvolutionMilestones(int flags) {
    public static final int INFINITE=1, PASSIVES=2, PERCENT=4;
    public static final EvolutionMilestones EMPTY=new EvolutionMilestones(0);
    public EvolutionMilestones {flags&=7;if((flags&PERCENT)!=0)flags|=PASSIVES|INFINITE;if((flags&PASSIVES)!=0)flags|=INFINITE;}
    public static final Codec<EvolutionMilestones> CODEC=Codec.INT.xmap(EvolutionMilestones::new,EvolutionMilestones::flags);
    public static final StreamCodec<RegistryFriendlyByteBuf,EvolutionMilestones> STREAM_CODEC=ByteBufCodecs.VAR_INT.map(EvolutionMilestones::new,EvolutionMilestones::flags).cast();
    public boolean has(int milestone){return (flags&milestone)!=0;}
    public EvolutionMilestones grant(int milestone){return new EvolutionMilestones(flags|milestone);}
}
