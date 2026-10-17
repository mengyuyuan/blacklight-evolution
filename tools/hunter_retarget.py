"""Calibrate the authored rest axes to the decoded hunter's joint directions.

The skin remains in its authored rest pose. A per-joint rest correction is
essential: copying rotational deltas assumes identical rest directions, which
is false for the bent elbows/knees of these two rigs. Preserve source twist,
then aim each collapsed chain at its actual animated child joint.
"""
from mathutils import Matrix, Vector

def calibration(geo, reference, mapping, reflection):
    pivots = {b['name']: Vector((-b['pivot'][0], b['pivot'][1], b['pivot'][2])) for b in geo}
    # Use the bone's direct child to measure its axis. A pelvis-to-upper-chest
    # vector includes spine bending and would incorrectly rotate the whole
    # pelvis/legs whenever the creature crouches or winds up a strike.
    aims = {'hips': ('torso', 'Spine_1'), 'torso': ('head', 'Neck')}
    for side, suffix, sign in [('left', 'L', -1), ('right', 'R', 1)]:
        pivots[side+'_hand_tip'] = Vector((sign*14, 3, -11))
        pivots[side+'_foot_tip'] = Vector((sign*7, 1.2, 11.8))
        aims.update({side+'_arm': (side+'_forearm', 'Elbow_'+suffix),
                     side+'_forearm': (side+'_hand_tip', 'Wrist_'+suffix),
                     side+'_thigh': (side+'_shin', 'Knee_'+suffix),
                     side+'_shin': (side+'_foot_tip', 'Ball_'+suffix)})
    offsets = {}; directions = {}; report = {}
    for bone, source in mapping.items():
        if bone in aims:
            child, source_child = aims[bone]
            rest = (pivots[child]-pivots[bone]).normalized()
            source_rest = reflection @ (reference[source_child].translation-reference[source].translation).normalized()
            offsets[bone] = rest.rotation_difference(source_rest)
            directions[bone] = rest
            report[bone] = {'authoredDirection': list(rest), 'sourceDirection': list(source_rest),
                            'restCorrectionDegrees': offsets[bone].angle*180/3.141592653589793}
        else:
            # Preserve head/jaw attachment to the calibrated chest at rest.
            offsets[bone] = offsets['torso'].copy()
    return aims, offsets, directions, report

def rotations(current, reference, mapping, reflection, setup):
    aims, offsets, directions, _ = setup
    result = {}
    for bone, source in mapping.items():
        delta = current[source].to_quaternion() @ reference[source].to_quaternion().inverted()
        world = (reflection @ delta.to_matrix() @ reflection).to_quaternion() @ offsets[bone]
        if bone in aims:
            source_child = aims[bone][1]
            desired = reflection @ (current[source_child].translation-current[source].translation).normalized()
            # Intermediate spine/clavicle joints are collapsed in the MC rig.
            # Their translations must influence the aim, not stretch the skin.
            world = (world @ directions[bone]).rotation_difference(desired) @ world
        result[bone] = world.normalized()
    return result

def pelvis_height(geo, current, reference, mapping, world_rotations):
    """Retarget root height against sole contact, with fixed target limb lengths.

    Source ankles are hocks, not soles. The old ankle-to-foot mapping lifted
    both feet. Ball joints supply the support plane; source airborne lift is
    retained relative to Motion_Root, whose world motion remains AI-owned.
    """
    transforms = {'root': Matrix.Identity(4)}
    for b in geo[1:]:
        name=b['name'];parent=b['parent'];p=Vector((-b['pivot'][0],b['pivot'][1],b['pivot'][2]))
        rotation=world_rotations[parent].inverted()@world_rotations[name] if parent in world_rotations else world_rotations[name]
        transforms[name]=transforms[parent]@Matrix.Translation(p)@rotation.to_matrix().to_4x4()@Matrix.Translation(-p)
    feet=[(transforms[side+'_shin']@Vector((sign*7,1.2,11.8))).y for side,sign in [('left',-1),('right',1)]]
    def support(pose):return min(pose['Ball_L'].translation.y,pose['Ball_R'].translation.y)-pose['Motion_Root'].translation.y
    scale=18/max(.001,reference['Pelvis'].translation.y)
    desired=1.2+(support(current)-support(reference))*scale
    return desired-min(feet)
