"""Original connected late-body growth, fitted to the existing animated joints.

Keeps all approved early geometry and native skins. Only the lateGrowth channel
is replaced; runtime unfolds it after full maturity using the saved tissue age.
"""
from pathlib import Path
import json, math
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/blacklight'
CHITIN=[0x242128,0x353039,0x29252c,0x46353a]
FLESH=[0x5a242c,0x722c34,0x452129,0x8b3c40]
BONE=[0x918675,0xb0a38b,0x6d665b,0x8a796a]

class Mesh:
    def __init__(self,ref=None):self.vertices=[];self.triangles=[];self.ref=ref
    def point(self,p):
        if self.ref is None:return np.array(p,dtype=float)
        low=np.array(self.ref['min']);return low+np.array(p)*(np.array(self.ref['max'])-low)
    def vertex(self,p):
        self.vertices.extend(np.round(p,6).tolist());return len(self.vertices)//3-1
    def face(self,ids,color):
        for i in range(1,len(ids)-1):self.triangles.extend([ids[0],ids[i],ids[i+1],color])
    def tube(self,centres,radii,colors=CHITIN):
        points=[self.point(p) for p in centres];start=len(self.vertices)//3
        # Four broad planes and clipped corners give a Minecraft silhouette.
        ring=[(-1,-.6),(-.6,-1),(.6,-1),(1,-.6),(1,.6),(.6,1),(-.6,1),(-1,.6)]
        scale=np.ones(3) if self.ref is None else np.array(self.ref['max'])-self.ref['min']
        for i,p in enumerate(points):
            direction=points[min(i+1,len(points)-1)]-points[max(i-1,0)];direction/=np.linalg.norm(direction)
            side=np.cross(direction,[0,0,1])
            if np.linalg.norm(side)<.1:side=np.cross(direction,[0,1,0])
            side/=np.linalg.norm(side);other=np.cross(direction,side)
            rw,rd=radii[i]
            # Radius inputs are physical block units, unlike centre coordinates.
            for x,y in ring:self.vertex(p+side*x*rw+other*y*rd)
        for level in range(len(points)-1):
            for edge in range(8):
                a=start+level*8+edge;b=start+level*8+(edge+1)%8
                self.face([a,b,b+8,a+8],colors[(edge+level)%len(colors)])
        self.face(list(range(start+7,start-1,-1)),colors[0])
        end=start+(len(points)-1)*8;self.face(list(range(end,end+8)),colors[0])
    def sheet(self,points,color):
        ids=[self.vertex(self.point(p)) for p in points];self.face(ids,color)
    def json(self):return {'vertices':self.vertices,'triangles':self.triangles}

def late_parts(species):
    path=ASSETS/f'infection_parts/{species}.json';data=json.loads(path.read_text())
    for b in data['bones']:
        m=Mesh(b['reference']);name=b['name'];lo=np.array(b['reference']['min']);hi=np.array(b['reference']['max']);s=hi-lo
        if species in ('bat','phantom'):
            if name=='body':
                if species=='bat':
                    m.tube([(.5,.06,.28),(.5,.45,.18),(.5,.86,.33)],[(s[0]*.28,s[2]*.28),(s[0]*.72,s[2]*.9),(s[0]*.52,s[2]*.6)],FLESH)
                    m.tube([(.5,.05,.08),(.5,.45,-.22),(.5,.85,.09)],[(.025,.016),(.055,.03),(.03,.02)])
                    for x in (.12,.88):m.tube([(x,.2,.8),(x,.65,1.1),(x,.95,.75)],[(.04,.04),(.07,.06),(.045,.035)])
                else:
                    m.tube([(.5,.12,.04),(.5,.2,.42),(.5,.35,.9)],[(s[0]*.35,s[1]*.55),(s[0]*.6,s[1]*1.1),(s[0]*.4,s[1]*.65)],FLESH)
                    m.tube([(.5,.8,.05),(.5,1.5,.38),(.5,1.2,.9)],[(.07,.04),(.14,.08),(.065,.03)])
                    for x in (.12,.88):m.tube([(x,.5,.12),(x,.8,.4),(x,.55,.85)],[(.06,.04),(.085,.06),(.04,.025)])
            elif name=='head':
                # Paired jaw rami are rooted behind the cheeks, with a clear mouth gap.
                for x in (.13,.87):
                    z=-.55 if species=='bat' else -.62
                    m.tube([(x,.48,.72),(x,.1,-.02),(x*.5+.25,.1,z)],[(.035,.035),(.045,.03),(.018,.018)])
                    m.tube([(x,.12,-.23),(x,.33,-.3)],[(.017,.017),(.001,.001)],BONE)
                if species=='bat':m.tube([(.5,.72,.9),(.5,1.25,.72),(.5,1.4,.35)],[(.07,.045),(.055,.03),(.006,.006)])
            elif name.startswith('wing'):
                positive=name.endswith('_1')
                def w(u,y,z):return (u if positive else 1-u,y,z)
                if species=='bat':
                    paths=[[(.03,.85,.3),(.5,.98,.3),(1.42,.2,.3)],[(.15,.72,.3),(.65,.49,.3),(1.03,-.18,.3)],[(.1,.7,.3),(.39,.2,.3),(.47,-.28,.3)]]
                    radii=[(.08,.035),(.065,.03),(.008,.006)]
                    for p in paths:m.tube([w(*q) for q in p],radii)
                    # Wrap both sides of the native cutout wing. Mid-plane panels
                    # were hidden behind that wing and barely changed its outline.
                    for z in (.12,.88):
                        m.sheet([w(.08,.87,z),w(.5,.93,z),w(1.39,.21,z),w(1.01,-.12,z),w(.64,.28,z)],0x51232e)
                        m.sheet([w(.1,.76,z),w(.64,.28,z),w(.47,-.22,z),w(.18,.15,z)],0x6b2933)
                else:
                    m.tube([w(.02,.65,.1),w(.44,1,.05),w(1,.65,.12)],[(.06,.04),(.07,.035),(.008,.006)])
                    for u,z in ((.47,1.28),(.83,1.12)):
                        m.tube([w(.18,.6,.2),w(u*.85,.8,.65),w(u,.6,z)],[(.035,.025),(.035,.025),(.003,.003)])
                    m.sheet([w(.16,.68,.28),w(.75,.78,.4),w(.81,.64,1.08),w(.44,.64,.9)],0x672b35)
            elif species=='phantom' and name=='tail':
                m.tube([(.5,.6,.0),(.5,1.05,.5),(.5,.7,1.22)],[(.065,.04),(.055,.04),(.006,.006)])
                for side in (-1,1):m.sheet([(.5,.65,.3),(.5+side*2,.68,.72),(.5,.65,1.22)],0x4a242c)
        elif species=='iron_golem':
            if name=='body':
                # Broad scapular arch joins both shoulders; the abdomen remains readable.
                m.tube([(.5,.25,.52),(.5,.65,.72),(.5,.93,.58)],[(.35,.2),(.66,.4),(.52,.29)])
                for x in (.18,.82):m.tube([(x,.28,-.02),(x,.61,-.2),(x,.9,.08)],[(.055,.05),(.11,.07),(.09,.06)],FLESH)
            elif name.startswith('arm'):
                side=-1 if name.endswith('_-1') else 1
                x=.5+side*.14
                # A single tapered shoulder and forearm sheath, three rooted digits.
                m.tube([(x,.96,.48),(x+side*.42,.79,.5),(x+side*.16,.57,.46)],[(.2,.24),(.3,.3),(.19,.22)])
                m.tube([(.5,.52,.5),(.5,.26,.38),(.5,.1,.26)],[(.19,.23),(.3,.34),(.26,.29)],FLESH)
                m.tube([(.5,.51,.68),(.5,.25,.82),(.5,.08,.6)],[(.16,.12),(.25,.16),(.21,.13)])
                for x in (.12,.5,.88):m.tube([(x,.14,.12),(x,-.02,-.12),(x,-.12,-.45)],[(.06,.055),(.05,.04),(.006,.006)],BONE)
            elif name=='head':
                for x in (.05,.95):m.tube([(x,.25,.5),(x,.65,.75),(x,1.1,.8)],[(.05,.06),(.08,.07),(.012,.012)])
            elif name.startswith('leg'):
                m.tube([(.5,.88,.5),(.5,.5,.66),(.5,.2,.55)],[(.16,.19),(.21,.23),(.16,.17)])
        b['lateGrowth']=m.json()
    if species=='bat':
        tips=[]
        for side,name in ((-1,'right_wing_tip'),(1,'left_wing_tip')):
            # Minecraft 1.21 wing-tip joint coordinates, in model pixels. The
            # outer six-pixel section must follow its own hinge, not the base.
            m=Mesh({'min':[0 if side>0 else -6,-2,-.12],'max':[6 if side>0 else 0,6,.12]})
            def t(x,y,z):return (x if side>0 else 1-x,y,z)
            for z in (0,1):
                m.sheet([t(0,.02,z),t(1.12,.06,z),t(1.05,.62,z),t(.62,.42,z)],0x51232e)
                m.sheet([t(0,.08,z),t(.62,.42,z),t(.62,1.08,z),t(0,.85,z)],0x6b2933)
            m.tube([t(0,.03,.5),t(.6,.02,.5),t(1.12,.06,.5)],[(.3,.16),(.25,.14),(.06,.05)])
            m.tube([t(0,.4,.5),t(.6,.55,.5),t(.72,1.12,.5)],[(.25,.15),(.22,.14),(.04,.04)])
            tips.append({'name':name,**m.json()})
        data['nativeLateGrowth']=tips
    path.write_text(json.dumps(data,separators=(',',':'))+'\n',encoding='utf-8')

def late_wither():
    path=ASSETS/'infected_models/wither.json';data=json.loads(path.read_text())
    for b in data['bones']:
        m=Mesh();n=b['name']
        if n=='chest':
            # A closed muscular thorax grows inside and between the existing ribs.
            m.tube([(0,13,1),(0,20,0),(0,25,0)],[(2.6,1.5),(5.6,3.1),(3.6,2.1)],FLESH)
            m.tube([(0,13,-.3),(0,20,-3.05),(0,25,-1.5)],[(.6,.5),(1.2,.6),(.6,.4)])
        elif n.startswith('rib_cage'):
            side=-1 if n.endswith('_-1') else 1
            m.tube([(side*2.5,24,1),(side*7.5,22,2),(side*8,17,1)],[(.8,.7),(1.2,1),(.5,.5)])
            for y in (18,21):m.tube([(side*2,y,1),(side*5,y,-3.5),(side*7,y-1,0)],[(.4,.4),(.7,.5),(.4,.4)])
        elif n.startswith('neck'):
            side=-1 if n.endswith('_-1') else 1
            m.tube([(side*2,24,1),(side*6,26,1),(side*10,28,0)],[(1.5,1.1),(1.8,1.3),(1.4,1)],FLESH)
        elif n.startswith('tendril'):
            side=-1 if n.endswith('_-1') else 1
            m.tube([(side*2,13,2),(side*4,7,3),(side*6,1,4),(side*9,1.5,6)],[(1.1,.8),(1.2,.8),(.6,.5),(.02,.02)])
        b['lateGrowth']=m.json()
    path.write_text(json.dumps(data,separators=(',',':'))+'\n',encoding='utf-8')

if __name__=='__main__':
    for species in ['bat','phantom','iron_golem']:late_parts(species)
    late_wither()
    print('Authored age-driven late growth on four existing species skeletons.')
