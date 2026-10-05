import csv,json,sys,math
S,tag=sys.argv[1:3]
rows=list(csv.DictReader(open(f"{S}/res/{tag}_blip.csv")))
ev=json.load(open(f"{S}/wav/blip.json"))
for t0,t1,hz,off in ev:
    fr=[r for r in rows if t0*1000<=int(r['t_ms'])<=t1*1000+1500]
    first=next((r for r in fr if r['locked']=='true'),None)
    if not first: print(f"base {hz:6.1f} blip@{off}s: never locked"); continue
    c=1200*math.log2(float(first['candidate_hz'])/hz)
    locks=sorted({round(1200*math.log2(float(r['candidate_hz'])/hz)/100) for r in fr if r['locked']=='true'})
    print(f"base {hz:6.1f} blip@{off:.2f}s: first lock {float(first['candidate_hz']):7.1f} ({c:+.0f} c) at {(int(first['t_ms'])/1000-t0):.2f}s; semitones locked {locks}")
