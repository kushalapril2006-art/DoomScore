"""Render the app's existing vector logo into store-sized PNGs (requires Pillow)."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import math

root=Path(__file__).resolve().parents[1]
out=root/'release/assets'
out.mkdir(parents=True,exist_ok=True)

def curve(p0,p1,p2,p3,steps=40):
    return [tuple((1-t)**3*p0[i]+3*(1-t)**2*t*p1[i]+3*(1-t)*t*t*p2[i]+t**3*p3[i] for i in range(2)) for t in [n/steps for n in range(steps+1)]]

icon=Image.new('RGBA',(2048,2048),'#09090f');draw=ImageDraw.Draw(icon)
scale=2048/48
points=[]
for part in [((24,6),(35,6),(42,15),(41,27)),((41,27),(44,36),(33,43),(23,41)),((23,41),(11,44),(5,35),(7,25)),((7,25),(4,14),(14,5),(24,6))]:points+=curve(*part)
draw.polygon([(x*scale,y*scale) for x,y in points],fill='#c6ff3d')
for x in [19,32]:
    draw.ellipse(tuple(v*scale for v in [x-7,14,x+7,28]),fill='white')
    draw.ellipse(tuple(v*scale for v in [x-3,19,x+3,25]),fill='#09090f')
smile=[]
for start,control,end in [((19,33),(24,38),(29,33)),((29,33),(24,35),(19,33))]:
    for n in range(41):
        t=n/40;smile.append(tuple(((1-t)**2*start[i]+2*(1-t)*t*control[i]+t*t*end[i])*scale for i in range(2)))
draw.polygon(smile,fill='#09090f')
icon.resize((512,512),Image.Resampling.LANCZOS).save(out/'play-icon-512.png')
graphic=Image.new('RGBA',(1024,500),'#09090f')
graphic.alpha_composite(icon.resize((340,340),Image.Resampling.LANCZOS),(12,75))
g=ImageDraw.Draw(graphic)
font_dir=Path('C:/Windows/Fonts')
for text,y,size,color,bold in [('doomscore',82,68,'#c6ff3d',True),('Stack reels. Climb ranks.',175,35,'#f4f4fa',True),('Competitive doomscrolling',244,27,'#b6b6c9',False),('Brainrot badges and flex cards',290,27,'#b6b6c9',False),('No screen recording',336,27,'#3de0ff',False),('One-time Accessibility setup required',427,19,'#b6b6c9',False)]:
    font=ImageFont.truetype(str(font_dir/('segoeuib.ttf' if bold else 'segoeui.ttf')),size)
    g.text((370,y),text,font=font,fill=color)
graphic.save(out/'play-feature-1024x500.png')
print('Exported the app logo and feature graphic at Google Play dimensions')
