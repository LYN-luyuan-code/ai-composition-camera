#!/usr/bin/env python3
"""Create the permanent distribution key locally; never overwrite an existing key."""
import os
from pathlib import Path
import secrets
import subprocess

root = Path(__file__).resolve().parent.parent
config = root / 'signing.properties'
folder = Path.home() / '.local/share/ai-composition-camera/signing'
key = folder / 'release.jks'
if config.exists() or key.exists():
    raise SystemExit('Signing material already exists. Reuse it; do not replace a published app key.')
folder.mkdir(parents=True, exist_ok=True, mode=0o700)
password = secrets.token_urlsafe(36)
env = dict(os.environ, CAMERA_KEY_PASSWORD=password)
subprocess.run(['keytool', '-genkeypair', '-keystore', str(key), '-storetype', 'JKS',
                '-alias', 'camera-release', '-keyalg', 'RSA', '-keysize', '4096',
                '-validity', '10000', '-dname', 'CN=AI Composition Camera',
                '-storepass:env', 'CAMERA_KEY_PASSWORD', '-keypass:env', 'CAMERA_KEY_PASSWORD'],
               env=env, check=True, capture_output=True)
contents = f'storeFile={key}\nstorePassword={password}\nkeyAlias=camera-release\nkeyPassword={password}\n'
for path in (folder / 'signing.properties', config):
    path.write_text(contents)
    path.chmod(0o600)
key.chmod(0o600)
print(f'Permanent key: {key}\nBack up this directory securely. Signing credentials were not printed.')
