package com.codex.blacklight.gametest;

import com.codex.blacklight.item.SymbioteCharm;
import com.codex.blacklight.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("blacklight")
@PrefixGameTestTemplate(false)
public final class SymbioteGameTest {
    private static ServerPlayer join(GameTestHelper h) {
        var profile = new GameProfile(UUID.randomUUID(), "symbiote-test");
        var server = h.getLevel().getServer();
        var player=new ServerPlayer(server,h.getLevel(),profile);
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection,player);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }


    private static void tap(ServerPlayer p,BlockPos at,ItemStack hand,boolean sneak) {
        p.setItemInHand(InteractionHand.MAIN_HAND,hand);
        p.setShiftKeyDown(sneak);
        var hit=new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(at).add(0,.375,0),net.minecraft.core.Direction.UP,at,false);
        p.gameMode.useItemOn(p,p.serverLevel(),hand,InteractionHand.MAIN_HAND,hit);
        p.setShiftKeyDown(false);
    }
    private static com.codex.blacklight.world.WeaveCell cell(GameTestHelper h,BlockPos pos){
        return (com.codex.blacklight.world.WeaveCell)h.getLevel().getBlockEntity(pos);
    }
    @GameTest(template="wall_test",timeoutTicks=50,batch="wither_core_drop")
    public static void infectedWitherDropsOneCraftingCore(GameTestHelper h) {
        var level = h.getLevel();
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        var at = h.absoluteVec(new Vec3(3,15,3));
        var wither = net.minecraft.world.entity.EntityType.WITHER.create(level);
        wither.setPos(at);
        wither.setNoAi(true);
        level.addFreshEntity(wither);
        var host = com.codex.blacklight.registry.ModEntities.INFECTED_HOST.get().create(level);
        host.inherit(wither);
        host.setPos(at);
        host.setNoAi(true);
        level.addFreshEntity(host);
        var drops = host.getBoundingBox().inflate(4);
        var core = ModItems.WITHER_CORE.get();
        wither.hurt(wither.damageSources().genericKill(),Float.MAX_VALUE);
        h.assertTrue(!wither.isAlive() && level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,drops,
                item->item.getItem().is(core)).isEmpty(),"ordinary Wither does not drop a core");
        host.hurt(host.damageSources().genericKill(),Float.MAX_VALUE);
        var cores = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,drops,item->item.getItem().is(core));
        h.assertTrue(!host.isAlive() && cores.size()==1 && cores.get(0).getItem().getCount()==1,"one infected Wither yields exactly one core");
        h.succeed();
    }

    private static void lay(GameTestHelper h,ServerPlayer p,BlockPos core,com.codex.blacklight.world.WeavePattern pattern) {
        for(int z=0;z<5;z++)for(int x=0;x<5;x++){
            var item=pattern.ingredient(x,z);
            if(item!=null){
                // A 90-degree rotation must craft exactly like the canonical guide.
                var pos=core.offset(2-z,0,x-2);
                var stack=new ItemStack(item,2);tap(p,pos,stack,false);
                h.assertTrue(stack.getCount()==1 && cell(h,pos).offering().is(item),"one offering per right click");
            }
        }
    }
    @GameTest(template="wall_test",timeoutTicks=540)
    public static void physicalWeaveNest(GameTestHelper h) {
        var level=h.getLevel();var p=join(h);
        var core=h.absolutePos(new BlockPos(4,2,4));var stand=Vec3.atBottomCenterOf(core).add(0,0,2.86);
        level.setBlockAndUpdate(core.offset(0,-1,3),Blocks.STONE.defaultBlockState());
        for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++)
            level.setBlockAndUpdate(core.offset(x,0,z),com.codex.blacklight.world.WeaveCell.part(x,z).defaultBlockState());
        for(int x:new int[]{-2,2})for(int z:new int[]{-2,2}){
            var corner=core.offset(x,0,z);
            var shape=level.getBlockState(corner).getShape(level,corner);
            h.assertTrue(shape.bounds().maxY>1.20,"assembled corner has a raised outer cap");
        }
        p.teleportTo(stand.x,stand.y,stand.z);
        var state=com.codex.blacklight.combat.QuillHarvest.state(p);state.infect();state.addBiomass(-1000);
        for(int i=0;i<25;i++){
            var pos=core.offset(i%5-2,0,i/5-2);
            tap(p,pos,new ItemStack(net.minecraft.world.item.Items.STRING),false);
            h.assertTrue(cell(h,pos).offering().is(net.minecraft.world.item.Items.STRING),"all 25 surface cells accept items");
        }
        tap(p,core,ItemStack.EMPTY,true);
        h.assertTrue(cell(h,core).weaver()==null,"extra materials cannot craft");
        for(int i=0;i<25;i++)tap(p,core.offset(i%5-2,0,i/5-2),ItemStack.EMPTY,false);
        var chest=com.codex.blacklight.world.WeavePattern.CHEST;
        var legs=com.codex.blacklight.world.WeavePattern.KNEES;
        var feet=com.codex.blacklight.world.WeavePattern.FEET;
        for (var pattern : com.codex.blacklight.world.WeavePattern.values()) {
            h.assertTrue(pattern.ingredient(2,2)==ModItems.WITHER_CORE.get(),"all four patterns require the Wither Core at the center");
        }
        lay(h,p,core,chest);tap(p,core,ItemStack.EMPTY,true);
        h.assertTrue(cell(h,core).weaver()==null,"insufficient biomass blocks infusion");
        state.addBiomass(600);
        tap(p,core,ItemStack.EMPTY,false);
        tap(p,core,ItemStack.EMPTY,true);
        h.assertTrue(cell(h,core).weaver()==null && state.getBiomass()==600,"a missing Wither Core prevents infusion");
        tap(p,core,new ItemStack(ModItems.WITHER_CORE.get()),false);
        p.teleportTo(stand.x,stand.y,stand.z+2.0);
        tap(p,core,ItemStack.EMPTY,true);
        h.assertTrue(cell(h,core).weaver()==null,"out of palm reach");
        p.teleportTo(stand.x,stand.y,stand.z);
        tap(p,core,ItemStack.EMPTY,true);
        h.assertTrue(cell(h,core).weaver()!=null,"channel starts on physical core");
        var missing=core.offset(-2,0,1);
        level.removeBlock(missing,false);
        var corner=core.offset(-2,0,-2);
        h.assertTrue(level.getBlockState(corner).getShape(level,corner).bounds().maxY<1,"incomplete frame lowers its corners");
        h.assertTrue(cell(h,core).weaver()==null && state.getBiomass()==600,"breaking a side cancels for free");
        level.setBlockAndUpdate(missing,com.codex.blacklight.world.WeaveCell.part(-2,1).defaultBlockState());
        tap(p,core,ItemStack.EMPTY,true);tap(p,core,ItemStack.EMPTY,true);
        h.runAfterDelay(103,()->{
            h.assertTrue(cell(h,core).offering().is(chest.result.get()) && state.getBiomass()==420,"chest charged once");
            tap(p,core,ItemStack.EMPTY,false);lay(h,p,core,legs);tap(p,core,ItemStack.EMPTY,true);
        });
        h.runAfterDelay(211,()->{
            h.assertTrue(cell(h,core).offering().is(legs.result.get()) && state.getBiomass()==300,"knee recipe");
            tap(p,core,ItemStack.EMPTY,false);lay(h,p,core,feet);tap(p,core,ItemStack.EMPTY,true);
        });
        h.runAfterDelay(322,()->{
            h.assertTrue(cell(h,core).offering().is(feet.result.get()) && state.getBiomass()==220,"anklet recipe");
            tap(p,core,ItemStack.EMPTY,false);
            lay(h,p,core,com.codex.blacklight.world.WeavePattern.TEMPLE);
            tap(p,core.offset(0,0,2),ItemStack.EMPTY,true);
            p.teleportTo(stand.x+.6,stand.y,stand.z);
        });
        h.runAfterDelay(330,()->{
            h.assertTrue(cell(h,core).weaver()==null && state.getBiomass()==220,"stepping away must cancel without payment");
            p.teleportTo(stand.x,stand.y,stand.z);
            tap(p,core.offset(0,0,2),ItemStack.EMPTY,true);
        });
        h.runAfterDelay(436,()->{
            h.assertTrue(cell(h,core).offering().is(ModItems.TEMPLE_BUD.get()) && state.getBiomass()==120,"head crafted from rim");
            for(int i=0;i<25;i++)if(i!=12)h.assertTrue(cell(h,core.offset(i%5-2,0,i/5-2)).offering().isEmpty(),"inputs consumed");
            p.server.getPlayerList().remove(p);h.succeed();
        });
    }

    @GameTest(template="wall_test",timeoutTicks=90)
    public static void distinctSlotsAndAttackModifiers(GameTestHelper h) {
        var level=h.getLevel();
        var player=join(h);
        var ground=h.absolutePos(new BlockPos(3,2,3));
        level.setBlockAndUpdate(ground,Blocks.STONE.defaultBlockState());
        var stand=Vec3.atBottomCenterOf(ground.above());
        player.teleportTo(stand.x,stand.y,stand.z);
        var items=new Item[]{ModItems.TEMPLE_BUD.get(),ModItems.PULSE_HEART.get(),ModItems.SINEW_KNOT.get(),ModItems.SPORE_ANKLET.get()};
        var slots=new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
        // A mock connection supplies no movement packets to drive ServerPlayer.doTick.
        player.doTick();
        double base=player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (int i=0;i<items.length;i++) {
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(items[i]));
            var used=items[i].use(level,player,InteractionHand.MAIN_HAND);
            player.setItemInHand(InteractionHand.MAIN_HAND,used.getObject());
            h.assertTrue(SymbioteCharm.wearing(player,slots[i]),"Right click equips "+slots[i]);
            player.doTick();
            double value=player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            h.assertTrue(Math.abs(value-base-25*(i+1))<.001,"After equipping "+slots[i]+" expected "+(base+25*(i+1))+" got "+value);
        }
        for (int i=0;i<items.length;i++) {
            player.setItemSlot(slots[i],ItemStack.EMPTY);
            player.doTick();
            h.assertTrue(!SymbioteCharm.wearing(player,slots[i]),"Taking off "+slots[i]);
            h.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE)-base-25*(3-i))<.001,"Removal clears only its own modifier");
        }
        player.server.getPlayerList().remove(player);
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=90)
    public static void armorDamageAndLastChance(GameTestHelper h) {
        var p=join(h);
        try {
            var state=com.codex.blacklight.combat.QuillHarvest.state(p);
            state.infect();state.setPrototypeMode(true);
            p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            var move=com.codex.blacklight.combat.WeaponMove.choose(com.codex.blacklight.power.PowerForm.NONE,
                    com.codex.blacklight.combat.WeaponMove.Input.ATTACK,false,false,false);
            var slots=new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
            var items=new Item[]{ModItems.TEMPLE_BUD.get(),ModItems.PULSE_HEART.get(),ModItems.SINEW_KNOT.get(),ModItems.SPORE_ANKLET.get()};
            for(int n=0;n<=4;n++){
                if(n>0)p.setItemSlot(slots[n-1],new ItemStack(items[n-1]));
                float raw=move.damageFor(p,com.codex.blacklight.power.PowerForm.NONE);
                float shown=raw*com.codex.blacklight.power.CombatScale.UNITS;
                h.assertTrue(Math.abs(shown-(32.5F+25*n))<.001,"base with "+n+" pieces: "+shown);
                var target=villager(h,1000);
                target.hurt(p.damageSources().playerAttack(p),com.codex.blacklight.power.CombatScale.weaponContact(target,raw));
                h.assertTrue(Math.abs(1000-target.getHealth()-shown)<.01,"server HP vs panel: "+(1000-target.getHealth())+"/"+shown);
                target.discard();
            }
            com.codex.blacklight.registry.ModAttachments.setData(p,com.codex.blacklight.registry.ModAttachments.EVOLUTION_MILESTONES,com.codex.blacklight.registry.ModAttachments.getData(p,com.codex.blacklight.registry.ModAttachments.EVOLUTION_MILESTONES).grant(com.codex.blacklight.state.EvolutionMilestones.INFINITE));
            com.codex.blacklight.registry.ModAttachments.setData(p,com.codex.blacklight.registry.ModAttachments.INFINITE_EVOLUTION,com.codex.blacklight.registry.ModAttachments.getData(p,com.codex.blacklight.registry.ModAttachments.INFINITE_EVOLUTION).buy(com.codex.blacklight.power.EvolutionAttribute.STRENGTH,5));
            double factor=com.codex.blacklight.power.InfiniteEvolutionSystem.strengthMultiplier(p);
            h.assertTrue(factor>1,"fixture has active infinite strength");
            var armored=villager(h,1000);
            float raw=move.damageFor(p,com.codex.blacklight.power.PowerForm.NONE);
            armored.hurt(p.damageSources().playerAttack(p),com.codex.blacklight.power.CombatScale.weaponContact(armored,raw));
            h.assertTrue(Math.abs(1000-armored.getHealth()-132.5*factor)<.02,"armor multiplied exactly once: "+(1000-armored.getHealth())+" factor "+factor);
            armored.discard();
            for(var slot:slots)p.setItemSlot(slot,ItemStack.EMPTY);
            for(var form:com.codex.blacklight.power.PowerForm.values()){
                if(!com.codex.blacklight.combat.WeaponMove.supports(form) && form!=com.codex.blacklight.power.PowerForm.BACKSPINES)continue;
                var target=villager(h,20);
                float attack;
                if(form==com.codex.blacklight.power.PowerForm.BACKSPINES)
                    attack=SymbioteCharm.attackBase(p,com.codex.blacklight.animation.ScapulaVolley.OPENING_SHOT_DAMAGE);
                else attack=com.codex.blacklight.combat.WeaponMove.choose(form,com.codex.blacklight.combat.WeaponMove.Input.ATTACK,false,false,false).damageFor(p,form);
                target.hurt(p.damageSources().playerAttack(p),com.codex.blacklight.power.CombatScale.weaponContact(target,attack));
                h.assertTrue(!target.isAlive()&&!com.codex.blacklight.combat.ConsumeLastChance.used(target),"first strike against villager: "+form);
                target.discard();
            }
            for(float maximum:new float[]{20,49,50,51}){
                var target=villager(h,maximum);
                target.hurt(p.damageSources().playerAttack(p),200);
                h.assertTrue(maximum<=50 ? !target.isAlive()&&!com.codex.blacklight.combat.ConsumeLastChance.used(target)
                        : target.getHealth()==1 && com.codex.blacklight.combat.ConsumeLastChance.awaitingFinisher(target),"lethal cutoff "+maximum);
                target.discard();
            }
        } finally { p.server.getPlayerList().remove(p); }
        h.succeed();
    }
    private static net.minecraft.world.entity.npc.Villager villager(GameTestHelper h,float hp){
        var v=net.minecraft.world.entity.EntityType.VILLAGER.create(h.getLevel());
        v.setNoAi(true);v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);v.setHealth(hp);
        v.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(3,3,3))));h.getLevel().addFreshEntity(v);
        return v;
    }

    @GameTest(template="wall_test",timeoutTicks=60)
    public static void retiredGiantSave(GameTestHelper h) {
        var nativeMob=net.minecraft.world.entity.EntityType.IRON_GOLEM.create(h.getLevel());
        var giant=com.codex.blacklight.registry.ModEntities.INFECTED_HOST.get().create(h.getLevel());
        giant.inherit(nativeMob,true);giant.setHealth(giant.getMaxHealth()/2);
        var saved=giant.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        var host=com.codex.blacklight.registry.ModEntities.INFECTED_HOST.get().create(h.getLevel());
        host.load(saved);
        h.assertTrue(!host.giantBoss() && host.evolutionTicks()==0 && host.bodyScale()<.2,"old giant returned to compact host");
        h.assertTrue(Math.abs(host.getHealth()/host.getMaxHealth()-.5)<.015,"retains health fraction");
        h.assertTrue(host.getUUID().equals(giant.getUUID()),"keeps entity identity");
        var growing=com.codex.blacklight.registry.ModEntities.INFECTED_HOST.get().create(h.getLevel());
        growing.inherit(nativeMob,false);growing.setEvolutionProgress(11500);
        host.load(growing.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(host.evolutionTicks()==0 && !host.giantBoss() && host.bodyScale()<.2,"old progressing host");
        var cmd=h.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("blacklight");
        h.assertTrue(cmd.getChild("spawn_colossus")==null && cmd.getChild("colossus_status")==null && cmd.getChild("hive").getChild("accelerate")==null,"retired gameplay commands");
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=90,batch="armor_opening_swing")
    public static void fullHealthVillagerDiesOnOpeningSwing(GameTestHelper h) {
        for(var form : com.codex.blacklight.power.PowerForm.values()) {
            if(!com.codex.blacklight.combat.WeaponMove.supports(form))continue;
            var p=join(h);
            var target=villager(h,20);
            try {
                var state=com.codex.blacklight.combat.QuillHarvest.state(p);
                state.infect();state.setPrototypeMode(true);state.unlock(form);state.activate(form);
                p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                var origin=Vec3.atCenterOf(h.absolutePos(new BlockPos(5,30,5)));
                p.setPos(origin);p.setOnGround(true);p.setDeltaMovement(Vec3.ZERO);
                p.setYRot(0);p.setXRot(0);p.setYHeadRot(0);p.yBodyRot=p.yBodyRotO=0;
                target.setNoGravity(true);
                target.setPos(origin.add(0,0,form==com.codex.blacklight.power.PowerForm.WHIPFIST?4:1.55));
                com.codex.blacklight.combat.WeaponCombatSystem.request(p,false);
                var move=com.codex.blacklight.registry.ModAttachments.getData(p,com.codex.blacklight.registry.ModAttachments.WEAPON_VISUAL).spec();
                h.assertTrue(move!=null,form+" accepts opening swing");
                for(int i=0;i<move.duration+12 && com.codex.blacklight.combat.WeaponCombatSystem.busy(p);i++){
                    p.tickCount++;com.codex.blacklight.combat.WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
                }
                h.assertTrue(!target.isAlive()&&!com.codex.blacklight.combat.ConsumeLastChance.used(target),form+" first swing: "+target.getHealth());
            } finally {
                com.codex.blacklight.combat.WeaponCombatSystem.cancel(p);
                target.discard();p.server.getPlayerList().remove(p);
            }
        }
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=100,batch="charged_weapon_contacts")
    public static void chargedWhipHitsWithArmorAndStrength(GameTestHelper h) {
        for (boolean air : new boolean[]{false,true}) {
            for (boolean hold : new boolean[]{false,true}) {
                for(int gear=0; gear<3; gear++){
                    var p=join(h);
                    var target=villager(h,1000);
                    try {
                        var form=com.codex.blacklight.power.PowerForm.WHIPFIST;
                        var state=com.codex.blacklight.combat.QuillHarvest.state(p);
                        state.infect();state.setPrototypeMode(true);state.unlock(form);state.activate(form);
                        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                        if(gear>0){
                            p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModItems.TEMPLE_BUD.get()));
                            p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.PULSE_HEART.get()));
                            p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(ModItems.SINEW_KNOT.get()));
                            p.setItemSlot(EquipmentSlot.FEET,new ItemStack(ModItems.SPORE_ANKLET.get()));
                        }
                        if(gear==2){
                            com.codex.blacklight.registry.ModAttachments.setData(p,com.codex.blacklight.registry.ModAttachments.EVOLUTION_MILESTONES,com.codex.blacklight.registry.ModAttachments.getData(p,com.codex.blacklight.registry.ModAttachments.EVOLUTION_MILESTONES).grant(com.codex.blacklight.state.EvolutionMilestones.INFINITE));
                            com.codex.blacklight.registry.ModAttachments.setData(p,com.codex.blacklight.registry.ModAttachments.INFINITE_EVOLUTION,com.codex.blacklight.registry.ModAttachments.getData(p,com.codex.blacklight.registry.ModAttachments.INFINITE_EVOLUTION).buy(com.codex.blacklight.power.EvolutionAttribute.STRENGTH,5));
                        }
                        var origin=Vec3.atCenterOf(h.absolutePos(new BlockPos(5,30,5)));
                        p.setPos(origin);p.setOnGround(!air);p.setDeltaMovement(Vec3.ZERO);
                        p.setYRot(0);p.setYHeadRot(0);p.setXRot(0);p.yBodyRot=p.yBodyRotO=0;
                        target.setNoGravity(true);target.setPos(origin.add(0,0,4));
                        for(int t=0;t<120 && target.getHealth()==1000;t++){
                            com.codex.blacklight.combat.WeaponCombatSystem.acceptInput(p,false,t==0||hold);
                            p.tickCount++;
                            com.codex.blacklight.combat.WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
                        }
                        var base=air?com.codex.blacklight.combat.WeaponMove.WHIP_AIR:com.codex.blacklight.combat.WeaponMove.WHIP_LONGSHOT;
                        var charged=air?com.codex.blacklight.combat.WeaponMove.WHIP_AIR_CHARGED:com.codex.blacklight.combat.WeaponMove.WHIP_LONGSHOT_CHARGED;
                        float expected=((hold?charged:base).damage*5+(gear==0?0:100))*(hold?1.5F:1F)
                                *(float)com.codex.blacklight.power.InfiniteEvolutionSystem.strengthMultiplier(p);
                        float actual=1000-target.getHealth();
                        h.assertTrue(Math.abs(actual-expected)<.03F,"right-click air="+air+" hold="+hold+" gear="+gear+" expected "+expected+" actual "+actual);
                    } finally {
                        com.codex.blacklight.combat.WeaponCombatSystem.cancel(p);
                        target.discard();p.server.getPlayerList().remove(p);
                    }
                }
            }
        }
        h.succeed();
    }
}
