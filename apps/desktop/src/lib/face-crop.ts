export type FaceCropBox = { x: number; y: number; width: number; height: number }

type FaceDetectionLike = {
	boundingBox?: { x?: number; y?: number; width?: number; height?: number }
}

type FaceDetectorLike = {
	detect(input: ImageBitmapSource): Promise<FaceDetectionLike[]>
}

type FaceDetectorCtor = new (...args: any[]) => FaceDetectorLike

const SESSION_PREFIX = 'face-crop:'

function clampPercent(value: number): number {
	if (!Number.isFinite(value)) return 0
	return Math.max(0, Math.min(100, value))
}

export function faceCropToObjectPosition(crop: FaceCropBox): string {
	const cx = clampPercent(crop.x + crop.width / 2)
	const cy = clampPercent(crop.y + crop.height / 2)
	return `${cx}% ${cy}%`
}

async function loadImage(url: string): Promise<HTMLImageElement> {
	const image = new Image()
	image.crossOrigin = 'anonymous'
	image.decoding = 'async'
	image.src = url
	await image.decode()
	return image
}

export async function detectFaceCrop(imageUrl: string): Promise<FaceCropBox | null> {
	// FaceDetector is currently Chromium-only; Safari/Firefox should gracefully return null.
	if (typeof window === 'undefined' || !imageUrl) return null
	const ctor = (window as unknown as { FaceDetector?: FaceDetectorCtor }).FaceDetector
	if (!ctor) return null

	try {
		const image = await loadImage(imageUrl)
		const detector = new ctor()
		const detections = await detector.detect(image)
		if (!detections.length) return null

		const largest = detections
			.map((d) => d.boundingBox ?? {})
			.map((box) => ({
				x: Number(box.x ?? 0),
				y: Number(box.y ?? 0),
				width: Number(box.width ?? 0),
				height: Number(box.height ?? 0),
			}))
			.filter((box) => box.width > 0 && box.height > 0)
			.sort((a, b) => b.width * b.height - a.width * a.height)[0]
		if (!largest) return null

		const width = image.naturalWidth || image.width || 1
		const height = image.naturalHeight || image.height || 1
		return {
			x: clampPercent((largest.x / width) * 100),
			y: clampPercent((largest.y / height) * 100),
			width: clampPercent((largest.width / width) * 100),
			height: clampPercent((largest.height / height) * 100),
		}
	} catch {
		return null
	}
}

export async function getCachedFaceCrop(
	mediaId: number,
	imageUrl: string
): Promise<FaceCropBox | null> {
	if (typeof window === 'undefined' || !mediaId || !imageUrl) return null
	const key = `${SESSION_PREFIX}${mediaId}`
	try {
		const cached = window.sessionStorage.getItem(key)
		if (cached) {
			const parsed = JSON.parse(cached) as FaceCropBox
			if (
				Number.isFinite(parsed.x) &&
				Number.isFinite(parsed.y) &&
				Number.isFinite(parsed.width) &&
				Number.isFinite(parsed.height)
			)
				return parsed
		}
	} catch {
		// Ignore corrupt cache values
	}

	const crop = await detectFaceCrop(imageUrl)
	if (!crop) return null
	try {
		window.sessionStorage.setItem(key, JSON.stringify(crop))
	} catch {
		// Ignore unavailable storage
	}
	return crop
}
