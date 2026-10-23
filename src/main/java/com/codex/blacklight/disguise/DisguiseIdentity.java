package com.codex.blacklight.disguise;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.npc.AbstractVillager;

/** Appearance only: never copy UUID, AI, passengers, trades, health or inventory ownership. */
public record DisguiseIdentity(String type, String family, CompoundTag appearance) {
    public static final DisguiseIdentity EMPTY=new DisguiseIdentity("","",new CompoundTag());
    public static final Codec<DisguiseIdentity> CODEC=RecordCodecBuilder.create(i->i.group(
            boundedString(128).fieldOf("type").forGetter(DisguiseIdentity::type),
            boundedString(32).fieldOf("family").forGetter(DisguiseIdentity::family),
            CompoundTag.CODEC.fieldOf("appearance").forGetter(DisguiseIdentity::appearance)
    ).apply(i,DisguiseIdentity::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,DisguiseIdentity> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,DisguiseIdentity::type,ByteBufCodecs.STRING_UTF8,DisguiseIdentity::family,
            ByteBufCodecs.COMPOUND_TAG,DisguiseIdentity::appearance,DisguiseIdentity::new);
    private static Codec<String> boundedString(int max) {
        return Codec.STRING.flatXmap(s->s.length()<=max&&s.chars().allMatch(c->c<128)?com.mojang.serialization.DataResult.success(s):com.mojang.serialization.DataResult.error(()->"Identity string too long"),
                s->s.length()<=max&&s.chars().allMatch(c->c<128)?com.mojang.serialization.DataResult.success(s):com.mojang.serialization.DataResult.error(()->"Identity string too long"));
    }
    private static final String[] APPEARANCE_KEYS={"Age","IsBaby","Baby","Variant","variant","Color","Sheared",
            "CatType","CollarColor","Tame","RabbitType","Type","Size","VillagerData",
            "HandItems","ArmorItems","CustomName","CustomNameVisible","Profession",
            "Pumpkin","player_created","carriedBlockState","HasChest","MainGene","HiddenGene",
            "MooshroomType","Strength","DecorItem","SaddleItem","Saddle","ArmorItem","BodyArmorItem"};
    public EntityType<?> entityType() {
        var id=ResourceLocation.tryParse(type);
        return id==null?null:BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }
    public boolean valid(){return entityType()!=null&&!type.isBlank();}
    /** Bound collection packets even for command-created mobs carrying huge cosmetic item NBT. */
    public DisguiseIdentity compact() {
        if(type.length()>128||family.length()>32||type.chars().anyMatch(c->c>=128)||family.chars().anyMatch(c->c>=128))return EMPTY;
        var tag=appearance.copy();
        if(!fits(tag)) {
            for(String key:new String[]{"HandItems","ArmorItems","CustomName","CustomNameVisible","DecorItem","ArmorItem","BodyArmorItem"})tag.remove(key);
            if(!fits(tag))tag=new CompoundTag();
        }
        return new DisguiseIdentity(type,family,tag);
    }
    private static boolean fits(CompoundTag tag) {
        // NbtIo substitutes an empty value for overlong UTF strings. Reject them before
        // measuring bytes, otherwise a huge in-memory cosmetic could appear to be tiny.
        if(tag.sizeInBytes()>4096)return false;
        try {
            var bytes=new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.write(tag,new java.io.DataOutputStream(bytes));
            return bytes.size()<=512;
        }catch(java.io.IOException|RuntimeException error){return false;}
    }
    public static DisguiseIdentity capture(Mob mob) {
        var id=BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        // Bosses, giants and custom infected bodies are not civilian identities.
        if((!id.getNamespace().equals("minecraft")&&!(mob instanceof com.codex.blacklight.entity.BlackwatchSoldier))||mob.getBbHeight()>3||mob.getBbWidth()>2.5
                ||mob.getType()==EntityType.WITHER||mob.getType()==EntityType.ENDER_DRAGON
                ||mob.getType()==EntityType.WARDEN||mob.getType()==EntityType.IRON_GOLEM
                ||mob.getType()==EntityType.RAVAGER)return EMPTY;
        var source=mob.saveWithoutId(new CompoundTag());var appearance=new CompoundTag();
        for(var key:APPEARANCE_KEYS)if(source.contains(key))appearance.put(key,source.get(key).copy());
        // Pets cannot be consumed. Owner is not needed to select a texture and must never be cloned.
        if(mob instanceof net.minecraft.world.entity.animal.Sheep sheep){
            appearance.putByte("Color",(byte)sheep.getColor().getId());appearance.putBoolean("Sheared",sheep.isSheared());
        }
        String family=mob instanceof com.codex.blacklight.entity.BlackwatchSoldier?"blackwatch":mob instanceof Zombie||mob instanceof AbstractSkeleton?"undead":
                mob instanceof AbstractIllager?"illager":mob instanceof AbstractVillager?"civilian":
                mob instanceof Animal?"animal":"other";
        return new DisguiseIdentity(id.toString(),family,appearance);
    }
}
