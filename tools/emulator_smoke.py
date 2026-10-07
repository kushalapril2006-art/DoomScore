"""End-to-end check of Android Accessibility, with no UiAutomation service.

Requires debug Doomscore and the test-only feed fixture on the isolated Doomscore AVD.
Reads only the debug app's synthetic test database; never operates on a physical phone.
"""
import argparse
import json
import sqlite3
import subprocess
import tempfile
import time
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--adb', default='adb')
parser.add_argument('--serial', required=True)
parser.add_argument('--report', default='test-results/emulator-smoke.json')
args = parser.parse_args()
app = 'com.gridcc.doomscore.android'
service = app + '/' + app + '.tracking.ReelAccessibilityService'

def adb(*parts, binary=False):
    result = subprocess.run([args.adb, '-s', args.serial, *parts], capture_output=True, timeout=30)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors='replace'))
    return result.stdout if binary else result.stdout.decode(errors='replace').strip()

assert args.serial.startswith('emulator-'), 'Physical devices are not permitted'
assert adb('shell', 'getprop', 'ro.boot.qemu') == '1'
assert adb('shell', 'getprop', 'ro.boot.qemu.avd_name') in {'DoomscoreTest', 'DoomscoreVerified'}
assert 'com.gridcc.doomscore.fixture.FixtureActivity' in adb('shell', 'dumpsys', 'package', 'com.instagram.android'), 'Synthetic fixture required'

with tempfile.TemporaryDirectory(prefix='doomscore-smoke-') as scratch:
    database = Path(scratch) / 'snapshot.db'
    def stats():
        for attempt in range(8):
            try:
                database.write_bytes(adb('exec-out', 'run-as', app, 'cat', 'databases/doomscore.db', binary=True))
                with sqlite3.connect(f'file:{database.as_posix()}?mode=ro', uri=True) as connection:
                    row = connection.execute('SELECT COALESCE(SUM(reels),0),COALESCE(SUM(ads),0),COALESCE(SUM(repeats),0),COALESCE(SUM(watch_ms),0) FROM stats').fetchone()
                return dict(zip(['reels', 'ads', 'rewatches', 'watch_ms'], row))
            except (sqlite3.Error, RuntimeError):
                if attempt == 7:
                    raise
                time.sleep(.25)

    # Reconnect after instrumentation force-stops its target. Preserve other enabled services.
    original = adb('shell', 'settings', 'get', 'secure', 'enabled_accessibility_services')
    assert service in original, 'Enable Doomscore Accessibility on the test emulator first'
    others = ':'.join(item for item in original.split(':') if item != service)
    if others:
        adb('shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', others)
    else:
        adb('shell', 'settings', 'delete', 'secure', 'enabled_accessibility_services')
    time.sleep(.5)
    adb('shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', original)
    time.sleep(2)

    def open_reel(reel):
        adb('shell', 'am', 'start', '-n', 'com.instagram.android/com.gridcc.doomscore.fixture.FixtureActivity', '--es', 'reel', reel, '--ez', 'busy', 'true')

    checks = []
    def expect(label, key, expected):
        deadline = time.monotonic() + 12
        value = stats()
        while value[key] < expected and time.monotonic() < deadline:
            time.sleep(.25)
            value = stats()
        assert value[key] == expected, f'{label}: expected {key}={expected}, got {value}'
        checks.append({'check': label, 'stats': value})
        print(f'PASS {label}: {value}', flush=True)
        return value

    # The app's disclosure must already be accepted on this test device.
    initial = stats()
    first, second = 'first_' + str(time.time_ns()), 'second_' + str(time.time_ns())
    open_reel(first)
    expect('First organic view', 'reels', initial['reels'] + 1)
    time.sleep(2)
    expect('Loop and frequent playback UI updates', 'reels', initial['reels'] + 1)
    open_reel(second)
    expect('Next organic reel', 'reels', initial['reels'] + 2)
    open_reel(first)
    expect('Recent rewatch excluded', 'rewatches', initial['rewatches'] + 1)
    expect('Rewatch leaves reel total unchanged', 'reels', initial['reels'] + 2)
    open_reel('ad')
    expect('Sponsored content excluded', 'ads', initial['ads'] + 1)
    expect('Ad leaves reel total unchanged', 'reels', initial['reels'] + 2)
    for panel in ['comments', 'home']:
        open_reel(panel)
        time.sleep(2)
        expect(f'{panel} does not count', 'reels', initial['reels'] + 2)
    final = stats()
    assert final['watch_ms'] > initial['watch_ms'] + 1000, 'Organic watch time must accumulate'
    report = {'passed': True, 'initial': initial, 'final': final, 'checks': checks}
    destination = Path(args.report)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2))
    print('PASS real Accessibility integration', flush=True)
