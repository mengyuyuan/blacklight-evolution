package com.codex.blacklight.infection;

import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.FriendlyByteBuf;

/** Presentation-only NBT: a byte ceiling and a separate decoded heap ceiling.
 * NBT's heap accounting is larger than its encoded bytes, especially for organ maps. */
public final class NativeTissueCodec {
    public static final int MAX_BYTES=65_536,MAX_HEAP=262_144;
    private NativeTissueCodec() {}
    public static final StreamCodec<FriendlyByteBuf,CompoundTag> STREAM_CODEC=StreamCodec.of(
            NativeTissueCodec::encode,NativeTissueCodec::decode);
    private static void encode(FriendlyByteBuf out,CompoundTag value){
        var encoded=new FriendlyByteBuf(Unpooled.buffer(256,MAX_BYTES+1));
        try{
            encoded.writeNbt(value);
            if(encoded.readableBytes()>MAX_BYTES)throw new EncoderException("Native tissue projection exceeds byte budget");
            out.writeVarInt(encoded.readableBytes());out.writeBytes(encoded);
        }catch(IndexOutOfBoundsException tooLarge){throw new EncoderException("Native tissue projection exceeds byte budget",tooLarge);}
        finally{encoded.release();}
    }
    private static CompoundTag decode(FriendlyByteBuf in){
        int length=in.readVarInt();
        if(length<1||length>MAX_BYTES||length>in.readableBytes())throw new DecoderException("Invalid native tissue projection length");
        var slice=new FriendlyByteBuf(in.readSlice(length));
        var tag=slice.readNbt(NbtAccounter.create(MAX_HEAP));
        if(slice.isReadable())throw new DecoderException("Trailing native tissue projection bytes");
        if(tag!=null&&tag.getId()!=net.minecraft.nbt.Tag.TAG_COMPOUND)throw new DecoderException("Native tissue root must be a compound");
        return tag==null?new CompoundTag():(CompoundTag)tag;
    }
}
