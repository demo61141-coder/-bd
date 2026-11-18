import { CharacterItem, Point2D, PuppetConfig } from '../types';

export const DEFAULT_PUPPET_CONFIG: PuppetConfig = {
  leftEye: { x: 0.36, y: 0.40 },
  rightEye: { x: 0.64, y: 0.40 },
  noseTip: { x: 0.50, y: 0.53 },
  mouthLeft: { x: 0.39, y: 0.67 },
  mouthRight: { x: 0.61, y: 0.67 },
  leftEyeBox: { left: 0.27, top: 0.33, right: 0.45, bottom: 0.46 },
  rightEyeBox: { left: 0.55, top: 0.33, right: 0.73, bottom: 0.46 },
  mouthBox: { left: 0.35, top: 0.60, right: 0.65, bottom: 0.75 }
};

export const CANONICAL_36_POINTS: Point2D[] = [
  { x: 0.24, y: 0.22 }, { x: 0.36, y: 0.17 }, { x: 0.50, y: 0.15 }, { x: 0.64, y: 0.17 }, { x: 0.76, y: 0.22 },
  { x: 0.27, y: 0.32 }, { x: 0.42, y: 0.31 }, { x: 0.58, y: 0.31 }, { x: 0.73, y: 0.32 },
  { x: 0.29, y: 0.39 }, { x: 0.36, y: 0.36 }, { x: 0.43, y: 0.39 }, { x: 0.36, y: 0.42 },
  { x: 0.57, y: 0.39 }, { x: 0.64, y: 0.36 }, { x: 0.71, y: 0.39 }, { x: 0.64, y: 0.42 },
  { x: 0.50, y: 0.38 }, { x: 0.50, y: 0.51 }, { x: 0.42, y: 0.54 }, { x: 0.58, y: 0.54 },
  { x: 0.18, y: 0.39 }, { x: 0.82, y: 0.39 }, { x: 0.21, y: 0.56 }, { x: 0.79, y: 0.56 },
  { x: 0.37, y: 0.65 }, { x: 0.50, y: 0.62 }, { x: 0.63, y: 0.65 }, { x: 0.50, y: 0.69 },
  { x: 0.50, y: 0.64 }, { x: 0.50, y: 0.66 },
  { x: 0.26, y: 0.72 }, { x: 0.37, y: 0.81 }, { x: 0.50, y: 0.85 }, { x: 0.63, y: 0.81 }, { x: 0.74, y: 0.72 }
];

export const MESH_TRIANGLES: number[] = [
  0, 1, 5, 1, 6, 5, 1, 2, 6, 2, 7, 6, 2, 3, 7, 3, 8, 7, 3, 4, 8,
  0, 5, 21, 4, 22, 8,
  5, 10, 9, 5, 6, 10, 6, 11, 10, 6, 17, 11, 6, 7, 17, 7, 13, 17, 7, 14, 13, 7, 8, 14, 8, 15, 14,
  9, 10, 12, 10, 11, 12, 13, 14, 16, 14, 15, 16,
  21, 5, 9, 21, 9, 23, 8, 22, 15, 15, 22, 24,
  9, 12, 23, 12, 19, 23, 12, 11, 19, 11, 17, 18, 11, 18, 19, 17, 13, 18, 13, 20, 18, 13, 16, 20, 16, 24, 20, 16, 15, 24,
  19, 18, 26, 18, 20, 26, 19, 26, 25, 20, 27, 26,
  25, 26, 29, 26, 27, 29, 25, 30, 28, 30, 27, 28,
  23, 19, 25, 23, 25, 31, 20, 24, 27, 27, 24, 35,
  31, 25, 32, 25, 28, 32, 28, 33, 32, 28, 34, 33, 28, 27, 34, 27, 35, 34
];

function generateSvgPortraitDataUrl(
  skinColor: string,
  accentColor: string,
  irisColor: string,
  kind: 'HUMAN' | 'CARTOON' | 'ANIMAL',
  variant: string
): string {
  const ears =
    kind === 'ANIMAL'
      ? `<polygon points="85,175 60,45 190,105" fill="${accentColor}" /><polygon points="427,175 452,45 322,105" fill="${accentColor}" />`
      : kind === 'CARTOON'
      ? `<circle cx="95" cy="240" r="32" fill="${accentColor}"/><circle cx="417" cy="240" r="32" fill="${accentColor}"/>`
      : '';
  const nose =
    kind === 'ANIMAL'
      ? `<polygon points="234,268 278,268 256,298" fill="#F43F5E"/>`
      : `<path d="M256,195 L236,268 L256,278 L276,268 Z" fill="rgba(0,0,0,0.22)"/>`;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
    <defs>
      <radialGradient id="g" cx="50%" cy="45%" r="50%">
        <stop offset="0%" stop-color="${skinColor}"/>
        <stop offset="100%" stop-color="${accentColor}"/>
      </radialGradient>
    </defs>
    ${ears}
    <ellipse cx="256" cy="256" rx="164" ry="188" fill="url(#g)" stroke="${accentColor}" stroke-width="6"/>
    <path d="M138,164 Q184,148 215,158" stroke="#0F172A" stroke-width="11" stroke-linecap="round" fill="none"/>
    <path d="M297,158 Q328,148 374,164" stroke="#0F172A" stroke-width="11" stroke-linecap="round" fill="none"/>
    <ellipse cx="184" cy="204" rx="36" ry="20" fill="#F8FAFC" stroke="#0F172A" stroke-width="5"/>
    <circle cx="184" cy="204" r="15" fill="${irisColor}"/>
    <circle cx="184" cy="204" r="7" fill="#090A0F"/>
    <ellipse cx="328" cy="204" rx="36" ry="20" fill="#F8FAFC" stroke="#0F172A" stroke-width="5"/>
    <circle cx="328" cy="204" r="15" fill="${irisColor}"/>
    <circle cx="328" cy="204" r="7" fill="#090A0F"/>
    ${nose}
    <path d="M192,338 Q256,318 320,338 Q256,368 192,338 Z" fill="#E11D48"/>
    <text x="256" y="116" text-anchor="middle" fill="rgba(255,255,255,0.28)" font-size="20" font-family="sans-serif" font-weight="bold">${variant}</text>
  </svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

export const BUILT_IN_CHARACTERS: CharacterItem[] = [
  // 6 Realistic Demo Characters
  {
    id: 'real_cyber',
    nameEn: 'Cyber Creator',
    nameBn: 'সাইবার ক্রিয়েটর',
    category: 'REALISTIC',
    rigType: 'AUTO_MESH',
    imageDataUrl: generateSvgPortraitDataUrl('#E8B89A', '#0088A3', '#00E5FF', 'HUMAN', 'CYBER'),
    landmarks: CANONICAL_36_POINTS,
    puppetConfig: DEFAULT_PUPPET_CONFIG
  },
  {
    id: 'real_maharaja',
    nameEn: 'Royal Maharaja',
    nameBn: 'রাজকীয় মহারাজা',
    category: 'REALISTIC',
    rigType: 'AUTO_MESH',
    imageDataUrl: generateSvgPortraitDataUrl('#DDA37B', '#B45309', '#F59E0B', 'HUMAN', 'ROYAL'),
    landmarks: CANONICAL_36_POINTS,
    puppetConfig: DEFAULT_PUPPET_CONFIG
  },
  {
    id: 'real_neon_pop',
    nameEn: 'Neon Pop Star',
    nameBn: 'নিয়ন পপ স্টার',
    category: 'REALISTIC',
    rigType: 'AUTO_MESH',
    imageDataUrl: generateSvgPortraitDataUrl('#F5C6AA', '#BE185D', '#EC4899', 'HUMAN', 'POP'),
    landmarks: CANONICAL_36_POINTS,
    puppetConfig: DEFAULT_PUPPET_CONFIG
  },
  {
    id: 'real_director',
    nameEn: 'Vintage Director',
    nameBn: 'ভিন্টেজ ডিরেক্টর',
    category: 'REALISTIC',
    rigType: 'AUTO_MESH',
    imageDataUrl: generateSvgPortraitDataUrl('#D89E78', '#78350F', '#D97706', 'HUMAN', 'CINEMA'),
    landmarks: CANONICAL_36_POINTS,
    puppetConfig: DEFAULT_PUPPET_CONFIG
  },
  {
    id: 'real_detective',
    nameEn: 'Classic Detective',
    nameBn: 'ক্লাসিক গোয়েন্দা',
    category: 'REALISTIC',
    rigType: 'AUTO_MESH',
    imageDataUrl: generateSvgPortraitDataUrl('#E2B598', '#334155', '#38BDF8', 'HUMAN', 'NOIR'),
    landmarks: CANONICAL_36_POINTS,
    puppetConfig: DEFAULT_PUPPET_CONFIG
  },
  {
    id: 'real_warrior',
    nameEn: 'Mystic Warrior',
    nameBn: 'মিস্টিক যোদ্ধা',
    category: 'REALISTIC',
    rigType: 'AUTO_MESH',
    imageDataUrl: generateSvgPortraitDataUrl('#CE8E64', '#065F46', '#10B981', 'HUMAN', 'WARRIOR'),
    landmarks: CANONICAL_36_POINTS,
    puppetConfig: DEFAULT_PUPPET_CONFIG
  },
  // 3 Cartoon Demo Characters
  {
    id: 'toon_anime_hero',
    nameEn: 'Anime Hero',
    nameBn: 'অ্যানিমে হিরো',
    category: 'CARTOON',
    rigType: 'MANUAL_PUPPET',
    imageDataUrl: generateSvgPortraitDataUrl('#FDE0C8', '#2563EB', '#3B82F6', 'CARTOON', 'ANIME'),
    puppetConfig: DEFAULT_PUPPET_CONFIG
  },
  {
    id: 'toon_cyber_bot',
    nameEn: 'Toon CyberBot',
    nameBn: 'টুন রোবট',
    category: 'CARTOON',
    rigType: 'MANUAL_PUPPET',
    imageDataUrl: generateSvgPortraitDataUrl('#475569', '#00E5FF', '#00E5FF', 'CARTOON', 'BOT'),
    puppetConfig: DEFAULT_PUPPET_CONFIG
  },
  {
    id: 'toon_villain',
    nameEn: 'Comic Villain',
    nameBn: 'কমিক ভিলেন',
    category: 'CARTOON',
    rigType: 'MANUAL_PUPPET',
    imageDataUrl: generateSvgPortraitDataUrl('#DDD6FE', '#7E22CE', '#10B981', 'CARTOON', 'COMIC'),
    puppetConfig: DEFAULT_PUPPET_CONFIG
  },
  // 3 Animal Demo Characters
  {
    id: 'animal_tiger',
    nameEn: 'Bengal Tiger',
    nameBn: 'রয়েল বেঙ্গল টাইগার',
    category: 'ANIMAL',
    rigType: 'MANUAL_PUPPET',
    imageDataUrl: generateSvgPortraitDataUrl('#FB923C', '#9A3412', '#F59E0B', 'ANIMAL', 'TIGER'),
    puppetConfig: DEFAULT_PUPPET_CONFIG
  },
  {
    id: 'animal_cyber_cat',
    nameEn: 'Cyber Cat',
    nameBn: 'সাইবার বিড়াল',
    category: 'ANIMAL',
    rigType: 'MANUAL_PUPPET',
    imageDataUrl: generateSvgPortraitDataUrl('#334155', '#06B6D4', '#10B981', 'ANIMAL', 'CAT'),
    puppetConfig: DEFAULT_PUPPET_CONFIG
  },
  {
    id: 'animal_panda',
    nameEn: 'Golden Panda',
    nameBn: 'গোল্ডেন পান্ডা',
    category: 'ANIMAL',
    rigType: 'MANUAL_PUPPET',
    imageDataUrl: generateSvgPortraitDataUrl('#F8FAFC', '#1E293B', '#F59E0B', 'ANIMAL', 'PANDA'),
    puppetConfig: DEFAULT_PUPPET_CONFIG
  }
];
