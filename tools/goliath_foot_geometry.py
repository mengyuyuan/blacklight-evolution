"""Attach project-designed boots and toe plates to animated joints."""
import copy

import numpy as np

import goliath_source_pose


def add_feet(geometry, motion, reader, rig, clips, nodes):
    bones = geometry['minecraft:geometry'][0]['bones']
    owners = {b['name']: b for b in bones}
    names = motion['bones']
    parents = motion['parents']
    pivots = motion['pivots']
    pixels = motion['pixelsPerSourceUnit']
    signs = np.diag([-1., 1., 1.])
    bind = goliath_source_pose.world(reader, rig, {'tracks': []}, 0)
    indices = {name: i for i, (name, _, _) in enumerate(rig)}
    foot_joints = []

    def add_joint_layer(name, parent):
        parents.append(names.index(parent))
        names.append(name)
        pivots.append([0., 0., 0.])
        bones.append(dict(name=name, parent=parent, pivot=[0, 0, 0]))

    def fitted_cube(template, joint, low, high):
        # The template provides all six authored UV rectangles. Its dimensions
        # follow the weighted raw-bind envelope, expressed in joint-local axes.
        inv = np.linalg.inv(bind[joint])
        center = (inv @ np.r_[(low + high) * .5, 1])[:3] * pixels
        size = (high - low) * pixels
        axes = signs @ inv[:3, :3] @ signs
        cube = copy.deepcopy(template)
        cube.pop('inflate', None)
        cube['origin'] = (center - size / 2).round(6).tolist()
        cube['pivot'] = center.round(6).tolist()
        cube['size'] = size.round(6).tolist()
        cube['rotation'] = (np.degrees(reader.euler(axes)) * [-1, -1, 1]).round(6).tolist()
        return cube

    for side, suffix in [('right', 'R'), ('left', 'L')]:
        shin = owners[side + '_shin']
        assert len(shin['cubes']) == 6, 'Expected shin, knee guard, sole and three toe cubes'
        shoe, *toes = shin['cubes'][2:]
        shin['cubes'] = shin['cubes'][:2]
        for name, joint, templates in [(side + '_foot', 'Ankle_' + suffix, [shoe]),
                                       (side + '_toes', 'Ball_' + suffix, toes)]:
            # The surviving L toes are all weighted to Ankle_L in TorsoShape001.
            if joint == 'Ball_L':
                joint = 'Ankle_L'
            index = indices[joint]
            parent = rig[index][1]
            bridge = 'source_parent_scale_' + joint
            animated = 'source_' + joint
            if animated not in names:
                add_joint_layer(bridge, 'source_' + rig[parent][0])
                add_joint_layer(animated, bridge)
                foot_joints.append((index, parent))
            # Project-designed boots and split toes. Bind positions locate the attachment;
            # width, height and toe offsets are specified in Minecraft model pixels.
            cubes=[]
            for toe,template in enumerate(templates):
                size=np.array([14.,8.,20.]) if len(templates)==1 else np.array([4.,4.5,10.])
                offset=np.array([0.,-1.,-3.]) if len(templates)==1 else np.array([(toe-1)*4.6,-2.,-5.])
                center=bind[joint][:3,3]+offset/pixels
                cube=fitted_cube(template,joint,center-size/(2*pixels),center+size/(2*pixels))
                # Rest the planted sole on block Y=0 during the full kneel. The ankle
                # and toe joints have different local axes; offsets are model pixels.
                if side=='right':
                    pad=[-1,2,0] if len(templates)==1 else [.5,-2,0]
                    for field in ('origin','pivot'):
                        cube[field]=[round(value+shift,6) for value,shift in zip(cube[field],pad)]
                cubes.append(cube)
            bones.append(dict(name=name, parent=animated, pivot=[0, 0, 0], cubes=cubes))

    for clip in motion['clips'].values():
        original = clips[clip['source']]
        for frame, row in enumerate(clip['frames']):
            local = goliath_source_pose.locals(reader, rig, original, frame)
            for index, parent in foot_joints:
                matrix = local[index]
                scales = np.linalg.norm(matrix[:3, :3], axis=0)
                axes = signs @ (matrix[:3, :3] / scales) @ signs
                offset = (signs @ matrix[:3, 3] * pixels).round(8)
                inverse = (1 / np.linalg.norm(local[parent][:3, :3], axis=0)).round(8)
                row.append([0., 0., 0., *offset.tolist(), *inverse.tolist()])
                row.append([*np.round(reader.euler(axes), 8).tolist(), 0., 0., 0., *scales.round(8).tolist()])

    add_calves(geometry, motion, reader, rig, clips)


def add_calves(geometry, motion, reader, rig, clips):
    # Ankle TRAN varies independently of the original knee's rotation (for
    # example 2.13 -> 2.85 source units in the left leg break). Preserve the
    # same authored calf plate while stretching it from knee to ankle.
    reference = goliath_source_pose.world(reader, rig, clips[reader.PROFILES['goliath'][2]], 0)
    signs = np.diag([-1., 1., 1.])
    bones = geometry['minecraft:geometry'][0]['bones']
    owners = {b['name']: b for b in bones}
    names = motion['bones']
    for side, suffix in [('right', 'R'), ('left', 'L')]:
        knee = 'Knee_' + suffix
        ankle = 'Ankle_' + suffix
        shaft = owners[side + '_shin']['cubes'].pop(0)
        size = np.array(shaft['size'])
        base_length = np.linalg.norm(reference[ankle][:3, 3] - reference[knee][:3, 3]) * motion['pixelsPerSourceUnit']
        x, y, z = np.radians(shaft['rotation']) * [-1, -1, 1]
        cx, sx, cy, sy, cz, sz = np.cos(x), np.sin(x), np.cos(y), np.sin(y), np.cos(z), np.sin(z)
        authored_axes = np.array([[cz*cy, cz*sy*sx-sz*cx, cz*sy*cx+sz*sx],
                                  [sz*cy, sz*sy*sx+cz*cx, sz*sy*cx-cz*sx], [-sy, cy*sx, cy*cx]])
        axes = signs @ np.linalg.inv(reference[knee][:3, :3]) @ signs @ authored_axes
        bind_span = signs @ np.linalg.solve(reference[knee][:3, :3], reference[ankle][:3, 3]-reference[knee][:3, 3])
        direction = 1 if axes[:, 1] @ bind_span > 0 else -1
        axes = reader.rotation_between(axes[:, 1], direction*bind_span/np.linalg.norm(bind_span)) @ axes
        size[1] = base_length + min(size[0], size[2]) * .4
        center = np.array([0., direction*base_length/2, 0.])
        shaft.update(origin=(center-size/2).round(6).tolist(), pivot=[0, 0, 0],
                     size=size.round(6).tolist(), rotation=[0, 0, 0])
        name = side + '_calf'
        parent = 'source_' + knee
        bridge = names.index('source_parent_scale_' + ankle)
        bones.append(dict(name=name, parent=parent, pivot=[0, 0, 0], cubes=[shaft]))
        motion['parents'].append(names.index(parent))
        motion['pivots'].append([0., 0., 0.])
        names.append(name)
        for clip in motion['clips'].values():
            for row in clip['frames']:
                span = np.array(row[bridge][3:6])
                length = np.linalg.norm(span)
                rotation = reader.rotation_between(axes[:, 1], direction*span/length) @ axes
                row.append([*np.round(reader.euler(rotation), 8).tolist(), 0., 0., 0., 1., round(length/base_length, 8), 1.])
