package com.codex.blacklight.infection;

import com.codex.blacklight.combat.ConsumeSequence;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModAttachments;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;

/** Short-lived access to the same development rules on either host representation.
 * Do not cache this handle. Capture a value-only Donor before a death callback. */
public final class TissueSubject {
    public record Donor(TissueInheritance inheritance,long seed,long age,int plan,
                        TissueCore core,String species,String appearance) {}
    private final Mob mob;
    private final BlacklightInfectedHost host;
    private TissueSubject(Mob mob){this.mob=mob;host=mob instanceof BlacklightInfectedHost h?h:null;}
    public static TissueSubject of(Mob mob){
        if(!ModAttachments.available(mob)||!mob.isAlive())return null;
        return mob instanceof BlacklightInfectedHost||NativeInfection.active(mob)?new TissueSubject(mob):null;
    }
    public Mob mob(){return mob;}
    public boolean valid(){return ModAttachments.available(mob)&&mob.isAlive()&&(host!=null||NativeInfection.active(mob));}
    public long age(){return !valid()?0:host!=null?host.tissueAge():NativeInfection.age(mob);}
    public long seed(){return TissueOrgans.seed(mob.getUUID());}
    public TissueCore core(){return !valid()?null:host!=null?host.knownNativeTissueCore():NativeTissue.knownCore(mob);}
    public TissueInheritance inheritance(){return !valid()?TissueInheritance.EMPTY:host!=null?host.tissueInheritance():NativeTissue.inheritance(mob);}
    public String species(){return !valid()?"":host!=null?host.speciesId():BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();}
    public int plan(){
        if(!valid())return -1;if(host!=null)return host.nativeTissuePlan();
        if(ConsumeSequence.humanoid(mob))return 0;
        if(ConsumeSequence.quadruped(mob)||mob instanceof net.minecraft.world.entity.animal.WaterAnimal
                ||mob instanceof FlyingMob||mob instanceof net.minecraft.world.entity.animal.FlyingAnimal)return 1;
        return mob.getBbWidth()>mob.getBbHeight()*1.3F?2:mob.getBbHeight()>mob.getBbWidth()*1.8F?0:1;
    }
    public boolean evolved(){return host!=null&&valid()&&host.isEvolved();}
    public boolean competitionEligible(){
        if(!valid())return false;
        if(host!=null)return !host.hasMultipartBody()&&!host.devourerIdentity()&&!DevourerFeedingSystem.isDevourer(host);
        return !mob.isMultipartEntity()&&!(mob instanceof OwnableEntity owned&&owned.getOwnerUUID()!=null);
    }
    public double power(TissueCore organ){
        if(!valid()||organ==null)return 0;if(host!=null)return host.organPower(organ);
        var genes=inheritance();var primary=core();long age=age();
        return genes.maturity(organ,primary,age)<1?0:Math.log1p(genes.strength(organ,primary,age)/100)/Math.log(2);
    }
    public boolean spend(int amount){return valid()&&!mob.level().isClientSide&&(host!=null?host.spendTissueNutrition(amount):NativeTissue.spend(mob,amount));}
    public Donor donor(){
        if(!valid())return null;
        return new Donor(inheritance(),seed(),age(),plan(),core(),species(),host==null?"":host.hostData().toString());
    }
    public boolean inherit(UUID operation,Donor donor){
        if(!valid()||mob.level().isClientSide||donor==null)return false;
        if(host==null)return NativeTissue.inherit(mob,operation,donor.inheritance(),donor.seed(),donor.age(),
                donor.plan(),donor.core(),donor.species(),donor.appearance());
        var before=host.tissueInheritance();
        host.inheritTissue(operation,donor.inheritance(),donor.seed(),donor.age(),donor.plan(),donor.core(),donor.species(),donor.appearance());
        return host.tissueInheritance()!=before;
    }
    public void scene(int target,long started,boolean prey,int duration){
        if(!ModAttachments.available(mob)||mob.level().isClientSide)return;
        if(host!=null)host.setCannibalVisual(target,started,prey,duration);else NativeTissue.scene(mob,target,started,prey,duration);
    }
}
