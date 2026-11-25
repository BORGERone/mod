/* Bottom Gap — content script.
 *
 * Три независимых режима, каждый включается по сайту из попапа:
 *
 * 1) «документ» (gaps) — padding-bottom на <html>.
 *    Добавляет 20px В КОНЦЕ документа. Честное предупреждение: место
 *    закрашивается фоном самой страницы, поэтому на однотонных сайтах
 *    визуально это ничем не отличается от «страница просто кончилась».
 *    Заметно там, где у подвала/фона есть граница или свой цвет.
 *
 * 2) «полоса» (strip) — вместо padding'а в конец <body> вставляется
 *    распорный блок нужной высоты, закрашенный полупрозрачной плашкой.
 *    Это тот же отступ, но его ВИДНО на любом сайте: страница явно
 *    заканчивается на 20px выше. Включается отдельно, потому что это
 *    уже вмешательство в вид страницы, а не в её высоту.
 *
 * 3) «панели» (lift) — ищет position: fixed у нижней кромки окна
 *    (плееры, док-бары) и поднимает их: bottom += px. Единственный
 *    режим, который двигает фиксированные элементы — padding документа
 *    на них не действует, они считаются от вьюпорта.
 *
 * Хранилище:
 *   { gaps: { "site.com": 20 }, strip: { "site.com": true }, lift: { "site.com": true } }
 */

const KEY_GAPS = 'gaps';
const KEY_STRIP = 'strip';
const KEY_LIFT = 'lift';

const STRIP_ID = 'zbg-gap-strip';
const STRIP_COLOR = 'rgba(127, 127, 127, 0.18)';

let appliedPx = 0;
let liftEnabled = false;

/* el -> прежнее инлайновое значение bottom (чтобы вернуть как было) */
const lifted = new Map();

let scanTimers = [];
let observer = null;

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
    // body ещё не создан — повторим после разбора документа
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

  // Уже поднятым панелям просто обновляем величину: значение могло
  // измениться в попапе, а заново их никто не поднимет (они в lifted).
  for (const el of lifted.keys()) {
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

function clearScans() {
  for (const t of scanTimers) clearTimeout(t);
  scanTimers = [];
  if (observer) {
    observer.disconnect();
    observer = null;
  }
}

/* Панель часто дорисовывается скриптами сайта уже после загрузки,
 * поэтому сканируем несколько раз + следим за DOM. */
function scheduleScans(px) {
  clearScans();
  for (const delay of [0, 400, 1200, 3000]) {
    scanTimers.push(setTimeout(() => liftBars(px), delay));
  }

  if (!document.body) return;
  let pending = false;
  observer = new MutationObserver(() => {
    if (pending) return;
    pending = true;
    setTimeout(() => {
      pending = false;
      liftBars(px);
    }, 300);
  });
  observer.observe(document.body, { childList: true, subtree: true });
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
      scheduleScans(appliedPx);
    } else {
      clearScans();
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
  if (liftEnabled) liftBars(appliedPx);
});

/* Попап спрашивает состояние: есть ли скрипт на вкладке и сколько
 * панелей реально поднято. */
browser.runtime.onMessage.addListener((msg) => {
  if (msg && msg.type === 'ping') {
    return Promise.resolve({ px: appliedPx, lift: liftEnabled, lifted: lifted.size });
  }
  return undefined;
});
