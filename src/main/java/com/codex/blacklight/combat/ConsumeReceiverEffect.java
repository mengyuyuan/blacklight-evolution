package com.codex.blacklight.combat;

import java.util.Map;

/** Material tracks owned by the ordinary paired receiver branches of alex_fig. */
public final class ConsumeReceiverEffect {
    public enum Joint { MOTION_ROOT, NECK, SPINE_2, ANKLE_L }
    public enum Direction { FORWARD, BACKWARD }

    /** offset identifies the source track. Its clock starts with the receiver scene. */
    public record Cue(int offset, float startSeconds, Joint joint, Direction direction,
                      float spreadRate, float growthThreshold) {
        public int startFrame() { return Math.round(startSeconds * 30); }
        public float age(float sceneSeconds) { return sceneSeconds - startSeconds; }

        // Shared fields of these 13 tracks. TimeReverse is a serialized F32 even for
        // single-direction modes. None of these tracks requests a mid-scene reversal.
        public float timeReverse() { return 1F; }
        public float timeEnd() { return -1F; }
        public boolean useTimeReveal() { return false; }
        public float timeReveal() { return 1F; }
        public boolean useSecondStartPoint() { return false; }
    }

    private static final Map<String, Cue> CUES = Map.ofEntries(
            Map.entry("claws",            new Cue(439604, .4F,       Joint.MOTION_ROOT, Direction.FORWARD, .5F,  .75F)),
            Map.entry("claws_head",       new Cue(442264, 2F,        Joint.NECK,        Direction.FORWARD, .15F, .2F)),
            Map.entry("hammer_smush",     new Cue(447780, 2F,        Joint.NECK,        Direction.FORWARD, .1F,  .2F)),
            Map.entry("hammer",           new Cue(454320, 2.4F,      Joint.NECK,        Direction.FORWARD, .2F,  .1F)),
            Map.entry("muscle_heart",     new Cue(461368, 1.33333F,  Joint.ANKLE_L,     Direction.BACKWARD,.1F,  .2F)),
            Map.entry("muscle",           new Cue(467960, .8F,       Joint.MOTION_ROOT, Direction.FORWARD, .5F,  .1F)),
            Map.entry("whip_lash",        new Cue(472496, 1.4F,      Joint.NECK,        Direction.FORWARD, .7F,  .8F)),
            Map.entry("whip",             new Cue(478488, 1.4F,      Joint.MOTION_ROOT, Direction.FORWARD, .5F,  .75F)),
            Map.entry("blade",            new Cue(498428, .666667F,  Joint.MOTION_ROOT, Direction.FORWARD, .5F,  .75F)),
            Map.entry("normal_beatdown",  new Cue(504044, 2.93333F,  Joint.NECK,        Direction.FORWARD, .4F,  .2F)),
            Map.entry("normal_head_stomp",new Cue(510036, .666667F,  Joint.NECK,        Direction.FORWARD, .9F,  .8F)),
            Map.entry("normal_knee_head", new Cue(516780, .666667F,  Joint.SPINE_2,     Direction.FORWARD, .5F,  .6F)),
            Map.entry("normal",           new Cue(531524, .666667F,  Joint.SPINE_2,     Direction.FORWARD, .5F,  .6F)));

    private ConsumeReceiverEffect() {}

    public static Cue forClip(String clip) {
        return clip == null ? null : CUES.get(clip);
    }
}
