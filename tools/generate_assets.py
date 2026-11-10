"""Generates all pixel-art sprites (PNG) and retro sound effects (WAV) into ../res."""
import math, os, random, struct, wave
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "res")
os.makedirs(ROOT + "/sprites", exist_ok=True); os.makedirs(ROOT + "/sfx", exist_ok=True)
random.seed(7)

def outline(im, col=(18, 10, 24, 255)):
    w, h = im.size; px = im.load(); out = im.copy(); op = out.load()
    for y in range(h):
        for x in range(w):
            if px[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and px[nx, ny][3] > 0:
                        op[x, y] = col; break
    return out

def sprite(name, fn):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); fn(ImageDraw.Draw(im))
    outline(im).resize((256, 256), Image.NEAREST).save(f"{ROOT}/sprites/{name}.png")

def slime(d):
    d.ellipse((3, 12, 28, 29), fill=(70, 200, 90)); d.ellipse((7, 14, 14, 18), fill=(150, 240, 160))
    for x in (9, 18):
        d.rectangle((x, 19, x + 3, 23), fill=(255, 255, 255)); d.rectangle((x + 2, 21, x + 3, 23), fill=(0, 0, 0))
    d.rectangle((13, 25, 18, 26), fill=(30, 80, 40))
    d.rectangle((9, 7, 22, 11), fill=(250, 210, 60))
    for x in (9, 13, 17, 21): d.rectangle((x, 4, x + 1, 7), fill=(250, 210, 60))
    d.rectangle((15, 8, 16, 9), fill=(220, 40, 60))

def skeleton(d):
    B, S = (235, 230, 210), (185, 175, 155)
    d.rectangle((9, 2, 22, 15), fill=B); d.rectangle((11, 15, 20, 20), fill=B)
    for x in (11, 17):
        d.rectangle((x, 7, x + 3, 11), fill=(20, 10, 25)); d.rectangle((x + 1, 9, x + 2, 10), fill=(255, 60, 60))
    d.rectangle((15, 12, 16, 14), fill=S)
    for x in range(12, 20, 2): d.rectangle((x, 17, x, 19), fill=S)
    d.rectangle((15, 21, 16, 23), fill=B)
    for y in (23, 25, 27, 29): d.rectangle((10, y, 21, y + 1), fill=B)
    d.rectangle((15, 23, 16, 30), fill=S); d.rectangle((5, 23, 7, 30), fill=B); d.rectangle((24, 23, 26, 30), fill=B)

def dragon(d):
    R, D, Y = (205, 55, 55), (140, 30, 45), (250, 200, 70)
    d.polygon([(1, 5), (10, 14), (8, 25), (1, 22)], fill=D); d.polygon([(30, 5), (21, 14), (23, 25), (30, 22)], fill=D)
    d.ellipse((8, 12, 23, 30), fill=R); d.ellipse((12, 18, 19, 29), fill=Y)
    d.rectangle((10, 4, 21, 13), fill=R); d.rectangle((12, 10, 19, 13), fill=(235, 100, 90))
    d.polygon([(10, 4), (8, 1), (12, 4)], fill=Y); d.polygon([(21, 4), (23, 1), (19, 4)], fill=Y)
    for x in (11, 18): d.rectangle((x, 6, x + 2, 7), fill=Y)
    d.rectangle((14, 11, 14, 11), fill=(60, 10, 20)); d.rectangle((17, 11, 17, 11), fill=(60, 10, 20))

def demon(d):
    P, L = (105, 50, 165), (155, 95, 220)
    d.rectangle((2, 18, 6, 28), fill=P); d.rectangle((25, 18, 29, 28), fill=P)
    d.ellipse((6, 8, 25, 30), fill=P); d.ellipse((9, 10, 22, 18), fill=L)
    d.polygon([(7, 11), (3, 2), (12, 8)], fill=(240, 230, 200)); d.polygon([(24, 11), (28, 2), (19, 8)], fill=(240, 230, 200))
    d.ellipse((11, 12, 20, 21), fill=(255, 255, 255)); d.ellipse((13, 14, 18, 19), fill=(230, 40, 60))
    d.rectangle((15, 15, 16, 18), fill=(0, 0, 0)); d.rectangle((9, 24, 22, 27), fill=(20, 5, 30))
    for x in range(10, 22, 3): d.rectangle((x, 24, x + 1, 25), fill=(255, 255, 255))

for n, f in (("slime", slime), ("skeleton", skeleton), ("dragon", dragon), ("demon", demon)): sprite(n, f)

# Slash effect: 4 frames
for i in range(4):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    e = 4 + 24 * min(1, (i + 1) / 2.5); s = 4 + 24 * max(0, (i - 1) / 3)
    d.line((s, s, e, e), fill=(255, 255, 255, 255), width=3 if i < 3 else 1)
    d.line((s + 1, s - 1, e + 1, e - 1), fill=(120, 220, 255, 255), width=1)
    im.resize((256, 256), Image.NEAREST).save(f"{ROOT}/sprites/slash_{i}.png")

# Heart icon
heart = ["01100110", "11111111", "11111111", "11111111", "01111110", "00111100", "00011000", "00000000"]
im = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
for y, row in enumerate(heart):
    for x, c in enumerate(row):
        if c == "1": im.putpixel((x, y), (235, 55, 75, 255))
im.putpixel((1, 1), (255, 190, 200, 255))
outline(im).resize((32, 32), Image.NEAREST).save(f"{ROOT}/sprites/heart.png")

# Dungeon background (240x150 -> x4)
bg = Image.new("RGB", (240, 150)); d = ImageDraw.Draw(bg)
for y in range(150): d.line((0, y, 240, y), fill=(22 + y // 8, 14 + y // 10, 38 + y // 6))
for row, y in enumerate(range(0, 120, 10)):
    for x in range(-12 * (row % 2), 240, 24):
        sh = random.randint(-6, 6)
        d.rectangle((x + 1, y + 1, x + 22, y + 8), fill=(44 + sh, 34 + sh, 66 + sh))
d.rectangle((0, 120, 240, 150), fill=(18, 12, 28)); d.line((0, 120, 240, 120), fill=(70, 55, 100))
for x in range(0, 240, 20): d.line((x, 120, x - 6, 150), fill=(30, 22, 44))
for tx in (28, 212):
    d.rectangle((tx - 1, 44, tx + 1, 56), fill=(90, 60, 40)); d.rectangle((tx - 3, 36, tx + 3, 44), fill=(255, 140, 40))
    d.rectangle((tx - 1, 32, tx + 1, 38), fill=(255, 220, 90))
bg.resize((960, 600), Image.NEAREST).save(f"{ROOT}/sprites/bg.png")

# Sound effects
SR = 22050
def tone(f0, f1, dur, kind="sq", vol=0.5):
    out, ph, n = [], 0.0, int(SR * dur)
    for i in range(n):
        t = i / n; ph += (f0 + (f1 - f0) * t) / SR
        v = (1 if ph % 1 < .5 else -1) if kind == "sq" else math.sin(6.2832 * ph) if kind == "sin" else random.uniform(-1, 1)
        out.append(v * vol * (1 - t) ** 1.5)
    return out
def mix(a, b): return [(a[i] if i < len(a) else 0) + (b[i] if i < len(b) else 0) for i in range(max(len(a), len(b)))]
def cat(*p): return [s for x in p for s in x]
def save(name, s):
    with wave.open(f"{ROOT}/sfx/{name}.wav", "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v)) * 30000)) for v in s))
save("key", tone(900, 700, .03, "sq", .2))
save("error", tone(120, 70, .16, "sq", .5))
save("hit", mix(tone(0, 0, .14, "noise", .6), tone(320, 60, .16, "sin", .7)))
save("kill", cat(tone(300, 300, .08), tone(400, 400, .08), tone(500, 500, .08), mix(tone(0, 0, .35, "noise", .6), tone(200, 40, .4, "sin", .8))))
save("win", cat(*[tone(f, f, .14, "sq", .4) for f in (523, 659, 784, 1046)]))
save("lose", cat(tone(400, 80, .8, "sq", .45)))
print("assets generated")
