#!/usr/bin/env python3
"""Exercise the actual vendored OSMD renderer in Chromium without external requests."""
from pathlib import Path
import json
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
 page=browser.new_page(viewport={'width':1000,'height':700})
 failures=[]
 page.on('pageerror',lambda err: failures.append(str(err)))
 page.on('console',lambda msg: failures.append(msg.text) if msg.type=='warning' else None)
 # A test-only inline fixture, not bundled in the Android APK.
 head='''<?xml version="1.0" encoding="utf-8"?><score-partwise version="4.0">
 <work><work-title>Renderer test</work-title></work><part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list><part id="P1">'''
 measures=[]
 for number in range(1,5):
  attrs='''<attributes><divisions>4</divisions><key><fifths>0</fifths></key><time><beats>4</beats><beat-type>4</beat-type></time><staves>2</staves><clef number="1"><sign>G</sign><line>2</line></clef><clef number="2"><sign>F</sign><line>4</line></clef></attributes>''' if number==1 else ''
  def notes(octave,staff):
   return ''.join(f'<note><pitch><step>{step}</step><octave>{octave}</octave></pitch><duration>4</duration><type>quarter</type><voice>{staff}</voice><staff>{staff}</staff></note>' for step in ('C','D','E','F'))
  measures.append(f'<measure number="{number}">{attrs}{notes(4,1)}<backup><duration>16</duration></backup>{notes(3,2)}</measure>')
 xml=head+''.join(measures)+'</part></score-partwise>'
 page.add_init_script('window.Android={score:()=>'+json.dumps(xml)+',ready:()=>{window.androidReady=true},seek:beat=>{window.seekedBeat=beat},error:e=>{window.androidError=e}}')
 page.route('http://piano.test/**',lambda route: route.fulfill(path=str(ASSETS/'notation'/route.request.url.split('/')[-1])))
 page.goto('http://piano.test/index.html')
 page.wait_for_function('window.androidReady || window.androidError',timeout=60000)
 assert not page.evaluate('window.androidError'),page.evaluate('window.androidError')
 assert page.locator('svg').count()>0
 count=page.evaluate('entries.length')
 assert count>20,count
 assert page.evaluate('[...new Set(entries.map(e=>e.staff))]')==[1,2]
 values=page.evaluate('entries.filter(e=>e.pitch>=0).slice(0,8).map(e=>({beat:e.beat,pitch:e.pitch}))')
 page.evaluate('progress(2,[[0,60]],[],0,8,1,false)')
 page.evaluate('progress(0,[],[],0,8,0.85,true)')
 page.evaluate('progress(4,[[0,60]],[[1,62]],0,8,1,false)')
 page.wait_for_timeout(300)
 page.evaluate('progress(4,[[0,60]],[[1,62]],0,8,1,false)')
 assert page.evaluate('lastBeat')==4
 assert page.locator('#playhead').count()==1
 assert page.locator('svg [fill="#16836d"]').count()>0,'Matched notes must turn green'
 # Exercise actual-pitch overlays, early/late positioning, a continuous cursor, held durations and dark horizontal layout.
 expected=[dict(id=0,beat=0,midi=60,staff=1)]
 take=[dict(id=0,pitch=60,beat=.2,duration=1.2,expectedId=0,onsetMs=200),dict(id=1,pitch=66,beat=1.5,duration=.4,expectedId=None,onsetMs=None)]
 page.evaluate('(args)=>progress(...args)',[1.5,[[0,60]],[[1,62]],0,8,1,False,take,expected,180,4,False,60])
 assert page.locator('[data-take-id]').count()==2
 assert page.locator('[data-hold-id]').count()>=2
 x1=page.locator('#playhead').evaluate('(e)=>parseFloat(e.style.left)')
 page.evaluate('(args)=>progress(...args)',[1.7,[[0,60]],[[1,62]],0,8,1,False,take,expected,180,4,False,60])
 assert page.locator('#playhead').evaluate('(e)=>parseFloat(e.style.left)')>x1,'Cursor must move between notes'
 page.screenshot(path=str(ROOT/'docs/notation-preview.png'),full_page=True)
 page.evaluate('(args)=>progress(...args)',[2,[],[],0,8,.85,True,take,expected,180,0,True,60])
 assert page.evaluate('osmd.EngravingRules.PageLeftMargin')==0
 assert page.evaluate('getComputedStyle(document.body).backgroundColor')=='rgb(20, 36, 33)'
 assert page.evaluate('getComputedStyle(document.documentElement).backgroundColor')=='rgb(20, 36, 33)'
 assert page.locator('[data-take-id]').count()==2
 page.screenshot(path=str(ROOT/'docs/notation-dark-horizontal.png'),full_page=True)
 assert not failures,failures
 # Touching another measure must post the estimated musical beat to Android.seek.
 page.evaluate('''() => {
   const e=entries.find(n=>n.beat>=6&&n.staff===1),svg=e.svg,rect=svg.getBoundingClientRect();
   const scale=rect.width/svg.viewBox.baseVal.width;
   document.getElementById('score').dispatchEvent(new MouseEvent('click',{bubbles:true,
     clientX:rect.left+e.x*scale,clientY:rect.top+e.y*scale}));
 }''')
 assert page.evaluate('window.seekedBeat')>=5.5,'Score tap must seek to the touched beat'
 assert page.locator('#playhead').get_attribute('aria-label').startswith('Score cursor')
 assert page.locator('#playhead').evaluate('(el)=>getComputedStyle(el).pointerEvents')=='auto'
 assert not failures,failures
 print(json.dumps({'testFixture':'four measures with both staves','renderer':'OSMD 1.9.2','graphicalNotes':count,'firstNotes':values,'svg':page.locator('svg').count(),'seekBeat':page.evaluate('window.seekedBeat'),'errors':failures}))
 browser.close()
