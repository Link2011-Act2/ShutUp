"""Create a same-package, foreign-signer negative fixture using a disposable key only."""
import argparse, subprocess
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--java-home', type=Path, required=True)
p.add_argument('--android-home', type=Path, required=True)
p.add_argument('--version', default='1.1.1-IntDev')
args = p.parse_args()
root = Path(__file__).resolve().parents[2]
work = root / 'build/full-goldy-20261107/fixture'
work.mkdir(parents=True, exist_ok=True)
tools = args.android_home / 'build-tools/36.0.0'
def run(*cmd):
    subprocess.run([str(x) for x in cmd], check=True, capture_output=True, timeout=90)
key = work / 'disposable.p12'
password = 'shutup-disposable-negative-fixture'
if not key.exists():
    run(args.java_home/'bin/keytool.exe', '-genkeypair', '-keystore', key, '-storetype', 'PKCS12',
        '-storepass', password, '-keypass', password, '-alias', 'test', '-keyalg', 'RSA', '-keysize', '2048',
        '-validity', '3650', '-dname', 'CN=Disposable foreign signer negative test')
from xml.sax.saxutils import quoteattr
manifest = work / 'AndroidManifest.xml'
manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="jp.linkserver.shutter" '
    'android:versionCode="10" android:versionName='+quoteattr(args.version)+'><uses-sdk android:minSdkVersion="28" '
    'android:targetSdkVersion="28"/><application android:hasCode="false"/></manifest>', encoding='utf-8')
unsigned = work/'unsigned.apk'
run(tools/'aapt2.exe', 'link', '-o', unsigned, '--manifest', manifest, '-I', args.android_home/'platforms/android-37.0/android.jar')
output = root/'app/src/androidTest/assets/goldy/foreign-signer.apk'
run(args.java_home/'bin/java.exe', '-jar', tools/'lib/apksigner.jar', 'sign', '--in', unsigned, '--out', output,
    '--ks', key, '--ks-key-alias', 'test', '--ks-pass', 'pass:'+password, '--v4-signing-enabled', 'false')
print(output)
