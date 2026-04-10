# peripheral

Поиск, проверка и вызов периферии, подключённой к компьютеру.

Модуль `cc.peripheral` сочетает две вещи:

- совместимость с базовым API `CC: Tweaked`
- Python-friendly helper-функции для поиска и обёрток

Формат страницы вдохновлён официальной документацией `CC: Tweaked` для [`peripheral`](https://tweaked.cc/module/peripheral.html).

## Импорт

```python
import cc
from cc import peripheral
```

## Имена периферии

Для локально подключённых устройств именем обычно служит сторона:

- `"left"`
- `"right"`
- `"top"`
- `"bottom"`
- `"front"`
- `"back"`

Именно это имя используется в вызовах `wrap`, `call`, `get_type` и т.д.

## Helper-функции

### `names(peripheral_type=None)`

Возвращает список имён всех найденных периферий.

Если `peripheral_type` задан, остаются только подходящие устройства.

```python
print(cc.peripheral.names())
print(cc.peripheral.names("monitor"))
```

### `list(peripheral_type=None)`

Возвращает список словарей вида:

```python
{"name": "left", "type": "monitor"}
```

Это удобный Python-заменитель для частого паттерна `getNames + getType`.

### `types()`

Возвращает словарь `name -> type`.

### `wrap(side)`

Возвращает Python-proxy для периферии, если она существует, иначе `None`.

Методы прокси вызываются напрямую:

```python
monitor = cc.peripheral.wrap("top")
if monitor:
    monitor.write("Hello")
```

### `wrap_all(peripheral_type=None)`

Возвращает словарь `name -> proxy`.

### `find(peripheral_type)`

Возвращает первое найденное устройство нужного типа, уже обёрнутое в proxy.

### `find_all(peripheral_type)`

Возвращает список всех найденных устройств нужного типа.

## Базовые методы CC

Также доступны низкоуровневые совместимые вызовы:

- `get_names()`
- `is_present(name)`
- `get_type(name)`
- `has_type(name, peripheral_type)`
- `get_methods(name)`
- `call(name, method, *args)`

Эти вызовы ближе к стилю оригинального Lua API.

## Примеры

### Список периферий

```python
import cc

for item in cc.peripheral.list():
    print(item["name"], "->", item["type"])
```

### Работа с монитором

```python
import cc

monitor = cc.peripheral.find("monitor")
if monitor:
    monitor.clear()
    monitor.set_cursor_pos(1, 1)
    monitor.write("Привет")
```

### Низкоуровневый вызов

```python
import cc

cc.peripheral.call("left", "open", 5)
```

## Ограничения

- Совместимость с addon-периферией строится через нативный Java backend `CC: Tweaked`.
- Обычные методы периферии работают напрямую.
- Асинхронные peripheral methods, которые используют callback/yield-pattern `CC: Tweaked`, пока ещё не поддержаны в native Python backend.
