package com.codex.blacklight.world;

/** Pure voxel layout shared by world generation and offline previews. Coordinates are
 * relative to the centre of the chamber floor; KEEP never changes existing terrain. */
public final class ColossalHiveLayout {
    public static final int RADIUS=56, MIN_Y=-10, MAX_Y=64;
    public static final int CHAMBER_CLEAR_RADIUS=23, CHAMBER_CLEAR_HEIGHT=42;
    public static final int CORE_X=28, CORE_Y=1, CORE_Z=8;
    public static final int[][] NUTRIENT_NODES={{-26,1,0},{26,1,0},{0,1,-26},{0,1,26}};
    public enum Material {
        KEEP, AIR, INFECTED_SOIL, INFECTED_STONE, TISSUE_FIBER, HIVE_VEIN,
        HIVE_MEMBRANE, HIVE_BONE, NUTRIENT_NODE, HIVE_FLESH, HIVE_CARAPACE, HIVE_SAC
    }
    private ColossalHiveLayout() {}

    public static Material materialAt(int x,int y,int z,long seed) {
        if(Math.abs(x)>RADIUS||Math.abs(z)>RADIUS||y<MIN_Y||y>MAX_Y)return Material.KEEP;
        double radius=Math.hypot(x,z), angle=Math.atan2(z,x);
        double ripple=Math.sin(angle*7+.4)*1.15+Math.sin(z*.19+x*.09)*.65;
        boolean footprint=ellipse(x,z,0,0,40,37)||ellipse(x,z,-29,8,19,24)||ellipse(x,z,29,8,19,24);
        boolean frontMouth=z<-20&&z>=-47&&Math.abs(x)<9&&y>=1&&y<15-Math.abs(x)*.55;
        boolean sideMouth=x>34&&x<51&&Math.abs(z-8)<5&&y>=1&&y<10;
        long noise=hash(x,y,z,seed);
        // A flat load-bearing chamber floor, with genuine layered subsoil beneath it.
        if(footprint&&y<=0) {
            if(y<-4)return Material.INFECTED_STONE;
            if(y<0)return Math.floorMod(noise,13)==0?Material.TISSUE_FIBER:Material.INFECTED_SOIL;
            if(radius<23)return Math.abs(Math.sin(x*.10+z*.16))<.07?Material.HIVE_VEIN:Material.HIVE_FLESH;
            return Math.floorMod(noise,9)==0?Material.HIVE_VEIN:Material.TISSUE_FIBER;
        }
        // These four exposed feeding nodes sit outside the giant's unobstructed arena.
        for(int[] n:NUTRIENT_NODES) {
            int dx=x-n[0], dz=z-n[2];
            if(dx==0&&dz==0&&y==1)return Material.NUTRIENT_NODE;
            if(Math.abs(dx)<=1&&Math.abs(dz)<=1&&y==1&&(dx!=0||dz!=0))return Material.HIVE_BONE;
        }
        boolean centralClear=radius<CHAMBER_CLEAR_RADIUS&&y>=1&&y<=CHAMBER_CLEAR_HEIGHT;
        boolean outer=ellipsoid(x,y,z,0,20,0,41+ripple,37,38+ripple)
                ||ellipsoid(x,y,z,-29,16,8,20,28,25)
                ||ellipsoid(x,y,z,29,16,8,20,28,25);
        boolean room=centralClear||ellipsoid(x,y,z,0,21,0,34,31,31)
                ||ellipsoid(x,y,z,-29,17,8,16,23,20)
                ||ellipsoid(x,y,z,29,17,8,16,23,20);
        if(frontMouth||sideMouth)return Material.AIR;
        if(room&&y>=1) {
            // Rib columns, nutrient sacs and hanging tendons leave the arena intact.
            if(!centralClear&&radius>26&&y<15&&y>3
                    &&Math.abs(Math.sin(angle*6+.2))<.045)return Material.HIVE_BONE;
            if(y>44&&y<49&&Math.abs(x%11)<=1&&Math.abs(z%13)<=1)return Material.HIVE_SAC;
            return Material.AIR;
        }
        if(outer&&y>0) {
            double ribs=Math.abs(Math.sin(angle*7+Math.sin(y*.065)*.12));
            if(ribs<.09&&radius>23)return Material.HIVE_BONE;
            if(Math.abs(Math.sin(y*.23+angle*3))<.11)return Material.HIVE_VEIN;
            if(Math.sin(y*.21+angle*2.5)>.38)return Material.HIVE_CARAPACE;
            if(Math.floorMod(noise,31)==0)return Material.HIVE_SAC;
            return Math.floorMod(noise,7)==0?Material.HIVE_MEMBRANE:Material.HIVE_FLESH;
        }
        // Broken dorsal crown has a stepped silhouette instead of one smooth dome.
        for(int k=-2;k<=2;k++) {
            int zz=k*9;
            int top=61-Math.abs(k)*3;
            if(y>=50&&y<=top&&Math.abs(x)<=Math.max(0,(top-y)/3)
                    &&Math.abs(z-zz)<=2)return Material.HIVE_BONE;
        }
        // Infected apron: raised root ridges, separate nodules and shallow dry depressions.
        if(radius<=RADIUS&&radius>31) {
            double fade=Math.max(0,(RADIUS-radius)/(RADIUS-31.0));
            double root=Math.abs(Math.sin(angle*9+radius*.047));
            int top=(int)Math.floor((2+Math.sin(x*.15)*Math.cos(z*.12)*2)*fade);
            if(root<.09)top+=(int)Math.ceil(4*fade);
            if(Math.sin(x*.11-z*.16)>.82&&root>.3)top-=2;
            if(y<=top&&y>=-7) {
                if(y==top&&root<.09)return Material.HIVE_VEIN;
                if(y==top)return Material.INFECTED_SOIL;
                return y>=top-2?Material.TISSUE_FIBER:Material.INFECTED_STONE;
            }
            if(y>top&&y<6&&root<.30&&radius<51)return Material.AIR;
        }
        return Material.KEEP;
    }

    public static int[] preyPosition(int index) {
        double angle=index*Math.PI*2/20;
        double radius=index%2==0?12:16;
        return new int[]{(int)Math.round(Math.cos(angle)*radius),1,(int)Math.round(Math.sin(angle)*radius)};
    }
    private static boolean ellipse(double x,double z,double cx,double cz,double rx,double rz) {
        return square((x-cx)/rx)+square((z-cz)/rz)<=1;
    }
    private static boolean ellipsoid(double x,double y,double z,double cx,double cy,double cz,double rx,double ry,double rz) {
        return square((x-cx)/rx)+square((y-cy)/ry)+square((z-cz)/rz)<=1;
    }
    private static double square(double a) { return a*a; }
    private static long hash(int x,int y,int z,long seed) {
        long n=seed^x*341873128712L^y*132897987541L^z*42317861L;
        n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;
        return n^(n>>>27);
    }
}
