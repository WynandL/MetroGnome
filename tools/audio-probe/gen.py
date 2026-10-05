import numpy as np, wave, sys, json
R=44100
out=sys.argv[1]
def sil(s): return np.zeros(int(R*s))
def tone(hz,s,amp=0.5,partials=(1.0,),decay=None):
    t=np.arange(int(R*s))/R
    x=sum(a*np.sin(2*np.pi*hz*(k+1)*t) for k,a in enumerate(partials))
    x=x/np.max(np.abs(x))*amp
    if decay: x*=np.exp(-t/decay)
    f=int(R*0.01); env=np.ones_like(x); env[:f]=np.linspace(0,1,f); env[-f:]=np.linspace(1,0,f)
    return x*env
def write(name,x,events):
    x=np.clip(x,-1,1); w=wave.open(f"{out}/{name}.wav","wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(R)
    w.writeframes((x*32767).astype('<i2').tobytes()); w.close()
    json.dump(events,open(f"{out}/{name}.json","w"))
H=(1,.5,.35,.2)
# A01: high tones
parts=[sil(3.0)]; ev=[]; t=3.0
for hz in [1760,2093.0,2637.0,3136.0,3520,3729.3,3951.1,4186.0,4434.9]:
    ev.append([t,t+2.5,hz]); parts+= [tone(hz,2.5),sil(1.2)]; t+=3.7
write("high",np.concatenate(parts),ev)
# A08: lock 440 then alternate silence / 660
parts=[sil(3.0),tone(440,3.0,partials=H)]; ev=[[3.0,6.0,440]]; t=6.0
for i in range(12):
    parts+=[sil(0.3),tone(660,0.3)]; ev.append([t+0.3,t+0.6,660]); t+=0.6
parts.append(sil(3.0)); write("alternate",np.concatenate(parts),ev)
# A02: octave replacement, hard switch
write("octave_replace",np.concatenate([sil(3.0),tone(440,3.0,partials=H),tone(880,6.0,partials=H),sil(2.0)]),[[3,6,440],[6,12,880]])
# A02: overlap, 440 ringing/decaying while 880 starts
a=tone(440,9.0,partials=H,decay=2.5); b=np.concatenate([sil(2.0),tone(880,7.0,partials=H,decay=2.5)])
write("octave_overlap",np.concatenate([sil(3.0),a+b,sil(2.0)]),[[3,12,440],[5,12,880]])
# A09: base note with a 90 ms blip a fifth up at varying offsets, different base each trial
parts=[sil(3.0)]; ev=[]; t=3.0
bases=[440,493.9,523.3,587.3,659.3,698.5,784.0,880.0,987.8]
for i,off in enumerate([0.35,0.45,0.55,0.65,0.75,0.85,0.95,1.05,1.15]):
    b=bases[i]; seg=tone(b,2.5,partials=H)
    n0=int(off*R); n1=n0+int(0.09*R); blip=tone(b*1.5,0.09,partials=H)
    seg[n0:n1]=blip
    parts+=[seg,sil(2.5)]; ev.append([t,t+2.5,b,off]); t+=5.0
write("blip",np.concatenate(parts),ev)
# A02 round 2: sustained strong 2nd harmonic (must NOT release), lock first on a normal A4
write("weak_fund_pure",np.concatenate([sil(3.0),tone(440,3.0,partials=H),tone(440,8.0,partials=(0.1,1.0)),sil(2.0)]),[[3,6,440],[6,14,440]])
write("weak_fund_inst",np.concatenate([sil(3.0),tone(440,3.0,partials=H),tone(440,8.0,partials=(0.2,1.0,0.6,0.3)),sil(2.0)]),[[3,6,440],[6,14,440]])
# brief octave burst inside a held A4
write("burst",np.concatenate([sil(3.0),tone(440,3.0,partials=H),tone(880,0.4,partials=H),tone(440,3.0,partials=H),sil(2.0)]),[[3,6,440],[6,6.4,880],[6.4,9.4,440]])
# replacement at 3x and back to the old note
write("triple_return",np.concatenate([sil(3.0),tone(440,3.0,partials=H),tone(1320,4.0,partials=H),tone(440,3.0,partials=H),sil(2.0)]),[[3,6,440],[6,10,1320],[10,13,440]])
# overlap where the old note dies away: A4 decays fast (tau 0.6 s), A5 sustained from 1.5 s after
a=tone(440,10.0,partials=H,decay=0.6); b=np.concatenate([sil(1.5),tone(880,8.5,partials=H)])
write("overlap_dies",np.concatenate([sil(3.0),a+b,sil(2.0)]),[[3,13,440],[4.5,13,880]])
