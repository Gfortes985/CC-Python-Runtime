# Архитектура

## 1. Выбор runtime

Проект использует `GraalPy`, встроенный через `org.graalvm.python:python-embedding`.

Почему выбран именно он:

- `Jython` фактически застрял на Python 2 и не подходит под целевую языковую модель.
- `JNI + CPython` нарушил бы требование не использовать внешний runtime и заметно усложнил бы sandboxing и multiplayer-hosting.
- `GraalPy` держит выполнение внутри JVM, работает с Java 21, умеет запрещать host class lookup, IO, создание процессов и потоков, и хорошо ложится на server-only архитектуру.

## 2. Модель выполнения

Стек выполнения намеренно разделён:

1. `python.lua` — это обычная программа CraftOS.
2. Она просит Java API `ccpython.start(...)` создать серверный Python process.
3. `PythonProcess` запускает GraalPy context на выделенном executor thread.
4. Когда Python-коду нужен API `CC`, runtime выпускает host-call action.
5. Нативный backend или bridge-слой выполняет соответствующий вызов.
6. Затем Java process продолжает выполнение с полученными значениями.

Это позволяет не блокировать основной серверный поток Minecraft и при этом сохранять семантику CraftOS и корректное поведение в мультиплеере.

## 3. Модель событий и coroutine

Python не выполняется на клиенте и не разговаривает с Minecraft packets напрямую ради вывода терминала. Вместо этого:

- Python использует `os.pull_event()`, `sleep()`, `rednet.receive()` и другие ожидающие операции через серверный runtime.
- Эти операции ожидают события в cooperative-модели, совместимой с поведением CraftOS.
- Java runtime блокируется на управляемом ожидании, пока не придёт нужное событие или ответ backend-а.
- Это сохраняет работу `Ctrl+T`, lifecycle semantics компьютера и ожидаемое поведение программ внутри `CC: Tweaked`.

## 4. Сеть

Синхронизация терминала:

- переиспользуется напрямую из `CC: Tweaked`
- это и есть правильное multiplayer-поведение, потому что терминал уже имеет надёжную server/client sync-модель

Собственные Python payloads:

- `PythonRuntimeStatePayload`
- `PythonRuntimeErrorPayload`

Эти payloads дают metadata, traceback-и и запас для будущих клиентских overlay-слоёв, не перенося выполнение Python на клиент.

## 5. Sandboxing

Sandbox состоит из нескольких слоёв:

- никакого client-side execution
- никакого прямого доступа к JVM
- никакого прямого host class lookup
- никакого host IO
- никакого host process creation
- никакого host thread creation
- curated import allowlist
- блокировка опасных модулей (`socket`, `ssl`, `subprocess`, `threading`, `polyglot` и т.д.)
- best-effort лимит по Graal statements
- watchdog timeout для CPU-bound циклов
- soft payload/source budget для memory accounting

Важная оговорка:

- жёсткая heap isolation зависит от наличия более строгих Graal sandbox options во время выполнения
- если они недоступны, мод пишет warning в лог и продолжает работу с soft-accounting + watchdog protection

## 6. Компоненты проекта

- `PythonRuntimeManager`: per-server реестр computer contexts
- `PythonComputerContext`: владение процессами на уровне одного компьютера
- `PythonExecutionService`: executor pool + watchdog
- `PythonEventLoop`: удерживает GraalPy worker, пока не придёт ответ
- `PythonAPIBindings`: устанавливает host bridge и Python bootstrap
- `SandboxManager`: строит GraalPy context с ограниченным доступом
- `FileSystemAdapter`: нормализует CraftOS paths и search paths
- `CoroutineAdapter`: переводит runtime wakeups в cooperative waits `CC`
- `NetworkSyncManager`: отправляет runtime state/errors клиентам
- `ClientTerminalSync`: отражает разделение между родной terminal sync `CC` и Python metadata sync
- `PacketHandler`: регистрирует NeoForge payload codecs/handlers

## 7. Что ещё предстоит

В репозитории намеренно оставлено место для следующих итераций:

- более богатая совместимость файловой системы для бинарных handles
- постоянная per-computer история REPL
- полноценная интеграция `startup.py` / autorun
- клиентские overlays для runtime state внутри terminal GUI
- более глубокое тестовое покрытие для dedicated server и multishell-сценариев
