#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
String Quality Checker for Android Localization
Checks for:
1. Parameter compatibility (format string parameters)
2. XML escape characters (' and &)
3. Missing translations (English-only strings)
"""

import re
import xml.etree.ElementTree as ET
from pathlib import Path
from collections import defaultdict

# Language folders
LANGUAGES = {
    'tr': 'values',
    'en': 'values-en',
    'es': 'values-es',
    'fr': 'values-fr',
    'pt': 'values-pt',
    'in': 'values-in',
    'hi': 'values-hi',
    'ja': 'values-ja',
    'th': 'values-th'
}

BASE_LANG = 'tr'  # Turkish is the base language

def extract_parameters(text):
    """Extract all format parameters from a string"""
    # Match patterns like %1$s, %2$d, %1$.1f, etc.
    pattern = r'%(\d+)\$[sd\.]?[0-9]?[fd]?'
    matches = re.findall(pattern, text)
    return sorted([int(m) for m in matches])

def check_xml_escape(text):
    """Check for unescaped XML characters"""
    issues = []
    # Check for unescaped apostrophes (should be \' or &apos;)
    # But in Android strings.xml, we use \' for apostrophes
    if "'" in text and "\\'" not in text and "&apos;" not in text:
        # Check if it's not already escaped properly
        unescaped_apos = re.findall(r"[^\\]'", text)
        if unescaped_apos:
            issues.append("Unescaped apostrophe (')")
    
    # Check for unescaped ampersand (should be &amp;)
    if "&" in text and "&amp;" not in text and "&apos;" not in text and "&lt;" not in text and "&gt;" not in text:
        issues.append("Unescaped ampersand (&)")
    
    return issues

def parse_strings_xml(file_path):
    """Parse strings.xml and return a dict of name -> text"""
    try:
        tree = ET.parse(file_path)
        root = tree.getroot()
        strings = {}
        for string_elem in root.findall('.//string'):
            name = string_elem.get('name')
            text = string_elem.text or ''
            strings[name] = text
        return strings
    except Exception as e:
        print(f"Error parsing {file_path}: {e}")
        return {}

def main():
    base_dir = Path('app/src/main/res')
    base_file = base_dir / LANGUAGES[BASE_LANG] / 'strings.xml'
    
    # Parse all language files
    all_strings = {}
    for lang_code, lang_folder in LANGUAGES.items():
        file_path = base_dir / lang_folder / 'strings.xml'
        if file_path.exists():
            all_strings[lang_code] = parse_strings_xml(file_path)
        else:
            print(f"Warning: {file_path} not found")
            all_strings[lang_code] = {}
    
    # Get all string keys from base language
    base_strings = all_strings[BASE_LANG]
    all_keys = set(base_strings.keys())
    
    # Add keys from other languages (in case some are missing in base)
    for lang_code, strings in all_strings.items():
        all_keys.update(strings.keys())
    
    # Results storage
    param_issues = []
    xml_escape_issues = []
    missing_translations = []
    
    # Check each string key
    for key in sorted(all_keys):
        base_text = base_strings.get(key, '')
        base_params = extract_parameters(base_text)
        
        # Check parameter compatibility
        for lang_code, lang_folder in LANGUAGES.items():
            if lang_code == BASE_LANG:
                continue
            
            lang_strings = all_strings[lang_code]
            if key not in lang_strings:
                continue
            
            lang_text = lang_strings[key]
            lang_params = extract_parameters(lang_text)
            
            if base_params != lang_params:
                param_issues.append({
                    'key': key,
                    'base_lang': BASE_LANG,
                    'base_params': base_params,
                    'base_text': base_text[:50] + '...' if len(base_text) > 50 else base_text,
                    'lang': lang_code,
                    'lang_params': lang_params,
                    'lang_text': lang_text[:50] + '...' if len(lang_text) > 50 else lang_text
                })
        
        # Check XML escape characters
        for lang_code, lang_folder in LANGUAGES.items():
            lang_strings = all_strings[lang_code]
            if key not in lang_strings:
                continue
            
            lang_text = lang_strings[key]
            escape_issues = check_xml_escape(lang_text)
            
            if escape_issues:
                xml_escape_issues.append({
                    'key': key,
                    'lang': lang_code,
                    'text': lang_text[:100] + '...' if len(lang_text) > 100 else lang_text,
                    'issues': escape_issues
                })
        
        # Check for missing translations (English-only strings)
        base_text_lower = base_text.lower().strip()
        for lang_code, lang_folder in LANGUAGES.items():
            if lang_code == BASE_LANG or lang_code == 'en':
                continue
            
            lang_strings = all_strings[lang_code]
            if key not in lang_strings:
                continue
            
            lang_text = lang_strings[key]
            lang_text_lower = lang_text.lower().strip()
            en_text = all_strings.get('en', {}).get(key, '')
            en_text_lower = en_text.lower().strip()
            
            # If the translation is the same as English (and not empty), it might be untranslated
            if lang_text_lower == en_text_lower and lang_text_lower and en_text_lower:
                # But skip if it's a technical term, number, or very short
                if len(lang_text) > 3 and not lang_text.isdigit():
                    missing_translations.append({
                        'key': key,
                        'lang': lang_code,
                        'text': lang_text
                    })
    
    # Print results
    print("=" * 80)
    print("STRING QUALITY CHECK REPORT")
    print("=" * 80)
    print()
    
    print("1. PARAMETER COMPATIBILITY ISSUES")
    print("-" * 80)
    if param_issues:
        print(f"Found {len(param_issues)} parameter mismatch(es):\n")
        for issue in param_issues:
            print(f"  Key: {issue['key']}")
            print(f"  Base ({issue['base_lang']}): {issue['base_params']} params - '{issue['base_text']}'")
            print(f"  {issue['lang']}: {issue['lang_params']} params - '{issue['lang_text']}'")
            print()
    else:
        print("✓ No parameter compatibility issues found!")
    print()
    
    print("2. XML ESCAPE CHARACTER ISSUES")
    print("-" * 80)
    if xml_escape_issues:
        print(f"Found {len(xml_escape_issues)} XML escape issue(s):\n")
        for issue in xml_escape_issues:
            print(f"  Key: {issue['key']} ({issue['lang']})")
            print(f"  Issues: {', '.join(issue['issues'])}")
            print(f"  Text: '{issue['text']}'")
            print()
    else:
        print("✓ No XML escape issues found!")
    print()
    
    print("3. POTENTIALLY MISSING TRANSLATIONS")
    print("-" * 80)
    if missing_translations:
        print(f"Found {len(missing_translations)} potentially untranslated string(s):\n")
        # Group by language
        by_lang = defaultdict(list)
        for item in missing_translations:
            by_lang[item['lang']].append(item)
        
        for lang, items in sorted(by_lang.items()):
            print(f"  {lang.upper()}: {len(items)} string(s)")
            for item in items[:10]:  # Show first 10
                print(f"    - {item['key']}: '{item['text']}'")
            if len(items) > 10:
                print(f"    ... and {len(items) - 10} more")
            print()
    else:
        print("✓ No obvious missing translations found!")
    print()
    
    print("=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print(f"Parameter Issues: {len(param_issues)}")
    print(f"XML Escape Issues: {len(xml_escape_issues)}")
    print(f"Missing Translations: {len(missing_translations)}")
    print()
    
    # Write detailed report to file
    with open('STRING_QUALITY_REPORT.md', 'w', encoding='utf-8') as f:
        f.write("# String Quality Check Report\n\n")
        f.write("This report identifies potential issues in string resources that could cause runtime crashes.\n\n")
        
        f.write("## 1. Parameter Compatibility Issues\n\n")
        if param_issues:
            f.write(f"**CRITICAL: Found {len(param_issues)} parameter mismatch(es)**\n\n")
            f.write("These can cause `IllegalFormatException` at runtime!\n\n")
            for issue in param_issues:
                f.write(f"### {issue['key']}\n\n")
                f.write(f"- **Base ({issue['base_lang']})**: `{issue['base_params']}` parameters\n")
                f.write(f"  - Text: `{issue['base_text']}`\n")
                f.write(f"- **{issue['lang']}**: `{issue['lang_params']}` parameters\n")
                f.write(f"  - Text: `{issue['lang_text']}`\n\n")
        else:
            f.write("✓ No issues found.\n\n")
        
        f.write("## 2. XML Escape Character Issues\n\n")
        if xml_escape_issues:
            f.write(f"**WARNING: Found {len(xml_escape_issues)} XML escape issue(s)**\n\n")
            for issue in xml_escape_issues:
                f.write(f"### {issue['key']} ({issue['lang']})\n\n")
                f.write(f"- **Issues**: {', '.join(issue['issues'])}\n")
                f.write(f"- **Text**: `{issue['text']}`\n\n")
        else:
            f.write("✓ No issues found.\n\n")
        
        f.write("## 3. Potentially Missing Translations\n\n")
        if missing_translations:
            f.write(f"**INFO: Found {len(missing_translations)} potentially untranslated string(s)**\n\n")
            by_lang = defaultdict(list)
            for item in missing_translations:
                by_lang[item['lang']].append(item)
            
            for lang, items in sorted(by_lang.items()):
                f.write(f"### {lang.upper()} ({len(items)} strings)\n\n")
                for item in items:
                    f.write(f"- `{item['key']}`: `{item['text']}`\n")
                f.write("\n")
        else:
            f.write("✓ No obvious issues found.\n\n")
    
    print("Detailed report saved to: STRING_QUALITY_REPORT.md")

if __name__ == '__main__':
    main()
