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
 xml=(ASSETS/'scores/study-1-01.musicxml').read_text()
 page.add_init_script('window.Android={score:()=>'+json.dumps(xml)+',ready:()=>{window.androidReady=true},error:e=>{window.androidError=e}}')
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
 rendered=[]
 for file in sorted((ASSETS/'scores').glob('*.musicxml')):
  count=page.evaluate('async xml=>{await osmd.load(xml);osmd.render();indexNotes();return entries.length}',file.read_text())
  assert count>20,(file.name,count)
  rendered.append(file.name)
 assert not failures,failures
 print(json.dumps({'scoresRendered':len(rendered),'renderer':'OSMD 1.9.2','graphicalNotes':count,'firstNotes':values,'svg':page.locator('svg').count(),'errors':failures}))
 browser.close()
