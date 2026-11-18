"""Record original attachment bytes and static DLL evidence; no engine execution."""
from pathlib import Path
import hashlib, json, re, struct

ROOT=Path(__file__).resolve().parents[1]
REF=Path('D:/PrototypeReference/p1')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
dll=REF/'input/prototypeenginef.dll';data=dll.read_bytes()
pe=struct.unpack_from('<I',data,0x3c)[0]
optional=pe+24;image_base=struct.unpack_from('<I',data,optional+28)[0]
sections=optional+struct.unpack_from('<H',data,pe+20)[0]
def raw(address):
    rva=address-image_base
    for i in range(struct.unpack_from('<H',data,pe+6)[0]):
        size,virtual,raw_size,file=struct.unpack_from('<4I',data,sections+i*40+8)
        if virtual<=rva<virtual+max(size,raw_size):return file+rva-virtual
    raise ValueError(hex(address))
def u32(address):return struct.unpack_from('<I',data,raw(address))[0]
def byte(address):return data[raw(address)]
def flt(address):return struct.unpack_from('<f',data,raw(address))[0]

objects=json.loads((REF/'private/greene-weapon-meta-objects.json').read_text())
mother=next(o for o in objects if o['name']=='MotherCore');meta=bytes.fromhex(mother['hex'])
bindings=[]
for match in re.finditer(rb'\x09VomitGun[1-9]|\x0aVomitGun10',meta):
    length=meta[match.start()];slot=meta[match.start()+1:match.start()+1+length].decode()
    joint=meta.find(b'\x05Mouth',match.end(),match.end()+70)
    if joint<0:continue
    at=joint+6;parent_pos=struct.unpack_from('>3f',meta,at);parent_rot=struct.unpack_from('>3f',meta,at+12)
    child_length=struct.unpack_from('>I',meta,at+24)[0]
    child=meta[at+28:at+28+child_length].decode();child_pos=struct.unpack_from('>3f',meta,at+28+child_length)
    child_rot=struct.unpack_from('>3f',meta,at+40+child_length)
    if any(parent_rot+child_pos+child_rot) or child:raise ValueError('Unhandled attachment '+slot)
    bindings.append({'slot':slot,'parentJoint':'Mouth','parentPosition':parent_pos,'parentEulerDegrees':parent_rot,
        'childJoint':child,'childPosition':child_pos,'childEulerDegrees':child_rot,'positionFileOffset':mother['dataOffset']+at})
if len(bindings)!=10:raise ValueError('Expected ten original emitters')

routes={
    'Grab_category_1f':hex(u32(0x102c897c+4*byte(0x102c8998+0x1f-2))),
    'Grab_subtype_1':hex(u32(0x102c89b8+4)),
    'Attachable_category_0f':hex(u32(0x1082aeb4+4*byte(0x1082aed0+0xf-2))),
    'Attachable_subtype_0':hex(u32(0x1082aeec+4*byte(0x1082af04))),
    'Attachable_mode_0':hex(u32(0x1082a1a4))}
expected={'Grab_category_1f':'0x102c8858','Grab_subtype_1':'0x102c887a','Attachable_category_0f':'0x1082ade4',
          'Attachable_subtype_0':'0x1082adff','Attachable_mode_0':'0x1082a05f'}
if routes!=expected:raise ValueError('Attachment jump table differs: '+str(routes))

evidence={
 '0x102c8a40':'GrabAutoSpawn creates parent offset from +18 position / +24 degree Euler and child offset from +38 / +44. 102c8c68 initializes descriptor; 102c8c8f and 102c8dc0 resolve parent/child joints. 102c8f18..102c8f59 sends category 1f subtype 1 with descriptor.',
 '0x107e9010':'Descriptor: parent GameObject handle +0, parent frame +4, child frame +50, blendSeconds +9c initialized 0, attachment mode +a0 initialized 0. GrabAutoSpawn does not overwrite mode.',
 '0x107e3380':'Each frame defaults to identity 4x4; joint +40 and position-only joint +48 are 0xffff; separate position flag +44 is false.',
 '0x10127b00':'Empty joint name returns 0xffff; nonempty joint resolves through object PoseAttribute 11283000. MotherCore supplies Mouth; emitter child joint is empty.',
 '0x102c8830':'Jump tables route category 1f subtype 1 to 102c887a, which calls 102c7ac0.',
 '0x102c7ac0':'102c7b82..102c7b93 copies supplied descriptor. 102c7bd5..102c7bda resets parent handle to actual grabbing owner. 102c7c3f..102c7c71 dispatches attachment category f subtype 0 to spawned weapon.',
 '0x1082adc0':'Jump tables route category f subtype 0 to 1082adff; descriptor message+c passed to 10829f40.',
 '0x10829f40':'Copies descriptor into active attachment, captures parent pose and preserves mode 0. Default mode enables update flag 40, rather than modes 2/3 physics attachment.',
 '0x1082ab50':'Active, valid parent attachment calls 1082a1c0; invalid handle detaches.',
 '0x1082a1c0':'Mode 0/1 and update flag exactly 40 call 10829720(dt, descriptor, &blendSeconds).',
 '0x107e32d0':'Output starts with local frame matrix, then multiplies joint pose matrix from pose+34 + index*64 when joint != ffff. Separate position-only path is disabled for these descriptors. Full pose basis reaches output: no scale removal here.',
 '0x10829720':'10829843 evaluates child frame; 10829861 parent frame. Copies child frame, inverts via 107bf0d0, multiplies parent frame then parent GameObject matrix via 107bf620. Crucially 10829894 calls 107bbff0 AFTER both multiplications.',
 '0x107bbff0':'Copies basis row X and old Y. 107bc03b normalizes X via 107bae80; 107bc040..107bc0a4 computes and normalizes Z=X cross oldY; 107bc0a9..107bc131 rebuilds Y=Z cross X. Only 3x3 basis entries are written. Translation remains unchanged; scale and shear are removed, including from parent GameObject.',
 '0x10828a00':'blendSeconds compared with constant 10e6c20c; at zero, jbe returns without blending. Default zero blend therefore retains the new orthonormalized world attachment immediately.',
 '0x101122e0':'FixedDirection angles converted degrees->radians using 10d93e54 then 107bb870; negates third row, resolves space via 10259660 and normalizes.',
 '0x107bb870':'Negated third row for angles x,y,z: [-(cos(z)*cos(x)*sin(y)+sin(z)*sin(x)), -(sin(z)*cos(x)*sin(y)-cos(z)*sin(x)), -cos(y)*cos(x)].'}
files=['src/main/java/com/codex/blacklight/combat/GreeneMouth.java','src/test/java/com/codex/blacklight/combat/GreeneMouthTest.java']
report={'schema':1,'date':'2026-11-17','state':'Native Mouth attached weapon direction implemented; compilation and tests deferred',
 'dll':{'path':str(dll),'sha256':sha(dll)},'meta':{'path':str(REF/'private/greene-weapon-meta-objects.json'),'sha256':sha(REF/'private/greene-weapon-meta-objects.json')},
 'bindings':bindings,'jumpTableBytesDecoded':routes,'constants':{'degreesToRadians':flt(0x10d93e54),'attachmentBlendThreshold':flt(0x10e6c20c)},
 'nativeStatic':evidence,
 'matrixContract':{'originalRowVector':'weaponWorld = orthonormalBasis(inverse(childOffset * childJointPose) * parentOffset * MouthPose * motherWorld); translation survives orthonormalization.',
   'theseTenEmitters':'Child frame identity, child joint absent, parent Euler zero; local parent offsets change origin only. All ten emitters share the same orthonormalized Mouth world basis.',
   'sourceScale':'Mouth pose retains complete parent ROT/TRAN/SCL and parent-scale compensation before attachment. Position retains affine basis; weapon direction is not normalized full-scale transformDirection.',
   'minecraftColumns':'Native rows transpose into JOML basis columns. X=normalize(column0); Z=normalize(X cross column1); Y=Z cross X; apply source Euler forward as coefficients of X,Y,Z.',
   'axes':'Current rebind_greene_source.py uses K @ sourceLocal @ K^-1, with source X equal to runtime model X. No extra mirror-X. Geometry JSON has its separate Gecko X conversion.',
   'world':'Use the same Minecraft world yaw rotation 180-yaw as GreeneMouth.position. No 16-pixel division is applied to unit direction.'},
 'api':'GreeneMouth.direction(float[][] pose, float yaw, float[] sourceEulerDegrees) -> normalized Vec3; existing position APIs unchanged.',
 'boundaries':['Original descriptor blending is zero for these ten emitters; no invented frame lag or new smoothing.',
   'Missing native Mouth or degenerate/nonfinite basis throws instead of emitting a NaN direction; this defensive error behavior is an explicit Minecraft boundary.',
   'Native skin remains authored Minecraft cuboids and existing scale; this task changes no pose exporter, geometry, socket offsets or collision data.',
   'Four Java regression scenarios added, not executed. No native DLL invocation/emulation, compilation, game/client or media playback.'],
 'codeSha256':{f:sha(ROOT/f) for f in files},
 'relatedRecords':['art/encounters/greene-native-bind-correction.json','art/encounters/greene-native-scale-source.json','art/encounters/greene-missile-flight-source.json']}
destination=ROOT/'art/encounters/greene-mouth-attachment-source.json'
destination.write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print('Recorded',len(bindings),'Mouth attachments;',len(routes),'static jump table routes;',sha(destination))
