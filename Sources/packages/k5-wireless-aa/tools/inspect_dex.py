"""Inventory DEX/ODEX signatures without deoptimizing or executing vehicle code.

Definitions and references are kept separate. Name matches identify analysis candidates,
not readable/live signals. Works on KitKat ODEX and classes*.dex in APK/JAR archives.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import struct
import zipfile

CATEGORIES = {
    'temperature': r'coolant|temperature|(?:engine|intake|oil|transmission|ambient|water)[_a-z]*temp',
    'turbo': r'turbo|boost|wastegate|manifold|barometr|intake[_a-z]*pressure',
    'cvvd': r'cvvd|valve[_a-z]*duration|duration[_a-z]*valve',
    'cvvt_not_cvvd': r'cvvt|camshaft|cam[_a-z]*phaser|valve[_a-z]*timing',
    'transmission': r'gear|transmission|clutch|torque[_a-z]*convert|(?:^|[/_])tcu',
    'engine_speed': r'engine[_a-z]*speed|rpm|tachometer',
    'vehicle_metadata': r'vehicleinfo|vehiclespeed|candb|fueltype|vehicletrim|classstatus',
}

def categories(text):
    return [k for k, pattern in CATEGORIES.items() if re.search(pattern, text, re.I)]

class Dex:
    def __init__(self, data):
        if data[:4] == b'dey\n':
            off, length = struct.unpack_from('<II', data, 8)
            if off < 40 or length > len(data) - off: raise ValueError('Invalid ODEX extent')
            data = data[off:off+length]
        if data[:4] != b'dex\n' or len(data) < 112: raise ValueError('Not a DEX container')
        self.b = data
        if self.u32(40) != 0x12345678: raise ValueError('Unsupported DEX endian tag')
        if self.u32(32) > len(data): raise ValueError('Truncated DEX')
        self.strings = []
        count, table = self.u32(56), self.u32(60)
        self.check(table, count * 4)
        for i in range(count):
            pos = self.u32(table + 4*i)
            _, pos = self.uleb(pos)
            end = data.find(b'\0', pos)
            if end == -1: raise ValueError('Unterminated DEX string')
            self.strings.append(data[pos:end].replace(b'\xc0\x80', b'\0').decode('utf-8', 'replace'))
        count, table = self.u32(64), self.u32(68)
        self.check(table, count*4)
        self.types = [self.strings[self.u32(table+4*i)] for i in range(count)]
        self.protos = []
        count, table = self.u32(72), self.u32(76)
        self.check(table, count*12)
        for i in range(count):
            _, ret, params = struct.unpack_from('<III', data, table + 12*i)
            args = []
            if params:
                n = self.u32(params); self.check(params+4, n*2)
                args = [self.types[struct.unpack_from('<H', data, params+4+2*j)[0]] for j in range(n)]
            self.protos.append('(' + ''.join(args) + ')' + self.types[ret])
        self.defined = set()
        self.defined_fields = set()
        self.class_names = []
        count, table = self.u32(96), self.u32(100)
        self.check(table, count*32)
        for i in range(count):
            row = table+32*i
            self.class_names.append(self.types[self.u32(row)])
            pos = self.u32(row+24)
            if not pos: continue
            counts = []
            for _ in range(4):
                n, pos = self.uleb(pos); counts.append(n)
            for n in counts[:2]:
                index = 0
                for _ in range(n):
                    delta, pos = self.uleb(pos); index += delta
                    _, pos = self.uleb(pos); self.defined_fields.add(index)
            for n in counts[2:]:
                index = 0
                for _ in range(n):
                    delta, pos = self.uleb(pos); index += delta
                    _, pos = self.uleb(pos); _, pos = self.uleb(pos)
                    self.defined.add(index)
        self.methods = []
        count, table = self.u32(88), self.u32(92)
        self.check(table, count*8)
        for i in range(count):
            cls, proto, name = struct.unpack_from('<HHI', data, table+8*i)
            self.methods.append({'owner': self.types[cls], 'name': self.strings[name],
                                 'descriptor': self.protos[proto], 'defined_here': i in self.defined})
        self.fields = []
        count, table = self.u32(80), self.u32(84)
        self.check(table, count*8)
        for i in range(count):
            cls, type_index, name = struct.unpack_from('<HHI', data, table+8*i)
            self.fields.append({'owner': self.types[cls], 'name': self.strings[name],
                                'descriptor': self.types[type_index],
                                'defined_here': i in self.defined_fields})
    def check(self, off, size):
        if off < 0 or size < 0 or off > len(self.b)-size: raise ValueError('DEX extent out of range')
    def u32(self, off):
        self.check(off, 4); return struct.unpack_from('<I', self.b, off)[0]
    def uleb(self, pos):
        value = 0
        for shift in range(0, 35, 7):
            self.check(pos, 1); b = self.b[pos]; pos += 1
            value |= (b & 127) << shift
            if b < 128: return value, pos
        raise ValueError('Invalid ULEB128')

def containers(path):
    if path.suffix.lower() in ('.jar', '.apk'):
        with zipfile.ZipFile(path) as z:
            for name in sorted(z.namelist()):
                if re.fullmatch(r'classes\d*\.dex', name):
                    if z.getinfo(name).file_size > 256*1024*1024: raise ValueError('Oversized DEX')
                    yield name, z.read(name)
    else:
        yield '', path.read_bytes()

def analyze(inputs):
    files = sorted({p for root in inputs for p in (root.rglob('*') if root.is_dir() else [root])
                    if p.suffix.lower() in ('.jar', '.apk', '.odex', '.dex')})
    result = {'warning': 'Names/signatures are candidates only; no live access or units verified.',
              'inputs': [], 'candidates': [], 'oem_methods': [], 'oem_fields': [],
              'candidate_strings': [], 'errors': []}
    for path in files:
        record = {'path': str(path.resolve()), 'bytes': path.stat().st_size,
                  'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'dex_members': []}
        result['inputs'].append(record)
        try:
            for member, data in containers(path):
                dex = Dex(data)
                origin = str(path.resolve()) + ('!'+member if member else '')
                record['dex_members'].append({'name': member, 'classes': len(dex.class_names),
                                             'methods': len(dex.methods)})
                for method in dex.methods:
                    owner = method['owner']
                    # Restrict framework rows to OEM classes. Stock app overrides are also candidates.
                    if owner.startswith('Lcom/hkmc/'):
                        result['oem_methods'].append({'source': origin, **method})
                    text = owner + ' ' + method['name']
                    groups = categories(text)
                    if groups and (owner.startswith('Lcom/hkmc/') or method['defined_here']):
                        result['candidates'].append({'source': origin, 'kind': 'method', 'categories': groups, **method})
                for field in dex.fields:
                    if field['owner'].startswith('Lcom/hkmc/'):
                        result['oem_fields'].append({'source': origin, **field})
                        groups = categories(field['owner']+' '+field['name'])
                        if groups:
                            result['candidates'].append({'source': origin, 'kind': 'field',
                                                         'categories': groups, **field})
                for value in dex.strings:
                    if len(value) < 240 and re.search(r'cvvd|cvvt|coolant|turbo|wastegate|manifold|transmission|oil.?temp|boost.?pressure', value, re.I):
                        result['candidate_strings'].append({'source': origin, 'value': value,
                                                            'categories': categories(value)})
        except (ValueError, IndexError, struct.error, zipfile.BadZipFile) as e:
            result['errors'].append({'path': str(path), 'error': str(e)})
    result['framework_present'] = {
        name: any(Path(x['path']).name == name for x in result['inputs'])
        for name in ('automotive.odex', 'automotive-service.odex', 'com.hkmc.ext.hcan.odex')
    }
    result['category_counts'] = {
        cat: {'definitions': sum(cat in x['categories'] and x['defined_here'] for x in result['candidates']),
              'references': sum(cat in x['categories'] and not x['defined_here'] for x in result['candidates'])}
        for cat in CATEGORIES}
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inputs', nargs='+', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    for p in args.inputs:
        if not p.exists(): parser.error(f'Input missing: {p}')
    result = analyze(args.inputs)
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out/'dex-inventory.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    text = ['K5 stock signature inventory', result['warning'],
            'The three full OEM frameworks are required before drawing absence conclusions.',
            'Full framework files present: '+json.dumps(result['framework_present']), '']
    for cat, counts in result['category_counts'].items():
        text.append(f'{cat}: {counts["definitions"]} definitions, {counts["references"]} references')
    text.append('\nOEM candidate signatures (deduplicated):')
    signatures = sorted({(tuple(x['categories']), x['owner'], x['name'], x['descriptor'], x['defined_here'])
                         for x in result['candidates'] if x['owner'].startswith('Lcom/hkmc/')})
    for groups, owner, name, desc, defined in signatures:
        text.append(f'{"DEF" if defined else "REF"} [{",".join(groups)}] {owner}->{name}{desc}')
    if result['errors']: text.append('\nERRORS: '+json.dumps(result['errors']))
    (args.out/'summary.txt').write_text('\n'.join(text)+'\n', encoding='utf8')
    print(json.dumps({'files': len(result['inputs']), 'framework_present': result['framework_present'],
                      'categories': result['category_counts'], 'errors': result['errors']}, indent=2))

if __name__ == '__main__': main()
