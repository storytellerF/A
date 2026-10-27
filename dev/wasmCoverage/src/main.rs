/*
 * This is a private project. All rights reserved.
 */

mod binary;
mod mappings;

use sha2::{Digest, Sha256};
use std::{collections::BTreeMap, env, error::Error, fs, path::Path};
use wasmparser::{Operator, Parser, Payload, TypeRef, Validator};

type Result<T> = std::result::Result<T, Box<dyn Error>>;

fn main() -> Result<()> {
    let args: Vec<String> = env::args().skip(1).collect();
    if args.len() != 5 {
        return Err(
            "Expected input.wasm input.wasm.map output.wasm manifest.json source-root".into(),
        );
    }
    instrument(&args[0], &args[1], &args[2], &args[3], Path::new(&args[4]))
}

fn instrument(input: &str, map: &str, output: &str, manifest: &str, root: &Path) -> Result<()> {
    let bytes = fs::read(input)?;
    let mut identity = Sha256::new();
    identity.update(&bytes);
    identity.update(fs::read(map)?);
    // Counter IDs and offsets belong to this exact rewrite algorithm, not just its input module.
    identity.update(include_str!("main.rs").as_bytes());
    identity.update(include_str!("binary.rs").as_bytes());
    identity.update(include_str!("mappings.rs").as_bytes());
    let build_id: String = identity
        .finalize()
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect();
    Validator::new_with_features(wasmparser::WasmFeatures::all()).validate_all(&bytes)?;
    let mappings = mappings::load(map, root)?;
    let mut imported_globals = 0;
    let mut functions = Vec::new();
    let mut sites = BTreeMap::new();
    for payload in Parser::new(0).parse_all(&bytes) {
        match payload? {
            Payload::ImportSection(reader) => {
                for import in reader.into_imports() {
                    if matches!(import?.ty, TypeRef::Global(_)) {
                        imported_globals += 1;
                    }
                }
            }
            Payload::ExportSection(reader) => {
                for export in reader {
                    if export?.name.starts_with("__e2e_cov_") {
                        return Err("Module is already coverage-instrumented".into());
                    }
                }
            }
            Payload::CodeSectionEntry(body) => {
                let range = body.range();
                functions.push(usize::try_from(range.start)?..usize::try_from(range.end)?);
                let mut reader = body.get_operators_reader()?;
                while !reader.eof() {
                    let (operator, offset) = reader.read_with_offset()?;
                    let offset = usize::try_from(offset)?;
                    // Source-map segments describe ranges, including function-local declarations.
                    // Preserve explicit unmapped segments so runtime code never inherits a project line.
                    if let Some((start, Some(location))) = mappings.range(..=offset).next_back() {
                        if *start < usize::try_from(range.start)? {
                            continue;
                        }
                        // Delimiters often retain the preceding arm's mapping. Probe the executable
                        // operator after the delimiter under its own mapping, never the old arm.
                        let insertion = match operator {
                            Operator::Else
                            | Operator::Catch { .. }
                            | Operator::CatchAll
                            | Operator::Delegate { .. }
                            | Operator::End => continue,
                            _ => offset,
                        };
                        sites.entry(insertion).or_insert_with(|| location.clone());
                    }
                }
            }
            _ => {}
        }
    }
    if sites.is_empty() {
        return Err("No project source-map entries coincide with Wasm operator boundaries".into());
    }
    let mut lines: Vec<_> = sites.values().cloned().collect();
    lines.sort();
    lines.dedup();
    let ids: BTreeMap<_, _> = lines
        .iter()
        .cloned()
        .enumerate()
        .map(|(id, line)| (line, id as u32))
        .collect();
    let sections = binary::sections(&bytes)?;
    let defined_globals = sections
        .iter()
        .find(|s| s.id == 6)
        .map(|s| binary::count(&bytes[s.range.clone()]))
        .transpose()?
        .unwrap_or(0);
    let base = imported_globals + defined_globals;
    let mut result = bytes[..8].to_vec();
    let mut globals_written = false;
    let mut exports_written = false;
    for section in sections {
        if !globals_written && matches!(section.id, 7..=12) {
            binary::write_section(
                &mut result,
                6,
                &binary::globals(&[], lines.len() as u32 + 1)?,
            );
            globals_written = true;
        }
        if !exports_written && matches!(section.id, 8..=12) {
            binary::write_section(
                &mut result,
                7,
                &binary::exports(&[], base, lines.len() as u32, &build_id)?,
            );
            exports_written = true;
        }
        let data = &bytes[section.range];
        let payload = match section.id {
            6 => {
                globals_written = true;
                binary::globals(data, lines.len() as u32 + 1)?
            }
            7 => {
                exports_written = true;
                binary::exports(data, base, lines.len() as u32, &build_id)?
            }
            10 => binary::code(&bytes, &functions, &sites, &ids, base)?,
            // Old binary offsets are invalid after insertion. The report owns the original map.
            0 if binary::custom_name(data)? == "sourceMappingURL"
                || binary::custom_name(data)?.starts_with(".debug_") =>
            {
                continue;
            }
            _ => data.to_vec(),
        };
        binary::write_section(&mut result, section.id, &payload);
    }
    Validator::new_with_features(wasmparser::WasmFeatures::all()).validate_all(&result)?;
    let report = serde_json::json!({
        "schema": 1,
        "buildId": build_id,
        "originalSha256": hex_digest(&bytes),
        "instrumentedSha256": hex_digest(&result),
        "sourceMapSha256": hex_digest(&fs::read(map)?),
        "engine": "wasm-source-map-lines",
        "metrics": ["mapped-source-lines"],
        "denominator": "project source lines mapped to linked Wasm operators",
        "lines": lines.iter().enumerate().map(|(id, line)| serde_json::json!({
            "id": id, "file": line.file, "line": line.line
        })).collect::<Vec<_>>(),
        "operatorProbes": sites.len(),
        "mappedOffsets": mappings.len(),
        "matchedSourceLocations": sites.values().collect::<std::collections::BTreeSet<_>>().len(),
        "branchCoverageSupported": false,
    });
    if let Some(parent) = Path::new(output).parent() {
        fs::create_dir_all(parent)?;
    }
    if let Some(parent) = Path::new(manifest).parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(output, result)?;
    fs::write(manifest, serde_json::to_vec_pretty(&report)?)?;
    Ok(())
}

fn hex_digest(bytes: &[u8]) -> String {
    Sha256::digest(bytes)
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect()
}
