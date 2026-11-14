/* Bottom Gap — content script.
 *
 * Два независимых режима, оба включаются по сайту из попапа:
 *
 * 1) «документ» (storage: gaps) — padding-bottom на <html>.
 *    Добавляет место В КОНЦЕ документа. Помогает страницам с обычным
 *    потоком, но НЕ двигает position: fixed — фиксированные элементы
 *    считаются от вьюпорта, и отступ документа им безразличен.
 *
 * 2) «панели» (storage: lift) — ищет элементы с position: fixed,
 *    приклеенные к нижней кромке окна (плееры, док-бары, cookie-баннеры)
 *    и поднимает их: bottom += px. Это то, что нужно на сайтах вроде
 *    Newgrounds, где внизу висит фиксированный плеер.
 *
 * Хранилище:
 *   { gaps: { "site.com": 20 }, lift: { "site.com": true } }
 *
 * Инъекция — инлайновый стиль через CSSOM, а не <style>: CSP страницы
 * не мешает, и это самый сильный author-уровень.
 */

const KEY_GAPS = 'gaps';
const KEY_LIFT = 'lift';

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

/* ---------- режим 2: подъём фиксированных панелей ---------- */

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
  return browser.storage.local.get([KEY_GAPS, KEY_LIFT]).then((data) => {
    const gaps = data[KEY_GAPS] || {};
    const lift = data[KEY_LIFT] || {};
    const host = location.hostname;

    appliedPx = Number(gaps[host]) || 0;
    liftEnabled = Boolean(lift[host]) && appliedPx > 0;

    withRoot((el) => applyDocumentGap(el, appliedPx));

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
  if (!changes[KEY_GAPS] && !changes[KEY_LIFT]) return;
  syncFromStorage();
});

window.addEventListener('resize', () => {
  if (liftEnabled) liftBars(appliedPx);
});

/* Попап спрашивает состояние: есть ли скрипт на вкладке и сколько
 * панелей реально поднято (полезно понять, что нашлось, а что нет). */
browser.runtime.onMessage.addListener((msg) => {
  if (msg && msg.type === 'ping') {
    return Promise.resolve({ px: appliedPx, lift: liftEnabled, lifted: lifted.size });
  }
  return undefined;
});
