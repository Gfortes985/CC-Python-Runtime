# fs

Работа с файловой системой CraftOS.

Модуль `cc.fs` сочетает базовые методы `CC: Tweaked` и Python-удобные helper-функции для текста, строк и JSON.

## Импорт

```python
import cc
from cc import fs
```

## Основные helper-функции

### `open(path, mode="r")`

Открывает файл и возвращает файловый объект `CC file handle`.

Поддерживаемые методы хендла:

- `read(count=None)`
- `readline()`
- `write(value)`
- `writeline(value="")`
- `flush()`
- `seek(whence="cur", offset=0)`
- `close()`

Файловый объект поддерживает `with`:

```python
with cc.fs.open("notes.txt", "w") as f:
    f.write("hello")
```

### `join(*parts)`

Собирает путь в стиле CraftOS.

```python
path = cc.fs.join("data", "logs", "latest.txt")
```

### `read_text(path)`

Читает весь файл как строку.

### `write_text(path, data)`

Полностью перезаписывает файл строкой `data`.

### `append_text(path, data)`

Дописывает строку `data` в конец файла.

### `read_lines(path)`

Читает файл и возвращает список строк.

### `write_lines(path, lines)`

Записывает список строк через `\n`.

### `read_json(path)`

Читает JSON-файл и возвращает Python-значение.

### `write_json(path, data, indent=2, sort_keys=False)`

Записывает Python-значение в JSON.

## Базовые методы CC

Модуль также проксирует основные функции `fs` из `CC: Tweaked`:

- `combine(path_a, path_b)`
- `get_name(path)`
- `get_dir(path)`
- `get_size(path)`
- `exists(path)`
- `is_dir(path)`
- `is_read_only(path)`
- `list(path)`
- `make_dir(path)`
- `move(from_path, to_path)`
- `copy(from_path, to_path)`
- `delete(path)`
- `get_free_space(path)`
- `get_capacity(path)`
- `get_drive(path)`
- `attributes(path)`

## Примеры

### Запись текста

```python
import cc

cc.fs.write_text("hello.txt", "Привет, мир")
print(cc.fs.read_text("hello.txt"))
```

### Работа со строками

```python
import cc

cc.fs.write_lines("todo.txt", ["one", "two", "three"])
print(cc.fs.read_lines("todo.txt"))
```

### Работа с JSON

```python
import cc

cc.fs.write_json("config.json", {"debug": True, "volume": 5})
print(cc.fs.read_json("config.json"))
```
