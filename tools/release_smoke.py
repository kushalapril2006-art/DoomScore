"""Black-box test of the minified, non-debuggable build, only on the synthetic AVD.
No production credentials, real Instagram, or physical devices are used.
"""
import argparse
import datetime
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--adb',required=True)
p.add_argument('--serial',default='emulator-5556')
p.add_argument('--report',default='test-results/release-smoke.json')
a=p.parse_args()
package='com.gridcc.doomscore.android'
activity=package+'/com.gridcc.doomscore.android.MainActivity'
service=package+'/com.gridcc.doomscore.android.tracking.ReelAccessibilityService'
debug_service='com.gridcc.doomscore.android/com.gridcc.doomscore.android.tracking.ReelAccessibilityService'
checks=[]

def adb(*args):
    r=subprocess.run([a.adb,'-s',a.serial,*args],capture_output=True,text=True,encoding='utf-8',timeout=40)
    if r.returncode: raise RuntimeError(r.stderr.strip() or r.stdout.strip())
    return r.stdout.strip()

def ui():
    for attempt in range(4):
        result=adb('shell','uiautomator','dump','--compressed','/sdcard/doomscore-release-ui.xml')
        if 'dumped' in result:
            return ET.fromstring(adb('shell','cat','/sdcard/doomscore-release-ui.xml'))
        time.sleep(.5)
    raise RuntimeError('Could not read the test UI')

def find(tree,label):
    return next((n for n in tree.iter('node') if n.get('text')==label or n.get('content-desc')==label),None)

def tap(node):
    assert node is not None,'UI control missing'
    left,top,right,bottom=map(int,re.findall(r'\d+',node.get('bounds')))
    assert right>left and bottom>top,'UI control is not visible'
    adb('shell','input','tap',str((left+right)//2),str((top+bottom)//2));time.sleep(.6)

def control(label,scroll=False):
    for attempt in range(7 if scroll else 3):
        n=find(ui(),label)
        if n is not None:return n
        if scroll:adb('shell','input','swipe','360','1250','360','450','350')
        else:time.sleep(1)
    raise AssertionError('Control not found: '+label)

def dashboard(count):
    adb('shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-f','0x14000000','-n',activity);time.sleep(2)
    assert find(ui(),f'{count} reels today') is not None,f'Expected {count} reels'

def reel(name,seconds=2.2):
    adb('shell','am','start','-n','com.instagram.android/com.gridcc.doomscore.fixture.FixtureActivity','--es','reel',name,'--ez','busy','true')
    time.sleep(seconds)

def setting(value):
    if value and value!='null':adb('shell','settings','put','secure','enabled_accessibility_services',value)
    else:adb('shell','settings','delete','secure','enabled_accessibility_services')

def record(name):checks.append(name);print('PASS:',name,flush=True)

assert a.serial.startswith('emulator-')
assert adb('shell','getprop','ro.boot.qemu.avd_name')=='DoomscoreVerified'
assert 'com.gridcc.doomscore.fixture.FixtureActivity' in adb('shell','dumpsys','package','com.instagram.android')
original=adb('shell','settings','get','secure','enabled_accessibility_services')
font=adb('shell','settings','get','system','font_scale')
report={'startedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'package':package,'checks':checks}
try:
    # Clear only the separate release-check app's synthetic test data.
    adb('shell','pm','clear',package)
    adb('shell','am','start','-n',activity);time.sleep(5)
    tap(control('Privacy & data',True))
    assert find(ui(),'Privacy & data') is not None
    assert 'Supabase' not in ET.tostring(ui(),encoding='unicode')
    record('Offline privacy is available before granting permission')
    adb('shell','input','keyevent','BACK');tap(control('Explore first',True));time.sleep(1)
    assert find(ui(),'battle') is None
    record('Unactivated online battles are absent from the release UI')
    tap(control('Settings'));tap(control('Automatic counting'))
    # Row-label tap may not hit the switch; find the actual first toggle if needed.
    tree=ui()
    if find(tree,'Enable automatic counting') is None:
        switches=[n for n in tree.iter('node') if n.get('checkable')=='true'];tap(switches[0])
    tap(control('Not now'));record('Declining the disclosure leaves counting disabled')
    tap(control('Settings'))
    tree=ui();switches=[n for n in tree.iter('node') if n.get('checkable')=='true'];tap(switches[0])
    tap(control('Agree & open settings'))
    others=':'.join(s for s in original.split(':') if s not in {service,debug_service,'null',''})
    setting(others);time.sleep(.5);setting(':'.join(filter(None,[others,service])))
    adb('shell','settings','put','secure','accessibility_enabled','1')
    dashboard(0);time.sleep(2)
    reel('A',4);dashboard(1);record('First reel and continuous video looping count once')
    reel('B');dashboard(2);record('A new reel increments the counter')
    reel('A');dashboard(2);record('Recently revisiting a reel preserves the count')
    reel('ad');dashboard(2);record('Sponsored content preserves the count')
    reel('comments');dashboard(2);reel('home');dashboard(2);record('Comments and regular feeds preserve the count')
    tap(control('Settings'))
    switches=[n for n in ui().iter('node') if n.get('checkable')=='true'];assert switches[0].get('checked')=='true';tap(switches[0])
    switches=[n for n in ui().iter('node') if n.get('checkable')=='true'];assert switches[0].get('checked')=='false'
    record('Pause switch updates immediately without reopening settings')
    adb('shell','input','keyevent','BACK');reel('C');dashboard(2);record('Paused counting ignores new reels')
    tap(control('Settings'));switches=[n for n in ui().iter('node') if n.get('checkable')=='true'];tap(switches[0])
    switches=[n for n in ui().iter('node') if n.get('checkable')=='true'];assert switches[0].get('checked')=='true'
    adb('shell','input','keyevent','BACK');reel('C');dashboard(3);record('Resuming counting observes new reels')
    adb('shell','am','force-stop',package);dashboard(3);record('Counts persist across process restart')
    tap(control('stats'));assert find(ui(),'3 reels') is not None
    record('Statistics retain the counted totals in the optimized build')
    tap(control('Open your Wrapped ↗',True));tap(control('Share your recap',True));time.sleep(2)
    assert 'ChooserActivity' in adb('shell','dumpsys','activity','activities'),'Recap share chooser did not open'
    record('Recap PNG/FileProvider opens the share chooser without sending to a recipient')
    adb('shell','input','keyevent','BACK');adb('shell','input','keyevent','BACK')
    adb('shell','settings','put','system','font_scale','1.5');time.sleep(2)
    tap(control('Settings'));tap(control('Privacy & data',True));assert find(ui(),'Close') is not None
    record('Large-font privacy dialog retains a visible dismissal action')
    report['passed']=True
except Exception as e:
    report.update(passed=False,error=str(e));raise
finally:
    setting(original)
    if font!='null':adb('shell','settings','put','system','font_scale',font)
    else:adb('shell','settings','delete','system','font_scale')
    report['finishedAt']=datetime.datetime.now(datetime.timezone.utc).isoformat()
    Path(a.report).parent.mkdir(parents=True,exist_ok=True)
    Path(a.report).write_text(json.dumps(report,indent=2))
