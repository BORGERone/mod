/* Bottom Gap — попап.
 *
 * Только читает/пишет storage. Content script сам слушает storage.onChanged
 * и перерисовывается мгновенно, так что перезагрузка вкладки не нужна.
 */

const KEY = 'gaps';
const DEFAULT_PX = 20;

const hostEl = document.getElementById('host');
const toggleEl = document.getElementById('toggle');
const pxEl = document.getElementById('px');
const rowEl = document.getElementById('row');
const noticeEl = document.getElementById('notice');

let host = null;

function hostOf(url) {
  try {
    const u = new URL(url);
    return (u.protocol === 'http:' || u.protocol === 'https:') ? u.hostname : null;
  } catch {
    return null;
  }
}

function clampPx(value) {
  const n = Math.round(Number(value));
  if (!Number.isFinite(n)) return DEFAULT_PX;
  return Math.min(300, Math.max(1, n));
}

async function loadGaps() {
  const data = await browser.storage.local.get(KEY);
  return data[KEY] || {};
}

async function saveGaps(gaps) {
  await browser.storage.local.set({ [KEY]: gaps });
}

function setRowState(on) {
  rowEl.dataset.off = on ? '0' : '1';
  pxEl.disabled = !on;
}

async function init() {
  const [tab] = await browser.tabs.query({ active: true, currentWindow: true });
  host = hostOf(tab && tab.url);

  if (!host) {
    hostEl.textContent = 'эта страница';
    noticeEl.textContent = 'Работает только на http/https.';
    toggleEl.disabled = true;
    setRowState(false);
    return;
  }

  hostEl.textContent = host;

  const gaps = await loadGaps();
  const current = gaps[host];
  const on = typeof current === 'number';

  toggleEl.checked = on;
  pxEl.value = on ? current : DEFAULT_PX;
  setRowState(on);

  // Вкладка могла быть открыта до установки расширения — тогда
  // content script в ней не запускался.
  try {
    await browser.tabs.sendMessage(tab.id, { type: 'ping' });
    noticeEl.textContent = '';
  } catch {
    noticeEl.textContent = 'Перезагрузите вкладку, чтобы отступ появился (F5).';
  }
}

toggleEl.addEventListener('change', async () => {
  if (!host) return;
  setRowState(toggleEl.checked);
  const gaps = await loadGaps();
  if (toggleEl.checked) {
    gaps[host] = clampPx(pxEl.value);
    pxEl.value = gaps[host];
  } else {
    delete gaps[host];
  }
  await saveGaps(gaps);
});

pxEl.addEventListener('input', async () => {
  if (!host || !toggleEl.checked) return;
  const gaps = await loadGaps();
  gaps[host] = clampPx(pxEl.value);
  await saveGaps(gaps);
});

pxEl.addEventListener('change', async () => {
  if (!host || !toggleEl.checked) return;
  const gaps = await loadGaps();
  gaps[host] = clampPx(pxEl.value);
  pxEl.value = gaps[host];
  await saveGaps(gaps);
});

init();
