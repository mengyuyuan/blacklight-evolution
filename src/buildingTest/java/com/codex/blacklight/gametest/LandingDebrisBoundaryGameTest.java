package com.codex.blacklight.gametest;

import com.codex.blacklight.movement.LandingDebrisSystem;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class LandingDebrisBoundaryGameTest {
    private static void boundary(GameTestHelper h,int mode){
        var level=h.getLevel();var at=h.absoluteVec(new Vec3(3,4,3));
        var born=new ArrayList<Display.BlockDisplay>();var once=new AtomicBoolean();int[] nested={0};
        Consumer<EntityJoinLevelEvent> join=e->{
            if(e.getLevel()!=level||!(e.getEntity() instanceof Display.BlockDisplay d)||d.position().distanceToSqr(at)>1)return;
            born.add(d);
            if(mode==1&&nested[0]++<80)LandingDebrisSystem.fragment(level,at,Blocks.STONE.defaultBlockState(),Vec3.ZERO,.2F,8);
        };
        Consumer<EntityLeaveLevelEvent> leave=e->{
            if(e.getLevel()==level&&born.contains(e.getEntity())&&mode!=1&&once.compareAndSet(false,true))
                LandingDebrisSystem.fragment(level,at,Blocks.STONE.defaultBlockState(),Vec3.ZERO,.2F,8);
        };
        MinecraftForge.EVENT_BUS.addListener(join);MinecraftForge.EVENT_BUS.addListener(leave);
        try{
            LandingDebrisSystem.fragment(level,at,Blocks.STONE.defaultBlockState(),Vec3.ZERO,.2F,1);
            if(mode==0){
                LandingDebrisSystem.onServerTick(new net.minecraftforge.event.TickEvent.ServerTickEvent(
                        net.minecraftforge.event.TickEvent.Phase.END,()->true,level.getServer()));
                h.assertTrue(once.get()&&born.size()==2&&!born.get(1).isRemoved(),"a real fragment removal callback can publish the next effect without invalidating the active tick traversal");
            }else if(mode==1){
                h.assertTrue(born.size()<=64&&born.size()>1,"the shared 64-fragment budget includes synchronous in-flight publications, not only committed entries; published="+born.size());
            }else{
                // Drive this registered lifecycle handler only. Broadcasting a
                // synthetic global shutdown would reset unrelated test systems
                // while their MinecraftServer is deliberately still running.
                LandingDebrisSystem.stopping(new ServerStoppingEvent(level.getServer()));
                h.assertTrue(once.get()&&born.size()==1&&born.get(0).isRemoved(),"the lifecycle handler releases transient displays and refuses reentry while clearing its server");
            }
        }finally{
            MinecraftForge.EVENT_BUS.unregister(join);MinecraftForge.EVENT_BUS.unregister(leave);
            born.forEach(Display.BlockDisplay::discard);LandingDebrisSystem.onServerTick(new net.minecraftforge.event.TickEvent.ServerTickEvent(
                    net.minecraftforge.event.TickEvent.Phase.END,()->true,level.getServer()));
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=20,batch="landing_debris_callback")
    public static void aFragmentRemovalCallbackCannotCrashTheServerTick(GameTestHelper h){boundary(h,0);}
    @GameTest(template="wall_test",timeoutTicks=20,batch="landing_debris_publication_budget")
    public static void nestedFragmentPublicationCannotExceedItsSharedBudget(GameTestHelper h){boundary(h,1);}
    @GameTest(template="wall_test",timeoutTicks=20,batch="landing_debris_server_stop")
    public static void serverStoppingReleasesDebrisWithoutRepublishingIt(GameTestHelper h){boundary(h,2);}
}
