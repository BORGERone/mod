/* Bottom Gap — content script.
 *
 * Запускается на каждой странице (document_start), но стиль ставит
 * только если текущий хост есть в списке включённых.
 *
 * Хранилище: { gaps: { "example.com": 20, "news.site": 32 } }
 *   ключ  — hostname (без порта и пути),
 *   значение — отступ в px. Нет ключа = выключено.
 *
 * Важно про способ инъекции: ставим инлайновый стиль на <html> через
 * CSSOM, а не вставляем <style>. Так CSP страницы не мешает, и это
 * самый сильный author-уровень (перебивает чужие правила без !important).
 * Задержка применения не страшна: padding на корне добавляет место
 * только в конце документа, поэтому «прыжка» вёрстки не видно.
 */

const KEY = 'gaps';

let appliedPx = 0;

/* На document_start documentElement может быть ещё не создан —
 * дожидаемся его появления и дальше работаем с ним. */
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

function apply(el, px) {
  appliedPx = px;
  if (px > 0) {
    // content-box: отступ прибавляется к высоте документа. Если сайт
    // ставит html { box-sizing: border-box; height: 100% }, при border-box
    // padding «съедается» высотой и его не будет видно.
    el.style.setProperty('box-sizing', 'content-box', 'important');
    el.style.setProperty('padding-bottom', `${px}px`, 'important');
  } else {
    el.style.removeProperty('padding-bottom');
    el.style.removeProperty('box-sizing');
  }
}

async function sync() {
  const data = await browser.storage.local.get(KEY);
  const gaps = data[KEY] || {};
  const px = Number(gaps[location.hostname]) || 0;
  withRoot((el) => apply(el, px));
}

sync();

/* Переключение в попапе меняет storage — страница подхватывает это
 * сама, без перезагрузки и без обмена сообщениями. */
browser.storage.onChanged.addListener((changes, area) => {
  if (area !== 'local' || !changes[KEY]) return;
  const gaps = changes[KEY].newValue || {};
  const px = Number(gaps[location.hostname]) || 0;
  withRoot((el) => apply(el, px));
});

/* Попап проверяет этим, что скрипт вообще есть на вкладке
 * (если аддон поставили при уже открытой вкладке — его там нет). */
browser.runtime.onMessage.addListener((msg) => {
  if (msg && msg.type === 'ping') return Promise.resolve({ px: appliedPx });
  return undefined;
});
