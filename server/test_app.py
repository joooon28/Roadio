import hashlib
import json
from pathlib import Path
import tempfile
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from uuid import uuid4
from http.cookies import SimpleCookie
from http.server import ThreadingHTTPServer

from app import Api, bootstrap_demo_users, connect, parse_stamp, stamp, token_user, utcnow
from datetime import timedelta


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.data = Path(self.temp.name)
        self.db = connect(self.data)
        self.people = {}
        for name in ('owner', 'neighbor', 'stranger'):
            user_id, token = str(uuid4()), f'{name}-' + 'x' * 36
            with self.db:
                self.db.execute('INSERT INTO users VALUES (?,?,?)', (user_id, name, hashlib.sha256(token.encode()).hexdigest()))
            self.people[name] = (user_id, token)
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), Api)
        self.server.data_dir, self.server.demo = self.data, False
        self.server.web_dir = Path(__file__).resolve().parent.parent / 'web'
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.db.close()
        self.temp.cleanup()

    def request(self, method, path, user='owner', body=None, content_type=None, headers=None):
        headers = dict(headers or {})
        if user:
            headers['Authorization'] = 'Bearer ' + self.people[user][1]
        if content_type:
            headers['Content-Type'] = content_type
        request = Request(f'http://127.0.0.1:{self.server.server_port}{path}', data=body, headers=headers, method=method)
        try:
            with urlopen(request, timeout=3) as response:
                return response.status, response.headers, response.read()
        except HTTPError as error:
            try:
                return error.code, error.headers, error.read()
            finally:
                error.close()

    def create(self, visibility='PUBLIC', recipients=None, user='owner', key=None, headers=None):
        metadata = {'title': '안녕', 'position': {'latitude': 37.5665, 'longitude': 126.978},
                    'visibility': visibility, 'contentOrigin': 'MOBILE_RECORDING', 'durationMs': 1200}
        if recipients is not None:
            metadata['selectedRecipientUserIds'] = recipients
        boundary = 'test-boundary'
        audio = b'RIFF' + b'0' * 32
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\nContent-Type: application/json\r\n\r\n'
                + json.dumps(metadata) + f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="audio"; filename="sample.wav"\r\n'
                + 'Content-Type: audio/wav\r\n\r\n').encode() + audio + f'\r\n--{boundary}--\r\n'.encode()
        status, _, payload = self.request('POST', '/v1/capsules', user, body,
                                          f'multipart/form-data; boundary={boundary}',
                                          {**(headers or {}), 'Idempotency-Key': key or str(uuid4())})
        return status, json.loads(payload)

    def search(self, user='stranger', lat=37.5665, source='PLEOS_EMULATOR'):
        body = json.dumps({'position': {'latitude': lat, 'longitude': 126.978}, 'locationSource': source}).encode()
        status, headers, payload = self.request('POST', '/v1/capsules/nearby/search', user, body, 'application/json')
        return status, headers, json.loads(payload)

    def test_web_entry_points_require_cache_revalidation(self):
        for method in ('GET', 'HEAD'):
            for path in ('/', '/index.html', '/index.html?view=current'):
                with self.subTest(method=method, path=path):
                    status, headers, _ = self.request(method, path, user=None)
                    self.assertEqual(status, 200)
                    self.assertEqual(headers['Cache-Control'], 'no-cache')

    def test_web_session_reuses_account_for_registration_and_archive(self):
        session_path = '/v1/web-session'
        web_headers = {'X-Roadio-Web': '1'}
        self.assertEqual(self.request('GET', session_path, user=None, headers=web_headers)[0], 401)
        self.assertEqual(self.request('POST', session_path, user=None, headers=web_headers)[0], 401)
        self.assertEqual(self.request('POST', session_path)[0], 403)

        status, headers, payload = self.request('POST', session_path, headers=web_headers)
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(payload), {'connected': True})
        cookie = SimpleCookie(headers['Set-Cookie'])['roadio_web_session']
        self.assertNotEqual(cookie.value, self.people['owner'][1])
        self.assertEqual(cookie['max-age'], str(180 * 24 * 60 * 60))
        self.assertEqual(cookie['path'], '/v1/')
        self.assertEqual(cookie['samesite'], 'Strict')
        self.assertTrue(cookie['httponly'])

        cookie_header = {'Cookie': f'{cookie.key}={cookie.value}'}
        session_headers = {**web_headers, **cookie_header}
        status, headers, _ = self.request('GET', session_path, user=None, headers=session_headers)
        self.assertEqual(status, 200)
        self.assertIsNone(headers['Set-Cookie'])
        session_hash = hashlib.sha256(cookie.value.encode()).hexdigest()
        expiry = self.db.execute('SELECT expires_at FROM web_sessions WHERE session_hash=?', (session_hash,)).fetchone()[0]
        self.assertEqual((parse_stamp(expiry) - utcnow()).days, 179)
        self.assertEqual(self.request('POST', session_path, user=None, headers=session_headers)[0], 401)
        self.assertEqual(self.request('GET', '/v1/capsules', user=None, headers=cookie_header)[0], 401)
        invalid_bearer = {**session_headers, 'Authorization': 'Bearer invalid'}
        self.assertEqual(self.request('GET', '/v1/capsules', user=None, headers=invalid_bearer)[0], 401)
        status, created = self.create(user=None, headers=session_headers)
        self.assertEqual(status, 201)
        status, _, archive = self.request('GET', '/v1/capsules', user=None, headers=session_headers)
        self.assertEqual(status, 200)
        self.assertEqual([item['id'] for item in json.loads(archive)], [created['id']])

        # An existing seven-day session upgrades without asking for the code again.
        with self.db:
            self.db.execute('UPDATE web_sessions SET expires_at=? WHERE session_hash=?',
                            (stamp(utcnow() + timedelta(days=7)), session_hash))
        status, headers, _ = self.request('GET', session_path, user=None, headers=session_headers)
        self.assertEqual(status, 200)
        renewed = SimpleCookie(headers['Set-Cookie'])['roadio_web_session']
        self.assertEqual(renewed['max-age'], str(180 * 24 * 60 * 60))
        renewed_headers = {**web_headers, 'Cookie': f'{renewed.key}={renewed.value}'}
        self.assertEqual(self.request('GET', '/v1/capsules', user=None, headers=renewed_headers)[0], 200)

        with self.db:
            self.db.execute('UPDATE web_sessions SET expires_at=?', (stamp(utcnow() - timedelta(seconds=1)),))
        self.assertEqual(self.request('GET', session_path, user=None, headers=session_headers)[0], 401)

    def test_visibility_radius_expiry_delete_and_range(self):
        self.assertEqual(self.create('SELF')[0], 201)
        neighbor_id = self.people['neighbor'][0]
        with self.db:
            self.db.execute('INSERT INTO recipients VALUES (?,?)', (self.people['owner'][0], neighbor_id))
        status, selected = self.create('SELECTED', [neighbor_id])
        self.assertEqual(status, 201)
        status, public = self.create()
        self.assertEqual(status, 201)
        self.assertEqual(public['radiusMeters'], 100)
        self.assertEqual(parse_stamp(public['expiresAt']) - parse_stamp(public['createdAt']), timedelta(hours=72))
        self.assertEqual(len(self.search('owner')[2]['capsules']), 3)
        self.assertEqual(len(self.search('neighbor')[2]['capsules']), 2)
        stranger_results = self.search('stranger')[2]['capsules']
        self.assertEqual(len(stranger_results), 1)
        self.assertNotIn('position', stranger_results[0])
        self.assertNotIn('audioUrl', stranger_results[0])
        self.assertEqual(self.search('neighbor', lat=37.568)[2]['capsules'], [])
        self.assertEqual(self.request('GET', f"/v1/capsules/{selected['id']}/audio", 'stranger')[0], 404)
        status, headers, audio = self.request('GET', f"/v1/capsules/{public['id']}/audio", 'stranger', headers={'Range': 'bytes=0-3'})
        self.assertEqual((status, audio, headers['Content-Range']), (206, b'RIFF', 'bytes 0-3/36'))
        with self.db:
            self.db.execute('UPDATE capsules SET expires_at=? WHERE id=?', (stamp(utcnow() - timedelta(seconds=1)), selected['id']))
        self.assertEqual(len(self.search('neighbor')[2]['capsules']), 1)
        self.assertEqual(self.request('GET', f"/v1/capsules/{selected['id']}/audio", 'neighbor')[0], 404)
        self.assertEqual(self.request('DELETE', f"/v1/capsules/{public['id']}", 'stranger')[0], 404)
        self.assertEqual(self.request('DELETE', f"/v1/capsules/{public['id']}", 'owner')[0], 204)
        self.assertEqual(self.search()[2]['capsules'], [])
        self.assertEqual(self.request('GET', f"/v1/capsules/{public['id']}/audio", 'owner')[0], 404)
        self.assertEqual(len(json.loads(self.request('GET', '/v1/capsules')[2])), 3)

    def test_update_audience_and_retry(self):
        health = self.request('GET', '/v1/health', user=None)
        self.assertEqual((health[0], json.loads(health[2])['service']), (200, 'roadio'))
        self.assertEqual(self.request('GET', '/index.html', user=None)[0], 200)
        key = str(uuid4())
        status, capsule = self.create('SELF', key=key)
        self.assertEqual(status, 201)
        self.assertEqual(self.create('SELF', key=key)[1]['id'], capsule['id'])
        self.assertEqual(len(json.loads(self.request('GET', '/v1/capsules')[2])), 1)
        body = json.dumps({'visibility': 'PUBLIC'}).encode()
        self.assertEqual(self.request('PATCH', f"/v1/capsules/{capsule['id']}", 'stranger', body, 'application/json')[0], 404)
        self.assertEqual(self.request('PATCH', f"/v1/capsules/{capsule['id']}", 'owner', body, 'application/json')[0], 200)
        self.assertEqual(len(self.search('stranger')[2]['capsules']), 1)
        self.assertEqual(self.request('GET', '/v1/me/recipients')[0], 200)

    def test_invalid_auth_and_recipients(self):
        self.assertEqual(self.request('GET', '/v1/capsules', user=None)[0], 401)
        self.assertEqual(self.create('SELECTED', [str(uuid4())])[0], 400)
        self.assertEqual(self.create('SELECTED', [])[0], 400)
        self.assertEqual(self.create('PUBLIC', [self.people['neighbor'][0]])[0], 400)

    def test_manual_demo_location_is_explicit_and_still_uses_server_access_rules(self):
        self.assertEqual(self.create('SELF')[0], 201)
        self.assertEqual(self.create('PUBLIC')[0], 201)
        status, _, data = self.search(source='DEMO_MANUAL')
        self.assertEqual(status, 200)
        self.assertEqual(len(data['capsules']), 1)
        self.assertEqual(data['capsules'][0]['visibility'], 'PUBLIC')

    def test_deployment_bootstrap_users_are_stable_idempotent_and_secret(self):
        env = {'ROADIO_BOOTSTRAP_OWNER_TOKEN': 'owner_' + 'x' * 36,
               'ROADIO_BOOTSTRAP_VEHICLE_TOKEN': 'vehicle_' + 'y' * 36,
               'ROADIO_BOOTSTRAP_OWNER_NAME': '배포 작성자',
               'ROADIO_BOOTSTRAP_VEHICLE_NAME': '배포 차량'}
        first = bootstrap_demo_users(self.db, env)
        second = bootstrap_demo_users(self.db, env)
        self.assertEqual(first, second)
        self.assertEqual(token_user(self.db, 'Bearer ' + env['ROADIO_BOOTSTRAP_OWNER_TOKEN'])['id'],
                         first['ownerUserId'])
        self.assertEqual(token_user(self.db, 'Bearer ' + env['ROADIO_BOOTSTRAP_VEHICLE_TOKEN'])['id'],
                         first['vehicleUserId'])
        self.assertEqual(self.db.execute('SELECT COUNT(*) FROM recipients WHERE owner_id=? AND recipient_id=?',
                                         (first['ownerUserId'], first['vehicleUserId'])).fetchone()[0], 1)
        stored = ' '.join(row['token_hash'] for row in self.db.execute('SELECT token_hash FROM users'))
        self.assertNotIn(env['ROADIO_BOOTSTRAP_OWNER_TOKEN'], stored)
        self.assertNotIn(env['ROADIO_BOOTSTRAP_VEHICLE_TOKEN'], stored)

    def test_deployment_bootstrap_requires_two_distinct_valid_tokens(self):
        with self.assertRaises(ValueError):
            bootstrap_demo_users(self.db, {'ROADIO_BOOTSTRAP_OWNER_TOKEN': 'short'})
        token = 'same_' + 'z' * 36
        with self.assertRaises(ValueError):
            bootstrap_demo_users(self.db, {'ROADIO_BOOTSTRAP_OWNER_TOKEN': token,
                                           'ROADIO_BOOTSTRAP_VEHICLE_TOKEN': token})


if __name__ == '__main__':
    unittest.main()
