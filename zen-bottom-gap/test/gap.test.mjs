/* Стенд для gap.js без браузера: Node + заглушки DOM.
 *
 *   node test/gap.test.mjs
 *
 * Проверяет ровно те места, где легко сломаться незаметно: подъём fixed-панелей,
 * отсев оверлеев/sticky/bottom:auto, появление панели ПОСЛЕ загрузки (как плеер
 * Newgrounds), обновление значения без перезапуска, видимую полосу и снятие
 * всех следов при выключении.
 */

import fs from 'node:fs';
import vm from 'node:vm';

const SRC = new URL('../extension/gap.js', import.meta.url);
const src = fs.readFileSync(SRC, 'utf8');

function fakeStyle() {
  const m = new Map();
  return {
    setProperty(k, v) { m.set(k, v); },
    removeProperty(k) { m.delete(k); },
    getPropertyValue(k) { return m.get(k) || ''; },
  };
}

function makeEl({ id = '', cs = {}, getRect = () => ({ top: 0, bottom: 0, height: 0, width: 0 }), all = null } = {}) {
  const children = [];
  const el = {
    id,
    style: fakeStyle(),
    children,
    parentNode: null,
    getBoundingClientRect: getRect,
    _cs: cs,
    appendChild(c) { children.push(c); c.parentNode = el; return c; },
    remove() {
      if (!el.parentNode) return;
      const i = el.parentNode.children.indexOf(el);
      if (i >= 0) el.parentNode.children.splice(i, 1);
      el.parentNode = null;
    },
    querySelectorAll: () => all || [],
    addEventListener() {},
  };
  return el;
}

const VH = 800;

// фиксированный бар у нижней кромки (должен подняться)
const bar = makeEl({
  cs: { position: 'fixed', bottom: '0px' },
  getRect: () => ({ top: 720, bottom: 800, height: 80, width: 1200 }),
});
// полноэкранный оверлей (не трогаем)
const overlay = makeEl({
  cs: { position: 'fixed', bottom: '0px' },
  getRect: () => ({ top: 0, bottom: 800, height: 800, width: 1200 }),
});
// sticky-хедер (не трогаем)
const sticky = makeEl({
  cs: { position: 'sticky', bottom: '0px' },
  getRect: () => ({ top: 720, bottom: 800, height: 80, width: 1200 }),
});
// fixed, но приклеен через top (не трогаем)
const topBar = makeEl({
  cs: { position: 'fixed', bottom: 'auto' },
  getRect: () => ({ top: 720, bottom: 800, height: 80, width: 1200 }),
});
// fixed со своим inline bottom (вернуть при выключении)
const own = makeEl({
  cs: { position: 'fixed', bottom: '10px' },
  getRect: () => ({ top: 720, bottom: 800, height: 80, width: 1200 }),
});
own.style.setProperty('bottom', '10px');

// плеер Newgrounds: элемент уже в DOM, но появляется и прилипает позже,
// когда пользователь включает музыку (меняются class и геометрия)
let lateRect = { top: 900, bottom: 980, height: 80, width: 1200 };
const lateBar = makeEl({
  cs: { position: 'fixed', bottom: '0px' },
  getRect: () => lateRect,
});

const all = [bar, overlay, sticky, topBar, own, lateBar];

const observers = [];
const body = makeEl({ all });
const root = makeEl();

const store = {
  gaps: { 'site.test': 20 },
  strip: {},
  lift: { 'site.test': true },
};
const listeners = [];

const document = {
  documentElement: root,
  body,
  visibilityState: 'visible',
  createElement: () => makeEl({ id: '' }),
  getElementById: (id) => body.children.find((c) => c.id === id) || null,
  addEventListener() {},
  removeEventListener() {},
};

const sandbox = {
  console,
  setTimeout,
  clearTimeout,
  setInterval,
  clearInterval,
  Date,
  location: { hostname: 'site.test' },
  window: { innerHeight: VH, addEventListener() {} },
  document,
  getComputedStyle: (node) => ({ ...node._cs }),
  MutationObserver: class {
    constructor(cb) { this.cb = cb; observers.push(this); }
    observe(target, opts) { this.target = target; this.opts = opts; }
    disconnect() { this.disconnected = true; }
  },
  browser: {
    storage: {
      local: {
        get: async (keys) => {
          const out = {};
          for (const k of [].concat(keys)) if (k in store) out[k] = store[k];
          return out;
        },
      },
      onChanged: { addListener: (fn) => listeners.push(fn) },
    },
    runtime: { onMessage: { addListener: (fn) => { sandbox.__msg = fn; } } },
  },
};
sandbox.globalThis = sandbox;
vm.createContext(sandbox);
vm.runInContext(src, sandbox);

const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const bottom = (e) => e.style.getPropertyValue('bottom');
const stripEl = () => document.getElementById('zbg-gap-strip');
const apply = (changes) => listeners.forEach((fn) => fn(changes, 'local'));

const fail = [];
const check = (name, got, want) => {
  const ok = JSON.stringify(got) === JSON.stringify(want);
  if (!ok) fail.push(`${name}: получили ${JSON.stringify(got)}, ждали ${JSON.stringify(want)}`);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name} = ${JSON.stringify(got)}`);
};

await wait(60);

// --- режим «документ» ---
check('padding документа', root.style.getPropertyValue('padding-bottom'), '20px');
check('box-sizing документа', root.style.getPropertyValue('box-sizing'), 'content-box');
check('плашки нет, пока выключена', stripEl(), null);

// --- режим «панели» ---
check('fixed-бар поднят', bottom(bar), '20px');
check('fullscreen-оверлей не тронут', bottom(overlay), '');
check('sticky не тронут', bottom(sticky), '');
check('bar с bottom:auto не тронут', bottom(topBar), '');
check('чужой inline bottom заменён', bottom(own), '20px');
check('в попап ушло число поднятых', (await sandbox.__msg({ type: 'ping' })).lifted, 2);
check('наблюдаем за атрибутами, не только за новыми узлами', observers.some((o) => o.opts && o.opts.attributes === true), true);
check('скрытый плеер пока не поднят', bottom(lateBar), '');

// --- включил музыку: плеер стал видимым и прилип к низу ---
lateRect = { top: 720, bottom: 800, height: 80, width: 1200 };
observers.forEach((o) => o.cb([]));
await wait(900);
check('поздно появившийся плеер поднят без перещёлкивания галки', bottom(lateBar), '20px');
check('первый бар не пострадал', bottom(bar), '20px');

// --- включаем полосу ---
store.strip = { 'site.test': true };
apply({ strip: { newValue: store.strip } });
await wait(80);
check('padding убран (нет двойного отступа)', root.style.getPropertyValue('padding-bottom'), '');
check('плашка создана', Boolean(stripEl()), true);
check('высота плашки', stripEl().style.getPropertyValue('height'), '20px');
check('плашка в конце body', body.children[body.children.length - 1].id, 'zbg-gap-strip');
check('плашка не ловит клики', stripEl().style.getPropertyValue('pointer-events'), 'none');

// --- меняем значение при включённой полосе ---
store.gaps = { 'site.test': 26 };
apply({ gaps: { newValue: store.gaps } });
await wait(80);
check('высота плашки обновилась', stripEl().style.getPropertyValue('height'), '26px');
check('панель тоже обновилась', bottom(bar), '26px');
check('поздний плеер тоже обновился', bottom(lateBar), '26px');
check('плашка не задублировалась', body.children.filter((c) => c.id === 'zbg-gap-strip').length, 1);

// --- выключаем полосу, отступ возвращается ---
store.strip = {};
apply({ strip: { newValue: {} } });
await wait(80);
check('плашка удалена', stripEl(), null);
check('padding вернулся', root.style.getPropertyValue('padding-bottom'), '26px');

// --- выключаем панели ---
store.lift = {};
apply({ lift: { newValue: {} } });
await wait(80);
check('бар отпущен', bottom(bar), '');
check('чужой bottom восстановлен', bottom(own), '10px');
check('поздний плеер отпущен', bottom(lateBar), '');
check('padding на месте', root.style.getPropertyValue('padding-bottom'), '26px');

// --- выключаем сайт целиком ---
store.gaps = {};
apply({ gaps: { newValue: {} } });
await wait(80);
check('padding снят', root.style.getPropertyValue('padding-bottom'), '');
check('box-sizing снят', root.style.getPropertyValue('box-sizing'), '');

console.log(fail.length ? `\nПРОВАЛЕНО:\n${fail.join('\n')}` : '\nВСЁ ПРОШЛО');
process.exit(fail.length ? 1 : 0);
