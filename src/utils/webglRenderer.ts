import { CharacterItem, LiveBlendshapes, Point2D } from '../types';
import { CANONICAL_36_POINTS, MESH_TRIANGLES } from './demoCharacters';

/**
 * Renders the live camera video frame, triangulated mesh warp (for AUTO_MESH)
 * or 2D puppet grid deformation (for MANUAL_PUPPET), and dark mouth interior
 * with optional teeth/tongue details.
 */
export function renderCompositeCanvasFrame(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  videoEl: HTMLVideoElement | null,
  characterImg: HTMLImageElement | null,
  character: CharacterItem,
  livePoints36: Point2D[],
  blendshapes: LiveBlendshapes,
  blendOpacity: number,
  isMirrored: boolean,
  detailedMouthInterior: boolean,
  showWireframe: boolean
) {
  ctx.clearRect(0, 0, width, height);

  // 1. Draw Live Camera Video Frame
  if (videoEl && videoEl.readyState >= 2) {
    ctx.save();
    if (isMirrored) {
      ctx.translate(width, 0);
      ctx.scale(-1, 1);
    }
    const vw = videoEl.videoWidth || width;
    const vh = videoEl.videoHeight || height;
    const scale = Math.max(width / vw, height / vh);
    const dw = vw * scale;
    const dh = vh * scale;
    ctx.drawImage(videoEl, (width - dw) * 0.5, (height - dh) * 0.5, dw, dh);
    ctx.restore();
  } else {
    const grad = ctx.createRadialGradient(width * 0.5, height * 0.45, 20, width * 0.5, height * 0.5, width * 0.8);
    grad.addColorStop(0, '#17223B');
    grad.addColorStop(1, '#090A0F');
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, width, height);
  }

  if (!characterImg || !characterImg.complete) return;

  ctx.save();
  ctx.globalAlpha = Math.min(Math.max(blendOpacity, 0.1), 1.0);

  if (character.rigType === 'AUTO_MESH') {
    const srcPts = character.landmarks && character.landmarks.length === 36 ? character.landmarks : CANONICAL_36_POINTS;
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
        s0.x * characterImg.width, s0.y * characterImg.height,
        s1.x * characterImg.width, s1.y * characterImg.height,
        s2.x * characterImg.width, s2.y * characterImg.height,
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
    // 2D Puppet Grid Warp for Cartoon & Animal Faces
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
    ctx.strokeStyle = 'rgba(0, 229, 255, 0.65)';
    ctx.lineWidth = 1.2;
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
  const leftTemple = livePoints36[21] || { x: 0.2, y: 0.4 };
  const rightTemple = livePoints36[22] || { x: 0.8, y: 0.4 };
  const headWidth = Math.hypot((rightTemple.x - leftTemple.x) * width, (rightTemple.y - leftTemple.y) * height) * 1.65;
  const headHeight = headWidth;

  const cx = noseLive.x * width;
  const cy = noseLive.y * height;
  const rollRad = (blendshapes.roll * Math.PI) / 180;
  const cosR = Math.cos(rollRad);
  const sinR = Math.sin(rollRad);
  const yawShift = Math.max(-0.4, Math.min(0.4, blendshapes.yaw / 45));
  const pitchShift = Math.max(-0.35, Math.min(0.35, blendshapes.pitch / 45));

  const cols = 7;
  const rows = 7;
  const grid: { sx: number; sy: number; dx: number; dy: number }[][] = [];

  for (let r = 0; r <= rows; r++) {
    const rowArr: { sx: number; sy: number; dx: number; dy: number }[] = [];
    for (let c = 0; c <= cols; c++) {
      const u = c / cols;
      const v = r / rows;
      let du = u;
      let dv = v;

      // Eye blink vertical squash inside leftEyeBox & rightEyeBox
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

      // Jaw open downward displacement in lower mouthBox
      const mBox = cfg.mouthBox;
      const mCx = (mBox.left + mBox.right) * 0.5;
      const mCy = (mBox.top + mBox.bottom) * 0.5;
      if (v > mCy - 0.02) {
        const hDist = Math.abs(u - mCx) / Math.max(0.08, (mBox.right - mBox.left) * 0.95);
        if (hDist < 1) {
          dv += blendshapes.jawOpen * 0.13 * (1 - hDist * hDist);
        }
      }

      let lx = (du - cfg.noseTip.x) * headWidth + yawShift * headWidth * 0.18;
      let ly = (dv - cfg.noseTip.y) * headHeight + pitchShift * headHeight * 0.14;
      const rx = lx * cosR - ly * sinR;
      const ry = lx * sinR + ly * cosR;

      rowArr.push({
        sx: u * img.width,
        sy: v * img.height,
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

    // Tongue
    ctx.fillStyle = '#D94668';
    ctx.beginPath();
    ctx.ellipse(bottomX, topY + mh * 0.72, mw * 0.32, mh * 0.38, 0, 0, Math.PI * 2);
    ctx.fill();

    // Upper teeth
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
