"""Roadio DEV-09 local API. Run `python3 server/app.py --help`."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import secrets
import sqlite3
from datetime import datetime, timedelta, timezone
from email.parser import BytesParser
from email.policy import default
from http.cookies import CookieError, SimpleCookie
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit
from uuid import NAMESPACE_URL, UUID, uuid4, uuid5

MAX_AUDIO = 10 * 1024 * 1024
MAX_REQUEST = MAX_AUDIO + 64 * 1024
MIMES = {'audio/webm', 'audio/mp4', 'audio/mpeg', 'audio/wav', 'audio/x-wav', 'audio/ogg', 'audio/aac'}
RADIUS = 100
LIFETIME = timedelta(hours=72)
TOKEN_PATTERN = re.compile(r'[A-Za-z0-9_-]{32,128}')
WEB_SESSION_COOKIE = 'roadio_web_session'
WEB_SESSION_LIFETIME = timedelta(days=180)
WEB_SESSION_RENEWAL_WINDOW = timedelta(days=7)


def utcnow():
    return datetime.now(timezone.utc)


def stamp(value):
    return value.isoformat(timespec='seconds').replace('+00:00', 'Z')


def parse_stamp(value):
    return datetime.fromisoformat(value.replace('Z', '+00:00'))


def connect(data_dir):
    data_dir.mkdir(parents=True, exist_ok=True)
    (data_dir / 'audio').mkdir(exist_ok=True)
    db = sqlite3.connect(data_dir / 'roadio.sqlite3', timeout=10)
    db.row_factory = sqlite3.Row
    db.execute('PRAGMA foreign_keys=ON')
    db.executescript('''
        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY, display_name TEXT NOT NULL,
            token_hash TEXT NOT NULL UNIQUE
        );
        CREATE TABLE IF NOT EXISTS web_sessions (
            session_hash TEXT PRIMARY KEY,
            user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            user_token_hash TEXT NOT NULL,
            expires_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS recipients (
            owner_id TEXT NOT NULL REFERENCES users(id),
            recipient_id TEXT NOT NULL REFERENCES users(id),
            PRIMARY KEY (owner_id, recipient_id)
        );
        CREATE TABLE IF NOT EXISTS capsules (
            id TEXT PRIMARY KEY, owner_id TEXT NOT NULL REFERENCES users(id),
            title TEXT NOT NULL, description TEXT NOT NULL, latitude REAL NOT NULL,
            longitude REAL NOT NULL, visibility TEXT NOT NULL,
            recipients TEXT NOT NULL, content_origin TEXT NOT NULL,
            audio_type TEXT NOT NULL DEFAULT 'VOICE_MESSAGE',
            created_at TEXT NOT NULL, expires_at TEXT NOT NULL,
            duration_ms INTEGER NOT NULL, state TEXT NOT NULL,
            audio_mime TEXT NOT NULL, audio_size INTEGER NOT NULL
        );
        CREATE INDEX IF NOT EXISTS capsules_active ON capsules(state, expires_at);
        CREATE INDEX IF NOT EXISTS capsules_owner ON capsules(owner_id, created_at);
    ''')
    columns = {row['name'] for row in db.execute('PRAGMA table_info(capsules)')}
    if 'request_key' not in columns:
        db.execute('ALTER TABLE capsules ADD COLUMN request_key TEXT')
    if 'audio_type' not in columns:
        db.execute("ALTER TABLE capsules ADD COLUMN audio_type TEXT NOT NULL DEFAULT 'VOICE_MESSAGE'")
    db.execute('CREATE UNIQUE INDEX IF NOT EXISTS capsules_request_key ON capsules(owner_id, request_key)')
    return db


def bootstrap_demo_users(db, env=os.environ):
    """Provision stable demo users from deployment secrets without logging either token."""
    owner_token = env.get('ROADIO_BOOTSTRAP_OWNER_TOKEN', '')
    vehicle_token = env.get('ROADIO_BOOTSTRAP_VEHICLE_TOKEN', '')
    if not owner_token and not vehicle_token:
        return None
    if not TOKEN_PATTERN.fullmatch(owner_token) or not TOKEN_PATTERN.fullmatch(vehicle_token):
        raise ValueError('both Roadio bootstrap tokens must contain 32–128 URL-safe characters')
    if owner_token == vehicle_token:
        raise ValueError('Roadio bootstrap owner and vehicle tokens must differ')
    owner_id = str(uuid5(NAMESPACE_URL, 'https://roadio.app/demo/owner'))
    vehicle_id = str(uuid5(NAMESPACE_URL, 'https://roadio.app/demo/vehicle'))
    owner_name = env.get('ROADIO_BOOTSTRAP_OWNER_NAME', 'Roadio 시연 작성자').strip()
    vehicle_name = env.get('ROADIO_BOOTSTRAP_VEHICLE_NAME', 'Roadio 차량 청취자').strip()
    if not owner_name or not vehicle_name or len(owner_name) > 60 or len(vehicle_name) > 60:
        raise ValueError('Roadio bootstrap display names must contain 1–60 characters')
    with db:
        db.execute('''INSERT INTO users (id, display_name, token_hash) VALUES (?,?,?)
                      ON CONFLICT(id) DO UPDATE SET display_name=excluded.display_name,
                                                    token_hash=excluded.token_hash''',
                   (owner_id, owner_name, hashlib.sha256(owner_token.encode()).hexdigest()))
        db.execute('''INSERT INTO users (id, display_name, token_hash) VALUES (?,?,?)
                      ON CONFLICT(id) DO UPDATE SET display_name=excluded.display_name,
                                                    token_hash=excluded.token_hash''',
                   (vehicle_id, vehicle_name, hashlib.sha256(vehicle_token.encode()).hexdigest()))
        db.execute('INSERT OR IGNORE INTO recipients VALUES (?,?)', (owner_id, vehicle_id))
    return {'ownerUserId': owner_id, 'vehicleUserId': vehicle_id}


def token_user(db, header):
    if not header.startswith('Bearer '):
        return None
    token = header[7:]
    if not TOKEN_PATTERN.fullmatch(token):
        return None
    return db.execute('SELECT * FROM users WHERE token_hash=?', (hashlib.sha256(token.encode()).hexdigest(),)).fetchone()


def point(value):
    if not isinstance(value, dict) or set(value) != {'latitude', 'longitude'}:
        raise ValueError('position must contain latitude and longitude')
    lat, lon = value['latitude'], value['longitude']
    if any(isinstance(x, bool) or not isinstance(x, (int, float)) or not math.isfinite(x) for x in (lat, lon)):
        raise ValueError('invalid coordinates')
    if not -90 <= lat <= 90 or not -180 <= lon <= 180:
        raise ValueError('coordinates out of range')
    return lat, lon


def distance(a, b, c, d):
    p1, p2 = math.radians(a), math.radians(c)
    dp, dl = p2 - p1, math.radians(d - b)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * 6371000 * math.asin(min(1, math.sqrt(h)))


def validate_recipients(db, owner_id, visibility, recipients):
    if not isinstance(recipients, list) or (visibility == 'SELECTED' and not recipients) or (visibility != 'SELECTED' and recipients):
        raise ValueError('invalid recipients for visibility')
    if len(recipients) != len(set(map(str, recipients))) or len(recipients) > 50:
        raise ValueError('duplicate or excessive recipients')
    for recipient in recipients:
        try:
            UUID(recipient)
        except (ValueError, TypeError, AttributeError):
            raise ValueError('invalid recipient ID') from None
        if not db.execute('SELECT 1 FROM recipients WHERE owner_id=? AND recipient_id=?', (owner_id, recipient)).fetchone():
            raise ValueError('recipient is not in the configured roster')


def validate_create(db, owner_id, data, demo):
    if not isinstance(data, dict) or set(data) - {'title', 'description', 'position', 'visibility', 'selectedRecipientUserIds', 'contentOrigin', 'durationMs', 'audioType'}:
        raise ValueError('unknown metadata field')
    title = data.get('title')
    if not isinstance(title, str) or not 1 <= len(title.strip()) <= 80:
        raise ValueError('title must contain 1–80 characters')
    description = data.get('description', '')
    if not isinstance(description, str) or len(description) > 160:
        raise ValueError('description too long')
    lat, lon = point(data.get('position'))
    visibility = data.get('visibility')
    if visibility not in ('SELF', 'SELECTED', 'PUBLIC'):
        raise ValueError('invalid visibility')
    recipients = data.get('selectedRecipientUserIds', [])
    validate_recipients(db, owner_id, visibility, recipients)
    origin = data.get('contentOrigin')
    if origin not in ('MOBILE_RECORDING', 'PRELOADED_SAMPLE') or (origin == 'PRELOADED_SAMPLE' and not demo):
        raise ValueError('invalid content origin')
    duration = data.get('durationMs')
    if isinstance(duration, bool) or not isinstance(duration, int) or not 1 <= duration <= 600000:
        raise ValueError('durationMs must be 1–600000')
    audio_type = data.get('audioType', 'VOICE_MESSAGE')
    if audio_type not in ('VOICE_MESSAGE', 'LOCAL_DRIVING_TIP'):
        raise ValueError('invalid audioType')
    return title.strip(), description, lat, lon, visibility, recipients, origin, duration, audio_type


def state_for(row, now):
    if row['state'] == 'DELETED':
        return 'DELETED'
    return 'EXPIRED' if parse_stamp(row['expires_at']) <= now else row['state']


def owner_json(row, now):
    return dict(id=row['id'], title=row['title'], description=row['description'],
                ownerUserId=row['owner_id'], position=dict(latitude=row['latitude'], longitude=row['longitude']),
                radiusMeters=RADIUS, visibility=row['visibility'], selectedRecipientUserIds=json.loads(row['recipients']),
                expiresAt=row['expires_at'], createdAt=row['created_at'], durationMs=row['duration_ms'],
                contentOrigin=row['content_origin'], audioType=row['audio_type'], state=state_for(row, now))


def accessible(row, user_id, now):
    if state_for(row, now) != 'ACTIVE':
        return False
    return (row['owner_id'] == user_id or row['visibility'] == 'PUBLIC' or
            row['visibility'] == 'SELECTED' and user_id in json.loads(row['recipients']))


class Api(SimpleHTTPRequestHandler):
    server_version = 'Roadio/0.1'

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(args[2].web_dir), **kwargs)

    def end_headers(self):
        if self.command in ('GET', 'HEAD') and urlsplit(self.path).path in ('/', '/index.html'):
            self.send_header('Cache-Control', 'no-cache')
        super().end_headers()

    def send_json(self, code, body, *, cookie=None):
        data = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        if cookie is not None:
            self.send_header('Set-Cookie', cookie)
        self.end_headers()
        self.wfile.write(data)

    def error_json(self, code, message):
        self.send_json(code, {'error': message})

    def read_body(self):
        try:
            length = int(self.headers.get('Content-Length', ''))
        except ValueError:
            raise ValueError('Content-Length required') from None
        if length < 0 or length > MAX_REQUEST:
            raise OverflowError('request too large')
        return self.rfile.read(length)

    def web_session_user(self, db):
        if self.headers.get('X-Roadio-Web') != '1':
            return None
        try:
            cookie = SimpleCookie(self.headers.get('Cookie', '')).get(WEB_SESSION_COOKIE)
        except CookieError:
            return None
        if cookie is None or not TOKEN_PATTERN.fullmatch(cookie.value):
            return None
        return db.execute('''SELECT u.*, s.expires_at AS web_session_expires_at
                             FROM web_sessions s JOIN users u ON u.id=s.user_id
                             WHERE s.session_hash=? AND s.expires_at>? AND s.user_token_hash=u.token_hash''',
                          (hashlib.sha256(cookie.value.encode()).hexdigest(), stamp(utcnow()))).fetchone()

    def user_and_db(self, *, bearer_only=False):
        db = connect(self.server.data_dir)
        authorization = self.headers.get('Authorization')
        if authorization is not None or bearer_only:
            user = token_user(db, authorization or '')
        else:
            user = self.web_session_user(db)
        if user is None:
            db.close()
            self.error_json(401, 'authentication required')
            return None, None
        return db, user

    def start_web_session(self, db, user):
        session = secrets.token_urlsafe(32)
        now = utcnow()
        with db:
            db.execute('DELETE FROM web_sessions WHERE expires_at<=?', (stamp(now),))
            db.execute('INSERT INTO web_sessions VALUES (?,?,?,?)',
                       (hashlib.sha256(session.encode()).hexdigest(), user['id'], user['token_hash'],
                        stamp(now + WEB_SESSION_LIFETIME)))
        cookie = SimpleCookie()
        cookie[WEB_SESSION_COOKIE] = session
        cookie[WEB_SESSION_COOKIE]['path'] = '/v1/'
        cookie[WEB_SESSION_COOKIE]['max-age'] = int(WEB_SESSION_LIFETIME.total_seconds())
        cookie[WEB_SESSION_COOKIE]['httponly'] = True
        cookie[WEB_SESSION_COOKIE]['samesite'] = 'Strict'
        # The local development server listens over HTTP on loopback.
        cookie[WEB_SESSION_COOKIE]['secure'] = self.server.server_address[0] != '127.0.0.1'
        self.send_json(200, {'connected': True}, cookie=cookie[WEB_SESSION_COOKIE].OutputString())

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == '/v1/health':
            self.send_json(200, {'service': 'roadio', 'mode': getattr(self.server, 'mode', 'local-development')})
            return
        if not path.startswith('/v1/'):
            return super().do_GET()
        db, user = self.user_and_db()
        if db is None:
            return
        try:
            if path == '/v1/web-session':
                if (self.headers.get('Authorization') is None
                        and parse_stamp(user['web_session_expires_at']) <= utcnow() + WEB_SESSION_RENEWAL_WINDOW):
                    self.start_web_session(db, user)
                else:
                    self.send_json(200, {'connected': True})
            elif path == '/v1/capsules':
                rows = db.execute('SELECT * FROM capsules WHERE owner_id=? ORDER BY created_at DESC', (user['id'],)).fetchall()
                self.send_json(200, [owner_json(row, utcnow()) for row in rows])
            elif path == '/v1/me/recipients':
                rows = db.execute('''SELECT u.id, u.display_name FROM recipients r JOIN users u ON u.id=r.recipient_id
                                     WHERE r.owner_id=? ORDER BY u.display_name''', (user['id'],)).fetchall()
                self.send_json(200, [{'id': row['id'], 'displayName': row['display_name']} for row in rows])
            elif re.fullmatch(r'/v1/capsules/[0-9a-f-]{36}/audio', path):
                capsule_id = path.split('/')[3]
                row = db.execute('SELECT * FROM capsules WHERE id=?', (capsule_id,)).fetchone()
                if row is None or not accessible(row, user['id'], utcnow()):
                    self.error_json(404, 'capsule not found')
                    return
                audio_path = self.server.data_dir / 'audio' / row['id']
                try:
                    size = audio_path.stat().st_size
                except FileNotFoundError:
                    self.error_json(404, 'audio not found')
                    return
                start, end = 0, size - 1
                range_header = self.headers.get('Range')
                if range_header:
                    match = re.fullmatch(r'bytes=(\d*)-(\d*)', range_header)
                    if not match or (not match[1] and not match[2]):
                        self.error_json(400, 'invalid Range')
                        return
                    if match[1]:
                        start = int(match[1]); end = min(int(match[2]), size - 1) if match[2] else size - 1
                    else:
                        suffix = int(match[2]); start = max(0, size - suffix)
                    if start >= size or start > end:
                        self.send_response(416)
                        self.send_header('Content-Range', f'bytes */{size}')
                        self.end_headers()
                        return
                self.send_response(206 if range_header else 200)
                self.send_header('Content-Type', row['audio_mime'])
                self.send_header('Content-Length', str(end - start + 1))
                self.send_header('Accept-Ranges', 'bytes')
                self.send_header('Cache-Control', 'no-store')
                self.send_header('X-Content-Type-Options', 'nosniff')
                if range_header:
                    self.send_header('Content-Range', f'bytes {start}-{end}/{size}')
                self.end_headers()
                with audio_path.open('rb') as file:
                    file.seek(start)
                    self.wfile.write(file.read(end - start + 1))
            else:
                self.error_json(404, 'not found')
        finally:
            db.close()

    def do_POST(self):
        path = urlsplit(self.path).path
        if path == '/v1/web-session' and self.headers.get('X-Roadio-Web') != '1':
            self.error_json(403, 'web session header required')
            return
        db, user = self.user_and_db(bearer_only=path == '/v1/web-session')
        if db is None:
            return
        try:
            if path == '/v1/web-session':
                self.start_web_session(db, user)
            elif path == '/v1/capsules':
                request_key = self.headers.get('Idempotency-Key', '')
                if not re.fullmatch(r'[A-Za-z0-9_-]{16,100}', request_key):
                    raise ValueError('Idempotency-Key required')
                existing = db.execute('SELECT * FROM capsules WHERE owner_id=? AND request_key=?',
                                      (user['id'], request_key)).fetchone()
                if existing:
                    self.send_json(201, owner_json(existing, utcnow()))
                    return
                content_type = self.headers.get('Content-Type', '')
                if not content_type.startswith('multipart/form-data;'):
                    raise ValueError('multipart/form-data required')
                body = self.read_body()
                message = BytesParser(policy=default).parsebytes(b'Content-Type: ' + content_type.encode() + b'\r\nMIME-Version: 1.0\r\n\r\n' + body)
                parts = {part.get_param('name', header='content-disposition'): part for part in message.iter_parts()}
                if set(parts) != {'metadata', 'audio'}:
                    raise ValueError('metadata and audio required')
                metadata = json.loads(parts['metadata'].get_payload(decode=True))
                values = validate_create(db, user['id'], metadata, self.server.demo)
                audio = parts['audio'].get_payload(decode=True)
                mime = parts['audio'].get_content_type()
                if mime not in MIMES or not audio:
                    raise ValueError('unsupported or empty audio')
                if len(audio) > MAX_AUDIO:
                    raise OverflowError('audio too large')
                capsule_id = str(uuid4())
                created = utcnow()
                audio_path = self.server.data_dir / 'audio' / capsule_id
                audio_path.write_bytes(audio)
                title, description, lat, lon, visibility, recipients, origin, duration, audio_type = values
                try:
                    with db:
                        db.execute('''INSERT INTO capsules (id, owner_id, title, description, latitude, longitude,
                                    visibility, recipients, content_origin, audio_type, created_at, expires_at, duration_ms,
                                    state, audio_mime, audio_size, request_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)''',
                                   (capsule_id, user['id'], title, description, lat, lon, visibility,
                                    json.dumps(recipients), origin, audio_type, stamp(created), stamp(created + LIFETIME),
                                    duration, 'ACTIVE', mime, len(audio), request_key))
                except Exception:
                    audio_path.unlink(missing_ok=True)
                    raise
                row = db.execute('SELECT * FROM capsules WHERE id=?', (capsule_id,)).fetchone()
                self.send_json(201, owner_json(row, created))
            elif path == '/v1/capsules/nearby/search':
                if not self.headers.get('Content-Type', '').startswith('application/json'):
                    raise ValueError('application/json required')
                request = json.loads(self.read_body())
                if not isinstance(request, dict) or set(request) - {'position', 'locationSource', 'limit'}:
                    raise ValueError('invalid search request')
                lat, lon = point(request.get('position'))
                if request.get('locationSource') not in ('PLEOS_DEVICE', 'PLEOS_EMULATOR', 'DEMO_MANUAL'):
                    raise ValueError('invalid locationSource')
                limit = request.get('limit', 20)
                if isinstance(limit, bool) or not isinstance(limit, int) or not 1 <= limit <= 30:
                    raise ValueError('invalid limit')
                now = utcnow()
                rows = db.execute('''SELECT c.*, u.display_name FROM capsules c JOIN users u ON u.id=c.owner_id
                                     WHERE c.state='ACTIVE' AND c.expires_at>?''', (stamp(now),)).fetchall()
                found = []
                for row in rows:
                    meters = distance(lat, lon, row['latitude'], row['longitude'])
                    if meters <= RADIUS and accessible(row, user['id'], now):
                        found.append(dict(id=row['id'], title=row['title'], authorDisplayName=row['display_name'],
                                          durationMs=row['duration_ms'], visibility=row['visibility'],
                                          expiresAt=row['expires_at'], distanceMeters=round(meters), contentOrigin=row['content_origin'],
                                          audioType=row['audio_type']))
                found.sort(key=lambda item: (item['distanceMeters'], item['id']))
                self.send_json(200, {'capsules': found[:limit]})
            else:
                self.error_json(404, 'not found')
        except OverflowError as error:
            self.error_json(413, str(error))
        except (ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
            self.error_json(400, str(error))
        finally:
            db.close()

    def do_PATCH(self):
        db, user = self.user_and_db()
        if db is None:
            return
        try:
            path = urlsplit(self.path).path
            if not re.fullmatch(r'/v1/capsules/[0-9a-f-]{36}', path):
                self.error_json(404, 'not found')
                return
            capsule_id = path.split('/')[3]
            row = db.execute('SELECT * FROM capsules WHERE id=? AND owner_id=?', (capsule_id, user['id'])).fetchone()
            if row is None or state_for(row, utcnow()) != 'ACTIVE':
                self.error_json(404, 'capsule not found')
                return
            if not self.headers.get('Content-Type', '').startswith('application/json'):
                raise ValueError('application/json required')
            data = json.loads(self.read_body())
            if not isinstance(data, dict) or set(data) - {'visibility', 'selectedRecipientUserIds'}:
                raise ValueError('invalid update request')
            visibility = data.get('visibility')
            if visibility not in ('SELF', 'SELECTED', 'PUBLIC'):
                raise ValueError('invalid visibility')
            recipients = data.get('selectedRecipientUserIds', [])
            validate_recipients(db, user['id'], visibility, recipients)
            with db:
                db.execute('UPDATE capsules SET visibility=?, recipients=? WHERE id=?',
                           (visibility, json.dumps(recipients), capsule_id))
            updated = db.execute('SELECT * FROM capsules WHERE id=?', (capsule_id,)).fetchone()
            self.send_json(200, owner_json(updated, utcnow()))
        except OverflowError as error:
            self.error_json(413, str(error))
        except (ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
            self.error_json(400, str(error))
        finally:
            db.close()

    def do_DELETE(self):
        db, user = self.user_and_db()
        if db is None:
            return
        try:
            path = urlsplit(self.path).path
            if not re.fullmatch(r'/v1/capsules/[0-9a-f-]{36}', path):
                self.error_json(404, 'not found')
                return
            capsule_id = path.split('/')[3]
            with db:
                changed = db.execute("UPDATE capsules SET state='DELETED' WHERE id=? AND owner_id=? AND state!='DELETED'",
                                     (capsule_id, user['id'])).rowcount
            if not changed:
                self.error_json(404, 'capsule not found')
                return
            self.send_response(204)
            self.send_header('Cache-Control', 'no-store')
            self.end_headers()
        finally:
            db.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data-dir', type=Path, default=Path('server/data'))
    sub = parser.add_subparsers(dest='command', required=True)
    user_cmd = sub.add_parser('create-user', help='Provision a local account and print its bearer token once')
    user_cmd.add_argument('display_name')
    link_cmd = sub.add_parser('add-recipient', help='Add a consented test recipient to an owner roster')
    link_cmd.add_argument('owner_user_id')
    link_cmd.add_argument('recipient_user_id')
    serve_cmd = sub.add_parser('serve', help='Start the local API')
    serve_cmd.add_argument('--host', default='127.0.0.1')
    serve_cmd.add_argument('--port', type=int, default=8787)
    serve_cmd.add_argument('--demo', action='store_true', help='Allow explicit preloaded demo samples')
    args = parser.parse_args()
    db = connect(args.data_dir)
    if args.command == 'create-user':
        user_id, token = str(uuid4()), secrets.token_urlsafe(32)
        with db:
            db.execute('INSERT INTO users VALUES (?,?,?)', (user_id, args.display_name,
                       hashlib.sha256(token.encode()).hexdigest()))
        print(json.dumps({'userId': user_id, 'bearerToken': token}, ensure_ascii=False))
    elif args.command == 'add-recipient':
        if args.owner_user_id == args.recipient_user_id:
            parser.error('owner and recipient must differ')
        try:
            with db:
                db.execute('INSERT INTO recipients VALUES (?,?)', (args.owner_user_id, args.recipient_user_id))
        except sqlite3.IntegrityError:
            parser.error('users must exist and the relationship must be new')
        print('Test recipient added. This command does not represent a production invitation or consent flow.')
    else:
        public_bind = os.environ.get('ROADIO_ALLOW_PUBLIC_BIND') == '1'
        if args.host not in ('127.0.0.1', 'localhost') and not public_bind:
            parser.error('set ROADIO_ALLOW_PUBLIC_BIND=1 to listen outside localhost')
        try:
            provisioned = bootstrap_demo_users(db)
        except ValueError as error:
            parser.error(str(error))
        if provisioned:
            print(json.dumps({'bootstrapUsersReady': True, **provisioned}), flush=True)
        server = ThreadingHTTPServer((args.host, args.port), Api)
        server.data_dir, server.demo = args.data_dir, args.demo
        server.mode = os.environ.get('ROADIO_DEPLOYMENT_MODE', 'local-development')
        server.web_dir = Path(__file__).resolve().parent.parent / 'web'
        print(f'Roadio API at http://{args.host}:{args.port} ({server.mode})', flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
        finally:
            server.server_close()
    db.close()


if __name__ == '__main__':
    main()
