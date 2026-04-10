# keys

Константы клавиш и перевод key code -> имя.

## Импорт

```python
import keys
import cc
```

## Константы

Модуль экспортирует имена клавиш как числовые key code.

Примеры:

- `keys.enter`
- `keys.backspace`
- `keys.left`
- `keys.right`
- `keys.up`
- `keys.down`
- `keys.home`
- `keys.end`
- `keys.delete`
- `keys.leftCtrl`
- `keys.rightCtrl`
- `keys.f1` ... `keys.f25`

Также есть alias:

- `keys.return_`

## Функции

### `get_name(code)`

Возвращает строковое имя клавиши или `None`, если код неизвестен.

Также доступно имя `getName`.

## Пример

```python
import cc
import keys

event = cc.os.pull_event("key")
code = event[1]

print(code)
print(keys.get_name(code))

if code == keys.enter:
    print("Нажат Enter")
```
