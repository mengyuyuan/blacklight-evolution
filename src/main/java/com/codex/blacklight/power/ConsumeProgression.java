package com.codex.blacklight.power;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.GrowthProgress;
import net.minecraft.world.entity.player.Player;
/** The same server-owned upgrade gates human and liquid consumption. */
public final class ConsumeProgression {
    public static float healthLimit(GrowthProgress progress){return progress.owns(GrowthUpgrade.ASSIMILATION_II)?.25F:progress.owns(GrowthUpgrade.ASSIMILATION)?.20F:.15F;}
    public static int absorbTicks(GrowthProgress progress){return progress.owns(GrowthUpgrade.ASSIMILATION_II)?36:progress.owns(GrowthUpgrade.ASSIMILATION)?48:60;}
    public static float healingMultiplier(GrowthProgress progress){return progress.owns(GrowthUpgrade.ASSIMILATION_II)?1.7F:progress.owns(GrowthUpgrade.ASSIMILATION)?1.35F:1;}
    public static float crawlSpeed(GrowthProgress progress){return progress.owns(GrowthUpgrade.SLIME_MOBILITY_III)?.20F:progress.owns(GrowthUpgrade.SLIME_MOBILITY_II)?.16F:progress.owns(GrowthUpgrade.SLIME_MOBILITY_I)?.12F:.085F;}
    public static GrowthProgress progress(Player p){return ModAttachments.getData(p,ModAttachments.GROWTH);}
    public static int absorbTicks(Player p){return absorbTicks(progress(p));}
    public static float crawlSpeed(Player p){return crawlSpeed(progress(p));}
    private ConsumeProgression(){}
}
