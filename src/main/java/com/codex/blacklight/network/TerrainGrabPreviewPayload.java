package com.codex.blacklight.network;
import net.minecraft.network.RegistryFriendlyByteBuf;import net.minecraft.network.codec.StreamCodec;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;import net.minecraft.resources.ResourceLocation;import net.minecraft.core.BlockPos;import java.util.*;
public record TerrainGrabPreviewPayload(List<BlockPos> blocks) implements CustomPacketPayload {
 public static final Type<TerrainGrabPreviewPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","terrain_grab_preview"));
 public static final StreamCodec<RegistryFriendlyByteBuf,TerrainGrabPreviewPayload> STREAM_CODEC=new StreamCodec<>(){
  public TerrainGrabPreviewPayload decode(RegistryFriendlyByteBuf b){int count=b.readVarInt();if(count<0||count>27)throw new IllegalArgumentException("terrain preview limit");var list=new ArrayList<BlockPos>();for(int i=0;i<count;i++)list.add(b.readBlockPos());return new TerrainGrabPreviewPayload(List.copyOf(list));}
  public void encode(RegistryFriendlyByteBuf b,TerrainGrabPreviewPayload p){b.writeVarInt(p.blocks.size());for(var pos:p.blocks)b.writeBlockPos(pos);}
 };public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
