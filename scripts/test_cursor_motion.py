#!/usr/bin/env python3
"""Real renderer: empty measure geometry and RAF motion without new note packets."""
import json
from playwright.sync_api import sync_playwright
from test_scrolling import open_score

def run(browser):
    page=open_score(browser,1000,400)
    page.evaluate('''() => {
      entries=entries.filter(e=>e.measure!==3 && !(e.measure===2&&e.staff===1));
      indexHitGroups();
      window.cursorRows=beatIndex.get(1).map(m=>m.measure);
      progress(0,[],[],0,64,.7,true,[],[],180,4,false,60,'BOTH',true,false,true,64,true,'#315f72',true,1);
    }''')
    assert 2 in page.evaluate('cursorRows') and 3 in page.evaluate('cursorRows'), 'Empty measures must have cursor geometry'
    for beat in (4,8):
        left=page.evaluate('b=>atBeatPosition(b+.15).x',beat)
        right=page.evaluate('b=>atBeatPosition(b+3.5).x',beat)
        assert right>left+5,(beat,left,right)
    samples=page.evaluate('''async() => {
      const samples=[];
      for(let i=0;i<75;i++){
        await new Promise(requestAnimationFrame);
        samples.push({beat:lastCursorBeat,x:parseFloat(document.getElementById('playhead').style.left)});
      }
      return samples;
    }''')
    assert samples[-1]['beat']-samples[0]['beat']>.6, 'Cursor must advance without note/native packets'
    assert len({round(s['x'],1) for s in samples})>25, 'Motion must be rendered between notes'
    assert all(b['beat']>=a['beat'] for a,b in zip(samples,samples[1:])), 'Motion must remain forward'
    page.evaluate("progress(2,[],[],0,64,.7,true,[],[],180,4,false,60,'BOTH',true,false,true,64,false,'#315f72',true,0)")
    before=page.locator('#playhead').get_attribute('style')
    page.wait_for_timeout(250)
    assert before==page.locator('#playhead').get_attribute('style'),'Paused motion must freeze'
    assert page.locator('#playhead').evaluate("e=>getComputedStyle(e,'::after').borderRadius")=='50%'
    page.close()

if __name__=='__main__':
    with sync_playwright() as p:
        browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
        run(browser);browser.close()
    print(json.dumps({'result':'passed','checks':['empty staff and measure geometry','frame motion without note packets','pause','circular pin']}))
