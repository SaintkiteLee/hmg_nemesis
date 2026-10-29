from pathlib import Path
import importlib.util,json,os,re,subprocess,tempfile,zipfile
from toolchain import ROOT, OUT, JDK, ANDROID, BT, java_tool, bash_tool, posix
BASH=bash_tool()
def run(cmd,**kw):return subprocess.run([str(a) for a in cmd],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=40,**kw)
def ok(cmd):
 p=run(cmd);assert p.returncode==0,(p.stdout,p.stderr);return p.stdout

def main():
 lines=[]
 with tempfile.TemporaryDirectory(prefix='k5-aa-policy-') as temp:
  classes=Path(temp)/'classes'
  ok([java_tool('javac'),'-d',classes,ROOT/'src/com/saintkite/k5aa/Installation.java',ROOT/'tests/InstallationTest.java',ROOT/'src/com/saintkite/k5aa/UsbSessionRecovery.java',ROOT/'tests/UsbSessionRecoveryTest.java',ROOT/'src/com/saintkite/k5aa/GpmState.java',ROOT/'tests/GpmStateTest.java'])
  result=ok([java_tool('java'),'-cp',classes,'com.saintkite.k5aa.InstallationTest']);print(result,end='');lines.extend(result.splitlines())
  result=ok([java_tool('java'),'-cp',classes,'com.saintkite.k5aa.UsbSessionRecoveryTest']);print(result,end='');lines.extend(result.splitlines())
  result=ok([java_tool('java'),'-cp',classes,'com.saintkite.k5aa.GpmStateTest']);print(result,end='');lines.extend(result.splitlines())
 spec=importlib.util.spec_from_file_location('dex',ROOT/'tools/inspect_dex.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
 for file in [OUT/'runtime.jar',OUT/'K5AASettingsBridge.apk']:
  with zipfile.ZipFile(file) as z:
   assert z.testzip() is None;d=m.Dex(z.read('classes.dex'));assert d.b[:8]==b'dex\n035\0'
   assert not any('InstallationTest' in c or '/tests/' in c for c in d.class_names)
   if file.suffix=='.apk':
    assert 'Lcom/daudio/app/deviceconnections/ui/connectivitySettings/PhoneProjectionSettingsActivity;' in d.class_names
    assert not any('TrialResult' in c or 'ApInfoActivity' in c for c in d.class_names)
   else:
    for name in ['Main','Installation','ApComparison','ApCredentials','UsbSessionRecovery','ExternalSettings']:
     assert 'Lcom/saintkite/k5aa/'+name+';' in d.class_names
   assert not any(any(x in c for x in ['NetworkDiagnostics','HostapdControl','DiagnosticText','Diagnostics;','Trial;','Test;']) for c in d.class_names)
  lines.append('PASS normal DEX contains no diagnostics or test classes: '+file.name)
 with zipfile.ZipFile(OUT/'k5-wireless-aa-0.5.0-usb.zip') as outer:
  assert set(outer.namelist())=={'NEMESIS.data','NEMESIS.sh','README.txt','packages/media-id','packages/k5.wireless.aa.zip'}
  import io
  with zipfile.ZipFile(io.BytesIO(outer.read('packages/k5.wireless.aa.zip'))) as package:
   assert set(package.namelist())=={'NEMESIS.data','lib/runtime.jar','bridge.apk','scripts/pre.sh','svc/setup.sh','bin/restore.sh'}
   assert package.read('lib/runtime.jar')==(OUT/'runtime.jar').read_bytes()
   assert package.read('bridge.apk')==(OUT/'K5AASettingsBridge.apk').read_bytes()
 lines.append('PASS exact nested normal release allowlist and byte-identical runtime payloads')
 for file in [OUT/'usb/NEMESIS.sh',OUT/'package/scripts/pre.sh',OUT/'package/svc/setup.sh',OUT/'package/bin/restore.sh']:
  assert b'\r' not in file.read_bytes();ok([BASH,'-n',file])
 lines.append('PASS LF and shell syntax for installer, package hooks and recovery')
 template=(ROOT/'usb/NEMESIS.sh.in').read_text()
 assert 'remount' not in template
 assert not re.search(r'^\s*(cp|mv|rm|chmod|chown|tee)\b[^\n]*\s/system/',template,re.M)
 lines.append('PASS installer has no system partition writes or remounts')
 # Firmware-source cross-checks were performed privately; copyrighted dumps are not distributed.
 lines.append('PASS public firmware profile matches the runtime firmware gate')
 profile=json.loads((ROOT/'profile.json').read_text())
 assert profile['firmware'] in (ROOT/'src/com/saintkite/k5aa/Main.java').read_text()
 (OUT/'test-results.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8')
 print('\n'.join(lines[-8:]))
if __name__=='__main__':main()
