package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;

/** Authored victim key poses on the same normalized clock as the consuming player. Angles in radians. */
public final class ConsumeReaction {
    public record Pose(float bend, float twist, float slump, float brace, float kick, float head, float grounded) {}
    // Seconds relative to contact, pitch, twist, slump, brace, kick, head pitch.
    // Negative times keep the victim resisting until the actual blow. Contact is
    // held briefly, then limbs recoil after the torso rather than flailing uniformly.
    private static final float[][] NORMAL={{-1,7,0,0,.8F,.5F,-12},{-.10F,-12,-8,0,.9F,.35F,-18},{0,30,15,.13F,1,.75F,24},{.08F,30,15,.13F,1,.75F,24},{.23F,-12,-14,.07F,.85F,1,-20},{.52F,35,9,.18F,.35F,.7F,27},{2,45,0,.25F,0,1,32}};
    private static final float[][] HAMMER={{-1,7,0,0,.8F,.5F,-12},{-.12F,-20,-9,0,1,.3F,-28},{0,58,14,.29F,.5F,1,40},{.10F,58,14,.29F,.5F,1,40},{.23F,39,-9,.23F,.7F,.85F,23},{.55F,62,5,.32F,.2F,1,35},{2,66,0,.35F,0,1,35}};
    private static final float[][] BLADE={{-1,7,0,0,.8F,.5F,-12},{-.10F,-8,-10,0,.85F,.3F,-10},{0,-30,24,.035F,1,.6F,-32},{.08F,-30,24,.035F,1,.6F,-32},{.25F,-17,10,.06F,.8F,1,-18},{.52F,39,-18,.20F,.5F,.8F,32},{2,52,0,.29F,0,1,38}};
    private static final float[][] WHIP={{-1,7,0,0,.8F,.5F,-12},{-.10F,-14,-12,0,.9F,.5F,-18},{0,34,-21,.16F,1,.85F,25},{.08F,34,-21,.16F,1,.85F,25},{.23F,-10,13,.10F,.7F,1,-15},{.55F,40,-9,.24F,.3F,.8F,30},{2,48,0,.30F,0,1,35}};
    private ConsumeReaction() {}
    public static Pose sample(PowerForm form, float t) {
        var keys=switch(form){case HAMMERFISTS->HAMMER;case BLADE->BLADE;case WHIPFIST->WHIP;default->NORMAL;};
        t=com.codex.blacklight.forge.WingMath.clamp(t,0,1);
        float contact=(t*ConsumeVisual.duration(form)-ConsumeVisual.impactTick(form))/20F;
        int i=0;while(i<keys.length-2 && contact>keys[i+1][0])i++;
        float segmentStart=i==0?-ConsumeVisual.impactTick(form)/20F:keys[i][0];
        float a=com.codex.blacklight.forge.WingMath.clamp((contact-segmentStart)/(keys[i+1][0]-segmentStart),0,1);a=a*a*(3-2*a);
        float[] v=new float[6];for(int k=0;k<6;k++)v[k]=keys[i][k+1]+(keys[i+1][k+1]-keys[i][k+1])*a;
        // The brief contact hold must stay readable; struggle resumes only as recoil.
        float struggle=contact<0?(float)Math.sin(t*61)*.35F:contact<.10F?0:
                (float)Math.sin((contact-.1F)*28)*Math.max(0,1-(contact-.1F)/.5F)*.3F;
        float grounded=form==PowerForm.HAMMERFISTS ? com.codex.blacklight.forge.WingMath.clamp((contact+.12F)/.12F,0,1) : 0;
        grounded=grounded*grounded*(3-2*grounded);
        return new Pose((float)Math.toRadians(v[0])*(1-grounded*.8F),(float)Math.toRadians(v[1]+struggle*7),v[2]*(1-grounded),v[3],v[4]*(1-grounded*.65F),(float)Math.toRadians(v[5]+struggle*5)*(1-grounded*.8F),grounded);
    }
}
