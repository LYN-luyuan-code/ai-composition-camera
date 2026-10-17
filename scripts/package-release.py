#!/usr/bin/env python3
"""Package a signed release and checksum with the name used by the in-app updater."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parent.parent
folder = root / 'app/build/outputs/apk/release'
metadata = json.loads((folder / 'output-metadata.json').read_text())
assert metadata['applicationId'] == 'com.luyuan.silkzoom'
assert len(metadata['elements']) == 1
item = metadata['elements'][0]
version, code = item['versionName'], item['versionCode']
tag = os.environ.get('RELEASE_TAG')
if tag and tag != f'v{version}':
    raise SystemExit(f'Tag {tag} does not match Android version {version}')
apk = folder / item['outputFile']
if 'unsigned' in apk.name:
    raise SystemExit('Release APK is unsigned. Configure signing.properties first.')
sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:
    properties = root / 'local.properties'
    sdk = next((line.removeprefix('sdk.dir=') for line in properties.read_text().splitlines() if line.startswith('sdk.dir=')), '')
verifiers = sorted((Path(sdk) / 'build-tools').glob('*/apksigner'))
if not verifiers:
    raise SystemExit('Android apksigner is required to verify the distribution APK.')
subprocess.run([str(verifiers[-1]), 'verify', '--verbose', str(apk)], check=True)
destination = root / 'output/releases' / f'v{version}'
destination.mkdir(parents=True, exist_ok=True)
output = destination / f'ai-composition-camera-{version}-{code}.apk'
shutil.copy2(apk, output)
digest = hashlib.sha256(output.read_bytes()).hexdigest()
(destination / 'SHA256SUMS.txt').write_text(f'{digest}  {output.name}\n')
print(output)
