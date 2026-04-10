# textutils

Форматирование текста, сериализация и маленькие утилиты для удобной работы в REPL и скриптах.

Модуль совместим с назначением `textutils` из `CC: Tweaked`, но реализован как отдельный Python-модуль.

## Импорт

```python
import textutils
import cc
```

## Специальные значения

- `textutils.json_null`
- `textutils.empty_json_array`

Они помогают при сериализации JSON в случаях, где нужно сохранить различие между `null`, пустым массивом и обычным `None`/списком.

## Функции вывода

### `slow_write(text, rate=None)`

Пишет текст посимвольно.

### `slow_print(text="", rate=None)`

То же самое, но добавляет перевод строки.

### `format_time(value, twenty_four_hour=False)`

Форматирует время в строку.

### `paged_print(text="", free_lines=0)`

Печатает длинный текст постранично, останавливаясь на экране терминала.

## Сериализация

### `serialise(value, opts=None)`

Сериализует Python-значение в Lua-подобную строку.

### `unserialise(text)`

Читает строку, созданную `serialise`, и возвращает Python-значение.

### `serialise_json(value, options=None)`

Сериализация в JSON.

### `unserialise_json(text, options=None)`

Чтение JSON обратно в Python-значение.

## Прочее

### `url_encode(text)`

URL-encoding строки в UTF-8.

### `complete(search_text, search_table=None)`

Возвращает список автодополнений по объекту или текущему scope.

## Совместимые имена

Доступны также Lua-style и legacy-alias варианты:

- `slowWrite`, `slowPrint`
- `formatTime`, `pagedPrint`
- `serialize`, `unserialize`
- `serialiseJSON`, `serializeJSON`
- `unserialiseJSON`, `unserializeJSON`
- `urlEncode`

## Примеры

### Сериализация

```python
import textutils

value = {"text": "ok", "nums": [1, 2, 3]}
encoded = textutils.serialise(value)
print(encoded)
print(textutils.unserialise(encoded))
```

### JSON

```python
import textutils

print(textutils.serialise_json({"ok": True, "name": "Привет"}))
```

### Автодополнение

```python
import textutils

print(textutils.complete("cc.pe"))
```

## Замечания

- `url_encode` выводит стандартный percent-encoding. Для Unicode это нормально выглядит как `%D0%...`.
- `complete("cc.pe")` возвращает суффиксы для вставки, а не полное имя целиком.
- Модуль совместим по смыслу, но не обязан совпадать с Lua `textutils` байт-в-байт.
