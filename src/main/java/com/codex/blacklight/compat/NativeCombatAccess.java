package com.codex.blacklight.compat;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;

/** Common health pools plus optional mechanism providers. Registration is bounded,
 * snapshots contain values only, and unknown mechanics never force-set health. */
public final class NativeCombatAccess {
    private static volatile List<DamageMechanismAdapter> adapters=List.of(new DamageMechanismAdapter(){
        public ResourceLocation id(){return new ResourceLocation("blacklight", "cultivation");}
        public boolean supports(LivingEntity entity){return CultivationCompatibility.creature(entity);}
        public OptionalDouble reserve(LivingEntity entity){return CultivationCompatibility.reserve(entity);}
        public boolean bypassNpcShield(LivingEntity entity,DamageSource source){return CultivationCompatibility.bypassNpcShield(entity,source);}
    });
    private NativeCombatAccess() {}
    public static synchronized void register(DamageMechanismAdapter adapter){
        Objects.requireNonNull(adapter);Objects.requireNonNull(adapter.id());
        if(adapters.size()>=32||adapters.stream().anyMatch(a->a.id().equals(adapter.id())))
            throw new IllegalArgumentException("Duplicate or excessive native combat adapter: "+adapter.id());
        var next=new ArrayList<>(adapters);next.add(adapter);adapters=List.copyOf(next);
    }
    public record Pools(float health,float absorption,Map<ResourceLocation,Double> reserves){}
    public static Pools read(LivingEntity entity,boolean reserves){
        Map<ResourceLocation,Double> values=new LinkedHashMap<>();
        if(reserves)for(var adapter:adapters)if(adapter.supports(entity)){
            var value=adapter.reserve(entity);
            if(value.isPresent()&&Double.isFinite(value.getAsDouble())&&value.getAsDouble()>=0)values.put(adapter.id(),value.getAsDouble());
        }
        return new Pools(entity.getHealth(),entity.getAbsorptionAmount(),Map.copyOf(values));
    }
    public static boolean bypassNpcShield(LivingEntity entity,DamageSource source){
        if(entity==null||source==null||entity instanceof Player||entity.isRemoved()||entity.level().isClientSide)return false;
        for(var adapter:adapters)if(adapter.supports(entity)&&adapter.bypassNpcShield(entity,source))return true;
        return false;
    }
    public static boolean reserveSpent(Pools before,Pools after){
        return before.reserves().entrySet().stream().anyMatch(e->after.reserves().containsKey(e.getKey())&&after.reserves().get(e.getKey())<e.getValue());
    }
}
