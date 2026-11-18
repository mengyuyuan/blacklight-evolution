package com.codex.blacklight.military.aircraft;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/** P1 Bank::select10a7e250, used after the bank's own conditions pass.
 * Conditions must be evaluated on each pass: a strafe-token request is a real
 * mutation, and cannot be replaced by a snapshot of eligible branches.
 */
final class HelicopterTacticBank {
    private HelicopterTacticBank(){}
    enum Tactic {
        CIRCLE(50), STRAFE(100), SIMPLE(30), HOVER(10000);
        final float weight;
        Tactic(float weight){this.weight=weight;}
    }
    record Branch<T>(T value,float weight,BooleanSupplier eligible){
        Branch {
            Objects.requireNonNull(value);Objects.requireNonNull(eligible);
            if(!Float.isFinite(weight))throw new IllegalArgumentException("Nonfinite branch weight");
        }
    }
    /** Returns null when no branch is accepted, including when eligibility
     * changes on the second pass and its accepted weights no longer reach draw.
     */
    static <T> T choose(List<Branch<T>> branches,DoubleSupplier random){
        Objects.requireNonNull(random);
        // Keep structural order fixed while allowing each condition's own state
        // to change, just like the source sibling list during selection.
        var ordered=List.copyOf(branches);float total=0;
        for(var branch:ordered){
            if(!branch.eligible().getAsBoolean())continue;
            if(branch.weight()<=0)return branch.value();
            total+=branch.weight();
        }
        if(total<=0)return null;
        if(!Float.isFinite(total))throw new IllegalArgumentException("Branch weight overflow");
        double sample=random.getAsDouble();
        if(!Double.isFinite(sample)||sample<0||sample>=1)throw new IllegalArgumentException("Random sample outside [0,1)");
        float draw=(float)(sample*total),accumulated=0;
        for(var branch:ordered){
            if(!branch.eligible().getAsBoolean()||branch.weight()<=0)continue;
            accumulated+=branch.weight();
            if(accumulated>=draw)return branch.value();
        }
        return null;
    }
}
