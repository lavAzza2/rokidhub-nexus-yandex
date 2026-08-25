from pathlib import Path

from PIL import Image, ImageDraw


output = Path(__file__).resolve().parents[1] / "store" / "rokidhub-yandex-icon.png"
output.parent.mkdir(parents=True, exist_ok=True)

image = Image.new("RGB", (512, 512), "#030C06")
draw = ImageDraw.Draw(image)
bolt = [
    (286, 78),
    (142, 270),
    (242, 270),
    (216, 434),
    (378, 218),
    (276, 218),
]
draw.polygon(bolt, fill="#03EF07")
image.save(output, format="PNG", optimize=True)
print(output)

