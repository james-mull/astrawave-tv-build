#!/usr/bin/env python3
import gzip
import io
import json
import os
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone, timedelta

SOURCES = [
    ('us-national', 'https://epgshare01.online/epgshare01/epg_ripper_US2.xml.gz'),
    ('us-sports', 'https://epgshare01.online/epgshare01/epg_ripper_US_SPORTS1.xml.gz'),
]
OUT_JSON = 'astrawave-epg/us.json.gz'
OUT_XML = 'astrawave-epg/us.xml.gz'
OUT_NOW = 'astrawave-epg/us-now-next.json'
WINDOW_HOURS = 96
NOW_WINDOW_HOURS = 18
MAX_CHANNELS = 5000
MAX_PROGRAMMES_PER_CHANNEL = 80


def fetch_gz(url: str) -> bytes:
    req = urllib.request.Request(url, headers={'User-Agent': 'AstraWave-EPG-Builder/1.1'})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def parse_time(raw: str):
    raw = (raw or '').strip()
    if not raw:
        return None
    try:
        stamp = raw[:14]
        dt = datetime.strptime(stamp, '%Y%m%d%H%M%S').replace(tzinfo=timezone.utc)
        tail = raw[14:].strip()
        if tail and len(tail) >= 5 and tail[0] in '+-':
            sign = 1 if tail[0] == '+' else -1
            hh, mm = int(tail[1:3]), int(tail[3:5])
            dt -= timedelta(minutes=sign * (hh * 60 + mm))
        return dt
    except Exception:
        return None


def xmltv_time(epoch_ms: int) -> str:
    return datetime.fromtimestamp(epoch_ms / 1000, tz=timezone.utc).strftime('%Y%m%d%H%M%S +0000')


def read_source(label: str, url: str, now: datetime, end: datetime, channels, programmes):
    payload = fetch_gz(url)
    raw = gzip.decompress(payload)
    channel_names = {}
    channel_icons = {}

    for event, elem in ET.iterparse(io.BytesIO(raw), events=('end',)):
        if elem.tag == 'channel':
            cid = elem.attrib.get('id', '').strip()
            if cid:
                display = next((x.text.strip() for x in elem.findall('display-name') if x.text and x.text.strip()), cid)
                icon = next((x.attrib.get('src', '').strip() for x in elem.findall('icon') if x.attrib.get('src')), '')
                channel_names[cid] = display
                channel_icons[cid] = icon
            elem.clear()
        elif elem.tag == 'programme':
            cid = elem.attrib.get('channel', '').strip()
            start = parse_time(elem.attrib.get('start', ''))
            stop = parse_time(elem.attrib.get('stop', ''))
            if cid and start and stop and stop >= now - timedelta(hours=1) and start <= end:
                title = next((x.text.strip() for x in elem.findall('title') if x.text and x.text.strip()), 'Program')
                category = next((x.text.strip() for x in elem.findall('category') if x.text and x.text.strip()), '')
                desc = next((x.text.strip() for x in elem.findall('desc') if x.text and x.text.strip()), '')
                row = programmes.setdefault(cid, [])
                if len(row) < MAX_PROGRAMMES_PER_CHANNEL:
                    row.append({'title': title, 'start': int(start.timestamp() * 1000), 'stop': int(stop.timestamp() * 1000), 'category': category, 'desc': desc})
            elem.clear()

    for cid in programmes.keys():
        if cid not in channels and len(channels) < MAX_CHANNELS:
            channels[cid] = {'id': cid, 'title': channel_names.get(cid, cid), 'posterUrl': channel_icons.get(cid, ''), 'source': label, 'tvgId': cid, 'kind': 'live'}


def write_xmltv(channels, programmes):
    tv = ET.Element('tv', {'generator-info-name': 'AstraWave EPG Cache'})
    for channel in channels:
        ch = ET.SubElement(tv, 'channel', {'id': channel['id']})
        ET.SubElement(ch, 'display-name').text = channel['title']
        if channel.get('posterUrl'):
            ET.SubElement(ch, 'icon', {'src': channel['posterUrl']})
    for cid, rows in programmes.items():
        for item in rows:
            p = ET.SubElement(tv, 'programme', {'channel': cid, 'start': xmltv_time(item['start']), 'stop': xmltv_time(item['stop'])})
            ET.SubElement(p, 'title').text = item['title']
            if item.get('category'):
                ET.SubElement(p, 'category').text = item['category']
            if item.get('desc'):
                ET.SubElement(p, 'desc').text = item['desc']
    payload = ET.tostring(tv, encoding='utf-8', xml_declaration=True)
    with gzip.open(OUT_XML, 'wb', compresslevel=9) as f:
        f.write(payload)


def write_now_next(doc, now):
    end_ms = int((now + timedelta(hours=NOW_WINDOW_HOURS)).timestamp() * 1000)
    min_ms = int((now - timedelta(hours=1)).timestamp() * 1000)
    programmes = {}
    for cid, rows in doc['programs'].items():
        filtered = [p for p in rows if p['stop'] >= min_ms and p['start'] <= end_ms][:24]
        if filtered:
            programmes[cid] = filtered
    used = set(programmes.keys())
    channels = [c for c in doc['channels'] if c['id'] in used]
    compact = {
        'schema': 2,
        'generatedAt': doc['generatedAt'],
        'windowHours': NOW_WINDOW_HOURS,
        'linkedChannels': len(channels),
        'scheduledChannels': len(programmes),
        'sources': doc['sources'],
        'channels': channels,
        'programs': programmes,
    }
    with open(OUT_NOW, 'w', encoding='utf-8') as f:
        json.dump(compact, f, separators=(',', ':'), ensure_ascii=False)


def main():
    now = datetime.now(timezone.utc)
    end = now + timedelta(hours=WINDOW_HOURS)
    channels = {}
    programmes = {}
    source_status = []
    for label, url in SOURCES:
        try:
            before = len(programmes)
            read_source(label, url, now, end, channels, programmes)
            source_status.append({'source': label, 'ok': True, 'channelsAdded': len(programmes) - before})
        except Exception as exc:
            source_status.append({'source': label, 'ok': False, 'error': str(exc)[:300]})

    programmes = {k: sorted(v, key=lambda x: x['start']) for k, v in programmes.items() if v}
    channels = [v for k, v in channels.items() if k in programmes]
    if not programmes:
        raise SystemExit('No EPG programmes were generated; refusing to overwrite last good cache.')

    doc = {
        'schema': 1,
        'generatedAt': now.isoformat(),
        'windowHours': WINDOW_HOURS,
        'linkedChannels': len(channels),
        'scheduledChannels': len(programmes),
        'sources': source_status,
        'channels': channels,
        'programs': programmes,
    }
    os.makedirs(os.path.dirname(OUT_JSON), exist_ok=True)
    with gzip.open(OUT_JSON, 'wt', encoding='utf-8', compresslevel=9) as f:
        json.dump(doc, f, separators=(',', ':'), ensure_ascii=False)
    write_xmltv(channels, programmes)
    write_now_next(doc, now)
    print(f'Wrote {OUT_JSON}, {OUT_XML} and {OUT_NOW}: {len(channels)} channels, {sum(len(v) for v in programmes.values())} programmes')


if __name__ == '__main__':
    main()
