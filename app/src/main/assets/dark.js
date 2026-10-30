(function () {
  'use strict';
  var D = document, W = window;
  if (!D.documentElement) return 'no-doc';

  /* ---------- 1) إصلاح العرض: منع تمدد الصفحة أفقياً ---------- */
  var m = D.querySelector('meta[name=viewport]');
  var head = D.head || D.documentElement;
  if (!m) { m = D.createElement('meta'); m.name = 'viewport'; head.appendChild(m); }
  m.setAttribute('content', 'width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover');

  /* ---------- 2) الثيم الداكن (قلب الألوان + لمسات رسومية) ---------- */
  var INV = 'invert(.92) hue-rotate(180deg)';
  var UNDO = 'invert(1) hue-rotate(180deg)';
  var css = [
    'html{filter:' + INV + '!important;max-width:100vw!important;overflow-x:hidden!important;',
    ' background:radial-gradient(520px 380px at 88% -4%,rgba(20,184,166,.40),transparent 70%),',
    ' radial-gradient(480px 420px at -6% 105%,rgba(99,102,241,.32),transparent 70%),',
    ' linear-gradient(180deg,#e6eef9,#d8e4f5) fixed #dce7f6!important}',
    'body{background:transparent!important;max-width:100vw!important;overflow-x:hidden!important;min-height:100vh}',
    'img,video,canvas,picture,iframe{filter:' + UNDO + '}',
    '::selection{background:#14b8a6;color:#fff}',
    '.sk-scroll{overflow-x:auto!important;max-width:100%;-webkit-overflow-scrolling:touch}',
    '.sk-scroll::-webkit-scrollbar{height:4px}',
    '[data-sk=card]{box-shadow:inset 0 0 0 999px rgba(120,140,175,.10),0 0 0 1px rgba(100,130,170,.35),0 14px 34px rgba(60,90,130,.28)!important;',
    ' border-color:rgba(100,130,170,.35)!important;',
    ' background-image:linear-gradient(90deg,#14b8a6,#6366f1)!important;background-repeat:no-repeat!important;',
    ' background-size:100% 3px!important;background-position:top!important}',
    '[data-sk=btn]{filter:' + UNDO + ';box-shadow:0 6px 18px rgba(20,184,166,.38)}',
    'table{border-collapse:separate;border-spacing:0}',
    'a{transition:opacity .15s}'
  ].join('');
  var st = D.getElementById('skar-dark-css');
  if (!st) {
    st = D.createElement('style'); st.id = 'skar-dark-css'; head.appendChild(st);
  }
  st.textContent = css;

  /* ---------- 3) إبقاء الصفحة بدون تمرير أفقي ---------- */
  if (!W.__skarScrollLock) {
    W.__skarScrollLock = true;
    W.addEventListener('scroll', function () {
      if (W.pageXOffset !== 0) W.scrollTo(0, W.pageYOffset);
    }, { passive: true });
  }

  /* ---------- 4) تصنيف العناصر (بطاقات / أزرار) + لفّ الجداول ---------- */
  function rgb(s) {
    var r = s && s.match(/rgba?\(([^)]+)\)/);
    if (!r) return null;
    var p = r[1].split(',').map(parseFloat);
    return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
  }
  function lum(c) { return (0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b) / 255; }
  function sat(c) {
    var mx = Math.max(c.r, c.g, c.b), mn = Math.min(c.r, c.g, c.b);
    return mx === 0 ? 0 : (mx - mn) / mx;
  }
  var SKIP = /^(HTML|BODY|HEAD|SCRIPT|STYLE|META|LINK|SVG|PATH|IMG|BR|TR|TD|TH|THEAD|TBODY|TFOOT|OPTION|CANVAS|VIDEO)$/;
  var CLICKABLE = /^(BUTTON|A|INPUT|LABEL)$/;

  function classify(el) {
    if (el.nodeType !== 1 || SKIP.test(el.tagName)) return;
    var cs = W.getComputedStyle(el);
    var bg = rgb(cs.backgroundColor), fg = rgb(cs.color), kind = null;
    if (bg && bg.a > 0.9) {
      if (bg.r > 249 && bg.g > 249 && bg.b > 249 &&
          parseFloat(cs.borderTopLeftRadius) >= 8 && el.offsetWidth > 140 &&
          !CLICKABLE.test(el.tagName) && !/^(SELECT|TEXTAREA|SPAN)$/.test(el.tagName)) {
        kind = 'card';
      } else if (sat(bg) > 0.35 && lum(bg) < 0.45 && fg && lum(fg) > 0.85 &&
                 (CLICKABLE.test(el.tagName) || cs.cursor === 'pointer')) {
        kind = 'btn';
      }
    }
    if (kind) { if (el.getAttribute('data-sk') !== kind) el.setAttribute('data-sk', kind); }
    else if (el.hasAttribute('data-sk')) el.removeAttribute('data-sk');
  }

  function wrapTables() {
    var ts = D.querySelectorAll('table');
    for (var i = 0; i < ts.length; i++) {
      var t = ts[i], p = t.parentNode;
      if (!p || (p.classList && p.classList.contains('sk-scroll'))) continue;
      var w = D.createElement('div');
      w.className = 'sk-scroll';
      p.insertBefore(w, t);
      w.appendChild(t);
    }
  }

  function scan() {
    try {
      wrapTables();
      var all = D.body ? D.body.querySelectorAll('*') : [];
      for (var i = 0; i < all.length; i++) classify(all[i]);
    } catch (e) { /* لا نكسر الصفحة أبداً */ }
  }

  scan();
  if (!W.__skarObs && D.body && W.MutationObserver) {
    var timer = null;
    W.__skarObs = new MutationObserver(function () {
      clearTimeout(timer);
      timer = setTimeout(scan, 120);
    });
    W.__skarObs.observe(D.body, {
      childList: true, subtree: true,
      attributes: true, attributeFilter: ['class', 'style', 'hidden']
    });
  }
  return 'ok';
})();
