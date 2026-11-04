#!/usr/bin/env python3
"""Real OSMD scroll regression tests. No server, downloads, or piano are required.

Run `python3 scripts/test_scrolling.py`; `--baseline` prints the old failure trace.
The original CC0 fixture deliberately combines high slurs, ties and register changes
with a score area as short as a phone's landscape reader.
"""
from pathlib import Path
import argparse
import json
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/notation"
XML = (ROOT / "scripts/fixtures/scrolling.musicxml").read_text()


def open_score(browser, width=900, height=200):
    page = browser.new_page(viewport={"width": width, "height": height})
    page.add_init_script("window.Android={score:()=>" + json.dumps(XML) +
                         ",ready:()=>window.androidReady=true,seek:b=>window.seekResult=b,setLoop:(a,b)=>window.loopResult=[a,b],error:e=>window.androidError=e}")
    page.route("http://piano.test/**", lambda route: route.fulfill(
        path=str(ASSETS / route.request.url.split("/")[-1])))
    page.goto("http://piano.test/index.html")
    page.wait_for_function("window.androidReady || window.androidError", timeout=60000)
    assert not page.evaluate("window.androidError"), page.evaluate("window.androidError")
    assert page.locator(".vf-stavetie path").count() > 0, "Fixture must render actual ties"
    return page


def progress(page, beat, zoom=1, horizontal=True, follow=True, seek=False):
    page.evaluate("args=>progress(...args)",
                  [beat, [], [], 0, 64, zoom, horizontal, [], [], 180, 4, False, 80, "BOTH", True, True, True, 64, follow or seek])
    return snapshot(page, beat)


def snapshot(page, beat):
    return page.evaluate("""beat => {
      const loc=locateBeat(beat,1), line=document.getElementById('playhead');
      const system=e=>e.system || e.g.parentVoiceEntry.parentStaffEntry.parentMeasure.parentMusicSystem;
      const systems=[...new Set(entries.map(system))], r=line.getBoundingClientRect();
      return {beat,x:scrollRoot.scrollLeft,y:scrollRoot.scrollTop,left:r.left,right:r.right,top:r.top,height:r.height,
        documentTop:parseFloat(line.style.top),width:innerWidth,viewportHeight:innerHeight,
        system:systems.indexOf(system(loc.e)),systems:systems.length,
        maxX:scrollRoot.scrollWidth-scrollRoot.clientWidth,
        maxY:scrollRoot.scrollHeight-scrollRoot.clientHeight};
    }""", beat)


def assert_cursor_visible(s):
    assert -2 <= s["left"] <= s["width"] - 1, ("Cursor escaped horizontally", s)
    assert s["top"] < s["viewportHeight"] and s["top"] + s["height"] > 0, (
        "Cursor escaped vertically", s)


def run_horizontal(browser):
    for zoom in (.7, 1, 1.3, 1.6, 2):
        page = open_score(browser)
        initial = progress(page, 0, zoom)
        previous = initial
        for step in range(1, 256):
            state = progress(page, step / 4, zoom)
            assert abs(state["y"] - initial["y"]) <= 1, (
                "Horizontal reading must not chase changing note or slur heights", zoom, initial, state)
            assert abs(state["documentTop"] - initial["documentTop"]) <= .1, (
                "Cursor top must stay fixed for one musical system", zoom, initial, state)
            assert abs(state["height"] - initial["height"]) <= .1, (
                "Cursor height must stay fixed for one musical system", zoom, initial, state)
            assert state["x"] >= previous["x"] - 1, ("Forward playback scrolled backward", previous, state)
            assert_cursor_visible(state)
            previous = state
        # A loop/restart may move backward and must reveal its start immediately.
        assert previous["x"] > 200, previous
        reset = progress(page, 0, zoom)
        assert reset["x"] < previous["x"], ("Loop did not return to its first bar", reset)
        assert_cursor_visible(reset)
        page.close()


def run_wrapped(browser):
    for zoom in (.85, 1.3, 2):
        page = open_score(browser, 700, 200)
        previous = progress(page, 0, zoom, horizontal=False)
        assert previous["systems"] > 1, previous
        visited = {previous["system"]}
        for step in range(1, 256):
            state = progress(page, step / 4, zoom, horizontal=False)
            if state["system"] == previous["system"]:
                assert abs(state["y"] - previous["y"]) <= 1, ("Same-system vertical shake", previous, state)
                assert abs(state["height"] - previous["height"]) <= .1, ("Changing system cursor bounds", previous, state)
                assert state["x"] >= previous["x"] - 1, ("Same-system horizontal reversal", previous, state)
            else:
                assert state["y"] >= previous["y"] - 1, ("Forward system transition scrolled upward", previous, state)
            assert_cursor_visible(state)
            visited.add(state["system"])
            previous = state
        assert len(visited) > 2, visited
        reset = progress(page, 0, zoom, horizontal=False)
        assert reset["y"] < previous["y"], ("Loop did not return to first system", reset)
        assert_cursor_visible(reset)
        page.close()


def run_manual_and_idle(browser):
    page = open_score(browser)
    progress(page, 0, zoom=1.3, follow=False)
    page.evaluate("scrollRoot.scrollTo(700,90)")
    manual = snapshot(page, 0)
    for beat in (4, 8, 12, 16):
        state = progress(page, beat, zoom=1.3, follow=False)
        assert abs(state["x"] - manual["x"]) <= 1 and abs(state["y"] - manual["y"]) <= 1, (
            "Idle/disabled follow changed the user's reading position", manual, state)
    sought = progress(page, 48, zoom=1.3, follow=False, seek=True)
    assert_cursor_visible(sought)
    # Touch panning temporarily owns the viewport, even during active playback.
    page.evaluate("""() => {
      document.dispatchEvent(new PointerEvent('pointerdown',{pointerType:'touch',bubbles:true}));
      scrollRoot.scrollTo(1100,120);
      document.dispatchEvent(new PointerEvent('pointerup',{pointerType:'touch',bubbles:true}));
    }""")
    manual = snapshot(page, 16)
    for beat in (17, 18, 19):
        state = progress(page, beat, zoom=1.3)
        assert abs(state["x"] - manual["x"]) <= 1 and abs(state["y"] - manual["y"]) <= 1, (
            "Auto-follow fought a touch gesture", manual, state)
    page.wait_for_timeout(2000)
    state = progress(page, 20, zoom=1.3)
    assert abs(state["y"] - manual["y"]) <= 1, ("Auto-follow lost the user's vertical pan", manual, state)
    assert_cursor_visible(state)
    # Wheel/trackpad gestures need the same protection as touch panning.
    page.evaluate("""() => {
      document.dispatchEvent(new WheelEvent('wheel',{deltaY:40,bubbles:true}));
      scrollRoot.scrollTo(1400,150);
    }""")
    wheel = snapshot(page, 20)
    state = progress(page, 21, zoom=1.3)
    assert abs(state["x"] - wheel["x"]) <= 1 and abs(state["y"] - wheel["y"]) <= 1, (
        "Auto-follow fought a wheel gesture", wheel, state)
    page.close()


def run_zoom_and_resize(browser):
    page = open_score(browser, 900, 280)
    progress(page, 35, follow=False)
    # Keep a specific rendered note in view while changing engraving scale.
    page.evaluate("""() => {
      const e=entries.find(e=>e.beat===32&&e.staff===1), r=e.g.getSVGGElement().getBoundingClientRect();
      scrollRoot.scrollTo(scrollRoot.scrollLeft+r.left-innerWidth*.5,scrollRoot.scrollTop+r.top-innerHeight*.5);
    }""")
    anchor_before = page.evaluate("""() => {
      const e=entries.find(e=>e.beat===32&&e.staff===1), r=e.g.getSVGGElement().getBoundingClientRect();
      return {x:r.left,y:r.top};
    }""")
    progress(page, 35, zoom=1.3, follow=False)
    anchor_after = page.evaluate("""() => {
      const e=entries.find(e=>e.beat===32&&e.staff===1), r=e.g.getSVGGElement().getBoundingClientRect();
      return {x:r.left,y:r.top};
    }""")
    assert abs(anchor_after["x"] - anchor_before["x"]) < 90, (
        "Zoom lost the visible passage", anchor_before, anchor_after)
    # Height-only changes (opening note monitor/results/keyboard) must not re-engrave.
    page.evaluate("window.testOriginalSvg=document.querySelector('#score svg')")
    page.set_viewport_size({"width": 900, "height": 220})
    page.wait_for_timeout(250)
    assert page.evaluate("testOriginalSvg===document.querySelector('#score svg')"), (
        "Height-only viewport changes unnecessarily re-render notation")
    # Width changes should render once after the resize settles, with no self-triggered loop.
    page.evaluate("() => {window.testRenders=0;const render=osmd.render.bind(osmd);osmd.render=()=>{testRenders++;return render()};}")
    page.set_viewport_size({"width": 750, "height": 220})
    page.wait_for_timeout(400)
    assert page.evaluate("testRenders") == 1, page.evaluate("testRenders")
    page.wait_for_timeout(300)
    assert page.evaluate("testRenders") == 1, "Resize/render feedback loop"
    page.close()


def run_gestures(browser):
    page = open_score(browser, 1000, 400)
    page.evaluate('progress(4,[],[],4,8,.7,true,[],[],180,4,false,80,"BOTH",true,true,true,64,false)')
    def position(beat):
        return page.evaluate('b=>{const p=atBeatPosition(b);return {x:p.x-scrollRoot.scrollLeft,y:p.y-scrollRoot.scrollTop}}', beat)
    start, target = position(4), position(6)
    page.mouse.move(start['x'], start['y']+8)
    page.mouse.down()
    page.mouse.move(target['x'], target['y']+8, steps=8)
    page.mouse.up()
    assert abs(page.evaluate('window.seekResult')-6)<.1, 'Blue cursor must commit the dragged beat'
    page.evaluate('window.seekResult=null')
    start, target = position(8), position(10)
    page.mouse.move(start['x'], start['y']+40)
    page.mouse.down()
    page.mouse.move(target['x'], target['y']+40, steps=8)
    page.mouse.up()
    result=page.evaluate('window.loopResult')
    assert result and abs(result[0]-4)<.1 and abs(result[1]-10)<.1, result
    assert page.evaluate('window.seekResult') is None, 'Loop release must not also seek the score'
    page.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", action="store_true")
    args = parser.parse_args()
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path="/usr/bin/chromium", headless=True,
                                    args=["--no-sandbox", "--disable-dev-shm-usage"])
        if args.baseline:
            page = open_score(browser)
            trace = [progress(page, beat, zoom=1) for beat in range(0, 32, 4)]
            print(json.dumps({"viewport": "900x200", "zoom": 1, "trace": trace}))
            page.close()
        else:
            run_horizontal(browser)
            run_wrapped(browser)
            run_manual_and_idle(browser)
            run_zoom_and_resize(browser)
            run_gestures(browser)
            print(json.dumps({"result": "passed", "horizontalZooms": 5, "wrappedZooms": 3,
                              "checks": ["stable system bounds", "monotonic following", "visible cursor",
                                         "loop restart", "manual pan", "idle pan", "zoom anchor",
                                         "height resize", "debounced width resize"],
                              "fixture": "original CC0 slurs/ties/register stress score"}))
        browser.close()


if __name__ == "__main__":
    main()
