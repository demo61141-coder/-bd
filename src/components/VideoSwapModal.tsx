import React, { useEffect, useRef, useState } from 'react';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { FaceLandmarker } from '@mediapipe/tasks-vision';
import { CharacterItem, LiveBlendshapes, Point2D, StudioSettings } from '../types';
import { drawWarpedCharacterOverlay } from '../utils/webglRenderer';

export interface WebDetectedFaceSlot {
  slotIndex: number;
  labelBn: string;
  labelEn: string;
  thumbnailDataUrl: string;
  centerX: number;
  centerY: number;
  assignedCharacterId: string | null; // null = Keep Original Face
}

interface Props {
  characters: CharacterItem[];
  defaultCharacterId: string;
  settings: StudioSettings;
  landmarker: FaceLandmarker | null;
  mpTo36Indices: number[];
  onUploadCustomFace: () => void;
  onShowToast: (msg: string) => void;
  onClose: () => void;
}

const SLOT_COLORS = ['#00E5FF', '#A855F7', '#10B981', '#F59E0B', '#FF2E54'];

export const VideoSwapModal: React.FC<Props> = ({
  characters,
  defaultCharacterId,
  settings,
  landmarker,
  mpTo36Indices,
  onUploadCustomFace,
  onShowToast,
  onClose
}) => {
  const videoInputRef = useRef<HTMLInputElement | null>(null);
  const uploadedVideoRef = useRef<HTMLVideoElement | null>(null);
  const previewCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const charImagesMapRef = useRef<Map<string, HTMLImageElement>>(new Map());
  const lastTimestampRef = useRef<number>(0);

  const [videoUrl, setVideoUrl] = useState<string | null>(null);
  const [isScanning, setIsScanning] = useState(false);
  const [detectedSlots, setDetectedSlots] = useState<WebDetectedFaceSlot[]>([]);
  const [selectedSlotIdx, setSelectedSlotIdx] = useState<number>(0);
  const [isExporting, setIsExporting] = useState(false);

  // Preload all character HTMLImageElements for synchronous multi-face frame rendering
  useEffect(() => {
    characters.forEach((c) => {
      if (!charImagesMapRef.current.has(c.id)) {
        const img = new Image();
        img.crossOrigin = 'anonymous';
        img.src = c.imageDataUrl;
        charImagesMapRef.current.set(c.id, img);
      }
    });
  }, [characters]);

  // Configure FaceLandmarker for up to 5 faces while inside VideoSwapModal
  useEffect(() => {
    if (landmarker) {
      try {
        landmarker.setOptions({ numFaces: 5, runningMode: 'VIDEO' });
      } catch {
        // ignore
      }
    }
    return () => {
      if (landmarker) {
        try {
          landmarker.setOptions({ numFaces: 1, runningMode: 'VIDEO' });
        } catch {
          // ignore
        }
      }
    };
  }, [landmarker]);

  const handleVideoFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = '';
    if (videoUrl) URL.revokeObjectURL(videoUrl);
    const url = URL.createObjectURL(file);
    setVideoUrl(url);
    scanVideoForFaces(url);
  };

  // Scan sampled frames across the uploaded video to find & cluster all faces present
  const scanVideoForFaces = async (url: string) => {
    setIsScanning(true);
    setDetectedSlots([]);

    const tempVideo = document.createElement('video');
    tempVideo.src = url;
    tempVideo.muted = true;
    tempVideo.playsInline = true;

    await new Promise<void>((resolve) => {
      tempVideo.onloadedmetadata = () => resolve();
      tempVideo.onerror = () => resolve();
    });

    const duration = Math.max(1, tempVideo.duration || 3);
    const vw = tempVideo.videoWidth || 720;
    const vh = tempVideo.videoHeight || 1280;
    const sampleCount = 6;
    const slots: WebDetectedFaceSlot[] = [];

    const offCanvas = document.createElement('canvas');
    offCanvas.width = vw;
    offCanvas.height = vh;
    const offCtx = offCanvas.getContext('2d')!;

    if (landmarker) {
      try {
        await landmarker.setOptions({ runningMode: 'IMAGE', numFaces: 5 });
      } catch {
        // ignore
      }
    }

    for (let i = 0; i < sampleCount; i++) {
      const seekSec = (duration * i) / sampleCount;
      tempVideo.currentTime = seekSec;
      await new Promise<void>((resolve) => {
        const onSeeked = () => {
          tempVideo.removeEventListener('seeked', onSeeked);
          resolve();
        };
        tempVideo.addEventListener('seeked', onSeeked);
        setTimeout(resolve, 400);
      });

      offCtx.drawImage(tempVideo, 0, 0, vw, vh);
      if (landmarker) {
        try {
          const res = landmarker.detect(offCanvas);
          const allFaceLms = res.faceLandmarks || [];
          // Sort left to right
          const sorted = [...allFaceLms].sort((a, b) => (a[1]?.x || 0.5) - (b[1]?.x || 0.5));
          for (const lm478 of sorted) {
            let minX = 1, minY = 1, maxX = 0, maxY = 0;
            for (const pt of lm478) {
              if (pt.x < minX) minX = pt.x;
              if (pt.y < minY) minY = pt.y;
              if (pt.x > maxX) maxX = pt.x;
              if (pt.y > maxY) maxY = pt.y;
            }
            const cx = (minX + maxX) * 0.5;
            const cy = (minY + maxY) * 0.5;

            const existing = slots.find((s) => Math.hypot(s.centerX - cx, s.centerY - cy) < 0.16);
            if (!existing && slots.length < 5) {
              const thumbCanvas = document.createElement('canvas');
              thumbCanvas.width = 128;
              thumbCanvas.height = 128;
              const tCtx = thumbCanvas.getContext('2d')!;
              const pad = Math.max(maxX - minX, maxY - minY) * 0.72;
              const sx = Math.max(0, (cx - pad) * vw);
              const sy = Math.max(0, (cy - pad) * vh);
              const sw = Math.min(vw - sx, pad * 2 * vw);
              const sh = Math.min(vh - sy, pad * 2 * vh);
              tCtx.drawImage(offCanvas, sx, sy, Math.max(16, sw), Math.max(16, sh), 0, 0, 128, 128);

              const slotNum = slots.length + 1;
              slots.push({
                slotIndex: slots.length,
                labelBn: `মুখ #${slotNum}`,
                labelEn: `Face #${slotNum}`,
                thumbnailDataUrl: thumbCanvas.toDataURL('image/jpeg', 0.85),
                centerX: cx,
                centerY: cy,
                assignedCharacterId: slots.length === 0 ? defaultCharacterId : null
              });
            }
          }
        } catch {
          // continue
        }
      }
    }

    if (landmarker) {
      try {
        await landmarker.setOptions({ runningMode: 'VIDEO', numFaces: 5 });
      } catch {
        // ignore
      }
    }

    if (slots.length === 0) {
      const thumbCanvas = document.createElement('canvas');
      thumbCanvas.width = 128;
      thumbCanvas.height = 128;
      thumbCanvas.getContext('2d')!.drawImage(offCanvas, vw * 0.25, vh * 0.2, vw * 0.5, vh * 0.5, 0, 0, 128, 128);
      slots.push({
        slotIndex: 0,
        labelBn: 'মুখ #১',
        labelEn: 'Face #1',
        thumbnailDataUrl: thumbCanvas.toDataURL('image/jpeg', 0.85),
        centerX: 0.5,
        centerY: 0.45,
        assignedCharacterId: defaultCharacterId
      });
    }

    setDetectedSlots(slots);
    setSelectedSlotIdx(0);
    setIsScanning(false);
    onShowToast(`ভিডিওতে ${slots.length}টি মুখ পাওয়া গেছে! / Found ${slots.length} faces in video!`);
  };

  // Real-time multi-face playback render loop on the uploaded video
  useEffect(() => {
    if (!videoUrl || isScanning) return;
    let animId = 0;

    const renderVideoFrame = () => {
      const video = uploadedVideoRef.current;
      const canvas = previewCanvasRef.current;
      if (video && canvas && video.readyState >= 2 && video.videoWidth > 0) {
        if (canvas.width !== video.videoWidth || canvas.height !== video.videoHeight) {
          canvas.width = video.videoWidth;
          canvas.height = video.videoHeight;
        }
        const ctx = canvas.getContext('2d');
        if (ctx) {
          const cw = canvas.width;
          const ch = canvas.height;
          ctx.drawImage(video, 0, 0, cw, ch);

          if (landmarker) {
            try {
              const nowMs = Math.max(performance.now(), lastTimestampRef.current + 1);
              lastTimestampRef.current = nowMs;
              const res = landmarker.detectForVideo(video, nowMs);
              const faceLandmarksList = res.faceLandmarks || [];
              const usedSlots = new Set<number>();

              faceLandmarksList.forEach((lm478, faceIdx) => {
                const nose = lm478[1] || { x: 0.5, y: 0.5 };
                let bestSlot: WebDetectedFaceSlot | null = null;
                let bestDist = 999;
                for (const s of detectedSlots) {
                  if (usedSlots.has(s.slotIndex)) continue;
                  const d = Math.hypot(s.centerX - nose.x, s.centerY - nose.y);
                  if (d < bestDist) {
                    bestDist = d;
                    bestSlot = s;
                  }
                }
                if (!bestSlot && detectedSlots.length > 0) {
                  bestSlot = detectedSlots[faceIdx % detectedSlots.length];
                }
                if (bestSlot && bestSlot.assignedCharacterId) {
                  usedSlots.add(bestSlot.slotIndex);
                  const assignedChar = characters.find((c) => c.id === bestSlot!.assignedCharacterId);
                  const charImg = assignedChar ? charImagesMapRef.current.get(assignedChar.id) : null;
                  if (assignedChar && charImg && charImg.complete) {
                    const pts36: Point2D[] = mpTo36Indices.map((idx) => {
                      const pt = lm478[idx] || { x: 0.5, y: 0.5 };
                      return { x: pt.x, y: pt.y };
                    });
                    const cats = res.faceBlendshapes?.[faceIdx]?.categories || [];
                    const findScore = (name: string) => cats.find((c) => c.categoryName === name)?.score || 0;
                    const le = pts36[9];
                    const re = pts36[15];
                    const ns = pts36[18];
                    const eyeDist = Math.max(0.05, Math.hypot(re.x - le.x, re.y - le.y));
                    const rollDeg = (Math.atan2(re.y - le.y, re.x - le.x) * 180) / Math.PI;
                    const yawDeg = ((ns.x - (le.x + re.x) * 0.5) / eyeDist) * 45;

                    const blendshapes: LiveBlendshapes = {
                      jawOpen: findScore('jawOpen'),
                      eyeBlinkLeft: findScore('eyeBlinkLeft'),
                      eyeBlinkRight: findScore('eyeBlinkRight'),
                      yaw: yawDeg,
                      pitch: 0,
                      roll: rollDeg
                    };

                    drawWarpedCharacterOverlay(
                      ctx,
                      cw,
                      ch,
                      charImg,
                      assignedChar,
                      pts36,
                      blendshapes,
                      settings.blendOpacity,
                      settings.detailedMouthInterior,
                      false
                    );
                  }
                }
              });
            } catch {
              // ignore frame error
            }
          }
        }
      }
      animId = requestAnimationFrame(renderVideoFrame);
    };

    animId = requestAnimationFrame(renderVideoFrame);
    return () => cancelAnimationFrame(animId);
  }, [videoUrl, isScanning, detectedSlots, characters, settings, landmarker, mpTo36Indices]);

  const handleAssignChar = (slotIndex: number, charId: string | null) => {
    setDetectedSlots((prev) =>
      prev.map((s) => (s.slotIndex === slotIndex ? { ...s, assignedCharacterId: charId } : s))
    );
  };

  const handleExportSwappedVideo = async () => {
    const video = uploadedVideoRef.current;
    const canvas = previewCanvasRef.current;
    if (!video || !canvas || isExporting) return;

    setIsExporting(true);
    video.currentTime = 0;
    video.loop = false;
    await video.play();

    const chunks: Blob[] = [];
    const stream = canvas.captureStream(30);
    const recorder = new MediaRecorder(stream);
    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunks.push(e.data);
    };
    recorder.onstop = async () => {
      video.loop = true;
      video.play();
      setIsExporting(false);

      const blob = new Blob(chunks, { type: 'video/webm' });
      const fileName = `FaceSwap_MultiFace_${Date.now()}.webm`;
      try {
        const reader = new FileReader();
        reader.onloadend = async () => {
          const base64 = (reader.result as string).split(',')[1];
          await Filesystem.writeFile({
            path: fileName,
            data: base64,
            directory: Directory.Documents
          });
          onShowToast(`মাল্টি-ফেস ভিডিও সেভ হয়েছে: ${fileName}`);
        };
        reader.readAsDataURL(blob);
      } catch {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = fileName;
        a.click();
        onShowToast('মাল্টি-ফেস ভিডিও ডাউনলোড হয়েছে! / Multi-face video saved!');
      }
    };

    recorder.start();
    video.onended = () => {
      if (recorder.state === 'recording') {
        recorder.stop();
      }
    };
  };

  const activeSlot = detectedSlots.find((s) => s.slotIndex === selectedSlotIdx) || detectedSlots[0];

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 70,
      background: '#090A0F', color: '#F8FAFC',
      display: 'flex', flexDirection: 'column', padding: 16, overflowY: 'auto'
    }}>
      <input
        ref={videoInputRef}
        type="file"
        accept="video/*"
        hidden
        onChange={handleVideoFileChange}
        style={{ position: 'absolute', width: 0, height: 0, opacity: 0, pointerEvents: 'none' }}
      />

      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <div>
          <div style={{ fontSize: 17, fontWeight: 700 }}>
            ভিডিও মাল্টি-ফেস স্টুডিও / Video Multi-Face Swap
          </div>
          <div style={{ fontSize: 12, color: '#00E5FF' }}>
            আগের রেকর্ড করা ভিডিওর সবগুলো মুখ আলাদাভাবে পরিবর্তন করুন
          </div>
        </div>
        <button
          onClick={onClose}
          style={{
            background: '#1B2032', color: '#F8FAFC', border: '1px solid #2A314A',
            borderRadius: 12, padding: '8px 14px', fontWeight: 700, cursor: 'pointer'
          }}
        >
          বন্ধ / Close
        </button>
      </div>

      {!videoUrl && !isScanning && (
        <div
          onClick={() => videoInputRef.current?.click()}
          style={{
            flex: 1, minHeight: 280, borderRadius: 22, border: '2px dashed #00E5FF',
            background: '#141722', display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center', padding: 24, textAlign: 'center', cursor: 'pointer'
          }}
        >
          <div style={{ fontSize: 42, marginBottom: 12 }}>🎬</div>
          <div style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>
            ফোন থেকে আগের রেকর্ড করা ভিডিও আপলোড করুন
          </div>
          <div style={{ fontSize: 13, color: '#00E5FF', marginBottom: 8 }}>
            Tap to Select a Pre-Recorded Video from Gallery
          </div>
          <div style={{ fontSize: 12, color: '#94A3B8', maxWidth: 360 }}>
            ভিডিওতে যতগুলো মুখ থাকবে সবগুলো শো করবে এবং প্রতিটি মুখে আলাদা ক্যারেক্টার বা কাস্টম ছবি বসাতে পারবেন।
          </div>
        </div>
      )}

      {isScanning && (
        <div style={{
          flex: 1, minHeight: 280, borderRadius: 22, border: '1px solid #00E5FF',
          background: '#141722', display: 'flex', flexDirection: 'column',
          alignItems: 'center', justifyContent: 'center', padding: 24, textAlign: 'center'
        }}>
          <div style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>
            ভিডিও স্ক্যান করে সবগুলো মুখ খোঁজা হচ্ছে...
          </div>
          <div style={{ fontSize: 12, color: '#00E5FF' }}>
            Detecting & clustering all faces across video frames...
          </div>
        </div>
      )}

      {videoUrl && !isScanning && (
        <>
          <video
            ref={uploadedVideoRef}
            src={videoUrl}
            playsInline
            autoPlay
            loop
            style={{ position: 'absolute', width: 2, height: 2, opacity: 0.001, pointerEvents: 'none' }}
          />

          <div style={{
            position: 'relative', width: '100%', maxHeight: '44vh',
            background: '#141722', borderRadius: 18, overflow: 'hidden',
            border: '1px solid #2A314A', display: 'flex', justifyContent: 'center'
          }}>
            <canvas
              ref={previewCanvasRef}
              style={{ maxWidth: '100%', maxHeight: '44vh', objectFit: 'contain' }}
            />
            <div style={{
              position: 'absolute', top: 10, left: 10,
              background: 'rgba(9,10,15,0.82)', border: '1px solid #00E5FF',
              color: '#00E5FF', padding: '4px 10px', borderRadius: 12, fontSize: 11, fontWeight: 700
            }}>
              {detectedSlots.length}টি মুখ পাওয়া গেছে • {detectedSlots.length} Faces Detected
            </div>
          </div>

          {/* 1. Detected Faces Row */}
          <div style={{ marginTop: 14 }}>
            <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 8 }}>
              ১. ভিডিওতে পাওয়া মুখ সিলেক্ট করুন / 1. Select a Detected Face ({detectedSlots.length}):
            </div>
            <div style={{ display: 'flex', gap: 10, overflowX: 'auto', paddingBottom: 4 }}>
              {detectedSlots.map((slot) => {
                const isSelected = slot.slotIndex === selectedSlotIdx;
                const badgeColor = SLOT_COLORS[slot.slotIndex % SLOT_COLORS.length];
                const assignedChar = characters.find((c) => c.id === slot.assignedCharacterId);
                return (
                  <button
                    key={slot.slotIndex}
                    onClick={() => setSelectedSlotIdx(slot.slotIndex)}
                    style={{
                      display: 'flex', alignItems: 'center', gap: 10,
                      padding: '8px 12px', borderRadius: 16, cursor: 'pointer',
                      background: isSelected ? 'rgba(0,229,255,0.16)' : '#141722',
                      border: isSelected ? `2px solid ${badgeColor}` : '1px solid #2A314A',
                      color: '#F8FAFC', minWidth: 175, textAlign: 'left'
                    }}
                  >
                    <img
                      src={slot.thumbnailDataUrl}
                      alt={slot.labelEn}
                      style={{
                        width: 44, height: 44, borderRadius: '50%',
                        objectFit: 'cover', border: `2px solid ${badgeColor}`
                      }}
                    />
                    <div>
                      <div style={{ fontSize: 12, fontWeight: 700 }}>
                        {slot.labelBn} • {slot.labelEn}
                      </div>
                      <div style={{ fontSize: 11, color: assignedChar ? badgeColor : '#94A3B8', marginTop: 2 }}>
                        {assignedChar ? `➜ ${assignedChar.nameBn}` : '• আসল মুখ / Original'}
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* 2. Replacement Character Picker for Selected Face Slot */}
          {activeSlot && (
            <div style={{ marginTop: 14 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: '#00E5FF', marginBottom: 8 }}>
                ২. '{activeSlot.labelBn} ({activeSlot.labelEn})'-এর জন্য নতুন মুখ বেছে নিন:
              </div>
              <div style={{ display: 'flex', gap: 10, overflowX: 'auto', paddingBottom: 6 }}>
                <button
                  onClick={() => handleAssignChar(activeSlot.slotIndex, null)}
                  style={{
                    minWidth: 76, padding: 8, borderRadius: 14, cursor: 'pointer',
                    border: activeSlot.assignedCharacterId === null ? '2px solid #10B981' : '1px solid #2A314A',
                    background: activeSlot.assignedCharacterId === null ? 'rgba(16,185,129,0.18)' : '#141722',
                    color: '#F8FAFC', fontSize: 11, fontWeight: 700
                  }}
                >
                  আসল মুখ<br />Original
                </button>

                <button
                  onClick={onUploadCustomFace}
                  style={{
                    minWidth: 76, padding: 8, borderRadius: 14, cursor: 'pointer',
                    border: '1.5px dashed #00E5FF', background: '#141722',
                    color: '#00E5FF', fontSize: 11, fontWeight: 700
                  }}
                >
                  + নতুন ছবি<br />Upload Face
                </button>

                {characters.map((c) => {
                  const isAssigned = activeSlot.assignedCharacterId === c.id;
                  return (
                    <button
                      key={c.id}
                      onClick={() => handleAssignChar(activeSlot.slotIndex, c.id)}
                      style={{
                        minWidth: 76, padding: 6, borderRadius: 14, cursor: 'pointer',
                        border: isAssigned ? '2px solid #00E5FF' : '1px solid #2A314A',
                        background: isAssigned ? 'rgba(0,229,255,0.18)' : '#141722',
                        color: '#F8FAFC', fontSize: 10
                      }}
                    >
                      <img
                        src={c.imageDataUrl}
                        alt={c.nameEn}
                        style={{ width: 42, height: 42, borderRadius: '50%', objectFit: 'cover' }}
                      />
                      <div style={{ marginTop: 4, fontWeight: 600 }}>{c.nameBn}</div>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* 3. Action Buttons */}
          <div style={{ display: 'flex', gap: 10, marginTop: 16 }}>
            <button
              onClick={() => videoInputRef.current?.click()}
              style={{
                flex: 1, padding: '12px', borderRadius: 14,
                border: '1px solid #2A314A', background: '#141722',
                color: '#F8FAFC', fontWeight: 700, cursor: 'pointer'
              }}
            >
              অন্য ভিডিও / Change Video
            </button>
            <button
              onClick={handleExportSwappedVideo}
              disabled={isExporting}
              style={{
                flex: 1.4, padding: '12px', borderRadius: 14,
                border: 'none', background: '#00E5FF',
                color: '#090A0F', fontWeight: 700, cursor: 'pointer'
              }}
            >
              {isExporting
                ? 'ভিডিও সেভ হচ্ছে... / Rendering...'
                : 'ভিডিও সেভ করুন / Save Swapped Video'}
            </button>
          </div>
        </>
      )}
    </div>
  );
};
