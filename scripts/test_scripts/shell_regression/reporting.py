# This is a private project. All rights reserved.
"""Serialize diagnostics at the XML boundary without changing their raw form."""
import copy
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def xml_text(value):
    if value is None:
        return None
    return "".join(character if (
        ord(character) in (9, 10, 13) or 0x20 <= ord(character) <= 0xD7FF
        or 0xE000 <= ord(character) <= 0xFFFD or 0x10000 <= ord(character) <= 0x10FFFF
    ) else f"\\u{ord(character):04x}" for character in value)


def write_junit(root, path):
    """Escape XML 1.0 forbidden characters everywhere; retain lossless raw JSON."""
    path = Path(path)
    raw = [{"tag": element.tag, "attributes": element.attrib, "text": element.text,
            "tail": element.tail} for element in root.iter()]
    path.with_suffix(".raw.json").write_text(json.dumps(raw, ensure_ascii=True, indent=2), encoding="utf-8")
    serialized = copy.deepcopy(root)
    for element in serialized.iter():
        element.attrib.update({key: xml_text(value) for key, value in element.attrib.items()})
        element.text = xml_text(element.text)
        element.tail = xml_text(element.tail)
    ET.indent(serialized)
    ET.ElementTree(serialized).write(path, encoding="utf-8", xml_declaration=True)
