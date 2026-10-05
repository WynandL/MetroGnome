import csv,sys,math
S,tag,name=sys.argv[1:4]
NAMES=['C','C#','D','D#','E','F','F#','G','G#','A','A#','B']
def note(h):
    if not h: return '-'
    m=round(69+12*math.log2(float(h)/440)); return f"{NAMES[m%12]}{m//12-1}"
rows=list(csv.DictReader(open(f"{S}/res/{tag}_{name}.csv")))
seg=None
for r in rows:
    key=(r['state'], note(r['candidate_hz']) if r['locked']=='true' else '-')
    raw=note(r['pitch_hz'])
    if seg and seg[0]==key: seg[2]=r['t_ms']; seg[3].add(raw)
    else:
        if seg: print(f"{int(seg[1])/1000:6.2f}-{int(seg[2])/1000:6.2f}s  {seg[0][0]:10s} lock={seg[0][1]:4s} heard={','.join(sorted(seg[3]))}")
        seg=[key,r['t_ms'],r['t_ms'],{raw}]
print(f"{int(seg[1])/1000:6.2f}-{int(seg[2])/1000:6.2f}s  {seg[0][0]:10s} lock={seg[0][1]:4s} heard={','.join(sorted(seg[3]))}")
