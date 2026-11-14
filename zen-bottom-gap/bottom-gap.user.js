// ==UserScript==
// @name         Bottom Gap (по сайтам)
// @namespace    local.bottom-gap
// @version      2.0.0
// @description  Добавляет пустое место в конце страницы — только на сайтах из @match.
// @match        *://example.com/*
// @match        *://*.example.com/*
// @run-at       document-start
// @grant        none
// @noframes
// ==/UserScript==

// Вариант для Violentmonkey / Tampermonkey.
// Список сайтов — строки @match в шапке: одна строка на сайт.
//   *://site.com/*        — только сам домен
//   *://*.site.com/*      — домен и все поддомены
// Размер — GAP_PX ниже (или сделайте отдельный скрипт под сайт с другим числом).

const GAP_PX = 20;

// Инлайновый стиль на <html> через CSSOM: CSP страницы не мешает,
// и это самый сильный уровень — чужие правила без !important проигрывают.
// Отступ применять можно в любой момент: он добавляет место только
// в конце документа, поэтому «дёрганья» вёрстки не видно.
function applyGap() {
  const el = document.documentElement;
  if (!el) return;
  el.style.setProperty('box-sizing', 'content-box', 'important');
  el.style.setProperty('padding-bottom', GAP_PX + 'px', 'important');
}

if (document.documentElement) {
  applyGap();
} else {
  const mo = new MutationObserver(() => {
    if (!document.documentElement) return;
    mo.disconnect();
    applyGap();
  });
  mo.observe(document, { childList: true, subtree: true });
}
