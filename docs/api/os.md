# os

События, таймеры и метаданные текущего компьютера.

`cc.os` — это Python-обёртка над компьютерным API `CC: Tweaked`. Самые часто используемые операции ожидания уже имеют Python-удобные имена.

## Импорт

```python
import cc
from cc import os
```

## Часто используемые функции

### `sleep(seconds)`

Приостанавливает выполнение на указанное количество секунд.

```python
cc.os.sleep(1.5)
```

### `pull_event(filter_name=None)`

Ждёт событие и возвращает его как кортеж.

```python
event = cc.os.pull_event()
print(event)
```

### `pull_event_raw(filter_name=None)`

То же самое, но без автоматической обработки `terminate`.

## Базовые методы CC

Также доступны основные методы `os` из `CC: Tweaked`:

- `queue_event(name, *args)`
- `start_timer(seconds)`
- `cancel_timer(timer_id)`
- `shutdown()`
- `reboot()`
- `get_computer_id()`
- `get_computer_label()`
- `set_computer_label(label_or_none)`
- `clock()`
- `time(locale=None)`
- `day(locale=None)`
- `epoch(locale=None)`
- `date(locale=None, epoch=None)`

## Примеры

### Ожидание клавиши

```python
import cc

print("Нажми любую клавишу")
print(cc.os.pull_event("key"))
```

### Таймер

```python
import cc

timer = cc.os.start_timer(2)
while True:
    event = cc.os.pull_event()
    if event[0] == "timer" and event[1] == timer:
        print("Готово")
        break
```
