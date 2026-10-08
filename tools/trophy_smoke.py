"""Read-only cabinet checks after resetting only the synthetic emulator's release-check app."""
import argparse
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

sys.stdout.reconfigure(encoding='utf-8')

p=argparse.ArgumentParser()
p.add_argument('--adb',required=True)
p.add_argument('--serial',default='emulator-5556')
p.add_argument('--directory',type=Path,default=Path('test-results'))
a=p.parse_args()
package='com.gridcc.doomscore.android'
checks=[]
def adb(*args):
    result=subprocess.run([a.adb,'-s',a.serial,*args],capture_output=True,text=True,encoding='utf-8',timeout=50)
    assert result.returncode==0,result.stderr or result.stdout
    return result.stdout.strip()
assert a.serial.startswith('emulator-')
assert adb('shell','getprop','ro.boot.qemu.avd_name')=='DoomscoreVerified'
assert adb('shell','getprop','ro.product.model').startswith('sdk_gphone')
def ui():
    for _ in range(5):
        result=adb('shell','uiautomator','dump','--compressed','/sdcard/doomscore-trophy-ui.xml')
        if 'dumped' in result:return ET.fromstring(adb('shell','cat','/sdcard/doomscore-trophy-ui.xml'))
        time.sleep(.4)
    raise AssertionError('UI snapshot unavailable')
def find(label):return next((n for n in ui().iter('node') if n.get('text')==label or n.get('content-desc')==label),None)
def control(label):
    for _ in range(12):
        node=find(label)
        if node is not None:return node
        adb('shell','input','swipe','360','1250','360','450','350');time.sleep(.4)
    raise AssertionError('Missing control: '+label)
def tap(label):
    node=control(label)
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    assert x2>x1 and y2>y1
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.5)
def check(label,condition=True):
    assert condition,label
    checks.append(label);print('PASS:',label,flush=True)
def launch():
    adb('shell','am','start','-W','-n',package+'/com.gridcc.doomscore.android.MainActivity');time.sleep(1)
font=adb('shell','settings','get','system','font_scale')
try:
    adb('shell','pm','clear',package);launch();tap('Explore first');tap('stats');tap('Brainrot Trophy Cabinet')
    check('cabinet opens without a profile or permission',find('0 / 8 UNLOCKED') is not None)
    a.directory.mkdir(parents=True,exist_ok=True)
    adb('shell','screencap','-p','/sdcard/doomscore-trophy-preview.png')
    adb('pull','/sdcard/doomscore-trophy-preview.png',str(a.directory/'trophy-cabinet.png'))
    badges=[('One More Then I Sleep','100 unique reels'),('For You? For Me.','1,000 unique reels'),
        ('Final Boss of Brainrot','10,000 unique reels'),('Bed Rot Any%','500 unique reels in one day'),
        ('Chronically Online','Scroll 7 days in a row'),('Bro Got Outscrolled','Win your first Battle'),
        ('Unemployed Behaviour','Win 5 Battles in a row'),('Touch Grass Is a Threat','Reach the global top 10')]
    for title,rule in badges:
        control(title);control(rule);check(title+' has its requested unlock rule')
    check('unconnected online awards stay locked',find('Join the global league to compete') is not None)
    tap('Close');check('cabinet dismisses to Stats',find('the receipts') is not None)
    tap('today');tap('Brainrot Trophy Cabinet');check('cabinet also opens from Today',find('0 / 8 UNLOCKED') is not None);tap('Close')
    adb('shell','am','force-stop',package);launch();tap('stats');tap('Brainrot Trophy Cabinet')
    check('restart does not invent unlocked badges',find('0 / 8 UNLOCKED') is not None);tap('Close')
    adb('shell','settings','put','system','font_scale','1.5');time.sleep(2);launch();tap('stats');tap('Brainrot Trophy Cabinet')
    control('Close');tap('Close');check('large-font cabinet keeps dismissal accessible',find('the receipts') is not None)
finally:
    if font=='null':adb('shell','settings','delete','system','font_scale')
    else:adb('shell','settings','put','system','font_scale',font)
(a.directory/'trophy-ui-smoke.json').write_text(json.dumps({'passed':True,'checks':checks,'count':len(checks),'device':'synthetic DoomscoreVerified emulator','liveBackendWrites':False},indent=2),encoding='utf-8')
print('PASS:',len(checks),'trophy UI checks',flush=True)
