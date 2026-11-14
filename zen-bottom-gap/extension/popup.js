/* Bottom Gap — попап.
 *
 * Только читает/пишет storage. Content script сам слушает
 * storage.onChanged и перерисовывается мгновенно — без перезагрузки.
 */

const KEY_GAPS = 'gaps';
const KEY_LIFT = 'lift';
const DEFAULT_PX = 20;

const hostEl = document.getElementById('host');
const toggleEl = document.getElementById('toggle');
const pxEl = document.getElementById('px');
const rowEl = document.getElementById('row');
const liftEl = document.getElementById('lift');
const liftRowEl = document.getElementById('liftRow');
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

async function loadAll() {
  const data = await browser.storage.local.get([KEY_GAPS, KEY_LIFT]);
  return { gaps: data[KEY_GAPS] || {}, lift: data[KEY_LIFT] || {} };
}

async function save(key, map) {
  await browser.storage.local.set({ [key]: map });
}

function setDocState(on) {
  rowEl.dataset.off = on ? '0' : '1';
  pxEl.disabled = !on;
  liftRowEl.dataset.off = on ? '0' : '1';
}

function setNotice(text) {
  noticeEl.textContent = text || '';
}

async function init() {
  const [tab] = await browser.tabs.query({ active: true, currentWindow: true });
  host = hostOf(tab && tab.url);

  if (!host) {
    hostEl.textContent = 'эта страница';
    setNotice('Работает только на http/https.');
    toggleEl.disabled = true;
    setDocState(false);
    return;
  }

  hostEl.textContent = host;

  const { gaps, lift } = await loadAll();
  const current = gaps[host];
  const on = typeof current === 'number';

  toggleEl.checked = on;
  pxEl.value = on ? current : DEFAULT_PX;
  liftEl.checked = on && Boolean(lift[host]);
  setDocState(on);

  // Вкладка могла быть открыта до установки расширения — тогда
  // content script в ней не запускался.
  try {
    const state = await browser.tabs.sendMessage(tab.id, { type: 'ping' });
    if (state && state.lift) {
      setNotice(
        state.lifted > 0
          ? `Поднято панелей: ${state.lifted}.`
          : 'Панели с position: fixed внизу не найдены — на этом сайте хватит отступа документа.'
      );
    } else {
      setNotice('');
    }
  } catch {
    setNotice('Перезагрузите вкладку, чтобы отступ появился (F5).');
  }
}

toggleEl.addEventListener('change', async () => {
  if (!host) return;
  setDocState(toggleEl.checked);
  const { gaps, lift } = await loadAll();

  if (toggleEl.checked) {
    gaps[host] = clampPx(pxEl.value);
    pxEl.value = gaps[host];
  } else {
    delete gaps[host];
    delete lift[host];       // выключили сайт — «подъём панелей» тоже не нужен
    liftEl.checked = false;
  }

  await save(KEY_GAPS, gaps);
  await save(KEY_LIFT, lift);
});

/* Пишем с небольшой задержкой: при наборе «24» не нужно дважды
 * перестраивать стили на странице. */
let inputTimer = null;
pxEl.addEventListener('input', () => {
  if (!host || !toggleEl.checked) return;
  clearTimeout(inputTimer);
  inputTimer = setTimeout(async () => {
    const { gaps } = await loadAll();
    gaps[host] = clampPx(pxEl.value);
    await save(KEY_GAPS, gaps);
  }, 150);
});

pxEl.addEventListener('change', async () => {
  if (!host || !toggleEl.checked) return;
  const { gaps } = await loadAll();
  gaps[host] = clampPx(pxEl.value);
  pxEl.value = gaps[host];
  await save(KEY_GAPS, gaps);
});

liftEl.addEventListener('change', async () => {
  if (!host || !toggleEl.checked) return;
  const { lift } = await loadAll();
  if (liftEl.checked) lift[host] = true;
  else delete lift[host];
  await save(KEY_LIFT, lift);
});

init();
