package com.codex.blacklight.city;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CityLayoutTest {
    @Test void everySeedKeepsSanctuaryAndExactlyTwoHives(){
        for(long seed=-100;seed<100;seed++){
            int hives=0,normal=0,infected=0;
            for(int x=0;x<8;x++)for(int z=0;z<8;z++){
                var a=CityLayout.lot(seed,x,z);assertEquals(a,CityLayout.lot(seed,x,z));
                if(a.hive()){hives++;assertEquals(CityLayout.Zone.INFECTED,a.zone());assertTrue(a.floors()>=5);}
                if(a.zone()==CityLayout.Zone.NORMAL)normal++;
                if(a.zone()==CityLayout.Zone.INFECTED)infected++;
                if(CityLayout.sanctuary(x,z))assertEquals(CityLayout.Zone.NORMAL,a.zone());
            }
            assertEquals(2,hives);assertTrue(normal>=4);assertTrue(infected>=5);
        }
    }
    @Test void worldSeedsChangeBuildingAndInfectionPlan(){
        int changes=0;for(int x=0;x<8;x++)for(int z=0;z<8;z++)if(!CityLayout.lot(1,x,z).equals(CityLayout.lot(2,x,z)))changes++;
        assertTrue(changes>16);
    }
    @Test void boundaryIsHalfOpen(){assertFalse(CityLayout.inside(-1,0));assertFalse(CityLayout.inside(512,2));assertTrue(CityLayout.inside(511,511));}
}
