# term

Управление терминалом компьютера или монитора.

`cc.term` проксирует стандартное терминальное API `CC: Tweaked`. В Python обычно удобнее использовать `snake_case`, но во многих случаях сохраняется и совместимость с исходными именами `CC`.

## Импорт

```python
import cc
from cc import term
```

## Основные функции

- `write(text)`
- `scroll(lines)`
- `get_cursor_pos()`
- `set_cursor_pos(x, y)`
- `get_cursor_blink()`
- `set_cursor_blink(enabled)`
- `get_size()`
- `clear()`
- `clear_line()`
- `get_text_color()`
- `set_text_color(color)`
- `get_background_color()`
- `set_background_color(color)`
- `is_color()`
- `blit(text, text_colors, background_colors)`
- `set_palette_color(...)`
- `get_palette_color(color)`
- `native_palette_color(color)`

## Примеры

### Очистка и позиционирование курсора

```python
import cc

cc.term.clear()
cc.term.set_cursor_pos(1, 1)
cc.term.write("Привет")
```

### Работа с цветом

```python
import cc
import colors

cc.term.set_background_color(colors.blue)
cc.term.set_text_color(colors.white)
cc.term.clear()
cc.term.set_cursor_pos(1, 1)
cc.term.write("Синий экран")
```

### `blit`

```python
import cc

cc.term.set_cursor_pos(1, 1)
cc.term.blit("ABC", "012", "fff")
```

## Замечания

- Некоторые операции меняют текущие цвета и позицию курсора.
- После перехода runtime на `8x8`-рендер ячейки терминала стали ближе к квадратным, поэтому примитивы и графика выглядят ровнее, чем в классическом `6x9` рендере.
