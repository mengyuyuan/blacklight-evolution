package com.codex.blacklight.infection;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.codex.blacklight.infection.TissueVitals.*;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

class TissueVitalsTest {
    private static final Environment AIR=new Environment(true,false,true,false,false,true,false,false,false,2,1);
    private static Environment water(boolean nativeWater,boolean vent){return new Environment(false,true,vent,false,true,true,nativeWater,false,false,2,1);}
    private static int fill(TissueVitals v,Profile p,int ticks,int food){for(int i=0;i<ticks;i++)food-=v.breathe(p,AIR,300,300,food).spent();return food;}
    @Test void oldOrEmptySavesDoNotStartWithFreeOxygenOrBlood(){
        var v=TissueVitals.read(new CompoundTag());assertEquals(0,v.oxygen());assertEquals(0,v.spleen());
        assertEquals(0,fill(v,Profile.EMPTY,1000,0));assertEquals(100,v.oxygen(),"ordinary breathing supplies gas, not food or stored blood");assertEquals(0,v.spleen());
    }
    @Test void nativeWaterBreathingAndAirBreathingRemainDifferentTraits(){
        var v=new TissueVitals();var fishDry=new Environment(true,false,true,false,false,false,true,false,false,1,1);
        assertFalse(v.breathe(Profile.EMPTY,fishDry,100,300,0).canBreathe());assertTrue(v.breathe(Profile.EMPTY,water(true,false),100,300,0).canBreathe());
        assertTrue(v.breathe(Profile.EMPTY,AIR,100,300,0).canBreathe());assertFalse(v.breathe(Profile.EMPTY,water(false,false),100,300,0).canBreathe());
    }
    @Test void acquiredGillsSpendOneRealUpkeepDoseAndItsRemainingTimeSurvivesReload(){
        var p=new Profile(2,0,0,0,0,0,0,0);var v=new TissueVitals();var first=v.breathe(p,water(false,false),10,300,1);
        assertTrue(first.canBreathe());assertEquals(1,first.spent());
        for(int i=0;i<49;i++)assertTrue(v.breathe(p,water(false,false),10,300,0).canBreathe());
        var saved=v.write();var restored=TissueVitals.read(saved);assertEquals(saved,restored.write());
        int left=saved.getInt("GillCharge");for(int i=0;i<left;i++)assertTrue(restored.breathe(p,water(false,false),10,300,0).canBreathe());
        assertFalse(restored.breathe(p,water(false,false),10,300,0).canBreathe(),"empty reserve cannot refresh the paid gill dose");
        assertTrue(new TissueVitals().breathe(p,water(true,false),10,300,0).refill()>4,"native gills gain an actual faster exchange rate");
    }
    @Test void bronchialLungsEnlargeRealAirCapacityButCannotBreatheWater(){
        var p=new Profile(0,2,0,0,0,0,0,0);assertTrue(p.maxAir(300)>300);assertTrue(p.maxAir(4800)>=4800);
        var v=new TissueVitals();assertTrue(v.breathe(p,AIR,100,300,0).refill()>4);assertFalse(v.breathe(p,water(false,false),100,300,0).canBreathe());
    }
    @Test void airflowAndBookLungsRespondToOppositeActualWorkConditions(){
        var moving=new Environment(true,false,true,true,false,true,false,false,false,2,1);
        var wetRest=new Environment(true,false,true,false,true,true,false,false,false,2,1);
        var airflow=new Profile(0,0,2,0,0,0,0,0);var book=new Profile(0,0,0,2,0,0,0,0);
        assertTrue(new TissueVitals().breathe(airflow,moving,100,300,0).refill()>new TissueVitals().breathe(airflow,AIR,100,300,0).refill());
        assertTrue(new TissueVitals().breathe(book,AIR,100,300,0).refill()>new TissueVitals().breathe(book,moving,100,300,0).refill());
        assertEquals(4,new TissueVitals().breathe(book,wetRest,100,300,0).refill(),"flooded peripheral book-lung vents do not earn their dry-rest exchange bonus");
    }
    @Test void tracheaeNeedAnExposedBodyVentAndLoseEfficiencyOnVeryLargeBodies(){
        var p=new Profile(0,0,0,0,2,0,0,0);assertFalse(new TissueVitals().breathe(p,water(false,false),20,300,1).canBreathe());
        assertEquals(Route.VENT,new TissueVitals().breathe(p,water(false,true),20,300,1).route());
        assertFalse(new TissueVitals().breathe(p,water(false,true),20,300,0).canBreathe());
        var huge=new Environment(false,true,true,false,true,true,false,false,false,600,1);
        assertTrue(trachealEfficiency(p,huge)<trachealEfficiency(p,water(false,true))*.1);
        assertTrue(trachealEfficiency(new Profile(0,0,0,0,2,8,0,0),huge)>trachealEfficiency(p,huge));
    }
    @Test void heartRecoveryPaysOxygenAndFoodInsteadOfMultiplyingDamageOrAnimationRate(){
        var p=new Profile(0,0,0,0,0,2,0,0);var v=new TissueVitals();fill(v,p,600,0);int before=v.oxygen();
        var r=v.recover(100,p,1,1);assertEquals(1,r.spent());assertTrue(r.ticks()<100&&r.ticks()>=72);assertEquals(before-18,v.oxygen());
        assertEquals(new Recovery(100,0),v.recover(100,p,0,1));
        for(int i=0;i<10;i++)v.recover(100,p,1,1);assertEquals(new Recovery(100,0),v.recover(100,p,1,1),"a depleted work-oxygen buffer cannot repeatedly boost recovery");
    }
    @Test void branchialHeartNeedsActualWaterExchangeAndHasNoUniversalLandBonus(){
        var p=new Profile(0,0,0,0,0,0,3,0);var land=new TissueVitals();fill(land,p,600,0);assertEquals(new Recovery(100,0),land.recover(100,p,5,1));
        var wet=new TissueVitals();for(int i=0;i<200;i++)wet.breathe(p,water(true,false),100,300,0);
        assertTrue(wet.recover(100,p,1,1).ticks()<100);assertTrue(wet.breathe(p,water(true,false),100,300,0).refill()>4);
        var sealed=new TissueVitals();for(int i=0;i<200;i++)sealed.breathe(p,water(false,false),100,300,0);assertEquals(new Recovery(100,0),sealed.recover(100,p,1,1));
    }
    @Test void storedBloodNeedsRealReplenishmentAndCanOnlyRescueAFiniteNumberOfTimes(){
        var p=new Profile(0,0,0,0,0,0,0,2);var v=new TissueVitals();int remaining=fill(v,p,4000,8);
        assertEquals(p.spleenCapacity(),v.spleen());assertEquals(8-v.spleen(),remaining);
        var restored=TissueVitals.read(v.write());int count=restored.spleen(),total=0;
        for(int i=0;i<count+2;i++){var r=restored.breathe(p,water(false,false),0,300,0);total+=r.airGrant();assertEquals(0,r.spent());}
        assertEquals(count*80,total);assertEquals(0,restored.spleen());
        var noFood=new TissueVitals();fill(noFood,p,5000,0);assertEquals(0,noFood.spleen());
    }
    @Test void absentSpleenKeepsItsSavedStockDormantAndExertionCanSpendItOnlyOnce(){
        var p=new Profile(0,0,0,0,0,2,0,2);var v=new TissueVitals();fill(v,p,4000,8);var saved=v.write();saved.putInt("Oxygen",0);
        var restored=TissueVitals.read(saved);int stock=restored.spleen();assertEquals(0,restored.breathe(Profile.EMPTY,water(false,false),0,300,0).airGrant());assertEquals(stock,restored.spleen());
        assertEquals(new Recovery(100,0),restored.recover(100,p,0,1));assertEquals(stock,restored.spleen());
        assertTrue(restored.recover(100,p,1,1).ticks()<100);assertEquals(stock-1,restored.spleen());assertEquals(12,restored.oxygen());
    }
    @Test void actualInjuryReducesTransportAndOnlyPaidRepairReducesItsPersistentLoad(){
        var p=new Profile(0,0,0,0,0,2,0,0);var v=new TissueVitals();fill(v,p,600,0);var healthy=TissueVitals.read(v.write());v.damaged(.5);
        assertTrue(v.oxygen()<healthy.oxygen());assertTrue(v.recover(100,p,1,.5).ticks()>healthy.recover(100,p,1,1).ticks());
        var saved=v.write();assertEquals(saved,TissueVitals.read(saved).write());float before=saved.getFloat("Strain");v.repaired(0);assertEquals(before,v.write().getFloat("Strain"));v.repaired(1);assertTrue(v.write().getFloat("Strain")<before);
    }
    @Test void externalBreathingProtectionDoesNotMintOxygenOrRefillStock(){
        var env=new Environment(false,true,false,false,true,true,false,true,false,2,1);var v=new TissueVitals();
        for(int i=0;i<1000;i++){var r=v.breathe(Profile.EMPTY,env,100,300,0);assertTrue(r.canBreathe());assertEquals(0,r.refill());assertEquals(Route.EXTERNAL,r.route());}
        assertEquals(0,v.oxygen());assertEquals(0,v.spleen());
    }
    @Test void badSavedValuesAndNonFiniteEditedStrengthRemainBounded(){
        var t=new CompoundTag();t.putInt("Oxygen",Integer.MAX_VALUE);t.putInt("Spleen",Integer.MAX_VALUE);t.putInt("GillCharge",Integer.MAX_VALUE);t.putFloat("Strain",Float.NaN);
        var v=TissueVitals.read(t);assertEquals(100,v.oxygen());assertEquals(8,v.spleen());assertEquals(0,v.write().getFloat("Strain"));assertTrue(v.write().getInt("GillCharge")<=360);
        var p=new Profile(Double.NaN,Double.POSITIVE_INFINITY,-100,1e10,0,0,0,0);assertEquals(0,p.gill());assertEquals(0,p.lung());assertEquals(8,p.book());assertTrue(p.maxAir(Integer.MAX_VALUE)<=24000);
    }
    @Test void visualExchangeUsesTheActualRouteRatherThanPulsingEveryAcquiredOrganUnderWater(){
        var b=new Bone("body",0,new Box(new V(-4,-6,-3),new V(4,6,3)),new V(0,-1,0),new V(0,0,-1),new V(1,0,0),new V(0,0,0));
        var dry=new CompoundTag();dry.putString("Route","AIR");var wet=new CompoundTag();wet.putString("Route","WATER");
        var lung=new Profile(0,2,0,0,0,0,0,0);var point=new V(4,0,3);
        assertNotEquals(point,TissueVitalMotion.field(b,lung,dry,10).point(point));
        assertEquals(point,TissueVitalMotion.field(b,lung,wet,10).point(point),"sealed pulmonary sacs do not pretend to exchange water");
        var heart=new Profile(0,0,0,0,0,0,2,0);dry.putInt("Oxygen",100);wet.putInt("Oxygen",100);
        assertEquals(point,TissueVitalMotion.field(b,heart,dry,5).point(point));assertNotEquals(point,TissueVitalMotion.field(b,heart,wet,5).point(point));
    }
    @Test void allEightAnatomiesHaveConnectedRootsAndShareASmoothBoundedSurfaceAtLongWorldTimes(){
        var box=new Box(new V(-4,-6,-3),new V(4,6,3));var up=new V(0,-1,0);var front=new V(0,0,-1);var right=new V(1,0,0);
        var state=new CompoundTag();state.putString("Route","WATER");state.putInt("Oxygen",100);state.putInt("Spleen",4);state.putInt("SpleenCapacity",4);
        for(var core:TissueCore.values())if(core.vital()){
            var b=new Bone("vital",core==TissueCore.GILL_LAMELLA?1:0,box,up,front,right,new V(0,0,0));var mesh=functional(core,b,0,42).get(0);var values=mesh.stages()[2].clone();var roots=java.util.Arrays.copyOf(values,24);
            TissueVitalMotion.module(values,b,core,state,5);assertArrayEquals(roots,java.util.Arrays.copyOf(values,24),0);for(float f:values)assertTrue(Float.isFinite(f));
        }
        var body=new Bone("body",0,box,up,front,right,new V(0,0,0));var p=new Profile(2,2,2,2,2,2,2,2);var a=TissueVitalMotion.field(body,p,state,TissueVitalMotion.clock(40_000_000,.1F));var b=TissueVitalMotion.field(body,p,state,TissueVitalMotion.clock(40_000_000,.6F));
        assertEquals(new V(4,-6,0),a.point(new V(4,-6,0)));assertEquals(4,a.point(new V(4,6,0)).x(),.0001);
        assertNotEquals(a.point(new V(4,0,3)),b.point(new V(4,0,3)));assertTrue(a.point(new V(4,0,3)).sub(new V(4,0,3)).length()<1);
        assertNull(TissueVitalMotion.field(new Bone("leg",2,box,up,front,right,new V(0,0,0)),p,state,10));
    }
}
