package com.codex.blacklight.combat;

import java.util.Arrays;

/** The paired scene's ConsumeEffect2 propagation on an input skeleton in source units. */
public final class ConsumeSkinGrowth {
    private static final float SEED = .001F;
    // P1 integrates the supplied frame delta. Fixed MC sampling prevents camera frame rate
    // and repeated render passes from changing the result at a particular scene age.
    private static final int STEPS_PER_SECOND = 60;

    private ConsumeSkinGrowth() {}

    public static final class Weights {
        private final float[] proximal, distal;

        private Weights(int count) {
            proximal = new float[count];
            distal = new float[count];
        }
        private Weights(Weights source) {
            proximal = source.proximal.clone();
            distal = source.distal.clone();
        }

        /** Valid native fractions stored at each end of the source bone. */
        public float proximal(int bone) { return proximal[bone]; }
        public float distal(int bone) { return distal[bone]; }

        /** Coverage is zero for the original skin and one for the consume material.
         * Interpolate along each influencing bone first, then blend by the skin weights. */
        public float coverage(int bone, float distalWeight) {
            float t = clamp(distalWeight);
            return proximal[bone] + (distal[bone] - proximal[bone]) * t;
        }
        public float bone(int index) { return coverage(index, .5F); }

        /** Original six-byte per-vertex table: the two coefficients are independent.
         * Coordinates must be in P1 units. Clamp only after combining the skin influences. */
        public float influence(int bone, float distanceToJoint, float distanceToLastChild) {
            return proximal[bone]*(distanceByte(distanceToLastChild)*(1F/255))
                    + distal[bone]*(distanceByte(distanceToJoint)*(1F/255));
        }
        private static int distanceByte(float distance) {
            return distance <= 1F ? 255 : (int)((1F/distance)*255F);
        }
    }

    /** parents use -1 for a root. Each length measures that bone towards its child, in P1 units. */
    public static final class Skeleton {
        private final int[] parents;
        private final int[][] children;
        private final float[] lengths;

        public Skeleton(int[] parents, float[] lengths) {
            int count = parents.length;
            if (count == 0 || count != lengths.length) throw new IllegalArgumentException("Missing bone spans");
            this.parents = parents.clone();
            this.lengths = lengths.clone();
            int[] totals = new int[count];
            for (int i = 0; i < count; i++) {
                int parent = parents[i];
                if (parent < -1 || parent >= count || parent == i || !Float.isFinite(lengths[i]) || lengths[i] < 0)
                    throw new IllegalArgumentException("Invalid consume skeleton at bone " + i);
                if (parent >= 0) totals[parent]++;
                this.lengths[i] = Math.max(.00001F, lengths[i]);
            }
            children = new int[count][];
            for (int i = 0; i < count; i++) children[i] = new int[totals[i]];
            Arrays.fill(totals, 0);
            for (int i = 0; i < count; i++) {
                int parent = parents[i];
                if (parent >= 0) children[parent][totals[parent]++] = i;
                for (int at = parent, depth = 0; at >= 0; at = parents[at])
                    if (++depth >= count) throw new IllegalArgumentException("Cyclic consume skeleton");
            }
        }

        private Weights initial(ConsumeReceiverEffect.Cue cue, int seed) {
            Weights weights = new Weights(parents.length);
            if (seed < 0 || seed >= parents.length) throw new IllegalArgumentException("Invalid consume seed");
            boolean backward = cue.direction() == ConsumeReceiverEffect.Direction.BACKWARD;
            if (backward) {
                Arrays.fill(weights.proximal, 1);
                Arrays.fill(weights.distal, 1);
            }
            weights.proximal[seed] = weights.distal[seed] = SEED;
            return weights;
        }

        /** ForwardBackward changes direction in place. In particular, saturated central bones
         * only start to retreat once a neighbouring, partly covered bone reaches them. */
        public Weights[] forwardBackward(int seed, float rate, float threshold, float reverse, float end) {
            int last = (int)Math.ceil(end * STEPS_PER_SECOND);
            Weights[] frames = new Weights[last + 1];
            Weights weights = new Weights(parents.length);
            weights.proximal[seed] = weights.distal[seed] = SEED;
            frames[0] = new Weights(weights);
            for (int i = 1; i <= last; i++) {
                step(weights, 4F * rate / STEPS_PER_SECOND, threshold, (i - 1) >= Math.round(reverse * STEPS_PER_SECOND));
                frames[i] = new Weights(weights);
            }
            return frames;
        }

        public Playback playback(ConsumeReceiverEffect.Cue cue, int seed) { return new Playback(this, cue, seed); }
        public Weights sample(ConsumeReceiverEffect.Cue cue, float ageSeconds, int seed) {
            return playback(cue, seed).sample(ageSeconds);
        }

        private boolean step(Weights w, float delta, float threshold, boolean backward) {
            float sign = backward ? -1F : 1F;
            for (int bone = 0; bone < parents.length; bone++) {
                boolean active = backward ? w.proximal[bone] < 1 || w.distal[bone] < 1
                                          : w.proximal[bone] > 0 || w.distal[bone] > 0;
                if (!active) continue;
                w.proximal[bone] += sign*delta;
                w.distal[bone] += sign*delta;
                float toParent = backward ? 1-w.proximal[bone] : w.proximal[bone];
                if (toParent > threshold) passUp(w, parents[bone], toParent*delta, sign);
                // Passing up the tree does not write back down into this bone.
                float toChildren = backward ? 1-w.distal[bone] : w.distal[bone];
                if (toChildren > threshold)
                    for (int child : children[bone]) passDown(w, child, toChildren*delta, sign);
            }
            boolean settled = true;
            float target = backward ? 0 : 1;
            for (int i = 0; i < parents.length; i++) {
                w.proximal[i] = clamp(w.proximal[i]);
                w.distal[i] = clamp(w.distal[i]);
                settled &= w.proximal[i] == target && w.distal[i] == target;
            }
            return settled;
        }

        private void passUp(Weights w, int bone, float incoming, float sign) {
            while (bone >= 0 && incoming > 0) {
                w.distal[bone] += sign*incoming;
                incoming = Math.max(0F, incoming - incoming/lengths[bone]);
                w.proximal[bone] += sign*incoming;
                bone = parents[bone];
            }
        }

        private void passDown(Weights w, int bone, float incoming, float sign) {
            if (incoming <= 0) return;
            w.proximal[bone] += sign*incoming;
            float remaining = Math.max(0F, incoming - incoming/lengths[bone]);
            w.distal[bone] += sign*remaining;
            if (remaining > 0)
                for (int child : children[bone]) passDown(w, child, remaining, sign);
        }
    }

    /** One receiver scene. Keep the last committed 60-Hz state separate from fractional
     * previews so render passes can repeat or seek without feeding a preview back into the simulation. */
    public static final class Playback {
        private final Skeleton skeleton;
        private final ConsumeReceiverEffect.Cue cue;
        private final int seed;
        private final Weights inactive;
        private Weights committed, lastPreview;
        private int committedFrame;
        private float lastAge = Float.NaN;
        private boolean settled;

        private Playback(Skeleton skeleton, ConsumeReceiverEffect.Cue cue, int seed) {
            this.skeleton = skeleton;
            this.cue = cue;
            this.seed = seed;
            this.inactive = new Weights(skeleton.parents.length);
        }

        public Weights sample(float ageSeconds) {
            if (cue == null || ageSeconds < 0 || !Float.isFinite(ageSeconds)) return inactive;
            if (ageSeconds == lastAge) return lastPreview;
            int frame = (int)Math.floor((double)ageSeconds * STEPS_PER_SECOND);
            if (committed == null || frame < committedFrame) {
                committed = skeleton.initial(cue, seed);
                committedFrame = 0;
                settled = false;
            }
            float speed = 4*cue.spreadRate();
            boolean backward = cue.direction() == ConsumeReceiverEffect.Direction.BACKWARD;
            while (committedFrame < frame && !settled) {
                settled = skeleton.step(committed, speed/STEPS_PER_SECOND, cue.growthThreshold(), backward);
                committedFrame++;
            }
            if (settled) committedFrame = frame;
            // Callers may keep a sample while another age is rendered. Never return the mutable state.
            lastPreview = new Weights(committed);
            float remainder = (float)(ageSeconds - frame/(double)STEPS_PER_SECOND);
            if (remainder > 0 && !settled)
                skeleton.step(lastPreview, speed*remainder, cue.growthThreshold(), backward);
            lastAge = ageSeconds;
            return lastPreview;
        }
    }

    public static Weights sample(ConsumeReceiverEffect.Cue cue, float ageSeconds, int[] parents,
                                 float[] lengths, int seed) {
        return new Skeleton(parents, lengths).sample(cue, ageSeconds, seed);
    }

    private static float clamp(float n) { return Math.max(0, Math.min(1, n)); }
}
