"""Verify release manifest, signing, ZIP/native alignment and absence of capture permissions."""
import argparse
import datetime
import hashlib
import json
import os
import re
import struct
import subprocess
from pathlib import Path
from zipfile import ZipFile

p=argparse.ArgumentParser()
p.add_argument('--apk',required=True,type=Path)
p.add_argument('--build-tools',required=True,type=Path)
p.add_argument('--package',default='com.gridcc.doomscore.android')
p.add_argument('--report',default='test-results/release-apk-verification.json',type=Path)
a=p.parse_args()

def command(name,*args):
    result=subprocess.run([str(a.build_tools/name),*map(str,args)],capture_output=True,text=True,timeout=90)
    assert result.returncode==0,result.stderr or result.stdout
    return result.stdout

badging=command('aapt2.exe','dump','badging',a.apk)
manifest=command('aapt2.exe','dump','xmltree','--file','AndroidManifest.xml',a.apk)
permissions=command('aapt2.exe','dump','permissions',a.apk)
signature=command('apksigner.bat','verify','--verbose',a.apk)
alignment=command('zipalign.exe','-c','-P','16','4',a.apk)
assert f"package: name='{a.package}'" in badging
assert re.search(r"(?:minSdkVersion|sdkVersion):'26'",badging) and "targetSdkVersion:'36'" in badging
assert 'application-debuggable' not in badging
assert not re.search(r':testOnly\([^)]*\)=(?:true|\(type 0x12\)0xffffffff\b)',manifest), 'APK must allow normal file installation'
for name in ['allowBackup','usesCleartextTraffic']:
    assert re.search(r':'+name+r'\([^)]*\)=(?:false|\(type 0x12\)0x0\b)',manifest),name+' is not explicitly disabled'
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in signature
requested=re.findall(r"uses-permission: name='([^']+)'",permissions)
# Keep an exact allowlist. Unused transitive biometric permissions are removed by our manifest.
allowed={'android.permission.INTERNET','android.permission.POST_NOTIFICATIONS','android.permission.POST_PROMOTED_NOTIFICATIONS',
         a.package+'.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
assert set(requested)<=allowed,'Unexpected permission'
native=[]
with ZipFile(a.apk) as z:
    assert z.testzip() is None
    for entry in z.infolist():
        if not entry.filename.endswith('.so'):continue
        data=z.read(entry)
        assert data[:4]==b'\x7fELF'
        if data[4]!=2:continue
        assert data[5]==1,'Unexpected ELF byte order'
        offset=struct.unpack_from('<Q',data,32)[0]
        size,count=struct.unpack_from('<HH',data,54)
        aligns=[struct.unpack_from('<Q',data,offset+i*size+48)[0] for i in range(count) if struct.unpack_from('<I',data,offset+i*size)[0]==1]
        assert aligns and all(v>=16384 for v in aligns),entry.filename+' has an unaligned LOAD segment'
        with a.apk.open('rb') as f:
            f.seek(entry.header_offset)
            local=f.read(30)
            filename_len,extra_len=struct.unpack_from('<HH',local,26)
        content_offset=entry.header_offset+30+filename_len+extra_len
        assert entry.compress_type!=0 or content_offset%16384==0,'Uncompressed native ZIP entry is not 16 KB aligned'
        native.append({'path':entry.filename,'loadSegmentAlignment':aligns,'uncompressedZipOffset':content_offset})
report={'checkedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'passed':True,'package':a.package,
    'debuggable':False,'minSdk':26,'targetSdk':36,'permissions':requested,'native64BitLibraries':native,
    'apkBytes':a.apk.stat().st_size,'sha256':hashlib.sha256(a.apk.read_bytes()).hexdigest(),
    'limitations':'Checks binary alignment and manifest/signature; real 16 KB device/runtime and Play review remain separate.'}
a.report.parent.mkdir(parents=True,exist_ok=True)
a.report.write_text(json.dumps(report,indent=2))
print('PASS: non-debuggable release APK, restricted permissions, v2 signing, ZIP and 16 KB native alignment')
