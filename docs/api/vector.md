# vector

Небольшой модуль для работы с трёхмерными векторами.

## Импорт

```python
import vector
import cc
```

## Создание

### `vector.new(x=0, y=0, z=0)`

Создаёт новый вектор.

```python
v = vector.new(1, 2, 3)
```

## Операции

Векторы поддерживают:

- `+`
- `-`
- `*`
- `/`
- `==`
- унарный `-`

## Методы вектора

- `add(other)`
- `sub(other)`
- `mul(other)`
- `div(other)`
- `dot(other)`
- `cross(other)`
- `length()`
- `normalize()`
- `round(tolerance=1.0)`
- `tostring()`

## Примеры

```python
import vector

a = vector.new(1, 2, 3)
b = vector.new(4, 5, 6)

print(a + b)
print(a.dot(b))
print(a.cross(b))
print(a.length())
print(a.normalize())
```

## Замечания

- Умножение и деление поддерживают как scalar, так и vector-подобные значения.
- `round` удобно использовать для аккуратного вывода и сравнения координат после вычислений с плавающей точкой.
