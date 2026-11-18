import React, { useState, useRef } from 'react';
import { PuppetConfig } from '../types';
import { DEFAULT_PUPPET_CONFIG } from '../utils/demoCharacters';

interface Props {
  imageDataUrl: string;
  initialConfig?: PuppetConfig;
  onCancel: () => void;
  onSave: (nameEn: string, nameBn: string, config: PuppetConfig) => void;
}

const PIN_LABELS = [
  { key: 'leftEye', label: '1. বাম চোখ / Left Eye', color: '#00E5FF' },
  { key: 'rightEye', label: '2. ডান চোখ / Right Eye', color: '#00E5FF' },
  { key: 'noseTip', label: '3. নাক / Nose', color: '#10B981' },
  { key: 'mouthLeft', label: '4. মুখের বাম / Mouth L', color: '#F59E0B' },
  { key: 'mouthRight', label: '5. মুখের ডান / Mouth R', color: '#FF2E54' }
] as const;

export const ManualSetupModal: React.FC<Props> = ({
  imageDataUrl,
  initialConfig = DEFAULT_PUPPET_CONFIG,
  onCancel,
  onSave
}) => {
  const [config, setConfig] = useState<PuppetConfig>(initialConfig);
  const [activePin, setActivePin] = useState<number>(0);
  const [nameBn, setNameBn] = useState('কার্টুন/প্রাণী');
  const [nameEn, setNameEn] = useState('Custom Puppet');
  const containerRef = useRef<HTMLDivElement | null>(null);

  const handleCanvasClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const x = Math.min(0.95, Math.max(0.05, (e.clientX - rect.left) / rect.width));
    const y = Math.min(0.95, Math.max(0.05, (e.clientY - rect.top) / rect.height));

    const next = { ...config };
    if (activePin === 0) {
      next.leftEye = { x, y };
      next.leftEyeBox = { left: x - 0.09, top: y - 0.06, right: x + 0.09, bottom: y + 0.06 };
    } else if (activePin === 1) {
      next.rightEye = { x, y };
      next.rightEyeBox = { left: x - 0.09, top: y - 0.06, right: x + 0.09, bottom: y + 0.06 };
    } else if (activePin === 2) {
      next.noseTip = { x, y };
    } else if (activePin === 3) {
      next.mouthLeft = { x, y };
      const cy = (y + next.mouthRight.y) * 0.5;
      next.mouthBox = {
        left: Math.min(x, next.mouthRight.x) - 0.03,
        top: cy - 0.07,
        right: Math.max(x, next.mouthRight.x) + 0.03,
        bottom: cy + 0.08
      };
    } else {
      next.mouthRight = { x, y };
      const cy = (next.mouthLeft.y + y) * 0.5;
      next.mouthBox = {
        left: Math.min(next.mouthLeft.x, x) - 0.03,
        top: cy - 0.07,
        right: Math.max(next.mouthLeft.x, x) + 0.03,
        bottom: cy + 0.08
      };
    }
    setConfig(next);
    setActivePin((activePin + 1) % 5);
  };

  const pins = [config.leftEye, config.rightEye, config.noseTip, config.mouthLeft, config.mouthRight];

  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(9,10,15,0.96)', zIndex: 100,
      display: 'flex', flexDirection: 'column', padding: 16, overflowY: 'auto'
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <div>
          <h3 style={{ fontSize: 18, fontWeight: 700 }}>ম্যানুয়াল সেটআপ / Manual Puppet Setup</h3>
          <p style={{ fontSize: 12, color: '#94A3B8' }}>
            মানুষের মুখ পাওয়া যায়নি — ৫টি পয়েন্ট ট্যাপ করুন / Tap 5 points for Cartoon & Animal face
          </p>
        </div>
        <button onClick={onCancel} style={{ background: '#1B2032', color: '#F8FAFC', border: 'none', padding: '8px 12px', borderRadius: 10 }}>
          বাতিল / Close
        </button>
      </div>

      <div style={{ display: 'flex', gap: 6, marginBottom: 10, flexWrap: 'wrap' }}>
        {PIN_LABELS.map((p, idx) => (
          <button
            key={p.key}
            onClick={() => setActivePin(idx)}
            style={{
              flex: 1,
              minWidth: 90,
              padding: '6px 8px',
              borderRadius: 10,
              border: `1.5px solid ${activePin === idx ? p.color : '#2A314A'}`,
              background: activePin === idx ? 'rgba(0,229,255,0.16)' : '#1B2032',
              color: '#F8FAFC',
              fontSize: 11,
              cursor: 'pointer'
            }}
          >
            {p.label}
          </button>
        ))}
      </div>

      <div
        ref={containerRef}
        onClick={handleCanvasClick}
        style={{
          position: 'relative',
          width: '100%',
          maxWidth: 380,
          aspectRatio: '1 / 1',
          margin: '0 auto 14px',
          borderRadius: 16,
          overflow: 'hidden',
          border: '1.5px solid #2A314A',
          background: 'repeating-conic-gradient(#141824 0% 25%, #1E2436 0% 50%) 50% / 24px 24px',
          cursor: 'crosshair'
        }}
      >
        <img src={imageDataUrl} alt="Upload preview" style={{ width: '100%', height: '100%', objectFit: 'contain' }} />

        {[
          { box: config.leftEyeBox, color: '#00E5FF' },
          { box: config.rightEyeBox, color: '#00E5FF' },
          { box: config.mouthBox, color: '#FF2E54' }
        ].map((item, i) => (
          <div
            key={i}
            style={{
              position: 'absolute',
              left: `${item.box.left * 100}%`,
              top: `${item.box.top * 100}%`,
              width: `${(item.box.right - item.box.left) * 100}%`,
              height: `${(item.box.bottom - item.box.top) * 100}%`,
              border: `2px dashed ${item.color}`,
              background: 'rgba(0, 229, 255, 0.12)',
              pointerEvents: 'none'
            }}
          />
        ))}

        {pins.map((pt, idx) => (
          <div
            key={idx}
            style={{
              position: 'absolute',
              left: `calc(${pt.x * 100}% - 10px)`,
              top: `calc(${pt.y * 100}% - 10px)`,
              width: 20,
              height: 20,
              borderRadius: '50%',
              background: PIN_LABELS[idx].color,
              border: '2px solid #090A0F',
              color: '#090A0F',
              fontSize: 10,
              fontWeight: 700,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              pointerEvents: 'none'
            }}
          >
            {idx + 1}
          </div>
        ))}
      </div>

      <div style={{ display: 'flex', gap: 10, marginBottom: 14 }}>
        <input
          value={nameBn}
          onChange={(e) => setNameBn(e.target.value)}
          placeholder="বাংলা নাম"
          style={{ flex: 1, padding: 10, borderRadius: 10, border: '1px solid #2A314A', background: '#1B2032', color: '#F8FAFC' }}
        />
        <input
          value={nameEn}
          onChange={(e) => setNameEn(e.target.value)}
          placeholder="English Name"
          style={{ flex: 1, padding: 10, borderRadius: 10, border: '1px solid #2A314A', background: '#1B2032', color: '#F8FAFC' }}
        />
      </div>

      <button
        onClick={() => onSave(nameEn, nameBn, config)}
        style={{
          padding: 14,
          borderRadius: 14,
          border: 'none',
          background: '#00E5FF',
          color: '#090A0F',
          fontWeight: 700,
          fontSize: 15,
          cursor: 'pointer'
        }}
      >
        সেভ করুন / Save Character to IndexedDB
      </button>
    </div>
  );
};
