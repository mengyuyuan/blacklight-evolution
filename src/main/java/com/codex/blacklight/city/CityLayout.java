package com.codex.blacklight.city;

/** Pure, seed-stable layout. Coordinates are blocks; no world access during planning. */
public final class CityLayout {
    public static final int SIZE=512, CELL=64, GROUND=63;
    public static final int[][] HATCHES={{20,14},{44,14},{20,50},{32,50},{44,50},{14,32},{50,32}};
    public enum Zone { NORMAL, EDGE, INFECTED, CONFLICT }
    public record Lot(int x,int z,int style,int floors,Zone zone,boolean hive) {}
    private CityLayout() {}
    public static long hash(long seed,int x,int z) {
        long n=seed^(x*0x632BE59BD9B4E019L)^(z*0x9E3779B97F4A7C15L);
        n=(n^(n>>>30))*0xBF58476D1CE4E5B9L;n=(n^(n>>>27))*0x94D049BB133111EBL;return n^(n>>>31);
    }
    public static boolean inside(int x,int z){return x>=0&&z>=0&&x<SIZE&&z<SIZE;}
    /** Smooth coarse noise makes connected tissue patches rather than per-block confetti. */
    public static double tissue(long seed,int x,int z){return noise(seed,x,z,12)*.7+noise(seed^1793,x,z,32)*.3;}
    private static double noise(long seed,int x,int z,int size){
        int cx=Math.floorDiv(x,size),cz=Math.floorDiv(z,size);
        double u=Math.floorMod(x,size)/(double)size,v=Math.floorMod(z,size)/(double)size;
        u=u*u*(3-2*u);v=v*v*(3-2*v);
        double a=unit(hash(seed,cx,cz)),b=unit(hash(seed,cx+1,cz)),c=unit(hash(seed,cx,cz+1)),d=unit(hash(seed,cx+1,cz+1));
        return (a+(b-a)*u)*(1-v)+(c+(d-c)*u)*v;
    }
    private static double unit(long hash){return (hash>>>11)*0x1.0p-53;}
    public static boolean sanctuary(int x,int z){return x<2&&z<2;}
    public static int hiveX(long seed,int index){return index==0?2+(int)Math.floorMod(hash(seed,17,19),3):5+(int)Math.floorMod(hash(seed,25,28),2);}
    public static int hiveZ(long seed,int index){return index==0?3+(int)Math.floorMod(hash(seed,33,8),3):1+(int)Math.floorMod(hash(seed,26,4),3);}
    public static boolean hive(long seed,int x,int z){return (x==hiveX(seed,0)&&z==hiveZ(seed,0))||(x==hiveX(seed,1)&&z==hiveZ(seed,1));}
    public static Zone zone(long seed,int x,int z){
        if(x<0||z<0||x>=8||z>=8||sanctuary(x,z))return Zone.NORMAL;
        int d=99;for(int i=0;i<2;i++)d=Math.min(d,Math.abs(x-hiveX(seed,i))+Math.abs(z-hiveZ(seed,i)));
        if(d<=1)return Zone.INFECTED;
        if(d==2)return Math.floorMod(hash(seed,x,z),3)==0?Zone.CONFLICT:Zone.EDGE;
        return Zone.NORMAL;
    }
    public static Lot lot(long seed,int x,int z){
        boolean hive=hive(seed,x,z);long h=hash(seed,x,z);
        int style=(int)Math.floorMod(h,5);
        if(hive)style=1;
        if(x==0&&z==0)style=0;
        return new Lot(x,z,style,style==0?0:style==1?5+(int)Math.floorMod(h>>>8,6):style==2?3:style==3?2:6,zone(seed,x,z),hive);
    }
}
