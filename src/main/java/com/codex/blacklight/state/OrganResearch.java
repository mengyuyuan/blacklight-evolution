package com.codex.blacklight.state;

import com.codex.blacklight.infection.TissueCore;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Stable-ID specimen records. Old five two-bit slots are decoded only at the save boundary. */
public record OrganResearch(Map<String,Progress> organs) {
    public record Progress(int sample,int rank) {
        public Progress {sample=Math.clamp(sample,0,3);rank=Math.clamp(rank,0,sample);}
        static final Codec<Progress> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.INT.fieldOf("sample").forGetter(Progress::sample),Codec.INT.fieldOf("rank").forGetter(Progress::rank)).apply(i,Progress::new));
    }
    public static final OrganResearch EMPTY=new OrganResearch(Map.of());
    public OrganResearch {
        var copy=new TreeMap<String,Progress>();
        organs.forEach((id,p)->{String key=key(id);if(key!=null&&p!=null)copy.merge(key,p,(a,b)->new Progress(Math.max(a.sample,b.sample),Math.max(a.rank,b.rank)));});organs=Map.copyOf(copy);
    }
    public OrganResearch(int samples,int upgrades){this(legacy(samples,upgrades));}
    private static Map<String,Progress> legacy(int samples,int upgrades){
        var result=new HashMap<String,Progress>();for(int i=0;i<5;i++){
            int sample=(samples>>>(i*2))&3,rank=(upgrades>>>(i*2))&3;
            if(sample>0)result.put(TissueCore.values()[i].key(),new Progress(sample,rank));
        }return result;
    }
    private static String key(String id){if(id==null)return null;var key=ResourceLocation.tryParse(id.indexOf(':')<0?"blacklight:"+id:id);return key==null?null:key.toString();}
    public static final Codec<OrganResearch> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.unboundedMap(Codec.STRING,Progress.CODEC).optionalFieldOf("organs",Map.of()).forGetter(OrganResearch::organs),
            Codec.INT.optionalFieldOf("samples",0).forGetter(v->0),Codec.INT.optionalFieldOf("upgrades",0).forGetter(v->0))
            .apply(i,(map,samples,upgrades)->{var merged=new HashMap<>(legacy(samples,upgrades));merged.putAll(map);return new OrganResearch(merged);}));
    public static final StreamCodec<RegistryFriendlyByteBuf,OrganResearch> STREAM_CODEC=new StreamCodec<>(){
        public OrganResearch decode(RegistryFriendlyByteBuf b){
            int size=b.readVarInt();if(size<0||size>256)throw new IllegalArgumentException("Organ research count");
            var map=new HashMap<String,Progress>();for(int i=0;i<size;i++)map.put(b.readUtf(256),new Progress(b.readVarInt(),b.readVarInt()));
            return new OrganResearch(map);
        }
        public void encode(RegistryFriendlyByteBuf b,OrganResearch value){
            if(value.organs.size()>256)throw new IllegalArgumentException("Organ research count");
            b.writeVarInt(value.organs.size());new TreeMap<>(value.organs).forEach((id,p)->{b.writeUtf(id,256);b.writeVarInt(p.sample);b.writeVarInt(p.rank);});
        }
    };
    public int sample(TissueCore core){return sample(core.key());}
    public int rank(TissueCore core){return rank(core.key());}
    public int sample(String id){String key=key(id);var p=key==null?null:organs.get(key);return p==null?0:p.sample;}
    public int rank(String id){String key=key(id);var p=key==null?null:organs.get(key);return p==null?0:p.rank;}
    public OrganResearch discover(TissueCore core,int quality){return discover(core.key(),quality);}
    public OrganResearch discover(String id,int quality){
        var next=new HashMap<>(organs);String key=key(id);if(key==null)return this;
        next.put(key,new Progress(Math.max(sample(id),quality),rank(id)));return new OrganResearch(next);
    }
    public boolean canBuy(TissueCore core){return rank(core)<sample(core);}
    public OrganResearch buy(TissueCore core){
        if(!canBuy(core))return this;var next=new HashMap<>(organs);next.put(core.key(),new Progress(sample(core),rank(core)+1));return new OrganResearch(next);
    }
    /** Legacy accessors for integrations that read the original five entries. */
    public int samples(){int result=0;for(int i=0;i<5;i++)result|=sample(TissueCore.values()[i])<<(i*2);return result;}
    public int upgrades(){int result=0;for(int i=0;i<5;i++)result|=rank(TissueCore.values()[i])<<(i*2);return result;}
}
