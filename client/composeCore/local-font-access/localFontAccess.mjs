let cancelPendingQuery = null;
const cjkFontFamilies = [
  'Microsoft YaHei UI',
  'Microsoft YaHei',
  'DengXian',
  'PingFang SC',
  'Hiragino Sans GB',
  'Noto Sans CJK SC',
  'Noto Sans SC',
  'Source Han Sans SC',
  'Source Han Sans CN',
  'HarmonyOS Sans SC',
  'MiSans',
  'WenQuanYi Micro Hei',
  'Droid Sans Fallback',
  'Arial Unicode MS',
  'SimHei',
  'SimSun',
];

const bytesToBase64 = (bytes) => {
  const chunkSize = 0x8000;
  let binary = '';
  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize));
  }
  return btoa(binary);
};

export const isSupported = () =>
  window.isSecureContext && typeof window.queryLocalFonts === 'function';

const stylePriority = (style) => {
  const normalizedStyle = style.toLocaleLowerCase();
  if (normalizedStyle === 'regular') return 0;
  if (normalizedStyle === 'normal') return 1;
  if (normalizedStyle.includes('medium')) return 2;
  return 3;
};

const selectPreferredFont = (fonts) => {
  for (const family of cjkFontFamilies) {
    const normalizedFamily = family.toLocaleLowerCase();
    const candidates = fonts
      .filter(
        (font) =>
          font.family.toLocaleLowerCase() === normalizedFamily ||
          font.fullName.toLocaleLowerCase() === normalizedFamily,
      )
      .sort((left, right) => stylePriority(left.style) - stylePriority(right.style));
    if (candidates.length > 0) return candidates[0];
  }
  return null;
};

const loadPreferredFontJson = async () => {
  const selectedFont = selectPreferredFont(await window.queryLocalFonts());
  if (!selectedFont) return 'null';
  const blob = await selectedFont.blob();
  const base64 = bytesToBase64(new Uint8Array(await blob.arrayBuffer()));
  return JSON.stringify({
    identity: selectedFont.postscriptName,
    base64,
  });
};

export const loadPreferredFontJsonAfterUserActivation = () =>
  new Promise((resolve, reject) => {
    const cleanup = () => {
      window.removeEventListener('pointerdown', queryFonts, true);
      window.removeEventListener('keydown', queryFonts, true);
      cancelPendingQuery = null;
    };
    const queryFonts = () => {
      cleanup();
      loadPreferredFontJson().then(resolve, reject);
    };
    cancelPendingQuery?.();
    cancelPendingQuery = cleanup;
    window.addEventListener('pointerdown', queryFonts, true);
    window.addEventListener('keydown', queryFonts, true);
  });

export const cancelLocalFontQuery = () => {
  cancelPendingQuery?.();
};
