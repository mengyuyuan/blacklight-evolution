package com.codex.blacklight.state;

import com.codex.blacklight.power.EvolutionAttribute;
import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record InfiniteProgress(Map<String,Long> ranks) {
    public InfiniteProgress {
        var clean=new HashMap<String,Long>();
        for(var a:EvolutionAttribute.values()){long rank=Math.max(0,ranks.getOrDefault(a.id,0L));if(rank>0)clean.put(a.id,rank);}
        ranks=Map.copyOf(clean);
    }
    public static final InfiniteProgress EMPTY=new InfiniteProgress(Map.of());
    public static final Codec<InfiniteProgress> CODEC=Codec.unboundedMap(Codec.STRING,Codec.LONG).xmap(InfiniteProgress::new,InfiniteProgress::ranks);
    public static final StreamCodec<RegistryFriendlyByteBuf,InfiniteProgress> STREAM_CODEC=new StreamCodec<>(){
        public InfiniteProgress decode(RegistryFriendlyByteBuf b){
            int count=b.readVarInt();if(count<0||count>EvolutionAttribute.values().length)throw new IllegalArgumentException("Invalid evolution rank count");
            var ranks=new HashMap<String,Long>();for(int i=0;i<count;i++)ranks.put(b.readUtf(64),b.readVarLong());return new InfiniteProgress(ranks);
        }
        public void encode(RegistryFriendlyByteBuf b,InfiniteProgress p){b.writeVarInt(p.ranks.size());for(var a:EvolutionAttribute.values())if(p.rank(a)>0){b.writeUtf(a.id);b.writeVarLong(p.rank(a));}}
    };
    public long rank(EvolutionAttribute attribute){return ranks.getOrDefault(attribute.id,0L);}
    /** Adds n ranks at once, saturating at Long.MAX_VALUE. */
    public InfiniteProgress buy(EvolutionAttribute attribute,long count){
        long n=Math.max(0,count),rank=rank(attribute);if(n==0||rank==Long.MAX_VALUE)return this;
        var next=new HashMap<>(ranks);next.put(attribute.id,rank>Long.MAX_VALUE-n?Long.MAX_VALUE:rank+n);return new InfiniteProgress(next);
    }
    public InfiniteProgress buy(EvolutionAttribute attribute){
        if(rank(attribute)==Long.MAX_VALUE)return this;
        var next=new HashMap<>(ranks);next.put(attribute.id,rank(attribute)+1);return new InfiniteProgress(next);
    }
}
