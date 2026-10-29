"""Normal NEMESIS package. Optional diagnostics are never part of this allowlist."""
from pathlib import Path
import hashlib,json,shutil,zipfile,uuid
from toolchain import ROOT,OUT
VERSION='0.5.0'

def write(path,text):path.write_text(text,encoding='utf-8',newline='\n')
def archive(source,target):
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(source.rglob('*')):
            if p.is_file():z.write(p,p.relative_to(source).as_posix())

def main():
    OUT.mkdir(exist_ok=True);pkg=OUT/'package';usb=OUT/'usb'
    for path in (pkg,usb):
        if path.exists():shutil.rmtree(path)
        path.mkdir()
    (pkg/'lib').mkdir();(usb/'packages').mkdir()
    shutil.copyfile(OUT/'runtime.jar',pkg/'lib/runtime.jar')
    shutil.copyfile(OUT/'K5AASettingsBridge.apk',pkg/'bridge.apk')
    profile=json.loads((ROOT/'profile.json').read_text())
    receipt=json.loads((OUT/'build-receipt.json').read_text())
    replacements={
        '@BRIDGE_SHA256@':receipt['bridgeSHA256'],
        '@FIRMWARE@':profile['firmware'],
        **{'@'+name.split('.')[0].upper()+'_MD5@':value for name,value in profile['md5'].items()}}
    for p in sorted((ROOT/'package').rglob('*')):
        if p.is_file():
            dest=pkg/p.relative_to(ROOT/'package');dest.parent.mkdir(parents=True,exist_ok=True)
            text=p.read_text(encoding='utf-8')
            for key,value in replacements.items():text=text.replace(key,value)
            assert '@' not in text, 'Unexpanded package template: '+p.name
            write(dest,text)
    payload=usb/'packages/k5.wireless.aa.zip';archive(pkg,payload)
    text=(ROOT/'usb/NEMESIS.sh.in').read_text().replace('@PACKAGE_MD5@',hashlib.md5(payload.read_bytes()).hexdigest())
    write(usb/'NEMESIS.sh',text);write(usb/'NEMESIS.data',(ROOT/'usb/NEMESIS.data').read_text())
    write(usb/'packages/media-id','k5-wireless-aa:'+str(uuid.uuid4())+'\n')
    write(usb/'README.txt',(ROOT/'USB_README.txt').read_text())
    archive(usb,OUT/'k5-wireless-aa-0.5.0-usb.zip')
    manifest=[{'path':p.relative_to(usb).as_posix(),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(usb.rglob('*')) if p.is_file()]
    write(OUT/'USB-SHA256SUMS.json',json.dumps(manifest,indent=2)+'\n')
    print('Normal USB files: '+str(len(manifest)))
if __name__=='__main__':main()
