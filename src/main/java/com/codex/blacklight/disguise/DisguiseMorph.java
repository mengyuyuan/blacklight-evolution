package com.codex.blacklight.disguise;

import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.ByteBufCodecs;
import com.codex.blacklight.forge.codec.StreamCodec;

/** Transient server clock. Identity is committed halfway through the biomass cover. */
public record DisguiseMorph(long started, boolean entering) {
    public static final int TICKS=16,CONTACT=8;
    public static final DisguiseMorph EMPTY=new DisguiseMorph(-1,false);
    public static final StreamCodec<FriendlyByteBuf,DisguiseMorph> CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,DisguiseMorph::started,ByteBufCodecs.BOOL,DisguiseMorph::entering,DisguiseMorph::new);
    public boolean active(long now){return started>=0&&now>=started&&now-started<TICKS;}
    public float progress(long now,float partial){return com.codex.blacklight.util.MathCompat.clamp((now-started+partial)/TICKS,0,1);}
}
