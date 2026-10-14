package com.codex.blacklight.client.renderer;
import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;
import static org.junit.jupiter.api.Assertions.*;
class TentacleGaitTest {
 @Test void stationaryFeetNeverWander(){
   var g=new TentacleGait();g.update(0,Vec3.ZERO,0,true,.76,p->p);Vec3[] tips=new Vec3[8];
   for(int i=0;i<8;i++)tips[i]=g.tip(i);
   for(int t=1;t<500;t++)g.update(t,Vec3.ZERO,0,true,.76,p->{fail("idle must not resample ground");return p;});
   for(int i=0;i<8;i++)assertEquals(tips[i],g.tip(i));
 }
 @Test void bodyMovesButSupportTipsRemainPlanted(){
   var g=new TentacleGait();g.update(0,Vec3.ZERO,0,true,.76,p->p);Vec3 foot=g.tip(1);
   for(int t=1;t<=6;t++)g.update(t,new Vec3(0,0,t*.06),0,true,.76,p->p);
   assertTrue(g.swinging(0));assertFalse(g.swinging(1));assertEquals(foot,g.tip(1));
   assertTrue(g.tip(0).y>.025,"reaching tips lift above ground");
 }
 @Test void consecutiveStridesAlternateSupportGroups(){
   var g=new TentacleGait();boolean even=false,odd=false;
   for(int t=0;t<40;t++){g.update(t,new Vec3(0,0,t*.06),0,true,.76,p->p);even|=g.swinging(0);odd|=g.swinging(1);assertFalse(g.swinging(0)&&g.swinging(1));}
   assertTrue(even&&odd);
 }
 @Test void contactsFollowGroundHeight(){
   var g=new TentacleGait();g.update(0,Vec3.ZERO,0,true,.76,p->new Vec3(p.x,.35,p.z));
   for(int i=0;i<8;i++)assertEquals(.35,g.tip(i).y,1e-9);
 }
 @Test void teleportResetsOldContacts(){
   var g=new TentacleGait();g.update(0,Vec3.ZERO,0,true,.76,p->p);var destination=new Vec3(100,50,-100);
   g.update(1,destination,0,true,.76,p->p);
   for(int i=0;i<8;i++)assertTrue(g.tip(i).distanceTo(destination)<1);
 }
 @Test void airborneDoesNotQueryTerrainAndLandingReplants(){
   var g=new TentacleGait();g.update(0,Vec3.ZERO,0,false,.76,p->{fail("no terrain query in air");return p;});
   g.update(1,Vec3.ZERO,0,true,.76,p->new Vec3(p.x,.05,p.z));
   assertEquals(.05,g.tip(0).y,1e-9);
 }
}
