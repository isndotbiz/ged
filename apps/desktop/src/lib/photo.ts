import { isTauri } from './platform';

async function tauriInvoke<T>(cmd: string, args: Record<string, unknown>): Promise<T> {
  const { invoke } = await import('@tauri-apps/api/core');
  return invoke<T>(cmd, args);
}

async function tauriConvertFileSrc(path: string): Promise<string> {
  const { convertFileSrc } = await import('@tauri-apps/api/core');
  return convertFileSrc(path);
}

/**
 * Get a thumbnail URL for display in tree cards.
 * Uses asset protocol for speed. Falls back to base64.
 */
export async function getThumbUrl(filePath: string, size: number = 96): Promise<string> {
  if (!filePath || !isTauri()) return '';
  try {
    const thumbPath = await tauriInvoke<string>('generate_thumbnail', { path: filePath, size });
    return await tauriConvertFileSrc(thumbPath);
  } catch {
    try {
      return await tauriInvoke<string>('read_image_base64', { path: filePath });
    } catch {
      return '';
    }
  }
}

/**
 * Generate a custom-cropped face thumbnail via Rust.
 * Returns base64 data URL of the ACTUAL cropped file.
 */
export async function cropFace(
  filePath: string,
  cropX: number,
  cropY: number,
  cropZoom: number,
  size: number = 200
): Promise<string> {
  if (!filePath || !isTauri()) return '';
  const thumbPath = await tauriInvoke<string>('crop_face', {
    path: filePath, cropX, cropY, cropZoom, size
  });
  return await tauriInvoke<string>('read_image_base64', { path: thumbPath });
}

/**
 * Save a crop: generates final thumbnails at both sizes.
 */
export async function saveFaceCrop(
  filePath: string,
  cropX: number,
  cropY: number,
  cropZoom: number,
): Promise<string> {
  if (!filePath || !isTauri()) return '';
  const path96 = await tauriInvoke<string>('crop_face', {
    path: filePath, cropX, cropY, cropZoom, size: 96
  });
  await tauriInvoke<string>('crop_face', {
    path: filePath, cropX, cropY, cropZoom, size: 200
  });
  return path96;
}

/**
 * Clear cached thumbnails for a file.
 */
export async function clearCache(filePath: string): Promise<void> {
  if (!filePath || !isTauri()) return;
  try { await tauriInvoke('clear_thumb_cache', { path: filePath }); } catch {}
}

/**
 * Get full image as base64 data URL (for crop tool).
 */
export async function getFullImageBase64(filePath: string): Promise<string> {
  if (!filePath || !isTauri()) return '';
  try {
    return await tauriInvoke<string>('read_image_base64', { path: filePath });
  } catch {
    return '';
  }
}
