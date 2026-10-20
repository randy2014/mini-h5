// 默认小说封面（占位图）：品牌墨绿书封 + 暖金细框 + 书页徽记。
// 与后端 CoverService.FALLBACK_SVG 保持同一份设计，保证全站（首页/书卡/详情/订阅频道）回退风格一致。
// 要求：150x200 竖版、纯 SVG（无外链字体/图片）、体积 <1KB、任意分辨率不糊。
const FALLBACK_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" width="150" height="200" viewBox="0 0 150 200">' +
  '<defs>' +
  '<linearGradient id="mnBg" x1="0" y1="0" x2="0.55" y2="1">' +
  '<stop offset="0" stop-color="#2a7b6f"/>' +
  '<stop offset="0.5" stop-color="#1d5d55"/>' +
  '<stop offset="1" stop-color="#10403b"/>' +
  '</linearGradient>' +
  '<radialGradient id="mnGlow" cx="0.5" cy="0.27" r="0.78">' +
  '<stop offset="0" stop-color="#ffffff" stop-opacity="0.15"/>' +
  '<stop offset="1" stop-color="#ffffff" stop-opacity="0"/>' +
  '</radialGradient>' +
  '</defs>' +
  '<rect width="150" height="200" fill="url(#mnBg)"/>' +
  '<rect width="150" height="200" fill="url(#mnGlow)"/>' +
  '<rect x="7" y="7" width="136" height="186" rx="3.5" fill="none" stroke="#dcc088" stroke-opacity="0.55"/>' +
  '<path d="M75 50l4.6 5.6L75 61.2l-4.6-5.6z" fill="#dcc088" fill-opacity="0.9"/>' +
  '<g fill="none" stroke="#eddeba" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round">' +
  '<path d="M75 76c-7-4.6-15.5-6.6-23.5-5.6v33c8-1 16.5 1 23.5 5.6z"/>' +
  '<path d="M75 76c7-4.6 15.5-6.6 23.5-5.6v33c-8-1-16.5 1-23.5 5.6z"/>' +
  '<path d="M75 76v33"/>' +
  '</g>' +
  '<text x="75" y="172" text-anchor="middle" font-family="Georgia,serif" font-size="7.4" letter-spacing="2.6" fill="#eddeba" fill-opacity="0.72">MINI NOVEL</text>' +
  '</svg>';

export const FALLBACK_COVER = 'data:image/svg+xml,' + encodeURIComponent(FALLBACK_SVG);

export function handleImgError(event) {
  const target = event.currentTarget;
  if (target && target.src !== FALLBACK_COVER) {
    target.src = FALLBACK_COVER;
  }
}
