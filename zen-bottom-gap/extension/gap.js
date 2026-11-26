/* Bottom Gap — content script.
 *
 * Три независимых режима, каждый включается по сайту из попапа:
 *
 * 1) «документ» (gaps) — padding-bottom на <html>.
 *    Добавляет 20px В КОНЦЕ документа. Честное предупреждение: место
 *    закрашивается фоном самой страницы, поэтому на однотонных сайтах
 *    визуально это ничем не отличается от «страница просто кончилась».
 *
 * 2) «полоса» (strip) — вместо padding'а в конец <body> вставляется
 *    распорный блок нужной высоты, закрашенный полупрозрачной плашкой:
 *    отступ видно на любом сайте.
 *
 * 3) «панели» (lift) — ищет position: fixed у нижней кромки окна
 *    (плееры, док-бары) и поднимает их: bottom += px. Единственный
 *    режим, который двигает фиксированные элементы — padding документа
 *    на них не действует, они считаются от вьюпорта.
 *
 * Хранилище:
 *   { gaps: { "site.com": 26 }, strip: { "site.com": true }, lift: { "site.com": true } }
 */

const KEY_GAPS = 'gaps';
const KEY_STRIP = 'strip';
const KEY_LIFT = 'lift';

const STRIP_ID = 'zbg-gap-strip';
const STRIP_COLOR = 'rgba(127, 127, 127, 0.18)';

/* Как часто максимум пересканируем страницу: чаще — только вредит */
const MIN_SCAN_INTERVAL = 350;
/* Страховочный тик для SPA, которые показывают панель вообще без мутаций */
const TICK_INTERVAL = 2000;
/* Первые проходы после загрузки: вёрстка часто «доезжает» */
const BURST_DELAYS = [0, 400, 1200, 3000];

let appliedPx = 0;
let liftEnabled = false;

/* el -> прежнее инлайновое значение bottom (чтобы вернуть как было) */
const lifted = new Map();

let scanTimer = null;
let burstTimers = [];
let ticker = null;
let lastScan = 0;
let observer = null;

const onMotion = () => requestScan(appliedPx);

function withRoot(fn) {
  if (document.documentElement) {
    fn(document.documentElement);
    return;
  }
  const mo = new MutationObserver(() => {
    if (!document.documentElement) return;
    mo.disconnect();
    fn(document.documentElement);
  });
  mo.observe(document, { childList: true, subtree: true });
}

/* ---------- режим 1: отступ в конце документа ---------- */

function applyDocumentGap(el, px) {
  if (px > 0) {
    // content-box: отступ прибавляется к высоте документа. При border-box
    // и html { height: 100% } padding «съедается» высотой и его не видно.
    el.style.setProperty('box-sizing', 'content-box', 'important');
    el.style.setProperty('padding-bottom', `${px}px`, 'important');
  } else {
    el.style.removeProperty('padding-bottom');
    el.style.removeProperty('box-sizing');
  }
}

/* ---------- режим 2: видимая плашка в конце страницы ---------- */

function applyStrip(px, on) {
  const existing = document.getElementById(STRIP_ID);

  if (!on || px <= 0) {
    if (existing) existing.remove();
    return;
  }

  if (!document.body) {
    document.addEventListener('DOMContentLoaded', () => applyStrip(px, on), { once: true });
    return;
  }

  let el = existing;
  if (!el) {
    el = document.createElement('div');
    el.id = STRIP_ID;
    el.style.setProperty('display', 'block', 'important');
    el.style.setProperty('width', '100%', 'important');
    el.style.setProperty('margin', '0', 'important');
    el.style.setProperty('padding', '0', 'important');
    el.style.setProperty('border', '0', 'important');
    el.style.setProperty('flex', '0 0 auto', 'important');   // body бывает flex-контейнером
    el.style.setProperty('background-color', STRIP_COLOR, 'important');
    el.style.setProperty('pointer-events', 'none', 'important');
    document.body.appendChild(el);
  }
  el.style.setProperty('height', `${px}px`, 'important');
}

/* ---------- режим 3: подъём фиксированных панелей ---------- */

function liftBars(px) {
  const vh = window.innerHeight;
  if (!vh || !document.body || px <= 0) return;

  // Уже поднятые: обновляем величину (могли поменять в попапе) и заодно
  // выкидываем те, что сайт успел удалить из DOM.
  for (const el of [...lifted.keys()]) {
    if (el.isConnected === false) {
      lifted.delete(el);
      continue;
    }
    el.style.setProperty('bottom', `${px}px`, 'important');
  }

  // Сначала только геометрия (без записи в DOM, чтобы не дёргать layout),
  // потом у кандидатов проверяем computed style.
  const candidates = [];
  for (const el of document.body.querySelectorAll('*')) {
    if (lifted.has(el)) continue;
    const r = el.getBoundingClientRect();
    if (r.width === 0 || r.height === 0) continue;
    if (r.height > vh * 0.5) continue;          // полноэкранные оверлеи не трогаем
    if (Math.abs(r.bottom - vh) > 4) continue;  // не приклеен к нижней кромке
    candidates.push(el);
  }

  for (const el of candidates) {
    const cs = getComputedStyle(el);
    if (cs.position !== 'fixed') continue;  // sticky/absolute сюда не входят
    if (cs.bottom === 'auto') continue;     // приклеен не через bottom — не трогаем
    lifted.set(el, el.style.getPropertyValue('bottom'));
    el.style.setProperty('bottom', `${px}px`, 'important');
  }
}

function unliftAll() {
  for (const [el, prev] of lifted) {
    if (prev) el.style.setProperty('bottom', prev);
    else el.style.removeProperty('bottom');
  }
  lifted.clear();
}

/* ---------- когда пересканировать ---------- */

function requestScan(px) {
  if (scanTimer !== null) return;  // проход уже запланирован
  const wait = Math.max(0, MIN_SCAN_INTERVAL - (Date.now() - lastScan));
  scanTimer = setTimeout(() => {
    scanTimer = null;
    lastScan = Date.now();
    if (document.visibilityState === 'hidden') return;
    if (!liftEnabled) return;
    liftBars(px);
  }, wait);
}

function startWatching(px) {
  stopWatching();
  if (!document.body) {
    document.addEventListener('DOMContentLoaded', () => startWatching(px), { once: true });
    return;
  }

  // childList — панель добавили в DOM.
  // attributes — панель уже была в DOM скрытой и стала видимой/фиксированной
  // (именно так ведёт себя плеер Newgrounds: класс меняется при старте трека).
  observer = new MutationObserver(() => requestScan(px));
  observer.observe(document.body, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['class', 'style', 'hidden'],
  });

  // Панель, выехавшая анимацией, «встаёт» только к её концу.
  document.addEventListener('transitionend', onMotion, true);
  document.addEventListener('animationend', onMotion, true);

  // Страховка: часть SPA показывает панель вообще без мутаций в body
  // (например, меняя только размеры контейнера).
  ticker = setInterval(() => requestScan(px), TICK_INTERVAL);

  for (const delay of BURST_DELAYS) {
    burstTimers.push(setTimeout(() => requestScan(px), delay));
  }
  requestScan(px);
}

function stopWatching() {
  if (scanTimer !== null) {
    clearTimeout(scanTimer);
    scanTimer = null;
  }
  for (const t of burstTimers) clearTimeout(t);
  burstTimers = [];
  if (ticker !== null) { clearInterval(ticker); ticker = null; }
  if (observer) { observer.disconnect(); observer = null; }
  document.removeEventListener('transitionend', onMotion, true);
  document.removeEventListener('animationend', onMotion, true);
}

/* ---------- состояние ---------- */

function syncFromStorage() {
  return browser.storage.local.get([KEY_GAPS, KEY_STRIP, KEY_LIFT]).then((data) => {
    const gaps = data[KEY_GAPS] || {};
    const strip = data[KEY_STRIP] || {};
    const lift = data[KEY_LIFT] || {};
    const host = location.hostname;

    appliedPx = Number(gaps[host]) || 0;
    const useStrip = Boolean(strip[host]) && appliedPx > 0;
    liftEnabled = Boolean(lift[host]) && appliedPx > 0;

    // Полоса сама создаёт место нужной высоты, поэтому padding в этом
    // случае не нужен — иначе отступ был бы двойным.
    withRoot((el) => applyDocumentGap(el, useStrip ? 0 : appliedPx));
    applyStrip(appliedPx, useStrip);

    if (liftEnabled) {
      startWatching(appliedPx);
    } else {
      stopWatching();
      unliftAll();
    }
  });
}

syncFromStorage();

/* Тумблеры в попапе меняют storage — страница подхватывает это сама. */
browser.storage.onChanged.addListener((changes, area) => {
  if (area !== 'local') return;
  if (!changes[KEY_GAPS] && !changes[KEY_STRIP] && !changes[KEY_LIFT]) return;
  syncFromStorage();
});

window.addEventListener('resize', () => {
  if (liftEnabled) requestScan(appliedPx);
});

/* Попап спрашивает состояние: есть ли скрипт на вкладке и сколько
 * панелей реально поднято. */
browser.runtime.onMessage.addListener((msg) => {
  if (msg && msg.type === 'ping') {
    return Promise.resolve({ px: appliedPx, lift: liftEnabled, lifted: lifted.size });
  }
  return undefined;
});
