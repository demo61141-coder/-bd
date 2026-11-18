export type CharacterCategory = 'ALL' | 'REALISTIC' | 'CARTOON' | 'ANIMAL' | 'CUSTOM';
export type RigType = 'AUTO_MESH' | 'MANUAL_PUPPET';

export interface Point2D {
  x: number;
  y: number;
}

export interface NormalizedBox {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

export interface PuppetConfig {
  leftEye: Point2D;
  rightEye: Point2D;
  noseTip: Point2D;
  mouthLeft: Point2D;
  mouthRight: Point2D;
  leftEyeBox: NormalizedBox;
  rightEyeBox: NormalizedBox;
  mouthBox: NormalizedBox;
}

export interface CharacterItem {
  id: string;
  nameEn: string;
  nameBn: string;
  category: CharacterCategory;
  rigType: RigType;
  imageDataUrl: string;
  landmarks?: Point2D[];
  puppetConfig: PuppetConfig;
  isCustom?: boolean;
}

export interface StudioSettings {
  facingMode: 'user' | 'environment';
  smoothingStrength: number;
  blendOpacity: number;
  resolution: '720p' | '1080p';
  isMirrored: boolean;
  detailedMouthInterior: boolean;
  showWireframe: boolean;
}

export interface LiveBlendshapes {
  jawOpen: number;
  eyeBlinkLeft: number;
  eyeBlinkRight: number;
  yaw: number;
  pitch: number;
  roll: number;
}
