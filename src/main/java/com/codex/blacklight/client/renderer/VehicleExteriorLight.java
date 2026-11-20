package com.codex.blacklight.client.renderer;

import net.minecraft.client.renderer.LightTexture;

final class VehicleExteriorLight {
    private VehicleExteriorLight() {}

    static int combine(int first,int second){
        return LightTexture.pack(Math.max(LightTexture.block(first),LightTexture.block(second)),
                Math.max(LightTexture.sky(first),LightTexture.sky(second)));
    }
}
