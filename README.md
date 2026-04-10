# CC Python Runtime

`CC Python Runtime` — это NeoForge-аддон для `Minecraft 1.21.1` + `CC: Tweaked`, который добавляет Python как второй язык внутри компьютеров и turtles CraftOS.

Мод сохраняет Lua без изменений и использует серверный runtime на базе GraalPy для выполнения Python. Отрисовка терминала и ввод игрока по-прежнему идут через родной стек терминала `CC: Tweaked`, а состояние Python runtime и traceback-ошибки дополнительно синхронизируются через NeoForge payloads для видимости на клиенте.

## Кратко об устройстве

- Lua остаётся языком по умолчанию, а shell не меняется.
- Python выполняется только на сервере.
- Каждый компьютер владеет переиспользуемым Python runtime, поэтому повторные запуски заметно быстрее.
- Основные API постепенно переводятся на нативные Java/Python backend-реализации вместо Lua transport layer.
- Мультиплеер работает корректно, потому что клиенты не исполняют Python и продолжают использовать обычные terminal packets `CC: Tweaked`.

## Текущий объём реализации

- launcher `python file.py`
- алиас `py`
- интерактивный REPL
- Python import loader поверх CraftOS filesystem
- best-effort лимиты по инструкциям, watchdog и soft-memory protection
- sync payloads для runtime state и traceback
- `8x8` terminal rendering с практической поддержкой кириллицы
- Python-friendly модули `CC`, например `cc.rednet`, `paintutils`, `textutils`, `vector` и `parallel`

## Важное ограничение

Этот репозиторий уже даёт серьёзную серверную основу и рабочий vertical slice, но это всё ещё не drop-in replacement для полноценного CPython. Главное ограничение сейчас — sandboxing: встраиваемый GraalPy на Java является правильной базой, но жёсткая изоляция heap-памяти всё ещё best-effort и откатывается к soft-accounting, если более строгие sandbox options Graal недоступны во время выполнения.

Полное описание архитектуры смотри в `docs/ARCHITECTURE.md`.

Текущий гайд по написанию кода на Python смотри в `docs/PYTHON_CODING.md`.
