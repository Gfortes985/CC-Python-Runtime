# paintutils

Утилиты для рисования пикселей, линий, прямоугольников и изображений на терминале.

Формат и назначение модуля вдохновлены официальной страницей [`paintutils`](https://tweaked.cc/module/paintutils.html), но реализация здесь написана для Python runtime.

## Импорт

```python
import paintutils
import cc
```

## Функции

### `parse_image(image)`

Разбирает строку в формате `.nfp`-подобного цветового изображения и возвращает список строк-пикселей.

### `load_image(path)`

Читает изображение из файла и возвращает разобранную структуру, либо `None`, если файл не найден.

### `draw_pixel(x_pos, y_pos, colour=None)`

Рисует один пиксель.

### `draw_line(start_x, start_y, end_x, end_y, colour=None)`

Рисует линию.

### `draw_box(start_x, start_y, end_x, end_y, colour=None)`

Рисует контур прямоугольника.

### `draw_filled_box(start_x, start_y, end_x, end_y, colour=None)`

Рисует закрашенный прямоугольник.

### `draw_image(image, x_pos, y_pos)`

Рисует разобранное изображение.

## Совместимые имена

Доступны также camelCase-версии:

- `parseImage`
- `loadImage`
- `drawPixel`
- `drawLine`
- `drawBox`
- `drawFilledBox`
- `drawImage`

## Примеры

### Простейшая графика

```python
import cc
import paintutils
import colors

cc.term.clear()
paintutils.draw_pixel(10, 5, colors.red)
paintutils.draw_line(2, 2, 20, 10, colors.blue)
paintutils.draw_box(3, 3, 15, 8, colors.yellow)
paintutils.draw_filled_box(25, 4, 35, 9, colors.green)
```

### Круг

```python
import math
import cc
import paintutils
import colors

cc.term.clear()
w, h = cc.term.get_size()
cx = w // 2
cy = h // 2
r = min(w, h) // 4

for deg in range(360):
    rad = math.radians(deg)
    x = round(cx + math.cos(rad) * r)
    y = round(cy + math.sin(rad) * r)
    paintutils.draw_pixel(x, y, colors.red)
```

## Замечания

- Рисование может менять фон терминала и позицию курсора.
- После перехода на `8x8` терминальный рендер пиксельная графика выглядит ближе к квадратной сетке, чем в классическом рендере `CC: Tweaked`.
