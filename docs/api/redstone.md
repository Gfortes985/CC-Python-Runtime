# redstone

Работа с обычным, аналоговым и bundled redstone.

## Импорт

```python
import cc
from cc import redstone
```

## Доступные функции

- `get_sides()`
- `set_output(side, value)`
- `get_output(side)`
- `get_input(side)`
- `set_analog_output(side, strength)`
- `get_analog_output(side)`
- `get_analog_input(side)`
- `set_bundled_output(side, mask)`
- `get_bundled_output(side)`
- `get_bundled_input(side)`
- `test_bundled_input(side, color_mask)`

## Примеры

### Простой сигнал

```python
import cc

cc.redstone.set_output("right", True)
cc.sleep(1)
cc.redstone.set_output("right", False)
```

### Аналоговый сигнал

```python
import cc

cc.redstone.set_analog_output("back", 10)
print(cc.redstone.get_analog_input("left"))
```

### Bundled redstone

```python
import cc
import colors

mask = colors.combine(colors.red, colors.blue)
cc.redstone.set_bundled_output("top", mask)
print(cc.redstone.test_bundled_input("top", colors.red))
```
