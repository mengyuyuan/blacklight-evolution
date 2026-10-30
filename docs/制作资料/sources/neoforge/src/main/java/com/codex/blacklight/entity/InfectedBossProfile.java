package com.codex.blacklight.entity;

import java.util.List;
import net.minecraft.world.phys.AABB;

/** Shared server/client dimensions in blocks. Authored boss meshes already contain this scale. */
public record InfectedBossProfile(String id, String family, float width, float height,
                                  float visualWidth, float visualDepth, float visualHeight,
                                  double health, double damage, double speed, List<Part> parts) {
    /** Local coordinates: x right, y up (bottom of box), z toward the back of the mesh. */
    public record Part(String name, double x, double y, double z, float width, float height) {}

    private static Part p(String name,double x,double y,double z,float w,float h) {
        return new Part(name,x,y,z,w,h);
    }

    /** Authoring bounds (X/right, Y/up, Z/back), including asymmetric claws and dragon tail. */
    public AABB localVisualBounds() {
        return switch(id) {
            case "minecraft:warden" -> new AABB(-17.61,0,-9.55,16.92,36.01,9.56);
            case "minecraft:iron_golem" -> new AABB(-17.10,0,-10.07,15.11,30.01,9.19);
            case "minecraft:wither" -> new AABB(-15.22,0,-5.38,15.20,40.01,8.05);
            case "minecraft:ender_dragon" -> new AABB(-56.02,0,-42.84,56.02,37.77,61.88);
            default -> new AABB(-visualWidth/2,0,-visualDepth,visualWidth/2,visualHeight,visualDepth);
        };
    }

    public static final InfectedBossProfile WARDEN = new InfectedBossProfile(
            "minecraft:warden","ground",6,30,34.5F,19.08F,36,700,10,.27,List.of(
            p("chest",0,15,0,11,12), p("head",0,26,-2,8,8),
            p("pelvis",0,10,0,10,8),
            p("left_upper_arm",-10,17,0,8,10),p("right_upper_arm",10,17,0,8,10),
            p("left_forearm",-13,8,-2,8,12),p("right_forearm",13,8,-2,9,12),
            p("left_hand",-14,3,-4,9,7),p("right_hand",14,3,-4,10,7),
            p("left_leg",-5,0,0,6,13),p("right_leg",5,0,0,6,13)));

    public static final InfectedBossProfile IRON_GOLEM = new InfectedBossProfile(
            "minecraft:iron_golem","ground",6,26,32.18F,19.23F,30,500,9,.25,List.of(
            p("chest",0,13,0,11,11),p("head",0,23,-1,6,7),p("pelvis",0,8,0,9,8),
            p("left_upper_arm",-9,14,0,8,10),p("right_upper_arm",9,14,0,8,10),
            p("left_forearm",-11,7,-1,9,10),p("right_forearm",11,7,-1,7,10),
            p("left_hammer",-12,2,-3,11,7),p("right_claw",12,1,-3,8,9),
            p("left_leg",-4.5,0,0,6,12),p("right_leg",4.5,0,0,6,12)));

    public static final InfectedBossProfile WITHER = new InfectedBossProfile(
            "minecraft:wither","air",6,32,30.38F,13.4F,40,800,8,.30,List.of(
            p("chest",0,17,0,9,12),p("spine",0,7,2,6,13),
            p("head",0,28,-1,8,9),p("left_head",-11.3,26,-1,7,7),p("right_head",11.3,26,-1,7,7),
            p("left_ribs",-7,18,0,6,12),p("right_ribs",7,18,0,6,12),
            p("left_tendril",-3,0,2,3,10),p("right_tendril",3,0,2,3,10)));

    public static final InfectedBossProfile ENDER_DRAGON = new InfectedBossProfile(
            "minecraft:ender_dragon","air",12,22,112,104.68F,37.75F,1000,10,.32,List.of(
            p("chest",0,11,-2,14,14),p("abdomen",0,11,10,12,12),
            p("neck_base",0,18,-10,9,8),p("neck_mid",0,22,-18,8,8),p("neck_tip",0,23,-25,7,8),
            p("head",0,24,-32,11,9),
            p("front_left_leg",-7,0,-6,7,16),p("front_right_leg",7,0,-6,7,16),
            p("back_left_leg",-7,0,13,7,16),p("back_right_leg",7,0,13,7,16),
            p("left_wing_root",-10,20,-3,8,6),p("right_wing_root",10,20,-3,8,6),
            p("left_wing_elbow",-25,30,-3,7,6),p("right_wing_elbow",25,30,-3,7,6),
            p("tail_base",0,10,21,9,8),p("tail_1",0,9,30,7,7),
            p("tail_2",1,8,40,6,6),p("tail_3",3,10,49,5,6),p("tail_tip",5,14,58,4,5)));

    public static InfectedBossProfile forSpecies(String id) {
        return switch (id) {
            case "minecraft:warden" -> WARDEN;
            case "minecraft:iron_golem" -> IRON_GOLEM;
            case "minecraft:wither" -> WITHER;
            case "minecraft:ender_dragon" -> ENDER_DRAGON;
            default -> null;
        };
    }
}
