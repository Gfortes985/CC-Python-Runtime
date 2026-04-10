# rednet

Сетевое взаимодействие между компьютерами через модемы.

В этом runtime `rednet` реализован как отдельный Python-модуль поверх модемов, событий и native peripheral backend. Он не вызывает Lua `rednet` как транспортный слой.

## Импорт

```python
import cc
from cc import rednet
```

## Основные функции

### `open(modem)`

Открывает модем на указанной стороне.

```python
cc.rednet.open("left")
```

### `close(modem=None)`

Закрывает один модем или все открытые модемы.

### `is_open(modem=None)`

Проверяет, открыт ли конкретный модем, либо есть ли вообще открытые rednet-модемы.

### `send(recipient_id, message, protocol=None)`

Отправляет сообщение на конкретный компьютер.

Возвращает `True`, если сообщение было отправлено через хотя бы один открытый модем.

### `broadcast(message, protocol=None)`

Широковещательная отправка.

### `receive(timeout=None, protocol=None)`

Ждёт `rednet_message`.

Возвращает:

- `(sender_id, message, protocol)`
- или `None`, если сработал таймаут

### `host(protocol, hostname)`

Регистрирует локальное имя в простом DNS-слое rednet.

### `unhost(protocol)`

Снимает регистрацию имени.

### `lookup(protocol, hostname=None)`

Ищет компьютеры, объявившие данный `protocol`.

Возвращает:

- `id`, если указан конкретный `hostname`
- кортеж `ids`, если имя не указано
- `None`, если ничего не найдено

### JSON-обёртки

- `send_json(recipient_id, payload, protocol=None)`
- `broadcast_json(payload, protocol=None)`
- `receive_json(timeout=None, protocol=None)`

## Примеры

### Отправка

```python
import cc

cc.rednet.open("left")
cc.rednet.send(5, "hello from python")
```

### Приём

```python
import cc

cc.rednet.open("left")
print(cc.rednet.receive())
```

### DNS host/lookup

```python
import cc

cc.rednet.open("left")
cc.rednet.host("chat", "node1")
print(cc.rednet.lookup("chat"))
print(cc.rednet.lookup("chat", "node1"))
```

## Замечания

- Для `rednet` нужен модем.
- Если модем не открыт, `send` вернёт `False`, а `lookup` быстро завершится без удалённых результатов.
- Вызовы периферий модема понимают Python-стиль имён, например `is_open`, даже если реальный метод в `CC: Tweaked` называется `isOpen`.
