#!/usr/bin/env python3
"""
anki_to_cardpop.py — Convert Anki .apkg to CSV for CardPop import.

Usage:
    python anki_to_cardpop.py my_deck.apkg
    python anki_to_cardpop.py my_deck.apkg -o output.csv
    python anki_to_cardpop.py my_deck.apkg --category "HSK 1"

CardPop CSV format: question,answer,category
"""

import argparse
import csv
import html
import os
import re
import sqlite3
import sys
import tempfile
import zipfile


def strip_html(text: str) -> str:
    """Remove HTML tags and decode entities."""
    text = re.sub(r"<br\s*/?>", "\n", text, flags=re.IGNORECASE)
    text = re.sub(r"<div[^>]*>", "\n", text, flags=re.IGNORECASE)
    text = re.sub(r"<[^>]+>", "", text)
    text = html.unescape(text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def extract_apkg(apkg_path: str, tmpdir: str) -> str:
    """Extract .apkg (zip) and return path to collection.anki2 or collection.anki21."""
    with zipfile.ZipFile(apkg_path, "r") as zf:
        zf.extractall(tmpdir)

    # Try both db filenames (anki2 = older, anki21 = newer)
    for name in ("collection.anki21", "collection.anki2"):
        db_path = os.path.join(tmpdir, name)
        if os.path.exists(db_path):
            return db_path

    raise FileNotFoundError(
        f"No collection.anki2 or collection.anki21 found in {apkg_path}"
    )


def read_notes(db_path: str) -> list[dict]:
    """Read notes from Anki SQLite database, return list of {fields, tags, mid}."""
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    # Get model (notetype) info for field names
    # In Anki, models are stored in 'col' table (older) or 'notetypes' table (newer)
    models = {}
    try:
        cur.execute("SELECT id, name, flds FROM notetypes")
        for mid, name, flds_json in cur.fetchall():
            import json
            flds = json.loads(flds_json) if isinstance(flds_json, str) else []
            field_names = [f.get("name", f"Field {i}") for i, f in enumerate(flds)]
            models[mid] = {"name": name, "fields": field_names}
    except sqlite3.OperationalError:
        # Older format: models stored as JSON in col table
        import json
        cur.execute("SELECT models FROM col")
        row = cur.fetchone()
        if row:
            mods = json.loads(row[0])
            for mid_str, m in mods.items():
                field_names = [f["name"] for f in m.get("flds", [])]
                models[int(mid_str)] = {"name": m.get("name", ""), "fields": field_names}

    # Read all notes
    cur.execute("SELECT mid, flds, tags FROM notes")
    notes = []
    for mid, flds_str, tags in cur.fetchall():
        fields = flds_str.split("\x1f")  # Anki field separator
        model = models.get(mid, {})
        field_names = model.get("fields", [])
        model_name = model.get("name", "")

        note = {
            "fields": fields,
            "field_names": field_names,
            "tags": tags.strip(),
            "model_name": model_name,
        }
        notes.append(note)

    conn.close()
    return notes


def notes_to_csv_rows(
    notes: list[dict], category: str | None = None
) -> list[dict]:
    """Convert Anki notes to FloFlaCards CSV rows {question, answer, category}."""
    rows = []
    for note in notes:
        fields = note["fields"]
        if len(fields) < 2:
            continue

        question = strip_html(fields[0])
        answer = strip_html(fields[1])

        if not question or not answer:
            continue

        cat = category or note["model_name"] or "Imported"
        rows.append({"question": question, "answer": answer, "category": cat})

    return rows


def write_csv(rows: list[dict], output_path: str) -> None:
    """Write FloFlaCards-compatible CSV."""
    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=["question", "answer", "category"])
        writer.writeheader()
        writer.writerows(rows)


def main():
    parser = argparse.ArgumentParser(
        description="Convert Anki .apkg to FloFlaCards CSV"
    )
    parser.add_argument("apkg", help="Path to .apkg file")
    parser.add_argument("-o", "--output", help="Output CSV path (default: <input>.csv)")
    parser.add_argument(
        "-c", "--category", help="Override category name (default: use deck/model name)"
    )
    args = parser.parse_args()

    if not os.path.exists(args.apkg):
        print(f"Error: {args.apkg} not found", file=sys.stderr)
        sys.exit(1)

    output = args.output or os.path.splitext(args.apkg)[0] + ".csv"

    with tempfile.TemporaryDirectory() as tmpdir:
        print(f"Extracting {args.apkg}...")
        db_path = extract_apkg(args.apkg, tmpdir)

        print("Reading notes...")
        notes = read_notes(db_path)
        print(f"  Found {len(notes)} notes")

        # Show field preview
        if notes:
            n = notes[0]
            names = n["field_names"] or [f"Field {i}" for i in range(len(n["fields"]))]
            print(f"  Model: {n['model_name']}")
            print(f"  Fields: {names}")
            preview = [strip_html(f)[:40] for f in n["fields"][:4]]
            print(f"  Preview: {preview}")

        rows = notes_to_csv_rows(notes, args.category)
        print(f"  Converted {len(rows)} cards")

        write_csv(rows, output)
        print(f"\nDone! CSV saved to: {output}")
        print(f"\nImport in FloFlaCards: Settings > Import CSV")


if __name__ == "__main__":
    main()
