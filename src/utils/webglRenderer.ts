import { CharacterItem, LiveBlendshapes, Point2D } from '../types';
import { CANONICAL_36_POINTS, MESH_TRIANGLES } from './demoCharacters';

/**
 * Computes the un-zoomed natural camera placement rectangle on the canvas
 * so the camera stream is never excessively zoomed or double-cropped.
 */
export function computeVideoDrawRect(
  canvasW: number,
  canvasH: number,
  videoW: number,
  videoH: number
): { drawX: number; drawY: number; drawW: number; drawH: number } {
  if (videoW <= 0 || videoH <= 0) {
    return { drawX: 0, drawY: 0, drawW: canvasW, drawH: canvasH };
  }
  // Use fit-width with gentle vertical centering (or at most 1.08x fill) so a 4:3 phone camera
  // stream shows its full natural width and background just like the native phone camera app!
  const fitWidthScale = canvasW / videoW;
  const coverScale = Math.max(canvasW / videoW, canvasH / videoH);
  // Blend toward fitWidthScale so horizontal field-of-view is preserved without zooming in
  const scale = Math.min(coverScale, fitWidthScale * 1.12);
  const drawW = videoW * scale;
  const drawH = videoH * scale;
  const drawX = (canvasW - drawW) * 0.5;
  const drawY = (canvasH - drawH) * 0.5;
  return { drawX, drawY, drawW, drawH };
}

/**
 * Crops & feather-masks an uploaded human photo strictly around the inner facial features
 * (eyebrows, eyes, nose, cheeks, lips, chin) so passport backgrounds, hair, ears, and clothing
 * are completely removed and the face blends seamlessly at 1:1 scale.
 */
export function createFaceCutoutFromLandmarks(
  img: HTMLImageElement,
  lm478: { x: number; y: number }[],
  indices36: number[]
): { dataUrl: string; landmarks36: Point2D[] } {
  const w = img.naturalWidth || img.width || 512;
  const h = img.naturalHeight || img.height || 512;

  let minX = 1, minY = 1, maxX = 0, maxY = 0;
  for (const pt of lm478) {
    if (pt.x < minX) minX = pt.x;
    if (pt.y < minY) minY = pt.y;
    if (pt.x > maxX) maxX = pt.x;
    if (pt.y > maxY) maxY = pt.y;
  }

  const cx = ((minX + maxX) * 0.5) * w;
  const cy = ((minY + maxY) * 0.5) * h;
  const faceW = (maxX - minX) * w;
  const faceH = (maxY - minY) * h;
  // Tight framing so inner facial landmarks fit cleanly without oversized edges
  const halfSpan = Math.max(faceW, faceH) * 0.56;

  const cropLeft = cx - halfSpan;
  const cropTop = cy - halfSpan;
  const cropSize = Math.max(32, halfSpan * 2);

  const outSize = 512;
  const canvas = document.createElement('canvas');
  canvas.width = outSize;
  canvas.height = outSize;
  const ctx = canvas.getContext('2d')!;

  const scale = outSize / cropSize;
  ctx.drawImage(img, -cropLeft * scale, -cropTop * scale, w * scale, h * scale);

  // Soft inner-face oval alpha mask: keeps brows, eyes, nose, cheeks, and mouth while
  // smoothly fading out before the outer hairline, ears, or background.
  ctx.save();
  ctx.globalCompositeOperation = 'destination-in';
  ctx.translate(outSize * 0.5, outSize * 0.51);
  ctx.scale(0.86, 1.06);
  const grad = ctx.createRadialGradient(0, 0, outSize * 0.20, 0, 0, outSize * 0.40);
  grad.addColorStop(0, 'rgba(255,255,255,1)');
  grad.addColorStop(0.62, 'rgba(255,255,255,0.98)');
  grad.addColorStop(0.82, 'rgba(255,255,255,0.55)');
  grad.addColorStop(0.94, 'rgba(255,255,255,0.12)');
  grad.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.fillStyle = grad;
  ctx.beginPath();
  ctx.arc(0, 0, outSize * 0.42, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();

  const landmarks36: Point2D[] = indices36.map((mpIdx) => {
    const pt = lm478[mpIdx] || { x: 0.5, y: 0.5 };
    const px = pt.x * w;
    const py = pt.y * h;
    return {
      x: Math.min(0.96, Math.max(0.04, (px - cropLeft) / cropSize)),
      y: Math.min(0.96, Math.max(0.04, (py - cropTop) / cropSize))
    };
  });

  return {
    dataUrl: canvas.toDataURL('image/png'),
    landmarks36
  };
}

/**
 * 3D cylindrical head-model projection of the 36 canonical facial vertices for wide head turns
 * (yaw up to ±75°, pitch up to ±45°, roll ±60°).
 * Foreshortens the far side of the face realistically when turning left or right so the swapped
 * face stays locked onto the person's head during side-profile talking!
 */
export function projectCanonical36ToBox(
  centerX: number,
  centerY: number,
  boxWidth: number,
  boxHeight: number,
  yawDeg: number,
  pitchDeg: number,
  rollDeg: number,
  jawOpen: number,
  blinkLeft: number,
  blinkRight: number
): Point2D[] {
  const rollRad = (rollDeg * Math.PI) / 180;
  const cosR = Math.cos(rollRad);
  const sinR = Math.sin(rollRad);

  const yawClamped = Math.max(-75, Math.min(75, yawDeg));
  const yawRad = (yawClamped * Math.PI) / 180;
  const sinYaw = Math.sin(yawRad);
  const cosYaw = Math.max(0.28, Math.cos(yawRad));
  const pitchShift = Math.max(-0.45, Math.min(0.45, pitchDeg / 45));

  return CANONICAL_36_POINTS.map((pt, idx) => {
    const lx = pt.x - 0.5;
    let ly = pt.y - 0.5;

    if (idx === 10) ly += blinkLeft * 0.026;
    if (idx === 12) ly -= blinkLeft * 0.020;
    if (idx === 14) ly += blinkRight * 0.026;
    if (idx === 16) ly -= blinkRight * 0.020;

    if (idx === 29) ly -= jawOpen * 0.008;
    if (idx === 30) ly += jawOpen * 0.068;
    if (idx === 28) ly += jawOpen * 0.072;
    if (idx === 32 || idx === 34) ly += jawOpen * 0.048;
    if (idx === 33) ly += jawOpen * 0.058;

    const normHoriz = Math.max(-1, Math.min(1, lx / 0.34));
    const zDepth = Math.max(0.2, 1 - normHoriz * normHoriz * 0.65);

    const sideCompression =
      lx * sinYaw < 0
        ? cosYaw * 0.72 // Far side of face turned away from camera compresses
        : 0.65 + 0.35 * cosYaw; // Near side stays prominent

    const rotated3DX = lx * sideCompression + zDepth * sinYaw * 0.16;
    const rotated3DY = ly + pitchShift * 0.12 * zDepth;

    const scaledX = rotated3DX * (boxWidth / 0.64);
    const scaledY = rotated3DY * (boxHeight / 0.70);

    const rx = scaledX * cosR - scaledY * sinR;
    const ry = scaledX * sinR + scaledY * cosR;

    return {
      x: Math.max(-0.15, Math.min(1.15, centerX + rx)),
      y: Math.max(-0.15, Math.min(1.15, centerY + ry))
    };
  });
}

/**
 * Tracks a person's head position, scale, and left/right profile yaw direction directly from
 * skin-tone vs hair pixel distribution when they turn their head sideways (>45°..85° profile)
 * and MediaPipe's frontal detector temporarily drops out.
 */
export function estimateProfileHeadInCanvas(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  prevX: number,
  prevY: number,
  prevScale: number,
  prevYaw: number
): { centerX: number; centerY: number; scale: number; yaw: number } {
  try {
    const searchRadius = Math.max(0.20, Math.min(0.50, prevScale * 1.1));
    const minPxX = Math.max(0, Math.floor((prevX - searchRadius) * width));
    const maxPxX = Math.min(width - 1, Math.ceil((prevX + searchRadius) * width));
    const minPxY = Math.max(0, Math.floor((prevY - searchRadius) * height));
    const maxPxY = Math.min(height - 1, Math.ceil((prevY + searchRadius) * height));
    const boxW = Math.max(16, maxPxX - minPxX);
    const boxH = Math.max(16, maxPxY - minPxY);

    const imgData = ctx.getImageData(minPxX, minPxY, boxW, boxH).data;
    let skinSumX = 0;
    let skinSumY = 0;
    let skinWeight = 0;
    let hairSumX = 0;
    let hairWeight = 0;

    const step = 4;
    for (let y = 0; y < boxH; y += step) {
      const ny = (minPxY + y) / height;
      for (let x = 0; x < boxW; x += step) {
        const nx = (minPxX + x) / width;
        const idx = (y * boxW + x) * 4;
        const r = imgData[idx];
        const g = imgData[idx + 1];
        const b = imgData[idx + 2];

        const isSkin =
          r > 55 &&
          g > 35 &&
          b > 20 &&
          r > g &&
          g >= b * 0.85 &&
          r - b > 14 &&
          r < 245;

        const dist = Math.hypot(nx - prevX, ny - prevY);
        const w = Math.max(0.1, 1 - dist / searchRadius);

        if (isSkin) {
          skinSumX += nx * w;
          skinSumY += ny * w;
          skinWeight += w;
        } else if (r < 65 && g < 65 && b < 68 && ny < prevY + prevScale * 0.35) {
          hairSumX += nx * w;
          hairWeight += w;
        }
      }
    }

    if (skinWeight < 1.5) {
      return { centerX: prevX, centerY: prevY, scale: prevScale, yaw: prevYaw };
    }

    const skinCx = skinSumX / skinWeight;
    const skinCy = skinSumY / skinWeight;

    let estimatedYaw = prevYaw;
    if (hairWeight > 1.0) {
      const hairCx = hairSumX / hairWeight;
      const deltaX = (skinCx - hairCx) / Math.max(0.15, prevScale);
      estimatedYaw = Math.max(-62, Math.min(62, deltaX * 95));
    }

    return {
      centerX: Math.max(0.12, Math.min(0.88, prevX * 0.35 + skinCx * 0.65)),
      centerY: Math.max(0.12, Math.min(0.88, prevY * 0.35 + skinCy * 0.65)),
      scale: prevScale,
      yaw: estimatedYaw
    };
  } catch {
    return { centerX: prevX, centerY: prevY, scale: prevScale, yaw: prevYaw };
  }
}

/**
 * Renders the live camera video frame (at natural un-zoomed field of view) and warps
 * the selected character's face mesh 1:1 onto the live tracked face.
 */
export function renderCompositeCanvasFrame(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  videoEl: HTMLVideoElement | null,
  characterImg: HTMLImageElement | null,
  character: CharacterItem,
  isFaceTracked: boolean,
  livePoints36: Point2D[],
  blendshapes: LiveBlendshapes,
  blendOpacity: number,
  isMirrored: boolean,
  detailedMouthInterior: boolean,
  showWireframe: boolean
) {
  ctx.fillStyle = '#090A0F';
  ctx.fillRect(0, 0, width, height);

  // 1. Draw Live Camera Video Frame at natural un-zoomed scale
  if (videoEl && videoEl.readyState >= 2 && videoEl.videoWidth > 0) {
    const vw = videoEl.videoWidth;
    const vh = videoEl.videoHeight;
    const { drawX, drawY, drawW, drawH } = computeVideoDrawRect(width, height, vw, vh);

    ctx.save();
    if (isMirrored) {
      ctx.translate(width, 0);
      ctx.scale(-1, 1);
    }
    ctx.drawImage(videoEl, drawX, drawY, drawW, drawH);
    ctx.restore();
  } else {
    const grad = ctx.createRadialGradient(width * 0.5, height * 0.45, 20, width * 0.5, height * 0.5, width * 0.8);
    grad.addColorStop(0, '#17223B');
    grad.addColorStop(1, '#090A0F');
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, width, height);
  }

  if (!isFaceTracked || !characterImg || !characterImg.complete || characterImg.naturalWidth === 0) {
    return;
  }

  drawWarpedCharacterOverlay(
    ctx,
    width,
    height,
    characterImg,
    character,
    livePoints36,
    blendshapes,
    blendOpacity,
    detailedMouthInterior,
    showWireframe
  );
}

/**
 * Renders a single warped character face (478-landmark triangulation mesh or 2D puppet grid)
 * onto an existing canvas context. Can be called multiple times per frame for multi-face video swaps!
 */
export function drawWarpedCharacterOverlay(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  characterImg: HTMLImageElement,
  character: CharacterItem,
  livePoints36: Point2D[],
  blendshapes: LiveBlendshapes,
  blendOpacity: number,
  detailedMouthInterior: boolean,
  showWireframe: boolean
) {
  ctx.save();
  ctx.globalAlpha = Math.min(Math.max(blendOpacity, 0.1), 1.0);

  if (character.rigType === 'AUTO_MESH') {
    const srcPts = character.landmarks && character.landmarks.length === 36 ? character.landmarks : CANONICAL_36_POINTS;
    const imgW = characterImg.naturalWidth || characterImg.width;
    const imgH = characterImg.naturalHeight || characterImg.height;

    for (let i = 0; i < MESH_TRIANGLES.length; i += 3) {
      const i0 = MESH_TRIANGLES[i];
      const i1 = MESH_TRIANGLES[i + 1];
      const i2 = MESH_TRIANGLES[i + 2];

      const s0 = srcPts[i0], s1 = srcPts[i1], s2 = srcPts[i2];
      const d0 = livePoints36[i0], d1 = livePoints36[i1], d2 = livePoints36[i2];
      if (!s0 || !s1 || !s2 || !d0 || !d1 || !d2) continue;

      drawTexturedTriangle(
        ctx,
        characterImg,
        s0.x * imgW, s0.y * imgH,
        s1.x * imgW, s1.y * imgH,
        s2.x * imgW, s2.y * imgH,
        d0.x * width, d0.y * height,
        d1.x * width, d1.y * height,
        d2.x * width, d2.y * height
      );
    }

    if (blendshapes.jawOpen > 0.04) {
      const mL = livePoints36[25];
      const mR = livePoints36[27];
      const uIn = livePoints36[29];
      const lIn = livePoints36[30];
      drawOpenMouthCavity(
        ctx,
        mL.x * width, mL.y * height,
        mR.x * width, mR.y * height,
        uIn.x * width, uIn.y * height,
        lIn.x * width, lIn.y * height,
        blendshapes.jawOpen,
        detailedMouthInterior
      );
    }
  } else {
    render2DPuppetGrid(
      ctx,
      width,
      height,
      characterImg,
      character,
      livePoints36,
      blendshapes,
      detailedMouthInterior
    );
  }

  if (showWireframe) {
    ctx.strokeStyle = 'rgba(0, 229, 255, 0.72)';
    ctx.lineWidth = 1.4;
    for (let i = 0; i < MESH_TRIANGLES.length; i += 3) {
      const p0 = livePoints36[MESH_TRIANGLES[i]];
      const p1 = livePoints36[MESH_TRIANGLES[i + 1]];
      const p2 = livePoints36[MESH_TRIANGLES[i + 2]];
      if (!p0 || !p1 || !p2) continue;
      ctx.beginPath();
      ctx.moveTo(p0.x * width, p0.y * height);
      ctx.lineTo(p1.x * width, p1.y * height);
      ctx.lineTo(p2.x * width, p2.y * height);
      ctx.closePath();
      ctx.stroke();
    }
  }

  ctx.restore();
}

function render2DPuppetGrid(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  img: HTMLImageElement,
  character: CharacterItem,
  livePoints36: Point2D[],
  blendshapes: LiveBlendshapes,
  detailedMouthInterior: boolean
) {
  const cfg = character.puppetConfig;
  const noseLive = livePoints36[18] || { x: 0.5, y: 0.5 };
  const leftTemple = livePoints36[21] || { x: 0.25, y: 0.4 };
  const rightTemple = livePoints36[22] || { x: 0.75, y: 0.4 };
  // Exact 1.12x temple-to-temple width so 2D puppet fits the head naturally without looking huge
  const headWidth = Math.hypot((rightTemple.x - leftTemple.x) * width, (rightTemple.y - leftTemple.y) * height) * 1.12;
  const headHeight = headWidth;

  const cx = noseLive.x * width;
  const cy = noseLive.y * height;
  const rollRad = (blendshapes.roll * Math.PI) / 180;
  const cosR = Math.cos(rollRad);
  const sinR = Math.sin(rollRad);
  const yawShift = Math.max(-0.4, Math.min(0.4, blendshapes.yaw / 45));
  const pitchShift = Math.max(-0.35, Math.min(0.35, blendshapes.pitch / 45));

  const imgW = img.naturalWidth || img.width;
  const imgH = img.naturalHeight || img.height;

  const cols = 7;
  const rows = 7;
  const grid: { sx: number; sy: number; dx: number; dy: number }[][] = [];

  for (let r = 0; r <= rows; r++) {
    const rowArr: { sx: number; sy: number; dx: number; dy: number }[] = [];
    for (let c = 0; c <= cols; c++) {
      const u = c / cols;
      const v = r / rows;
      const du = u;
      let dv = v;

      const lBox = cfg.leftEyeBox;
      const lCx = (lBox.left + lBox.right) * 0.5;
      const lCy = (lBox.top + lBox.bottom) * 0.5;
      const lDist = Math.hypot((u - lCx) / Math.max(0.05, lBox.right - lBox.left), (v - lCy) / Math.max(0.05, lBox.bottom - lBox.top));
      if (lDist < 0.85) {
        dv += (lCy - v) * blendshapes.eyeBlinkLeft * 0.85 * (1 - lDist / 0.85);
      }

      const rBox = cfg.rightEyeBox;
      const rCx = (rBox.left + rBox.right) * 0.5;
      const rCy = (rBox.top + rBox.bottom) * 0.5;
      const rDist = Math.hypot((u - rCx) / Math.max(0.05, rBox.right - rBox.left), (v - rCy) / Math.max(0.05, rBox.bottom - rBox.top));
      if (rDist < 0.85) {
        dv += (rCy - v) * blendshapes.eyeBlinkRight * 0.85 * (1 - rDist / 0.85);
      }

      const mBox = cfg.mouthBox;
      const mCx = (mBox.left + mBox.right) * 0.5;
      const mCy = (mBox.top + mBox.bottom) * 0.5;
      if (v > mCy - 0.02) {
        const hDist = Math.abs(u - mCx) / Math.max(0.08, (mBox.right - mBox.left) * 0.95);
        if (hDist < 1) {
          dv += blendshapes.jawOpen * 0.13 * (1 - hDist * hDist);
        }
      }

      const lx = (du - cfg.noseTip.x) * headWidth + yawShift * headWidth * 0.18;
      const ly = (dv - cfg.noseTip.y) * headHeight + pitchShift * headHeight * 0.14;
      const rx = lx * cosR - ly * sinR;
      const ry = lx * sinR + ly * cosR;

      rowArr.push({
        sx: u * imgW,
        sy: v * imgH,
        dx: cx + rx,
        dy: cy + ry
      });
    }
    grid.push(rowArr);
  }

  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      const p00 = grid[r][c];
      const p10 = grid[r][c + 1];
      const p01 = grid[r + 1][c];
      const p11 = grid[r + 1][c + 1];

      drawTexturedTriangle(ctx, img, p00.sx, p00.sy, p10.sx, p10.sy, p01.sx, p01.sy, p00.dx, p00.dy, p10.dx, p10.dy, p01.dx, p01.dy);
      drawTexturedTriangle(ctx, img, p10.sx, p10.sy, p11.sx, p11.sy, p01.sx, p01.sy, p10.dx, p10.dy, p11.dx, p11.dy, p01.dx, p01.dy);
    }
  }

  if (blendshapes.jawOpen > 0.04) {
    const mapPt = (u: number, v: number) => {
      const lx = (u - cfg.noseTip.x) * headWidth + yawShift * headWidth * 0.18;
      const ly = (v - cfg.noseTip.y) * headHeight + pitchShift * headHeight * 0.14;
      return {
        x: cx + (lx * cosR - ly * sinR),
        y: cy + (lx * sinR + ly * cosR)
      };
    };
    const mL = mapPt(cfg.mouthLeft.x, cfg.mouthLeft.y);
    const mR = mapPt(cfg.mouthRight.x, cfg.mouthRight.y);
    const mMidU = (cfg.mouthLeft.x + cfg.mouthRight.x) * 0.5;
    const mMidV = (cfg.mouthLeft.y + cfg.mouthRight.y) * 0.5;
    const uIn = mapPt(mMidU, mMidV - blendshapes.jawOpen * 0.015);
    const lIn = mapPt(mMidU, mMidV + blendshapes.jawOpen * 0.11);

    drawOpenMouthCavity(ctx, mL.x, mL.y, mR.x, mR.y, uIn.x, uIn.y, lIn.x, lIn.y, blendshapes.jawOpen, detailedMouthInterior);
  }
}

function drawOpenMouthCavity(
  ctx: CanvasRenderingContext2D,
  leftX: number,
  leftY: number,
  rightX: number,
  rightY: number,
  topX: number,
  topY: number,
  bottomX: number,
  bottomY: number,
  jawOpen: number,
  detailed: boolean
) {
  ctx.save();
  ctx.beginPath();
  ctx.moveTo(leftX, leftY);
  ctx.quadraticCurveTo(topX, topY, rightX, rightY);
  ctx.quadraticCurveTo(bottomX, bottomY, leftX, leftY);
  ctx.closePath();

  ctx.fillStyle = '#14070C';
  ctx.fill();

  if (detailed && jawOpen > 0.08) {
    ctx.clip();
    const mw = Math.hypot(rightX - leftX, rightY - leftY);
    const mh = Math.hypot(bottomX - topX, bottomY - topY);

    ctx.fillStyle = '#D94668';
    ctx.beginPath();
    ctx.ellipse(bottomX, topY + mh * 0.72, mw * 0.32, mh * 0.38, 0, 0, Math.PI * 2);
    ctx.fill();

    ctx.fillStyle = '#F1F5F9';
    ctx.fillRect(leftX + mw * 0.14, topY - mh * 0.08, mw * 0.72, mh * 0.26);
  }
  ctx.restore();
}

function drawTexturedTriangle(
  ctx: CanvasRenderingContext2D,
  img: HTMLImageElement,
  u0: number, v0: number,
  u1: number, v1: number,
  u2: number, v2: number,
  x0: number, y0: number,
  x1: number, y1: number,
  x2: number, y2: number
) {
  const denom = u0 * (v2 - v1) - u1 * v2 + u2 * v1 + (u1 - u2) * v0;
  if (Math.abs(denom) < 0.0001) return;

  const m11 = -(v0 * (x2 - x1) - v1 * x2 + v2 * x1 + (v1 - v2) * x0) / denom;
  const m12 = (v1 * y2 + v0 * (y1 - y2) - v2 * y1 + (v2 - v1) * y0) / denom;
  const m21 = (u0 * (x2 - x1) - u1 * x2 + u2 * x1 + (u1 - u2) * x0) / denom;
  const m22 = -(u1 * y2 + u0 * (y1 - y2) - u2 * y1 + (u2 - u1) * y0) / denom;
  const dx = (u0 * (v2 * x1 - v1 * x2) + v0 * (u1 * x2 - u2 * x1) + (u2 * v1 - u1 * v2) * x0) / denom;
  const dy = (u0 * (v2 * y1 - v1 * y2) + v0 * (u1 * y2 - u2 * y1) + (u2 * v1 - u1 * v2) * y0) / denom;

  ctx.save();
  ctx.beginPath();
  ctx.moveTo(x0, y0);
  ctx.lineTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.closePath();
  ctx.clip();
  ctx.transform(m11, m12, m21, m22, dx, dy);
  ctx.drawImage(img, 0, 0);
  ctx.restore();
}
