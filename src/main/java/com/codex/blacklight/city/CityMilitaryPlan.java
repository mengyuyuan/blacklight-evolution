package com.codex.blacklight.city;

import java.util.Optional;

/** Bounded, deterministic selection; never reads or changes a Minecraft world. */
public final class CityMilitaryPlan {
    public record Surface(int ground,boolean water,boolean blocked,boolean road){}
    public record Checkpoint(int x,int z,int floor,int rotation){
        public int[] world(int localX,int localZ){return switch(rotation&3){case 1->new int[]{x+16-localZ,z+localX};case 2->new int[]{x+16-localX,z+16-localZ};case 3->new int[]{x+localZ,z+16-localX};default->new int[]{x+localX,z+localZ};};}
    }
    @FunctionalInterface public interface Survey { Surface at(int x,int z); }
    public record Site(int x,int z,int floor,int rotation,boolean blackwatch){
        public int templateDepth(){return blackwatch?81:65;}
        public int width(){return (rotation&1)==0?65:templateDepth();}
        public int depth(){return (rotation&1)==0?templateDepth():65;}
        public int[] local(int worldX,int worldZ){int dx=worldX-x,dz=worldZ-z;return switch(rotation&3){case 1->new int[]{dz,templateDepth()-1-dx};case 2->new int[]{64-dx,templateDepth()-1-dz};case 3->new int[]{64-dz,dx};default->new int[]{dx,dz};};}
        public int[] world(int localX,int localZ){
            return switch(rotation&3){
                case 1->new int[]{x+templateDepth()-1-localZ,z+localX};
                case 2->new int[]{x+64-localX,z+templateDepth()-1-localZ};
                case 3->new int[]{x+localZ,z+64-localX};
                default->new int[]{x+localX,z+localZ};
            };
        }
    }
    public static Optional<Site> find(long seed,int chunkX,int chunkZ,boolean blackwatch,boolean roadRequired,Survey survey){
        int first=(int)(mix(seed^((long)chunkX<<32)^chunkZ)&3);
        for(int ring=0;ring<=4;ring++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
            int x=chunkX*16-32+dx*16,z=chunkZ*16-32+dz*16;
            for(int i=0;i<4;i++){
                int rotation=(first+i)&3;var sample=new Site(x,z,0,rotation,blackwatch);
                // Native starts are at least 13 chunks apart. Keep disjoint 208-block
                // windows, also inside vanilla's eight-chunk structure reference range.
                if(x<chunkX*16-96||z<chunkZ*16-96||x+sample.width()-1>chunkX*16+111||z+sample.depth()-1>chunkZ*16+111)continue;
                int floor=coarseFloor(sample,survey);if(floor==Integer.MIN_VALUE)continue;
                var site=new Site(x,z,floor,rotation,blackwatch);
                if(roadRequired&&!adjacentRoad(site,survey))continue;
                if(validFootprint(site,survey))return Optional.of(site);
            }
        }
        return Optional.empty();
    }
    private static int coarseFloor(Site site,Survey survey){
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for(int x=0;x<=site.width();x+=8)for(int z=0;z<=site.depth();z+=8){
            var s=survey.at(site.x()+Math.min(x,site.width()-1),site.z()+Math.min(z,site.depth()-1));
            if(s.water()||s.blocked()||s.road())return Integer.MIN_VALUE;
            low=Math.min(low,s.ground());high=Math.max(high,s.ground());
            if(high-low>3)return Integer.MIN_VALUE;
        }
        return high;
    }
    public static boolean validFootprint(Site site,Survey survey){
        // A narrow building between the fast probes must still remain untouched.
        for(int x=0;x<site.width();x++)for(int z=0;z<site.depth();z++){
            var s=survey.at(site.x()+x,site.z()+z);
            if(s.water()||s.blocked()||s.road()||s.ground()>site.floor()||site.floor()-s.ground()>3)return false;
        }
        return true;
    }
    public static Optional<Checkpoint> checkpoint(Site base,int chunkX,int chunkZ,Survey survey){
        int[] entry=base.world(32,base.templateDepth()-1);
        int dx=switch(base.rotation()&3){case 1->-1;case 3->1;default->0;},dz=switch(base.rotation()&3){case 0->1;case 2->-1;default->0;};
        for(int distance=1;distance<=16;distance++){
            int x=entry[0]+dx*distance,z=entry[1]+dz*distance;if(!survey.at(x,z).road())continue;
            for(int turn=0;turn<2;turn++)for(int shift:new int[]{0,48,-48}){
                int cx=x+(turn==1?shift:0),cz=z+(turn==0?shift:0);
                if(!survey.at(cx,cz).road()||!survey.at(cx+(turn==1?8:0),cz+(turn==0?8:0)).road()||!survey.at(cx-(turn==1?8:0),cz-(turn==0?8:0)).road())continue;
                int minX=cx-8,minZ=cz-8;
                if(minX<chunkX*16-96||minZ<chunkZ*16-96||minX+16>chunkX*16+111||minZ+16>chunkZ*16+111)continue;
                if(minX<=base.x()+base.width()-1&&minX+16>=base.x()&&minZ<=base.z()+base.depth()-1&&minZ+16>=base.z())continue;
                boolean safe=true;
                for(int px=minX;px<=minX+16&&safe;px++)for(int pz=minZ;pz<=minZ+16;pz++){
                    var s=survey.at(px,pz);if(s.water()||s.blocked()||s.ground()>base.floor()||base.floor()-s.ground()>3){safe=false;break;}
                }
                if(safe)return Optional.of(new Checkpoint(minX,minZ,base.floor(),turn));
            }
        }return Optional.empty();
    }
    private static boolean adjacentRoad(Site site,Survey survey){
        int[] entry=site.world(32,site.templateDepth()-1);
        int dx=switch(site.rotation()&3){case 1->-1;case 3->1;default->0;};
        int dz=switch(site.rotation()&3){case 0->1;case 2->-1;default->0;};
        for(int distance=1;distance<=16;distance++){
            var s=survey.at(entry[0]+dx*distance,entry[1]+dz*distance);
            if(s.water()||s.blocked()||Math.abs(s.ground()-site.floor())>3)return false;
            if(s.road())return true;
        }
        return false;
    }
    private static long mix(long value){value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;value=(value^(value>>>27))*0x94d049bb133111ebL;return value^(value>>>31);}
    private CityMilitaryPlan(){}
}
