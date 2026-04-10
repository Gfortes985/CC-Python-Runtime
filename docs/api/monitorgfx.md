# monitorgfx

Hi-res графика для мониторов `CC: Tweaked`.

Модуль `cc.monitorgfx` добавляет отдельный графический слой поверх обычного мониторного текста. Это не замена `term` и не замена стандартного monitor API, а именно отдельный framebuffer-режим для изображений и пиксельной графики.

Обычный терминальный вывод на монитор продолжает существовать. Hi-res слой можно:

- очистить;
- рисовать по одному пикселю;
- выводить подготовленное изображение;
- полностью отключать и возвращаться к обычному режиму.

## Импорт

```python
import cc
from cc import monitorgfx
```

## Цель рисования

Во всех функциях `target` может быть:

- строкой со стороной, например `"left"`;
- обёрнутым monitor-периферийным объектом, например из `cc.peripheral.find("monitor")`.

Пример:

```python
monitor = cc.peripheral.find("monitor")
cc.monitorgfx.clear(monitor)
```

## Координаты

Все координаты в `cc.monitorgfx`:

- 1-based;
- измеряются в hi-res пикселях, а не в текстовых ячейках.

Текущая реализация использует сетку `64x64` пикселя на один блок монитора.

## Функции

### `size(target)`

Возвращает размер hi-res буфера монитора в пикселях.

```python
w, h = cc.monitorgfx.size("top")
print(w, h)
```

### `clear(target, color=colors.black)`

Полностью очищает hi-res буфер монитора одним цветом.

`color` можно задавать:

- через цвет `CC`, например `colors.red`;
- через числовой RGB/ARGB-цвет.

```python
import cc
import colors

monitor = cc.peripheral.find("monitor")
cc.monitorgfx.clear(monitor, colors.blue)
```

### `disable(target)`

Отключает hi-res слой для монитора.

После этого монитор снова отображается только в обычном текстовом режиме.

```python
cc.monitorgfx.disable("right")
```

### `set_pixel(target, x, y, color=colors.black)`

Устанавливает один hi-res пиксель.

```python
import cc
import colors

monitor = cc.peripheral.find("monitor")
cc.monitorgfx.set_pixel(monitor, 10, 10, colors.red)
```

CamelCase-алиас:

- `setPixel(target, x, y, color=colors.black)`

### `draw(target, image, x=1, y=1, clear=False)`

Рисует объект изображения, созданный через [`cc.image`](./image.md).

- `target`: монитор;
- `image`: объект изображения из `cc.image`;
- `x`, `y`: верхний левый угол;
- `clear=True`: перед рисованием очистить hi-res слой.

```python
import cc

monitor = cc.peripheral.find("monitor")
img = cc.image.open("picture.png")

cc.monitorgfx.draw(monitor, img, 1, 1, clear=True)
```

Совместимые имена:

- `draw_image(target, image, x=1, y=1, clear=False)`
- `drawImage(target, image, x=1, y=1, clear=False)`

## Примеры

### Нарисовать несколько пикселей вручную

```python
import cc
import colors

monitor = cc.peripheral.find("monitor")
cc.monitorgfx.clear(monitor, colors.black)

for i in range(1, 33):
    cc.monitorgfx.set_pixel(monitor, i, i, colors.red)
```

### Вывести локальную картинку

```python
import cc

monitor = cc.peripheral.find("monitor")
img = cc.image.open("logo.png")

w, h = cc.monitorgfx.size(monitor)
img = img.resize(w, h, "nearest").quantize_monitor(True)
cc.monitorgfx.draw(monitor, img, 1, 1, clear=True)
```

### Скачать картинку и показать на мониторе

```python
import cc

monitor = cc.peripheral.find("monitor")
img = cc.image.load_url("https://example.com/picture.jpg")

w, h = cc.monitorgfx.size(monitor)
img = img.resize(w, h, "bilinear").quantize_monitor(True)
cc.monitorgfx.draw(monitor, img, 1, 1, clear=True)
```

### Вернуться к обычному монитору

```python
import cc

monitor = cc.peripheral.find("monitor")
cc.monitorgfx.disable(monitor)
```

## Замечания

- Hi-res графика существует отдельно от обычного текста монитора.
- Если включён hi-res слой, именно он становится видимым поверх обычного терминального содержимого.
- Для лучших результатов изображения почти всегда стоит сначала подгонять под размер монитора и сводить к палитре монитора через `quantize_monitor(...)`.
- `draw(...)` принимает только изображения, созданные модулем `cc.image`.
