package com.codex.blacklight.disguise;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Transient server clock. Identity is committed halfway through the biomass cover. */
public record DisguiseMorph(long started, boolean entering) {
    public static final int TICKS=16,CONTACT=8;
    public static final DisguiseMorph EMPTY=new DisguiseMorph(-1,false);
    public static final StreamCodec<RegistryFriendlyByteBuf,DisguiseMorph> CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,DisguiseMorph::started,ByteBufCodecs.BOOL,DisguiseMorph::entering,DisguiseMorph::new);
    public boolean active(long now){return started>=0&&now>=started&&now-started<TICKS;}
    public float progress(long now,float partial){return Math.clamp((now-started+partial)/TICKS,0,1);}
}
