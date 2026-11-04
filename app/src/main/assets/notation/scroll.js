/* One owner for automatic score scrolling. Geometry is in document CSS pixels. */
class ScoreScroller {
  constructor() {
    this.system = null;
    this.beat = null;
    this.following = false;
    this.activeTouches = 0;
    this.pointers = new Set();
    this.pauseUntil = 0;
    const pause = () => { this.pauseUntil = performance.now() + 1800; };
    window.addEventListener('pointerdown', event => { this.pointers.add(event.pointerId); pause(); }, {passive: true, capture: true});
    for (const name of ['pointerup', 'pointercancel']) {
      window.addEventListener(name, event => { this.pointers.delete(event.pointerId); pause(); }, {passive: true, capture: true});
    }
    window.addEventListener('blur', () => { this.pointers.clear(); this.activeTouches = 0; });
    for (const name of ['touchstart', 'touchmove', 'touchend', 'touchcancel']) {
      window.addEventListener(name, event => {
        this.activeTouches = event.touches.length;
        pause();
      }, {passive: true, capture: true});
    }
    window.addEventListener('wheel', pause, {passive: true, capture: true});
    window.addEventListener('keydown', event => {
      if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'PageUp', 'PageDown', 'Home', 'End', ' '].includes(event.key)) pause();
    });
    // Also leave time for a touch fling to finish. Programmatic scrolling outside
    // a user gesture does not extend the pause or start a competing animation.
    window.addEventListener('scroll', () => {
      if (this.paused()) pause();
    }, {passive: true, capture: true});
  }

  paused() { return this.activeTouches > 0 || this.pointers.size > 0 || performance.now() < this.pauseUntil; }

  viewport() {
    const v = window.visualViewport;
    return {left: v?.pageLeft ?? scrollX, top: v?.pageTop ?? scrollY,
      width: v?.width ?? innerWidth, height: v?.height ?? innerHeight};
  }

  move(left, top) {
    const v = this.viewport(), root = document.scrollingElement;
    const x = Math.max(0, Math.min(root.scrollWidth - root.clientWidth, scrollX + left - v.left));
    const y = Math.max(0, Math.min(root.scrollHeight - root.clientHeight, scrollY + top - v.top));
    // Fractional SVG rounding must never cause repeated one-pixel corrections.
    if (Math.abs(x - scrollX) > 1 || Math.abs(y - scrollY) > 1) {
      window.scrollTo({left: x, top: y, behavior: 'instant'});
    }
  }

  follow(frame, beat, enabled) {
    if (!enabled) { this.following = false; return; }
    if (this.paused()) {
      // Transport continues during a gesture. Do not misread the elapsed beats
      // as a seek and undo the user's vertical pan when following resumes.
      this.system = frame.system; this.beat = beat; this.following = true;
      return;
    }
    const v = this.viewport();
    const entering = !this.following || this.system !== frame.system;
    const seeking = this.beat !== null && (beat < this.beat - .01 || beat > this.beat + 2);
    let left = v.left, top = v.top;
    if (frame.x < left + v.width * .12 || frame.x > left + v.width * .82) {
      left = frame.x - v.width * .28;
    }
    // Use an entire system, never the changing extrema of the current chord or
    // measure. An oversized system gets one stable top alignment; users can pan
    // to the bass staff without the next beat pulling it back to the treble.
    if (entering || seeking) {
      const padding = Math.min(16, v.height * .08);
      const height = frame.bottom - frame.top;
      if (height > v.height - 2 * padding) top = frame.top - padding;
      else if (frame.top < top + padding) top = frame.top - padding;
      else if (frame.bottom > top + v.height - padding) top = frame.bottom - v.height + padding;
    }
    this.move(left, top);
    this.system = frame.system;
    this.beat = beat;
    this.following = true;
  }

  // Preserve the reader's location across engraving zoom, theme and width changes.
  capture(entries) {
    if (!entries.length) return null;
    const v = this.viewport(), cx = v.left + v.width / 2, cy = v.top + v.height / 2;
    let best = null, distance = Infinity;
    for (const e of entries) {
      const p = scorePoint(e.svg, e.x, e.y);
      const d = Math.abs(p.x - cx) / v.width + Math.abs(p.y - cy) / v.height;
      if (d < distance) { distance = d; best = {beat: e.beat, pitch: e.pitch, staff: e.staff,
        x: (p.x - v.left) / v.width, y: (p.y - v.top) / v.height}; }
    }
    return best;
  }

  restore(anchor, entries) {
    if (!anchor) return;
    const e = entries.find(e => e.beat === anchor.beat && e.pitch === anchor.pitch && e.staff === anchor.staff);
    if (!e) return;
    const p = scorePoint(e.svg, e.x, e.y), v = this.viewport();
    this.move(p.x - anchor.x * v.width, p.y - anchor.y * v.height);
    // Systems are rebuilt by OSMD. Rebind the identity without treating an idle
    // zoom as a request to jump back to the playback cursor.
    this.system = e.system;
  }
}

function scorePoint(svg, x, y) {
  const p = svg.createSVGPoint(); p.x = x; p.y = y;
  const screen = p.matrixTransform(svg.getScreenCTM());
  return {x: screen.x + scrollX, y: screen.y + scrollY};
}
