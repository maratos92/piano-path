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
 values=page.evaluate('entries.filter(e=>e.pitch>=0).slice(0,8).map(e=>({beat:e.beat,pitch:e.pitch}))')
 page.evaluate('progress(2,[[0,60]],[],0,8,1,false)')
 page.evaluate('progress(0,[],[],0,8,0.85,true)')
 page.evaluate('progress(4,[[0,60]],[[1,62]],0,8,1,false)')
 page.wait_for_timeout(300)
 page.evaluate('progress(4,[[0,60]],[[1,62]],0,8,1,false)')
 assert abs(page.evaluate('osmd.cursor.Iterator.currentTimeStamp.RealValue*4')-4)<0.001
 assert page.locator('svg [fill="#16836d"]').count()>0,'Matched notes must turn green'
 page.screenshot(path=str(ROOT/'docs/notation-preview.png'),full_page=True)
 assert not failures,failures
 rendered=[]
 for file in sorted((ASSETS/'scores').glob('*.musicxml')):
  count=page.evaluate('async xml=>{await osmd.load(xml);osmd.render();indexNotes();return entries.length}',file.read_text())
  assert count>20,(file.name,count)
  rendered.append(file.name)
 assert not failures,failures
 print(json.dumps({'scoresRendered':len(rendered),'renderer':'OSMD 1.9.2','graphicalNotes':count,'firstNotes':values,'svg':page.locator('svg').count(),'errors':failures}))
 browser.close()
