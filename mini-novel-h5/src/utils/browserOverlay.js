/**
 * 屏蔽「第三方注入型」视频悬浮工具条（下载 / 存网盘 / 分享 / 投屏 / 倍速）。
 *
 * 背景：H5 已经换成自定义控件播放器（页面里没有原生 controls），但部分手机浏览器
 * （UC / 夸克等）和桌面浏览器插件（网盘助手 / 下载器）会自己往页面里注入一条悬浮工具条，
 * 里面带「下载」「存网盘」等按钮。这类节点不在我们的组件里，只能按特征识别后隐藏。
 *
 * 判定条件（宁可漏杀不误杀）：
 * - 不在自家 Vant 弹层/提示里（.van-*，我们自己的 UI 一律不动）；
 * - 文本命中关键词；
 * - 且是悬浮定位（fixed/absolute/sticky）+ z-index ≥ 1000（工具条特征）。
 *
 * 注意：如果工具条是浏览器原生层渲染（不在 DOM 里），网页无法隐藏，只能换浏览器/App 访问。
 */

export const BROWSER_BAR_KEYWORDS = ['存网盘', '网盘', '下载', '分享', '投屏', '倍速'];

const SKIP_TAGS = new Set(['SCRIPT', 'STYLE', 'LINK', 'META', 'TEMPLATE', 'NOSCRIPT', 'HTML', 'HEAD', 'BODY']);

/** 纯函数：判断某个节点的样式/文本是否像第三方注入的视频工具条（便于单测）。 */
export function looksLikeBrowserVideoBar({ text, position, zIndex, protectedUi } = {}) {
  if (protectedUi) return false;
  const flat = String(text == null ? '' : text).trim().slice(0, 120);
  if (!flat) return false;
  if (!BROWSER_BAR_KEYWORDS.some((k) => flat.includes(k))) return false;
  const floating = position === 'fixed' || position === 'absolute' || position === 'sticky';
  const z = Number(zIndex);
  return floating && Number.isFinite(z) && z >= 1000;
}

function hideIfBrowserBar(el) {
  if (!el || el.nodeType !== 1) return;
  if (SKIP_TAGS.has(el.tagName)) return;
  if (el.closest && el.closest('.van-')) return; // 自家的 Vant 弹层/提示不参与判定
  let style;
  try {
    style = getComputedStyle(el);
  } catch {
    return;
  }
  if (looksLikeBrowserVideoBar({ text: el.textContent, position: style.position, zIndex: style.zIndex })) {
    el.style.setProperty('display', 'none', 'important');
  }
}

/**
 * 安装一个轻量的 MutationObserver：对 DOM 里新出现的第三方悬浮工具条做隐藏。
 * 返回卸载函数（正常情况下页面生命周期内不需要调用）。
 */
export function suppressBrowserVideoBars() {
  if (typeof window === 'undefined' || typeof document === 'undefined' || typeof MutationObserver === 'undefined') {
    return () => {};
  }

  const scanExisting = () => {
    if (!document.body) return;
    Array.from(document.body.children).forEach((el) => {
      hideIfBrowserBar(el);
      if (el.querySelectorAll) {
        el.querySelectorAll('div, section, aside, iframe').forEach(hideIfBrowserBar);
      }
    });
  };

  const observer = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      mutation.addedNodes.forEach((node) => {
        if (!node || node.nodeType !== 1) return;
        hideIfBrowserBar(node);
        if (node.querySelectorAll) {
          node.querySelectorAll('div, section, aside, iframe').forEach(hideIfBrowserBar);
        }
      });
    });
  });

  const start = () => {
    scanExisting();
    observer.observe(document.body, { childList: true, subtree: true });
    // 不少浏览器/插件是延迟注入的，再补扫两次
    setTimeout(scanExisting, 1200);
    setTimeout(scanExisting, 4000);
  };

  if (document.body) {
    start();
  } else {
    window.addEventListener('DOMContentLoaded', start, { once: true });
  }

  return () => observer.disconnect();
}
