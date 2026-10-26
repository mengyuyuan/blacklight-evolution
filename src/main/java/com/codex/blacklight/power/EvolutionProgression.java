package com.codex.blacklight.power;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.*;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Readable on both sides; only the server may grant saved qualifications. */
public final class EvolutionProgression {
    public static final List<EvolutionAttribute> CORE_ATTRIBUTES=List.of(EvolutionAttribute.VITALITY,EvolutionAttribute.STRENGTH,
            EvolutionAttribute.SKILL_POWER,EvolutionAttribute.RESILIENCE,EvolutionAttribute.RECOVERY,EvolutionAttribute.MOBILITY);
    private EvolutionProgression(){}
    public static List<BaseEvolutionCatalog.Requirement> missing(Player p){return BaseEvolutionCatalog.missing(p.getData(ModAttachments.PLAYER_STATE),p.getData(ModAttachments.GROWTH));}
    public static boolean infiniteUnlocked(Player p){
        return p.getData(ModAttachments.PLAYER_STATE).isInfected()&&(p.getData(ModAttachments.EVOLUTION_MILESTONES).has(EvolutionMilestones.INFINITE)
                ||BaseEvolutionCatalog.complete(p.getData(ModAttachments.PLAYER_STATE),p.getData(ModAttachments.GROWTH)));
    }
    public static boolean everyAttribute(InfiniteProgress progress,int required){return CORE_ATTRIBUTES.stream().allMatch(a->progress.rank(a)>=required);}
    public static int required(CombatSkill skill){return skill==CombatSkill.ADAPTATION_PERCENT?Math.max(PlayerEvolutionConfig.PASSIVE_LEVEL.get(),PlayerEvolutionConfig.PERCENT_LEVEL.get()):PlayerEvolutionConfig.PASSIVE_LEVEL.get();}
    public static boolean canLearn(Player p,CombatSkill skill){
        if(!skill.adaptivePassive())return true;
        int milestone=skill==CombatSkill.ADAPTATION_PERCENT?EvolutionMilestones.PERCENT:EvolutionMilestones.PASSIVES;
        return p.getData(ModAttachments.PLAYER_STATE).isInfected()&&(p.getData(ModAttachments.EVOLUTION_MILESTONES).has(milestone)
                ||infiniteUnlocked(p)&&everyAttribute(p.getData(ModAttachments.INFINITE_EVOLUTION),required(skill)));
    }
    public static boolean mayActivate(Player p,CombatSkill skill){return canLearn(p,skill)
            ||PlayerEvolutionConfig.KEEP_LEGACY_PASSIVES.get()&&p.getData(ModAttachments.PLAYER_STATE).hasCombatSkill(skill);}
    public static void refresh(ServerPlayer p){
        if(!p.getData(ModAttachments.PLAYER_STATE).isInfected())return;
        var before=p.getData(ModAttachments.EVOLUTION_MILESTONES);var after=before;
        if(infiniteUnlocked(p)){
            after=after.grant(EvolutionMilestones.INFINITE);var ranks=p.getData(ModAttachments.INFINITE_EVOLUTION);
            if(everyAttribute(ranks,required(CombatSkill.ADAPTATION)))after=after.grant(EvolutionMilestones.PASSIVES);
            if(everyAttribute(ranks,required(CombatSkill.ADAPTATION_PERCENT)))after=after.grant(EvolutionMilestones.PERCENT);
        }
        if(!after.equals(before)){p.setData(ModAttachments.EVOLUTION_MILESTONES,after);p.syncData(ModAttachments.EVOLUTION_MILESTONES);}
    }
}
