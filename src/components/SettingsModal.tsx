import React from 'react';
import { StudioSettings } from '../types';

interface Props {
  settings: StudioSettings;
  onUpdate: (next: Partial<StudioSettings>) => void;
  onClose: () => void;
}

export const SettingsModal: React.FC<Props> = ({ settings, onUpdate, onClose }) => {
  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.65)', zIndex: 90,
      display: 'flex', alignItems: 'flex-end'
    }}>
      <div style={{
        width: '100%', background: '#121520', borderTopLeftRadius: 24, borderTopRightRadius: 24,
        padding: 20, borderTop: '1px solid #2A314A', maxHeight: '85vh', overflowY: 'auto'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <h3 style={{ fontSize: 18, fontWeight: 700 }}>সেটিংস / Studio Settings</h3>
          <button onClick={onClose} style={{ background: '#1B2032', color: '#F8FAFC', border: 'none', padding: '6px 12px', borderRadius: 10 }}>
            বন্ধ / Close
          </button>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ background: '#1B2032', padding: 12, borderRadius: 14, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>ক্যামেরা পরিবর্তন / Camera Flip ({settings.facingMode === 'user' ? 'Front' : 'Rear'})</span>
            <button
              onClick={() => onUpdate({ facingMode: settings.facingMode === 'user' ? 'environment' : 'user' })}
              style={{ background: '#00E5FF', color: '#090A0F', border: 'none', padding: '8px 14px', borderRadius: 10, fontWeight: 700 }}
            >
              Flip Camera
            </button>
          </div>

          <div style={{ background: '#1B2032', padding: 12, borderRadius: 14 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
              <span>স্মুথিং (One Euro Filter) / Smoothing</span>
              <strong style={{ color: '#00E5FF' }}>{Math.round(settings.smoothingStrength * 100)}%</strong>
            </div>
            <input
              type="range"
              min={0}
              max={1}
              step={0.05}
              value={settings.smoothingStrength}
              onChange={(e) => onUpdate({ smoothingStrength: parseFloat(e.target.value) })}
              style={{ width: '100%' }}
            />
          </div>

          <div style={{ background: '#1B2032', padding: 12, borderRadius: 14 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
              <span>ব্লেন্ড ওপাসিটি / Blend Opacity</span>
              <strong style={{ color: '#00E5FF' }}>{Math.round(settings.blendOpacity * 100)}%</strong>
            </div>
            <input
              type="range"
              min={0.15}
              max={1}
              step={0.05}
              value={settings.blendOpacity}
              onChange={(e) => onUpdate({ blendOpacity: parseFloat(e.target.value) })}
              style={{ width: '100%' }}
            />
          </div>

          <div style={{ background: '#1B2032', padding: 12, borderRadius: 14, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>মুখের ভেতরের দাঁত ও জিহ্বা / Detailed Mouth Interior</span>
            <input
              type="checkbox"
              checked={settings.detailedMouthInterior}
              onChange={(e) => onUpdate({ detailedMouthInterior: e.target.checked })}
            />
          </div>

          <div style={{ background: '#1B2032', padding: 12, borderRadius: 14, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>মিরর ভিউ / Mirror Toggle</span>
            <input
              type="checkbox"
              checked={settings.isMirrored}
              onChange={(e) => onUpdate({ isMirrored: e.target.checked })}
            />
          </div>

          <div style={{ background: '#1B2032', padding: 12, borderRadius: 14, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>রেজোলিউশন / Resolution</span>
            <select
              value={settings.resolution}
              onChange={(e) => onUpdate({ resolution: e.target.value as '720p' | '1080p' })}
              style={{ background: '#090A0F', color: '#F8FAFC', padding: '6px 10px', borderRadius: 8, border: '1px solid #2A314A' }}
            >
              <option value="720p">720p HD</option>
              <option value="1080p">1080p FHD</option>
            </select>
          </div>
        </div>
      </div>
    </div>
  );
};
