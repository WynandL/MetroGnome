import csv,json,sys,math
S=sys.argv[1]
ev=json.load(open(f"{S}/wav/blip.json"))
for tag in sys.argv[2:]:
    rows=list(csv.DictReader(open(f"{S}/res/{tag}_blip.csv")))
    out=[]
    for t0,t1,hz,off in ev:
        fr=[r for r in rows if t0*1000-200<=int(r['t_ms'])<=t1*1000+1500]
        heard=next((int(r['t_ms']) for r in fr if r['pitch_hz'] and abs(1200*math.log2(float(r['pitch_hz'])/hz))<50),None)
        lock=next((int(r['t_ms']) for r in fr if r['locked']=='true'),None)
        out.append((lock-heard) if heard and lock else None)
    print(tag,"lock minus first-heard (ms):",out)
