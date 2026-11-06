#!/usr/bin/env python3
"""Exercise the real notation renderer's passage band and cursor drag."""
import json
from playwright.sync_api import sync_playwright
from test_scrolling import open_score


def run(browser):
    page = open_score(browser, 1000, 400)
    page.evaluate('''() => {
      window.seekCalls=[]; window.previewCalls=[];
      Android.seek=b=>seekCalls.push(b);
      Android.previewSeek=b=>previewCalls.push(b);
      progress(4,[],[],4,20,.7,true,[],[],180,4,false,80,"BOTH",true,true,true,64,false);
    }''')
    coverage=page.evaluate('''() => {
      const measures=hitGroups.filter(m=>m.start>=4 && m.end<=20);
      const boundaries=[];
      for(let i=0;i<measures.length-1;i++) {
        const a=measures[i],b=measures[i+1];
        if(a.svg!==b.svg || Math.abs(a.top-b.top)>1)continue;
        const x=(a.right+b.left)/2;
        const covered=[...a.svg.querySelectorAll('.loop-layer rect')].some(r=>
          +r.getAttribute('x')<=x && +r.getAttribute('x')+(+r.getAttribute('width'))>=x);
        boundaries.push({x,covered});
      }
      return boundaries;
    }''')
    assert len(coverage)>=2, coverage
    assert all(b['covered'] for b in coverage), ('Passage band has a gap at a measure boundary',coverage)

    def position(beat):
        return page.evaluate('b=>{const p=atBeatPosition(b);return {x:p.x-scrollRoot.scrollLeft,y:p.y-scrollRoot.scrollTop}}',beat)
    start,target=position(4),position(6)
    page.mouse.move(start['x'],start['y']+8)
    page.mouse.down()
    page.mouse.move(target['x'],target['y']+8,steps=8)
    assert page.locator('#playhead').is_visible(), 'Cursor must remain visible during drag'
    assert page.evaluate('previewCalls.length')>0, 'Drag must preview the keyboard before release'
    assert page.evaluate('seekCalls.length')==0, 'Drag must not commit before release'
    page.mouse.up()
    calls=page.evaluate('seekCalls')
    assert len(calls)==1 and abs(calls[0]-6)<.1, calls

    # Reflow should retain user styling and the separate performed-note payload.
    page.evaluate('''() => {
      const e=entries.find(n=>n.pitch>=0 && n.beat>=4);
      const expected=[{id:123,beat:e.beat,midi:e.pitch,staff:e.staff}];
      const take=[{id:99,pitch:e.pitch,beat:e.beat,duration:1,expectedId:123,onsetMs:0}];
      setPerformanceData([],[],take,expected);
      progress(4,performanceData.matched,performanceData.missed,4,20,.7,true,
        performanceData.take,performanceData.expected,180,4,false,80,"BOTH",true,true,true,64,true,"#a1327b",false);
    }''')
    assert page.locator('[data-take-id="99"]').count() > 0
    page.set_viewport_size({'width':850,'height':400})
    page.wait_for_timeout(700)
    state=page.evaluate('''() => ({accent:document.documentElement.style.getPropertyValue('--cursor'),
      animation:allowCursorAnimation,take:performanceData.take.map(n=>n.id),expected:performanceData.expected.map(n=>n.id)})''')
    assert state=={'accent':'#a1327b','animation':False,'take':[99],'expected':[123]}, state
    assert page.locator('[data-take-id="99"]').count() > 0, 'Performed-note overlay must survive width reflow'
    page.close()


if __name__=='__main__':
    with sync_playwright() as p:
        browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
        run(browser)
        browser.close()
    print(json.dumps({'result':'passed','checks':['contiguous passage band','visible drag preview','single seek commit']}))
