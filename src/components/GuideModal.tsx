import React from 'react';

interface Props {
  onClose: () => void;
}

export const GuideModal: React.FC<Props> = ({ onClose }) => {
  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.65)', zIndex: 90,
      display: 'flex', alignItems: 'flex-end'
    }}>
      <div style={{
        width: '100%', background: '#121520', borderTopLeftRadius: 24, borderTopRightRadius: 24,
        padding: 20, borderTop: '1px solid #2A314A', maxHeight: '85vh', overflowY: 'auto'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
          <h3 style={{ fontSize: 18, fontWeight: 700 }}>নির্দেশিকা / Creator Face Guide</h3>
          <button onClick={onClose} style={{ background: '#1B2032', color: '#F8FAFC', border: 'none', padding: '6px 12px', borderRadius: 10 }}>
            বন্ধ / Close
          </button>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, fontSize: 13, lineHeight: 1.5, color: '#CBD5E1' }}>
          <div style={{ background: '#1B2032', padding: 14, borderRadius: 14, borderLeft: '4px solid #10B981' }}>
            <strong style={{ color: '#F8FAFC', display: 'block', marginBottom: 4 }}>
              ১. বাস্তব মানুষের ছবি (Realistic Faces — What Works Best)
            </strong>
            <p>• সোজা সামনে তাকানো ছবি (Front-facing), পরিষ্কার আলো (clear lighting), সানগ্লাস ছাড়া (no sunglasses)।</p>
          </div>

          <div style={{ background: '#1B2032', padding: 14, borderRadius: 14, borderLeft: '4px solid #FF2E54' }}>
            <strong style={{ color: '#F8FAFC', display: 'block', marginBottom: 4 }}>
              ২. যা কাজ করে না (What Doesn't Work)
            </strong>
            <p>• একপাশ থেকে তোলা ছবি (side profiles), অন্ধকার ছায়া বা হাত দিয়ে চোখ/মুখ ঢাকা ছবি।</p>
          </div>

          <div style={{ background: '#1B2032', padding: 14, borderRadius: 14, borderLeft: '4px solid #A855F7' }}>
            <strong style={{ color: '#F8FAFC', display: 'block', marginBottom: 4 }}>
              ৩. কার্টুন ও প্রাণীর ছবি (Cartoon & Animal 2D Puppet Tips)
            </strong>
            <p>• সোজা সামনে তাকানো কার্টুন বা প্রাণীর মুখ, যেখানে দুই চোখ ও মুখ স্পষ্ট দেখা যায়। আপলোডের পর ৫টি পয়েন্ট ও বক্স সেট করুন।</p>
          </div>

          <div style={{ background: '#1B2032', padding: 14, borderRadius: 14, borderLeft: '4px solid #F59E0B' }}>
            <strong style={{ color: '#F8FAFC', display: 'block', marginBottom: 4 }}>
              ৪. ব্যাকগ্রাউন্ড রিমুভ টিপস (Transparent PNG & Remove Background Hint)
            </strong>
            <p>• স্বচ্ছ ব্যাকগ্রাউন্ড (Transparent PNG) ব্যবহার করলে সবচেয়ে ভালো দেখায়। আপলোডের আগে ফোনের Background Remover দিয়ে ব্যাকগ্রাউন্ড মুছে PNG সেভ করুন।</p>
          </div>
        </div>
      </div>
    </div>
  );
};
