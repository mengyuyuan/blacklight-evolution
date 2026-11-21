package com.codex.blacklight.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/** A single sprite emitter at a confirmed hammer impact. Durations are Minecraft ticks. */
public final class HammerImpactParticle extends TextureSheetParticle {
    public enum Layer {
        WAVE(10,.55F,.08F), FIRE(7,1.05F,.05F), DUST(17,.7F,.05F),
        DIRT(12,.58F,-.025F), DEBRIS(11,.32F,-.035F);
        final int ticks;
        final float size, lift;
        Layer(int ticks,float size,float lift) { this.ticks=ticks; this.size=size; this.lift=lift; }
    }
    private final Layer layer;
    private final SpriteSet frames;
    private final float startSize;
    private final int delay;

    public HammerImpactParticle(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,
                                SpriteSet frames,Layer layer) {
        super(level,x,y,z);
        this.layer=layer;
        this.frames=frames;
        // Fire is sent singly. Its X argument encodes the source's zero/one-tick onset.
        delay=layer==Layer.WAVE?1:layer==Layer.FIRE?(int)dx:0;
        this.xd=layer==Layer.FIRE?0:dx;
        this.yd=(layer==Layer.FIRE?0:dy)+Math.max(0,layer.lift);
        this.zd=layer==Layer.FIRE?0:dz;
        hasPhysics=false;
        lifetime=layer.ticks+delay;
        startSize=layer.size*(.9F+.2F*random.nextFloat());
        quadSize=startSize;
        alpha=delay>0?0:1;
        setSprite(frames.get(0,1));
    }
    @Override public void tick() {
        xo=x; yo=y; zo=z;
        if (++age>=lifetime) { remove(); return; }
        int elapsed=age-delay;
        if (elapsed<0) return;
        float t=elapsed/(float)layer.ticks;
        setSprite(frames.get(elapsed,layer.ticks-1));
        move(xd,yd,zd);
        xd*=.90; zd*=.90;
        yd=yd*.9+Math.min(0,layer.lift);
        quadSize=startSize*(1+.6F*t);
        alpha=(1-t*t)*(layer==Layer.WAVE?.7F:1F);
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    @Override public int getLightColor(float partial) {
        return layer==Layer.FIRE?0xf000f0:super.getLightColor(partial);
    }
}
