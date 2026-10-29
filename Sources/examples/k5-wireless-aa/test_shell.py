"""Exercise the packaged USB script with isolated Android command doubles."""
from pathlib import Path
import os,shutil,subprocess,tempfile
from toolchain import ROOT, OUT, bash_tool, posix
BASH=bash_tool()
def unix(p):return posix(p)
def write(p,s):p.write_text(s,encoding='utf-8',newline='\n')
def main():
 lines=[]
 with tempfile.TemporaryDirectory(prefix='k5-aa-usb-tests-') as temp:
  base=Path(temp);mock=base/'mock';mock.mkdir()
  write(mock/'id','#!/bin/bash\nif [ "$TEST_CASE" = nonroot ]; then echo "uid=1000(system)"; else echo "uid=0(root)"; fi\n')
  write(mock/'getprop','#!/bin/bash\nif [ "$TEST_CASE" = wrongfirmware ]; then echo other; else echo DL3.KOR.S5W_M.V039.001.260902; fi\n')
  write(mock/'md5','''#!/bin/bash
case "$1" in
 /system/framework/framework.odex) echo 'eab5af504694910d946f9067310b744a' ;;
 /system/framework/services.odex) echo '6ce1dd85f9c873dfa037f75b72807cc4' ;;
 /system/app/PhoneProjection.odex) echo 'c19f5f1b49b207a1df7142aaa572877c' ;;
 /system/app/HKMC_GPM.odex) echo 'def8588772f648a6acedbed8ecdcd5d8' ;;
 *) if [ "$TEST_CASE" = badhash ]; then echo invalid; else /usr/bin/md5sum "$1"; fi ;;
esac
''')
  write(mock/'app_process','''#!/bin/bash
case "$3" in
 snapshot) echo original > "$4/original.properties"; echo baseline > "$4/baseline.txt"; echo armed > "$4/phase" ;;
 restore) echo rolled_back > "$4/phase" ;;
 verify-restored) [ "$TEST_CASE" != previousmismatch ] || exit 1; echo INDEPENDENT_RESTORE_VERIFIED ;;
 status) echo "phase=$(cat "$4/phase")" ;;
 *) exit 1 ;;
esac
''')
  write(mock/'nemesis','''#!/bin/bash
case "$1" in
 install) mkdir -p "$TEST_APPS/k5.aa.apalign.guard"; echo authorization-evidence > "$TEST_STATE/ap-auth-events.txt"; echo lease-and-data-evidence > "$TEST_STATE/network-snapshots.txt";
 case "$TEST_CASE" in installfail) exit 1 ;; apfail) echo rolled_back > "$TEST_STATE/phase" ;; *) echo testing > "$TEST_STATE/phase" ;; esac ;;
 uninstall) [ "$TEST_CASE" != previouscleanupfail ] || exit 1
 if [ "$2" != k5.aa.apcompare.guard ]; then
 case "$(cat "$TEST_STATE/phase")" in committed|rolled_back) echo cleanup >> "$TEST_STATE/cleanup" ;; *) exit 99 ;; esac
 fi
 rmdir "$TEST_APPS/$2" ;;
 service) exit 0 ;;
 *) exit 1 ;;
esac
''')
  write(mock/'am','''#!/bin/bash
case "$*" in *ApInfoActivity*) exit 0 ;; esac
case "$TEST_CASE" in userrollback) echo rolled_back > "$TEST_STATE/phase" ;; *) echo committed > "$TEST_STATE/phase" ;; esac
''')
  for p in mock.iterdir():p.chmod(0o755)
  for case in ['keep','userrollback','installfail','apfail','wrongfirmware','badhash','nonroot','pending','final','legacypending','previousrolledback','previousmismatch','previouscleanupfail']:
   root=base/case;usb=root/'usb';state=root/'private';root.mkdir();state.mkdir()
   shutil.copytree(OUT/'usb',usb)
   text=(usb/'NEMESIS.sh').read_text().replace('export PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin','export PATH="'+unix(mock)+':/usr/bin:/bin"')
   assert 'STATE=/data/nemesis/k5-aa-apalign' in text
   text=text.replace('STATE=/data/nemesis/k5-aa-apalign','STATE="'+unix(state)+'"')
   text=text.replace('for old in /data/nemesis/k5-aa-fix /data/nemesis/k5-aa-session-fix /data/nemesis/k5-aa-network-diag /data/nemesis/k5-aa-auth-diag /data/nemesis/k5-aa-authwait-diag /data/nemesis/k5-aa-apcompare; do','for old in "'+unix(root/'previous-private')+'"; do')
   text=text.replace('/system/bin/app_process','"'+unix(mock/'app_process')+'"').replace('/system/xbin/nemesis','"'+unix(mock/'nemesis')+'"')
   text=text.replace('/data/nemesis/apps/',unix(root/'apps')+'/')
   write(usb/'NEMESIS.sh',text)
   if case in ['pending','final']:
    write(state/'phase','applying' if case=='pending' else 'committed');write(state/'original.properties','original')
   text=(usb/'NEMESIS.sh').read_text().replace('PREVIOUS=/data/nemesis/k5-aa-apcompare','PREVIOUS="'+unix(root/'previous-private')+'"')
   write(usb/'NEMESIS.sh',text)
   if case in ['previousrolledback','previousmismatch','previouscleanupfail']:
    previous=root/'previous-private';previous.mkdir();write(previous/'phase','rolled_back')
    (root/'apps/k5.aa.apcompare.guard').mkdir(parents=True)
   if case=='legacypending':
    previous=root/'previous-private';previous.mkdir();write(previous/'phase','testing')
   env=os.environ.copy();env.update(TEST_CASE=case,TEST_STATE=unix(state),TEST_APPS=unix(root/'apps'),MSYS_NO_PATHCONV='1')
   def execute():
    p=subprocess.run([str(BASH),unix(usb/'NEMESIS.sh')],env=env,capture_output=True,text=True,timeout=25)
    return p.returncode
   rc=execute()
   phase=(state/'phase').read_text().strip() if (state/'phase').exists() else ''
   if case in ['wrongfirmware','badhash','nonroot','legacypending','previousmismatch','previouscleanupfail']:
    assert rc!=0 and not phase and not (state/'original.properties').exists(),case
   elif case=='previousrolledback':
    assert rc==0 and phase=='testing',case
    assert any('INDEPENDENT_RESTORE_VERIFIED' in p.read_text() for p in (usb/'K5-AA-FIX').rglob('previous-restore-verification.txt')),case
    assert any('PREVIOUS_GUARD_ABSENT_VERIFIED' in p.read_text() for p in (usb/'K5-AA-FIX').rglob('previous-guard-cleanup.txt')),case
   elif case in ['keep','userrollback']:
    assert rc==0 and phase=='testing' and (usb/'NEMESIS.data').exists(),(case,rc,phase,[p.read_text() for p in usb.rglob('console.txt')])
    assert execute()==0,case
    assert (state/'phase').read_text().strip()==('committed' if case=='keep' else 'rolled_back'),case
    assert (usb/'NEMESIS.data.used').exists(),case
    exports=list((usb/'K5-AA-FIX').rglob('ap-auth-events.txt'));assert exports and exports[-1].read_text().strip()=='authorization-evidence'
    assert any(p.read_text().strip()=='lease-and-data-evidence' for p in (usb/'K5-AA-FIX').rglob('network-snapshots.txt'))
    if case=='keep':assert (state/'export-complete').exists() and not (state/'cleanup').exists(),case
    else:assert any('GUARD_ABSENT_VERIFIED' in p.read_text() for p in (usb/'K5-AA-FIX').rglob('guard-cleanup.txt')),case
   elif case in ['apfail','installfail']:
    assert rc!=0 and phase=='rolled_back' and (usb/'NEMESIS.data.used').exists(),case
   else:assert rc==0 and (usb/'NEMESIS.data.used').exists(),case
   lines.append('PASS packaged USB workflow: '+case)
 (OUT/'shell-test-results.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8');print('\n'.join(lines))
if __name__=='__main__':main()
