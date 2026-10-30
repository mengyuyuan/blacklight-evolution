package com.codex.blacklight.state;

import com.codex.blacklight.power.EvolutionAttribute;
import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.StreamCodec;

public record InfiniteProgress(Map<String,Long> ranks) {
    public InfiniteProgress {
        var clean=new HashMap<String,Long>();
        for(var a:EvolutionAttribute.values()){long rank=Math.max(0,ranks.getOrDefault(a.id,0L));if(rank>0)clean.put(a.id,rank);}
        ranks=Map.copyOf(clean);
    }
    public static final InfiniteProgress EMPTY=new InfiniteProgress(Map.of());
    public static final Codec<InfiniteProgress> CODEC=Codec.unboundedMap(Codec.STRING,Codec.LONG).xmap(InfiniteProgress::new,InfiniteProgress::ranks);
    public static final StreamCodec<FriendlyByteBuf,InfiniteProgress> STREAM_CODEC=new StreamCodec<>(){
        public InfiniteProgress decode(FriendlyByteBuf b){
            int count=b.readVarInt();if(count<0||count>EvolutionAttribute.values().length)throw new IllegalArgumentException("Invalid evolution rank count");
            var ranks=new HashMap<String,Long>();for(int i=0;i<count;i++)ranks.put(b.readUtf(64),b.readVarLong());return new InfiniteProgress(ranks);
        }
        public void encode(FriendlyByteBuf b,InfiniteProgress p){b.writeVarInt(p.ranks.size());for(var a:EvolutionAttribute.values())if(p.rank(a)>0){b.writeUtf(a.id);b.writeVarLong(p.rank(a));}}
    };
    public long rank(EvolutionAttribute attribute){return ranks.getOrDefault(attribute.id,0L);}
    public InfiniteProgress buy(EvolutionAttribute attribute){
        if(rank(attribute)==Long.MAX_VALUE)return this;
        var next=new HashMap<>(ranks);next.put(attribute.id,rank(attribute)+1);return new InfiniteProgress(next);
    }
}
