# Справочник API

Эта справка описывает Python API, доступный внутри компьютеров `CC: Tweaked` в моде `CC Python Runtime`.

Структура сделана в стиле модульной справки `CC: Tweaked`: у каждого модуля есть отдельная страница с описанием, списком функций и короткими примерами.

Формат страниц вдохновлён официальной документацией `CC: Tweaked`, например страницей [`peripheral`](https://tweaked.cc/module/peripheral.html).

## Соглашения

- Основное пространство имён: `cc`.
- Большинство модулей также можно импортировать напрямую:
  - `import rednet`
  - `import paintutils`
  - `import textutils`
- Во многих местах доступны оба стиля имён:
  - Python-стиль: `set_cursor_pos`
  - исходный стиль `CC: Tweaked`: `setCursorPos`
- Все примеры ниже используют Python-стиль.

## Базовые встроенные функции

Они доступны без импорта:

- `print(*values, sep=" ", end="\n")`
- `input(prompt="")`
- `open(path, mode="r")`
- `sleep(seconds)`
- `help(topic=None)`
- `exit(code=None)`
- `quit(code=None)`

## Пространство имён `cc`

- [`cc.fs`](./api/fs.md)
- [`cc.os`](./api/os.md)
- [`cc.term`](./api/term.md)
- [`cc.peripheral`](./api/peripheral.md)
- [`cc.redstone`](./api/redstone.md)
- [`cc.rednet`](./api/rednet.md)
- [`cc.colors` / `cc.colours`](./api/colors.md)
- [`cc.keys`](./api/keys.md)
- [`cc.paintutils`](./api/paintutils.md)
- [`cc.textutils`](./api/textutils.md)
- [`cc.vector`](./api/vector.md)
- [`cc.parallel`](./api/parallel.md)
- [`cc.image`](./api/image.md)
- [`cc.monitorgfx`](./api/monitorgfx.md)
- [`cc.midi`](./api/midi.md)
- [`cc.imports`](./api/imports.md)

## Быстрый пример

```python
import cc

cc.term.clear()
cc.term.set_cursor_pos(1, 1)
print("ID:", cc.os.get_computer_id())
print("Периферии:", cc.peripheral.names())
```

## Статус совместимости

Справка ниже описывает текущее состояние реализации в этом репозитории. Это не дословная копия Lua API `CC: Tweaked`, а Python-обёртка и набор Python-модулей поверх той же компьютерной среды.

Особенно важно помнить:

- `parallel` сейчас кооперативный и ориентирован на `async def`/`await`.
- `textutils` совместим по назначению, но не обязан совпадать байт-в-байт с Lua-версией.
- `rednet` реализован как отдельный Python-модуль поверх модемов и событий, а не как вызов Lua `rednet`.
