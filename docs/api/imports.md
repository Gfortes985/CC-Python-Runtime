# imports

Небольшой служебный модуль для диагностики импортов.

Он доступен как `cc.imports`.

## Импорт

```python
import cc

print(cc.imports.paths())
```

## Функции

### `paths()`

Возвращает текущий `sys.path` как список строк.

### `loaded_modules(prefix=None)`

Возвращает отсортированный список загруженных модулей.

Если `prefix` задан, остаются только модули с этим префиксом.

### `invalidate_caches()`

Сбрасывает кэш import machinery.

## Примеры

```python
import cc

print(cc.imports.paths())
print(cc.imports.loaded_modules("cc"))
cc.imports.invalidate_caches()
```
