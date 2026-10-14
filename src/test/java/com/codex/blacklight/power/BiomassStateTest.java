package com.codex.blacklight.power;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BiomassStateTest {
 @Test void saveKeepsCommittedConsumptionButNotWorldLocalVictimIds(){
   var start=new BiomassState(BiomassState.CRAWLING,10,true,"550e8400-e29b-41d4-a716-446655440000","claws",-1);
   for(var s:java.util.List.of(start.absorb(42,20),start.reform(30),start.ready(50))){
     var json=BiomassState.CODEC.encodeStart(JsonOps.INSTANCE,s).getOrThrow(false,error->{throw new AssertionError(error);});
     var saved=BiomassState.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow(false,error->{throw new AssertionError(error);});
     assertEquals(-1,saved.victim());assertEquals(s.phase(),saved.phase());assertEquals(s.shell(),saved.shell());assertEquals(s.consumed(),saved.consumed());
   }
 }
 @Test void networkRoundtripIncludesVictimAndTiming(){
   var s=new BiomassState(BiomassState.ABSORBING,987,false,"husk","blade",412);
   var buffer=new FriendlyByteBuf(Unpooled.buffer());
   try{BiomassState.STREAM_CODEC.encode(buffer,s);assertEquals(s,BiomassState.STREAM_CODEC.decode(buffer));assertFalse(buffer.isReadable());}finally{buffer.release();}
 }
 @Test void lowCeilingDoesNotTrapPlayerOrRequireAnotherMeal(){
   var s=BiomassState.EMPTY.crawl(0).absorb(3,1).reform(25).ready(43);
   assertTrue(s.active());assertTrue(s.consumed());assertFalse(s.immobilized());
   assertEquals(0,s.progress(40,18));assertEquals(1,s.progress(100,18));
 }
 @Test void recallNeverCancelsFatalFormOrCommittedKill(){
   var voluntary=new BiomassState(BiomassState.CRAWLING,0,false,"shell","claws",-1);
   assertTrue(voluntary.recallable());assertTrue(voluntary.absorb(3,1).recallable());
   assertFalse(voluntary.reform(2).recallable());assertFalse(voluntary.ready(3).recallable());
   assertFalse(new BiomassState(BiomassState.CRAWLING,0,true,"shell","claws",-1).recallable());
   assertFalse(BiomassState.EMPTY.recallable());assertFalse(BiomassState.EMPTY.crawl(0).recallable());
 }
 @Test void respawnCannotBypassFatalConsumptionRequirement(){
   var fatal=new BiomassState(BiomassState.CRAWLING,10,true,"","claws",-1);
   var after=fatal.absorb(12,20).afterRespawn(30);
   assertTrue(after.active());assertTrue(after.fatal());assertFalse(after.recallable());assertFalse(after.consumed());assertEquals(-1,after.victim());assertTrue(after.shell().isEmpty());
   assertTrue(fatal.reform(20).afterRespawn(30).consumed());
   assertEquals(BiomassState.EMPTY,BiomassState.EMPTY.afterRespawn(30));
 }
}
