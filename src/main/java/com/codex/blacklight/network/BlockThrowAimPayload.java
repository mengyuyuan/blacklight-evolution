package com.codex.blacklight.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** View intent only. Range, targets, charge and launch velocity are decided by the server. */
public record BlockThrowAimPayload(double x,double y,double z,boolean assist) implements CustomPacketPayload {
 public static final Type<BlockThrowAimPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","block_throw_aim"));
 public static final StreamCodec<RegistryFriendlyByteBuf,BlockThrowAimPayload> STREAM_CODEC=new StreamCodec<>(){
  public BlockThrowAimPayload decode(RegistryFriendlyByteBuf b){return new BlockThrowAimPayload(b.readDouble(),b.readDouble(),b.readDouble(),b.readBoolean());}
  public void encode(RegistryFriendlyByteBuf b,BlockThrowAimPayload p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeBoolean(p.assist);}
 };
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
