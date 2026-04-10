# colors

Константы цветов и битовые операции над цветами.

Модуль доступен как:

- `colors`
- `colours`
- `cc.colors`
- `cc.colours`

## Константы

Доступны стандартные 16 цветов `CC: Tweaked`:

- `white`
- `orange`
- `magenta`
- `lightBlue`
- `yellow`
- `lime`
- `pink`
- `gray`
- `lightGray`
- `cyan`
- `purple`
- `blue`
- `brown`
- `green`
- `red`
- `black`

В модуле `colours` дополнительно есть псевдонимы:

- `grey`
- `lightGrey`

## Функции

### `combine(*values)`

Объединяет несколько цветовых масок.

### `subtract(colors, *values)`

Убирает цвета из маски.

### `test(colors, color)`

Проверяет, содержится ли цвет `color` внутри маски `colors`.

### `pack_rgb(r, g, b)`

Преобразует три значения `0.0..1.0` в `0xRRGGBB`.

### `unpack_rgb(rgb)`

Преобразует `0xRRGGBB` в кортеж `(r, g, b)` со значениями `0.0..1.0`.

### `rgb8(r, g=None, b=None)`

Короткая форма:

- `rgb8(r, g, b)` -> packed RGB
- `rgb8(rgb)` -> unpacked RGB

### `to_blit(color)`

Преобразует цвет в hex-символ для `term.blit`.

### `from_blit(hex_value)`

Преобразует hex-символ обратно в цветовую маску.

## Совместимые имена

Также доступны варианты:

- `packRGB`
- `unpackRGB`
- `toBlit`
- `fromBlit`

## Примеры

```python
import colors

mask = colors.combine(colors.red, colors.blue)
print(mask)
print(colors.test(mask, colors.red))
print(colors.to_blit(colors.red))
print(colors.unpack_rgb(colors.pack_rgb(1, 0.5, 0)))
```
