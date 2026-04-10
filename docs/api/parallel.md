# parallel

Кооперативный запуск нескольких задач в одном Python-процессе.

`cc.parallel` не копирует буквально Lua-корутины `CC: Tweaked`, а реализует Python-native cooperative scheduler.

Главное правило:

- для `parallel` нужно использовать `async def` и `await`
- либо generator-based задачи

Обычные синхронные функции, которые сами блокируются на `input()` или `sleep()`, не смогут прозрачно переключаться между собой автоматически.

## Импорт

```python
import cc
import parallel
```

## Awaitable helper-объекты

### `cc.parallel.sleep(seconds)`

Ожидаемый объект для паузы внутри `async`-задачи.

### `cc.parallel.pull_event(filter_name=None)`

Ожидание события.

### `cc.parallel.pull_event_raw(filter_name=None)`

Ожидание raw-события.

### `cc.parallel.receive(timeout=None, protocol=None)`

Ожидание rednet-сообщения.

## Запуск задач

### `wait_for_any(*functions)`

Запускает несколько задач и возвращает индекс первой завершившейся.

### `wait_for_all(*functions)`

Ждёт завершения всех задач.

## Совместимые имена

- `waitForAny`
- `waitForAll`

## Примеры

### `wait_for_any`

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

print(parallel.wait_for_any(tick, wait_q))
```

### `wait_for_all`

```python
import cc
import parallel

async def a():
    await cc.parallel.sleep(1)
    print("A done")

async def b():
    await cc.parallel.sleep(2)
    print("B done")

print(parallel.wait_for_all(a, b))
```

## Ограничения

- Планировщик кооперативный, а не потоковый.
- Чтобы задача уступала управление, она должна дойти до `await`.
- Этот модуль уже полезен для `sleep`, событий и `rednet`, но это всё ещё Python-специфичная реализация, а не буквальная копия Lua `parallel`.
