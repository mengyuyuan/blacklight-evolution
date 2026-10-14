"""Separate a motion's starting lineage from subsequent Blacklight adaptation.

These fields describe provenance, not a license or a per-keyframe authorship audit.
Keep privateNotice as a compatibility key for existing packaging tools.
"""
import re


def motion_notice(initial_note: str) -> str:
    """Retain technical/source context without obsolete local-build instructions."""
    note = initial_note
    for obsolete in (
        'Never publish or package this file in the mod.',
        'Never include in public repository, mod assets or distribution.',
        'Never publish or package into Mod JAR.',
        'Never publish/package in mod.',
        'Never publish or package in mod.',
        'do not redistribute or package.',
        'never publish.', 'never distribute.', 'Never publish.',
        'private local use only.', 'Private local use only.',
        'local use only.', 'Local-only;', 'local-only;', 'local-only.',
        'Local only.', 'local only.', 'local only;', 'local only,',
        'local-only', 'PRIVATE ', 'Private ', 'private ',
    ):
        note = note.replace(obsolete, '')
    note = re.sub(r'\blocal[- ]only\b[;,]?', '', note, flags=re.IGNORECASE)
    note = re.sub(r'\s+', ' ', note).strip(' ,;.')
    return ('Project-modified and adapted Blacklight motion data. '
            'Initial lineage / technical context: ' + note + '.')


def motion_provenance() -> dict:
    return {
        'schema': 1,
        'currentStatus': 'project_modified_and_adapted',
        'project': 'Blacklight: Ascension',
        'initialLineage': 'Prototype source data / animation reference',
        'sourceRecordScope': 'Initial lineage; not a claim that the current motion is unmodified.',
        'authorshipScope': 'Project modifications and adaptation; not a claim that every keyframe was authored from scratch.',
    }
