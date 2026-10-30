package com.codex.blacklight.combat;

/** Audited ordinary Hunter branch. Weapon-specific human branches exclude brawler.
 * See docs/RC24-hunter-consume.md for source paths and adaptation boundaries. */
public final class HunterConsume {
    public static final float COMPLETE_SECONDS=2.5F, RECEIVER_END_SECONDS=94F/30;
    public static final float EFFECT_BEGIN_SECONDS=20F/30, EFFECT_REVERSE_SECONDS=1.5F;
    public static final int COMPLETE_TICKS=50, ACTOR_TICKS=71;
    // Minecraft corpse retention policy, not a recovered source event.
    public static final int REMAINS_TICKS=100;
    private record Profile(float actorSeconds,float[][] scale) {}
    private static Profile profile;
    private HunterConsume() {}
    private static synchronized Profile profile() {
        if(profile!=null)return profile;
        var data=MotionResources.read("hunter-consume.data",64*1024);
        if(data.isEmpty())return profile=new Profile(11F/3,new float[0][]);
        var root=data.orElseThrow();
        if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Hunter consume schema");
        float seconds=root.get("actorClipSeconds").getAsFloat();
        if(!Float.isFinite(seconds)||seconds<COMPLETE_SECONDS||seconds>10)throw new IllegalArgumentException("Hunter actor duration");
        var rows=root.getAsJsonArray("jawScale");
        if(rows.isEmpty()||rows.size()>1000)throw new IllegalArgumentException("Hunter scale count");
        float[][] scale=new float[rows.size()][4];float previous=-1;
        for(int i=0;i<scale.length;i++) {
            var row=rows.get(i).getAsJsonArray();
            if(row.size()!=4)throw new IllegalArgumentException("Hunter scale width");
            for(int j=0;j<4;j++) {
                float v=row.get(j).getAsFloat();
                if(!Float.isFinite(v)||v<0||v>10||(j>0&&v==0))throw new IllegalArgumentException("Hunter scale value");
                scale[i][j]=v;
            }
            if(scale[i][0]<=previous)throw new IllegalArgumentException("Hunter scale time order");
            previous=scale[i][0];
        }
        return profile=new Profile(seconds,scale);
    }
    public static float seconds(ConsumeVisual visual,long now,float partial) {
        return Math.max(0,(now-visual.started()+partial)/20F);
    }
    public static float actorProgress(float seconds) { return com.codex.blacklight.forge.WingMath.clamp(seconds/profile().actorSeconds(),0,1); }
    public static float[] jawScale(float seconds) {
        var keys=profile().scale();
        if(keys.length==0)return new float[]{1,1,1};
        int index=0;while(index+1<keys.length&&keys[index+1][0]<=seconds)index++;
        var a=keys[index];var b=keys[Math.min(index+1,keys.length-1)];
        float t=b[0]>a[0]?com.codex.blacklight.forge.WingMath.clamp((seconds-a[0])/(b[0]-a[0]),0,1):0;
        return new float[]{a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t,a[3]+(b[3]-a[3])*t};
    }
}
