"""Goob island acceptance on the isolated synthetic AVD, never a physical phone."""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--adb',required=True)
p.add_argument('--directory',required=True,type=Path)
a=p.parse_args()
serial='emulator-5556'
package='com.gridcc.doomscore.android'
service=package+'/com.gridcc.doomscore.android.tracking.ReelAccessibilityService'
checks=[]
def adb(*args):
    result=subprocess.run([a.adb,'-s',serial,*args],capture_output=True,text=True,encoding='utf-8',timeout=45)
    assert result.returncode==0,result.stderr or result.stdout
    return result.stdout.strip()
assert adb('shell','getprop','ro.boot.qemu.avd_name')=='DoomscoreVerified'
assert adb('shell','getprop','ro.product.model').startswith('sdk_gphone')
last_snapshot=None
def ui():
    global last_snapshot
    for _ in range(40):
        value=adb('shell','run-as','com.gridcc.doomscore.android','cat','cache/island-ui.xml')
        tree=ET.fromstring(value)
        stamp=tree.get('capture-ms')
        if tree.get('watch')=='true' and stamp!=last_snapshot:
            last_snapshot=stamp
            return tree
        time.sleep(.15)
    raise AssertionError('Fresh test UI unavailable')
def find(tree,label):
    return next((n for n in tree.iter('node') if label in (n.get('text'),n.get('content-desc'))),None)
def bounds(node):return list(map(int,re.findall(r'\d+',node.get('bounds'))))
def tap(node):
    assert node is not None,'Missing test control'
    l,t,r,b=bounds(node);assert r>l and b>t
    adb('shell','input','tap',str((l+r)//2),str((t+b)//2));time.sleep(.5)
def control(label):
    direction='up';previous=None
    for _ in range(18):
        tree=ui();n=find(tree,label)
        if n is not None:return tree,n
        visible='|'.join(n.get('text','') for n in tree.iter('node'))
        if previous==visible:direction='down' if direction=='up' else 'up'
        previous=visible
        coords=('360','1250','360','480') if direction=='up' else ('360','480','360','1250')
        adb('shell','input','swipe',*coords,'350')
    raise AssertionError('Missing '+label)
def switch(label,value):
    def target(tree,node):
        parents={child:parent for parent in tree.iter() for child in parent}
        scope=node
        candidates=[]
        while scope is not None:
            candidates=[n for n in scope.iter('node') if n.get('checkable')=='true']
            if candidates:break
            scope=parents.get(scope)
        y=sum(bounds(node)[1::2])/2
        return min(candidates,key=lambda n:abs(sum(bounds(n)[1::2])/2-y)),y
    tree,node=control(label)
    n,y=target(tree,node)
    assert abs(sum(bounds(n)[1::2])/2-y)<100,'Switch row not located'
    if (n.get('checked')=='true')!=value:tap(n)
    tree,node=control(label)
    n,_=target(tree,node)
    assert (n.get('checked')=='true')==value,'Switch did not update: '+label
def launch():
    adb('shell','am','start','-W','-n',package+'/com.gridcc.doomscore.android.MainActivity');time.sleep(1)
def setup():
    launch()
    if find(ui(),'counter setup') is None:tap(control('Settings')[1])
def reel(name):
    adb('shell','am','start','-W','-n','com.instagram.android/com.gridcc.doomscore.fixture.FixtureActivity','--es','reel',name,'--ez','busy','true');time.sleep(2)
def pill():
    return next((n for n in ui().iter('node') if n.get('content-desc','').startswith('Goob: ')),None)
def count(node):return int(re.search(r'Goob: (\d+) reels',node.get('content-desc')).group(1))
def check(label,condition=True):
    assert condition,label
    checks.append(label);print('PASS:',label,flush=True)
def notification():
    value=adb('shell','dumpsys','notification','--noredact')
    return next((s for s in value.split('NotificationRecord(') if f'pkg={package}' in s and 'id=104 ' in s),None)
def capture(name):
    adb('shell','screencap','-p','/sdcard/doomscore-island-preview.png')
    adb('pull','/sdcard/doomscore-island-preview.png',str(a.directory/name))
original=adb('shell','settings','get','secure','enabled_accessibility_services')
font=adb('shell','settings','get','system','font_scale')
scale=adb('shell','settings','get','global','animator_duration_scale')
a.directory.mkdir(parents=True,exist_ok=True)
observer=None
report={'package':package,'device':'DoomscoreVerified synthetic API 36.1','checks':checks,'nativeOemOrThirdPartyHostTested':False}
try:
    # Existing counter smoke has already completed disclosure and left three counted reels.
    adb('shell','pm','revoke',package,'android.permission.POST_NOTIFICATIONS')
    adb('shell','pm','clear-permission-flags',package,'android.permission.POST_NOTIFICATIONS','user-set','user-fixed')
    adb('shell','settings','put','secure','enabled_accessibility_services',service)
    adb('shell','settings','put','secure','accessibility_enabled','1')
    adb('shell','run-as','com.gridcc.doomscore.android','rm','-f','cache/island-probe-stop')
    observer=subprocess.Popen([a.adb,'-s',serial,'shell','am','instrument','-w','-e','class','com.gridcc.doomscore.android.UiProbe','-e','watch','true','com.gridcc.doomscore.android.test/androidx.test.runner.AndroidJUnitRunner'],stdout=(a.directory/'island-inspector.log').open('w'),stderr=subprocess.STDOUT)
    time.sleep(2)
    launch()
    for _ in range(3):
        if find(ui(),'Settings') is not None:break
        adb('shell','input','keyevent','BACK');time.sleep(.4)
    setup();switch('Island app notification',False);switch('Goob island',True)
    check('Island can be enabled without notification permission')
    adb('shell','input','keyevent','BACK');reel('island_A')
    node=pill();check('Active feed shows Goob with today score and rank',node is not None)
    n=count(node);l,t,r,b=bounds(node)
    check('Island is centred near the top and leaves the status bar usable',t>=48 and t<220 and abs((l+r)/2-360)<80)
    check('Pill has a bounded touch area',r-l<600 and b-t<180)
    capture('island-preview.png')
    reel('island_B');check('New reel updates the island score',count(pill())==n+1)
    reel('island_A');check('Rewatch does not inflate island score',count(pill())==n+1)
    reel('ad');check('Recognized ad does not inflate island score',count(pill())==n+1)
    node=pill();l,t,r,b=bounds(node)
    adb('shell','input','swipe',str((l+r)//2),str((t+b)//2),str((l+r)//2+75),str((t+b)//2+100),'400');time.sleep(.5)
    check('Dragging moves the pill without opening the dashboard',bounds(pill())[1]>t+40)
    l,t,r,b=bounds(pill())
    adb('shell','input','swipe',str((l+r)//2),str((t+b)//2),str((l+r)//2),'0','400');time.sleep(.5)
    check('Dragging upward keeps the status bar clear',bounds(pill())[1]>=48)
    reel('home');check('Regular feed hides the island',pill() is None)
    reel('island_B');tap(pill());check('Tapping opens Doomscore and hides the feed island',find(ui(),'Settings') is not None and pill() is None)
    setup();switch('Automatic counting',False);adb('shell','input','keyevent','BACK');reel('island_C')
    check('Paused counter does not display the island',pill() is None)
    setup();switch('Automatic counting',True);switch('Island app notification',True)
    tree=ui()
    deny=next((n for n in tree.iter('node') if n.get('resource-id','').endswith('permission_deny_button')),None)
    assert deny is not None,'Expected notification opt-in prompt';tap(deny)
    check('Notification refusal leaves the counter available',find(ui(),"Notifications weren't enabled. Counting and the Goob island still work. You can enable notifications in Android settings.") is not None)
    adb('shell','input','keyevent','BACK');reel('island_C');check('Goob island works after notification refusal',pill() is not None)
    adb('shell','pm','grant',package,'android.permission.POST_NOTIFICATIONS')
    setup();switch('Island app notification',True);switch('On-screen Goob counter',False)
    adb('shell','input','keyevent','BACK');reel('island_D')
    check('Notification-only mode avoids duplicate pills',pill() is None)
    record=notification();check('Active feed publishes a silent score notification',record is not None and 'live_counter' in record and 'reels today' in record)
    setup();switch('Automatic counting',False);adb('shell','input','keyevent','BACK');reel('island_paused')
    check('Pausing removes the ongoing notification',notification() is None)
    setup();switch('Automatic counting',True);adb('shell','input','keyevent','BACK');reel('island_D')
    check('Resuming restores the active score notification',notification() is not None)
    reel('home');check('Leaving the feed cancels the ongoing notification',notification() is None)
    reel('island_D');adb('shell','pm','revoke',package,'android.permission.POST_NOTIFICATIONS');time.sleep(1)
    setup();switch('On-screen Goob counter',True);adb('shell','input','keyevent','BACK');reel('island_E')
    check('Revoked notification permission does not break counting or overlay',pill() is not None and notification() is None)
    adb('shell','input','keyevent','26');time.sleep(1);check('Locking the device removes the live display',pill() is None and notification() is None)
    adb('shell','input','keyevent','224');adb('shell','wm','dismiss-keyguard')
    setup();switch('Island app notification',False)
    adb('shell','settings','put','system','font_scale','1.5');adb('shell','settings','put','global','animator_duration_scale','0');time.sleep(1)
    adb('shell','input','keyevent','BACK');reel('island_F')
    check('Large text and disabled animations retain a usable island',pill() is not None)
    capture('island-large-font.png')
    report.update(passed=True,count=len(checks))
except Exception as e:
    report.update(passed=False,error=str(e));raise
finally:
    if observer is not None:
        adb('shell','run-as','com.gridcc.doomscore.android','touch','cache/island-probe-stop')
        try:observer.wait(timeout=30)
        except subprocess.TimeoutExpired:observer.terminate()
    for table,key,value in [('secure','enabled_accessibility_services',original),('system','font_scale',font),('global','animator_duration_scale',scale)]:
        adb('shell','settings','put',table,key,value) if value!='null' else adb('shell','settings','delete',table,key)
    (a.directory/'island-ui-smoke.json').write_text(json.dumps(report,indent=2))
