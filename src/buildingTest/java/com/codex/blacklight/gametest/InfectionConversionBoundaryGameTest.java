package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.*;

/** Real synchronous entity-join callbacks around the infection replacement transaction. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class InfectionConversionBoundaryGameTest {
    private static void convert(Mob source,ServerLevel level){
        try{
            var method=BlacklightInfectionSystem.class.getDeclaredMethod("convertHost",Mob.class,ServerLevel.class);
            method.setAccessible(true);method.invoke(null,source,level);
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static void boundary(GameTestHelper h,int mode){
        var level=h.getLevel();var source=EntityType.COW.create(level);
        source.setNoAi(true);source.setPersistenceRequired();
        source.setPos(h.absolutePos(new BlockPos(3,3,3)).getBottomCenter());
        source.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));
        level.addFreshEntity(source);BlacklightInfectionSystem.infect(source,null);
        String marker="audit-conversion-"+source.getUUID();source.addTag(marker);
        var created=new ArrayList<BlacklightInfectedHost>();var once=new AtomicBoolean();
        var publishedCopies=new AtomicInteger();
        Consumer<EntityJoinLevelEvent> callback=e->{
            if(e.getLevel()!=level||!(e.getEntity() instanceof BlacklightInfectedHost host)||!host.getTags().contains(marker))return;
            created.add(host);
            if(!once.compareAndSet(false,true))return;
            if(mode==0){
                int copies=(source.getMainHandItem().is(Items.DIAMOND_SWORD)?1:0)+(host.getMainHandItem().is(Items.DIAMOND_SWORD)?1:0);
                publishedCopies.set(copies);
            }else if(mode==1)host.discard();
            else convert(source,level);
        };
        NeoForge.EVENT_BUS.addListener(callback);
        try{
            convert(source,level);
            h.assertTrue(once.get(),"actual EntityJoinLevelEvent reached the replacement");
            if(mode==0){
                h.assertTrue(publishedCopies.get()==1,"equipment must have one owner while callbacks can inspect or take it");
                h.assertTrue(source.isRemoved()&&created.size()==1&&created.getFirst().getMainHandItem().is(Items.DIAMOND_SWORD),"successful conversion transfers the original equipment");
            }else if(mode==1){
                h.assertTrue(source.isAlive()&&!source.isRemoved()&&source.getMainHandItem().is(Items.DIAMOND_SWORD),"a replacement removed by a join callback cannot consume the source or its equipment");
            }else{
                h.assertTrue(created.size()==1&&source.isRemoved(),"a nested callback cannot publish a second replacement for the same source");
            }
        }finally{
            NeoForge.EVENT_BUS.unregister(callback);created.forEach(Entity::discard);source.discard();
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=20,batch="conversion_equipment_publication")
    public static void infectionPublicationDoesNotDuplicateEquipment(GameTestHelper h){boundary(h,0);}
    @GameTest(template="wall_test",timeoutTicks=20,batch="conversion_removed_replacement")
    public static void removedReplacementPreservesTheOriginalHost(GameTestHelper h){boundary(h,1);}
    @GameTest(template="wall_test",timeoutTicks=20,batch="conversion_publication_reentry")
    public static void infectionPublicationCannotReenterTheSameSource(GameTestHelper h){boundary(h,2);}
}
