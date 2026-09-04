#!/usr/bin/env python3
import json, time, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "sources.json"
OUT = ROOT / "status.json"
ALLOWED = {"official", "public-domain", "creative-commons", "authorized-redistribution"}

with SRC.open() as f:
    data = json.load(f)

results = []
for ch in data.get("channels", []):
    rights = ch.get("rightsStatus")
    playback_mode = ch.get("playbackMode", "direct")
    enabled = rights in ALLOWED and bool(ch.get("rightsEvidenceUrl"))
    ok = False
    code = None
    latency_ms = None
    error = None
    if enabled:
        req = urllib.request.Request(ch["streamUrl"], method="GET", headers={"User-Agent":"AstraWave-Free-TV-Health/1.0", "Range":"bytes=0-1023"})
        started = time.time()
        try:
            with urllib.request.urlopen(req, timeout=12) as r:
                code = getattr(r, "status", 200)
                r.read(1024)
                ok = 200 <= code < 400
        except Exception as e:
            error = str(e)[:240]
        latency_ms = int((time.time() - started) * 1000)
    results.append({
        "id": ch.get("id"),
        "name": ch.get("name"),
        "playbackMode": playback_mode,
        "rightsStatus": rights,
        "rightsVerified": enabled,
        "healthy": ok,
        "httpStatus": code,
        "latencyMs": latency_ms,
        "error": error,
        "checkedAt": int(time.time())
    })

healthy_direct_ids = {
    r["id"] for r in results
    if r["rightsVerified"] and r["healthy"] and r["playbackMode"] == "direct"
}
playlist = [ch for ch in data.get("channels", []) if ch.get("id") in healthy_direct_ids]
handoffs = [
    ch for ch in data.get("channels", [])
    if ch.get("playbackMode") == "external"
    and ch.get("rightsStatus") in ALLOWED
    and ch.get("rightsEvidenceUrl")
]

OUT.write_text(json.dumps({
    "checkedAt": int(time.time()),
    "results": results,
    "healthyDirectChannelCount": len(playlist),
    "approvedExternalHandoffCount": len(handoffs)
}, indent=2) + "\n")

m3u = ["#EXTM3U"]
for ch in playlist:
    logo = ch.get("logo", "")
    group = ch.get("group", "AstraWave Free TV")
    tvg = ch.get("tvgId", "")
    m3u.append(f'#EXTINF:-1 tvg-id="{tvg}" tvg-logo="{logo}" group-title="{group}",{ch["name"]}')
    m3u.append(ch["streamUrl"])
(ROOT / "astrawave-free-tv.m3u").write_text("\n".join(m3u) + "\n")

print(f"Healthy authorized direct channels: {len(playlist)}")
print(f"Approved official handoffs: {len(handoffs)}")
