#!/usr/bin/env python3
"""Reproducible original pedagogical miniatures. No third-party score data used.
These files are exercises, not substitute editions of the named Chopin waltzes.
All generated notation and motifs are dedicated to the public domain under CC0.
"""
import json, random, xml.etree.ElementTree as ET
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'app/src/main/assets/scores'
NAMES = [
 ['First Light','Quiet Steps','Little Lantern','Two Hands Together','Morning Bells','A Simple Answer','Garden Path','Gentle Waltz','Open Window','Homeward'],
 ['River Stones','Turning Leaves','Evening Walk','Waltz for a Rainy Day','The Small Clock','Skipping Pebbles','Bluebird','Across the Bridge','Candlelight','A New Beginning'],
 ['Amber Waltz','Rolling Hills','Nocturne Study','Waterwheel','Broken Chords','Counterpoint in Green','Shifting Shadows','A Minor Conversation','The Old Courtyard','Flowing Lines'],
 ['Glass Arpeggios','Autumn Dialogue','Moonlit Steps','Syncopation Study','Inner Voices','Spiral Staircase','Restless Water','Distant Bells','Chromatic Thread','Evening Reverie'],
 ['Prism Toccata','Contrary Motion Study','Waltz in Five Colors','Cascade','Interlocking Voices','Night Journey','Harmonic Turns','Rapid Reflections','Wide Horizons','Finale in Light']
]
LEVELS = ['Beginner','Elementary','Intermediate','Upper-intermediate','Advanced']
KEYS=[(0,0),(7,1),(5,-1),(2,2),(9,3),(3,-3),(10,-2),(4,4)]
def element(parent, tag, text=None, **attrs):
 e=ET.SubElement(parent,tag,attrs)
 if text is not None:e.text=str(text)
 return e
def pitch(parent,midi):
 p=element(parent,'pitch');names=['C','C','D','D','E','F','F','G','G','A','A','B'];pc=midi%12
 element(p,'step',names[pc])
 if pc in [1,3,6,8,10]:element(p,'alter',1)
 element(p,'octave',midi//12-1)
def note(measure,midi,duration,staff,chord=False,rest=False,finger=None):
 n=element(measure,'note')
 if chord:element(n,'chord')
 if rest:element(n,'rest')
 else:pitch(n,midi)
 element(n,'duration',round(duration*4));element(n,'voice',staff)
 types={.25:('16th',False),.5:('eighth',False),.75:('eighth',True),1:('quarter',False),1.5:('quarter',True),2:('half',False),3:('half',True),4:('whole',False)}
 t,d=types[duration];element(n,'type',t)
 if d:element(n,'dot')
 element(n,'staff',staff)
 if finger:element(element(element(n,'notations'),'technical'),'fingering',finger)
CAT=[]
for level,titles in enumerate(NAMES):
 for index,title in enumerate(titles):
  identifier=f'study-{level+1}-{index+1:02}'
  rng=random.Random(1103+level*100+index);root,fifths=KEYS[(index+level)%len(KEYS)] if level else [(0,0),(7,1),(5,-1)][index%3]
  beats=3 if index in [3,7] or 'Waltz' in title else 4;bars=8 if level<2 else 12 if level<4 else 16
  scale=[0,2,4,5,7,9,11];tempo=[64,72,80,88,104][level]
  score=ET.Element('score-partwise',{'version':'4.0'})
  element(element(score,'work'),'work-title',title)
  identification=element(score,'identification');element(identification,'creator','Piano Path',type='composer');element(identification,'rights','CC0-1.0 · Original Piano Path exercise. No third-party edition used.')
  sp=element(element(score,'part-list'),'score-part',id='P1');element(sp,'part-name','Piano');part=element(score,'part',id='P1')
  motif=[0,1,2,4,3,2,1,0] if index%2==0 else [4,2,3,1,2,0,1,0]
  def degree(d,base):return base+root+scale[d%7]+12*(d//7)
  for bar in range(bars):
   m=element(part,'measure',number=str(bar+1));a=element(m,'attributes') if bar==0 else None
   if a is not None:
    element(a,'divisions',4);element(element(a,'key'),'fifths',fifths);time=element(a,'time');element(time,'beats',beats);element(time,'beat-type',4);element(a,'staves',2)
    for staff,sign,line in [(1,'G',2),(2,'F',4)]:c=element(a,'clef',number=str(staff));element(c,'sign',sign);element(c,'line',line)
    direction=element(m,'direction');metro=element(element(direction,'direction-type'),'metronome');element(metro,'beat-unit','quarter');element(metro,'per-minute',tempo);element(direction,'sound',tempo=str(tempo))
   for staff in [1,2]:
    if staff==2:element(element(m,'backup'),'duration',beats*4)
    pos=0;step=0;last=0
    while pos<beats:
     if bar==bars-1:
      duration=float(beats);d=0;rest=False
     else:
      opts=[1.] if level==0 else [.5,1.] if level==1 else [.5,1.,1.5] if level==2 else [.25,.5,.75,1.] if level==3 else [.25,.5,.75]
      if staff==2 and level<2:opts=[float(beats)] if level==0 else [1.,2.]
      duration=rng.choice([x for x in opts if x<=beats-pos])
      motif_index=(bar%4*3+step+index)%len(motif)
      d=motif[motif_index]+(bar//4%2)*2
      if staff==2:d=[0,3,4,0][bar%4]+([0,2,4,2][step%4] if level>=2 else 0)
      if level>=3 and staff==1:d+=rng.choice([-2,0,0,2,4])
      rest=level>0 and step>0 and rng.randrange(18)==0
     midi=degree(d,60 if staff==1 else 36 if level>=2 else 48)
     note(m,midi,duration,staff,rest=rest)
     if not rest and ((bar==bars-1 and level>=1) or level>=2 and staff==1 and rng.randrange(5-level if level<4 else 2)==0):
      note(m,degree(d+2,60 if staff==1 else 48),duration,staff,chord=True)
      if level>=3:note(m,degree(d+4,60 if staff==1 else 48),duration,staff,chord=True)
     pos+=duration;step+=1
  ET.indent(score);path=OUT/f'{identifier}.musicxml';ET.ElementTree(score).write(path,encoding='UTF-8',xml_declaration=True)
  CAT.append(dict(key=identifier,title=title,composer='Piano Path · original study',difficulty=LEVELS[level],asset=path.name,license='CC0-1.0',attribution='Original notation and exercise by Piano Path contributors (2026). Public domain dedication.',source='Project scripts/generate_library.py',description=f'{bars}-measure original {beats}/4 study, tempo {tempo} BPM. Difficulty is approximate.'))
(OUT/'catalog.json').write_text(json.dumps(CAT,indent=2,ensure_ascii=False)+'\n')
lines=['# Offline score catalogue','','These 50 original pedagogical miniatures are CC0-1.0. They are not historical repertoire editions. Difficulty is approximate, not independently graded. Reproduce with `python3 scripts/generate_library.py`.','','|Title|Composer|Difficulty|Licence|Source|','|---|---|---|---|---|']
for p in CAT:lines.append(f"|{p['title']}|Piano Path|{p['difficulty']}|CC0-1.0|[generator](../scripts/generate_library.py)|")
(ROOT/'docs/CATALOG.md').write_text('\n'.join(lines)+'\n')
print(f'Generated {len(CAT)} original studies')
