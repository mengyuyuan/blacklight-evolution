package com.codex.blacklight.gametest;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.state.BlacklightState;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class DevastatorTrueDamageGameTest {
    private static ServerPlayer caster(GameTestHelper h){
        var profile=new GameProfile(java.util.UUID.randomUUID(),"DevastatorQA");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault());p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;
        p.setGameMode(GameType.SURVIVAL);var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.activate(PowerForm.CLAWS);state.addEvolutionPoints(10000);
        for(var skill:new CombatSkill[]{CombatSkill.CRITICAL_MASS,CombatSkill.GROUNDSPIKE_GRAVEYARD,CombatSkill.TENDRIL_BARRAGE,CombatSkill.CRITICAL_PAIN})state.buyCombatSkill(skill);
        p.setData(ModAttachments.PLAYER_STATE,state);GrowthSystem.updateHealth(p);p.setHealth(p.getMaxHealth());p.setData(ModAttachments.CRITICAL_MASS,new CriticalMassState(1,false,-1000));return p;
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void trueDamageIgnoresHunterArmorResistanceAndItsPlayerPenalty(GameTestHelper h){
        var p=caster(h);var hunter=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());hunter.setNoAi(true);hunter.setPos(h.absolutePos(new BlockPos(4,4,4)).getCenter());h.getLevel().addFreshEntity(hunter);hunter.setHealth(hunter.getMaxHealth());
        try{
            hunter.getAttribute(Attributes.ARMOR).setBaseValue(40);hunter.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(30);hunter.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,200,4));
            var source=DevastatorDamage.source(p);
            for(var tag:new net.minecraft.tags.TagKey[]{DamageTypeTags.BYPASSES_ARMOR,DamageTypeTags.BYPASSES_RESISTANCE,DamageTypeTags.BYPASSES_ENCHANTMENTS,DamageTypeTags.BYPASSES_SHIELD,DamageTypeTags.BYPASSES_EFFECTS,DamageTypeTags.BYPASSES_COOLDOWN})
                h.assertTrue(source.is(tag),"real registered Devastator damage tag exists: "+tag);
            h.assertTrue(!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY),"true damage never grants administrative invulnerability bypass");
            h.assertTrue(DevastatorDamage.hurt(p,hunter,2000),"armored resistant hunter accepts the real hurt call");
            h.assertTrue(Math.abs(hunter.getHealth()-500)<.01,"2500 health becomes 500, without armor/resistance/25% penalty; actual="+hunter.getHealth());
            h.assertTrue(DevastatorDamage.hurt(p,hunter,4000)&&hunter.isDeadOrDying(),"lethal hit uses ordinary death processing");
            h.assertTrue(hunter.getKillCredit()==p,"kill credit belongs to the casting player");
        }finally{hunter.discard();p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void unscaledTargetsLoseTheSameActualHpAndOrdinaryAttacksStayOrdinary(GameTestHelper h){
        var p=caster(h);var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(8000);cow.setHealth(8000);cow.getPersistentData().putLong(com.codex.blacklight.infection.InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
        cow.setPos(h.absolutePos(new BlockPos(4,4,4)).getCenter());h.getLevel().addFreshEntity(cow);
        try{
            h.assertTrue(CombatScale.units(cow)==1,"fixture is an ordinary unscaled creature");
            h.assertTrue(DevastatorDamage.hurt(p,cow,4000)&&Math.abs(cow.getHealth()-4000)<.01,"unscaled target also loses exactly 4000 health");
            var normal=p.damageSources().playerAttack(p);h.assertTrue(!DevastatorDamage.isTrueDamage(normal)&&!normal.is(DamageTypeTags.BYPASSES_ARMOR),"ordinary attacks are not upgraded to true damage");
            cow.setInvulnerable(true);h.assertTrue(!DevastatorDamage.hurt(p,cow,4000)&&cow.getHealth()==4000,"explicit invulnerability is respected");
        }finally{cow.discard();p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=340,batch="true_dev_ground")
    public static void fullGroundspikeInputAnimationAndContactUseTrueDamage(GameTestHelper h){actualSkill(h,0);}
    @GameTest(template="wall_test",timeoutTicks=340,batch="true_dev_tendril")
    public static void fullTendrilInputAnimationAndContactUseTrueDamage(GameTestHelper h){actualSkill(h,1);}
    @GameTest(template="wall_test",timeoutTicks=340,batch="true_dev_pain")
    public static void fullCriticalPainInputAnimationAndContactUseTrueDamage(GameTestHelper h){actualSkill(h,2);}
    private static void actualSkill(GameTestHelper h,int kind){
        var p=caster(h);
        var tickets=new java.util.HashSet<ChunkPos>();
        var min=h.absolutePos(new BlockPos(0,119,0));var max=h.absolutePos(new BlockPos(39,119,7));
        for(int x=min.getX()>>4;x<=max.getX()>>4;x++)for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++){
            var chunk=new ChunkPos(x,z);
            if(!h.getLevel().getForcedChunks().contains(chunk.toLong())){h.getLevel().setChunkForced(x,z,true);tickets.add(chunk);}
            h.getLevel().getChunk(x,z);
        }
        // The 28-block contact lane extends beyond the shared template's clear box.
        // Isolate it above other fixtures and remove old walls before casting.
        for(int x=0;x<40;x++)for(int z=0;z<8;z++){
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x,119,z)),Blocks.STONE.defaultBlockState(),2);
            for(int y=120;y<=132;y++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),Blocks.AIR.defaultBlockState(),2);
        }
        var start=h.absolutePos(new BlockPos(4,120,4)).getBottomCenter();p.moveTo(start.x,start.y,start.z,-90,0);p.setOnGround(true);h.getLevel().addNewPlayer(p);
        var target=EntityType.COW.create(h.getLevel());target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);target.getPersistentData().putLong(com.codex.blacklight.infection.InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
        target.setPos(start.add(kind==0?28:22,0,0));h.getLevel().addFreshEntity(target);target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,1000,4));
        h.assertTrue(CombatTargeting.visible(p,target),"the real contact lane starts unobstructed");
        if(kind==2)CombatTargeting.requestLock(p,target.getId());
        // This server fixture has no real client packet listener calling doTick.
        // Advance its actor clock and dispatch the same formal skill tick against real world time and victims.
        for(int t=2;t<290;t++)h.runAfterDelay(t,()->{
            p.tickCount++;DevastatorSystem.acceptInput(p,kind!=1,kind!=2,kind!=0,false);
            DevastatorSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
        });
        h.runAfterDelay(300,()->{try{
            float loss=10000-target.getHealth(),expected=DevastatorDamage.primary(kind);
            h.assertTrue(loss>=expected-.1F&&loss<=expected+81,"full skill pipeline uses high true damage once, kind="+kind+" loss="+loss+" snapshot="+DevastatorSystem.snapshot(p)+" actor="+p.position()+" target="+target.position()+" sight="+CombatTargeting.visible(p,target));
            h.assertTrue(p.getData(ModAttachments.CRITICAL_MASS).reserve()==0,"actual release consumes its real critical reserve");
            h.succeed();
        }finally{DevastatorSystem.cancel(p);CombatTargeting.requestLock(p,-1);target.discard();h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
            for(int x=0;x<40;x++)for(int z=0;z<8;z++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,119,z)),Blocks.AIR.defaultBlockState(),2);
            for(var chunk:tickets)h.getLevel().setChunkForced(chunk.x,chunk.z,false);
        }});
    }
}
