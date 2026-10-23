package com.codex.blacklight.disguise;

/** Explicit vanilla adapters; an unknown mod id never inherits a similarly named mob's powers. */
public enum SpeciesTrait {
    NONE(0), CLIMB(10), FLAP(18), LEAP(30), SWIM(24), BLINK(100), ARROW(24), FIREBALL(40), EXPLODE(160);
    public final int cooldown;
    SpeciesTrait(int cooldown) { this.cooldown=cooldown; }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public static SpeciesTrait of(String type) {
        if(!type.startsWith("minecraft:"))return NONE;
        return switch(type.substring(10)) {
            case "spider","cave_spider" -> CLIMB;
            case "bat","bee","parrot","chicken" -> FLAP;
            case "rabbit","frog","slime","magma_cube" -> LEAP;
            case "cod","salmon","pufferfish","tropical_fish","squid","glow_squid","dolphin","axolotl","turtle","guardian" -> SWIM;
            case "enderman" -> BLINK;
            case "skeleton","stray","wither_skeleton","bogged" -> ARROW;
            case "blaze" -> FIREBALL;
            case "creeper" -> EXPLODE;
            default -> NONE;
        };
    }
    public static boolean fireproof(String type) {
        return type.equals("minecraft:blaze")||type.equals("minecraft:magma_cube")||type.equals("minecraft:strider");
    }
}
