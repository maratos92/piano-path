#!/usr/bin/env python3
"""Real OSMD: sustained wrong input must not rebuild score glyphs or grow SVG forever."""
import json
from playwright.sync_api import sync_playwright
from test_scrolling import open_score


def run(browser):
    page=open_score(browser,1000,400)
    result=page.evaluate('''() => {
      progress(0,[],[],0,64,.7,true,[],[],180,4,false,80,'BOTH',true,false,true,64,false,'#315f72',true,0);
      const e=entries.find(n=>n.pitch>=0);
      const expected=[{id:123,beat:e.beat,midi:e.pitch,staff:e.staff}];
      let colorCalls=0;
      for(const entry of entries){const original=entry.g.setColor.bind(entry.g);entry.g.setColor=(...a)=>{colorCalls++;return original(...a)}}
      const render=take=>{setPerformanceData(performanceData.matched,performanceData.missed,take,expected);progress(0,performanceData.matched,performanceData.missed,0,64,.7,true,performanceData.take,performanceData.expected,180,4,false,80,'BOTH',true,false,true,64,true,'#315f72',true,0)};
      const note=id=>({id,pitch:e.pitch,beat:e.beat,duration:.2,expectedId:null,onsetMs:null});
      render([note(0)]);const first=document.querySelector('[data-take-id="0"]');
      render([note(0),note(1)]);
      const retained=first===document.querySelector('[data-take-id="0"]');
      let maximum=0;
      for(let batch=0;batch<80;batch++){
        render(Array.from({length:256},(_,i)=>note(batch*256+i)));
        maximum=Math.max(maximum,document.querySelectorAll('.take-layer > *').length);
      }
      const held={...note(99999),duration:null};render([held]);
      const heldHead=document.querySelector('[data-take-id="99999"]');
      for(let i=0;i<100;i++)progress(0,performanceData.matched,performanceData.missed,0,64,.7,true,performanceData.take,performanceData.expected,180,4,false,80,'BOTH',true,false,true,64,true,'#315f72',true,0);
      const idleRetained=heldHead===document.querySelector('[data-take-id="99999"]');
      render([]);stopCursorMotion();
      return {colorCalls,retained,idleRetained,maximum,remaining:document.querySelectorAll('.take-layer > *').length,cached:takeNodes.size};
    }''')
    assert result['retained'],'A new error must not rebuild previous glyphs'
    assert result['idleRetained'],'Idle WAIT must not rebuild held-note glyphs'
    assert result['colorCalls']==0,result
    assert result['maximum']<=1024,result
    assert result['remaining']==0 and result['cached']==0,result
    page.close()
    return result

if __name__=='__main__':
    with sync_playwright() as p:
        browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
        result=run(browser);browser.close()
    print(json.dumps({'result':'passed','checks':result}))
