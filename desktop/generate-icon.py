from pathlib import Path
from PIL import Image, ImageDraw
icons=Path(__file__).resolve().parent/'icons'
scale=16
im=Image.new('RGBA',(64*scale,64*scale))
d=ImageDraw.Draw(im)
def rect(box,radius,fill=None,outline=None,width=1):d.rounded_rectangle(tuple(int(x*scale) for x in box),radius=radius*scale,fill=fill,outline=outline,width=round(width*scale))
def line(points,fill,width=3.2):
 points=[(round(x*scale),round(y*scale)) for x,y in points]
 d.line(points,fill=fill,width=round(width*scale),joint='curve')
 for x,y in points:
  rad=width*scale/2;d.ellipse((x-rad,y-rad,x+rad,y+rad),fill=fill)
rect((0,0,64,64),14,'#2563EB')
rect((14,16,50,42),4,outline='white',width=3.2)
line([(32,43),(32,49)],'white');line([(24,50),(40,50)],'white')
line([(25,34),(39,23)],'#83F5D3');line([(30,23),(39,23),(39,32)],'#83F5D3')
im.save(icons/'seektop-1024.png')
im.resize((256,256),Image.Resampling.LANCZOS).save(icons/'seektop.ico',sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
