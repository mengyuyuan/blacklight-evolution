package com.codex.blacklight.client.renderer;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;

/** Resolves the positional sound once, keeping the stock event ID, volume, and subtitles. */
public final class ConsumeFoleyPlayback extends SimpleSoundInstance {
    private static final Map<ResourceLocation,ResourceLocation> previous=new HashMap<>();
    private final ResourceLocation bank;

    private ConsumeFoleyPlayback(SoundEvent event,ResourceLocation bank,Vec3 at,float volume,float pitch){
        super(event,SoundSource.PLAYERS,volume,pitch,SoundInstance.createUnseededRandom(),at.x,at.y,at.z);
        this.bank=bank;
    }

    public static void play(ClientLevel level,SoundEvent event,Vec3 at,Vec3 contact,float volume,float pitch){
        ResourceLocation id=event.getLocation();
        ResourceLocation bank=id;
        if(id.getPath().equals("consume.body_land"))
            bank=new ResourceLocation(id.getNamespace(),"consume.body_land_"+surface(level,contact));
        Minecraft.getInstance().getSoundManager().play(new ConsumeFoleyPlayback(event,bank,at,volume,pitch));
    }

    private static String surface(ClientLevel level,Vec3 contact){
        var block=level.getBlockState(BlockPos.containing(contact.x,contact.y-.025,contact.z));
        var type=block.getSoundType();
        if(type==SoundType.METAL||type==SoundType.ANVIL||type==SoundType.CHAIN||type==SoundType.COPPER
                ||type==SoundType.NETHERITE_BLOCK||type==SoundType.LANTERN)return "metal";
        if(block.is(BlockTags.PLANKS)||block.is(BlockTags.LOGS)||type==SoundType.WOOD||type==SoundType.BAMBOO_WOOD
                ||type==SoundType.NETHER_WOOD||type==SoundType.CHERRY_WOOD||type==SoundType.SCAFFOLDING)return "wood";
        if(block.is(BlockTags.DIRT)||type==SoundType.GRASS||type==SoundType.GRAVEL||type==SoundType.SAND
                ||type==SoundType.SNOW||type==SoundType.WOOL||type==SoundType.MUD||type==SoundType.MOSS)return "soil";
        return "stone";
    }

    @Override public WeighedSoundEvents resolve(SoundManager manager){
        var event=super.resolve(manager);
        if(event==null)return null;
        var choices=bank.equals(getLocation())?event:manager.getSoundEvent(bank);
        if(choices==null||choices.getWeight()==0)return event;
        if(choices!=event)sound=choices.getSound(random);
        var last=previous.get(bank);
        if(sound.getLocation().equals(last)){
            // Default banks contain two or three equally weighted takes. Also respect protected or
            // replacement packs with only one take, event references, or different weights.
            int weight=choices.getWeight(),count=Math.min(64,weight),offset=random.nextInt(weight);
            for(int i=0;i<count;i++){
                int slot=(int)((offset+(long)i*weight/count)%weight);
                var next=choices.getSound(new LegacyRandomSource(random.nextLong()){
                    @Override public int nextInt(int bound){return slot%bound;}
                });
                if(!next.getLocation().equals(last)){sound=next;break;}
            }
        }
        previous.put(bank,sound.getLocation());
        return event;
    }
}
