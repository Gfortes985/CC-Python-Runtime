# midi

Парсинг и воспроизведение MIDI-файлов через периферию `speaker`.

Модуль `cc.midi` читает стандартные MIDI-файлы формата `0` и `1`, понимает tempo events и program changes и умеет воспроизводить их двумя способами:

- `mode="notes"`: через `speaker.play_note(...)`
- `mode="audio"`: через PCM-стрим в `speaker.play_audio(...)`

`notes`-режим ближе к классическому CC-спикеру и дешевле по ресурсам, но упирается в noteblock-инструменты и лимит нот за tick.  
`audio`-режим звучит заметно богаче и лучше подходит для обычных `.mid`, но это всё ещё встроенный простой синтезатор, а не полный General MIDI soundfont.

## Импорт

```python
import cc
from cc import midi
```

## `open(path)`

Открывает и парсит MIDI-файл из файловой системы компьютера.

```python
song = cc.midi.open("song.mid")
print(song.info())
```

## `load(path)`

Алиас для `open(path)`.

## `play(target, song_or_path, tempo_scale=1.0, volume=1.0, transpose=0, mode="notes")`

Проигрывает MIDI на периферии `speaker`.

`target` может быть:

- строкой со стороной, например `"left"`
- объектом speaker из `cc.peripheral.find("speaker")`

`song_or_path` может быть:

- объектом песни из `cc.midi.open(...)`
- строкой с путём к `.mid`

Параметры:

- `tempo_scale`: множитель темпа
- `volume`: общий множитель громкости
- `transpose`: сдвиг нот в полутонах
- `mode`: `"notes"` или `"audio"`

Возвращает словарь со статистикой проигрывания.

### Пример: notes mode

```python
import cc

speaker = cc.peripheral.find("speaker")
result = cc.midi.play(speaker, "song.mid", mode="notes")
print(result)
```

### Пример: audio mode

```python
import cc

speaker = cc.peripheral.find("speaker")
result = cc.midi.play(speaker, "song.mid", mode="audio")
print(result)
```

## Объект песни

### `song.info()`

Возвращает словарь:

```python
{
    "path": "song.mid",
    "format": 1,
    "tracks": 3,
    "division": 480,
    "notes": 128,
    "events": 190,
    "duration": 12.34,
}
```

### `song.play(speaker, tempo_scale=1.0, volume=1.0, transpose=0, mode="notes")`

Метод-обёртка над `cc.midi.play(...)`.

```python
import cc

song = cc.midi.open("song.mid")
speaker = cc.peripheral.find("speaker")
song.play(speaker, mode="audio")
```

## Режимы

### `mode="notes"`

Использует `speaker.play_note(...)`.

Плюсы:

- лёгкий и дешёвый режим
- хорошо подходит для простых мелодий
- ближе к ванильной модели звука `CC: Tweaked`

Минусы:

- ограниченный набор инструментов
- лимит плотности нот в один tick
- сложные MIDI могут звучать урезанно

### `mode="audio"`

Использует `speaker.play_audio(...)` и встроенный PCM-синтезатор.

Плюсы:

- лучше полифония
- меньше пропавших нот
- заметно богаче звучание на обычных MIDI

Минусы:

- это не soundfont и не полноценный GM-синтезатор
- инструменты только приближённо мапятся на разные формы волны
- тяжёлее по CPU, чем `notes`

## Примеры

### Быстрая проверка файла

```python
import cc

song = cc.midi.open("song.mid")
print(song.info())
```

### Сравнение режимов

```python
import cc

speaker = cc.peripheral.find("speaker")
song = cc.midi.open("song.mid")

print("notes mode")
print(song.play(speaker, mode="notes"))

sleep(1)

print("audio mode")
print(song.play(speaker, mode="audio"))
```

### Быстрее и выше

```python
import cc

speaker = cc.peripheral.find("speaker")
cc.midi.play(speaker, "song.mid", tempo_scale=1.25, transpose=2, mode="audio")
```

### Медленнее и тише

```python
import cc

speaker = cc.peripheral.find("speaker")
cc.midi.play(speaker, "song.mid", tempo_scale=0.8, volume=0.6, mode="audio")
```

## Замечания

- Пока поддерживаются только обычные PPQN MIDI-файлы.
- SMPTE time division пока не поддерживается.
- Для первого теста лучше использовать короткие `.mid`.
- Если нужен максимально “музыкальный” результат, сейчас лучше пробовать `mode="audio"`.
