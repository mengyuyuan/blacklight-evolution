package com.codex.blacklight.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** Shared ground-contact plume; 1.20.1 must not replace the 1.21.1 plume with death smoke.
 * Uses the vanilla generic sprites and the same lift, drag and growth on both platforms. */
public final class ContactDustParticle extends BaseAshSmokeParticle {
    private ContactDustParticle(ClientLevel level,double x,double y,double z,
                                double dx,double dy,double dz,SpriteSet sprites) {
        super(level,x,y,z,.7F,.6F,.7F,dx,dy+.15,dz,1F,sprites,.5F,7,.5F,false);
        float shade=random.nextFloat()*.2F;
        setColor(186F/255-shade,178F/255-shade,194F/255-shade);
    }
    @Override public void tick(){gravity*=.88F;friction*=.92F;super.tick();}
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites){this.sprites=sprites;}
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel level,double x,double y,double z,double dx,double dy,double dz){
            return new ContactDustParticle(level,x,y,z,dx,dy,dz,sprites);
        }
    }
}