#!/usr/bin/env bash
set -euo pipefail

# send_fcm_push.sh <deviceId> [sync|logcat|location]
#
# Emergency wake tool: sends a HIGH-PRIORITY FCM data message to the given
# device so its foreground service (which hosts the app-snapshot coroutine)
# is restarted even in the background (Android 12+ grants the background
# FGS-start exemption after a high-priority FCM message).
#
#   sync     (default) full sync + FGS restart
#   logcat   immediately upload a fresh logcat dump to devices/<id>/logcat
#   location restart foreground service only
#
# Uses the LOCAL Firebase service-account key (app/src/main/assets/service-account.json)
# which is never committed and never exposed in the public viewer.
#
# Prereqs (all ship with macOS): python3, openssl, curl.

DEVICE_ID="${1:-}"
MODE="${2:-sync}"
if [ -z "$DEVICE_ID" ]; then
  echo "Usage: $0 <deviceId> [sync|logcat|location]" >&2
  echo "  e.g. $0 b0ed0743930aef8e         (Samsung A26, full sync)" >&2
  echo "  e.g. $0 b0ed0743930aef8e logcat  (on-demand logcat dump)" >&2
  exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SVC_ACCOUNT="$SCRIPT_DIR/app/src/main/assets/service-account.json"
RTDB_BASE="https://instgram-7148c-default-rtdb.europe-west1.firebasedatabase.app"

if [ ! -f "$SVC_ACCOUNT" ]; then
  echo "ERROR: service account not found at $SVC_ACCOUNT" >&2
  echo "Restore it from Firebase Console -> Project settings -> Service accounts." >&2
  exit 1
fi

KEY_FILE="$(mktemp -t fcm_key.XXXXXX)"
trap 'rm -f "$KEY_FILE"' EXIT

# Extract the PKCS#8 private key from the JSON (json.load turns \n escapes into real newlines)
python3 -c "import json,sys;print(json.load(open(sys.argv[1]))['private_key'])" "$SVC_ACCOUNT" > "$KEY_FILE"
CLIENT_EMAIL="$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))['client_email'])" "$SVC_ACCOUNT")"
PROJECT_ID="$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))['project_id'])" "$SVC_ACCOUNT")"

NOW="$(date +%s)"
EXP="$((NOW + 3600))"

# ── 1. Build a signed RS256 JWT for OAuth2 ─────────────────────────────
HEADER="$(python3 -c "import json,base64;print(base64.urlsafe_b64encode(json.dumps({'alg':'RS256','typ':'JWT'}).encode()).rstrip(b'=').decode())")"
CLAIMS="$(python3 -c "
import json,base64,sys
c={'iss':sys.argv[1],'scope':'https://www.googleapis.com/auth/firebase.messaging','aud':'https://oauth2.googleapis.com/token','iat':int(sys.argv[2]),'exp':int(sys.argv[3])}
print(base64.urlsafe_b64encode(json.dumps(c).encode()).rstrip(b'=').decode())
" "$CLIENT_EMAIL" "$NOW" "$EXP")"
SIG="$(printf '%s' "$HEADER.$CLAIMS" | openssl dgst -sha256 -sign "$KEY_FILE" -binary | python3 -c "import base64,sys;print(base64.urlsafe_b64encode(sys.stdin.buffer.read()).rstrip(b'=').decode())")"
JWT="$HEADER.$CLAIMS.$SIG"

# ── 2. Exchange the JWT for an OAuth2 access token ─────────────────────
TOKEN_JSON="$(curl -sS -X POST "https://oauth2.googleapis.com/token" \
  -d "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
  --data-urlencode "assertion=$JWT")"
ACCESS_TOKEN="$(python3 -c "import json,sys;print(json.loads(sys.argv[1]).get('access_token',''))" "$TOKEN_JSON")"
if [ -z "$ACCESS_TOKEN" ]; then
  echo "ERROR: failed to obtain access token: $TOKEN_JSON" >&2
  exit 1
fi

# ── 3. Fetch the device FCM token from RTDB ────────────────────────────
FCM_JSON="$(curl -sS "$RTDB_BASE/devices/$DEVICE_ID/fcm/token.json")"
FCM_TOKEN="$(python3 -c "import json,sys;print(json.loads(sys.argv[1]).get('fcmToken',''))" "$FCM_JSON" 2>/dev/null || true)"
if [ -z "$FCM_TOKEN" ]; then
  echo "ERROR: no FCM token registered for device $DEVICE_ID (see the viewer's Sync tab)" >&2
  exit 1
fi

# ── 4. Send the high-priority wake message ─────────────────────────────
case "$MODE" in
  logcat) DATA="{'logcat':'true'}" ;;
  location) DATA="{'location':'true'}" ;;
  sync) DATA="{'sync':'true'}" ;;
  *) echo "ERROR: unknown mode '$MODE' (use sync|logcat|location)" >&2; exit 2 ;;
esac
PAYLOAD="$(python3 -c "
import json,sys
print(json.dumps({'message':{'token':sys.argv[1],'data':sys.argv[2],'android':{'priority':'high'}}}))
" "$FCM_TOKEN" "$DATA")"
echo "Sending high-priority FCM '$MODE' message to device $DEVICE_ID ..."
curl -sS -X POST "https://fcm.googleapis.com/v1/projects/$PROJECT_ID/messages:send" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD"
echo
