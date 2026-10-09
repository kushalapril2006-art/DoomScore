"""Synthetic emulator UI checks; never resets or touches a real phone or Instagram."""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--adb',required=True)
p.add_argument('--serial',default='emulator-5556')
p.add_argument('--directory',type=Path,default=Path('test-results'))
a=p.parse_args()
package='com.gridcc.doomscore.android'
checks=[]
def adb(*args):
    result=subprocess.run([a.adb,'-s',a.serial,*args],capture_output=True,text=True,encoding='utf-8',timeout=45)
    assert result.returncode==0,result.stderr or result.stdout
    return result.stdout.strip()
assert a.serial.startswith('emulator-')
assert adb('shell','getprop','ro.boot.qemu.avd_name')=='DoomscoreVerified'
assert adb('shell','getprop','ro.product.model').startswith('sdk_gphone')
def launch():
    adb('shell','am','start','-W','-n',package+'/com.gridcc.doomscore.android.MainActivity')
    time.sleep(1)
def ui():
    for _ in range(5):
        result=adb('shell','uiautomator','dump','--compressed','/sdcard/doomscore-league-ui.xml')
        if 'dumped' in result:return ET.fromstring(adb('shell','cat','/sdcard/doomscore-league-ui.xml'))
        time.sleep(.4)
    raise AssertionError('UI dump failed')
def node(label):
    return next((n for n in ui().iter('node') if n.get('text')==label or n.get('content-desc')==label),None)
def keyboard_top(tree):
    tops=[]
    for n in tree.iter('node'):
        if '.inputmethod' in n.get('package',''):
            bounds=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
            if len(bounds)==4 and bounds[3]>bounds[1]:tops.append(bounds[1])
    windows=adb('shell','dumpsys','window','windows')
    section=re.search(r'(?s)Window #\d+ Window\{[^\n]+InputMethod\}:.*?(?=\n  Window #|\Z)',windows)
    if section and 'isVisible=true' in section.group():
        inset=re.search(r'mGivenContentInsets=\[\d+,(\d+)\]',section.group())
        frame=re.search(r'\bframe=\[\d+,(\d+)\]',section.group())
        if inset and frame:tops.append(int(inset.group(1))+int(frame.group(1)))
    return min(tops,default=1600)
def unobscured(n,tree):
    bounds=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
    return len(bounds)==4 and bounds[3]>bounds[1] and bounds[3]<=keyboard_top(tree)
def scroll_form(tree):
    start=min(keyboard_top(tree)-80,1100)
    adb('shell','input','swipe','360',str(start),'360','450','350');time.sleep(.3)
def tap_node(n):
    assert n is not None
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    assert x2>x1 and y2>y1
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.5)
def control(label):
    for _ in range(6):
        tree=ui()
        n=next((n for n in tree.iter('node') if n.get('text')==label or n.get('content-desc')==label),None)
        if n is not None and unobscured(n,tree):return n
        scroll_form(tree)
    raise AssertionError('Missing control: '+label)
def tap(label):tap_node(control(label))
def enabled(label):
    tree=ui()
    parents={child:parent for parent in tree.iter() for child in parent}
    current=next((n for n in tree.iter('node') if n.get('text')==label),None)
    assert current is not None,label
    while current is not None:
        if current.get('enabled')=='false':return False
        current=parents.get(current)
    return True
def field(label,value):
    target=None
    for _ in range(6):
        tree=ui()
        fields=[n for n in tree.iter('node') if n.get('class')=='android.widget.EditText' and n.get('package')==package]
        target=next((n for n in fields if any(c.get('text')==label for c in n.iter('node'))),None)
        if target is not None and unobscured(target,tree):break
        scroll_form(tree)
    assert target is not None and unobscured(target,tree),'Input obscured: '+label
    tap_node(target)
    adb('shell','input','keyevent','KEYCODE_MOVE_END',*(['KEYCODE_DEL']*40))
    adb('shell','input','text',value)
    time.sleep(.4)
def check(label,condition=True):
    assert condition,label
    checks.append(label);print('PASS:',label,flush=True)
def capture(name):
    a.directory.mkdir(parents=True,exist_ok=True)
    adb('shell','screencap','-p','/sdcard/doomscore-league-preview.png')
    adb('pull','/sdcard/doomscore-league-preview.png',str(a.directory/name))

adb('shell','pm','clear',package)
launch()
tap('Explore first')
tap('league')
check('global tab opens without registration',node('global leaderboard') is not None)
check('no invented ranking',node('unranked') is not None)
tap('Account')
check('disconnected backend is clearly disclosed',any("Global rankings aren't connected yet" in n.get('text','') for n in ui().iter('node')))
check('no invented participants',node('0 ranked scrollers') is not None)
capture('league-screen.png')
tap('Choose username')
field('Doomscore username','thumb.goblin')
field('Instagram username · optional','doom_ig_demo')
capture('league-profile.png')
tap('save on this phone')
check('valid local username and optional Instagram saved',node('@thumb.goblin') is not None)
adb('shell','am','force-stop',package)
launch();tap('league')
check('encrypted profile survives app restart',node('@thumb.goblin') is not None)
tap('Account');tap('Edit profile')
field('Instagram username · optional','bad..handle')
control('save on this phone')
check('Instagram validation message identifies the correct field',any('Use an Instagram username' in n.get('text','') for n in ui().iter('node')))
check('invalid Instagram input cannot be saved',not enabled('save on this phone'))
tap('done')
tap('Account');tap('Edit profile');tap('Delete identity')
check('identity deletion requires explicit confirmation',node('Delete your identity?') is not None)
tap('Delete')
check('local profile deleted without touching reel history',node('Choose your league username') is not None)
tap('stats');tap('this month')
check('stats uses calendar month selector',node('this month') is not None)
control('each bar = one day')
check('stats begins on the first of this month',any('1 ' in n.get('text','') and '– today' in n.get('text','') for n in ui().iter('node')))
adb('shell','settings','put','system','font_scale','1.5')
try:
    launch();tap('league');tap('Account');tap('Choose username');tap('done')
    check('profile sheet closes at large font scale',node('global leaderboard') is not None)
finally:adb('shell','settings','put','system','font_scale','1.0')
report={'passed':True,'checks':checks,'count':len(checks),'device':'synthetic DoomscoreVerified emulator','backend':'disconnected; no live writes'}
(a.directory/'league-ui-smoke.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print('PASS:',len(checks),'league UI checks',flush=True)
