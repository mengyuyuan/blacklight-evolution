package com.codex.blacklight.combat;
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
public final class GroundSpikeAnchorGameTest {
    private static ServerPlayer caster(GameTestHelper h){
        var profile=new GameProfile(java.util.UUID.randomUUID(),"DevastatorQA");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault());p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;
        p.setGameMode(GameType.SURVIVAL);var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.activate(PowerForm.CLAWS);state.addEvolutionPoints(10000);
        for(var skill:new CombatSkill[]{CombatSkill.CRITICAL_MASS,CombatSkill.GROUNDSPIKE_GRAVEYARD,CombatSkill.TENDRIL_BARRAGE,CombatSkill.CRITICAL_PAIN})state.buyCombatSkill(skill);
        p.setData(ModAttachments.PLAYER_STATE,state);GrowthSystem.updateHealth(p);p.setHealth(p.getMaxHealth());p.setData(ModAttachments.CRITICAL_MASS,new CriticalMassState(1,false,-1000));return p;
    }
    @GameTest(template="wall_test",timeoutTicks=220,batch="anchored_graveyard")
    public static void graveyardCanBeDodgedBeforeAHitAndDoesNotTrackAfterwards(GameTestHelper h){
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
        target.setPos(start.add(28,0,0));h.getLevel().addFreshEntity(target);target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,1000,4));
        h.assertTrue(CombatTargeting.visible(p,target),"the real contact lane starts unobstructed");
        var dodger=EntityType.COW.create(h.getLevel());dodger.setNoAi(true);dodger.setPos(target.position().add(0,0,2));h.getLevel().addFreshEntity(dodger);
        var initial=dodger.position();boolean[] seen=new boolean[4];
        for(int t=2;t<175;t++)h.runAfterDelay(t,()->{
            p.tickCount++;DevastatorSystem.acceptInput(p,true,true,false,false);
            DevastatorSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));

            var visual=p.getData(ModAttachments.DEVASTATOR_VISUAL);
            for(var c:visual.contacts()){
                if(c.targetId()!=dodger.getId())continue;
                h.assertTrue(c.groundAnchor()!=null&&c.groundAnchor().distanceToSqr(initial)<.001,"published root followed moving/despawned target");
                if(!seen[0]){
                    var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
                    try{DevastatorVisual.CODEC.encode(buf,visual);
                        h.assertTrue(DevastatorVisual.CODEC.decode(buf).equals(visual),"lost contact root in observer packet");
                    }finally{buf.release();}
                    dodger.setPos(initial.add(-3,0,0));seen[0]=true;
                }
                if(visual.age(p,0)>c.at()+.1&&!seen[2]){
                    h.assertTrue(dodger.isAlive()&&dodger.getHealth()==dodger.getMaxHealth()&&!GroundSpikePin.held(dodger),"dodged shaft still hit");
                    dodger.discard();seen[2]=true;
                }else if(seen[2]&&dodger.isRemoved())seen[3]=true;
            }
            if(GroundSpikePin.held(target))seen[1]=true;
        });
        h.runAfterDelay(182,()->{try{
            h.assertTrue(seen[0]&&seen[1]&&seen[2]&&seen[3],"missing selection, caught survivor, dodge, or persistent contact "+java.util.Arrays.toString(seen));
            h.assertTrue(!GroundSpikePin.held(target),"graveyard pin survived withdrawal");
            h.succeed();
        }finally{
            DevastatorSystem.cancel(p);dodger.discard();target.discard();h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
            for(int x=0;x<40;x++)for(int z=0;z<8;z++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,119,z)),Blocks.AIR.defaultBlockState(),2);
            for(var chunk:tickets)h.getLevel().setChunkForced(chunk.x,chunk.z,false);
        }});
    }
}
