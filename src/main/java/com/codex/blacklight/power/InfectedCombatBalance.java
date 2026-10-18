package com.codex.blacklight.power;

import com.codex.blacklight.entity.BlacklightHunter;
import com.codex.blacklight.combat.HunterBalance;
import com.codex.blacklight.entity.BlacklightMutant;
import com.codex.blacklight.entity.BlacklightHive;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Threat budgets for an infected 100-HP protagonist, independent of health growth.
 * Adjust original damage units before shield, armor and CombatScale's conversion.
 */
@EventBusSubscriber(modid="blacklight")
public final class InfectedCombatBalance {
    private InfectedCombatBalance() {}
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void incoming(LivingIncomingDamageEvent event){
        if(event.getEntity().level().isClientSide||event.isCanceled()||event.getAmount()<=0)return;
        var source=event.getSource();
        if(event.getEntity() instanceof BlacklightHunter && source.getEntity() instanceof Player
                && !source.is(com.codex.blacklight.combat.BulletdiveDamage.TYPE)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            event.setAmount(event.getAmount()*HunterBalance.PLAYER_ATTACK_FACTOR);
        var hunter=source.getEntity() instanceof BlacklightHunter h&&source.getDirectEntity()==h?h:null;
        if(hunter!=null&&!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            event.setAmount(event.getAmount()*com.codex.blacklight.combat.HunterBalance.damageMultiplier(hunter.attackMove()));
        if(hunter!=null && event.getEntity() instanceof Player ordinary
                && !ordinary.getData(ModAttachments.PLAYER_STATE).isInfected()
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            event.setAmount(event.getAmount()*HunterBalance.ORDINARY_PLAYER_HURT_FACTOR);
        if(!(event.getEntity() instanceof Player player)
                ||!player.getData(ModAttachments.PLAYER_STATE).isInfected()
                ||event.getAmount()<=0||event.isCanceled())return;
        // Projectile DamageSources retain their mob owner. Player attacks, void,
        // commands and environmental damage do not acquire a creature budget.
        if(!(source.getEntity() instanceof Mob attacker)
                ||source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        var type=attacker.getType();
        boolean boss=attacker instanceof BlacklightHive
                ||attacker instanceof com.codex.blacklight.entity.BlacklightInfectedHost host
                    &&(host.giantBoss()||host.encounterBoss()||host.speciesId().equals("minecraft:warden"))
                ||type==EntityType.WARDEN
                ||type==EntityType.WITHER||type==EntityType.ENDER_DRAGON;
        boolean elite=attacker instanceof BlacklightHunter||attacker instanceof BlacklightMutant
                ||attacker instanceof com.codex.blacklight.entity.BlacklightInfectedHost host
                    &&java.util.List.of("minecraft:ravager","minecraft:iron_golem","minecraft:piglin_brute","minecraft:elder_guardian").contains(host.speciesId())
                ||type==EntityType.RAVAGER||type==EntityType.IRON_GOLEM||type==EntityType.PIGLIN_BRUTE
                ||type==EntityType.ELDER_GUARDIAN;
        // Explosions stay dangerous, while ordinary claws/arrows are chip damage.
        float factor=boss?.50F:elite?.40F:.30F;
        float budget=boss?40:elite?22:source.is(DamageTypeTags.IS_EXPLOSION)?28:10;
        float difficulty=switch(player.level().getDifficulty()){
            case EASY -> .75F;case HARD -> 1.5F;default -> 1;
        };
        if(hunter!=null){
            event.setAmount(com.codex.blacklight.combat.HunterBalance.infectedDamage(event.getAmount(),hunter.attackMove(),difficulty));
            return;
        }
        // Cap in the old units; the later conversion occurs exactly once. Both
        // shield consumption and armored HP loss see the same balanced attack.
        event.setAmount(Math.min(event.getAmount()*factor,budget*difficulty/CombatScale.UNITS));
    }
}
