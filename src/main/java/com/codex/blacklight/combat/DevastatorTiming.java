package com.codex.blacklight.combat;

import com.google.gson.JsonObject;

/** FIG target/effect times are relative to entering the formal release/antic.
 * World distances and damage use explicit Minecraft unit conversions. */
public record DevastatorTiming(float effectStart,float effectEnd,float damageStart,float damageEnd,
                                float targetStart,float targetStop,float radius,float damage,int targets) {
    /** Measured Alex retarget scale .8721314614 times native player .9375. */
    public static final float SOURCE_WORLD_SCALE=.8176232451F;
    public static DevastatorTiming of(WeaponMove move) {
        var definition=LocalWeaponMotion.find(move.id).orElseThrow(()->new IllegalStateException("Missing source skill "+move.id));
        var root=definition.animation().getAsJsonObject("blacklight");
        var f=root.has("devastator")?root.getAsJsonObject("devastator"):new JsonObject();
        int kind=DevastatorSystem.kind(move);
        boolean air=move.id.startsWith("dev_air_");
        float begin=kind==0?1.766667F:kind==1?(air?1.4F:1.9F):kind==2?1.5F:0;
        float end=kind==0?4.766667F:kind==1?(air?4F:4.5F):kind==2?4.5F:.9F;
        float damageStart=kind==0?begin+.333333F:kind==1?(air?1.416667F:1.933333F):kind==2?2.166667F:0;
        float radiusSource=kind==0?40:kind==1?30:kind==2?200:24;
        float damageSource=kind==0?500:kind==1?400:kind==2?1250:550;
        begin=value(f,"effectStart",begin);end=value(f,"effectEnd",end);
        damageStart=value(f,"damageStart",damageStart);
        float damageEnd=value(f,"damageEnd",end);
        float targetStart=value(f,"targetBeginStart",damageStart),targetStop=value(f,"targetBeginStop",damageStart+.666667F);
        // Bullet's zero fields are undecoded source sentinels, not zero damage.
        // These three values are explicitly Minecraft tuning until decoded.
        float sourceRadius=value(f,"radiusSource",radiusSource), sourceDamage=value(f,"damageSource",damageSource);
        float sourceTargets=value(f,"targetCountSource",kind==2?1:kind==0?40:50);
        if(kind==3){if(sourceRadius<=0)sourceRadius=24;if(sourceDamage<=0)sourceDamage=550;if(sourceTargets<=0)sourceTargets=16;}
        float radius=kind==3?6:sourceRadius*SOURCE_WORLD_SCALE, damage=sourceDamage*.04F;
        int targets=Math.clamp(Math.round(sourceTargets),1,kind==2?1:64);
        if(end<=begin||damageEnd<damageStart||radius<=0||damage<=0)throw new IllegalStateException("Invalid source skill effect window "+move.id);
        return new DevastatorTiming(begin,end,damageStart,damageEnd,targetStart,targetStop,radius,damage,targets);
    }
    private static float value(JsonObject f,String key,float fallback){float v=f.has(key)?f.get(key).getAsFloat():fallback;if(!Float.isFinite(v))throw new IllegalStateException("Non-finite source field "+key);return v;}
    /** Front propagation spreads actual target contacts; each victim is hit once. */
    public double contactAt(double distance){return Math.max(damageStart,targetStart+(targetStop-targetStart)*Math.clamp(distance/radius,0,1));}
}
