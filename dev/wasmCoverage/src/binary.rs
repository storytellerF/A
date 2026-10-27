/*
 * This is a private project. All rights reserved.
 */
use crate::{Result, mappings::SourceLine};
use std::{collections::BTreeMap, ops::Range};

pub struct Section {
    pub id: u8,
    pub range: Range<usize>,
}

pub fn number(bytes: &[u8], offset: &mut usize) -> Result<u32> {
    let mut result = 0u32;
    for shift in (0..35).step_by(7) {
        let value = *bytes.get(*offset).ok_or("Truncated unsigned LEB128")?;
        *offset += 1;
        if shift == 28 && value & 0xf0 != 0 {
            return Err("Unsigned LEB128 overflow".into());
        }
        result |= u32::from(value & 0x7f) << shift;
        if value & 0x80 == 0 {
            return Ok(result);
        }
    }
    Err("Invalid unsigned LEB128".into())
}

pub fn count(bytes: &[u8]) -> Result<u32> {
    number(bytes, &mut 0)
}

fn encoded(output: &mut Vec<u8>, mut value: u32) {
    loop {
        let byte = (value & 0x7f) as u8;
        value >>= 7;
        output.push(byte | if value != 0 { 0x80 } else { 0 });
        if value == 0 {
            break;
        }
    }
}

pub fn sections(bytes: &[u8]) -> Result<Vec<Section>> {
    if bytes.get(..8) != Some(b"\0asm\x01\0\0\0") {
        return Err("Expected core Wasm version 1".into());
    }
    let mut offset = 8;
    let mut result = Vec::new();
    while offset < bytes.len() {
        let id = bytes[offset];
        offset += 1;
        let size = number(bytes, &mut offset)? as usize;
        let end = offset
            .checked_add(size)
            .filter(|end| *end <= bytes.len())
            .ok_or("Truncated section")?;
        result.push(Section {
            id,
            range: offset..end,
        });
        offset = end;
    }
    Ok(result)
}

pub fn write_section(output: &mut Vec<u8>, id: u8, data: &[u8]) {
    output.push(id);
    encoded(output, data.len() as u32);
    output.extend_from_slice(data);
}

pub fn custom_name(data: &[u8]) -> Result<&str> {
    let mut offset = 0;
    let size = number(data, &mut offset)? as usize;
    Ok(std::str::from_utf8(
        data.get(offset..offset + size)
            .ok_or("Truncated custom section name")?,
    )?)
}

fn append_entries(original: &[u8], extra: u32) -> Result<Vec<u8>> {
    let mut offset = 0;
    let count = if original.is_empty() {
        0
    } else {
        number(original, &mut offset)?
    };
    let mut output = Vec::new();
    encoded(
        &mut output,
        count.checked_add(extra).ok_or("Section count overflow")?,
    );
    output.extend_from_slice(&original[offset..]);
    Ok(output)
}

pub fn globals(original: &[u8], extra: u32) -> Result<Vec<u8>> {
    let mut output = append_entries(original, extra)?;
    for _ in 0..extra {
        // Append mutable i32 globals. Existing global, function and type indices remain unchanged.
        output.extend_from_slice(&[0x7f, 1, 0x41, 0, 0x0b]);
    }
    Ok(output)
}

pub fn exports(original: &[u8], base: u32, extra: u32, build_id: &str) -> Result<Vec<u8>> {
    let mut output = append_entries(original, extra + 1)?;
    for id in 0..extra {
        let name = format!("__e2e_cov_line_{id}");
        encoded(&mut output, name.len() as u32);
        output.extend_from_slice(name.as_bytes());
        output.push(3);
        encoded(&mut output, base + id);
    }
    let name = format!("__e2e_cov_build_{build_id}");
    encoded(&mut output, name.len() as u32);
    output.extend_from_slice(name.as_bytes());
    output.push(3);
    encoded(&mut output, base + extra);
    Ok(output)
}

pub fn code(
    bytes: &[u8],
    functions: &[Range<usize>],
    sites: &BTreeMap<usize, SourceLine>,
    ids: &BTreeMap<SourceLine, u32>,
    base: u32,
) -> Result<Vec<u8>> {
    let mut output = Vec::new();
    encoded(&mut output, functions.len() as u32);
    for function in functions {
        let mut body = Vec::new();
        let mut position = function.start;
        for (&offset, source) in sites.range(function.clone()) {
            body.extend_from_slice(&bytes[position..offset]);
            // Saturating boolean coverage is stack-neutral and never calls JavaScript in a hot path.
            body.extend_from_slice(&[0x41, 1, 0x24]);
            encoded(&mut body, base + ids[source]);
            position = offset;
        }
        body.extend_from_slice(&bytes[position..function.end]);
        encoded(&mut output, body.len() as u32);
        output.extend_from_slice(&body);
    }
    Ok(output)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn unsigned_leb_boundaries_and_corrupt_input() {
        for expected in [0, 127, 128, 16384, u32::MAX] {
            let mut bytes = Vec::new();
            encoded(&mut bytes, expected);
            assert_eq!(count(&bytes).unwrap(), expected);
        }
        assert!(count(&[0x80]).is_err());
        assert!(count(&[0xff, 0xff, 0xff, 0xff, 0x1f]).is_err());
    }

    #[test]
    fn rejects_sections_outside_the_binary() {
        let mut bytes = b"\0asm\x01\0\0\0".to_vec();
        bytes.extend_from_slice(&[10, 4, 0]);
        assert!(sections(&bytes).is_err());
    }
}
