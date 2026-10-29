from pathlib import Path
import hashlib,json,shutil,zipfile,uuid
from toolchain import ROOT, OUT
USB=OUT/'usb'
receipt=json.loads((OUT/'build-receipt.json').read_text())
pkg=OUT/'guard-package';(pkg/'lib').mkdir(parents=True,exist_ok=True)
shutil.copyfile(OUT/'guard.jar',pkg/'lib/guard.jar')
shutil.copyfile(OUT/'K5AASettingsBridge.apk',pkg/'bridge.apk')
def write(p,text):p.write_text(text,encoding='utf-8',newline='\n')
write(pkg/'NEMESIS.data','''[nemesis]
version=0.4.4
id=k5.aa.apalign.guard
name=K5 AA reversible AP credential alignment guard
types=service
preuninstall=preun.sh

[service.0]
name=monitor
kind=sh
path=run.sh
uid=root
mode=restart
''')
write(pkg/'run.sh','''#!/system/bin/sh
export PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
ROOT=/data/nemesis/apps/k5.aa.apalign.guard
export CLASSPATH="$ROOT/lib/guard.jar"
exec /system/bin/app_process /system/bin --nice-name=k5-aa-guard com.saintkite.k5aa.Main run /data/nemesis/k5-aa-apalign "$ROOT/bridge.apk"
''')
write(pkg/'preun.sh','''#!/system/bin/sh
export PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
STATE=/data/nemesis/k5-aa-apalign
case "$(cat "$STATE/phase" 2>/dev/null)" in committed|rolled_back) exit 0 ;; esac
echo rollback > "$STATE/decision"
CLASSPATH="$STATE/guard.jar" /system/bin/app_process /system/bin com.saintkite.k5aa.Main cleanup "$STATE"
''')
(USB/'aa').mkdir(parents=True,exist_ok=True)
write(USB/'aa/trial-media-id','k5-aa-network-0.4.4:'+str(uuid.uuid4())+'\n')
with zipfile.ZipFile(USB/'aa/k5.aa.apalign.guard.zip','w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(pkg.rglob('*')):
        if p.is_file():z.write(p,p.relative_to(pkg).as_posix())
shutil.copyfile(OUT/'guard.jar',USB/'aa/guard.jar')
shutil.copyfile(ROOT/'usb/restore.sh',USB/'aa/restore.sh')
write(USB/'NEMESIS.data',(ROOT/'usb/NEMESIS.data').read_text())
profile=json.loads((ROOT/'profile.json').read_text())
values={
 '@FRAMEWORK_MD5@':profile['md5']['framework.odex'],
 '@SERVICES_MD5@':profile['md5']['services.odex'],
 '@PHONE_MD5@':profile['md5']['PhoneProjection.odex'],
 '@GPM_MD5@':profile['md5']['HKMC_GPM.odex'],
 '@GUARD_MD5@':hashlib.md5((OUT/'guard.jar').read_bytes()).hexdigest(),
 '@PACKAGE_MD5@':hashlib.md5((USB/'aa/k5.aa.apalign.guard.zip').read_bytes()).hexdigest(),
 '@BRIDGE_SHA256@':receipt['bridgeSHA256'],
}
text=(ROOT/'usb/NEMESIS.sh.in').read_text()
for old,new in values.items():text=text.replace(old,new)
assert not any(key in text for key in values)
write(USB/'NEMESIS.sh',text)
write(USB/'README.txt',(ROOT/'USB_README.txt').read_text(encoding='utf-8'))
files=[p for p in sorted(USB.rglob('*')) if p.is_file()]
manifest=[{'path':p.relative_to(USB).as_posix(),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in files]
(OUT/'USB-SHA256SUMS.json').write_text(json.dumps(manifest,indent=2)+'\n')
with zipfile.ZipFile(OUT/'aa-ap-fix-0.4.4-usb.zip','w',zipfile.ZIP_DEFLATED) as z:
    for p in files:z.write(p,p.relative_to(USB).as_posix())
with zipfile.ZipFile(OUT/'aa-ap-fix-0.4.4-usb.zip') as z:assert z.testzip() is None
with zipfile.ZipFile(OUT/'aa-ap-fix-0.4.4-source.zip','w',zipfile.ZIP_DEFLATED) as z:
    for prefix in ['src','bridge','usb','tests','tools']:
        for p in sorted((ROOT/prefix).rglob('*')):
            if p.is_file():z.write(p,p.relative_to(ROOT).as_posix())
    for name in ['build.py','package.py','test.py','test_adapter.py','test_shell.py','toolchain.py','profile.json','README.md','USB_README.txt']:z.write(ROOT/name,name)
print(json.dumps({'usbFiles':len(files),'usbStage':str(USB),'guardPackageBytes':(USB/'aa/k5.aa.apalign.guard.zip').stat().st_size},indent=2))
