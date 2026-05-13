from PIL import Image

# Load the demon character image
img = Image.open(r'C:\Users\cyber\Downloads\icon_demon_character.png')

# Crop to square (center crop)
width, height = img.size
size = min(width, height)
left = (width - size) // 2
top = (height - size) // 2
right = left + size
bottom = top + size
img_cropped = img.crop((left, top, right, bottom))

# Android icon sizes
sizes = {
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
}

base_path = r'C:\Users\cyber\AndroidStudioProjects\SoloLeveling\app\src\main\res'

for density, size in sizes.items():
    resized = img_cropped.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(f'{base_path}\\{density}\\ic_launcher.png')
    resized.save(f'{base_path}\\{density}\\ic_launcher_round.png')

print('Icon resized and saved!')
