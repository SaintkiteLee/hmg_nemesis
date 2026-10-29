from pathlib import Path
import hashlib,json,shutil,subprocess,zipfile
from toolchain import ROOT, OUT, ANDROID, BT, java_tool
def run(*a):
    p=subprocess.run([str(x) for x in a],capture_output=True,text=True,encoding='utf-8',errors='replace')
    if p.returncode:raise RuntimeError(p.stdout+'\n'+p.stderr)
    return p.stdout+p.stderr
def main():
    OUT.mkdir(parents=True,exist_ok=True);b=ROOT/'build'
    for name in ['bridge-classes','bridge-dex','helper-classes','helper-dex']:
        target=b/name
        if target.exists():
            assert target.resolve().is_relative_to(ROOT.resolve());shutil.rmtree(target)
        target.mkdir(parents=True)
    def compile_set(src,classes,dex):
        run(java_tool('javac'),'-encoding','UTF-8','-source','7','-target','7','-bootclasspath',ANDROID,
            '-d',classes,*sorted(src.rglob('*.java')))
        run(java_tool('java'),'-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','19','--lib',ANDROID,
            '--release','--output',dex,*sorted(classes.rglob('*.class')))
    compile_set(ROOT/'src',b/'helper-classes',b/'helper-dex')
    with zipfile.ZipFile(OUT/'guard.jar','w',zipfile.ZIP_DEFLATED) as z:z.write(b/'helper-dex/classes.dex','classes.dex')
    compile_set(ROOT/'bridge/src',b/'bridge-classes',b/'bridge-dex')
    run(BT/('aapt.exe' if __import__('os').name=='nt' else 'aapt'),'package','-f','-M',ROOT/'bridge/AndroidManifest.xml','-I',ANDROID,'-F',b/'resources.apk')
    shutil.copyfile(b/'resources.apk',b/'unsigned.apk')
    with zipfile.ZipFile(b/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:z.write(b/'bridge-dex/classes.dex','classes.dex')
    private=ROOT/'.private';private.mkdir(exist_ok=True);key=private/'bridge.keystore'
    if not key.exists():
        run(java_tool('keytool'),'-genkeypair','-keystore',key,'-alias','k5aa','-storepass','android','-keypass','android',
            '-keyalg','RSA','-keysize','2048','-validity','3650','-dname','CN=K5 AA Compatibility,O=Local Development,C=KR')
    security=b/'allow-sha1.security';security.write_text('jdk.jar.disabledAlgorithms=MD2, MD5, RSA keySize < 1024\n')
    shutil.copyfile(b/'unsigned.apk',b/'signed.apk')
    run(java_tool('jarsigner'),'-J-Djava.security.properties='+str(security),'-sigalg','SHA1withRSA','-digestalg','SHA1',
        '-keystore',key,'-storepass','android','-keypass','android',b/'signed.apk','k5aa')
    apk=OUT/'K5AASettingsBridge.apk';run(BT/('zipalign.exe' if __import__('os').name=='nt' else 'zipalign'),'-f','4',b/'signed.apk',apk);run(BT/('zipalign.exe' if __import__('os').name=='nt' else 'zipalign'),'-c','4',apk)
    verify=run(java_tool('java'),'-jar',BT/'lib/apksigner.jar','verify','--verbose','--min-sdk-version','19','--max-sdk-version','19',apk)
    assert 'v1 scheme (JAR signing): true' in verify
    (OUT/'signature-verification.txt').write_text(verify)
    badge=run(BT/('aapt.exe' if __import__('os').name=='nt' else 'aapt'),'dump','badging',apk);assert "sdkVersion:'19'" in badge
    (OUT/'apk-badging.txt').write_text(badge,encoding='utf-8')
    receipt={'bridgePackage':'com.daudio.app.deviceconnections','bridgeSHA256':hashlib.sha256(apk.read_bytes()).hexdigest(),
        'guardSHA256':hashlib.sha256((OUT/'guard.jar').read_bytes()).hexdigest(),'minSdk':19,'trialMinutes':8,
        'commitRequires':'Explicit user confirmation of real wireless AA, with AP in mode 2 and actual SSID, WPA2 and credentials verified against AA fields',
        'rollbackOn':['AP startup or bridge install failure','AA service restart failure','User reports failure','No confirmation within 8 minutes','Interrupted service or changed boot','Post-export AA recovery failure'],
        'revision':'0.4.4',
        'gpmPreflight':'Sample getGPMConnectedMode and getGpmStatus twice; only status zero is eligible for idle reset; protect all nonzero and unknown states',
        'diagnostics':{'oemDhcpLeasePath':'/data/misc/dnsmasq/dnsmasq.leases','snapshotSleepSeconds':2,
            'authentication':'Read-only hostapd STA queries and separate ATTACH monitor; errors recorded if unavailable',
            'network':['OEM clients','ARP','interface addresses','IPv4 and IPv6 TCP/UDP','AA port 40515','socket owners when libcore readlink is available'],
            'credentialsExported':False,'workerIndependentOfRollback':True,
            'idleMonitorRead':'Check InputStream.available before reading; no idle blocking receive',
            'monitorHealth':'Periodic poll status; full socket-error details and independent monitor reconnection'},
        'apComparison':'Pinned GPM ConnectivitySSID/ConnectivityPSK versus hostapd configuration; equality only for credentials; cached and Bluetooth wire values unobserved',
        'previousTrialCleanup':'Read-only verification of 0.4.3 original state before removing its guard; abort on mismatch',
        'apAlignment':'Copy original Parcelable; set only SSID/preSharedKey from ConnectivitySSID/ConnectivityPSK; preserve private original; verify actual hostapd SSID, WPA2/CCMP and credential equality before testing or keeping',
        'credentialVerification':'Plain passphrase or PBKDF2-HMAC-SHA1 derived hostapd PSK comparison in RAM only; no credential or derived-key export',
        'rollbackFinalization':'Independent original-state verification and guard-directory absence check before reporting completion',
        'privateState':'/data/nemesis/k5-aa-apalign','previousTrialBackupPreserved':True,
        'deployment':'PC build only; removable USB is not prepared',
        'physicalVehicleTested':False}
    (OUT/'build-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
    print(json.dumps(receipt,indent=2))
if __name__=='__main__':main()
