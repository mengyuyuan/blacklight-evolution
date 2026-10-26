package com.codex.blacklight.city;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

/** Exercises actual scene decoding, rather than only atlas city names. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class CitySurfaceGameTest {
    @GameTest(template="wall_test",timeoutTicks=100,batch="full_city_surface")
    public static void BothCitiesRetainTheirAuthoredCoresWithoutTheWholeMunicipalArchive(GameTestHelper h){
        var shenzhen=WorldTerrain.column(h.getLevel().getSeed(),670,820);
        h.assertTrue(shenzhen.core!=null&&shenzhen.top>=63,"Shenzhen retains its authored civic-center surface");
        int x=WorldAtlas.worldX(-118.2455),z=WorldAtlas.worldZ(34.0561);
        var la=WorldTerrain.column(h.getLevel().getSeed(),x,z);
        h.assertTrue(!CityGenerationProfile.usesArchive()&&la.arnis==null,"default geographic generation must not initialize a baked city archive");
        h.assertTrue(la.scenery,"LA civic center retains its detailed source scenery");
        h.assertTrue(la.top>0&&la.top<WorldTerrain.HEIGHT,"decoded city fits its dimension height");
        boolean surface=false;
        for(int y=la.top;y>=Math.max(1,la.top-4);y--)surface|=!la.state(y).isAir();
        h.assertTrue(surface,"native region palette and chunk block storage yield a real surface");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=100,batch="geographic_city_surface")
    public static void MappedBuildingsAndRoadsGenerateBeyondTheCoreAndKeepTheirGeography(GameTestHelper h){
        int x=WorldAtlas.worldX(-118.3287),z=WorldAtlas.worldZ(34.1016);
        var tile=WorldAtlas.tile(x,z);boolean building=false,road=false;
        for(var f:tile.features){
            if(f.kind==8&&!building){
                outer:for(int iz=0;iz<10;iz++)for(int ix=0;ix<10;ix++){
                    int wx=(int)Math.floor(f.x0+(f.x1-f.x0)*(ix+.5)/10),wz=(int)Math.floor(f.z0+(f.z1-f.z0)*(iz+.5)/10);
                    if(!f.contains(wx+.5,wz+.5)||LosAngelesMap.inside(wx,wz))continue;
                    var a=WorldTerrain.column(1,wx,wz);var b=WorldTerrain.column(98765,wx,wz);
                    h.assertTrue(a.arnis==null&&!a.scenery,"outlying streets use geographic generation");
                    h.assertTrue(a.parts.stream().anyMatch(part->part.feature.id==f.id),"mapped building footprint survives");
                    h.assertTrue(a.top==b.top&&a.ground==b.ground,"changing the world seed must not move mapped buildings or terrain");
                    h.assertTrue(!a.state(f.base+f.height).isAir(),"mapped building has a generated roof");
                    building=true;break outer;
                }
            }
            if(f.kind==6&&f.layer==0&&f.lines.length>0&&!road){
                var line=f.lines[0];if(line.length<4)continue;
                int wx=(int)Math.floor((line[0]+line[2])/2),wz=(int)Math.floor((line[1]+line[3])/2);
                var c=WorldTerrain.column(1,wx,wz);
                h.assertTrue(c.road==6,"mapped road generates beyond the authored core");road=true;
            }
            if(building&&road)break;
        }
        h.assertTrue(building&&road,"real Hollywood tile contains generated buildings and roads");
        h.assertTrue("los_angeles".equals(WorldAtlas.regionId(x,z)),"outlying scene remains within LA geography");h.succeed();
    }
}
