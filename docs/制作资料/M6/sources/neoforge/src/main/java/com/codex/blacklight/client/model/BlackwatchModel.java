package com.codex.blacklight.client.model;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.firearms.FirearmItem;
import com.codex.blacklight.firearms.FirearmViewState;
import java.util.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;

/** Layered native geometry, retargeted from 1145homo's authorised two-hand reference.
 * Original gear declarations remain traceable to art/military2/build_soldiers.py;
 * the production profile supplies cloth UVs, real joint transforms and light weapon meshes. */
public final class BlackwatchModel extends HumanoidModel<MilitarySoldier> {
    private final boolean military;
    private final ModelPart[] equipment;
    private final ModelPart[] ranks = new ModelPart[8];
    private final ModelPart modelRoot,carry,rightForearm,leftForearm;
    private final Map<String,ModelPart> weapons = new LinkedHashMap<>();
    private final ModelPart[] forearmEquipment = new ModelPart[2];
    private final ModelPart[][] forearmCommands = new ModelPart[3][2];
    private static final BlackwatchReferenceProfile REFERENCE=BlackwatchReferenceProfile.load();
    private static final class ActorState {
        final FirearmViewState view=new FirearmViewState();
        double at=Double.NaN,movement,crouch,aimYaw,aimPitch;
    }
    // Shared renderer models never share animation clocks or a strong reference to an entity.
    private final Map<MilitarySoldier,ActorState> actors=new WeakHashMap<>();
    private final ModelPart[][] commandParts = new ModelPart[3][6];
    private final ModelPart[] hoodShell;
    private final ModelPart[] patrolPacks = new ModelPart[5];
    public BlackwatchModel(ModelPart root) { this(root, false); }
    public BlackwatchModel(ModelPart root, boolean military) {
        super(root);
        this.military=military;
        modelRoot=root;carry=body.getChild("carry");
        rightForearm=rightArm.getChild("forearm");leftForearm=leftArm.getChild("forearm");
        forearmEquipment[0]=rightForearm.getChild("equipment");forearmEquipment[1]=leftForearm.getChild("equipment");
        hat.visible = false;
        equipment = new ModelPart[]{head.getChild("equipment"), body.getChild("equipment"),
            rightArm.getChild("equipment"), leftArm.getChild("equipment"),
            rightLeg.getChild("equipment"), leftLeg.getChild("equipment")};
        for (int i=0; i<8; i++) ranks[i] = body.getChild("rank_"+i);
        for(String kind:REFERENCE.weapons().keySet())weapons.put(kind,carry.getChild("weapon_mount").getChild(kind));
        String[] parents={"head","body","right_arm","left_arm","right_leg","left_leg"};
        String[] roles={"team_leader","officer","commander"};
        for(int i=0;i<roles.length;i++) for(int j=0;j<parents.length;j++)
            commandParts[i][j]=root.getChild(parents[j]).getChild("role_"+roles[i]);
        for(int i=0;i<roles.length;i++)for(int j=0;j<2;j++)
            forearmCommands[i][j]=(j==0?rightForearm:leftForearm).getChild("role_"+roles[i]);
        String[] shellNames=military?new String[0]:new String[]{"gear_hood_crown","gear_hood_rim",
            "gear_hood_rear","gear_hood_right","gear_hood_left"};
        hoodShell=new ModelPart[shellNames.length];
        for(int i=0;i<shellNames.length;i++) hoodShell[i]=equipment[0].getChild(shellNames[i]);
        String[] packNames={"gear_patrol_pack","gear_radio","gear_aerial","gear_supply_pack","gear_supply_lid"};
        for(int i=0;i<packNames.length;i++)
            patrolPacks[i]=equipment[1].hasChild(packNames[i])?equipment[1].getChild(packNames[i]):null;
    }
    public static LayerDefinition layer(boolean heavy) { return layer(heavy,false); }
    public static LayerDefinition layer(boolean heavy,boolean military) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head",CubeListBuilder.create(),PartPose.ZERO);
        root.addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
        root.addOrReplaceChild("body",CubeListBuilder.create(),PartPose.ZERO);
        root.addOrReplaceChild("right_arm",CubeListBuilder.create(),PartPose.offset(-6,0,0));
        root.addOrReplaceChild("left_arm",CubeListBuilder.create(),PartPose.offset(6,0,0));
        root.addOrReplaceChild("right_leg",CubeListBuilder.create(),PartPose.offset(-1.9F,12,0));
        root.addOrReplaceChild("left_leg",CubeListBuilder.create(),PartPose.offset(1.9F,12,0));
        for(String parent:new String[]{"head","body","right_arm","left_arm","right_leg","left_leg"})
            root.getChild(parent).addOrReplaceChild("equipment",CubeListBuilder.create(),PartPose.ZERO);
        for(String parent:new String[]{"head","body","right_arm","left_arm","right_leg","left_leg"})
            for(String role:new String[]{"team_leader","officer","commander"})
                root.getChild(parent).addOrReplaceChild("role_"+role,CubeListBuilder.create(),PartPose.ZERO);
        for(int i=0;i<8;i++) root.getChild("body").addOrReplaceChild("rank_"+i,CubeListBuilder.create(),PartPose.ZERO);
        for(String arm:new String[]{"right_arm","left_arm"}) {
            var lower=root.getChild(arm).addOrReplaceChild("forearm",CubeListBuilder.create(),PartPose.offset(0,6,0));
            lower.addOrReplaceChild("equipment",CubeListBuilder.create(),PartPose.ZERO);
            for(String role:new String[]{"team_leader","officer","commander"})lower.addOrReplaceChild("role_"+role,CubeListBuilder.create(),PartPose.ZERO);
        }
        var origin=REFERENCE.vector("carry_origin");
        var carry=root.getChild("body").addOrReplaceChild("carry",CubeListBuilder.create(),PartPose.offset((float)origin[0],(float)origin[1],(float)origin[2]));
        for(String grip:new String[]{"main_grip","support_grip"}) {
            var p=REFERENCE.vector(grip);carry.addOrReplaceChild(grip,CubeListBuilder.create(),PartPose.offset((float)(p[0]-origin[0]),(float)(p[1]-origin[1]),(float)(p[2]-origin[2])));
        }
        var mount=REFERENCE.vector("weapon_mount_position");var rotation=REFERENCE.vector("weapon_mount_rotation");
        var weaponMount=carry.addOrReplaceChild("weapon_mount",CubeListBuilder.create(),PartPose.offsetAndRotation((float)mount[0],(float)mount[1],(float)mount[2],(float)rotation[0],(float)rotation[1],(float)rotation[2]));
        for(var entry:REFERENCE.weapons().entrySet()){
            var group=weaponMount.addOrReplaceChild(entry.getKey(),CubeListBuilder.create(),PartPose.ZERO);
            for(var p:entry.getValue()){
                var b=p.box();group.addOrReplaceChild(p.name(),CubeListBuilder.create().texOffs(p.u(),p.v()).addBox(b[0],b[1],b[2],b[3],b[4],b[5]),PartPose.ZERO);
            }
        }
        piece(root,military,heavy,"all","head","uniform_head",2,2,-4F,-8F,-4F,8F,8F,8F);
        piece(root,military,heavy,"all","body","uniform_torso",36,2,-4F,0F,-2F,8F,12F,4F);
        piece(root,military,heavy,"all","right_arm","uniform_sleeve",62,2,-3F,-2F,-2F,4F,12F,4F);
        piece(root,military,heavy,"all","right_leg","uniform_trouser",80,2,-2F,0F,-2F,4F,12F,4F);
        piece(root,military,heavy,"all","right_leg","uniform_boot",98,2,-2.06F,8.2F,-2.4F,4.12F,3.8F,4.55F);
        piece(root,military,heavy,"all","right_leg","uniform_sole",118,2,-2.1F,11.45F,-2.55F,4.2F,0.6F,4.75F);
        piece(root,military,heavy,"all","left_arm","uniform_sleeve",138,2,-1F,-2F,-2F,4F,12F,4F);
        piece(root,military,heavy,"all","left_leg","uniform_trouser",156,2,-2F,0F,-2F,4F,12F,4F);
        piece(root,military,heavy,"all","left_leg","uniform_boot",174,2,-2.06F,8.2F,-2.4F,4.12F,3.8F,4.55F);
        piece(root,military,heavy,"all","left_leg","uniform_sole",194,2,-2.1F,11.45F,-2.55F,4.2F,0.6F,4.75F);
        piece(root,military,heavy,"military","head","gear_helmet_crown",214,2,-4.075F,-8.65F,-4.075F,8.15F,1.55F,8.15F);
        piece(root,military,heavy,"military","head","gear_helmet_rim",2,20,-4.25F,-7.1F,-4.2F,8.5F,1.4F,8.4F);
        piece(root,military,heavy,"military","head","gear_helmet_rear",38,20,-4.2F,-5.7F,3.7F,8.4F,2.4F,0.65F);
        piece(root,military,heavy,"military","head","gear_helmet_right",59,20,-4.3F,-5.7F,-3F,0.6F,1.9F,6.7F);
        piece(root,military,heavy,"military","head","gear_helmet_left",76,20,3.7F,-5.7F,-3F,0.6F,1.9F,6.7F);
        piece(root,military,heavy,"military","head","gear_helmet_brim",93,20,-4.3F,-5.95F,-4.65F,8.6F,0.45F,1.2F);
        piece(root,military,heavy,"military","head","gear_headset",115,20,-4.65F,-4.8F,-0.9F,0.7F,2.25F,2.1F);
        piece(root,military,heavy,"military","head","gear_microphone",123,20,-4.45F,-2.8F,-3.6F,0.35F,0.4F,3.5F);
        piece(root,military,heavy,"military","head","gear_stowed_goggles",133,20,-2.9F,-7.35F,-4.52F,5.8F,1.3F,0.55F);
        piece(root,military,heavy,"bw","head","gear_hood_crown",148,20,-4.075F,-8.6F,-4.075F,8.15F,1.55F,8.15F);
        piece(root,military,heavy,"bw","head","gear_hood_rim",183,20,-4.3F,-7.05F,-4.25F,8.6F,1.25F,8.6F);
        piece(root,military,heavy,"bw","head","gear_hood_rear",220,20,-4.25F,-5.8F,3.8F,8.5F,5.9F,0.65F);
        piece(root,military,heavy,"bw","head","gear_hood_right",2,32,-4.35F,-5.8F,-4.2F,0.65F,5.7F,8.6F);
        piece(root,military,heavy,"bw","head","gear_hood_left",23,32,3.7F,-5.8F,-4.2F,0.65F,5.7F,8.6F);
        piece(root,military,heavy,"bw","head","gear_mask_seal",44,32,-3.75F,-5.9F,-4.48F,7.5F,6.1F,0.65F);
        piece(root,military,heavy,"bw","head","gear_lens_right",63,32,-3.3F,-5.25F,-4.88F,2.95F,1.45F,0.5F);
        piece(root,military,heavy,"bw","head","gear_lens_left",72,32,0.35F,-5.25F,-4.88F,2.95F,1.45F,0.5F);
        piece(root,military,heavy,"bw","head","gear_respirator",81,32,-2.6F,-3.35F,-5.2F,5.2F,2.85F,1.15F);
        piece(root,military,heavy,"bw","head","gear_filter_right",96,32,-4.05F,-2.85F,-5.4F,1.7F,2.1F,1.65F);
        piece(root,military,heavy,"bw","head","gear_filter_left",105,32,2.35F,-2.85F,-5.4F,1.7F,2.1F,1.65F);
        piece(root,military,heavy,"all","body","gear_front_carrier",114,32,-3.8F,0.8F,-2.7F,7.6F,7.55F,0.8F);
        piece(root,military,heavy,"all","body","gear_rear_carrier",133,32,-3.8F,0.8F,1.95F,7.6F,7.55F,0.8F);
        piece(root,military,heavy,"all","body","gear_strap_right",152,32,-3.65F,-0.15F,-2.65F,1.45F,2F,5.3F);
        piece(root,military,heavy,"all","body","gear_strap_left",168,32,2.2F,-0.15F,-2.65F,1.45F,2F,5.3F);
        piece(root,military,heavy,"all","body","gear_magpouch_0",184,32,-3.15F,4.65F,-4.05F,1.8F,3.2F,1.45F);
        piece(root,military,heavy,"all","body","gear_magpouch_1",193,32,-0.9F,4.65F,-4.05F,1.8F,3.2F,1.45F);
        piece(root,military,heavy,"all","body","gear_magpouch_2",202,32,1.35F,4.65F,-4.05F,1.8F,3.2F,1.45F);
        piece(root,military,heavy,"all","body","gear_belt",211,32,-4.12F,9.25F,-2.2F,8.24F,1.1F,4.4F);
        piece(root,military,heavy,"all","body","gear_utility_pouch",239,32,3.6F,8.7F,-0.1F,1.45F,2.8F,2.1F);
        piece(root,military,heavy,"all","body","gear_rank_patch",249,32,-3.25F,1.8F,-3.62F,2.15F,2.1F,0.3F);
        piece(root,military,heavy,"all","body","gear_faction_tab",2,49,0.8F,1.85F,-3.62F,2.35F,1F,0.3F);
        piece(root,military,heavy,"regular","body","gear_patrol_pack",10,49,-2.7F,2F,2.75F,5.4F,6.1F,2.3F);
        piece(root,military,heavy,"regular","body","gear_radio",28,49,-4.15F,1.15F,2.75F,1.65F,3.75F,1.7F);
        piece(root,military,heavy,"heavy","body","gear_supply_pack",37,49,-3.25F,1F,2.75F,6.5F,8.6F,3.5F);
        piece(root,military,heavy,"heavy","body","gear_supply_lid",59,49,-3.4F,0.65F,2.65F,6.8F,1.65F,3.7F);
        piece(root,military,heavy,"all","body","gear_aerial",82,49,-3.5F,-2.3F,3.75F,0.3F,4F,0.3F);
        piece(root,military,heavy,"heavy","body","gear_sternum_plate",86,49,-3.55F,0.7F,-3.25F,7.1F,3.5F,0.65F);
        piece(root,military,heavy,"heavy","body","gear_abdomen_plate",104,49,-2.7F,8F,-2.75F,5.4F,2.25F,0.65F);
        piece(root,military,heavy,"bwHeavy","body","gear_neck_guard",119,49,-3.5F,-0.65F,-2.45F,7F,1.45F,4.9F);
        piece(root,military,heavy,"all","right_arm","gear_glove",145,49,-3.06F,6.85F,-2.06F,4.12F,3.3F,4.12F);
        piece(root,military,heavy,"heavy","right_arm","gear_shoulder",164,49,-3.15F,-2.25F,-2.35F,4.3F,2.9F,4.7F);
        piece(root,military,heavy,"heavy","right_arm","gear_forearm",184,49,-3.08F,4.4F,-2.35F,4.16F,2.45F,0.55F);
        piece(root,military,heavy,"all","right_leg","gear_kneepad",196,49,-1.55F,4.75F,-2.5F,3.1F,2.65F,0.7F);
        piece(root,military,heavy,"heavy","right_leg","gear_shin",206,49,-1.55F,7.45F,-2.55F,3.1F,1.8F,0.65F);
        piece(root,military,heavy,"bwHeavy","right_leg","gear_thigh",216,49,-1.6F,1.1F,-2.35F,3.2F,2.8F,0.55F);
        piece(root,military,heavy,"all","left_arm","gear_glove",226,49,-1.06F,6.85F,-2.06F,4.12F,3.3F,4.12F);
        piece(root,military,heavy,"heavy","left_arm","gear_shoulder",2,64,-1.15F,-2.25F,-2.35F,4.3F,2.9F,4.7F);
        piece(root,military,heavy,"heavy","left_arm","gear_forearm",22,64,-1.08F,4.4F,-2.35F,4.16F,2.45F,0.55F);
        piece(root,military,heavy,"all","left_leg","gear_kneepad",34,64,-1.55F,4.75F,-2.5F,3.1F,2.65F,0.7F);
        piece(root,military,heavy,"heavy","left_leg","gear_shin",44,64,-1.55F,7.45F,-2.55F,3.1F,1.8F,0.65F);
        piece(root,military,heavy,"bwHeavy","left_leg","gear_thigh",54,64,-1.6F,1.1F,-2.35F,3.2F,2.8F,0.55F);
        piece(root,military,heavy,"weapon_rifle","right_arm","stock",64,64,-1.8F,3.2F,-1.8F,1.6F,5F,2.45F);
        piece(root,military,heavy,"weapon_rifle","right_arm","receiver",75,64,-1.95F,8F,-2.25F,1.9F,5.4F,2.45F);
        piece(root,military,heavy,"weapon_rifle","right_arm","grip",86,64,-1.65F,9.1F,0.15F,1.3F,1.4F,2.6F);
        piece(root,military,heavy,"weapon_rifle","right_arm","handguard",96,64,-1.8F,13.4F,-2.05F,1.6F,4.6F,1.95F);
        piece(root,military,heavy,"weapon_rifle","right_arm","barrel",106,64,-1.43F,18F,-1.45F,0.85F,3.8F,0.85F);
        piece(root,military,heavy,"weapon_rifle","right_arm","muzzle",112,64,-1.57F,21.5F,-1.59F,1.14F,1.15F,1.14F);
        piece(root,military,heavy,"weapon_rifle","right_arm","sight",119,64,-1.4F,10F,-3.15F,0.8F,2.4F,0.9F);
        piece(root,military,heavy,"weapon_rifle","right_arm","magazine",125,64,-1.65F,11.7F,0.1F,1.3F,2.5F,3.25F);
        piece(root,military,heavy,"weapon_machine","right_arm","stock",137,64,-1.8F,3.2F,-1.8F,1.6F,5F,2.45F);
        piece(root,military,heavy,"weapon_machine","right_arm","receiver",148,64,-1.95F,8F,-2.25F,1.9F,5.4F,2.45F);
        piece(root,military,heavy,"weapon_machine","right_arm","grip",159,64,-1.65F,9.1F,0.15F,1.3F,1.4F,2.6F);
        piece(root,military,heavy,"weapon_machine","right_arm","handguard",169,64,-1.8F,13.4F,-2.05F,1.6F,5.7F,1.95F);
        piece(root,military,heavy,"weapon_machine","right_arm","barrel",179,64,-1.43F,19.1F,-1.45F,0.85F,6F,0.85F);
        piece(root,military,heavy,"weapon_machine","right_arm","muzzle",185,64,-1.57F,24.7F,-1.59F,1.14F,1.15F,1.14F);
        piece(root,military,heavy,"weapon_machine","right_arm","sight",192,64,-1.4F,10F,-3.15F,0.8F,2.4F,0.9F);
        piece(root,military,heavy,"weapon_machine","right_arm","ammo_box",198,64,-2.7F,11F,0.1F,3.4F,3.6F,3.1F);
        piece(root,military,heavy,"weapon_machine","right_arm","bipod",213,64,-1.65F,19.5F,-0.5F,1.3F,4.3F,0.5F);
        piece(root,military,heavy,"weapon_grenade","right_arm","stock",219,64,-1.8F,3.2F,-1.8F,1.6F,5F,2.45F);
        piece(root,military,heavy,"weapon_grenade","right_arm","receiver",230,64,-1.95F,8F,-2.25F,1.9F,5.4F,2.45F);
        piece(root,military,heavy,"weapon_grenade","right_arm","grip",241,64,-1.65F,9.1F,0.15F,1.3F,1.4F,2.6F);
        piece(root,military,heavy,"weapon_grenade","right_arm","handguard",2,74,-1.8F,13.4F,-2.05F,1.6F,4.6F,1.95F);
        piece(root,military,heavy,"weapon_grenade","right_arm","barrel",12,74,-1.43F,18F,-1.45F,0.85F,3.8F,0.85F);
        piece(root,military,heavy,"weapon_grenade","right_arm","muzzle",18,74,-1.57F,21.5F,-1.59F,1.14F,1.15F,1.14F);
        piece(root,military,heavy,"weapon_grenade","right_arm","sight",25,74,-1.4F,10F,-3.15F,0.8F,2.4F,0.9F);
        piece(root,military,heavy,"weapon_grenade","right_arm","drum",31,74,-2.65F,10.65F,0.1F,3.3F,3.2F,3.1F);
        piece(root,military,heavy,"weapon_grenade","right_arm","wide_bore",46,74,-2F,16.1F,-2.25F,2F,4.3F,2.2F);
        piece(root,military,heavy,"weapon_launcher","right_arm","tube",57,74,-3.25F,-0.6F,-4F,3.5F,19.8F,3.5F);
        piece(root,military,heavy,"weapon_launcher","right_arm","rear_ring",73,74,-3.5F,-0.7F,-4.25F,4F,1.15F,4F);
        piece(root,military,heavy,"weapon_launcher","right_arm","front_ring",91,74,-3.5F,18.2F,-4.25F,4F,1.2F,4F);
        piece(root,military,heavy,"weapon_launcher","right_arm","grip",109,74,-2F,8.1F,-0.5F,1.4F,1.6F,3.3F);
        piece(root,military,heavy,"weapon_launcher","right_arm","trigger_mount",121,74,-2.25F,6.8F,-1F,1.9F,4F,0.8F);
        piece(root,military,heavy,"weapon_launcher","right_arm","optic",129,74,0.25F,9.7F,-3.7F,1.2F,3.7F,1.35F);
        piece(root,military,heavy,"rank_0","body","mark_0",137,74,-3.02F,2.15F,-3.82F,1.65F,0.22F,0.14F);
        piece(root,military,heavy,"rank_1","body","mark_0",143,74,-3.02F,2.15F,-3.82F,1.65F,0.22F,0.14F);
        piece(root,military,heavy,"rank_1","body","mark_1",149,74,-3.02F,2.62F,-3.82F,1.65F,0.22F,0.14F);
        piece(root,military,heavy,"rank_2","body","mark_0",155,74,-3.02F,2.15F,-3.82F,1.65F,0.22F,0.14F);
        piece(root,military,heavy,"rank_2","body","mark_1",161,74,-3.02F,2.62F,-3.82F,1.65F,0.22F,0.14F);
        piece(root,military,heavy,"rank_2","body","mark_2",167,74,-3.02F,3.09F,-3.82F,1.65F,0.22F,0.14F);
        piece(root,military,heavy,"rank_3","body","mark_0",173,74,-3.02F,2.15F,-3.82F,1.65F,0.22F,0.14F);
        piece(root,military,heavy,"rank_4","body","mark_0",179,74,-3.02F,2.15F,-3.82F,1.65F,0.22F,0.14F);
        piece(root,military,heavy,"rank_4","body","mark_1",185,74,-3.02F,2.62F,-3.82F,1.65F,0.22F,0.14F);
        piece(root,military,heavy,"rank_5","body","mark_0",191,74,-2.95F,2.25F,-3.82F,0.52F,0.52F,0.14F);
        piece(root,military,heavy,"rank_6","body","mark_0",195,74,-2.95F,2.25F,-3.82F,0.52F,0.52F,0.14F);
        piece(root,military,heavy,"rank_6","body","mark_1",199,74,-2.15F,2.25F,-3.82F,0.52F,0.52F,0.14F);
        piece(root,military,heavy,"rank_6","body","mark_2",203,74,-2.95F,3F,-3.82F,0.52F,0.52F,0.14F);
        piece(root,military,heavy,"rank_7","body","mark_0",207,74,-2.95F,2.25F,-3.82F,0.52F,0.52F,0.14F);
        piece(root,military,heavy,"rank_7","body","mark_1",211,74,-2.15F,2.25F,-3.82F,0.52F,0.52F,0.14F);
        piece(root,military,heavy,"rank_7","body","mark_2",215,74,-2.95F,3F,-3.82F,0.52F,0.52F,0.14F);
        piece(root,military,heavy,"rank_7","body","mark_3",219,74,-2.15F,3F,-3.82F,0.52F,0.52F,0.14F);
        piece(root,military,heavy,"militaryRole_team_leader","head","role_leader_crown_stripe",223,74,-0.7F,-8.77F,-4.12F,1.4F,0.18F,8.24F);
        piece(root,military,heavy,"militaryRole_team_leader","head","role_leader_front_stripe",2,100,-3.9F,-6.55F,-4.8F,7.8F,0.45F,0.2F);
        piece(root,military,heavy,"militaryRole_team_leader","head","role_leader_rear_stripe",20,100,-0.7F,-7.05F,4.4F,1.4F,2.3F,0.18F);
        piece(root,military,heavy,"militaryRole_team_leader","right_arm","role_leader_arm_bar_0",26,100,-3.12F,1.1F,-2.15F,4.24F,0.42F,0.26F);
        piece(root,military,heavy,"militaryRole_team_leader","right_arm","role_leader_arm_bar_1",37,100,-3.12F,1.85F,-2.15F,4.24F,0.42F,0.26F);
        piece(root,military,heavy,"militaryRole_team_leader","left_arm","role_leader_arm_bar_0",48,100,-1.12F,1.1F,-2.15F,4.24F,0.42F,0.26F);
        piece(root,military,heavy,"militaryRole_team_leader","left_arm","role_leader_arm_bar_1",59,100,-1.12F,1.85F,-2.15F,4.24F,0.42F,0.26F);
        piece(root,military,heavy,"militaryRole_team_leader","body","role_leader_radio_panel",70,100,2.45F,2.7F,-3.6F,1.6F,1.5F,0.75F);
        piece(root,military,heavy,"militaryRole_team_leader","body","role_leader_radio_battery",77,100,-2.4F,1.2F,5.1F,4.8F,3.6F,1.1F);
        piece(root,military,heavy,"militaryRole_team_leader","body","role_leader_long_aerial",91,100,1.6F,-4.1F,5.5F,0.28F,5.6F,0.28F);
        piece(root,military,heavy,"militaryRole_team_leader","head","role_leader_comms",95,100,4.32F,-4.9F,-0.75F,0.75F,2.4F,2F);
        piece(root,military,heavy,"militaryRole_officer","body","role_command_pack",103,100,-2.8F,1.6F,2.8F,5.6F,5.5F,1.8F);
        piece(root,military,heavy,"militaryRole_officer","body","role_command_pack_frame",120,100,-3F,1.35F,2.7F,6F,0.65F,2F);
        piece(root,military,heavy,"militaryRole_officer","body","role_command_aerial",138,100,-2.6F,-2.8F,3.5F,0.28F,4.3F,0.28F);
        piece(root,military,heavy,"militaryRole_officer","body","role_map_case",142,100,3.7F,6.25F,-0.8F,1.3F,5.1F,3.7F);
        piece(root,military,heavy,"militaryRole_officer","left_arm","role_field_terminal",154,100,-0.25F,3.8F,-2.65F,2.5F,2F,0.65F);
        piece(root,military,heavy,"militaryRole_officer","right_arm","role_shoulder_board",163,100,-3.15F,-2.65F,-1.75F,4.3F,0.65F,3.5F);
        piece(root,military,heavy,"militaryRole_officer","right_arm","role_shoulder_core",181,100,-2.75F,-2.81F,-1.25F,3.5F,0.2F,2.5F);
        piece(root,military,heavy,"militaryRole_officer","left_arm","role_shoulder_board",195,100,-1.15F,-2.65F,-1.75F,4.3F,0.65F,3.5F);
        piece(root,military,heavy,"militaryRole_officer","left_arm","role_shoulder_core",213,100,-0.75F,-2.81F,-1.25F,3.5F,0.2F,2.5F);
        piece(root,military,heavy,"militaryRole_officer","body","role_cross_strap_0",227,100,2.2F,3.9F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"militaryRole_officer","body","role_cross_strap_1",232,100,1F,4.6F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"militaryRole_officer","body","role_cross_strap_2",237,100,-0.2F,5.3F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"militaryRole_officer","body","role_cross_strap_3",242,100,-1.4F,6F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"militaryRole_officer","head","role_cap_crown",2,111,-4.22F,-8.8F,-4.15F,8.44F,2.5F,8.3F);
        piece(root,military,heavy,"militaryRole_officer","head","role_cap_top",38,111,-4.08F,-9.3F,-4.02F,8.16F,0.5F,8.04F);
        piece(root,military,heavy,"militaryRole_officer","head","role_cap_band",73,111,-4.3F,-6.3F,-4.25F,8.6F,0.75F,8.5F);
        piece(root,military,heavy,"militaryRole_officer","head","role_cap_visor",110,111,-4.35F,-5.75F,-6.05F,8.7F,0.42F,3.35F);
        piece(root,military,heavy,"militaryRole_officer","head","role_cap_badge",137,111,-0.7F,-8F,-4.4F,1.4F,1.35F,0.3F);
        piece(root,military,heavy,"militaryRole_officer","head","role_officer_headset",143,111,-4.7F,-4.8F,-0.9F,0.7F,2.2F,2F);
        piece(root,military,heavy,"militaryRole_officer","head","role_officer_boom",151,111,-4.5F,-2.8F,-3.5F,0.35F,0.35F,3.2F);
        piece(root,military,heavy,"militaryRole_commander","body","role_command_pack",161,111,-2.8F,1.6F,2.8F,5.6F,5.5F,1.8F);
        piece(root,military,heavy,"militaryRole_commander","body","role_command_pack_frame",178,111,-3F,1.35F,2.7F,6F,0.65F,2F);
        piece(root,military,heavy,"militaryRole_commander","body","role_command_aerial",196,111,-2.6F,-2.8F,3.5F,0.28F,4.3F,0.28F);
        piece(root,military,heavy,"militaryRole_commander","body","role_map_case",200,111,3.7F,6.25F,-0.8F,1.3F,5.1F,3.7F);
        piece(root,military,heavy,"militaryRole_commander","left_arm","role_field_terminal",212,111,-0.25F,3.8F,-2.65F,2.5F,2F,0.65F);
        piece(root,military,heavy,"militaryRole_commander","right_arm","role_shoulder_board",221,111,-3.15F,-2.65F,-1.75F,4.3F,0.65F,3.5F);
        piece(root,military,heavy,"militaryRole_commander","right_arm","role_shoulder_core",239,111,-2.75F,-2.81F,-1.25F,3.5F,0.2F,2.5F);
        piece(root,military,heavy,"militaryRole_commander","left_arm","role_shoulder_board",2,124,-1.15F,-2.65F,-1.75F,4.3F,0.65F,3.5F);
        piece(root,military,heavy,"militaryRole_commander","left_arm","role_shoulder_core",20,124,-0.75F,-2.81F,-1.25F,3.5F,0.2F,2.5F);
        piece(root,military,heavy,"militaryRole_commander","body","role_cross_strap_0",34,124,2.2F,3.9F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"militaryRole_commander","body","role_cross_strap_1",39,124,1F,4.6F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"militaryRole_commander","body","role_cross_strap_2",44,124,-0.2F,5.3F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"militaryRole_commander","body","role_cross_strap_3",49,124,-1.4F,6F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"militaryRole_commander","head","role_cap_crown",54,124,-4.22F,-8.8F,-4.15F,8.44F,2.5F,8.3F);
        piece(root,military,heavy,"militaryRole_commander","head","role_cap_top",90,124,-4.08F,-9.3F,-4.02F,8.16F,0.5F,8.04F);
        piece(root,military,heavy,"militaryRole_commander","head","role_cap_band",125,124,-4.3F,-6.3F,-4.25F,8.6F,0.75F,8.5F);
        piece(root,military,heavy,"militaryRole_commander","head","role_cap_visor",162,124,-4.35F,-5.75F,-6.05F,8.7F,0.42F,3.35F);
        piece(root,military,heavy,"militaryRole_commander","head","role_cap_badge",189,124,-0.7F,-8F,-4.4F,1.4F,1.35F,0.3F);
        piece(root,military,heavy,"militaryRole_commander","head","role_officer_headset",195,124,-4.7F,-4.8F,-0.9F,0.7F,2.2F,2F);
        piece(root,military,heavy,"militaryRole_commander","head","role_officer_boom",203,124,-4.5F,-2.8F,-3.5F,0.35F,0.35F,3.2F);
        piece(root,military,heavy,"militaryRole_commander","body","role_command_collar_right",213,124,-4.45F,-0.8F,-2.6F,0.85F,2.65F,5.15F);
        piece(root,military,heavy,"militaryRole_commander","body","role_command_collar_left",227,124,3.6F,-0.8F,-2.6F,0.85F,2.65F,5.15F);
        piece(root,military,heavy,"militaryRole_commander","body","role_collar_lapel_right",241,124,-4.6F,-0.3F,-3.15F,2.1F,2.15F,0.65F);
        piece(root,military,heavy,"militaryRole_commander","body","role_collar_lapel_left",2,137,2.5F,-0.3F,-3.15F,2.1F,2.15F,0.65F);
        piece(root,military,heavy,"militaryRole_commander","body","role_collar_rear",10,137,-4.45F,-0.8F,2.05F,8.9F,1.6F,0.6F);
        piece(root,military,heavy,"militaryRole_commander","body","role_command_mantle",31,137,-4.8F,0.05F,2.25F,9.6F,0.75F,1.05F);
        piece(root,military,heavy,"militaryRole_commander","right_arm","role_command_board_edge",55,137,-3.25F,-2.45F,-2.15F,4.5F,0.9F,0.35F);
        piece(root,military,heavy,"militaryRole_commander","left_arm","role_command_board_edge",67,137,-1.25F,-2.45F,-2.15F,4.5F,0.9F,0.35F);
        piece(root,military,heavy,"militaryRole_commander","body","role_coat_front_right",79,137,-4.1F,10.25F,-2.65F,3.65F,3.7F,0.6F);
        piece(root,military,heavy,"militaryRole_commander","body","role_coat_front_left",90,137,0.45F,10.25F,-2.65F,3.65F,3.7F,0.6F);
        piece(root,military,heavy,"militaryRole_commander","body","role_coat_rear",101,137,-4.1F,9.8F,2.25F,8.2F,4.15F,0.65F);
        piece(root,military,heavy,"militaryRole_commander","body","role_belt_buckle",121,137,-1F,9.25F,-2.75F,2F,1.05F,0.65F);
        piece(root,military,heavy,"militaryRole_commander","body","role_command_back_tab",129,137,-1.65F,3.25F,4.62F,3.3F,2F,0.25F);
        piece(root,military,heavy,"bwRole_team_leader","head","role_leader_crown_stripe",139,137,-0.7F,-8.77F,-4.12F,1.4F,0.18F,8.24F);
        piece(root,military,heavy,"bwRole_team_leader","head","role_leader_front_stripe",161,137,-3.9F,-6.55F,-4.8F,7.8F,0.45F,0.2F);
        piece(root,military,heavy,"bwRole_team_leader","head","role_leader_rear_stripe",179,137,-0.7F,-7.05F,4.4F,1.4F,2.3F,0.18F);
        piece(root,military,heavy,"bwRole_team_leader","right_arm","role_leader_arm_bar_0",185,137,-3.12F,1.1F,-2.15F,4.24F,0.42F,0.26F);
        piece(root,military,heavy,"bwRole_team_leader","right_arm","role_leader_arm_bar_1",196,137,-3.12F,1.85F,-2.15F,4.24F,0.42F,0.26F);
        piece(root,military,heavy,"bwRole_team_leader","left_arm","role_leader_arm_bar_0",207,137,-1.12F,1.1F,-2.15F,4.24F,0.42F,0.26F);
        piece(root,military,heavy,"bwRole_team_leader","left_arm","role_leader_arm_bar_1",218,137,-1.12F,1.85F,-2.15F,4.24F,0.42F,0.26F);
        piece(root,military,heavy,"bwRole_team_leader","body","role_leader_radio_panel",229,137,2.45F,2.7F,-3.6F,1.6F,1.5F,0.75F);
        piece(root,military,heavy,"bwRole_team_leader","body","role_leader_radio_battery",236,137,-2.4F,1.2F,5.1F,4.8F,3.6F,1.1F);
        piece(root,military,heavy,"bwRole_team_leader","body","role_leader_long_aerial",250,137,1.6F,-4.1F,5.5F,0.28F,5.6F,0.28F);
        piece(root,military,heavy,"bwRole_team_leader","head","role_leader_comms",2,148,4.32F,-4.9F,-0.75F,0.75F,2.4F,2F);
        piece(root,military,heavy,"bwRole_officer","body","role_command_pack",10,148,-2.8F,1.6F,2.8F,5.6F,5.5F,1.8F);
        piece(root,military,heavy,"bwRole_officer","body","role_command_pack_frame",27,148,-3F,1.35F,2.7F,6F,0.65F,2F);
        piece(root,military,heavy,"bwRole_officer","body","role_command_aerial",45,148,-2.6F,-2.8F,3.5F,0.28F,4.3F,0.28F);
        piece(root,military,heavy,"bwRole_officer","body","role_map_case",49,148,3.7F,6.25F,-0.8F,1.3F,5.1F,3.7F);
        piece(root,military,heavy,"bwRole_officer","left_arm","role_field_terminal",61,148,-0.25F,3.8F,-2.65F,2.5F,2F,0.65F);
        piece(root,military,heavy,"bwRole_officer","right_arm","role_shoulder_board",70,148,-3.15F,-2.65F,-1.75F,4.3F,0.65F,3.5F);
        piece(root,military,heavy,"bwRole_officer","right_arm","role_shoulder_core",88,148,-2.75F,-2.81F,-1.25F,3.5F,0.2F,2.5F);
        piece(root,military,heavy,"bwRole_officer","left_arm","role_shoulder_board",102,148,-1.15F,-2.65F,-1.75F,4.3F,0.65F,3.5F);
        piece(root,military,heavy,"bwRole_officer","left_arm","role_shoulder_core",120,148,-0.75F,-2.81F,-1.25F,3.5F,0.2F,2.5F);
        piece(root,military,heavy,"bwRole_officer","body","role_cross_strap_0",134,148,2.2F,3.9F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"bwRole_officer","body","role_cross_strap_1",139,148,1F,4.6F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"bwRole_officer","body","role_cross_strap_2",144,148,-0.2F,5.3F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"bwRole_officer","body","role_cross_strap_3",149,148,-1.4F,6F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"bwRole_officer","head","role_hood_crown",154,148,-4.12F,-9.05F,-4.12F,8.24F,1.8F,8.24F);
        piece(root,military,heavy,"bwRole_officer","head","role_hood_band",189,148,-4.45F,-7.25F,-4.4F,8.9F,1.5F,8.8F);
        piece(root,military,heavy,"bwRole_officer","head","role_hood_rear",227,148,-4.4F,-5.75F,3.85F,8.8F,5.9F,0.65F);
        piece(root,military,heavy,"bwRole_officer","head","role_hood_right",2,161,-4.5F,-5.75F,-4.3F,0.75F,5.9F,8.7F);
        piece(root,military,heavy,"bwRole_officer","head","role_hood_left",23,161,3.75F,-5.75F,-4.3F,0.75F,5.9F,8.7F);
        piece(root,military,heavy,"bwRole_officer","head","role_visor_bridge",44,161,-3.7F,-5.85F,-4.9F,7.4F,0.42F,0.55F);
        piece(root,military,heavy,"bwRole_officer","head","role_hood_badge",62,161,-1F,-7.1F,-4.66F,2F,0.9F,0.28F);
        piece(root,military,heavy,"bwRole_officer","head","role_hood_rear_band",69,161,-4.5F,-6.9F,4.42F,9F,0.55F,0.2F);
        piece(root,military,heavy,"bwRole_commander","body","role_command_pack",90,161,-2.8F,1.6F,2.8F,5.6F,5.5F,1.8F);
        piece(root,military,heavy,"bwRole_commander","body","role_command_pack_frame",107,161,-3F,1.35F,2.7F,6F,0.65F,2F);
        piece(root,military,heavy,"bwRole_commander","body","role_command_aerial",125,161,-2.6F,-2.8F,3.5F,0.28F,4.3F,0.28F);
        piece(root,military,heavy,"bwRole_commander","body","role_map_case",129,161,3.7F,6.25F,-0.8F,1.3F,5.1F,3.7F);
        piece(root,military,heavy,"bwRole_commander","left_arm","role_field_terminal",141,161,-0.25F,3.8F,-2.65F,2.5F,2F,0.65F);
        piece(root,military,heavy,"bwRole_commander","right_arm","role_shoulder_board",150,161,-3.15F,-2.65F,-1.75F,4.3F,0.65F,3.5F);
        piece(root,military,heavy,"bwRole_commander","right_arm","role_shoulder_core",168,161,-2.75F,-2.81F,-1.25F,3.5F,0.2F,2.5F);
        piece(root,military,heavy,"bwRole_commander","left_arm","role_shoulder_board",182,161,-1.15F,-2.65F,-1.75F,4.3F,0.65F,3.5F);
        piece(root,military,heavy,"bwRole_commander","left_arm","role_shoulder_core",200,161,-0.75F,-2.81F,-1.25F,3.5F,0.2F,2.5F);
        piece(root,military,heavy,"bwRole_commander","body","role_cross_strap_0",214,161,2.2F,3.9F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"bwRole_commander","body","role_cross_strap_1",219,161,1F,4.6F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"bwRole_commander","body","role_cross_strap_2",224,161,-0.2F,5.3F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"bwRole_commander","body","role_cross_strap_3",229,161,-1.4F,6F,-4.3F,0.85F,0.78F,0.35F);
        piece(root,military,heavy,"bwRole_commander","head","role_hood_crown",2,178,-4.12F,-9.05F,-4.12F,8.24F,1.8F,8.24F);
        piece(root,military,heavy,"bwRole_commander","head","role_hood_band",37,178,-4.45F,-7.25F,-4.4F,8.9F,1.5F,8.8F);
        piece(root,military,heavy,"bwRole_commander","head","role_hood_rear",75,178,-4.4F,-5.75F,3.85F,8.8F,5.9F,0.65F);
        piece(root,military,heavy,"bwRole_commander","head","role_hood_right",96,178,-4.5F,-5.75F,-4.3F,0.75F,5.9F,8.7F);
        piece(root,military,heavy,"bwRole_commander","head","role_hood_left",117,178,3.75F,-5.75F,-4.3F,0.75F,5.9F,8.7F);
        piece(root,military,heavy,"bwRole_commander","head","role_visor_bridge",138,178,-3.7F,-5.85F,-4.9F,7.4F,0.42F,0.55F);
        piece(root,military,heavy,"bwRole_commander","head","role_hood_badge",156,178,-1F,-7.1F,-4.66F,2F,0.9F,0.28F);
        piece(root,military,heavy,"bwRole_commander","head","role_hood_rear_band",163,178,-4.5F,-6.9F,4.42F,9F,0.55F,0.2F);
        piece(root,military,heavy,"bwRole_commander","body","role_command_collar_right",184,178,-4.45F,-0.8F,-2.6F,0.85F,2.65F,5.15F);
        piece(root,military,heavy,"bwRole_commander","body","role_command_collar_left",198,178,3.6F,-0.8F,-2.6F,0.85F,2.65F,5.15F);
        piece(root,military,heavy,"bwRole_commander","body","role_collar_lapel_right",212,178,-4.6F,-0.3F,-3.15F,2.1F,2.15F,0.65F);
        piece(root,military,heavy,"bwRole_commander","body","role_collar_lapel_left",220,178,2.5F,-0.3F,-3.15F,2.1F,2.15F,0.65F);
        piece(root,military,heavy,"bwRole_commander","body","role_collar_rear",228,178,-4.45F,-0.8F,2.05F,8.9F,1.6F,0.6F);
        piece(root,military,heavy,"bwRole_commander","body","role_command_mantle",2,195,-4.8F,0.05F,2.25F,9.6F,0.75F,1.05F);
        piece(root,military,heavy,"bwRole_commander","right_arm","role_command_board_edge",26,195,-3.25F,-2.45F,-2.15F,4.5F,0.9F,0.35F);
        piece(root,military,heavy,"bwRole_commander","left_arm","role_command_board_edge",38,195,-1.25F,-2.45F,-2.15F,4.5F,0.9F,0.35F);
        piece(root,military,heavy,"bwRole_commander","head","role_commander_visor_flash_right",50,195,-4.15F,-6.85F,-4.82F,2.6F,0.48F,0.2F);
        piece(root,military,heavy,"bwRole_commander","head","role_commander_visor_flash_left",58,195,1.55F,-6.85F,-4.82F,2.6F,0.48F,0.2F);
        piece(root,military,heavy,"bwRole_commander","body","role_coat_front_right",66,195,-4.1F,10.25F,-2.65F,3.65F,3.7F,0.6F);
        piece(root,military,heavy,"bwRole_commander","body","role_coat_front_left",77,195,0.45F,10.25F,-2.65F,3.65F,3.7F,0.6F);
        piece(root,military,heavy,"bwRole_commander","body","role_coat_rear",88,195,-4.1F,9.8F,2.25F,8.2F,4.15F,0.65F);
        piece(root,military,heavy,"bwRole_commander","body","role_belt_buckle",108,195,-1F,9.25F,-2.75F,2F,1.05F,0.65F);
        piece(root,military,heavy,"bwRole_commander","body","role_command_back_tab",116,195,-1.65F,3.25F,4.62F,3.3F,2F,0.25F);
        return LayerDefinition.create(mesh,256,256);
    }
    /** Retain original role/gear filtering while moving lower-arm pieces onto the real elbow. */
    private static void piece(PartDefinition root,boolean military,boolean heavy,String group,
                              String parent,String name,int u,int v,
                              float x,float y,float z,float w,float h,float d) {
        if(group.startsWith("weapon_"))return; // Replaced by measured meshes from the production reference profile.
        boolean include=switch(group){case "military"->military;case "bw"->!military;case "regular"->!heavy;case "heavy"->heavy;case "bwHeavy"->!military&&heavy;default->true;};
        if(group.startsWith("militaryRole_")&&!military||group.startsWith("bwRole_")&&military)include=false;
        if(!include)return;
        var target=root.getChild(parent);boolean arm=parent.endsWith("_arm"),lower=false;
        if(arm){
            x+=parent.equals("right_arm")?1:-1;y+=2;
            if(name.equals("uniform_sleeve")){
                boolean left=parent.equals("left_arm");var upperUv=REFERENCE.armUv(left,false);var lowerUv=REFERENCE.armUv(left,true);
                target.addOrReplaceChild(name,CubeListBuilder.create().texOffs(upperUv[0],upperUv[1]).addBox(x,0,z,w,6,d),PartPose.ZERO);
                target.getChild("forearm").addOrReplaceChild(name,CubeListBuilder.create().texOffs(lowerUv[0],lowerUv[1]).addBox(x,0,z,w,6,d),PartPose.ZERO);return;
            }
            // Gear crossing the crease follows its centre; cloth itself is split into two exact six-unit pieces.
            lower=y+h*.5F>=6;
            if(lower){target=target.getChild("forearm");y-=6;}
        }
        if(group.contains("Role_"))target=target.getChild("role_"+group.substring(group.indexOf("Role_")+5));
        else if(group.startsWith("rank_"))target=target.getChild(group);
        else if(name.startsWith("gear_"))target=target.getChild("equipment");
        target.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,v).addBox(x,y,z,w,h,d),PartPose.ZERO);
    }
    private static void position(ModelPart part,BlackwatchRigPose.Vec p){part.setPos((float)p.x(),(float)p.y(),(float)p.z());}
    private static void rotation(ModelPart part,BlackwatchRigPose.Vec p){part.setRotation((float)p.x(),(float)p.y(),(float)p.z());}
    private static void arm(ModelPart upper,ModelPart lower,BlackwatchRigPose.Arm pose){position(upper,pose.shoulder());rotation(upper,pose.upper());rotation(lower,pose.forearm());lower.setPos(0,6,0);}
    /** Same pose adapter used by the live renderer, FK tests and real-asset offline previews. */
    public void applyReferencePose(BlackwatchRigPose.Pose pose){
        carry.visible=pose.weight()>0;carry.xScale=pose.leftHanded()?-1:1;
        position(body,pose.torsoPosition());rotation(body,pose.torsoRotation());
        position(carry,pose.carryPosition());rotation(carry,pose.carryRotation());
        arm(rightArm,rightForearm,pose.right());arm(leftArm,leftForearm,pose.left());
        position(head,pose.headPosition());rotation(head,pose.headRotation());
        position(rightLeg,pose.rightLegPosition());rotation(rightLeg,pose.rightLegRotation());
        position(leftLeg,pose.leftLegPosition());rotation(leftLeg,pose.leftLegRotation());
        if(pose.weight()<=0){rightForearm.setRotation(0,0,0);leftForearm.setRotation(0,0,0);}
    }
    /** Seated pose uses the actual split joints; the held gun remains in the entity inventory. */
    public void applyVehicleSeatPose(boolean driver){
        applyVehicleSeatPose(driver,0);
    }
    private void applyVehicleSeatPose(boolean driver,float drop){
        carry.visible=false;
        body.setPos(0,drop,0);body.setRotation(0,0,0);head.setPos(0,drop,0);
        rightArm.setPos(-6,drop,0);leftArm.setPos(6,drop,0);
        rightArm.setRotation(driver?-1.05F:-.35F,0,0);leftArm.setRotation(driver?-1.05F:-.35F,0,0);
        rightForearm.setRotation(driver?-.4F:-.2F,0,0);leftForearm.setRotation(driver?-.4F:-.2F,0,0);
        rightForearm.setPos(0,6,0);leftForearm.setPos(0,6,0);
        // Level, parallel legs tuck into the seat instead of splaying through its walls and floor.
        rightLeg.setPos(-1.9F,11+drop,0);leftLeg.setPos(1.9F,11+drop,0);
        rightLeg.setRotation(-(float)Math.PI/2,0,0);leftLeg.setRotation(-(float)Math.PI/2,0,0);
        for(String hem:List.of("role_coat_front_right","role_coat_front_left","role_coat_rear"))
            commandParts[2][1].getChild(hem).setPos(0,-.5F,0);
    }
    private static double blend(double current,double target,double delta){return current+(target-current)*(1-Math.exp(-Math.max(0,delta)*.3));}
    @Override public void setupAnim(MilitarySoldier entity,float swing,float amount,float age,float yaw,float pitch){
        // These objects are shared across NPCs and disguise/grab views: restore the entire added hierarchy first.
        modelRoot.getAllParts().forEach(ModelPart::resetPose);
        super.setupAnim(entity,swing,amount,age,yaw,pitch);hat.visible=false;carry.visible=false;
        rightForearm.visible=true;leftForearm.visible=true;
        rightArm.x=-6;rightArm.y=0;leftArm.x=6;leftArm.y=0;
        for(var part:equipment)part.visible=entity.equipped();for(var part:forearmEquipment)part.visible=entity.equipped();
        int role=switch(entity.commandRole()){case TEAM_LEADER->0;case OFFICER->1;case COMMANDER->2;default->-1;};
        for(int i=0;i<3;i++){for(var part:commandParts[i])part.visible=entity.equipped()&&i==role;for(var part:forearmCommands[i])part.visible=entity.equipped()&&i==role;}
        boolean commandHead=role>=1;if(military&&commandHead)equipment[0].visible=false;
        for(var part:hoodShell)part.visible=!commandHead;for(var part:patrolPacks)if(part!=null)part.visible=!commandHead;
        for(int i=0;i<ranks.length;i++)ranks[i].visible=entity.equipped()&&i==entity.rank().ordinal();
        var stack=entity.getMainHandItem();var gun=stack.getItem() instanceof FirearmItem item?item:null;
        var state=actors.computeIfAbsent(entity,ignored->new ActorState());
        if(entity.getVehicle() instanceof com.codex.blacklight.entity.MilitaryVehicle vehicle){
            state.view.reset();state.at=Double.NaN;
            String seat=vehicle.seatOf(entity.getUUID());
            applyVehicleSeatPose(vehicle.definition().seats().stream().anyMatch(s->s.id().equals(seat)&&s.job()==com.codex.blacklight.military.vehicle.VehicleDefinition.Job.DRIVER),com.codex.blacklight.client.vehicle.VehicleSeatPose.drop(vehicle.definition()));return;
        }
        if(!entity.equipped()||gun==null||com.codex.blacklight.combat.ConsumeControl.protectedEntity(entity)){
            state.view.reset();state.at=Double.NaN;return;
        }
        var ammo=FirearmItem.state(stack,0);var definition=gun.definition();long tick=entity.level().getGameTime();
        state.view.observe(definition.id(),0,ammo.magazine(),ammo.readyAt(),definition.cooldownTicks(),definition.reloadTicks(),tick,true);
        double clock=tick+(age-Math.floor(age)),delta=Double.isFinite(state.at)?Math.min(3,Math.max(0,clock-state.at)):1;
        state.at=clock;state.movement=blend(state.movement,amount,delta);state.crouch=blend(state.crouch,entity.isCrouching()?1:0,delta);
        state.aimYaw=blend(state.aimYaw,Math.toRadians(yaw)*.65,delta);state.aimPitch=blend(state.aimPitch,Math.toRadians(pitch)*.75,delta);
        applyReferencePose(BlackwatchRigPose.animated(state.aimYaw,state.aimPitch,entity.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT,
                state.view.reloadProgress(clock),1,age/20d,state.movement,state.crouch,state.view.shotAgeSeconds(clock)));
        for(var entry:weapons.entrySet())entry.getValue().visible=entry.getKey().equals(definition.id());
    }
}
