package com.codex.blacklight.network;
import java.util.WeakHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import com.codex.blacklight.firearms.FirearmItem;

public record FirearmReloadPayload(int hand) implements CustomPacketPayload {
    public static final Type<FirearmReloadPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","firearm_reload"));
    public static final StreamCodec<FriendlyByteBuf,FirearmReloadPayload> STREAM_CODEC=StreamCodec.of((b,p)->b.writeByte(p.hand),b->new FirearmReloadPayload(b.readUnsignedByte()));
    private static final WeakHashMap<ServerPlayer,Long> LAST=new WeakHashMap<>();
    public boolean handle(ServerPlayer player){
        if(player==null||hand<0||hand>1||!player.isAlive()||player.isRemoved()||player.isSpectator())return false;
        var actual=hand==0?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
        if(!(player.getItemInHand(actual).getItem() instanceof FirearmItem gun))return false;
        long now=player.level().getGameTime();Long previous=LAST.get(player);if(previous!=null&&now>=previous&&now-previous<4)return false;
        LAST.put(player,now);return gun.reload(player,actual);
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
