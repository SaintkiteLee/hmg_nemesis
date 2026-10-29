"""Execute normal USB and package hooks against isolated command doubles."""
from pathlib import Path
import os,shutil,subprocess,tempfile
from toolchain import ROOT,OUT,bash_tool,posix
BASH=bash_tool()

def write(p,text):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8',newline='\n')
def main():
    lines=[]
    with tempfile.TemporaryDirectory(prefix='k5-aa-hooks-') as temp:
        root=Path(temp);mock=root/'mock';mock.mkdir()
        write(mock/'id', '#!/bin/bash\n[ "$TEST_CASE" != nonroot ] || { echo 1000; exit; }; echo 0\n')
        write(mock/'getprop', '#!/bin/bash\n[ "$TEST_CASE" != wrongfirmware ] || { echo other; exit; }; echo DL3.KOR.S5W_M.V039.001.260902\n')
        write(mock/'md5', '''#!/bin/bash
case "$1" in
 /system/framework/framework.odex) hash=eab5af504694910d946f9067310b744a ;;
 /system/framework/services.odex) hash=6ce1dd85f9c873dfa037f75b72807cc4 ;;
 /system/app/PhoneProjection.odex) hash=c19f5f1b49b207a1df7142aaa572877c ;;
 /system/app/HKMC_GPM.odex) hash=def8588772f648a6acedbed8ecdcd5d8 ;;
 *) if [ "$TEST_CASE" = badpayload ]; then echo invalid; exit; else exec /usr/bin/md5sum "$1"; fi ;;
esac
[ "$TEST_CASE" != badfirmwarehash ] || hash=invalid
# Also test toolbox variants that print the digest without a filename.
echo "$hash"
''')
        write(mock/'app_process', '''#!/bin/bash
echo "$3" >> "$TEST_ROOT/calls"
case "$3" in
 snapshot)
   [ "$TEST_CASE" != occupied ] || exit 1
   echo baseline > "$4/original.properties"; echo armed > "$4/phase" ;;
 restore) [ "$TEST_CASE" != restorefail ] || exit 1; echo rolled_back > "$4/phase" ;;
 verify-restored) [ "$TEST_CASE" != verifyfail ] || exit 1; [ "$(cat "$4/phase")" = rolled_back ] ;;
 *) exit 1 ;;
esac
''')
        write(mock/'nemesis', '''#!/bin/bash
case "$1" in
 install)
   [ "$TEST_CASE" != installfail ] || exit 1
   case "$TEST_CASE" in setupfail) echo rolled_back > "$TEST_STATE/phase" ;; *) echo waiting_usb > "$TEST_STATE/phase" ;; esac ;;
 service) exit 0 ;;
 *) exit 1 ;;
esac
''')
        for file in mock.iterdir():file.chmod(0o755)
        def rewritten(text,state):
            text=text.replace('export PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin','export PATH="'+posix(mock)+':/usr/bin:/bin"')
            text=text.replace('STATE=/data/nemesis/k5-wireless-aa','STATE="'+posix(state)+'"')
            text=text.replace('/system/bin/app_process','"'+posix(mock/'app_process')+'"')
            return text.replace('/system/xbin/nemesis','"'+posix(mock/'nemesis')+'"')
        for case in ['ready','nonroot','badpayload','installfail','setupfail','wrongfirmware','badfirmwarehash','occupied','restoreok','restorefail','verifyfail']:
            folder=root/case;state=folder/'private';state.mkdir(parents=True)
            usb=folder/'usb';shutil.copytree(OUT/'usb',usb)
            pkg=folder/'package';shutil.copytree(OUT/'package',pkg)
            env=os.environ.copy();env.update(TEST_ROOT=posix(folder),TEST_STATE=posix(state),TEST_CASE=case,
                K5AA_USB_ROOT=posix(usb),MSYS_NO_PATHCONV='1')
            def execute(file,cwd):
                result=subprocess.run([str(BASH),posix(file)],cwd=cwd,env=env,capture_output=True,text=True,timeout=12)
                return result.returncode
            if case in ['ready','nonroot','badpayload','installfail','setupfail']:
                file=usb/'NEMESIS.sh';write(file,rewritten(file.read_text(),state));rc=execute(file,usb)
                assert (rc==0)==(case=='ready'),(case,rc)
                assert (usb/'NEMESIS.data.used').exists()==(case=='ready'),case
                assert not (usb/'K5-AA-FIX').exists(),case
            elif case in ['wrongfirmware','badfirmwarehash','occupied']:
                file=pkg/'scripts/pre.sh';write(file,rewritten(file.read_text(),state));rc=execute(file,pkg)
                assert rc!=0 and not (state/'original.properties').exists(),case
                assert not (state/'runtime.jar').exists(),case
            else:
                write(state/'original.properties','private-baseline');shutil.copyfile(OUT/'runtime.jar',state/'runtime.jar')
                file=pkg/'bin/restore.sh';write(file,rewritten(file.read_text(),state));rc=execute(file,pkg)
                assert (rc==0)==(case=='restoreok'),case
                assert (state/'original.properties').read_text()=='private-baseline',case
                assert (state/'runtime.jar').is_file(),case
                calls=(folder/'calls').read_text()
                assert ('verify-restored' in calls)==(case!='restorefail'),case
            lines.append('PASS normal USB/package hooks: '+case)
        # A fresh preinstall must retain an independent recovery tool beside the backup.
        case='fresh';folder=root/case;state=folder/'private';state.mkdir(parents=True)
        pkg=folder/'package';shutil.copytree(OUT/'package',pkg);usb=folder/'usb';shutil.copytree(OUT/'usb',usb)
        env.update(TEST_ROOT=posix(folder),TEST_STATE=posix(state),TEST_CASE=case,K5AA_USB_ROOT=posix(usb))
        file=pkg/'scripts/pre.sh';write(file,rewritten(file.read_text(),state));assert execute(file,pkg)==0
        assert (state/'runtime.jar').read_bytes()==(OUT/'runtime.jar').read_bytes()
        assert (state/'restore.sh').is_file()
        lines.append('PASS independent recovery tools retained outside installed package')
    (OUT/'shell-test-results.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8');print('\n'.join(lines))
if __name__=='__main__':main()
