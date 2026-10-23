package com.codex.blacklight.disguise;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import java.util.ArrayList;
import java.util.List;

/** Server-owned appearance collection. Packets select a revision/index, never a supplied entity NBT. */
public record DisguiseLibrary(int revision, List<DisguiseIdentity> identities) {
    public static final int LIMIT=64;
    public static final DisguiseLibrary EMPTY=new DisguiseLibrary(0,List.of());
    private record Stored(int revision,List<DisguiseIdentity> identities) {}
    private static final Codec<Stored> STORAGE_CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.INT.fieldOf("revision").forGetter(Stored::revision),
            DisguiseIdentity.CODEC.listOf().fieldOf("identities").forGetter(Stored::identities)
    ).apply(i,Stored::new));
    // Older DFU passes partial values into record constructors. Validate after decoding
    // the harmless intermediate record so a damaged save returns an error, not a throw.
    public static final Codec<DisguiseLibrary> CODEC=STORAGE_CODEC.flatXmap(
            stored->stored.revision()<0||stored.identities().size()>LIMIT
                    ?DataResult.error(()->"Invalid identity collection")
                    :DataResult.success(new DisguiseLibrary(stored.revision(),stored.identities())),
            library->DataResult.success(new Stored(library.revision(),library.identities())));
    public static final StreamCodec<RegistryFriendlyByteBuf,DisguiseLibrary> STREAM_CODEC=new StreamCodec<>() {
        @Override public DisguiseLibrary decode(RegistryFriendlyByteBuf buf) {
            int revision=buf.readVarInt(),size=buf.readVarInt();
            if(revision<0||size<0||size>LIMIT)throw new DecoderException("Invalid identity collection");
            var entries=new ArrayList<DisguiseIdentity>(size);
            for(int n=0;n<size;n++)entries.add(DisguiseIdentity.STREAM_CODEC.decode(buf));
            return new DisguiseLibrary(revision,entries);
        }
        @Override public void encode(RegistryFriendlyByteBuf buf,DisguiseLibrary value) {
            buf.writeVarInt(value.revision);buf.writeVarInt(value.identities.size());
            value.identities.forEach(entry->DisguiseIdentity.STREAM_CODEC.encode(buf,entry));
        }
    };
    public DisguiseLibrary {
        if(revision<0||identities.size()>LIMIT)throw new IllegalArgumentException("Invalid identity collection");
        identities=identities.stream().map(DisguiseIdentity::compact).toList();
    }
    public DisguiseLibrary remember(DisguiseIdentity identity) {
        if(identity.type().isBlank())return this;
        var compact=identity.compact();
        if(identities.contains(compact))return this;
        var entries=new ArrayList<DisguiseIdentity>();entries.add(compact);
        entries.addAll(identities.subList(0,Math.min(identities.size(),LIMIT-1)));
        return new DisguiseLibrary((revision+1)&Integer.MAX_VALUE,entries);
    }
    public DisguiseIdentity select(int expectedRevision,int index) {
        return expectedRevision==revision&&index>=0&&index<identities.size()?identities.get(index):null;
    }
}
