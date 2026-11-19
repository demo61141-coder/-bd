import React, { useEffect, useRef, useState } from 'react';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { FaceLandmarker, FilesetResolver } from '@mediapipe/tasks-vision';
import {
  CharacterCategory,
  CharacterItem,
  LiveBlendshapes,
  Point2D,
  PuppetConfig,
  StudioSettings
} from './types';
import {
  BUILT_IN_CHARACTERS,
  CANONICAL_36_POINTS,
  DEFAULT_PUPPET_CONFIG
} from './utils/demoCharacters';
import {
  loadCustomCharactersFromDB,
  saveCustomCharacterToDB
} from './utils/db';
import { OneEuroLandmarkBank } from './utils/oneEuroFilter';
import {
  computeVideoDrawRect,
  createFaceCutoutFromLandmarks,
  renderCompositeCanvasFrame
} from './utils/webglRenderer';
import { ManualSetupModal } from './components/ManualSetupModal';
import { SettingsModal } from './components/SettingsModal';
import { GuideModal } from './components/GuideModal';
import { VideoSwapModal } from './components/VideoSwapModal';

// Indices mapping MediaPipe 478 Face Landmarker points to our 36 canonical triangulation points
const MP_TO_36_INDICES = [
  54, 103, 10, 332, 284,
  70, 107, 336, 300,
  33, 159, 133, 145,
  362, 386, 263, 374,
  168, 1, 98, 327,
  127, 356, 234, 454,
  61, 0, 291, 17, 13, 14,
  172, 148, 152, 377, 397
];

export const App: React.FC = () => {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const [characters, setCharacters] = useState<CharacterItem[]>(BUILT_IN_CHARACTERS);
  const [selectedCategory, setSelectedCategory] = useState<CharacterCategory>('ALL');
  const [selectedChar, setSelectedChar] = useState<CharacterItem>(BUILT_IN_CHARACTERS[0]);
  const [settings, setSettings] = useState<StudioSettings>({
    facingMode: 'user',
    smoothingStrength: 0.65,
    blendOpacity: 0.92,
    resolution: '720p',
    isMirrored: true,
    detailedMouthInterior: true,
    showWireframe: false
  });

  const [isLoadingModel, setIsLoadingModel] = useState(true);
  const [isLiveFaceDetected, setIsLiveFaceDetected] = useState(false);
  const [permissionError, setPermissionError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [isRecording, setIsRecording] = useState(false);
  const [manualUploadDataUrl, setManualUploadDataUrl] = useState<string | null>(null);
  const [showSettings, setShowSettings] = useState(false);
  const [showGuide, setShowGuide] = useState(false);
  const [showVideoSwap, setShowVideoSwap] = useState(false);

  const landmarkerRef = useRef<FaceLandmarker | null>(null);
  const isStaticDetectingRef = useRef<boolean>(false);
  const lastDetectTimestampRef = useRef<number>(0);
  const lastTrackedPtsRef = useRef<Point2D[]>(CANONICAL_36_POINTS);
  const lastTrackedTimeRef = useRef<number>(0);
  const filterBankRef = useRef(new OneEuroLandmarkBank());
  const activeCharImgRef = useRef<HTMLImageElement | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const recordedChunksRef = useRef<Blob[]>([]);

  const showToastMsg = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(null), 3500);
  };

  // Load saved custom characters from IndexedDB
  useEffect(() => {
    loadCustomCharactersFromDB().then((customs) => {
      if (customs.length > 0) {
        setCharacters([...customs, ...BUILT_IN_CHARACTERS]);
      }
    });
  }, []);

  // Load active character HTMLImageElement
  useEffect(() => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      activeCharImgRef.current = img;
    };
    img.src = selectedChar.imageDataUrl;
  }, [selectedChar]);

  // Initialize MediaPipe Face Landmarker (GPU with CPU fallback)
  useEffect(() => {
    let cancelled = false;
    async function initMediaPipe() {
      try {
        const vision = await FilesetResolver.forVisionTasks(
          'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14/wasm'
        );
        let landmarker: FaceLandmarker;
        try {
          landmarker = await FaceLandmarker.createFromOptions(vision, {
            baseOptions: {
              modelAssetPath:
                'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task',
              delegate: 'GPU'
            },
            outputFaceBlendshapes: true,
            runningMode: 'VIDEO',
            numFaces: 1,
            minFaceDetectionConfidence: 0.35,
            minFacePresenceConfidence: 0.35,
            minTrackingConfidence: 0.35
          });
        } catch {
          landmarker = await FaceLandmarker.createFromOptions(vision, {
            baseOptions: {
              modelAssetPath:
                'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task',
              delegate: 'CPU'
            },
            outputFaceBlendshapes: true,
            runningMode: 'VIDEO',
            numFaces: 1,
            minFaceDetectionConfidence: 0.35,
            minFacePresenceConfidence: 0.35,
            minTrackingConfidence: 0.35
          });
        }
        if (!cancelled) {
          landmarkerRef.current = landmarker;
          setIsLoadingModel(false);
        }
      } catch {
        if (!cancelled) {
          setIsLoadingModel(false);
        }
      }
    }
    initMediaPipe();
    return () => {
      cancelled = true;
    };
  }, []);

  // Open camera via getUserMedia at natural un-zoomed field of view
  useEffect(() => {
    let activeStream: MediaStream | null = null;
    async function startCamera() {
      try {
        setPermissionError(null);
        let stream: MediaStream;
        try {
          // Request natural 4:3 sensor aspect ratio so the camera is not digitally cropped/zoomed
          stream = await navigator.mediaDevices.getUserMedia({
            video: {
              facingMode: settings.facingMode,
              width: { ideal: 1280 },
              height: { ideal: 960 }
            },
            audio: true
          });
        } catch {
          stream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: settings.facingMode },
            audio: false
          });
        }

        // Explicitly reset hardware/digital zoom to minimum (1.0x) if supported by the phone lens
        const videoTrack = stream.getVideoTracks()[0];
        if (videoTrack && typeof videoTrack.getCapabilities === 'function') {
          try {
            const caps = videoTrack.getCapabilities() as Record<string, { min?: number }>;
            if (caps.zoom && typeof caps.zoom.min === 'number') {
              await videoTrack.applyConstraints({
                advanced: [{ zoom: caps.zoom.min } as MediaTrackConstraintSet]
              });
            }
          } catch {
            // Ignore if zoom constraint is unsupported
          }
        }

        activeStream = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          await videoRef.current.play();
        }
      } catch {
        setPermissionError('ক্যামেরা ও মাইক্রোফোন অনুমতি পাওয়া যায়নি / Camera or Microphone permission denied');
      }
    }
    startCamera();
    return () => {
      activeStream?.getTracks().forEach((t) => t.stop());
    };
  }, [settings.facingMode, settings.resolution]);

  // Real-time 478 landmark detection + One Euro smoothing + Mesh/Puppet render loop
  useEffect(() => {
    let animId = 0;
    const renderLoop = () => {
      const canvas = canvasRef.current;
      const video = videoRef.current;
      if (canvas) {
        // Match canvas internal resolution to its exact screen display aspect ratio (prevents CSS double-zoom!)
        const dpr = Math.min(window.devicePixelRatio || 1, 2);
        const targetW = Math.round((canvas.clientWidth || window.innerWidth) * dpr);
        const targetH = Math.round((canvas.clientHeight || window.innerHeight) * dpr);
        if (targetW > 0 && targetH > 0 && (canvas.width !== targetW || canvas.height !== targetH)) {
          canvas.width = targetW;
          canvas.height = targetH;
        }

        const ctx = canvas.getContext('2d');
        if (ctx) {
          const nowMs = Math.max(performance.now(), lastDetectTimestampRef.current + 1);
          lastDetectTimestampRef.current = nowMs;
          const tSec = nowMs / 1000;

          let pts36: Point2D[] = lastTrackedPtsRef.current;
          let faceDetectedNow = false;
          let blendshapes: LiveBlendshapes = {
            jawOpen: 0.05,
            eyeBlinkLeft: 0,
            eyeBlinkRight: 0,
            yaw: 0,
            pitch: 0,
            roll: 0
          };

          if (
            landmarkerRef.current &&
            !isStaticDetectingRef.current &&
            video &&
            video.readyState >= 2 &&
            video.videoWidth > 0
          ) {
            try {
              const res = landmarkerRef.current.detectForVideo(video, nowMs);
              if (res.faceLandmarks && res.faceLandmarks.length > 0) {
                faceDetectedNow = true;
                lastTrackedTimeRef.current = nowMs;
                const lm478 = res.faceLandmarks[0];

                const vw = video.videoWidth;
                const vh = video.videoHeight;
                const cw = canvas.width;
                const ch = canvas.height;
                const { drawX, drawY, drawW, drawH } = computeVideoDrawRect(cw, ch, vw, vh);

                pts36 = MP_TO_36_INDICES.map((mpIdx, i) => {
                  const pt = lm478[mpIdx] || { x: 0.5, y: 0.5 };
                  const rawX = settings.isMirrored ? 1 - pt.x : pt.x;
                  const canvasNormX = (rawX * drawW + drawX) / cw;
                  const canvasNormY = (pt.y * drawH + drawY) / ch;
                  return filterBankRef.current.filterPoint(
                    i,
                    canvasNormX,
                    canvasNormY,
                    settings.smoothingStrength,
                    tSec
                  );
                });
                lastTrackedPtsRef.current = pts36;

                const cats = res.faceBlendshapes?.[0]?.categories || [];
                const findScore = (name: string) => cats.find((c) => c.categoryName === name)?.score || 0;

                const le = pts36[9];
                const re = pts36[15];
                const nose = pts36[18];
                const eyeMidX = (le.x + re.x) * 0.5;
                const eyeDist = Math.max(0.05, Math.hypot(re.x - le.x, re.y - le.y));
                const rollDeg = (Math.atan2(re.y - le.y, re.x - le.x) * 180) / Math.PI;
                const yawDeg = ((nose.x - eyeMidX) / eyeDist) * 45;

                blendshapes = {
                  jawOpen: filterBankRef.current.filterScalar('jaw', findScore('jawOpen'), settings.smoothingStrength, tSec),
                  eyeBlinkLeft: filterBankRef.current.filterScalar('blL', findScore('eyeBlinkLeft'), settings.smoothingStrength, tSec),
                  eyeBlinkRight: filterBankRef.current.filterScalar('blR', findScore('eyeBlinkRight'), settings.smoothingStrength, tSec),
                  yaw: filterBankRef.current.filterScalar('yaw', yawDeg, settings.smoothingStrength, tSec),
                  pitch: 0,
                  roll: filterBankRef.current.filterScalar('roll', rollDeg, settings.smoothingStrength, tSec)
                };
              } else if (nowMs - lastTrackedTimeRef.current < 450) {
                faceDetectedNow = true;
              }
            } catch {
              // Keep last state
            }
          }

          setIsLiveFaceDetected(faceDetectedNow);

          renderCompositeCanvasFrame(
            ctx,
            canvas.width,
            canvas.height,
            video,
            activeCharImgRef.current,
            selectedChar,
            faceDetectedNow,
            pts36,
            blendshapes,
            settings.blendOpacity,
            settings.isMirrored,
            settings.detailedMouthInterior,
            settings.showWireframe
          );
        }
      }
      animId = requestAnimationFrame(renderLoop);
    };
    animId = requestAnimationFrame(renderLoop);
    return () => cancelAnimationFrame(animId);
  }, [selectedChar, settings]);

  // Handle Custom Image Upload: First try MediaPipe face detection; if it fails, open Manual Setup!
  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = '';

    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = reader.result as string;
      const img = new Image();
      img.onload = async () => {
        const landmarker = landmarkerRef.current;
        if (landmarker) {
          try {
            isStaticDetectingRef.current = true;
            await landmarker.setOptions({ runningMode: 'IMAGE' });
            const detection = landmarker.detect(img);
            await landmarker.setOptions({ runningMode: 'VIDEO' });
            isStaticDetectingRef.current = false;

            if (detection.faceLandmarks && detection.faceLandmarks.length > 0) {
              const lm478 = detection.faceLandmarks[0];
              const cutout = createFaceCutoutFromLandmarks(img, lm478, MP_TO_36_INDICES);
              const customCount = characters.filter((c) => c.isCustom).length + 1;
              const newChar: CharacterItem = {
                id: `custom_${Date.now()}`,
                nameEn: `Custom Face #${customCount}`,
                nameBn: `কাস্টম মুখ #${customCount}`,
                category: 'CUSTOM',
                rigType: 'AUTO_MESH',
                imageDataUrl: cutout.dataUrl,
                landmarks: cutout.landmarks36,
                puppetConfig: DEFAULT_PUPPET_CONFIG,
                isCustom: true
              };
              await saveCustomCharacterToDB(newChar);
              setCharacters((prev) => [newChar, ...prev]);
              setSelectedChar(newChar);
              showToastMsg('মানুষের মুখ শনাক্ত হয়েছে! ৪৭৮ মেস রেডি / Face detected & background cutout ready!');
              return;
            }
          } catch {
            isStaticDetectingRef.current = false;
            try {
              await landmarker.setOptions({ runningMode: 'VIDEO' });
            } catch {
              // ignore
            }
          }
        }
        // No human face detected -> open Manual Setup Editor for Cartoon / Animal
        showToastMsg('মানুষের মুখ পাওয়া যায়নি — কার্টুন/প্রাণীর জন্য ম্যানুয়াল সেটআপ খুলছে');
        setManualUploadDataUrl(dataUrl);
      };
      img.src = dataUrl;
    };
    reader.readAsDataURL(file);
  };

  const handleSaveManualCharacter = async (nameEn: string, nameBn: string, config: PuppetConfig) => {
    if (!manualUploadDataUrl) return;
    const item: CharacterItem = {
      id: `custom_${Date.now()}`,
      nameEn,
      nameBn,
      category: 'CUSTOM',
      rigType: 'MANUAL_PUPPET',
      imageDataUrl: manualUploadDataUrl,
      puppetConfig: config,
      isCustom: true
    };
    await saveCustomCharacterToDB(item);
    setCharacters((prev) => [item, ...prev]);
    setSelectedChar(item);
    setManualUploadDataUrl(null);
    showToastMsg('ক্যারেক্টার সেভ হয়েছে! / Saved to IndexedDB!');
  };

  // Photo Capture
  const handleCapturePhoto = async () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const dataUrl = canvas.toDataURL('image/jpeg', 0.94);
    const fileName = `FaceSwap_${Date.now()}.jpg`;
    try {
      await Filesystem.writeFile({
        path: fileName,
        data: dataUrl.split(',')[1],
        directory: Directory.Documents
      });
      showToastMsg(`ছবি সেভ হয়েছে: ${fileName}`);
    } catch {
      const a = document.createElement('a');
      a.href = dataUrl;
      a.download = fileName;
      a.click();
      showToastMsg('ছবি ডাউনলোড হয়েছে / Photo downloaded!');
    }
  };

  // Video + Mic Audio Recording via MediaRecorder + @capacitor/filesystem
  const handleToggleRecord = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    if (isRecording) {
      mediaRecorderRef.current?.stop();
      setIsRecording(false);
      return;
    }

    recordedChunksRef.current = [];
    const canvasStream = canvas.captureStream(30);
    const videoStream = videoRef.current?.srcObject as MediaStream | null;
    if (videoStream) {
      videoStream.getAudioTracks().forEach((track) => canvasStream.addTrack(track));
    }

    const recorder = new MediaRecorder(canvasStream);
    recorder.ondataavailable = (ev) => {
      if (ev.data.size > 0) recordedChunksRef.current.push(ev.data);
    };
    recorder.onstop = async () => {
      const blob = new Blob(recordedChunksRef.current, { type: 'video/webm' });
      const fileName = `FaceSwap_Video_${Date.now()}.webm`;
      try {
        const reader = new FileReader();
        reader.onloadend = async () => {
          const base64 = (reader.result as string).split(',')[1];
          await Filesystem.writeFile({
            path: fileName,
            data: base64,
            directory: Directory.Documents
          });
          showToastMsg(`ভিডিও সেভ হয়েছে / Saved via Capacitor: ${fileName}`);
        };
        reader.readAsDataURL(blob);
      } catch {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        a.click();
        showToastMsg('ভিডিও ডাউনলোড হয়েছে / Video saved!');
      }
    };
    recorder.start();
    mediaRecorderRef.current = recorder;
    setIsRecording(true);
    showToastMsg('রেকর্ডিং শুরু / Recording video + audio...');
  };

  const filteredChars =
    selectedCategory === 'ALL'
      ? characters
      : characters.filter((c) => c.category === selectedCategory);

  return (
    <div style={{ position: 'relative', width: '100vw', height: '100vh', background: '#090A0F', overflow: 'hidden' }}>
      {/* Do NOT use display:none on video in Android WebView or frame decoding pauses */}
      <video
        ref={videoRef}
        playsInline
        muted
        autoPlay
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: 2,
          height: 2,
          opacity: 0.001,
          pointerEvents: 'none'
        }}
      />
      <canvas
        ref={canvasRef}
        width={settings.resolution === '1080p' ? 1080 : 720}
        height={settings.resolution === '1080p' ? 1920 : 1280}
        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
      />

      <input
        ref={fileInputRef}
        type="file"
        hidden
        accept="image/png,image/jpeg,image/webp"
        onChange={handleFileUpload}
        style={{ position: 'absolute', width: 0, height: 0, opacity: 0, pointerEvents: 'none' }}
      />

      {/* Top HUD */}
      <div style={{
        position: 'absolute', top: 14, left: 14, right: 14,
        display: 'flex', justifyContent: 'space-between', alignItems: 'center'
      }}>
        <div style={{
          background: 'rgba(20,23,34,0.85)', padding: '8px 14px', borderRadius: 20,
          border: '1px solid #2A314A', fontSize: 13
        }}>
          <div style={{ fontWeight: 700 }}>FaceSwap Cam • ফেসসোয়াপ ক্যাম</div>
          <div style={{ fontSize: 11, color: isLiveFaceDetected ? '#10B981' : '#F59E0B', marginTop: 2 }}>
            {isLiveFaceDetected ? '● Tracking • ট্র্যাকিং' : '● মুখ খুঁজছে... • Point at Face'}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button
            onClick={() =>
              setSettings((prev) => ({
                ...prev,
                facingMode: prev.facingMode === 'user' ? 'environment' : 'user',
                isMirrored: prev.facingMode !== 'user'
              }))
            }
            style={{ background: 'rgba(20,23,34,0.85)', color: '#F8FAFC', border: '1px solid #2A314A', padding: '8px 12px', borderRadius: 16 }}
          >
            Flip
          </button>
          <button
            onClick={() => setShowGuide(true)}
            style={{ background: 'rgba(20,23,34,0.85)', color: '#F8FAFC', border: '1px solid #2A314A', padding: '8px 12px', borderRadius: 16 }}
          >
            গাইড / Guide
          </button>
          <button
            onClick={() => setShowSettings(true)}
            style={{ background: 'rgba(20,23,34,0.85)', color: '#F8FAFC', border: '1px solid #2A314A', padding: '8px 12px', borderRadius: 16 }}
          >
            সেটিংস / Settings
          </button>
        </div>
      </div>

      {isLoadingModel && (
        <div style={{
          position: 'absolute', top: '42%', left: '50%', transform: 'translate(-50%, -50%)',
          background: 'rgba(20,23,34,0.92)', padding: '16px 22px', borderRadius: 18, border: '1px solid #00E5FF',
          textAlign: 'center'
        }}>
          ৪৭৮ ফেস ল্যান্ডমার্ক মডেল লোড হচ্ছে... / Loading MediaPipe Face Landmarker...
        </div>
      )}

      {permissionError && (
        <div style={{
          position: 'absolute', top: '25%', left: 20, right: 20,
          background: 'rgba(27,32,50,0.95)', padding: 18, borderRadius: 18, border: '1px solid #FF2E54',
          textAlign: 'center'
        }}>
          {permissionError}
        </div>
      )}

      {toast && (
        <div style={{
          position: 'absolute', top: 76, left: '50%', transform: 'translateX(-50%)',
          background: 'rgba(20,23,34,0.95)', padding: '8px 16px', borderRadius: 14, border: '1px solid #00E5FF',
          fontSize: 12, textAlign: 'center', maxWidth: '88vw'
        }}>
          {toast}
        </div>
      )}

      {/* Bottom Dock */}
      <div style={{
        position: 'absolute', bottom: 0, left: 0, right: 0,
        background: 'rgba(20,23,34,0.88)', borderTopLeftRadius: 24, borderTopRightRadius: 24,
        padding: '12px 12px 18px', borderTop: '1px solid #2A314A'
      }}>
        {/* Category Chips */}
        <div style={{ display: 'flex', gap: 6, overflowX: 'auto', marginBottom: 10 }}>
          {(['ALL', 'REALISTIC', 'CARTOON', 'ANIMAL', 'CUSTOM'] as CharacterCategory[]).map((cat) => (
            <button
              key={cat}
              onClick={() => setSelectedCategory(cat)}
              style={{
                padding: '5px 11px',
                borderRadius: 14,
                border: 'none',
                background: selectedCategory === cat ? '#00E5FF' : '#1B2032',
                color: selectedCategory === cat ? '#090A0F' : '#F8FAFC',
                fontWeight: 700,
                fontSize: 11,
                cursor: 'pointer'
              }}
            >
              {cat}
            </button>
          ))}
        </div>

        {/* Character Carousel */}
        <div style={{ display: 'flex', gap: 10, overflowX: 'auto', marginBottom: 12 }}>
          <button
            onClick={() => fileInputRef.current?.click()}
            style={{
              minWidth: 72, padding: 8, borderRadius: 14, border: '1.5px dashed #00E5FF',
              background: '#1B2032', color: '#00E5FF', fontSize: 11, fontWeight: 700, cursor: 'pointer'
            }}
          >
            + ছবি মুখ<br />Upload Face
          </button>
          <button
            onClick={() => setShowVideoSwap(true)}
            style={{
              minWidth: 76, padding: 8, borderRadius: 14, border: '1.5px solid #A855F7',
              background: '#1B2032', color: '#F8FAFC', fontSize: 11, fontWeight: 700, cursor: 'pointer'
            }}
          >
            🎬 ভিডিও সোয়াপ<br />Video Swap
          </button>
          {filteredChars.map((c) => (
            <button
              key={c.id}
              onClick={() => setSelectedChar(c)}
              style={{
                minWidth: 74, padding: 6, borderRadius: 14,
                border: selectedChar.id === c.id ? '2px solid #00E5FF' : '1px solid #2A314A',
                background: selectedChar.id === c.id ? 'rgba(0,229,255,0.16)' : '#1B2032',
                color: '#F8FAFC', fontSize: 10, cursor: 'pointer'
              }}
            >
              <img src={c.imageDataUrl} alt={c.nameEn} style={{ width: 42, height: 42, borderRadius: '50%', objectFit: 'cover' }} />
              <div style={{ marginTop: 4, fontWeight: 600 }}>{c.nameBn}</div>
            </button>
          ))}
        </div>

        {/* Photo & Video Record Controls */}
        <div style={{ display: 'flex', justifyContent: 'space-around', alignItems: 'center' }}>
          <button
            onClick={handleCapturePhoto}
            style={{
              padding: '10px 16px', borderRadius: 20, border: '1.5px solid #00E5FF',
              background: '#1B2032', color: '#00E5FF', fontWeight: 700, cursor: 'pointer'
            }}
          >
            ছবি / Photo
          </button>

          <button
            onClick={handleToggleRecord}
            style={{
              width: 64, height: 64, borderRadius: '50%',
              border: '3px solid #F8FAFC',
              background: isRecording ? '#991B1B' : '#FF2E54',
              color: '#FFFFFF', fontWeight: 700, fontSize: 11, cursor: 'pointer'
            }}
          >
            {isRecording ? 'STOP' : 'REC'}
          </button>

          <button
            onClick={() => setShowVideoSwap(true)}
            style={{
              padding: '10px 14px', borderRadius: 20, border: '1.5px solid #A855F7',
              background: '#1B2032', color: '#F8FAFC', fontWeight: 700, fontSize: 11, cursor: 'pointer'
            }}
          >
            ভিডিও সোয়াপ / Video Swap
          </button>
        </div>
      </div>

      {manualUploadDataUrl && (
        <ManualSetupModal
          imageDataUrl={manualUploadDataUrl}
          initialConfig={selectedChar.puppetConfig}
          onCancel={() => setManualUploadDataUrl(null)}
          onSave={handleSaveManualCharacter}
        />
      )}

      {showSettings && (
        <SettingsModal
          settings={settings}
          onUpdate={(next) => setSettings((prev) => ({ ...prev, ...next }))}
          onClose={() => setShowSettings(false)}
        />
      )}

      {showGuide && <GuideModal onClose={() => setShowGuide(false)} />}

      {showVideoSwap && (
        <VideoSwapModal
          characters={characters}
          defaultCharacterId={selectedChar.id}
          settings={settings}
          landmarker={landmarkerRef.current}
          mpTo36Indices={MP_TO_36_INDICES}
          onUploadCustomFace={() => fileInputRef.current?.click()}
          onShowToast={showToastMsg}
          onClose={() => setShowVideoSwap(false)}
        />
      )}
    </div>
  );
};
