package com.codex.blacklight.power;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;
/** Shared server/client form size and integrity policy. Age is persisted, never reset by relogging. */
public final class BiomassEvolution {
    private BiomassEvolution(){}
    public static int lifetime(Player p){var g=ModAttachments.getData(p,ModAttachments.GROWTH);
        return g.owns(GrowthUpgrade.SLIME_DECAY_III)?-1:g.owns(GrowthUpgrade.SLIME_DECAY_II)?2400:g.owns(GrowthUpgrade.SLIME_DECAY_I)?1500:900;}
    public static float integrity(Player p){int life=lifetime(p);return life<0?1F:Math.max(0,1F-BiomassSystem.state(p).age()/(float)life);}
    public static float scale(Player p){var g=ModAttachments.getData(p,ModAttachments.GROWTH);
        float size=g.owns(GrowthUpgrade.SLIME_SIZE_III)?1.8F:g.owns(GrowthUpgrade.SLIME_SIZE_II)?1.5F:g.owns(GrowthUpgrade.SLIME_SIZE_I)?1.25F:1F;
        // Discrete collision-size bands avoid resizing the player every tick.
        return size*(.55F+.45F*(float)Math.ceil(integrity(p)*20)/20F);}
    public static float width(Player p){return .8F*scale(p);}
    public static float height(Player p){return .3F*scale(p);}
}
