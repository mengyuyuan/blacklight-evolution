package com.codex.blacklight.disguise;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DisguiseLibraryTest {
    private static DisguiseIdentity identity(int variant){var tag=new CompoundTag();tag.putInt("Variant",variant);return new DisguiseIdentity("minecraft:cat","animal",tag);}
    @Test void distinctAppearancesSurviveAndDuplicatesDoNotEvict(){
        var a=identity(1);var b=identity(2);var library=DisguiseLibrary.EMPTY.remember(a).remember(b);
        assertEquals(List.of(b,a),library.identities());assertSame(library,library.remember(a));
        assertThrows(UnsupportedOperationException.class,()->library.identities().clear());
    }
    @Test void boundedCollectionRejectsStaleOrForgedSelections(){
        var library=DisguiseLibrary.EMPTY;
        for(int n=0;n<100;n++)library=library.remember(identity(n));
        assertEquals(64,library.identities().size());assertEquals(identity(99),library.select(library.revision(),0));
        assertEquals(identity(36),library.select(library.revision(),63));
        assertNull(library.select(library.revision()-1,0));assertNull(library.select(library.revision(),-1));assertNull(library.select(library.revision(),64));
    }
    @Test void cosmeticItemNbtCannotMakeAnOversizedCollectionPacket(){
        var tag=new CompoundTag();tag.putInt("Variant",4);tag.putString("HandItems","x".repeat(100000));
        var original=new DisguiseIdentity("minecraft:cat","animal",tag);var compact=original.compact();
        assertEquals(4,compact.appearance().getInt("Variant"));assertFalse(compact.appearance().contains("HandItems"));
        assertTrue(original.appearance().contains("HandItems"));
        tag=new CompoundTag();tag.putString("Variant","x".repeat(2000));assertTrue(new DisguiseIdentity("minecraft:cat","animal",tag).compact().appearance().isEmpty());
    }
    @Test void savedLibraryRoundTripsAndMalformedSaveIsRejected(){
        var library=DisguiseLibrary.EMPTY.remember(identity(4)).remember(identity(7));
        var nbt=DisguiseLibrary.CODEC.encodeStart(NbtOps.INSTANCE,library).getOrThrow();
        assertEquals(library,DisguiseLibrary.CODEC.parse(NbtOps.INSTANCE,nbt).getOrThrow());
        var bad=new CompoundTag();bad.putInt("revision",-1);bad.put("identities",new ListTag());
        assertTrue(DisguiseLibrary.CODEC.parse(NbtOps.INSTANCE,bad).error().isPresent());
    }
    @Test void fullLibraryFitsForgeEnvelopeAndRoundTrips(){
        var library=DisguiseLibrary.EMPTY;
        for(int n=0;n<64;n++){var tag=new CompoundTag();tag.putInt("Variant",n);tag.putString("Cosmetic","a".repeat(430));library=library.remember(new DisguiseIdentity("minecraft:"+"a".repeat(118),"a".repeat(32),tag));}
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{DisguiseLibrary.STREAM_CODEC.encode(buffer,library);assertTrue(buffer.readableBytes()<48000);assertEquals(library,DisguiseLibrary.STREAM_CODEC.decode(buffer));assertFalse(buffer.isReadable());}
        finally{buffer.release();}
    }
    @Test void oversizedNetworkListIsRejectedBeforeEntryAllocation(){
        for(int count:new int[]{-1,65,Integer.MAX_VALUE}){
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try{buffer.writeVarInt(0);buffer.writeVarInt(count);assertThrows(DecoderException.class,()->DisguiseLibrary.STREAM_CODEC.decode(buffer));}
            finally{buffer.release();}
        }
    }
}
