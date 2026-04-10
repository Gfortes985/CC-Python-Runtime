# Руководство по написанию кода на Python

Этот документ описывает ту Python-поверхность, которая уже реализована в `CC Python Runtime`.

Это практический гайд для написания программ внутри компьютеров и turtles `CC: Tweaked` на Python.

## Состояние проекта

Текущие цели реализации:

- Python — это второй язык, а не замена Lua.
- Python выполняется только на сервере.
- Клиент остаётся только терминальным UI.
- Повторные запуски на одном и том же компьютере быстрые, потому что runtime переиспользуется.
- Терминал использует ячейки `8x8`, поэтому графика и кириллица выглядят гораздо лучше, чем при старой модели `6x9`.

## Запуск программ

Доступные shell-команды:

- `python file.py [args...]`
- `py file.py [args...]`
- `python`
- `python -i`

Замечания:

- `python` и `python -i` открывают интерактивный REPL.
- `py` — это просто алиас для `python`.
- Для скриптов заполняется `sys.argv`.
- Файлы `.py` живут в обычной файловой системе CraftOS.

## Модель выполнения

Сейчас Python-программы ведут себя так:

- выполнение происходит на сервере
- у каждого CC-компьютера есть собственный Python runtime
- runtime является persistent для компьютера, поэтому второй и последующие запуски заметно быстрее
- отключение игрока не останавливает программу, пока компьютер остаётся загруженным
- выгрузка чанка или отключение питания компьютера всё ещё останавливают программу, как и в обычном `CC: Tweaked`

Важное следствие:

- это не "пауза и продолжение с точного состояния интерпретатора после выгрузки чанка"
- когда компьютер выгружается, он фактически выключается

## Терминал и текст

Сейчас мод включает:

- `8x8` отрисовку терминала
- поддержку кириллицы в shell, editor, terminal output и Python input/output
- почти квадратные terminal cells, из-за чего рисование выглядит заметно правильнее

Важное ограничение:

- терминальный текст всё ещё работает через однобайтную terminal page, а не через полный unrestricted Unicode
- текущая цель — практичная совместимость терминального текста, а не desktop-style рендер любого шрифта

## Модель импортов

Python imports работают поверх CraftOS filesystem.

Поддерживается:

- обычные модульные файлы вроде `lib.py`
- package-папки с `__init__.py`
- относительные импорты
- namespace packages

Корни поиска включают:

- директорию скрипта
- текущую рабочую директорию
- `lib`
- `site-packages`
- `packages`

Полезный helper:

- `cc.imports.paths()`
- `cc.imports.loaded_modules(prefix=None)`
- `cc.imports.invalidate_caches()`

## Стили кода

Все эти стили поддерживаются:

```python
import cc
print(cc.os.get_computer_id())
```

```python
from cc import os, term, peripheral
print(os.get_computer_id())
```

```python
import colors
import rednet
import paintutils
```

Есть и convenience globals:

- `print`
- `input`
- `open`
- `sleep`
- `help`
- `exit`
- `quit`

## Реализованные API

### Основные CC-style модули

Сейчас доступны:

- `cc.os`
- `cc.term`
- `cc.fs`
- `cc.peripheral`
- `cc.redstone`
- `cc.rednet`

На практике:

- `os`, `term`, `fs`, `peripheral` и `redstone` идут через Java backend
- `rednet` реализован на Python поверх native event/peripheral access

### Utility-модули

Сейчас доступны:

- `colors`
- `colours`
- `keys`
- `paintutils`
- `textutils`
- `vector`
- `parallel`

И также через namespace:

- `cc.colors`
- `cc.colours`
- `cc.keys`
- `cc.paintutils`
- `cc.textutils`
- `cc.vector`
- `cc.parallel`

### Turtle

`turtle` уже экспортируется, но пока не документируется как полностью native-complete на том же уровне, что и core modules выше.

Если ты сильно завязан на turtle-сценарии, лучше считать его доступным, но всё ещё частью compatibility layer, пока эта часть не будет окончательно доведена.

## REPL

REPL сейчас поддерживает:

- `help()`
- `help(cc)`
- `help(cc.fs)`
- `help(cc.peripheral)`
- `help(cc.rednet)`
- `help(cc.paintutils)`
- `help(cc.parallel)`
- `help(cc.vector)`
- `help(cc.textutils)`
- `exit()`
- `quit()`

## Параллельное выполнение

`parallel` реализован как cooperative scheduler для Python-задач.

Важное отличие от Lua:

- используй `async def` задачи или generator-style cooperative tasks
- не стоит ожидать, что обычные блокирующие Python-функции будут автоматически переключаться так же, как Lua coroutines

Рабочий паттерн:

```python
import cc
import parallel
import keys

async def tick():
    while True:
        await cc.parallel.sleep(1)
        print("tick")

async def wait_q():
    while True:
        event = await cc.parallel.pull_event("key")
        if event[1] == keys.q:
            print("q pressed")
            return

parallel.wait_for_any(tick, wait_q)
```

Сейчас доступны:

- `parallel.wait_for_any(...)`
- `parallel.wait_for_all(...)`
- `cc.parallel.sleep(...)`
- `cc.parallel.pull_event(...)`
- `cc.parallel.pull_event_raw(...)`
- `cc.parallel.receive(...)`

## Примеры

### Hello World

```python
print("Hello from Python")
print("Привет из Python")
```

### Файлы

```python
import cc

cc.fs.write_text("demo.txt", "hello")
print(cc.fs.read_text("demo.txt"))
```

### Поиск периферии

```python
import cc

for name in cc.peripheral.get_names():
    print(name, cc.peripheral.get_type(name))
```

### Rednet

```python
import cc

cc.rednet.open("left")
cc.rednet.send(5, "hello from python")
```

### Круг через Paintutils

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

### Векторы

```python
import vector

a = vector.new(1, 2, 3)
b = vector.new(4, 5, 6)

print(a + b)
print(a.dot(b))
print(a.cross(b))
```

### Textutils

```python
import textutils

print(textutils.serialise({"ok": True, "nums": [1, 2, 3]}))
print(textutils.serialise_json({"name": "Привет", "ok": True}))
```

## Текущие ограничения

Вот что важно учитывать честно при разработке под этот runtime:

- Это не полный CPython.
- Нет `pip`, native extensions, socket-ов, subprocess-ов и прямого доступа к JVM.
- Изоляция sandbox-ресурсов всё ещё best-effort для части лимитов.
- `parallel` сейчас рассчитан на cooperative Python tasks, а не на произвольные блокирующие функции.
- `textutils` уже пригоден к использованию, но не гарантирует побайтную совместимость с Lua во всех edge-case сценариях.
- Async peripheral methods пока ещё не полностью поддержаны в native backend.
- Часть Lua ROM APIs `CC: Tweaked` всё ещё отсутствует или не доведена как native Python modules.

Примеры API, которые всё ещё правильнее считать неполными или будущей работой:

- `gps`
- `settings`
- `window`
- `disk`
- `http`
- `io`
- `command`

## Рекомендуемый стиль для нового кода

Если ты пишешь новые Python-программы под этот runtime прямо сейчас, лучше придерживаться такого подхода:

- использовать `import cc`
- использовать `cc.*` для CC APIs
- использовать `colors`, `keys`, `paintutils`, `textutils`, `vector`, `parallel` напрямую там, где это делает код понятнее
- использовать `async def` для любого кода, который должен работать под `parallel`
- хранить состояние явно и сохранять его в файлы, если нужна устойчивость к рестартам

## Итог

На текущем этапе runtime уже достаточно силён для:

- терминальных приложений
- файловых утилит
- event-driven программ
- redstone-логики
- automation через peripheral
- сетевого кода через rednet
- рисования через `paintutils`
- cooperative multitasking через `parallel`

Самые большие оставшиеся пробелы сейчас — это полная API parity, async peripheral compatibility и оставшиеся ROM modules, которые ещё не были перенесены или доведены.
