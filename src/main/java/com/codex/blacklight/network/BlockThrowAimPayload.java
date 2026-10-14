package com.codex.blacklight.network;
import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.StreamCodec;
import com.codex.blacklight.forge.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** View intent only. Range, targets, charge and launch velocity are decided by the server. */
public record BlockThrowAimPayload(double x,double y,double z,boolean assist) implements CustomPacketPayload {
 public static final Type<BlockThrowAimPayload> TYPE=new Type<>(new ResourceLocation("blacklight","block_throw_aim"));
 public static final StreamCodec<FriendlyByteBuf,BlockThrowAimPayload> STREAM_CODEC=new StreamCodec<>(){
  public BlockThrowAimPayload decode(FriendlyByteBuf b){return new BlockThrowAimPayload(b.readDouble(),b.readDouble(),b.readDouble(),b.readBoolean());}
  public void encode(FriendlyByteBuf b,BlockThrowAimPayload p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeBoolean(p.assist);}
 };
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
