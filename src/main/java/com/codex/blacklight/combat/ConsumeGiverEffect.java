package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;

/** alex_fig ordinary receivers call giver/cyclic -> AlexBody, branch 110. */
public final class ConsumeGiverEffect {
    public static final float REVERSE_SECONDS=69F/30, END_SECONDS=200F/30;
    private ConsumeGiverEffect() {}

    public static float cue(PowerForm form,int variant){
        if(form!=PowerForm.NONE&&form!=PowerForm.CLAWS&&form!=PowerForm.HAMMERFISTS
                &&form!=PowerForm.MUSCLE_MASS&&form!=PowerForm.WHIPFIST&&form!=PowerForm.BLADE)return Float.NaN;
        int frame=switch(ConsumeSequence.key(form,variant)){
            case "normal" -> 55;
            case "normal_head_stomp" -> 35;
            case "normal_beatdown" -> 88;
            case "normal_knee_head" -> 45;
            case "claws","muscle_heart" -> 40;
            case "claws_head" -> 130;
            case "hammer" -> 86;
            case "hammer_smush" -> 77;
            case "muscle" -> 36;
            case "whip" -> 50;
            case "whip_lash" -> 60;
            case "blade" -> 25;
            default -> -1;
        };
        return frame<0?Float.NaN:frame/30F;
    }
    public static float cue(ConsumeVisual scene){
        var forms=PowerForm.values();
        return scene.form()>=0&&scene.form()<forms.length?cue(forms[scene.form()],scene.variant()):Float.NaN;
    }

    private record ExteriorWindow(float start, float settled, float peak) {}
    private static final PowerForm[] FORMS = PowerForm.values();
    private static final ExteriorWindow[][] WINDOWS = new ExteriorWindow[FORMS.length][ConsumeVariant.STEALTH_JUGULAR + 1];

    private static synchronized ExteriorWindow window(ConsumeVisual scene) {
        if (!scene.active() || (scene.stage() != ConsumeVisual.CONSUME && scene.stage() != ConsumeVisual.MOBILE_CONSUME)
                || scene.form() < 0 || scene.form() >= FORMS.length) return null;
        PowerForm form = FORMS[scene.form()];
        if (form == PowerForm.BACKSPINES) return null; // The grab key recalls quills in this form.
        int variant = scene.variant();
        ExteriorWindow cached = WINDOWS[form.ordinal()][variant];
        if (cached != null) return cached;

        var receiver = ConsumeReceiverScene.profile(form, false, variant);
        float flow = receiver == null ? ConsumeSequence.splitSeconds(form, false, variant)
                : ConsumeReceiverScene.flowStartSeconds(form, variant, receiver);
        if (!Float.isFinite(flow)) return null;
        float contact = receiver == null || receiver.hitSeconds().isEmpty() ? flow : receiver.hitSeconds().get(0);
        float settled = ConsumeSequence.completionTick(form, false, variant) / 20F;
        // Let the leading fibres catch the victim at contact. Delay the bulk of the wrap
        // until the return on multi-hit executions, then carry it across removal.
        float start = Math.max(0F, Math.min(settled - .45F, Math.max(contact - .12F, flow - .34F)));
        float nativeCue = cue(form, variant);
        // The recovered ForwardBackward giver track reverses 69 frames after its own callback.
        // Keep a visible handoff when the Minecraft receiver settles later than that.
        float peak = Math.max(settled + 1.35F, (Float.isFinite(nativeCue) ? nativeCue : start) + REVERSE_SECONDS);
        return WINDOWS[form.ordinal()][variant] = new ExteriorWindow(start, settled, peak);
    }

    public static float exteriorStart(ConsumeVisual scene) {
        var window = window(scene);
        return window == null ? Float.NaN : window.start();
    }

    public static float exterior(ConsumeVisual scene, float elapsedSeconds) {
        var window = window(scene);
        if (window == null || !Float.isFinite(elapsedSeconds)) return 0;
        float grow = Math.max(0, Math.min(1, (elapsedSeconds - window.start()) / .28F));
        float tail = Math.max(0, Math.min(1, 1 - (elapsedSeconds - window.settled()) / .75F));
        return grow * grow * (3 - 2 * grow) * tail * tail * (3 - 2 * tail);
    }

    public static float residue(ConsumeVisual scene, float elapsedSeconds) {
        var window = window(scene);
        if (window == null || !Float.isFinite(elapsedSeconds)) return 0;
        float gather = Math.max(0, Math.min(1, (elapsedSeconds - window.settled() + .48F) / .55F));
        float retreat = Math.max(0, Math.min(1, (window.peak() + REVERSE_SECONDS - elapsedSeconds) / REVERSE_SECONDS));
        return gather * gather * (3 - 2 * gather) * retreat * retreat * (3 - 2 * retreat);
    }

    public static float residueEnd(ConsumeVisual scene) {
        var window = window(scene);
        return window == null ? 0 : window.peak() + REVERSE_SECONDS;
    }

    public static float bodyEnd(ConsumeVisual scene) {
        var window = window(scene);
        float nativeCue = cue(scene);
        return Math.max(window == null ? 0 : window.peak() + REVERSE_SECONDS,
                Float.isFinite(nativeCue) ? nativeCue + END_SECONDS : 0);
    }

    public static int tick(float seconds){return (int)Math.ceil(seconds*20-.0001F);}
    public static float reach(float age){
        if(!(age>0)||age>=END_SECONDS)return 0;
        return .35F*Math.max(0,Math.min(age,2*REVERSE_SECONDS-age));
    }
}
