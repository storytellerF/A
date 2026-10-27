/*
 * This is a private project. All rights reserved.
 */
use crate::Result;
use std::{
    collections::BTreeMap,
    fs,
    path::{Path, PathBuf},
};

#[derive(Clone, Eq, PartialEq, Ord, PartialOrd)]
pub struct SourceLine {
    pub file: String,
    pub line: u32,
}

struct SourceFile {
    file: String,
    line_count: usize,
}

fn relative_source(name: &str, root: &Path, map: &Path) -> Option<SourceFile> {
    let root = root.canonicalize().ok()?;
    [root.join(name), map.parent()?.join(name)]
        .into_iter()
        .find_map(|path| {
            let source = path.canonicalize().ok()?;
            let relative = source.strip_prefix(&root).ok()?;
            if relative
                .components()
                .any(|part| part.as_os_str() == "build" || part.as_os_str() == ".worktrees")
            {
                return None;
            }
            if source.extension()?.to_str()? != "kt" {
                return None;
            }
            Some(SourceFile {
                file: relative.to_str()?.replace('\\', "/"),
                line_count: fs::read_to_string(&source).ok()?.lines().count(),
            })
        })
}

fn vlq(segment: &str) -> Result<Vec<i64>> {
    let alphabet = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut fields = Vec::new();
    let mut value = 0i64;
    let mut shift = 0;
    for byte in segment.bytes() {
        let digit = alphabet
            .iter()
            .position(|entry| *entry == byte)
            .ok_or("Invalid VLQ character")? as i64;
        if shift > 55 {
            return Err("VLQ overflow".into());
        }
        value |= (digit & 31) << shift;
        if digit & 32 == 0 {
            fields.push(if value & 1 == 1 {
                -(value >> 1)
            } else {
                value >> 1
            });
            value = 0;
            shift = 0;
        } else {
            shift += 5;
        }
    }
    if shift != 0 {
        return Err("Truncated VLQ".into());
    }
    Ok(fields)
}

pub fn load(map: &str, root: &Path) -> Result<BTreeMap<usize, Option<SourceLine>>> {
    let path = PathBuf::from(map).canonicalize()?;
    let json: serde_json::Value = serde_json::from_slice(&fs::read(&path)?)?;
    if json["version"] != 3 {
        return Err("Expected source map version 3".into());
    }
    let source_root = json["sourceRoot"].as_str().unwrap_or("");
    let sources: Vec<_> = json["sources"]
        .as_array()
        .ok_or("Missing sources")?
        .iter()
        .map(|source| {
            relative_source(
                &Path::new(source_root)
                    .join(source.as_str().unwrap_or(""))
                    .to_string_lossy(),
                root,
                &path,
            )
        })
        .collect();
    let mapping = json["mappings"].as_str().ok_or("Missing mappings")?;
    if mapping.contains(';') {
        return Err("Expected Wasm byte offsets on a single generated line".into());
    }
    let (mut generated, mut source, mut line, mut column) = (0i64, 0i64, 0i64, 0i64);
    let mut result = BTreeMap::new();
    for segment in mapping.split(',').filter(|segment| !segment.is_empty()) {
        let fields = vlq(segment)?;
        generated = generated.checked_add(fields[0]).ok_or("Offset overflow")?;
        if generated < 0 {
            return Err("Negative Wasm byte offset".into());
        }
        if fields.len() == 1 {
            result.insert(generated as usize, None);
            continue;
        }
        if !(4..=5).contains(&fields.len()) {
            return Err("Invalid mapping segment".into());
        }
        source += fields[1];
        line += fields[2];
        column += fields[3];
        if source < 0 || line < 0 || column < 0 {
            return Err("Negative source location".into());
        }
        let file = sources
            .get(source as usize)
            .ok_or("Source index is out of range")?;
        // Kotlin can map synthesized operators to the empty EOF line. An invalid anchor
        // must remain unmapped so later operators cannot inherit the preceding real line.
        let location = file
            .as_ref()
            .filter(|file| (line as u64) < file.line_count as u64)
            .map(|file| {
                Ok::<_, Box<dyn std::error::Error>>(SourceLine {
                    file: file.file.clone(),
                    line: u32::try_from(line + 1)?,
                })
            })
            .transpose()?;
        result.insert(generated as usize, location);
    }
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decodes_signed_vlq_and_rejects_truncated_or_overflowing_fields() {
        assert_eq!(vlq("ACDF").unwrap(), vec![0, 1, -1, -2]);
        assert!(vlq("g").is_err());
        assert!(vlq("!").is_err());
        assert!(vlq("gggggggggggggggggg").is_err());
    }

    #[test]
    fn unmapped_ranges_do_not_inherit_previous_source_lines() {
        let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("build/mapping-test");
        fs::create_dir_all(&root).unwrap();
        fs::write(
            root.join("Source.kt"),
            "fun value() = 73\nfun other() = 74\n",
        )
        .unwrap();
        let map = root.join("fixture.map");
        fs::write(
            &map,
            r#"{"version":3,"sources":["Source.kt"],"mappings":"AAAA,U,UACA"}"#,
        )
        .unwrap();
        let result = load(map.to_str().unwrap(), &root).unwrap();
        assert_eq!(result[&0].as_ref().unwrap().line, 1);
        assert!(result.range(..=15).next_back().unwrap().1.is_none());
        assert_eq!(result[&20].as_ref().unwrap().line, 2);
    }
    #[test]
    fn eof_anchors_are_unmapped_with_and_without_a_final_newline() {
        let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("build/eof-mapping-test");
        fs::create_dir_all(&root).unwrap();
        for source in ["fun value() = 73", "fun value() = 73\n"] {
            fs::write(root.join("Source.kt"), source).unwrap();
            let map = root.join("fixture.map");
            fs::write(
                &map,
                r#"{"version":3,"sources":["Source.kt"],"mappings":"AAAA,UACA,UADA"}"#,
            )
            .unwrap();
            let result = load(map.to_str().unwrap(), &root).unwrap();
            assert_eq!(result[&0].as_ref().unwrap().line, 1);
            assert!(result[&10].is_none());
            assert!(result.range(..=15).next_back().unwrap().1.is_none());
            assert_eq!(result[&20].as_ref().unwrap().line, 1);
        }
    }
}
