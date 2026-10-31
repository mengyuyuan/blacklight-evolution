package com.codex.blacklight.firearms;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-owned controls for structural firearm effects. */
public final class FirearmsWorldConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BULLETS,EXPLOSIVES,PROTECT_BLOCK_ENTITIES;
    public static final ForgeConfigSpec.IntValue BLAST_BLOCK_LIMIT;
    static{
        var b=new ForgeConfigSpec.Builder();
        BULLETS=b.comment("Material-based bullet damage. Protected block tags and player break permissions apply.").define("bulletBlockDamage",true);
        EXPLOSIVES=b.comment("Bounded local ordnance damage. NPC damage also obeys mobGriefing and entity-destroy hooks.").define("explosiveBlockDamage",true);
        PROTECT_BLOCK_ENTITIES=b.comment("Keep inventory and functional block entities intact by default.").define("protectBlockEntities",true);
        BLAST_BLOCK_LIMIT=b.comment("Maximum eligible blocks receiving damage from one detonation.").defineInRange("maxBlocksPerBlast",48,1,128);
        SPEC=b.build();
    }
    private FirearmsWorldConfig(){}
}
