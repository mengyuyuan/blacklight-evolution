package com.codex.blacklight.combat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeldObjectIndexTest {
    private static final class Cargo { Object owner,world; boolean removed; double distance; }
    @Test void animatedGripCanLeaveSearchRadiusWithoutLosingThrowTarget(){
        var index=new HeldObjectIndex<Object,Cargo>();var player=new Object();var world=new Object();
        var block=new Cargo();block.owner=player;block.world=world;index.remember(player,block);
        for(double distance:new double[]{0,3.9,4.1,8,0}){
            block.distance=distance;
            assertSame(block,index.get(player,b->b.owner==player&&b.world==world&&!b.removed));
        }
    }
    @Test void releaseOrRemovalCannotBeReusedAsHeldCargo(){
        var index=new HeldObjectIndex<Object,Cargo>();var player=new Object();var block=new Cargo();
        block.owner=player;index.remember(player,block);block.owner=null;
        assertNull(index.get(player,b->b.owner==player));
        block.owner=player;assertNull(index.get(player,b->true));
        index.remember(player,block);block.removed=true;
        assertNull(index.get(player,b->!b.removed));
    }
    @Test void RespawnWorldChangeAndOtherPlayersDoNotInheritCarry(){
        var index=new HeldObjectIndex<Object,Cargo>();var oldPlayer=new Object();var newPlayer=new Object();
        var block=new Cargo();block.owner=oldPlayer;block.world=new Object();index.remember(oldPlayer,block);
        assertNull(index.get(newPlayer,b->true));
        var newWorld=new Object();assertNull(index.get(oldPlayer,b->b.world==newWorld));
        assertNull(index.get(oldPlayer,b->true));
    }
}
