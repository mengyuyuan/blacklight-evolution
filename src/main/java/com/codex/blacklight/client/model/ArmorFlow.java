package com.codex.blacklight.client.model;

import com.codex.blacklight.client.renderer.VisionPowerRenderer;
import com.codex.blacklight.item.SymbioteCharm;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Vector3f;

/** Moving ink in the armour's rest coordinates, so each marking stays on its plate through a pose. */
final class ArmorFlow {
    static final ResourceLocation TEXTURE = new ResourceLocation("blacklight", "textures/entity/symbiote_armor_flow.png");

    static boolean visible(AbstractClientPlayer player, EquipmentSlot slot) {
        return !VisionPowerRenderer.drawingSilhouettes() && SymbioteCharm.wearing(player, slot);
    }

    static float phase(AbstractClientPlayer player, float partial) {
        return (float)(((player.tickCount + (double)partial) * .009
                + (player.getUUID().hashCode() & 1023) / 1024.0) % 1.0);
    }

    static float u(Vector3f rest, float phase) { return (rest.x + .68F*rest.z)*2.4F + phase; }
    static float v(Vector3f rest, float phase) { return (rest.y - .45F*rest.z)*2.2F - phase; }

    private ArmorFlow() {}
}
