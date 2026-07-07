export const FALLBACK_COVER = 'data:image/svg+xml,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="150" height="200" viewBox="0 0 150 200">' +
  '<rect width="150" height="200" fill="#e2e8f0"/>' +
  '<text x="75" y="100" text-anchor="middle" fill="#94a3b8" font-size="14" font-family="sans-serif">暂无封面</text>' +
  '</svg>'
);

export function handleImgError(event) {
  const target = event.currentTarget;
  if (target && target.src !== FALLBACK_COVER) {
    target.src = FALLBACK_COVER;
  }
}
