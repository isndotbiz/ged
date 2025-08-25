from __future__ import annotations
from pathlib import Path
from typing import Iterable
from .io_utils import backup_file

NOTE_PREFIX = "AutoFix:"

def iterate_lines(p: Path):
    with p.open("r", encoding="utf-8-sig", errors="replace") as f:
        for line in f:
            yield line.rstrip("\n")

def write_lines(p: Path, lines: Iterable[str]) -> None:
    with p.open("w", encoding="utf-8", newline="\n") as f:
        for line in lines:
            f.write(line + "\n")

def fix_duplicates_and_suffix(inp: Path, out: Path, approve_dup_facts: bool, approve_suffix: bool, note_prefix: str|None, backup_dir: Path|None):
    if backup_dir:
        backup_file(inp, backup_dir)
    note_prefix = note_prefix or NOTE_PREFIX

    lines = list(iterate_lines(inp))
    # Simple state machine on INDI/FAM records to collapse IDENTICAL duplicate facts:
    # - Only collapse duplicates for the same tag when value lines (DATE/PLAC for events; exact text for NAME) are byte-identical.
    # - Never rewrite DATE values; only remove exact duplicate blocks.
    out_lines = []
    i=0
    while i < len(lines):
        s = " ".join(lines[i].strip().split())
        if s.startswith("0 @") and s.endswith(" INDI"):
            # collect full record
            rec_start = i
            j = i+1
            while j < len(lines) and not lines[j].startswith("0 "):
                j += 1
            rec = lines[i:j]
            rec_fixed = _fix_indi_record(rec, approve_dup_facts, approve_suffix, note_prefix)
            out_lines.extend(rec_fixed)
            i = j
            continue
        elif s.startswith("0 @") and s.endswith(" FAM"):
            # collapse duplicate MARR blocks if byte-identical
            rec_start = i
            j = i+1
            while j < len(lines) and not lines[j].startswith("0 "):
                j += 1
            rec = lines[i:j]
            rec_fixed = _fix_fam_record(rec, approve_dup_facts, note_prefix)
            out_lines.extend(rec_fixed)
            i = j
            continue
        else:
            out_lines.append(" ".join(lines[i].strip().split()))
            i += 1

    write_lines(out, out_lines)
    return {"changed": True, "notes_added": True, "out": str(out)}

def _fix_indi_record(rec: list[str], approve_dup_facts: bool, approve_suffix: bool, note_prefix: str) -> list[str]:
    # Remove duplicate BIRT/DEAT events with identical subtrees (DATE/PLAC text-equal)
    # Move suffix tokens from NAME GIVN/SURN into NSFX if approve_suffix=True
    # Keep structure otherwise intact
    out = []
    # first line:
    out.append(" ".join(rec[0].strip().split()))
    i=1
    # Collect NAME blocks to possibly adjust NSFX
    while i < len(rec):
        line = " ".join(rec[i].strip().split())
        parts = line.split(" ", 2)
        if len(parts)>=2 and parts[0]=="1" and parts[1] in ("BIRT","DEAT","CHR","BAPM"):
            # capture block
            k = i+1
            block = [line]
            while k < len(rec):
                nxt = rec[k]
                if nxt.strip() and int(nxt.split(" ",1)[0]) <= 1:
                    break
                block.append(" ".join(nxt.strip().split()))
                k += 1
            # decide dedupe later
            out.append(("EVENT_BLOCK", block))
            i = k
            continue
        elif len(parts)>=2 and parts[0]=="1" and parts[1]=="NAME":
            # capture NAME subtree
            k = i+1
            block = [line]
            while k < len(rec):
                if rec[k].strip() and int(rec[k].split(" ",1)[0]) <= 1:
                    break
                block.append(" ".join(rec[k].strip().split()))
                k += 1
            if approve_suffix:
                block = _move_suffix_to_nsfx(block, note_prefix)
            out.append(("NAME_BLOCK", block))
            i = k
            continue
        else:
            out.append(line)
            i += 1
    # Now collapse duplicate EVENT_BLOCKs if exact (byte-identical)
    final: list[str] = []
    final.append(out[0])  # header line
    seen_blocks = set()
    for item in out[1:]:
        if isinstance(item, tuple) and item[0] == "EVENT_BLOCK":
            block = item[1]
            sig = "\n".join(block)
            if sig in seen_blocks and approve_dup_facts:
                final.append(f"1 NOTE {note_prefix} Removed duplicate event block")
                continue
            seen_blocks.add(sig)
            final.extend(block)
        elif isinstance(item, tuple) and item[0] == "NAME_BLOCK":
            final.extend(item[1])
        else:
            final.append(item)
    return final

def _move_suffix_to_nsfx(name_block: list[str], note_prefix: str) -> list[str]:
    # If NAME has suffix tokens like "II" or "Jr" at end of GIVN or SURN, move to NSFX (add 2 NSFX)
    # Conservative: only move if NSFX not already present.
    SUF = {"JR","SR","II","III","IV","V","VI","VII","VIII","IX","X","ESQ"}
    givn=""; surn=""; has_nsfx=False
    for ln in name_block[1:]:
        if ln.startswith("2 GIVN "): givn = ln[7:].strip()
        if ln.startswith("2 SURN "): surn = ln[7:].strip()
        if ln.startswith("2 NSFX "): has_nsfx=True
    if has_nsfx:
        return name_block
    # extract suffix if last token matches
    moved = None
    tokens = (givn.split() + surn.split())[::-1]
    for t in tokens:
        up = t.strip(",. ").upper()
        if up in SUF:
            moved = t.strip(",. ")
            break
    if not moved:
        return name_block
    # add 2 NSFX if not present, and remove from the end of corresponding field (display-only; leave 1 NAME raw as-is)
    out=[]
    out.append(name_block[0])
    for ln in name_block[1:]:
        if ln.startswith("2 GIVN ") and givn.endswith(" "+moved):
            new = "2 GIVN " + givn[:-(len(moved)+1)]
            out.append(new)
            continue
        if ln.startswith("2 SURN ") and surn.endswith(" "+moved):
            new = "2 SURN " + surn[:-(len(moved)+1)]
            out.append(new)
            continue
        out.append(ln)
    out.append(f"2 NSFX {moved}")
    out.append(f"2 NOTE {note_prefix} Moved suffix '{moved}' to NSFX")
    return out

def _fix_fam_record(rec: list[str], approve_dup_facts: bool, note_prefix: str) -> list[str]:
    out=[]
    out.append(" ".join(rec[0].strip().split()))
    i=1
    blocks=[]
    while i < len(rec):
        line = " ".join(rec[i].strip().split())
        parts = line.split(" ", 2)
        if len(parts)>=2 and parts[0]=="1" and parts[1]=="MARR":
            k=i+1
            block=[line]
            while k < len(rec):
                nxt = rec[k]
                if nxt.strip() and int(nxt.split(" ",1)[0]) <= 1:
                    break
                block.append(" ".join(nxt.strip().split()))
                k += 1
            blocks.append(block)
            i=k
            continue
        else:
            out.append(line)
            i+=1
    # dedupe identical MARR blocks
    seen=set()
    kept=0
    for b in blocks:
        sig="\n".join(b)
        if sig in seen and approve_dup_facts:
            out.append(f"1 NOTE {note_prefix} Removed duplicate MARR block")
            continue
        seen.add(sig)
        out.extend(b); kept+=1
    return out
