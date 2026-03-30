use std::path::PathBuf;
use tauri::Manager;
use image::GenericImageView;
use serde::{Deserialize, Serialize};

// ============================================================
// Thumbnail generation with custom crop support
// ============================================================

/// Generate a default face-focused thumbnail (top-center crop).
/// Returns the absolute path to the cached thumbnail file.
#[tauri::command]
async fn generate_thumbnail(
    app: tauri::AppHandle,
    path: String,
    size: u32,
) -> Result<String, String> {
    let src = PathBuf::from(&path);
    if !src.exists() { return Err(format!("File not found: {}", path)); }

    let cache_dir = get_thumb_dir(&app)?;
    let hash = path_hash(&path);
    let thumb_path = cache_dir.join(format!("{}_{}.jpg", hash, size));

    // Return cached
    if thumb_path.exists() {
        return Ok(thumb_path.to_string_lossy().to_string());
    }

    let img = image::open(&src).map_err(|e| e.to_string())?;
    let (w, h) = img.dimensions();

    // Default face crop: square from top-center
    let crop_size = w.min(h);
    let crop_x = w.saturating_sub(crop_size) / 2;
    let crop_y = if h > w { (h - crop_size) / 5 } else { 0 };

    let cropped = img.crop_imm(crop_x, crop_y, crop_size, crop_size);
    let thumb = cropped.resize_exact(size, size, image::imageops::FilterType::Lanczos3);
    thumb.save(&thumb_path).map_err(|e| e.to_string())?;

    Ok(thumb_path.to_string_lossy().to_string())
}

/// Generate a CUSTOM-CROPPED thumbnail at specified position and zoom.
/// This is called when the user picks a face in the crop tool.
/// crop_x, crop_y: 0-100 (percentage position on the image)
/// crop_zoom: 1.0-5.0 (zoom level)
/// Returns the absolute path to the cropped thumbnail.
#[tauri::command]
async fn crop_face(
    app: tauri::AppHandle,
    path: String,
    crop_x: f64,
    crop_y: f64,
    crop_zoom: f64,
    size: u32,
) -> Result<String, String> {
    let src = PathBuf::from(&path);
    if !src.exists() { return Err(format!("File not found: {}", path)); }

    let cache_dir = get_thumb_dir(&app)?;
    // Unique hash for this specific crop
    let hash = format!("{}_{}_{}_{}",
        path_hash(&path),
        (crop_x * 10.0) as u32,
        (crop_y * 10.0) as u32,
        (crop_zoom * 10.0) as u32
    );
    let thumb_path = cache_dir.join(format!("{}_crop_{}.jpg", hash, size));

    // Return cached crop if it exists
    if thumb_path.exists() {
        return Ok(thumb_path.to_string_lossy().to_string());
    }

    let img = image::open(&src).map_err(|e| e.to_string())?;
    let (w, h) = img.dimensions();

    // Calculate crop region from percentage coordinates and zoom
    let zoom = crop_zoom.max(1.0).min(5.0);
    let visible_fraction = 1.0 / zoom;
    let crop_w = (w as f64 * visible_fraction) as u32;
    let crop_h = (h as f64 * visible_fraction) as u32;
    let crop_size = crop_w.min(crop_h); // square crop

    // Center the crop on the click position
    let center_x = (w as f64 * crop_x / 100.0) as u32;
    let center_y = (h as f64 * crop_y / 100.0) as u32;
    let half = crop_size / 2;

    let x = center_x.saturating_sub(half).min(w.saturating_sub(crop_size));
    let y = center_y.saturating_sub(half).min(h.saturating_sub(crop_size));

    let cropped = img.crop_imm(x, y, crop_size, crop_size);
    let thumb = cropped.resize_exact(size, size, image::imageops::FilterType::Lanczos3);
    thumb.save(&thumb_path).map_err(|e| e.to_string())?;

    Ok(thumb_path.to_string_lossy().to_string())
}

/// Delete all cached thumbnails for a specific source image.
/// Called when changing primary photo to force fresh generation.
#[tauri::command]
async fn clear_thumb_cache(
    app: tauri::AppHandle,
    path: String,
) -> Result<(), String> {
    let cache_dir = get_thumb_dir(&app)?;
    let hash = path_hash(&path);
    // Delete all files starting with this hash
    if let Ok(entries) = std::fs::read_dir(&cache_dir) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.starts_with(&hash) {
                let _ = std::fs::remove_file(entry.path());
            }
        }
    }
    Ok(())
}

// ============================================================
// Image reading (base64 for crop preview)
// ============================================================

/// Read an image file and return as base64 data URL.
/// Used for the crop tool preview where we need the full image in the webview.
#[tauri::command]
async fn read_image_base64(path: String) -> Result<String, String> {
    let src = PathBuf::from(&path);
    if !src.exists() { return Err("File not found".into()); }

    let bytes = std::fs::read(&src).map_err(|e| e.to_string())?;
    let ext = src.extension().and_then(|e| e.to_str()).unwrap_or("jpg").to_lowercase();
    let mime = match ext.as_str() {
        "png" => "image/png",
        "gif" => "image/gif",
        "webp" => "image/webp",
        _ => "image/jpeg",
    };

    let encoded = base64_encode(&bytes);
    Ok(format!("data:{};base64,{}", mime, encoded))
}

// ============================================================
// Media rename for export
// ============================================================

#[derive(Deserialize)]
struct RenameItem {
    original_path: String,
    person_given: String,
    person_surname: String,
    media_title: String,
    media_xref: String,
}

#[derive(Serialize)]
struct RenameResult {
    renamed: usize,
    failed: usize,
    failed_paths: Vec<String>,
    output_dir: String,
}

#[tauri::command]
async fn batch_rename_media(
    app: tauri::AppHandle,
    items: Vec<RenameItem>,
) -> Result<RenameResult, String> {
    let output_dir = app.path()
        .document_dir()
        .map_err(|e: tauri::Error| e.to_string())?
        .join("GedFix")
        .join("renamed_media");
    std::fs::create_dir_all(&output_dir).map_err(|e| e.to_string())?;

    let mut renamed = 0;
    let mut failed_paths: Vec<String> = Vec::new();

    for item in &items {
        let src = PathBuf::from(&item.original_path);
        if !src.exists() { failed_paths.push(item.original_path.clone()); continue; }

        let ext = src.extension().and_then(|e| e.to_str()).unwrap_or("jpg").to_lowercase();
        let clean = |s: &str| s.chars().map(|c| if c.is_alphanumeric() || c == '-' { c } else { '_' }).collect::<String>();
        let surname = clean(&item.person_surname);
        let given = clean(&item.person_given);
        let title = clean(if item.media_title.is_empty() { &item.media_xref } else { &item.media_title });
        let title_short = if title.len() > 50 { &title[..50] } else { &title };

        let surname_dir = output_dir.join(&surname);
        std::fs::create_dir_all(&surname_dir).ok();
        let dest = surname_dir.join(format!("{}_{}_{}.{}", given, surname, title_short, ext));

        match std::fs::copy(&src, &dest) {
            Ok(_) => renamed += 1,
            Err(e) => { failed_paths.push(format!("{}: {}", item.original_path, e)); }
        }
    }

    let failed = failed_paths.len();
    Ok(RenameResult {
        renamed, failed, failed_paths,
        output_dir: output_dir.to_string_lossy().to_string(),
    })
}

// ============================================================
// File existence check (for media cleanup)
// ============================================================

#[tauri::command]
async fn check_file_exists(path: String) -> Result<bool, String> {
    Ok(PathBuf::from(&path).exists())
}

// ============================================================
// Helpers
// ============================================================

fn get_thumb_dir(app: &tauri::AppHandle) -> Result<PathBuf, String> {
    let dir = app.path().app_cache_dir()
        .map_err(|e: tauri::Error| e.to_string())?
        .join("thumbs");
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    Ok(dir)
}

fn path_hash(input: &str) -> String {
    use std::hash::{Hash, Hasher};
    let mut hasher = std::collections::hash_map::DefaultHasher::new();
    input.hash(&mut hasher);
    format!("{:x}", hasher.finish())
}

fn base64_encode(data: &[u8]) -> String {
    const CHARS: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut result = String::with_capacity(data.len() * 4 / 3 + 4);
    for chunk in data.chunks(3) {
        let b0 = chunk[0] as u32;
        let b1 = if chunk.len() > 1 { chunk[1] as u32 } else { 0 };
        let b2 = if chunk.len() > 2 { chunk[2] as u32 } else { 0 };
        let triple = (b0 << 16) | (b1 << 8) | b2;
        result.push(CHARS[((triple >> 18) & 0x3F) as usize] as char);
        result.push(CHARS[((triple >> 12) & 0x3F) as usize] as char);
        if chunk.len() > 1 { result.push(CHARS[((triple >> 6) & 0x3F) as usize] as char); } else { result.push('='); }
        if chunk.len() > 2 { result.push(CHARS[(triple & 0x3F) as usize] as char); } else { result.push('='); }
    }
    result
}

// ============================================================
// EXIF metadata writing
// ============================================================

#[derive(Deserialize)]
struct ExifWriteItem {
    file_path: String,
    person_name: String,
    original_filename: String,
    gedcom_xref: String,
    category: String,
    description: String,
}

#[derive(Serialize)]
struct ExifWriteResult {
    written: usize,
    failed: usize,
    errors: Vec<String>,
}

#[tauri::command]
async fn write_exif_metadata(items: Vec<ExifWriteItem>) -> Result<ExifWriteResult, String> {
    use little_exif::metadata::Metadata;
    use little_exif::exif_tag::ExifTag;

    let mut written = 0;
    let mut errors: Vec<String> = Vec::new();

    for item in &items {
        let src = PathBuf::from(&item.file_path);
        if !src.exists() {
            errors.push(format!("Not found: {}", item.file_path));
            continue;
        }

        let ext = src.extension().and_then(|e| e.to_str()).unwrap_or("").to_lowercase();
        if !["jpg", "jpeg", "tiff", "tif"].contains(&ext.as_str()) {
            errors.push(format!("Unsupported format: {}", ext));
            continue;
        }

        let desc = format!("{} | {} | GEDCOM: {} | Category: {}",
            item.person_name, item.description, item.gedcom_xref, item.category);
        let comment = format!("GedFix Export | Person: {} | XREF: {} | Original: {} | Category: {}",
            item.person_name, item.gedcom_xref, item.original_filename, item.category);

        let mut metadata = match Metadata::new_from_path(&src) {
            Ok(m) => m,
            Err(_) => Metadata::new(),
        };

        metadata.set_tag(ExifTag::ImageDescription(desc));
        metadata.set_tag(ExifTag::DocumentName(item.original_filename.clone()));
        metadata.set_tag(ExifTag::Artist(item.person_name.clone()));
        metadata.set_tag(ExifTag::UserComment(comment));

        match metadata.write_to_file(&src) {
            Ok(_) => written += 1,
            Err(e) => errors.push(format!("{}: {}", item.file_path, e)),
        }
    }

    Ok(ExifWriteResult { written, failed: errors.len(), errors })
}

// ============================================================
// Organize media into categorized folders
// ============================================================

#[derive(Deserialize)]
struct OrganizeItem {
    source_path: String,
    category: String,
    person_surname: String,
    person_given: String,
    media_title: String,
}

#[derive(Serialize)]
struct OrganizeResult {
    organized: usize,
    failed: usize,
    output_dir: String,
}

#[tauri::command]
async fn organize_media_folders(
    app: tauri::AppHandle,
    items: Vec<OrganizeItem>,
) -> Result<OrganizeResult, String> {
    let output_dir = app.path()
        .document_dir()
        .map_err(|e: tauri::Error| e.to_string())?
        .join("GedFix")
        .join("organized_media");

    let category_folders: std::collections::HashMap<&str, &str> = [
        ("headshots", "People/Headshots"),
        ("single", "People/Headshots"),
        ("group", "People/Group_Photos"),
        ("family-group", "People/Group_Photos"),
        ("married", "People/Group_Photos"),
        ("homes", "Places/Homes_Castles"),
        ("castles", "Places/Homes_Castles"),
        ("graves", "Places/Graves"),
        ("crests", "Symbols/Crests_Flags"),
        ("ship-manifests", "Documents/Ship_Manifests"),
        ("census", "Documents/Census_Records"),
        ("marriage-docs", "Documents/Marriage_Records"),
        ("wills", "Documents/Wills_Probate"),
        ("histories", "Documents/Histories"),
        ("other-docs", "Documents/Other"),
        ("document", "Documents/Other"),
        ("other", "Other"),
    ].into_iter().collect();

    let mut organized = 0;
    let mut failed = 0;

    for item in &items {
        let src = PathBuf::from(&item.source_path);
        if !src.exists() { failed += 1; continue; }

        let folder_name = category_folders.get(item.category.as_str()).unwrap_or(&"Other");
        let dest_dir = output_dir.join(folder_name);
        std::fs::create_dir_all(&dest_dir).ok();

        let ext = src.extension().and_then(|e| e.to_str()).unwrap_or("jpg").to_lowercase();
        let clean = |s: &str| s.chars().map(|c| if c.is_alphanumeric() || c == '-' || c == ' ' { c } else { '_' }).collect::<String>();
        let surname = clean(&item.person_surname);
        let given = clean(&item.person_given);
        let title = clean(if item.media_title.is_empty() { "untitled" } else { &item.media_title });
        let title_short = if title.len() > 40 { &title[..40] } else { &title };

        let filename = format!("{}_{}_{}.{}", surname, given, title_short, ext);
        let dest = dest_dir.join(&filename);
        let final_dest = if dest.exists() {
            let mut counter = 1;
            loop {
                let alt = dest_dir.join(format!("{}_{}_{}_{}.{}", surname, given, title_short, counter, ext));
                if !alt.exists() { break alt; }
                counter += 1;
            }
        } else {
            dest
        };

        match std::fs::copy(&src, &final_dest) {
            Ok(_) => organized += 1,
            Err(_) => failed += 1,
        }
    }

    Ok(OrganizeResult { organized, failed, output_dir: output_dir.to_string_lossy().to_string() })
}

// ============================================================
// Directory scanning for media matcher
// ============================================================

#[tauri::command]
async fn list_image_files(dir: String) -> Result<Vec<String>, String> {
    let path = PathBuf::from(&dir);
    if !path.exists() { return Ok(vec![]); }
    let mut files = Vec::new();
    fn walk(dir: &std::path::Path, files: &mut Vec<String>) {
        if let Ok(entries) = std::fs::read_dir(dir) {
            for entry in entries.flatten() {
                let p = entry.path();
                if p.is_dir() {
                    walk(&p, files);
                } else if let Some(ext) = p.extension() {
                    let ext_lower = ext.to_string_lossy().to_lowercase();
                    if ["jpg","jpeg","png","gif","bmp","webp","tiff","tif"].contains(&ext_lower.as_str()) {
                        files.push(p.to_string_lossy().to_string());
                    }
                }
            }
        }
    }
    walk(&path, &mut files);
    Ok(files)
}

// ============================================================
// App entry
// ============================================================

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_sql::Builder::new().build())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            generate_thumbnail,
            crop_face,
            clear_thumb_cache,
            read_image_base64,
            batch_rename_media,
            check_file_exists,
            list_image_files,
            write_exif_metadata,
            organize_media_folders,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
