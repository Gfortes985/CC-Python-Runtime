# image

Загрузка, подготовка и преобразование изображений для hi-res графики на мониторах.

Модуль `cc.image` не связан с обычным текстовым терминалом. Он работает с отдельными объектами изображений, которые можно:

- открыть из файловой системы CraftOS;
- скачать по `http` или `https`;
- уменьшить или увеличить;
- подготовить под палитру монитора;
- передать в [`cc.monitorgfx`](./monitorgfx.md).

## Импорт

```python
import cc
from cc import image
```

## Функции модуля

### `open(path)`

Открывает изображение из файловой системы компьютера и возвращает объект изображения.

```python
img = cc.image.open("picture.png")
```

Поддерживаются форматы, которые умеет читать `ImageIO` на стороне Java.

### `load_url(url, headers=None, timeout=10)`

Скачивает изображение по сети и возвращает объект изображения.

- `url`: строка с `http` или `https` URL;
- `headers`: словарь HTTP-заголовков;
- `timeout`: таймаут в секундах.

```python
img = cc.image.load_url(
    "https://example.com/cat.png",
    headers={"User-Agent": "My Program"},
    timeout=10,
)
```

CamelCase-алиас:

- `loadUrl(url, headers=None, timeout=10)`

## Объект изображения

Функции `open(...)` и `load_url(...)` возвращают объект изображения. Это server-side handle, который хранит исходные пиксели до тех пор, пока вы его используете.

### `info()`

Возвращает словарь с метаданными изображения.

Сейчас гарантированно доступны:

- `width`
- `height`

```python
img = cc.image.open("picture.png")
print(img.info())
```

### `width`

Ширина изображения в пикселях.

```python
print(img.width)
```

### `height`

Высота изображения в пикселях.

```python
print(img.height)
```

### `size()`

Возвращает пару `(width, height)`.

```python
w, h = img.size()
```

### `resize(width, height, resample="bilinear")`

Создаёт новое изображение с указанным размером и возвращает новый объект изображения.

Поддерживаемые режимы `resample`:

- `"nearest"`
- `"nearest_neighbor"`
- `"pixel"`
- `"bilinear"`
- `"bicubic"`

```python
small = img.resize(128, 128, "nearest")
smooth = img.resize(320, 180, "bilinear")
```

Исходное изображение при этом не меняется.

### `quantize_monitor(dither=True)`

Создаёт новую копию изображения, сведённую к 16-цветной палитре монитора `CC: Tweaked`.

- `dither=True` включает дизеринг;
- `dither=False` делает прямое приближение к ближайшему цвету.

```python
ready = img.quantize_monitor(True)
```

### `close()`

Освобождает server-side handle изображения.

```python
img.close()
```

Обычно это не обязательно: при завершении Python-процесса handles автоматически очищаются. Но если программа долго живёт и создаёт много временных изображений, `close()` полезен.

## Примеры

### Открыть локальный файл и подогнать под монитор

```python
import cc

monitor = cc.peripheral.find("monitor")
img = cc.image.open("wallpaper.png")

w, h = cc.monitorgfx.size(monitor)
img = img.resize(w, h, "bilinear").quantize_monitor(True)
cc.monitorgfx.draw(monitor, img, 1, 1, clear=True)
```

### Скачать картинку по URL

```python
import cc

monitor = cc.peripheral.find("monitor")
img = cc.image.load_url("https://example.com/picture.jpg")

w, h = cc.monitorgfx.size(monitor)
img = img.resize(w, h, "bilinear").quantize_monitor(True)
cc.monitorgfx.draw(monitor, img, 1, 1, clear=True)
```

### Ручное управление временными изображениями

```python
import cc

img = cc.image.open("photo.png")
thumb = img.resize(64, 64, "nearest")

print("source:", img.size())
print("thumb:", thumb.size())

thumb.close()
img.close()
```

## Замечания

- `load_url(...)` принимает только `http` и `https`.
- Текущий лимит загрузки по сети составляет 16 МиБ на изображение.
- Текущий лимит по размеру изображения — 4_194_304 пикселя.
- Для вывода на монитор лучше почти всегда делать `resize(...).quantize_monitor(...)` перед `cc.monitorgfx.draw(...)`.
