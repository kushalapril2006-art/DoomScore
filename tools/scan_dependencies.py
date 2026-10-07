"""Scan resolved Maven modules and locked npm packages against the public OSV API.
Only package names and versions leave the machine; no source, keys or user data.
"""
import argparse
import datetime
import json
import urllib.request
import time
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--project', type=Path, default=Path('.'))
args = parser.parse_args()
queries = []
for coordinate in json.loads((args.project / 'test-results/dependency-inventory.json').read_text())['maven']:
    group, artifact, version = coordinate.split(':')
    queries.append({'package': {'ecosystem': 'Maven', 'name': group + ':' + artifact}, 'version': version})
lock = args.project / 'backend/tests/package-lock.json'
if lock.exists():
    for path, record in json.loads(lock.read_text())['packages'].items():
        if path.startswith('node_modules/') and 'version' in record:
            name = path.rsplit('node_modules/', 1)[1]
            queries.append({'package': {'ecosystem': 'npm', 'name': name}, 'version': record['version']})
findings = []
def fetch_json(request):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                return json.load(response)
        except (OSError, ValueError):
            if attempt == 3:
                raise
            time.sleep(2 ** attempt)

for offset in range(0, len(queries), 50):
    batch = queries[offset:offset + 50]
    request = urllib.request.Request('https://api.osv.dev/v1/querybatch', data=json.dumps({'queries': batch}).encode(), headers={'Content-Type': 'application/json'}, method='POST')
    results = fetch_json(request)['results']
    assert len(results) == len(batch), 'Incomplete vulnerability response'
    for query, result in zip(batch, results):
        vulnerabilities = result.get('vulns', [])
        if vulnerabilities:
            entry = {**query, 'vulnerabilities': []}
            for vulnerability in vulnerabilities:
                entry['vulnerabilities'].append(fetch_json('https://api.osv.dev/v1/vulns/' + vulnerability['id']))
            findings.append(entry)
report = {'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'packagesChecked': len(queries), 'findings': findings,
          'limitations': 'Known OSV advisories only; absence of findings is not proof that software has no vulnerabilities.'}
destination = args.project / 'test-results/dependency-scan.json'
destination.write_text(json.dumps(report, indent=2))
print(f'Checked {len(queries)} exact Maven/npm versions; {len(findings)} packages with advisories')
for entry in findings:
    print(entry['package']['name'], entry['version'], [(v['id'], v.get('summary', '')) for v in entry['vulnerabilities']])
