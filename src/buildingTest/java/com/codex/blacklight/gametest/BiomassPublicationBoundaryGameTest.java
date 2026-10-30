package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BiomassHusk;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class BiomassPublicationBoundaryGameTest {
    private static void boundary(GameTestHelper h,int mode){
        var player=LockedGrabGameTest.player(h,PowerForm.CLAWS);
        var husks=new ArrayList<BiomassHusk>();var once=new AtomicBoolean();var nested=new AtomicBoolean();
        Consumer<EntityJoinLevelEvent> callback=e->{
            if(e.getLevel()!=player.level()||!(e.getEntity() instanceof BiomassHusk husk)||!player.getUUID().equals(husk.owner()))return;
            husks.add(husk);if(!once.compareAndSet(false,true))return;
            if(mode==0)nested.set(BiomassSystem.begin(player,false));
            else if(mode==1)husk.discard();
            else com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE).setPrototypeMode(false);
        };
        MinecraftForge.EVENT_BUS.addListener(callback);
        try{
            boolean started=BiomassSystem.begin(player,false);
            h.assertTrue(once.get(),"the real shell publication callback ran");
            if(mode==0)h.assertTrue(started&&!nested.get()&&husks.size()==1&&BiomassSystem.active(player),"one separation publishes one shell and cannot reenter before the state commit");
            else h.assertTrue(!started&&!BiomassSystem.active(player)&&husks.stream().allMatch(BiomassHusk::isRemoved),"removed shells or changed player qualifications leave no orphan slime session");
        }finally{
            MinecraftForge.EVENT_BUS.unregister(callback);husks.forEach(BiomassHusk::discard);
            com.codex.blacklight.registry.ModAttachments.setData(player, ModAttachments.BIOMASS_FORM,BiomassState.EMPTY);player.refreshDimensions();player.discard();
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=20,batch="slime_publication_reentry")
    public static void slimeSeparationCannotPublishTwoShells(GameTestHelper h){boundary(h,0);}
    @GameTest(template="wall_test",timeoutTicks=20,batch="slime_removed_shell")
    public static void removedShellCannotStartAnUnrecallableSeparation(GameTestHelper h){boundary(h,1);}
    @GameTest(template="wall_test",timeoutTicks=20,batch="slime_changed_qualification")
    public static void changedModeDuringShellPublicationCancelsTheSeparation(GameTestHelper h){boundary(h,2);}
}
