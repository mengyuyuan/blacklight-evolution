package com.codex.blacklight.config;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerLevel;

/** Server-owned ecology policy, independent of genetic/evolution limits. */
public final class InfectionEcologyConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue LOCAL_SOURCE, PRESERVE_EXTERNAL, PROTECT_NAMED, PROTECT_PETS, PROTECT_TRADERS, PROTECT_CLAIMS;
    public static final ForgeConfigSpec.IntValue SOURCE_RADIUS, TISSUE_RADIUS, WORLD_CAP, CITY_CAP;
    public static final ForgeConfigSpec.DoubleValue WORLD_SHARE, CITY_SHARE, REWARD_STRENGTH, REWARD_MAX;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IMMUNE, PRESERVE, EXCLUDED_DIMENSIONS;
    public static final ForgeConfigSpec.BooleanValue NATIVE_FALLBACK;
    public static final ForgeConfigSpec.DoubleValue FALLBACK_DAMAGE,FALLBACK_SPEED;
    public static final ForgeConfigSpec.IntValue FALLBACK_RADIUS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FALLBACK_ENTITIES;
    static {
        var b=new ForgeConfigSpec.Builder();
        LOCAL_SOURCE=b.comment("Natural infection and ambient births require a nearby actual infected mob, living colony, hatch, tissue block or recent vial release. Global outbreak time alone is not a source.").define("requireLocalSource",true);
        SOURCE_RADIUS=b.defineInRange("livingSourceRadius",24,1,96);
        TISSUE_RADIUS=b.comment("Bounded loaded-block lookup, never requests chunks.").defineInRange("tissueSourceRadius",4,0,8);
        WORLD_CAP=b.comment("Loaded infected-mob ceiling outside Blacklight cities; includes incubation. Manual infection can exceed it but stops further natural replenishment. No culling.").defineInRange("worldInfectedCap",96,1,4096);
        CITY_CAP=b.defineInRange("cityInfectedCap",128,1,4096);
        WORLD_SHARE=b.comment("Soft regional infected share; reference population is max(16, loaded native+infected mobs in 96 blocks). Also bounded by existing district caps. Does not restrict manual vials or evolution.").defineInRange("worldRegionalShare",.35,.01,1);
        CITY_SHARE=b.defineInRange("cityRegionalShare",.8,.01,1);
        PRESERVE_EXTERNAL=b.comment("Keep foreign mobs as the SAME entity with original AI, UUID, equipment and capability data. Blacklight components add infection/growth without deleting native gameplay.").define("preserveExternalEntities",true);
        PROTECT_CLAIMS=b.comment("Honor installed OPAC claim access for the non-operator natural growth actor. Other protection integrations can cancel NaturalInfectionEvent or use immune tags/persistent protection flags.").define("protectClaims",true);
        PROTECT_NAMED=b.define("protectNamedFromNaturalInfection",true);
        PROTECT_PETS=b.define("protectTamedFromNaturalInfection",true);
        PROTECT_TRADERS=b.define("protectTradersFromNaturalInfection",true);
        IMMUNE=b.comment("Absolute immunity: entity ID, namespace:* or #entity_type_tag. Applies to manual and natural exposure. The blacklight:infection_immune entity tag is always honored.").defineListAllowEmpty("immuneEntities",List.of(),v->v instanceof String);
        PRESERVE=b.comment("Additional IDs/namespace:*/#tags to preserve in place, including selected vanilla species.").defineListAllowEmpty("preserveEntities",List.of(),v->v instanceof String);
        EXCLUDED_DIMENSIONS=b.comment("Disable automatic infection and replenishment in these dimension IDs. Manual exposure remains local and explicit.").defineListAllowEmpty("naturalDisabledDimensions",List.of(),v->v instanceof String);
        REWARD_STRENGTH=b.comment("Logarithmic biomass/EP reward gain from native effective health, attack and armor. No copying foreign realms or spells; kill rewards remain the configured fraction.").defineInRange("threatRewardStrength",1D,0,10);
        REWARD_MAX=b.comment("Maximum threat-based reward multiplier only, NOT a limit on evolution, attributes or generations.").defineInRange("threatRewardMaximum",8D,1,100);
        b.push("nativeAttackFallback");
        NATIVE_FALLBACK=b.comment("Add a cooperative attack goal to otherwise unarmed passive preserved animals. Native targeting, ranged AI, controllers and attributes are not replaced.").define("enabled",true);
        FALLBACK_DAMAGE=b.comment("Base contact damage before existing tissue growth; no generation or evolution cap is added.").defineInRange("baseDamage",4D,0,1000);
        FALLBACK_SPEED=b.defineInRange("navigationSpeed",1D,.1,2);
        FALLBACK_RADIUS=b.defineInRange("targetRadius",12,1,32);
        FALLBACK_ENTITIES=b.comment("Explicit additional IDs/namespace:*/#tags; existing native combat targets still take precedence.").defineListAllowEmpty("additionalEntities",List.of(),v->v instanceof String);
        b.pop();
        SPEC=b.build();
    }
    private InfectionEcologyConfig(){}
    public static <T>T value(ForgeConfigSpec.ConfigValue<T> setting){return SPEC.isLoaded()?setting.get():setting.getDefault();}
    public static boolean matches(LivingEntity mob,List<? extends String> rules){
        var id=BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        for(String raw:rules){
            String rule=raw.trim();
            if(rule.equals(id.toString())||rule.equals(id.getNamespace()+":*"))return true;
            if(rule.startsWith("#")){var tag=ResourceLocation.tryParse(rule.substring(1));if(tag!=null&&mob.getType().is(TagKey.create(Registries.ENTITY_TYPE,tag)))return true;}
        }
        return false;
    }
    public static boolean immune(LivingEntity mob){return mob.getType().is(TagKey.create(Registries.ENTITY_TYPE,new ResourceLocation("blacklight","infection_immune")))||matches(mob,value(IMMUNE));}
    public static boolean naturalDimension(ServerLevel level){return !value(EXCLUDED_DIMENSIONS).contains(level.dimension().location().toString());}
    public static int dimensionCap(ServerLevel level){return Math.min(com.codex.blacklight.infection.OutbreakConfig.DIMENSION_CAP.get(),value(com.codex.blacklight.city.CityTravel.isCity(level.dimension())?CITY_CAP:WORLD_CAP));}
    public static int regionalCap(ServerLevel level,int population,int districtCap){return Math.min(districtCap,Math.max(1,(int)Math.floor(Math.max(16,population)*value(com.codex.blacklight.city.CityTravel.isCity(level.dimension())?CITY_SHARE:WORLD_SHARE))));}
}
