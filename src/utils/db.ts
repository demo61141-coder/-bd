import { CharacterItem } from '../types';

const DB_NAME = 'faceswap_cam_db';
const DB_VERSION = 1;
const STORE_CHARACTERS = 'custom_characters';

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_CHARACTERS)) {
        db.createObjectStore(STORE_CHARACTERS, { keyPath: 'id' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

export async function loadCustomCharactersFromDB(): Promise<CharacterItem[]> {
  try {
    const db = await openDatabase();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_CHARACTERS, 'readonly');
      const store = tx.objectStore(STORE_CHARACTERS);
      const req = store.getAll();
      req.onsuccess = () => resolve((req.result as CharacterItem[]) || []);
      req.onerror = () => reject(req.error);
    });
  } catch {
    return [];
  }
}

export async function saveCustomCharacterToDB(item: CharacterItem): Promise<void> {
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_CHARACTERS, 'readwrite');
    const store = tx.objectStore(STORE_CHARACTERS);
    store.put(item);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export async function deleteCustomCharacterFromDB(id: string): Promise<void> {
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_CHARACTERS, 'readwrite');
    const store = tx.objectStore(STORE_CHARACTERS);
    store.delete(id);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}
