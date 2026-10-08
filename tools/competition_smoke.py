"""Competitive-theme UI acceptance, only on the synthetic DoomscoreVerified AVD."""
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
p.add_argument('--directory',type=Path,required=True)
p.add_argument('--serial',default='emulator-5556')
p.add_argument('--expect-upgrade-count',type=int)
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
def ui():
    for _ in range(5):
        if 'dumped' in adb('shell','uiautomator','dump','--compressed','/sdcard/doomscore-competition-ui.xml'):
            return ET.fromstring(adb('shell','cat','/sdcard/doomscore-competition-ui.xml'))
        time.sleep(.5)
    raise AssertionError('UI unavailable')
def find(tree,label):
    return next((n for n in tree.iter('node') if label in (n.get('text'),n.get('content-desc'))),None)
def active(tree,label):
    parents={child:parent for parent in tree.iter() for child in parent}
    node=find(tree,label)
    while node is not None:
        if node.get('checked')=='true' or node.get('selected')=='true':return True
        if node.get('checkable')=='true':return False
        node=parents.get(node)
    return False

def control(label,scroll=True):
    direction='up';previous=None
    for _ in range(14 if scroll else 4):
        tree=ui();n=find(tree,label)
        if n is not None:return n
        if scroll:
            visible='|'.join(node.get('text','') for node in tree.iter('node'))
            if visible==previous:direction='down' if direction=='up' else 'up'
            previous=visible
            points=('360','1250','360','480') if direction=='up' else ('360','480','360','1250')
            adb('shell','input','swipe',*points,'350')
        time.sleep(.4)
    raise AssertionError('Missing control: '+label)
def tap(label,scroll=True):
    n=control(label,scroll)
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.5)
def nav(label):
    nodes=[n for n in ui().iter('node') if label in (n.get('text'),n.get('content-desc'))]
    assert nodes,'Missing navigation: '+label
    node=max(nodes,key=lambda n:int(re.findall(r'\d+',n.get('bounds'))[1]))
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.5)

def check(label,condition=True):
    assert condition,label
    checks.append(label);print('PASS:',label,flush=True)
def no_coaching(tree):
    content=ET.tostring(tree,encoding='unicode').lower()
    forbidden=['daily cap','cap reminder','chill streak','self-control','take a break','touch grass legend','gentle nudge','accountable','remaining allowance']
    assert not any(text in content for text in forbidden),'Retired coaching visible'
def capture(name):
    a.directory.mkdir(parents=True,exist_ok=True)
    adb('shell','screencap','-p','/sdcard/doomscore-competition-preview.png')
    adb('pull','/sdcard/doomscore-competition-preview.png',str(a.directory/name))
def launch():
    adb('shell','am','start','-W','-n',package+'/com.gridcc.doomscore.android.MainActivity');time.sleep(1)
font=adb('shell','settings','get','system','font_scale')
scale=adb('shell','settings','get','global','animator_duration_scale')
try:
    if a.expect_upgrade_count is not None:
        launch();nav('today');control(f'{a.expect_upgrade_count} reels today')
        check('in-place upgrade preserves the previous reel total')
    adb('shell','pm','clear',package);launch()
    tree=ui();check('onboarding introduces competitive doomscrolling',find(tree,'COMPETITIVE DOOMSCROLLING') is not None);no_coaching(tree)
    capture('competition-onboarding.png')
    control('🏆   Scroll ranks, badges and personal bests');check('onboarding highlights scroll ranks and badges')
    tap('Explore first');tree=ui();no_coaching(tree)
    check('Today opens with unranked score and next-rank progress',find(tree,'reels today · unranked') is not None and find(tree,'next rank at 1') is not None)
    capture('competition-today.png')
    control('scroll streak · best 0d');check('zero-count days do not earn scrolling streaks')
    control('personal best · reels in a day');check('personal best replaces daily-limit tracking')
    control("this month's score");check('Today presents the calendar-month competition score')
    tap('Settings',False);tree=ui();no_coaching(tree)
    check('settings keep counting controls without caps or reminders',find(tree,'counter setup') is not None and find(tree,'Automatic counting') is not None)
    tap('Done');nav('stats');tap('Open your Wrapped ↗')
    tree=ui();no_coaching(tree)
    check('empty recap celebrates a pending debut',find(tree,'your debut is loading') is not None)
    check('recap Close and Share remain visible',find(tree,'Close') is not None and find(tree,'Share your recap') is not None)
    adb('shell','input','keyevent','BACK');nav('league')
    control('More reels, higher rank. Ties use a fixed order. Exact ranks through #200; everyone below gets a top-percentage badge. New month, new leaderboard.')
    check('League explicitly rewards higher scores')
    nav('stats');tap('Brainrot Trophy Cabinet 🏆');control('Touch Grass Is a Threat')
    check('ironic top-10 trophy retains its requested name');tap('Close',False)
    tap('year');control('each bar = one week');check('year view loads the batched history')
    nav('league');nav('stats')
    check('Stats period survives tab navigation',active(ui(),'year'))
    control('your doom hour');nav('league');nav('stats')
    check('Stats scroll position survives tab navigation',find(ui(),'your doom hour') is not None)
    adb('shell','input','keyevent','BACK');time.sleep(.5)
    check('Back from a primary tab returns to Today',active(ui(),'today'))

    adb('shell','settings','put','system','font_scale','1.5');time.sleep(2);launch();tap('Settings',False);tap('Done')
    check('large-font counter setup stays dismissible',find(ui(),'Settings') is not None)
    adb('shell','settings','put','global','animator_duration_scale','0');nav('stats');control('each bar = one week')
    check('navigation works with system animations disabled',active(ui(),'stats'))
finally:
    if scale=='null':adb('shell','settings','delete','global','animator_duration_scale')
    else:adb('shell','settings','put','global','animator_duration_scale',scale)
    if font=='null':adb('shell','settings','delete','system','font_scale')
    else:adb('shell','settings','put','system','font_scale',font)
(a.directory/'competition-ui-smoke.json').write_text(json.dumps({'passed':True,'count':len(checks),'checks':checks,'syntheticEmulator':True,'liveBackendWrites':False},indent=2),encoding='utf-8')
print('PASS:',len(checks),'competition UI checks')
