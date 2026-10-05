import csv,json,sys,math,statistics as st
S=sys.argv[1]; tag=sys.argv[2]; name=sys.argv[3]
rows=list(csv.DictReader(open(f"{S}/res/{tag}_{name}.csv")))
ev=json.load(open(f"{S}/wav/{name}.json"))
f=lambda v: float(v) if v else None
def c(a,b): return 1200*math.log2(a/b)
for e in ev:
    t0,t1,hz=e[0],e[1],e[2]
    fr=[r for r in rows if t0*1000+300<=int(r['t_ms'])<=t1*1000]
    lk=[r for r in fr if r['locked']=='true']
    cand=[f(r['candidate_hz']) for r in lk if r['candidate_hz']]
    raw=[f(r['pitch_hz']) for r in fr if r['pitch_hz']]
    mc=st.median(cand) if cand else None; mr=st.median(raw) if raw else None
    print(f"{hz:8.1f} Hz  frames={len(fr):3d} locked={len(lk):3d}  lock={mc and round(mc,1)} ({mc and round(c(mc,hz)):+} c)  raw={mr and round(mr,1)} ({mr and round(c(mr,hz)):+} c)")
