#!/usr/bin/env python3
import gzip, json, os, urllib.request, xml.etree.ElementTree as ET
from datetime import datetime, timezone, timedelta

SOURCES=[('us-national','https://epgshare01.online/epgshare01/epg_ripper_US2.xml.gz'),('us-sports','https://epgshare01.online/epgshare01/epg_ripper_US_SPORTS1.xml.gz')]
OUT='astrawave-epg/us-live-guide.json'

def parse_time(raw):
    raw=(raw or '').strip()
    if not raw:return None
    try:
        dt=datetime.strptime(raw[:14],'%Y%m%d%H%M%S').replace(tzinfo=timezone.utc)
        tail=raw[14:].strip()
        if len(tail)>=5 and tail[0] in '+-':
            sign=1 if tail[0]=='+' else -1
            dt-=timedelta(minutes=sign*(int(tail[1:3])*60+int(tail[3:5])))
        return dt
    except:return None

def fetch(url):
    req=urllib.request.Request(url,headers={'User-Agent':'AstraWave-EPG-Lite/1.0'})
    with urllib.request.urlopen(req,timeout=120) as r:return gzip.decompress(r.read())

def main():
    now=datetime.now(timezone.utc); end=now+timedelta(hours=5); min_dt=now-timedelta(hours=1)
    names={}; schedules={}; status=[]
    for label,url in SOURCES:
        try:
            raw=fetch(url); root=ET.fromstring(raw); added=0
            for ch in root.findall('channel'):
                cid=ch.attrib.get('id','').strip()
                if not cid:continue
                dn=ch.find('display-name'); names[cid]=(dn.text or cid).strip() if dn is not None else cid
            for p in root.findall('programme'):
                cid=p.attrib.get('channel','').strip(); start=parse_time(p.attrib.get('start')); stop=parse_time(p.attrib.get('stop'))
                if not cid or not start or not stop or stop<min_dt or start>end:continue
                title=p.find('title'); category=p.find('category')
                row={'title':(title.text or 'Program').strip() if title is not None else 'Program','start':int(start.timestamp()*1000),'stop':int(stop.timestamp()*1000)}
                if category is not None and category.text:row['category']=category.text.strip()
                arr=schedules.setdefault(cid,[])
                if len(arr)<6:arr.append(row)
            status.append({'source':label,'ok':True,'channels':len(schedules)})
        except Exception as e:status.append({'source':label,'ok':False,'error':str(e)[:180]})
    schedules={k:sorted(v,key=lambda x:x['start'])[:6] for k,v in schedules.items() if v}
    if not schedules:raise SystemExit('No programmes; refusing to overwrite runtime guide')
    channels=[{'id':cid,'tvgId':cid,'title':names.get(cid,cid)} for cid in schedules]
    doc={'schema':3,'generatedAt':now.isoformat(),'windowHours':5,'linkedChannels':len(channels),'scheduledChannels':len(schedules),'sources':status,'channels':channels,'programs':schedules}
    os.makedirs(os.path.dirname(OUT),exist_ok=True)
    with open(OUT,'w',encoding='utf-8') as f:json.dump(doc,f,separators=(',',':'),ensure_ascii=False)
    print('Runtime EPG:',len(channels),'channels',os.path.getsize(OUT),'bytes')
if __name__=='__main__':main()
