import glob, os, sys
from PIL import Image
os.chdir(os.path.dirname(os.path.abspath(__file__)))
fs = sorted(glob.glob('out/*.png'))
if len(sys.argv) > 1:
    fs = [f for f in fs if any(a in f for a in sys.argv[1:])]
ims = [Image.open(f).convert('RGB') for f in fs]
H = 520
row = [im.resize((int(im.width * H / im.height), H)) for im in ims]
per = 6
lines = [row[i:i + per] for i in range(0, len(row), per)]
W = max(sum(r.width for r in l) + 10 * len(l) for l in lines)
sheet = Image.new('RGB', (W, (H + 10) * len(lines)), (60, 60, 60))
for li, l in enumerate(lines):
    x = 0
    for r in l:
        sheet.paste(r, (x, li * (H + 10))); x += r.width + 10
sheet.save('sheet.png')
print([os.path.basename(f) for f in fs], sheet.size)
