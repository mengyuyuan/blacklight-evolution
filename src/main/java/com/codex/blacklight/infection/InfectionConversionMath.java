package com.codex.blacklight.infection;

/** Initial infection gains only. Age, inherited organs and evolution are not inputs. */
public final class InfectionConversionMath {
    private InfectionConversionMath() {}
    public static double nonnegative(double value) {
        return Double.isNaN(value)||value<=0?0:Math.min(Float.MAX_VALUE,value);
    }
    /** Continuous at the threshold: ordinary bonus below, smaller bonus on the excess. */
    public static double enhance(double original,double threshold,double ordinaryBonus,double excessBonus) {
        double base=nonnegative(original),split=nonnegative(threshold);
        return nonnegative(base+Math.min(base,split)*nonnegative(ordinaryBonus)+Math.max(0,base-split)*nonnegative(excessBonus));
    }
}
