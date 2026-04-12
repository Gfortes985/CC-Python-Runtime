import builtins as _builtins
import ast as _ast
import codeop as _codeop
import importlib.abc as _abc
import importlib.machinery as _machinery
import json as _json
import math as _math
import random as _random
import re as _re
import sys as _sys
import traceback as _traceback
import types as _types

_HOST = __ccpython_host
del __ccpython_host

_ALLOWED_STDLIB = {
    "codeop",
    "codecs",
    "collections",
    "dataclasses",
    "encodings",
    "enum",
    "functools",
    "heapq",
    "itertools",
    "json",
    "math",
    "operator",
    "random",
    "re",
    "statistics",
    "string",
    "sys",
    "traceback",
    "types",
    "typing",
}

_BLOCKED = {
    "asyncio",
    "ctypes",
    "_ctypes",
    "http",
    "importlib",
    "multiprocessing",
    "pathlib",
    "polyglot",
    "selectors",
    "socket",
    "ssl",
    "subprocess",
    "threading",
    "urllib",
}

_INTERNAL_TRACE_FUNCTIONS = {
    "_unwrap",
    "_call",
    "_invoke",
    "_run_script",
    "_run_repl",
    "__ccpython_run",
    "exec_module",
}
_MAX_IMPORT_NAME_LENGTH = 200
_MAX_IMPORT_SEARCH_PATHS = 32
_TERMINAL_EVENT_DECODERS = {
    "char": 1,
    "paste": 1,
}
_COLOR_VALUES = {
    "white": 0x1,
    "orange": 0x2,
    "magenta": 0x4,
    "lightBlue": 0x8,
    "yellow": 0x10,
    "lime": 0x20,
    "pink": 0x40,
    "gray": 0x80,
    "lightGray": 0x100,
    "cyan": 0x200,
    "purple": 0x400,
    "blue": 0x800,
    "brown": 0x1000,
    "green": 0x2000,
    "red": 0x4000,
    "black": 0x8000,
}
_COLOR_HEX_LOOKUP = {value: format(index, "x") for index, value in enumerate(_COLOR_VALUES.values())}
_KEY_NAMES = {
    32: "space",
    39: "apostrophe",
    44: "comma",
    45: "minus",
    46: "period",
    47: "slash",
    48: "zero",
    49: "one",
    50: "two",
    51: "three",
    52: "four",
    53: "five",
    54: "six",
    55: "seven",
    56: "eight",
    57: "nine",
    59: "semicolon",
    61: "equals",
    65: "a",
    66: "b",
    67: "c",
    68: "d",
    69: "e",
    70: "f",
    71: "g",
    72: "h",
    73: "i",
    74: "j",
    75: "k",
    76: "l",
    77: "m",
    78: "n",
    79: "o",
    80: "p",
    81: "q",
    82: "r",
    83: "s",
    84: "t",
    85: "u",
    86: "v",
    87: "w",
    88: "x",
    89: "y",
    90: "z",
    91: "leftBracket",
    92: "backslash",
    93: "rightBracket",
    96: "grave",
    257: "enter",
    258: "tab",
    259: "backspace",
    260: "insert",
    261: "delete",
    262: "right",
    263: "left",
    264: "down",
    265: "up",
    266: "pageUp",
    267: "pageDown",
    268: "home",
    269: "end",
    280: "capsLock",
    281: "scrollLock",
    282: "numLock",
    283: "printScreen",
    284: "pause",
    290: "f1",
    291: "f2",
    292: "f3",
    293: "f4",
    294: "f5",
    295: "f6",
    296: "f7",
    297: "f8",
    298: "f9",
    299: "f10",
    300: "f11",
    301: "f12",
    302: "f13",
    303: "f14",
    304: "f15",
    305: "f16",
    306: "f17",
    307: "f18",
    308: "f19",
    309: "f20",
    310: "f21",
    311: "f22",
    312: "f23",
    313: "f24",
    314: "f25",
    320: "numPad0",
    321: "numPad1",
    322: "numPad2",
    323: "numPad3",
    324: "numPad4",
    325: "numPad5",
    326: "numPad6",
    327: "numPad7",
    328: "numPad8",
    329: "numPad9",
    330: "numPadDecimal",
    331: "numPadDivide",
    332: "numPadMultiply",
    333: "numPadSubtract",
    334: "numPadAdd",
    335: "numPadEnter",
    336: "numPadEqual",
    340: "leftShift",
    341: "leftCtrl",
    342: "leftAlt",
    343: "leftSuper",
    344: "rightShift",
    345: "rightCtrl",
    346: "rightAlt",
    348: "menu",
}
_SYNTHETIC_EVENTS = []
_REDNET_RECEIVED_MESSAGES = {}
_REDNET_HOSTNAMES = {}
_REDNET_PRUNE_TIMER = None


def _camel(name):
    if "_" not in name:
        return name
    pieces = name.split("_")
    value = pieces[0] + "".join(part[:1].upper() + part[1:] for part in pieces[1:])
    return value.replace("Id", "ID").replace("Gps", "GPS").replace("Http", "HTTP").replace("Url", "URL")


def _unwrap(response, decode=True):
    if not response["ok"]:
        raise RuntimeError(response["error"] or "Lua host call failed")

    results = response.get("results") or []
    if len(results) == 0:
        return None
    if len(results) == 1:
        return _decode_host_value(results[0]) if decode else results[0]
    return tuple((_decode_host_value(result) if decode else result) for result in results)


def _call(module, method, *args):
    return _unwrap(_HOST.call(module, _camel(method), [_encode_host_value(arg) for arg in args]))


def _call_raw(module, method, *args):
    return _unwrap(_HOST.call(module, _camel(method), [_encode_host_value(arg) for arg in args]), decode=False)


def _peripheral_call(side, method, *args):
    return _call("peripheral", "call", side, _camel(method), *args)


def _is_valid_module_name(name):
    if not isinstance(name, str):
        return False
    if not name or len(name) > _MAX_IMPORT_NAME_LENGTH:
        return False
    for part in name.split("."):
        if not part or not part.isidentifier():
            return False
    return True


def _sanitize_search_paths(search_paths):
    sanitized = []
    for path in list(search_paths or []):
        if not isinstance(path, str):
            continue
        if not path:
            continue
        if len(path) > 512:
            continue
        if path not in sanitized:
            sanitized.append(path)
        if len(sanitized) >= _MAX_IMPORT_SEARCH_PATHS:
            break
    return sanitized


def _looks_like_terminal_bytes(value):
    return isinstance(value, str) and value and all(ord(ch) <= 0xFF for ch in value)


def _decode_terminal_text(value):
    if not _looks_like_terminal_bytes(value):
        return value

    raw = bytes(ord(ch) & 0xFF for ch in value)
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError:
        try:
            return raw.decode("cp866")
        except UnicodeDecodeError:
            return value


def _encode_terminal_text(value):
    if not isinstance(value, str) or not value:
        return value
    if _looks_like_terminal_bytes(value):
        return value

    try:
        encoded = value.encode("cp866")
    except UnicodeEncodeError:
        return value

    return "".join(chr(byte) for byte in encoded)


def _decode_host_value(value):
    if isinstance(value, str):
        return _decode_terminal_text(value)
    if isinstance(value, tuple):
        return tuple(_decode_host_value(item) for item in value)
    if isinstance(value, list):
        return [_decode_host_value(item) for item in value]
    if isinstance(value, dict):
        return {
            _decode_host_value(key): _decode_host_value(item)
            for key, item in value.items()
        }
    return value


def _encode_host_value(value):
    if isinstance(value, str):
        return _encode_terminal_text(value)
    if isinstance(value, tuple):
        return [_encode_host_value(item) for item in value]
    if isinstance(value, list):
        return [_encode_host_value(item) for item in value]
    if isinstance(value, dict):
        return {
            _encode_host_value(key): _encode_host_value(item)
            for key, item in value.items()
        }
    return value


def _decode_terminal_event(event):
    if not isinstance(event, tuple) or not event:
        return event

    field_index = _TERMINAL_EVENT_DECODERS.get(event[0])
    if field_index is None or len(event) <= field_index:
        return event

    values = list(event)
    values[field_index] = _decode_terminal_text(values[field_index])
    return tuple(values)


class _LuaProxyModule(_types.ModuleType):
    def __init__(self, name, host_module):
        super().__init__(name)
        self._host_module = host_module

    def __repr__(self):
        return "<cc module '%s'>" % self.__name__

    def __getattr__(self, item):
        def _invoke(*args):
            return _call(self._host_module, item, *args)

        return _invoke


class _ColorsModule(_types.ModuleType):
    def __init__(self, name, grey_alias=False):
        super().__init__(name)
        for key, value in _COLOR_VALUES.items():
            setattr(self, key, value)
        if grey_alias:
            self.grey = self.gray
            self.lightGrey = self.lightGray

    def __repr__(self):
        return "<cc module '%s'>" % self.__name__

    def combine(self, *values):
        result = 0
        for value in values:
            result |= int(value)
        return result

    def subtract(self, colors, *values):
        result = int(colors)
        for value in values:
            result &= ~int(value)
        return result

    def test(self, colors, color):
        colors = int(colors)
        color = int(color)
        return (colors & color) == color

    def pack_rgb(self, r, g, b):
        return ((int(r * 255) & 0xFF) << 16) | ((int(g * 255) & 0xFF) << 8) | (int(b * 255) & 0xFF)

    def packRGB(self, r, g, b):
        return self.pack_rgb(r, g, b)

    def unpack_rgb(self, rgb):
        rgb = int(rgb)
        return ((rgb >> 16) & 0xFF) / 255, ((rgb >> 8) & 0xFF) / 255, (rgb & 0xFF) / 255

    def unpackRGB(self, rgb):
        return self.unpack_rgb(rgb)

    def rgb8(self, r, g=None, b=None):
        if g is None and b is None:
            return self.unpack_rgb(r)
        return self.pack_rgb(r, g, b)

    def to_blit(self, color):
        color = int(color)
        hex_value = _COLOR_HEX_LOOKUP.get(color)
        if hex_value is not None:
            return hex_value
        if color < 1 or color > 0xFFFF:
            raise ValueError("Colour out of range")
        return format(color.bit_length() - 1, "x")

    def toBlit(self, color):
        return self.to_blit(color)

    def from_blit(self, hex_value):
        if not isinstance(hex_value, str) or len(hex_value) != 1:
            return None
        try:
            return 2 ** int(hex_value, 16)
        except ValueError:
            return None

    def fromBlit(self, hex_value):
        return self.from_blit(hex_value)


class _KeysModule(_types.ModuleType):
    def __init__(self):
        super().__init__("keys")
        for key_code, key_name in _KEY_NAMES.items():
            setattr(self, key_name, key_code)
        self.return_ = self.enter

    def __repr__(self):
        return "<cc module 'keys'>"

    def get_name(self, code):
        return _KEY_NAMES.get(int(code))

    def getName(self, code):
        return self.get_name(code)


class _PaintutilsModule(_types.ModuleType):
    def __init__(self):
        super().__init__("paintutils")

    def __repr__(self):
        return "<cc module 'paintutils'>"

    def parse_image(self, image):
        if not isinstance(image, str):
            raise TypeError("image must be a string")

        parsed = []
        for line in (image + "\n").splitlines():
            parsed.append([colors.from_blit(ch) or 0 for ch in line])
        return parsed

    def parseImage(self, image):
        return self.parse_image(image)

    def load_image(self, path):
        if not fs.exists(path):
            return None
        return self.parse_image(fs.read_text(path))

    def loadImage(self, path):
        return self.load_image(path)

    def draw_pixel(self, x_pos, y_pos, colour=None):
        x_pos = int(x_pos)
        y_pos = int(y_pos)
        if colour is not None:
            term.set_background_color(int(colour))
        term.set_cursor_pos(x_pos, y_pos)
        term.write(" ")

    def drawPixel(self, x_pos, y_pos, colour=None):
        return self.draw_pixel(x_pos, y_pos, colour)

    def draw_line(self, start_x, start_y, end_x, end_y, colour=None):
        start_x = _math.floor(start_x)
        start_y = _math.floor(start_y)
        end_x = _math.floor(end_x)
        end_y = _math.floor(end_y)

        if colour is not None:
            term.set_background_color(int(colour))

        if start_x == end_x and start_y == end_y:
            self.draw_pixel(start_x, start_y)
            return

        min_x = min(start_x, end_x)
        if min_x == start_x:
            min_y = start_y
            max_x = end_x
            max_y = end_y
        else:
            min_y = end_y
            max_x = start_x
            max_y = start_y

        x_diff = max_x - min_x
        y_diff = max_y - min_y

        if x_diff > abs(y_diff):
            y = min_y
            dy = y_diff / x_diff
            for x in range(min_x, max_x + 1):
                self.draw_pixel(x, _math.floor(y + 0.5))
                y += dy
        else:
            x = min_x
            dx = 0 if y_diff == 0 else x_diff / y_diff
            if max_y >= min_y:
                for y in range(min_y, max_y + 1):
                    self.draw_pixel(_math.floor(x + 0.5), y)
                    x += dx
            else:
                for y in range(min_y, max_y - 1, -1):
                    self.draw_pixel(_math.floor(x + 0.5), y)
                    x -= dx

    def drawLine(self, start_x, start_y, end_x, end_y, colour=None):
        return self.draw_line(start_x, start_y, end_x, end_y, colour)

    def draw_box(self, start_x, start_y, end_x, end_y, colour=None):
        start_x = _math.floor(start_x)
        start_y = _math.floor(start_y)
        end_x = _math.floor(end_x)
        end_y = _math.floor(end_y)

        if colour is not None:
            term.set_background_color(int(colour))
        else:
            colour = term.get_background_colour()

        if start_x == end_x and start_y == end_y:
            self.draw_pixel(start_x, start_y)
            return

        min_x = min(start_x, end_x)
        max_x = max(start_x, end_x)
        min_y = min(start_y, end_y)
        max_y = max(start_y, end_y)
        width = max_x - min_x + 1
        blit_color = colours.to_blit(colour)

        for y in range(min_y, max_y + 1):
            if y == min_y or y == max_y:
                term.set_cursor_pos(min_x, y)
                term.blit(" " * width, blit_color * width, blit_color * width)
            else:
                term.set_cursor_pos(min_x, y)
                term.blit(" ", blit_color, blit_color)
                term.set_cursor_pos(max_x, y)
                term.blit(" ", blit_color, blit_color)

    def drawBox(self, start_x, start_y, end_x, end_y, colour=None):
        return self.draw_box(start_x, start_y, end_x, end_y, colour)

    def draw_filled_box(self, start_x, start_y, end_x, end_y, colour=None):
        start_x = _math.floor(start_x)
        start_y = _math.floor(start_y)
        end_x = _math.floor(end_x)
        end_y = _math.floor(end_y)

        if colour is not None:
            term.set_background_color(int(colour))
        else:
            colour = term.get_background_colour()

        if start_x == end_x and start_y == end_y:
            self.draw_pixel(start_x, start_y)
            return

        min_x = min(start_x, end_x)
        max_x = max(start_x, end_x)
        min_y = min(start_y, end_y)
        max_y = max(start_y, end_y)
        width = max_x - min_x + 1
        blit_color = colours.to_blit(colour)

        for y in range(min_y, max_y + 1):
            term.set_cursor_pos(min_x, y)
            term.blit(" " * width, blit_color * width, blit_color * width)

    def drawFilledBox(self, start_x, start_y, end_x, end_y, colour=None):
        return self.draw_filled_box(start_x, start_y, end_x, end_y, colour)

    def draw_image(self, image, x_pos, y_pos):
        if not isinstance(image, list):
            raise TypeError("image must be a table/list")
        x_pos = int(x_pos)
        y_pos = int(y_pos)

        for y_index, line in enumerate(image, start=0):
            if not isinstance(line, list):
                continue
            for x_index, pixel in enumerate(line, start=0):
                if int(pixel) > 0:
                    term.set_background_color(int(pixel))
                    self.draw_pixel(x_pos + x_index, y_pos + y_index)

    def drawImage(self, image, x_pos, y_pos):
        return self.draw_image(image, x_pos, y_pos)


class _Vector:
    __slots__ = ("x", "y", "z")

    def __init__(self, x=0, y=0, z=0):
        self.x = float(x)
        self.y = float(y)
        self.z = float(z)

    def __repr__(self):
        return f"vector.new({self.x:g}, {self.y:g}, {self.z:g})"

    def __iter__(self):
        yield self.x
        yield self.y
        yield self.z

    def __neg__(self):
        return _Vector(-self.x, -self.y, -self.z)

    def __add__(self, other):
        other = _coerce_vector(other)
        return _Vector(self.x + other.x, self.y + other.y, self.z + other.z)

    def __sub__(self, other):
        other = _coerce_vector(other)
        return _Vector(self.x - other.x, self.y - other.y, self.z - other.z)

    def __mul__(self, other):
        scalar = _coerce_scalar(other)
        return _Vector(self.x * scalar, self.y * scalar, self.z * scalar)

    def __rmul__(self, other):
        return self.__mul__(other)

    def __truediv__(self, other):
        scalar = _coerce_scalar(other)
        return _Vector(self.x / scalar, self.y / scalar, self.z / scalar)

    def __eq__(self, other):
        try:
            other = _coerce_vector(other)
        except TypeError:
            return False
        return self.x == other.x and self.y == other.y and self.z == other.z

    def add(self, other):
        return self + other

    def sub(self, other):
        return self - other

    def mul(self, other):
        return self * other

    def div(self, other):
        return self / other

    def dot(self, other):
        other = _coerce_vector(other)
        return self.x * other.x + self.y * other.y + self.z * other.z

    def cross(self, other):
        other = _coerce_vector(other)
        return _Vector(
            self.y * other.z - self.z * other.y,
            self.z * other.x - self.x * other.z,
            self.x * other.y - self.y * other.x,
        )

    def length(self):
        return _math.sqrt(self.x * self.x + self.y * self.y + self.z * self.z)

    def normalize(self):
        length = self.length()
        if length == 0:
            return _Vector(0, 0, 0)
        return self / length

    def round(self, tolerance=1.0):
        tolerance = abs(float(tolerance))
        if tolerance == 0:
            tolerance = 1.0
        return _Vector(
            _round_to_tolerance(self.x, tolerance),
            _round_to_tolerance(self.y, tolerance),
            _round_to_tolerance(self.z, tolerance),
        )

    def tostring(self):
        return repr(self)


def _coerce_scalar(value):
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, (int, float)):
        return float(value)
    raise TypeError("expected a number")


def _coerce_vector(value):
    if isinstance(value, _Vector):
        return value
    if isinstance(value, (list, tuple)) and len(value) == 3:
        return _Vector(value[0], value[1], value[2])
    raise TypeError("expected a vector")


def _round_to_tolerance(value, tolerance):
    return _math.floor((value / tolerance) + 0.5) * tolerance


class _VectorModule(_types.ModuleType):
    def __init__(self):
        super().__init__("vector")

    def __repr__(self):
        return "<cc module 'vector'>"

    def new(self, x=0, y=0, z=0):
        return _Vector(x, y, z)


_LUA_KEYWORDS = {
    "and", "break", "do", "else", "elseif", "end", "false", "for", "function",
    "if", "in", "local", "nil", "not", "or", "repeat", "return", "then",
    "true", "until", "while",
}


def _is_lua_identifier(value):
    return isinstance(value, str) and _re.match(r"^[A-Za-z_][A-Za-z0-9_]*$", value) and value not in _LUA_KEYWORDS


def _escape_lua_string(value):
    parts = ['"']
    for char in value:
        if char == "\\":
            parts.append("\\\\")
        elif char == '"':
            parts.append('\\"')
        elif char == "\n":
            parts.append("\\n")
        elif char == "\r":
            parts.append("\\r")
        elif char == "\t":
            parts.append("\\t")
        elif char == "\b":
            parts.append("\\b")
        elif char == "\f":
            parts.append("\\f")
        elif ord(char) < 32:
            parts.append(f"\\{ord(char):03d}")
        else:
            parts.append(char)
    parts.append('"')
    return "".join(parts)


def _escape_json_string(value, unicode_strings=True):
    parts = ['"']
    for char in value:
        code = ord(char)
        if char == '"':
            parts.append('\\"')
        elif char == "\\":
            parts.append("\\\\")
        elif char == "\b":
            parts.append("\\b")
        elif char == "\f":
            parts.append("\\f")
        elif char == "\n":
            parts.append("\\n")
        elif char == "\r":
            parts.append("\\r")
        elif char == "\t":
            parts.append("\\t")
        elif code < 0x20:
            parts.append(f"\\u{code:04X}")
        elif unicode_strings or code < 0x7F:
            parts.append(char)
        elif code <= 0xFFFF:
            parts.append(f"\\u{code:04X}")
        else:
            code -= 0x10000
            high = 0xD800 + ((code >> 10) & 0x3FF)
            low = 0xDC00 + (code & 0x3FF)
            parts.append(f"\\u{high:04X}\\u{low:04X}")
    parts.append('"')
    return "".join(parts)


class _LuaTextParser:
    def __init__(self, text):
        self.text = text
        self.length = len(text)
        self.pos = 0

    def parse(self):
        value = self._parse_value()
        self._skip_ws()
        if self.pos != self.length:
            raise ValueError("unexpected trailing data")
        return value

    def _skip_ws(self):
        while self.pos < self.length and self.text[self.pos].isspace():
            self.pos += 1

    def _peek(self):
        self._skip_ws()
        if self.pos >= self.length:
            return None
        return self.text[self.pos]

    def _consume(self, token):
        self._skip_ws()
        if not self.text.startswith(token, self.pos):
            raise ValueError(f"expected {token!r}")
        self.pos += len(token)

    def _parse_value(self):
        self._skip_ws()
        if self.pos >= self.length:
            raise ValueError("unexpected end of input")

        if self.text.startswith("0/0", self.pos):
            self.pos += 3
            return float("nan")
        if self.text.startswith("1/0", self.pos):
            self.pos += 3
            return float("inf")
        if self.text.startswith("-1/0", self.pos):
            self.pos += 4
            return float("-inf")

        char = self.text[self.pos]
        if char == "{":
            return self._parse_table()
        if char in ("'", '"'):
            return self._parse_string()
        if char.isdigit() or char in "+-.":
            return self._parse_number()
        if self.text.startswith("true", self.pos):
            self.pos += 4
            return True
        if self.text.startswith("false", self.pos):
            self.pos += 5
            return False
        if self.text.startswith("nil", self.pos):
            self.pos += 3
            return None
        raise ValueError("unexpected token")

    def _parse_string(self):
        quote = self.text[self.pos]
        self.pos += 1
        parts = []
        while self.pos < self.length:
            char = self.text[self.pos]
            self.pos += 1
            if char == quote:
                return "".join(parts)
            if char != "\\":
                parts.append(char)
                continue
            if self.pos >= self.length:
                raise ValueError("unterminated escape")
            esc = self.text[self.pos]
            self.pos += 1
            mapping = {
                "a": "\a",
                "b": "\b",
                "f": "\f",
                "n": "\n",
                "r": "\r",
                "t": "\t",
                "v": "\v",
                "\\": "\\",
                '"': '"',
                "'": "'",
            }
            if esc in mapping:
                parts.append(mapping[esc])
            elif esc.isdigit():
                digits = esc
                for _ in range(2):
                    if self.pos < self.length and self.text[self.pos].isdigit():
                        digits += self.text[self.pos]
                        self.pos += 1
                    else:
                        break
                parts.append(chr(int(digits, 10)))
            else:
                parts.append(esc)
        raise ValueError("unterminated string")

    def _parse_number(self):
        start = self.pos
        while self.pos < self.length and self.text[self.pos] in "+-0123456789.eE":
            self.pos += 1
        raw = self.text[start:self.pos]
        if not raw:
            raise ValueError("expected number")
        if any(ch in raw for ch in ".eE"):
            return float(raw)
        return int(raw, 10)

    def _parse_identifier(self):
        self._skip_ws()
        start = self.pos
        while self.pos < self.length and (self.text[self.pos].isalnum() or self.text[self.pos] == "_"):
            self.pos += 1
        if start == self.pos:
            return None
        return self.text[start:self.pos]

    def _parse_table(self):
        self._consume("{")
        entries = {}
        array_index = 1
        has_named_keys = False

        while True:
            self._skip_ws()
            if self._peek() == "}":
                self.pos += 1
                break

            if self._peek() == "[":
                self.pos += 1
                key = self._parse_value()
                self._consume("]")
                self._consume("=")
                value = self._parse_value()
                entries[key] = value
                has_named_keys = True
            else:
                snapshot = self.pos
                identifier = self._parse_identifier()
                if identifier is not None and identifier not in ("true", "false", "nil"):
                    self._skip_ws()
                    if self._peek() == "=":
                        self.pos += 1
                        value = self._parse_value()
                        entries[identifier] = value
                        has_named_keys = True
                    else:
                        self.pos = snapshot
                        entries[array_index] = self._parse_value()
                        array_index += 1
                else:
                    self.pos = snapshot
                    entries[array_index] = self._parse_value()
                    array_index += 1

            self._skip_ws()
            if self._peek() in (",", ";"):
                self.pos += 1

        if not has_named_keys:
            numeric_keys = sorted(key for key in entries.keys() if isinstance(key, int) and key >= 1)
            if numeric_keys == list(range(1, len(numeric_keys) + 1)) and len(numeric_keys) == len(entries):
                return [entries[index] for index in numeric_keys]
        return entries


class _TextutilsJsonNull:
    def __repr__(self):
        return "textutils.json_null"


class _TextutilsEmptyJsonArray:
    def __repr__(self):
        return "textutils.empty_json_array"


class _TextutilsModule(_types.ModuleType):
    def __init__(self):
        super().__init__("textutils")
        self.json_null = _TextutilsJsonNull()
        self.empty_json_array = _TextutilsEmptyJsonArray()

    def __repr__(self):
        return "<cc module 'textutils'>"

    def slow_write(self, text, rate=None):
        rate = 20 if rate is None else rate
        if rate < 0:
            raise ValueError("Rate must be positive")
        delay = 0 if rate == 0 else 1 / rate

        wrapped = "\n".join(self._wrap(str(text)))
        for char in wrapped:
            if delay > 0:
                sleep(delay)
            if char == "\n":
                _terminal_newline()
            else:
                term.write(char)

    def slowWrite(self, text, rate=None):
        return self.slow_write(text, rate)

    def slow_print(self, text="", rate=None):
        self.slow_write(text, rate)
        print()

    def slowPrint(self, text="", rate=None):
        return self.slow_print(text, rate)

    def format_time(self, value, twenty_four_hour=False):
        value = float(value)
        period = None
        if not twenty_four_hour:
            period = "PM" if value >= 12 else "AM"
            if value >= 13:
                value -= 12
        hour = int(_math.floor(value))
        minute = int(_math.floor((value - hour) * 60))
        if period is not None:
            return f"{12 if hour == 0 else hour}:{minute:02d} {period}"
        return f"{hour}:{minute:02d}"

    def formatTime(self, value, twenty_four_hour=False):
        return self.format_time(value, twenty_four_hour)

    def paged_print(self, text="", free_lines=0):
        lines = self._wrap("" if text is None else str(text))
        _, height = term.get_size()
        printed = 0
        remaining_free = free_lines

        for line in lines:
            if printed > 0 and remaining_free <= 0 and self._cursor_y() >= height:
                _terminal_write_text("Press any key to continue")
                os.pull_event("key")
                term.clear_line()
                term.set_cursor_pos(1, height)
            elif remaining_free > 0:
                remaining_free -= 1
            print(line)
            printed += 1
        return printed

    def pagedPrint(self, text="", free_lines=0):
        return self.paged_print(text, free_lines)

    def serialise(self, value, opts=None):
        return self._serialise(value, opts or {})

    def serialize(self, value, opts=None):
        return self.serialise(value, opts)

    def unserialise(self, text):
        try:
            return _LuaTextParser(text).parse()
        except Exception:
            return None

    def unserialize(self, text):
        return self.unserialise(text)

    def serialise_json(self, value, options=None):
        if isinstance(options, bool):
            options = {"nbt_style": options}
        else:
            options = options or {}
        return self._serialise_json(value, options, {})

    def serialize_json(self, value, options=None):
        return self.serialise_json(value, options)

    def serialiseJSON(self, value, options=None):
        return self.serialise_json(value, options)

    def serializeJSON(self, value, options=None):
        return self.serialise_json(value, options)

    def unserialise_json(self, text, options=None):
        options = options or {}
        value = _json.loads(text)
        return self._from_json_compatible(value, options)

    def unserialize_json(self, text, options=None):
        return self.unserialise_json(text, options)

    def unserialiseJSON(self, text, options=None):
        return self.unserialise_json(text, options)

    def unserializeJSON(self, text, options=None):
        return self.unserialise_json(text, options)

    def url_encode(self, text):
        data = str(text).replace("\n", "\r\n").encode("utf-8")
        parts = []
        for byte in data:
            char = chr(byte)
            if ("A" <= char <= "Z") or ("a" <= char <= "z") or ("0" <= char <= "9") or char in "-_.":
                parts.append(char)
            elif char == " ":
                parts.append("+")
            else:
                parts.append(f"%{byte:02X}")
        return "".join(parts)

    def urlEncode(self, text):
        return self.url_encode(text)

    def complete(self, search_text, search_table=None):
        if search_table is None:
            search_table = _base_scope()

        table = search_table
        prefix = str(search_text)
        if "." in prefix:
            head, prefix = prefix.rsplit(".", 1)
            table = self._resolve_completion_target(search_table, head)
            if table is None:
                return []

        results = []
        seen = set()
        for key in self._candidate_keys(table):
            if key in seen or not key.startswith(prefix):
                continue
            seen.add(key)
            suffix = key[len(prefix):]
            value = self._lookup(table, key)
            if callable(value):
                suffix += "("
            elif self._has_children(value):
                suffix += "."
            results.append(suffix)
        return sorted(results)

    def _wrap(self, text):
        width, _ = term.get_size()
        width = max(int(width), 1)
        lines, current = [], ""
        pos = 0

        while pos < len(text):
            head = text[pos]
            if head in " \t":
                start = pos
                while pos < len(text) and text[pos] in " \t":
                    pos += 1
                current += text[start:pos]
            elif head == "\n":
                lines.append(current[:width])
                current = ""
                pos += 1
            else:
                start = pos
                while pos < len(text) and text[pos] not in " \t\n":
                    pos += 1
                word = text[start:pos]
                if len(word) > width:
                    while word:
                        remaining = width - len(current)
                        if remaining <= 0:
                            lines.append(current[:width])
                            current = ""
                            remaining = width
                        current += word[:remaining]
                        word = word[remaining:]
                else:
                    if len(current) + len(word) > width:
                        lines.append(current[:width])
                        current = ""
                    current += word

        lines.append(current[:width])
        return lines

    def _cursor_y(self):
        _, y = term.get_cursor_pos()
        return y

    def _serialise(self, value, opts, tracking=None, indent=""):
        if tracking is None:
            tracking = {}

        if value is self.json_null:
            return "nil"
        if value is self.empty_json_array:
            return "{}"
        if value is None:
            return "nil"
        if isinstance(value, str):
            return _escape_lua_string(value)
        if isinstance(value, bool):
            return "true" if value else "false"
        if isinstance(value, int):
            return str(value)
        if isinstance(value, float):
            if _math.isnan(value):
                return "0/0"
            if value == float("inf"):
                return "1/0"
            if value == float("-inf"):
                return "-1/0"
            return repr(value)

        if isinstance(value, (list, tuple, dict)):
            obj_id = id(value)
            if obj_id in tracking:
                if tracking[obj_id] is False:
                    raise ValueError("Cannot serialise table with repeated entries")
                raise ValueError("Cannot serialise table with recursive entries")
            tracking[obj_id] = True
            compact = opts.get("compact", False)
            if compact:
                open_text, sub_indent, open_key, close_key, equal, comma = "{", "", "[", "]=", "=", ","
            else:
                open_text, sub_indent, open_key, close_key, equal, comma = "{\n", indent + "  ", "[ ", " ] = ", " = ", ",\n"

            parts = []
            if isinstance(value, (list, tuple)):
                for item in value:
                    parts.append(sub_indent + self._serialise(item, opts, tracking, sub_indent))
            else:
                seen_keys = set()
                array_index = 1
                while array_index in value:
                    seen_keys.add(array_index)
                    parts.append(sub_indent + self._serialise(value[array_index], opts, tracking, sub_indent))
                    array_index += 1
                for key, item in value.items():
                    if key in seen_keys:
                        continue
                    if _is_lua_identifier(key):
                        entry = f"{key}{equal}{self._serialise(item, opts, tracking, sub_indent)}"
                    else:
                        entry = f"{open_key}{self._serialise(key, opts, tracking, sub_indent)}{close_key}{self._serialise(item, opts, tracking, sub_indent)}"
                    parts.append(sub_indent + entry)

            if opts.get("allow_repetitions"):
                tracking.pop(obj_id, None)
            else:
                tracking[obj_id] = False

            if not parts:
                return "{}"
            if compact:
                return open_text + comma.join(part.lstrip() for part in parts) + "}"
            return open_text + comma.join(parts) + ",\n" + indent + "}"

        raise TypeError("Cannot serialise type %s" % type(value).__name__)

    def _serialise_json(self, value, options, tracking):
        unicode_strings = options.get("unicode_strings", True)

        if value is self.empty_json_array:
            return "[]"
        if value is self.json_null or value is None:
            return "null"
        if isinstance(value, bool):
            return "true" if value else "false"
        if isinstance(value, (int, float)) and not isinstance(value, bool):
            return str(value).lower()
        if isinstance(value, str):
            return _escape_json_string(value, unicode_strings=unicode_strings)

        if isinstance(value, (list, tuple)):
            obj_id = id(value)
            if obj_id in tracking:
                if tracking[obj_id] is False:
                    raise ValueError("Cannot serialise table with repeated entries")
                raise ValueError("Cannot serialise table with recursive entries")
            tracking[obj_id] = True
            result = "[" + ",".join(self._serialise_json(item, options, tracking) for item in value) + "]"
            if options.get("allow_repetitions"):
                tracking.pop(obj_id, None)
            else:
                tracking[obj_id] = False
            return result

        if isinstance(value, dict):
            obj_id = id(value)
            if obj_id in tracking:
                if tracking[obj_id] is False:
                    raise ValueError("Cannot serialise table with repeated entries")
                raise ValueError("Cannot serialise table with recursive entries")
            tracking[obj_id] = True

            if not value:
                result = "{}"
            else:
                object_parts = []
                largest_array_index = 0
                for key, item in value.items():
                    if isinstance(key, str):
                        if options.get("nbt_style"):
                            object_key = key
                        else:
                            object_key = _escape_json_string(key, unicode_strings=unicode_strings)
                        object_parts.append(object_key + ":" + self._serialise_json(item, options, tracking))
                    elif isinstance(key, int) and key > largest_array_index:
                        largest_array_index = key

                array_parts = []
                for index in range(1, largest_array_index + 1):
                    if index not in value:
                        array_parts.append("null")
                    else:
                        array_parts.append(self._serialise_json(value[index], options, tracking))

                if object_parts or not array_parts:
                    result = "{" + ",".join(object_parts) + "}"
                else:
                    result = "[" + ",".join(array_parts) + "]"

            if options.get("allow_repetitions"):
                tracking.pop(obj_id, None)
            else:
                tracking[obj_id] = False
            return result

        raise TypeError("Cannot serialise type %s" % type(value).__name__)

    def _to_json_compatible(self, value):
        if value is self.json_null:
            return None
        if value is self.empty_json_array:
            return []
        if isinstance(value, list):
            return [self._to_json_compatible(item) for item in value]
        if isinstance(value, tuple):
            return [self._to_json_compatible(item) for item in value]
        if isinstance(value, dict):
            return {str(key): self._to_json_compatible(item) for key, item in value.items()}
        return value

    def _from_json_compatible(self, value, options):
        if value is None and options.get("parse_null"):
            return self.json_null
        if isinstance(value, list):
            if not value and options.get("parse_empty_array", True):
                return self.empty_json_array
            return [self._from_json_compatible(item, options) for item in value]
        if isinstance(value, dict):
            return {key: self._from_json_compatible(item, options) for key, item in value.items()}
        return value

    def _resolve_completion_target(self, search_table, head):
        target = search_table
        for part in head.split("."):
            target = self._lookup(target, part)
            if target is None:
                return None
        return target

    def _candidate_keys(self, table):
        if isinstance(table, dict):
            return [str(key) for key in table.keys() if isinstance(key, str)]
        return [name for name in dir(table) if isinstance(name, str)]

    def _lookup(self, table, key):
        if isinstance(table, dict):
            return table.get(key)
        return getattr(table, key, None)

    def _has_children(self, value):
        return hasattr(value, "__dict__") or isinstance(value, (dict, _types.ModuleType))


class _CCFile:
    def __init__(self, token, mode):
        self._token = token
        self._mode = mode
        self._binary = "b" in str(mode or "")
        self._closed = False

    def read(self, count=None):
        caller = _call_raw if self._binary else _call
        value = caller("__fs_handle", "read_all" if count is None else "read", self._token, *((), (count,))[count is not None])
        if value is None:
            return None
        if self._binary:
            return bytes(ord(ch) & 0xFF for ch in value)
        if count is None:
            return _decode_terminal_text(value)
        return value

    def readline(self):
        value = (_call_raw if self._binary else _call)("__fs_handle", "read_line", self._token)
        if value is None:
            return None
        if self._binary:
            return bytes(ord(ch) & 0xFF for ch in value)
        return _decode_terminal_text(value)

    def write(self, value):
        if self._binary:
            if isinstance(value, bytearray):
                value = bytes(value)
            if isinstance(value, bytes):
                payload = "".join(chr(byte) for byte in value)
            else:
                raise TypeError("binary file write expects bytes-like data")
            _call("__fs_handle", "write", self._token, payload)
            return None

        _call("__fs_handle", "write", self._token, _encode_terminal_text(str(value)))

    def writeline(self, value=""):
        if self._binary:
            if isinstance(value, bytearray):
                value = bytes(value)
            if isinstance(value, bytes):
                self.write(value + b"\n")
            else:
                raise TypeError("binary file writeline expects bytes-like data")
            return None

        _call("__fs_handle", "write_line", self._token, _encode_terminal_text(str(value)))

    def flush(self):
        _call("__fs_handle", "flush", self._token)

    def seek(self, whence="cur", offset=0):
        return _call("__fs_handle", "seek", self._token, whence, offset)

    def close(self):
        if not self._closed:
            _call("__fs_handle", "close", self._token)
            self._closed = True

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, exc_tb):
        self.close()
        return False

    def __repr__(self):
        state = "closed" if self._closed else "open"
        return "<cc file mode=%r %s>" % (self._mode, state)


class _FSModule(_LuaProxyModule):
    def open(self, path, mode="r"):
        return _CCFile(_call("__fs_handle", "open", path, mode), mode)

    def join(self, *parts):
        if not parts:
            return "/"

        current = parts[0]
        for part in parts[1:]:
            current = _call("fs", "combine", current, part)
        return current

    def read_all(self, path):
        return _decode_terminal_text(_call("__fs", "read_all", path))

    def write_all(self, path, data):
        return _call("__fs", "write_all", path, _encode_terminal_text(str(data)))

    def append_all(self, path, data):
        return _call("__fs", "append_all", path, _encode_terminal_text(str(data)))

    def read_text(self, path):
        return self.read_all(path)

    def write_text(self, path, data):
        return self.write_all(path, data)

    def append_text(self, path, data):
        return self.append_all(path, data)

    def read_lines(self, path):
        content = self.read_text(path)
        if not content:
            return []
        return content.splitlines()

    def write_lines(self, path, lines):
        self.write_text(path, "\n".join(str(line) for line in lines))

    def read_json(self, path):
        return _json.loads(self.read_text(path))

    def write_json(self, path, data, indent=2, sort_keys=False):
        self.write_text(path, _json.dumps(data, indent=indent, sort_keys=sort_keys))


class _PeripheralProxy:
    def __init__(self, side):
        self._side = side

    def __repr__(self):
        return "<peripheral %r>" % self._side

    def __getattr__(self, item):
        def _invoke(*args):
            return _peripheral_call(self._side, item, *args)

        return _invoke


class _PeripheralModule(_LuaProxyModule):
    def names(self, peripheral_type=None):
        names = _call("peripheral", "get_names") or []
        if peripheral_type is None:
            return list(names)
        return [name for name in names if _call("peripheral", "has_type", name, peripheral_type)]

    def list(self, peripheral_type=None):
        return [
            {
                "name": name,
                "type": _call("peripheral", "get_type", name),
            }
            for name in self.names(peripheral_type)
        ]

    def types(self):
        return {name: _call("peripheral", "get_type", name) for name in self.names()}

    def wrap(self, side):
        if not _call("peripheral", "is_present", side):
            return None
        return _PeripheralProxy(side)

    def wrap_all(self, peripheral_type=None):
        return {name: _PeripheralProxy(name) for name in self.names(peripheral_type)}

    def find(self, peripheral_type):
        names = self.names(peripheral_type)
        if names:
            return _PeripheralProxy(names[0])
        return None

    def find_all(self, peripheral_type):
        return [_PeripheralProxy(name) for name in self.names(peripheral_type)]


def _queue_synthetic_event(*values):
    _SYNTHETIC_EVENTS.append(tuple(values))


def _pop_synthetic_event(filter_name=None):
    for index, event in enumerate(_SYNTHETIC_EVENTS):
        if filter_name is None or event[0] == filter_name:
            return _SYNTHETIC_EVENTS.pop(index)
    return None


def _host_pull_event(raw):
    if raw:
        return _decode_terminal_event(_call("os", "pull_event_raw"))
    return _decode_terminal_event(_call("os", "pull_event"))


def _peripheral_type(name):
    value = _call("peripheral", "get_type", name)
    if isinstance(value, tuple):
        return value[0] if value else None
    if isinstance(value, list):
        return value[0] if value else None
    return value


def _rednet_id_as_channel(value=None):
    if value is None:
        value = _call("os", "get_computer_id")
    return int(value) % 65500


def _rednet_open_modems():
    names = _call("peripheral", "get_names") or []
    return [
        name for name in names
        if _peripheral_type(name) == "modem"
        and _peripheral_call(name, "is_open", _rednet_id_as_channel())
        and _peripheral_call(name, "is_open", 65535)
    ]


def _rednet_clock():
    return float(_call("os", "clock"))


def _rednet_ensure_prune_timer():
    global _REDNET_PRUNE_TIMER
    if _REDNET_PRUNE_TIMER is None:
        _REDNET_PRUNE_TIMER = _call("os", "start_timer", 10)


def _rednet_mark_received(message_id):
    _REDNET_RECEIVED_MESSAGES[message_id] = _rednet_clock() + 9.5
    _rednet_ensure_prune_timer()


def _rednet_queue_message(sender_id, message, protocol):
    _queue_synthetic_event("rednet_message", sender_id, message, protocol)
    if protocol == "dns" and isinstance(message, dict) and message.get("sType") == "lookup":
        hostname = _REDNET_HOSTNAMES.get(message.get("sProtocol"))
        if hostname is not None and (message.get("sHostname") is None or message.get("sHostname") == hostname):
            rednet.send(
                sender_id,
                {
                    "sType": "lookup response",
                    "sHostname": hostname,
                    "sProtocol": message.get("sProtocol"),
                },
                "dns",
            )


def _rednet_prune_received_messages():
    global _REDNET_PRUNE_TIMER
    now = _rednet_clock()
    expired = [
        message_id
        for message_id, deadline in _REDNET_RECEIVED_MESSAGES.items()
        if deadline <= now
    ]
    for message_id in expired:
        _REDNET_RECEIVED_MESSAGES.pop(message_id, None)
    _REDNET_PRUNE_TIMER = _call("os", "start_timer", 10) if _REDNET_RECEIVED_MESSAGES else None


def _rednet_process_event(event):
    global _REDNET_PRUNE_TIMER

    if not isinstance(event, tuple) or not event:
        return event

    event_name = event[0]
    if event_name == "modem_message" and len(event) >= 5:
        modem, channel, reply_channel, message = event[1], event[2], event[3], event[4]
        local_channel = _rednet_id_as_channel()
        if channel in (local_channel, 65535):
            if (
                isinstance(message, dict)
                and isinstance(message.get("nMessageID"), (int, float))
                and message.get("nMessageID") == message.get("nMessageID")
                and message.get("nMessageID") not in _REDNET_RECEIVED_MESSAGES
                and (
                    message.get("nSender") is None
                    or isinstance(message.get("nSender"), (int, float))
                )
                and (
                    message.get("nRecipient") == _call("os", "get_computer_id")
                    or channel == 65535
                )
                and modem in _rednet_open_modems()
            ):
                _rednet_mark_received(message["nMessageID"])
                _rednet_queue_message(
                    message.get("nSender", reply_channel),
                    message.get("message"),
                    message.get("sProtocol"),
                )

    elif event_name == "timer" and len(event) >= 2 and event[1] == _REDNET_PRUNE_TIMER:
        _REDNET_PRUNE_TIMER = None
        _rednet_prune_received_messages()

    return event


def _pull_event_impl(raw=False, filter_name=None):
    queued = _pop_synthetic_event(filter_name)
    if queued is not None:
        _rednet_process_event(queued)
        return _decode_terminal_event(queued)

    while True:
        event = _host_pull_event(raw)
        event = _rednet_process_event(event)

        if filter_name is None:
            return event

        if isinstance(event, tuple) and event and event[0] == filter_name:
            return event

        queued = _pop_synthetic_event(filter_name)
        if queued is not None:
            _rednet_process_event(queued)
            return _decode_terminal_event(queued)


class _RednetModule(_types.ModuleType):
    CHANNEL_BROADCAST = 65535
    CHANNEL_REPEAT = 65533
    MAX_ID_CHANNELS = 65500

    def __repr__(self):
        return "<cc module 'rednet'>"

    def open(self, modem):
        if _peripheral_type(modem) != "modem":
            raise ValueError("No such modem: %s" % modem)
        _peripheral_call(modem, "open", _rednet_id_as_channel())
        _peripheral_call(modem, "open", self.CHANNEL_BROADCAST)

    def close(self, modem=None):
        if modem is not None:
            if _peripheral_type(modem) != "modem":
                raise ValueError("No such modem: %s" % modem)
            _peripheral_call(modem, "close", _rednet_id_as_channel())
            _peripheral_call(modem, "close", self.CHANNEL_BROADCAST)
            return

        for side in _call("peripheral", "get_names") or []:
            if self.is_open(side):
                self.close(side)

    def is_open(self, modem=None):
        if modem is not None:
            return (
                _peripheral_type(modem) == "modem"
                and _peripheral_call(modem, "is_open", _rednet_id_as_channel())
                and _peripheral_call(modem, "is_open", self.CHANNEL_BROADCAST)
            )
        return bool(_rednet_open_modems())

    def send(self, recipient_id, message, protocol=None):
        message_id = _random.randint(1, 2147483647)
        _rednet_mark_received(message_id)

        if recipient_id == _call("os", "get_computer_id"):
            _rednet_queue_message(recipient_id, message, protocol)
            return True

        recipient_channel = self.CHANNEL_BROADCAST if recipient_id == self.CHANNEL_BROADCAST else _rednet_id_as_channel(recipient_id)
        reply_channel = _rednet_id_as_channel()
        payload = {
            "nMessageID": message_id,
            "nRecipient": recipient_id,
            "nSender": _call("os", "get_computer_id"),
            "message": message,
            "sProtocol": protocol,
        }

        sent = False
        for modem in _rednet_open_modems():
            _peripheral_call(modem, "transmit", recipient_channel, reply_channel, payload)
            _peripheral_call(modem, "transmit", self.CHANNEL_REPEAT, reply_channel, payload)
            sent = True
        return sent

    def broadcast(self, message, protocol=None):
        return self.send(self.CHANNEL_BROADCAST, message, protocol)

    def receive(self, first=None, second=None):
        if isinstance(first, str) or first is None and isinstance(second, (int, float, type(None))):
            protocol_filter, timeout = first, second
        else:
            timeout, protocol_filter = first, second

        timer = _call("os", "start_timer", timeout) if timeout is not None else None
        try:
            while True:
                event = _pull_event_impl(raw=False, filter_name=None if timer is not None else "rednet_message")
                if not event:
                    continue

                if event[0] == "rednet_message":
                    sender_id, message, protocol = event[1], event[2], event[3]
                    if protocol_filter is None or protocol == protocol_filter:
                        return sender_id, message, protocol
                elif event[0] == "timer" and timer is not None and len(event) >= 2 and event[1] == timer:
                    return None
            return None
        finally:
            if timer is not None:
                _call("os", "cancel_timer", timer)

    def host(self, protocol, hostname):
        if hostname == "localhost":
            raise ValueError("Reserved hostname")
        if _REDNET_HOSTNAMES.get(protocol) != hostname:
            if self.lookup(protocol, hostname) is not None:
                raise ValueError("Hostname in use")
            _REDNET_HOSTNAMES[protocol] = hostname

    def unhost(self, protocol):
        _REDNET_HOSTNAMES.pop(protocol, None)

    def lookup(self, protocol, hostname=None):
        results = [] if hostname is None else None
        localhost = _REDNET_HOSTNAMES.get(protocol)
        if localhost:
            local_id = _call("os", "get_computer_id")
            if hostname is None:
                results.append(local_id)
            elif hostname in ("localhost", localhost):
                return local_id

        if not self.is_open():
            return tuple(results) if results is not None else None

        self.broadcast(
            {
                "sType": "lookup",
                "sProtocol": protocol,
                "sHostname": hostname,
            },
            "dns",
        )

        timer = _call("os", "start_timer", 2)
        try:
            while True:
                event = _pull_event_impl(raw=False, filter_name=None)
                if not event:
                    continue

                if event[0] == "rednet_message":
                    sender_id, message, message_protocol = event[1], event[2], event[3]
                    if (
                        message_protocol == "dns"
                        and isinstance(message, dict)
                        and message.get("sType") == "lookup response"
                        and message.get("sProtocol") == protocol
                    ):
                        if hostname is None:
                            results.append(sender_id)
                        elif message.get("sHostname") == hostname:
                            return sender_id
                elif event[0] == "timer" and len(event) >= 2 and event[1] == timer:
                    break
        finally:
            _call("os", "cancel_timer", timer)

        return tuple(results) if results is not None else None

    def run(self):
        while True:
            _pull_event_impl(raw=True, filter_name=None)

    def send_json(self, recipient_id, payload, protocol=None):
        return self.send(recipient_id, _json.dumps(payload, separators=(",", ":")), protocol)

    def broadcast_json(self, payload, protocol=None):
        return self.broadcast(_json.dumps(payload, separators=(",", ":")), protocol)

    def receive_json(self, timeout=None, protocol=None):
        message = self.receive(timeout, protocol)
        if message is None:
            return None
        if isinstance(message, tuple) and len(message) >= 2 and isinstance(message[1], str):
            values = list(message)
            values[1] = _json.loads(values[1])
            return tuple(values)
        if isinstance(message, str):
            return _json.loads(message)
        return message


def _monitor_side(target):
    if isinstance(target, str):
        return target
    side = getattr(target, "_side", None)
    if isinstance(side, str):
        return side
    raise TypeError("monitor target must be a side string or wrapped monitor peripheral")


class _ImageHandle:
    def __init__(self, token):
        self._token = str(token)

    def __repr__(self):
        info = self.info()
        return f"<cc image {info['width']}x{info['height']}>"

    def info(self):
        return _call("__image", "info", self._token)

    @property
    def width(self):
        return int(self.info()["width"])

    @property
    def height(self):
        return int(self.info()["height"])

    def size(self):
        info = self.info()
        return int(info["width"]), int(info["height"])

    def resize(self, width, height, resample="bilinear"):
        return _ImageHandle(_call("__image", "resize", self._token, int(width), int(height), resample))

    def quantize_monitor(self, dither=True):
        return _ImageHandle(_call("__image", "quantize_monitor", self._token, bool(dither)))

    def close(self):
        _call("__image", "close", self._token)


class _ImageModule(_types.ModuleType):
    def __init__(self):
        super().__init__("image")

    def __repr__(self):
        return "<cc module 'image'>"

    def open(self, path):
        return _ImageHandle(_call("__image", "open", path))

    def load_url(self, url, headers=None, timeout=10):
        return _ImageHandle(_call("__image", "load_url", url, headers or {}, int(timeout)))

    def loadUrl(self, url, headers=None, timeout=10):
        return self.load_url(url, headers, timeout)


class _MonitorGraphicsModule(_types.ModuleType):
    def __init__(self):
        super().__init__("monitorgfx")

    def __repr__(self):
        return "<cc module 'monitorgfx'>"

    def size(self, target):
        width, height = _call("__monitor_gfx", "size", _monitor_side(target))
        return int(width), int(height)

    def clear(self, target, color=None):
        if color is None:
            color = colors.black
        return _call("__monitor_gfx", "clear", _monitor_side(target), int(color))

    def disable(self, target):
        return _call("__monitor_gfx", "disable", _monitor_side(target))

    def set_pixel(self, target, x, y, color=None):
        if color is None:
            color = colors.black
        return _call("__monitor_gfx", "set_pixel", _monitor_side(target), int(x), int(y), int(color))

    def setPixel(self, target, x, y, color=None):
        return self.set_pixel(target, x, y, color)

    def draw(self, target, image, x=1, y=1, clear=False):
        if not isinstance(image, _ImageHandle):
            raise TypeError("image must be created by cc.image")
        return _call("__monitor_gfx", "draw_image", _monitor_side(target), image._token, int(x), int(y), bool(clear))

    def draw_image(self, target, image, x=1, y=1, clear=False):
        return self.draw(target, image, x, y, clear)

    def drawImage(self, target, image, x=1, y=1, clear=False):
        return self.draw(target, image, x, y, clear)


class _MidiParseError(ValueError):
    pass


class _MidiReader:
    def __init__(self, data, source="<midi>"):
        self._data = bytes(data)
        self._source = source
        self._pos = 0

    def remaining(self):
        return len(self._data) - self._pos

    def read_u8(self):
        self._require(1)
        value = self._data[self._pos]
        self._pos += 1
        return value

    def read_u16be(self):
        self._require(2)
        value = (self._data[self._pos] << 8) | self._data[self._pos + 1]
        self._pos += 2
        return value

    def read_u32be(self):
        self._require(4)
        value = (
            (self._data[self._pos] << 24)
            | (self._data[self._pos + 1] << 16)
            | (self._data[self._pos + 2] << 8)
            | self._data[self._pos + 3]
        )
        self._pos += 4
        return value

    def read_bytes(self, length):
        length = int(length)
        self._require(length)
        value = self._data[self._pos : self._pos + length]
        self._pos += length
        return value

    def unread(self, count):
        self._pos = max(0, self._pos - int(count))

    def read_vlq(self):
        value = 0
        for _ in range(4):
            byte = self.read_u8()
            value = (value << 7) | (byte & 0x7F)
            if (byte & 0x80) == 0:
                return value
        raise _MidiParseError("Malformed variable-length quantity in %s" % self._source)

    def _require(self, length):
        if self.remaining() < length:
            raise _MidiParseError("Unexpected end of MIDI data in %s" % self._source)


def _midi_read_chunk(reader):
    chunk_type = reader.read_bytes(4)
    chunk_length = reader.read_u32be()
    chunk_data = reader.read_bytes(chunk_length)
    return chunk_type, chunk_data


def _midi_program_to_instrument(channel, program, note=None):
    if int(channel) == 9:
        if note in (49, 52, 55, 57):
            return "cow_bell"
        if note in (42, 44, 46):
            return "hat"
        if note in (38, 40):
            return "snare"
        if note in (35, 36):
            return "basedrum"
        if note in (41, 43, 45, 47, 48, 50):
            return "didgeridoo"
        return "basedrum"

    program = int(program or 0)
    if 0 <= program <= 7:
        return "harp"
    if 8 <= program <= 15:
        return "xylophone" if 8 <= program <= 11 else "bell"
    if 16 <= program <= 23:
        return "pling"
    if 24 <= program <= 31:
        return "guitar" if program <= 27 else "banjo"
    if 32 <= program <= 39:
        return "bass" if program <= 35 else "didgeridoo"
    if 40 <= program <= 47:
        return "flute"
    if 48 <= program <= 55:
        return "chime"
    if 56 <= program <= 63:
        return "didgeridoo"
    if 64 <= program <= 71:
        return "flute"
    if 72 <= program <= 79:
        return "flute"
    if 80 <= program <= 87:
        return "bit"
    if 88 <= program <= 95:
        return "bell"
    if 96 <= program <= 103:
        return "bit"
    if 104 <= program <= 111:
        return "banjo"
    if 112 <= program <= 119:
        return "cow_bell"
    if 120 <= program <= 127:
        return "bit"
    return "harp"


def _midi_velocity_to_volume(velocity, scale=1.0):
    velocity = max(0.0, min(127.0, float(velocity)))
    scale = float(scale)
    return max(0.03, min(3.0, (velocity / 127.0) * scale))


def _midi_note_to_pitch(note, transpose=0):
    return float(int(note) + int(transpose) - 54)


def _midi_note_to_frequency(note, transpose=0):
    return 440.0 * (2.0 ** (((float(note) + float(transpose)) - 69.0) / 12.0))


def _midi_estimate_duration(events, division):
    tempo = 500000
    current_tick = 0
    seconds = 0.0

    for event in events:
        tick = int(event["tick"])
        delta_ticks = tick - current_tick
        if delta_ticks > 0:
            seconds += (delta_ticks * tempo) / (int(division) * 1000000.0)
            current_tick = tick
        if event["kind"] == "tempo":
            tempo = int(event["tempo"])

    return seconds


def _parse_midi_track(data, track_index, sequence):
    reader = _MidiReader(data, "<track %d>" % (track_index + 1))
    absolute_tick = 0
    running_status = None
    events = []
    note_count = 0

    while reader.remaining() > 0:
        absolute_tick += reader.read_vlq()
        status = reader.read_u8()

        if status < 0x80:
            if running_status is None:
                raise _MidiParseError("Running status without previous event in track %d" % (track_index + 1))
            reader.unread(1)
            status = running_status
        elif status < 0xF0:
            running_status = status
        else:
            running_status = None

        if status == 0xFF:
            meta_type = reader.read_u8()
            payload = reader.read_bytes(reader.read_vlq())

            if meta_type == 0x2F:
                break
            if meta_type == 0x51 and len(payload) == 3:
                tempo = (payload[0] << 16) | (payload[1] << 8) | payload[2]
                events.append({
                    "tick": absolute_tick,
                    "priority": 0,
                    "seq": sequence,
                    "kind": "tempo",
                    "tempo": tempo,
                })
                sequence += 1
            continue

        if status in (0xF0, 0xF7):
            reader.read_bytes(reader.read_vlq())
            continue

        event_type = status >> 4
        channel = status & 0x0F

        if event_type in (0x8, 0x9, 0xA, 0xB, 0xE):
            first = reader.read_u8()
            second = reader.read_u8()
            if event_type == 0x9 and second > 0:
                events.append({
                    "tick": absolute_tick,
                    "priority": 2,
                    "seq": sequence,
                    "kind": "note_on",
                    "channel": channel,
                    "note": first,
                    "velocity": second,
                })
                sequence += 1
                note_count += 1
            elif event_type == 0x8 or (event_type == 0x9 and second == 0):
                events.append({
                    "tick": absolute_tick,
                    "priority": 3,
                    "seq": sequence,
                    "kind": "note_off",
                    "channel": channel,
                    "note": first,
                    "velocity": second,
                })
                sequence += 1
            continue

        if event_type in (0xC, 0xD):
            first = reader.read_u8()
            if event_type == 0xC:
                events.append({
                    "tick": absolute_tick,
                    "priority": 1,
                    "seq": sequence,
                    "kind": "program",
                    "channel": channel,
                    "program": first,
                })
                sequence += 1
            continue

        raise _MidiParseError("Unsupported MIDI event 0x%02X in track %d" % (status, track_index + 1))

    return events, note_count, sequence


def _parse_midi_file(path):
    with fs.open(path, "rb") as handle:
        data = handle.read()

    if not data:
        raise _MidiParseError("MIDI file is empty: %s" % path)
    if not isinstance(data, (bytes, bytearray)):
        raise _MidiParseError("MIDI file did not load as bytes: %s" % path)

    reader = _MidiReader(data, path)
    chunk_type, header_data = _midi_read_chunk(reader)
    if chunk_type != b"MThd":
        raise _MidiParseError("Expected MThd header in %s" % path)

    header = _MidiReader(header_data[:6], path)
    format_type = header.read_u16be()
    track_count = header.read_u16be()
    division = header.read_u16be()

    if division & 0x8000:
        raise _MidiParseError("SMPTE MIDI time division is not supported yet")

    events = []
    note_count = 0
    sequence = 0

    for track_index in range(track_count):
        chunk_type, track_data = _midi_read_chunk(reader)
        if chunk_type != b"MTrk":
            raise _MidiParseError("Expected MTrk chunk #%d in %s" % (track_index + 1, path))

        track_events, track_notes, sequence = _parse_midi_track(track_data, track_index, sequence)
        events.extend(track_events)
        note_count += track_notes

    events.sort(key=lambda event: (event["tick"], event["priority"], event["seq"]))

    return _MidiSong(
        path=path,
        format_type=format_type,
        track_count=track_count,
        division=division,
        events=events,
        note_count=note_count,
        duration_seconds=_midi_estimate_duration(events, division),
    )


def _midi_resolve_speaker(target):
    if isinstance(target, str):
        if _peripheral_type(target) != "speaker":
            raise ValueError("No such speaker: %s" % target)
        return _PeripheralProxy(target)

    side = getattr(target, "_side", None)
    if isinstance(side, str):
        if _peripheral_type(side) != "speaker":
            raise ValueError("Peripheral %s is not a speaker" % side)
        return target

    raise TypeError("speaker target must be a side string or wrapped speaker peripheral")


def _midi_coerce_song(value):
    if isinstance(value, _MidiSong):
        return value
    if isinstance(value, str):
        return _parse_midi_file(value)
    raise TypeError("song must be a MIDI handle from cc.midi.open(...) or a path string")


def _midi_play_song_notes(song, target, tempo_scale=1.0, volume=1.0, transpose=0):
    song = _midi_coerce_song(song)
    speaker = _midi_resolve_speaker(target)
    tempo_scale = float(tempo_scale)
    if tempo_scale <= 0:
        raise ValueError("tempo_scale must be > 0")

    volume = float(volume)
    transpose = int(transpose)
    current_tick = 0
    tempo = 500000
    programs = {}
    attempted = 0
    played = 0
    spill_ticks = 0
    spill_notes = 0

    for event in song._events:
        tick = int(event["tick"])
        delta_ticks = tick - current_tick
        if delta_ticks > 0:
            delay = (delta_ticks * tempo) / (song._division * 1000000.0 * tempo_scale)
            if delay > 0:
                sleep(delay)
            current_tick = tick

        kind = event["kind"]
        if kind == "tempo":
            tempo = int(event["tempo"])
            continue
        if kind == "program":
            programs[int(event["channel"])] = int(event["program"])
            continue
        if kind != "note_on":
            continue

        instrument = _midi_program_to_instrument(
            event["channel"],
            programs.get(int(event["channel"]), 0),
            event["note"],
        )
        note_pitch = _midi_note_to_pitch(event["note"], transpose)
        note_volume = _midi_velocity_to_volume(event["velocity"], volume)

        attempted += 1
        first_try = True
        while not speaker.play_note(instrument, note_volume, note_pitch):
            first_try = False
            spill_notes += 1
            spill_ticks += 1
            sleep(0.05)
        played += 1

    return {
        "path": song._path,
        "format": song._format_type,
        "tracks": song._track_count,
        "notes": song._note_count,
        "played": played,
        "attempted": attempted,
        "duration": song._duration_seconds,
        "spill_notes": spill_notes,
        "spill_ticks": spill_ticks,
    }


def _midi_audio_voice(channel, program, note=None):
    if int(channel) == 9:
        if note in (49, 52, 55, 57):
            return "drum_cymbal"
        if note in (42, 44, 46):
            return "drum_hat"
        if note in (38, 40):
            return "drum_snare"
        if note in (35, 36):
            return "drum_kick"
        return "drum_tom"

    program = int(program or 0)
    if 0 <= program <= 7:
        return "piano"
    if 8 <= program <= 15:
        return "mallet"
    if 16 <= program <= 23:
        return "organ"
    if 24 <= program <= 31:
        return "guitar"
    if 32 <= program <= 39:
        return "bass"
    if 40 <= program <= 47:
        return "strings"
    if 48 <= program <= 55:
        return "choir"
    if 56 <= program <= 63:
        return "brass"
    if 64 <= program <= 71:
        return "reed"
    if 72 <= program <= 79:
        return "flute"
    if 80 <= program <= 87:
        return "lead"
    if 88 <= program <= 95:
        return "pad"
    if 96 <= program <= 103:
        return "synthfx"
    if 104 <= program <= 111:
        return "pluck"
    if 112 <= program <= 119:
        return "mallet"
    if 120 <= program <= 127:
        return "bit"
    return "piano"


def _midi_build_audio_notes(song, tempo_scale=1.0, transpose=0, volume=1.0):
    song = _midi_coerce_song(song)
    tempo_scale = float(tempo_scale)
    if tempo_scale <= 0:
        raise ValueError("tempo_scale must be > 0")

    current_tick = 0
    current_time = 0.0
    tempo = 500000
    programs = {}
    active = {}
    notes = []

    for event in song._events:
        tick = int(event["tick"])
        delta_ticks = tick - current_tick
        if delta_ticks > 0:
            current_time += (delta_ticks * tempo) / (song._division * 1000000.0 * tempo_scale)
            current_tick = tick

        kind = event["kind"]
        if kind == "tempo":
            tempo = int(event["tempo"])
            continue
        if kind == "program":
            programs[int(event["channel"])] = int(event["program"])
            continue

        key = (int(event["channel"]), int(event["note"]))
        if kind == "note_on":
            active.setdefault(key, []).append({
                "channel": int(event["channel"]),
                "note": int(event["note"]),
                "velocity": int(event["velocity"]),
                "program": int(programs.get(int(event["channel"]), 0)),
                "start": current_time,
            })
            continue
        if kind != "note_off":
            continue

        stack = active.get(key)
        if not stack:
            continue

        started = stack.pop()
        end_time = max(current_time, started["start"] + 0.02)
        notes.append({
            "channel": started["channel"],
            "note": started["note"],
            "velocity": started["velocity"],
            "program": started["program"],
            "voice": _midi_audio_voice(started["channel"], started["program"], started["note"]),
            "frequency": _midi_note_to_frequency(started["note"], transpose),
            "start": started["start"],
            "end": end_time,
            "gain": max(0.0, min(2.5, (started["velocity"] / 127.0) * float(volume))),
        })

    final_time = max(current_time, float(song._duration_seconds) / tempo_scale)
    for stack in active.values():
        for started in stack:
            end_time = max(final_time, started["start"] + 0.12)
            notes.append({
                "channel": started["channel"],
                "note": started["note"],
                "velocity": started["velocity"],
                "program": started["program"],
                "voice": _midi_audio_voice(started["channel"], started["program"], started["note"]),
                "frequency": _midi_note_to_frequency(started["note"], transpose),
                "start": started["start"],
                "end": end_time,
                "gain": max(0.0, min(2.5, (started["velocity"] / 127.0) * float(volume))),
            })

    notes.sort(key=lambda item: item["start"])
    return notes, max(final_time, max((note["end"] for note in notes), default=0.0))


def _midi_wave_sample(note, sample_time, sample_index):
    start = float(note["start"])
    end = float(note["end"])
    rel = sample_time - start
    duration = max(0.02, end - start)
    voice = note["voice"]
    gain = float(note["gain"])

    attack = min(0.008, duration * 0.2)
    release = min(0.08, duration * 0.45)
    if release >= duration:
        release = duration * 0.5

    if attack > 0.0 and rel < attack:
        envelope = rel / attack
    elif rel > duration - release:
        envelope = max(0.0, (duration - rel) / max(release, 1e-6))
    else:
        envelope = 1.0

    phase = float(note["frequency"]) * rel
    frac = phase - int(phase)

    if voice == "sine":
        wave = _math.sin(2.0 * _math.pi * phase)
    elif voice == "square":
        wave = 1.0 if frac < 0.5 else -1.0
    elif voice == "saw":
        wave = (2.0 * frac) - 1.0
    elif voice == "triangle":
        wave = 1.0 - (4.0 * abs(frac - 0.5))
    elif voice == "bell":
        fade = _math.exp(-3.5 * rel)
        wave = (
            _math.sin(2.0 * _math.pi * phase)
            + 0.5 * _math.sin(4.0 * _math.pi * phase)
            + 0.2 * _math.sin(6.0 * _math.pi * phase)
        ) * fade
    elif voice == "noise":
        seed = (sample_index + 1) * (int(note["note"]) + 17) * 12.9898
        noise = _math.sin(seed) * 43758.5453
        wave = ((noise - _math.floor(noise)) * 2.0) - 1.0
        envelope *= _math.exp(-10.0 * rel)
    else:
        wave = _math.sin(2.0 * _math.pi * phase)

    return wave * gain * envelope


def _midi_soft_clip(sample):
    sample = float(sample)
    if -1.0 <= sample <= 1.0:
        return sample
    if sample > 0:
        return sample / (1.0 + sample)
    return sample / (1.0 - sample)


def _midi_play_song_audio(song, target, tempo_scale=1.0, volume=1.0, transpose=0, soundfont=None):
    song = _midi_coerce_song(song)
    speaker = _midi_resolve_speaker(target)

    soundfont_value = "" if soundfont in (None, "") else str(soundfont)
    result = _call(
        "__midi",
        "play_soundfont_song",
        speaker._side,
        song._path,
        float(tempo_scale),
        float(volume),
        int(transpose),
        soundfont_value,
    )

    total_duration = float(song._duration_seconds) / max(0.001, float(tempo_scale))
    if not isinstance(result, dict):
        result = {}
    result["engine"] = "soundfont"
    result.setdefault("requested_soundfont", soundfont_value or "default")

    if not isinstance(result, dict):
        result = {}
    result["path"] = song._path
    result["format"] = song._format_type
    result["tracks"] = song._track_count
    result["notes"] = song._note_count
    result["duration"] = total_duration
    result["mode"] = "audio"
    return result


def _midi_play_song_hifi(song, target, tempo_scale=1.0, volume=1.0, transpose=0, soundfont=None):
    song = _midi_coerce_song(song)
    speaker = _midi_resolve_speaker(target)

    soundfont_value = "" if soundfont in (None, "") else str(soundfont)
    result = _call(
        "__midi",
        "play_hifi_soundfont_song",
        speaker._side,
        song._path,
        float(tempo_scale),
        float(volume),
        int(transpose),
        soundfont_value,
    )

    total_duration = float(song._duration_seconds) / max(0.001, float(tempo_scale))
    if not isinstance(result, dict):
        result = {}
    result["engine"] = "soundfont_hifi"
    result.setdefault("requested_soundfont", soundfont_value or "default")
    result["path"] = song._path
    result["format"] = song._format_type
    result["tracks"] = song._track_count
    result["notes"] = song._note_count
    result["duration"] = total_duration
    result["mode"] = "hifi"
    return result


def _midi_play_song(song, target, tempo_scale=1.0, volume=1.0, transpose=0, mode="notes", soundfont=None):
    mode = str(mode or "notes").lower()
    if mode in ("notes", "note"):
        return _midi_play_song_notes(song, target, tempo_scale=tempo_scale, volume=volume, transpose=transpose)
    if mode == "audio":
        return _midi_play_song_audio(song, target, tempo_scale=tempo_scale, volume=volume, transpose=transpose, soundfont=soundfont)
    if mode in ("hifi", "audio16", "pcm16"):
        return _midi_play_song_hifi(song, target, tempo_scale=tempo_scale, volume=volume, transpose=transpose, soundfont=soundfont)
    raise ValueError("Unsupported midi mode: %s" % mode)


class _MidiSong:
    def __init__(self, path, format_type, track_count, division, events, note_count, duration_seconds):
        self._path = path
        self._format_type = int(format_type)
        self._track_count = int(track_count)
        self._division = int(division)
        self._events = list(events)
        self._note_count = int(note_count)
        self._duration_seconds = float(duration_seconds)

    def __repr__(self):
        return "<cc midi %s tracks=%d notes=%d duration=%.2fs>" % (
            self._path,
            self._track_count,
            self._note_count,
            self._duration_seconds,
        )

    def info(self):
        return {
            "path": self._path,
            "format": self._format_type,
            "tracks": self._track_count,
            "division": self._division,
            "notes": self._note_count,
            "events": len(self._events),
            "duration": self._duration_seconds,
        }

    def play(self, speaker, tempo_scale=1.0, volume=1.0, transpose=0, mode="notes", soundfont=None):
        return _midi_play_song(
            self,
            speaker,
            tempo_scale=tempo_scale,
            volume=volume,
            transpose=transpose,
            mode=mode,
            soundfont=soundfont,
        )


class _MidiModule(_types.ModuleType):
    def __init__(self):
        super().__init__("midi")

    def __repr__(self):
        return "<cc module 'midi'>"

    def open(self, path):
        return _parse_midi_file(path)

    def load(self, path):
        return self.open(path)

    def list_soundfonts(self):
        result = _call("__midi", "list_soundfonts")
        return list(result or [])

    def soundfonts(self):
        return self.list_soundfonts()

    def play(self, target, song, tempo_scale=1.0, volume=1.0, transpose=0, mode="notes", soundfont=None):
        return _midi_play_song(
            song,
            target,
            tempo_scale=tempo_scale,
            volume=volume,
            transpose=transpose,
            mode=mode,
            soundfont=soundfont,
        )


def _next_parallel_event():
    if _SYNTHETIC_EVENTS:
        return _SYNTHETIC_EVENTS.pop(0)
    return _rednet_process_event(_host_pull_event(True))


def _coerce_parallel_runnable(value):
    if hasattr(value, "__await__"):
        return value.__await__()
    if hasattr(value, "__next__") and hasattr(value, "send") and hasattr(value, "throw"):
        return value
    return None


class _ParallelRequest:
    def __await__(self):
        result = yield self
        return result

    __iter__ = __await__

    def matches(self, event):
        return False

    def resume(self, event):
        return event

    def cancel(self):
        return None


class _ParallelEventRequest(_ParallelRequest):
    def __init__(self, filter_name=None, raw=False):
        self.filter_name = filter_name
        self.raw = bool(raw)

    def matches(self, event):
        if not isinstance(event, tuple) or not event:
            return self.filter_name is None
        name = event[0]
        return name == "terminate" or self.filter_name is None or name == self.filter_name

    def resume(self, event):
        if (
            not self.raw
            and isinstance(event, tuple)
            and event
            and event[0] == "terminate"
        ):
            raise RuntimeError("Terminated")
        return event


class _ParallelTimerRequest(_ParallelRequest):
    def __init__(self, seconds):
        self.timer_id = _call("os", "start_timer", seconds)
        self._closed = False

    def matches(self, event):
        if not isinstance(event, tuple) or not event:
            return False
        if event[0] == "terminate":
            return True
        return event[0] == "timer" and len(event) >= 2 and event[1] == self.timer_id

    def resume(self, event):
        if event[0] == "terminate":
            self.cancel()
            raise RuntimeError("Terminated")
        self.cancel()
        return None

    def cancel(self):
        if not self._closed:
            self._closed = True
            try:
                _call("os", "cancel_timer", self.timer_id)
            except Exception:
                pass


class _ParallelRednetReceiveRequest(_ParallelRequest):
    def __init__(self, timeout=None, protocol=None):
        self.protocol = protocol
        self.timer_id = _call("os", "start_timer", timeout) if timeout is not None else None
        self._closed = False

    def matches(self, event):
        if not isinstance(event, tuple) or not event:
            return False
        name = event[0]
        if name == "terminate":
            return True
        if name == "rednet_message":
            if self.protocol is None:
                return True
            return len(event) >= 4 and event[3] == self.protocol
        return name == "timer" and self.timer_id is not None and len(event) >= 2 and event[1] == self.timer_id

    def resume(self, event):
        if event[0] == "terminate":
            self.cancel()
            raise RuntimeError("Terminated")
        if event[0] == "timer":
            self.cancel()
            return None
        self.cancel()
        return (event[1], event[2], event[3])

    def cancel(self):
        if not self._closed and self.timer_id is not None:
            self._closed = True
            try:
                _call("os", "cancel_timer", self.timer_id)
            except Exception:
                pass
        else:
            self._closed = True


class _ParallelTask:
    def __init__(self, index, function):
        self.index = index
        self.function = function
        self.coroutine = None
        self.request = None
        self.done = False
        self.result = None
        self.exception = None
        self._started = False

    def start(self):
        try:
            value = self.function()
            coroutine = _coerce_parallel_runnable(value)
            if coroutine is None:
                self.done = True
                self.result = value
                return
            self.coroutine = coroutine
            self._advance()
        except BaseException as exc:
            self.done = True
            self.exception = exc

    def _advance(self, send_value=None, throw_value=None):
        previous = self.request
        self.request = None
        try:
            if throw_value is not None:
                yielded = self.coroutine.throw(throw_value)
            elif not self._started:
                self._started = True
                yielded = next(self.coroutine)
            else:
                yielded = self.coroutine.send(send_value)
        except StopIteration as stop:
            self.done = True
            self.result = stop.value
            if previous is not None:
                previous.cancel()
            return
        except BaseException as exc:
            self.done = True
            self.exception = exc
            if previous is not None:
                previous.cancel()
            return

        request = yielded if isinstance(yielded, _ParallelRequest) else None
        if request is None:
            self.done = True
            self.exception = TypeError(
                "parallel tasks must yield from or await cc.parallel helpers"
            )
            if previous is not None:
                previous.cancel()
            return
        if previous is not None:
            previous.cancel()
        self.request = request

    def resume_from_event(self, event):
        if self.request is None:
            return
        request = self.request
        try:
            payload = request.resume(event)
            self._advance(send_value=payload)
        except BaseException as exc:
            self._advance(throw_value=exc)

    def close(self):
        if self.request is not None:
            self.request.cancel()
            self.request = None


class _ParallelModule(_types.ModuleType):
    def __init__(self):
        super().__init__("parallel")

    def __repr__(self):
        return "<cc module 'parallel'>"

    def sleep(self, seconds):
        return _ParallelTimerRequest(seconds)

    def pull_event(self, filter_name=None):
        return _ParallelEventRequest(filter_name, raw=False)

    def pull_event_raw(self, filter_name=None):
        return _ParallelEventRequest(filter_name, raw=True)

    def receive(self, timeout=None, protocol=None):
        return _ParallelRednetReceiveRequest(timeout, protocol)

    def wait_for_any(self, *functions):
        return self._run(functions, stop_after_first=True)

    def waitForAny(self, *functions):
        return self.wait_for_any(*functions)

    def wait_for_all(self, *functions):
        return self._run(functions, stop_after_first=False)

    def waitForAll(self, *functions):
        return self.wait_for_all(*functions)

    def _run(self, functions, stop_after_first):
        tasks = []
        for index, fn in enumerate(functions, start=1):
            if not callable(fn):
                raise TypeError(f"bad argument #{index} (function expected, got {type(fn).__name__})")
            task = _ParallelTask(index, fn)
            task.start()
            if task.exception is not None:
                for other in tasks:
                    other.close()
                raise task.exception
            tasks.append(task)
            if stop_after_first and task.done:
                for other in tasks:
                    if other is not task:
                        other.close()
                return task.index

        if not tasks:
            return 0

        while True:
            alive = [task for task in tasks if not task.done]
            if not alive:
                return 0

            event = _next_parallel_event()
            matched = [task for task in alive if task.request is not None and task.request.matches(event)]
            last_completed = None

            for task in matched:
                task.resume_from_event(event)
                if task.exception is not None:
                    for other in tasks:
                        if other is not task:
                            other.close()
                    raise task.exception
                if task.done:
                    last_completed = task.index
                    if stop_after_first:
                        for other in tasks:
                            if other is not task:
                                other.close()
                        return task.index

            if not stop_after_first and all(task.done for task in tasks):
                return 0 if last_completed is None else last_completed


class _OSModule(_LuaProxyModule):
    def sleep(self, seconds):
        return _call("__global", "sleep", seconds)

    def pull_event(self, filter_name=None):
        return _pull_event_impl(raw=False, filter_name=filter_name)

    def pull_event_raw(self, filter_name=None):
        return _pull_event_impl(raw=True, filter_name=filter_name)


term = _LuaProxyModule("term", "term")
turtle = _LuaProxyModule("turtle", "turtle")
redstone = _LuaProxyModule("redstone", "redstone")
rednet = _RednetModule("rednet")
image = _ImageModule()
monitorgfx = _MonitorGraphicsModule()
midi = _MidiModule()
parallel = _ParallelModule()
fs = _FSModule("fs", "fs")
os = _OSModule("os", "os")
peripheral = _PeripheralModule("peripheral", "peripheral")
colors = _ColorsModule("colors")
colours = _ColorsModule("colours", grey_alias=True)
keys = _KeysModule()
paintutils = _PaintutilsModule()
vector = _VectorModule()
textutils = _TextutilsModule()
cc = _types.ModuleType("cc")
cc.__path__ = []
cc.__package__ = "cc"


class _ImportToolsModule(_types.ModuleType):
    def paths(self):
        return list(_sys.path)

    def invalidate_caches(self):
        _sys.path_importer_cache.clear()

    def loaded_modules(self, prefix=None):
        modules = sorted(_sys.modules.keys())
        if prefix is None:
            return modules
        return [module_name for module_name in modules if module_name == prefix or module_name.startswith(prefix + ".")]

    def __repr__(self):
        return "<cc imports helper>"


_BRIDGE_MODULES = {
    "term": term,
    "turtle": turtle,
    "redstone": redstone,
    "rednet": rednet,
    "image": image,
    "monitorgfx": monitorgfx,
    "midi": midi,
    "parallel": parallel,
    "fs": fs,
    "os": os,
    "peripheral": peripheral,
    "colors": colors,
    "colours": colours,
    "keys": keys,
    "paintutils": paintutils,
    "vector": vector,
    "textutils": textutils,
}

_CC_NAMESPACE_EXPORTS = {
    "term": term,
    "turtle": turtle,
    "redstone": redstone,
    "rednet": rednet,
    "image": image,
    "monitorgfx": monitorgfx,
    "midi": midi,
    "parallel": parallel,
    "fs": fs,
    "os": os,
    "peripheral": peripheral,
    "colors": colors,
    "colours": colours,
    "keys": keys,
    "paintutils": paintutils,
    "vector": vector,
    "textutils": textutils,
}
imports = _ImportToolsModule("cc.imports")
_CC_NAMESPACE_EXPORTS["imports"] = imports


def _snapshot_module(module):
    return dict(module.__dict__)


def _restore_module(module, snapshot):
    module_dict = module.__dict__
    for key in list(module_dict.keys()):
        if key not in snapshot:
            del module_dict[key]
    module_dict.update(snapshot)


_BRIDGE_MODULE_BASELINES = {
    module_name: _snapshot_module(module)
    for module_name, module in _BRIDGE_MODULES.items()
}
_CC_MODULE_BASELINE = _snapshot_module(cc)
_BASE_SYS_MODULES = set(_sys.modules.keys())


class _BridgeLoader(_abc.Loader):
    def __init__(self, bridge_module):
        self._bridge_module = bridge_module

    def create_module(self, spec):
        return None

    def exec_module(self, module):
        module.__dict__.update(self._bridge_module.__dict__)


class _CCLoader(_abc.Loader):
    def __init__(self, resolved):
        self._resolved = resolved

    def create_module(self, spec):
        return None

    def exec_module(self, module):
        if self._resolved.get("namespace"):
            module.__file__ = None
            module.__path__ = [self._resolved["package_path"]]
            module.__package__ = module.__name__
            return

        source = _decode_terminal_text(_call("__fs", "read_all", self._resolved["path"]))
        module.__file__ = self._resolved["path"]
        if self._resolved.get("package"):
            module.__path__ = [self._resolved["package_path"]]
            module.__package__ = module.__name__
        else:
            module.__package__ = module.__name__.rpartition(".")[0]
        exec(compile(source, self._resolved["path"], "exec"), module.__dict__, module.__dict__)


class _CCFinder(_abc.MetaPathFinder):
    def resolve(self, module_name, search_paths):
        if not _is_valid_module_name(module_name):
            return None
        return _call("__fs", "resolve_import", module_name, _sanitize_search_paths(search_paths))

    def find_spec(self, fullname, path=None, target=None):
        if fullname in _BRIDGE_MODULES:
            return _machinery.ModuleSpec(fullname, _BridgeLoader(_BRIDGE_MODULES[fullname]), is_package=False)

        search_paths = list(path) if path is not None else list(_sys.path)
        module_name = fullname.rsplit(".", 1)[-1] if path is not None else fullname
        resolved = self.resolve(module_name, search_paths)
        if not resolved:
            return None
        spec = _machinery.ModuleSpec(fullname, _CCLoader(resolved), is_package=bool(resolved.get("package")))
        if resolved.get("package"):
            spec.submodule_search_locations = [resolved["package_path"]]
            spec.origin = None if resolved.get("namespace") else resolved["path"]
        return spec

    def invalidate_caches(self):
        _sys.path_importer_cache.clear()


_FINDER = _CCFinder()
_ORIGINAL_IMPORT = _builtins.__import__


def _safe_import(name, globals=None, locals=None, fromlist=(), level=0):
    top = name.split(".", 1)[0]
    if top in _BLOCKED:
        raise ImportError("Import '%s' is blocked inside CC Python Runtime" % top)

    if top == "cc" or top in _BRIDGE_MODULES or top in _ALLOWED_STDLIB or level > 0:
        return _ORIGINAL_IMPORT(name, globals, locals, fromlist, level)

    if _FINDER.find_spec(name) is not None or _FINDER.find_spec(top) is not None:
        return _ORIGINAL_IMPORT(name, globals, locals, fromlist, level)

    raise ImportError("Import '%s' is not available in the CC Python sandbox" % top)


def _terminal_newline():
    _, y_pos = term.get_cursor_pos()
    _, height = term.get_size()
    y_pos = int(y_pos)
    height = int(height)
    if y_pos >= height:
        term.scroll(1)
        y_pos = height
    else:
        y_pos += 1
    term.set_cursor_pos(1, int(y_pos))


def _terminal_write_text(text):
    if text is None:
        return None

    text = str(text)
    if not text:
        return None

    parts = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    for index, part in enumerate(parts):
        remaining = part
        while remaining:
            x_pos, _ = term.get_cursor_pos()
            width, _ = term.get_size()
            x_pos = int(x_pos)
            width = int(width)
            available = max(1, width - x_pos + 1)
            chunk = remaining[:available]
            term.write(chunk)
            remaining = remaining[available:]
            if remaining:
                _terminal_newline()

        if index != len(parts) - 1:
            _terminal_newline()

    return None


def print(*values, sep=" ", end="\n"):
    _terminal_write_text(sep.join(str(value) for value in values) + end)


def input(prompt=""):
    if prompt:
        _terminal_write_text(prompt)
    value = _call("__global", "read")
    if value is None:
        return ""
    return _decode_terminal_text(value)


def sleep(seconds):
    return os.sleep(seconds)


def open(path, mode="r"):
    return fs.open(path, mode)


def _is_internal_frame(frame):
    filename = frame.filename or ""
    return filename.endswith("ccpython_bootstrap.py") and frame.name in _INTERNAL_TRACE_FUNCTIONS


def _format_exception_text(exception=None):
    if exception is None:
        exception = _sys.exc_info()[1]
    if exception is None:
        return "Unknown Python error\n"

    traceback_exception = _traceback.TracebackException.from_exception(exception)
    filtered_frames = [frame for frame in traceback_exception.stack if not _is_internal_frame(frame)]
    if not filtered_frames:
        filtered_frames = list(traceback_exception.stack)

    parts = []
    if filtered_frames:
        parts.append("Traceback (most recent call last):\n")
        parts.extend(_traceback.format_list(filtered_frames))
    parts.extend(traceback_exception.format_exception_only())
    text = "".join(parts)
    return text if text.endswith("\n") else text + "\n"


def exit(code=None):
    raise SystemExit(code)


def quit(code=None):
    raise SystemExit(code)


def help(topic=None):
    if topic is None:
        print("CC Python Runtime help")
        print("  help(cc)            namespace overview")
        print("  help(cc.fs)         file helpers")
        print("  help(cc.peripheral) peripheral helpers")
        print("  help(cc.rednet)     rednet helpers")
        print("  help(cc.image)      image loading and processing")
        print("  help(cc.monitorgfx) hi-res monitor graphics")
        print("  help(cc.midi)       MIDI parsing and speaker playback")
        print("  help(cc.colors)     colour constants and helpers")
        print("  help(cc.keys)       keyboard constants")
        print("  help(cc.paintutils) drawing helpers")
        print("  help(cc.parallel)   cooperative task helpers")
        print("  help(cc.vector)     3D vector helpers")
        print("  help(cc.textutils)  formatting and serialisation helpers")
        print("  help(cc.imports)    import debugging")
        print("  exit() or quit()    leave the REPL")
        return None

    if topic is cc:
        print("cc namespace")
        print("  cc.fs           file helpers and CraftOS filesystem access")
        print("  cc.os           event and computer helpers")
        print("  cc.term         terminal helpers")
        print("  cc.peripheral   peripheral discovery and wrappers")
        print("  cc.rednet       networking helpers")
        print("  cc.image        image loading and processing helpers")
        print("  cc.monitorgfx   hi-res monitor graphics helpers")
        print("  cc.midi         MIDI parsing and speaker playback")
        print("  cc.redstone     redstone helpers")
        print("  cc.colors       colour constants and bitmask helpers")
        print("  cc.keys         keyboard constants")
        print("  cc.paintutils   drawing helpers")
        print("  cc.parallel     cooperative task helpers")
        print("  cc.vector       3D vector helpers")
        print("  cc.textutils    formatting and serialisation helpers")
        print("  cc.imports      inspect sys.path and loaded modules")
        print("  cc.sleep/open/print/input")
        return None

    module_name = getattr(topic, "__name__", None)
    if module_name in ("fs", "cc.fs"):
        print("cc.fs helpers")
        print("  read_text(path), write_text(path, data), append_text(path, data)")
        print("  read_lines(path), write_lines(path, lines)")
        print("  read_json(path), write_json(path, value)")
        print("  join(*parts), open(path, mode)")
        return None
    if module_name in ("peripheral", "cc.peripheral"):
        print("cc.peripheral helpers")
        print("  names(type=None), list(type=None), types()")
        print("  wrap(side), wrap_all(type=None)")
        print("  find(type), find_all(type)")
        return None
    if module_name in ("rednet", "cc.rednet"):
        print("cc.rednet helpers")
        print("  send(id, message, protocol=None)")
        print("  broadcast(message, protocol=None)")
        print("  receive(timeout=None, protocol=None)")
        print("  send_json(...), broadcast_json(...), receive_json(...)")
        return None
    if module_name in ("image", "cc.image"):
        print("cc.image helpers")
        print("  open(path)")
        print("  load_url(url, headers=None, timeout=10)")
        print("  image.size(), image.resize(width, height), image.quantize_monitor(dither=True)")
        return None
    if module_name in ("monitorgfx", "cc.monitorgfx"):
        print("cc.monitorgfx helpers")
        print("  size(target), clear(target, color=colors.black), disable(target)")
        print("  set_pixel(target, x, y, color)")
        print("  draw(target, image, x=1, y=1, clear=False)")
        return None
    if module_name in ("midi", "cc.midi"):
        print("cc.midi helpers")
        print("  open(path), load(path)")
        print("  song.info(), song.play(speaker, tempo_scale=1.0, volume=1.0, transpose=0)")
        print("  play(speaker, song_or_path, tempo_scale=1.0, volume=1.0, transpose=0)")
        return None
    if module_name in ("colors", "colours", "cc.colors", "cc.colours"):
        print("cc.colors helpers")
        print("  constants: white .. black")
        print("  combine(...), subtract(base, ...), test(mask, color)")
        print("  pack_rgb(r, g, b), unpack_rgb(rgb), to_blit(color), from_blit(hex)")
        return None
    if module_name in ("keys", "cc.keys"):
        print("cc.keys helpers")
        print("  constants: enter, backspace, left, right, up, down, f1..f25, etc.")
        print("  get_name(code)")
        return None
    if module_name in ("paintutils", "cc.paintutils"):
        print("cc.paintutils helpers")
        print("  parse_image(text), load_image(path)")
        print("  draw_pixel, draw_line, draw_box, draw_filled_box, draw_image")
        return None
    if module_name in ("parallel", "cc.parallel"):
        print("cc.parallel helpers")
        print("  wait_for_any(*functions), wait_for_all(*functions)")
        print("  use async def or generators with await/yield from cc.parallel.sleep/pull_event/receive")
        return None
    if module_name in ("vector", "cc.vector"):
        print("cc.vector helpers")
        print("  new(x=0, y=0, z=0)")
        print("  vector objects support +, -, *, / and dot/cross/length/normalize/round")
        return None
    if module_name in ("textutils", "cc.textutils"):
        print("cc.textutils helpers")
        print("  slow_write/slow_print, format_time, paged_print")
        print("  serialise, unserialise, serialise_json, unserialise_json")
        print("  url_encode, complete")
        return None
    if module_name == "cc.imports":
        print("cc.imports helpers")
        print("  paths()               current module search paths")
        print("  loaded_modules(prefix=None)")
        print("  invalidate_caches()")
        return None
    if isinstance(topic, _PeripheralProxy):
        print("Peripheral proxy for side:", topic._side)
        print("Call methods directly, for example peripheral.list() or proxy.get_size()")
        return None
    if isinstance(topic, _CCFile):
        print("CC file handle")
        print("  read(), readline(), write(value), writeline(value), flush(), seek(), close()")
        return None

    print("type:", type(topic).__name__)
    print(repr(topic))
    return None


def _reset_bridge_modules():
    for module_name, module in _BRIDGE_MODULES.items():
        _restore_module(module, _BRIDGE_MODULE_BASELINES[module_name])
    _restore_module(cc, _CC_MODULE_BASELINE)


def _purge_dynamic_modules():
    preserved = set(_BASE_SYS_MODULES)
    preserved.update(_BRIDGE_MODULES.keys())
    preserved.add("cc")
    preserved.update("cc." + module_name for module_name in _CC_NAMESPACE_EXPORTS.keys())

    for module_name in list(_sys.modules.keys()):
        top = module_name.split(".", 1)[0]
        if module_name in preserved or top in _ALLOWED_STDLIB:
            continue
        del _sys.modules[module_name]

    _sys.path_importer_cache.clear()


def _install_environment(search_path):
    _sys.path = [path for path in search_path if path]
    for module_name, module in _BRIDGE_MODULES.items():
        _sys.modules[module_name] = module
    _sys.modules["cc"] = cc
    for module_name, module in _CC_NAMESPACE_EXPORTS.items():
        setattr(cc, module_name, module)
        _sys.modules["cc." + module_name] = module
    cc.sleep = sleep
    cc.open = open
    cc.print = print
    cc.input = input
    cc.help = help
    cc.exit = exit
    cc.quit = quit
    cc.imports = imports
    _sys.modules["cc.imports"] = imports
    if _FINDER not in _sys.meta_path:
        _sys.meta_path.insert(0, _FINDER)

    _builtins.__import__ = _safe_import
    _builtins.print = print
    _builtins.input = input
    _builtins.sleep = sleep
    _builtins.open = open
    _builtins.help = help
    _builtins.exit = exit
    _builtins.quit = quit


def _reset_runtime_environment(search_path):
    _purge_dynamic_modules()
    _reset_bridge_modules()
    _install_environment(search_path)


def _base_scope():
    return {
        "__name__": "__main__",
        "__package__": None,
        "__builtins__": _builtins,
        "cc": cc,
        "term": term,
        "turtle": turtle,
        "redstone": redstone,
        "rednet": rednet,
        "image": image,
        "monitorgfx": monitorgfx,
        "midi": midi,
        "parallel": parallel,
        "fs": fs,
        "os": os,
        "peripheral": peripheral,
        "colors": colors,
        "colours": colours,
        "keys": keys,
        "paintutils": paintutils,
        "vector": vector,
        "textutils": textutils,
        "sleep": sleep,
        "open": open,
        "print": print,
        "input": input,
        "help": help,
        "exit": exit,
        "quit": quit,
    }


def _run_script(program_path):
    source = _decode_terminal_text(_call("__fs", "read_all", program_path))
    globals_dict = _base_scope()
    globals_dict["__file__"] = program_path
    exec(compile(source, program_path, "exec"), globals_dict, globals_dict)
    return None


def _run_repl():
    scope = _base_scope()
    buffer = []
    print("CC Python Runtime REPL")
    print("Server-side GraalPy sandbox with CraftOS bridge")
    print("Type help() for tips, exit() to leave.")

    while True:
        prompt = "... " if buffer else ">>> "
        line = input(prompt)
        if line is None:
            break

        source = "\n".join(buffer + [line])
        try:
            compiled = _codeop.compile_command(source, "<stdin>", "single")
        except Exception:
            _terminal_write_text(_format_exception_text())
            buffer = []
            continue

        if compiled is None:
            buffer.append(line)
            continue

        try:
            exec(compiled, scope, scope)
        except SystemExit:
            break
        except Exception:
            _terminal_write_text(_format_exception_text())
        buffer = []

    return None


def __ccpython_run(program_path, cwd, interactive, argv, search_path):
    _reset_runtime_environment(search_path)
    _sys.argv = ([program_path] if program_path else ["py"]) + list(argv or [])
    if interactive or not program_path:
        return _run_repl()
    return _run_script(program_path)
