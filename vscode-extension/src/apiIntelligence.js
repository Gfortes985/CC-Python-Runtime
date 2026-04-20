"use strict";

const vscode = require("vscode");

const PYTHON_SELECTORS = [
    { language: "python", scheme: "file" },
    { language: "python", scheme: "ccpython" },
    { language: "python", scheme: "untitled" }
];

const COLOR_CONSTANTS = [
    "white", "orange", "magenta", "lightBlue", "yellow", "lime", "pink", "gray",
    "lightGray", "cyan", "purple", "blue", "brown", "green", "red", "black"
];

const COLOURS_EXTRA_CONSTANTS = ["grey", "lightGrey"];

const KEY_CONSTANTS = [
    "space", "apostrophe", "comma", "minus", "period", "slash",
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    "semicolon", "equals",
    "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p",
    "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
    "leftBracket", "backslash", "rightBracket", "grave",
    "enter", "tab", "backspace", "insert", "delete", "right", "left", "down", "up",
    "pageUp", "pageDown", "home", "end",
    "capsLock", "scrollLock", "numLock", "printScreen", "pause",
    "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10", "f11", "f12",
    "f13", "f14", "f15", "f16", "f17", "f18", "f19", "f20", "f21", "f22", "f23", "f24", "f25",
    "numPad0", "numPad1", "numPad2", "numPad3", "numPad4", "numPad5", "numPad6", "numPad7", "numPad8", "numPad9",
    "numPadDecimal", "numPadDivide", "numPadMultiply", "numPadSubtract", "numPadAdd", "numPadEnter", "numPadEqual",
    "leftShift", "leftCtrl", "leftAlt", "leftSuper", "rightShift", "rightCtrl", "rightAlt", "menu", "return_"
];

const UNSUPPORTED_MODULES = new Set([
    "socket",
    "subprocess",
    "multiprocessing",
    "threading",
    "tkinter",
    "asyncio",
    "selectors"
]);

function moduleMember(label, documentation) {
    return {
        label,
        kind: vscode.CompletionItemKind.Module,
        detail: `cc.${label}`,
        documentation
    };
}

function func(label, detail, documentation) {
    return {
        label,
        kind: vscode.CompletionItemKind.Function,
        detail,
        documentation
    };
}

function method(label, detail, documentation) {
    return {
        label,
        kind: vscode.CompletionItemKind.Method,
        detail,
        documentation
    };
}

function property(label, detail, documentation) {
    return {
        label,
        kind: vscode.CompletionItemKind.Property,
        detail,
        documentation
    };
}

function constant(label, documentation) {
    return {
        label,
        kind: vscode.CompletionItemKind.Constant,
        detail: label,
        documentation
    };
}

function createColorSchema(includeGreyAliases) {
    const constants = COLOR_CONSTANTS.map(name => constant(name, "Colour constant."));
    const aliases = includeGreyAliases
        ? COLOURS_EXTRA_CONSTANTS.map(name => constant(name, "Grey alias for the colours module."))
        : [];

    return [
        ...constants,
        ...aliases,
        method("combine", "cc.colors.combine(*values)", "Combine one or more colour bits."),
        method("subtract", "cc.colors.subtract(colors, *values)", "Remove colour bits from a mask."),
        method("test", "cc.colors.test(colors, color)", "Check whether a colour bit is set."),
        method("pack_rgb", "cc.colors.pack_rgb(r, g, b)", "Pack normalized RGB values into an integer."),
        method("packRGB", "cc.colors.packRGB(r, g, b)", "CamelCase alias for pack_rgb."),
        method("unpack_rgb", "cc.colors.unpack_rgb(rgb)", "Unpack an RGB integer into normalized values."),
        method("unpackRGB", "cc.colors.unpackRGB(rgb)", "CamelCase alias for unpack_rgb."),
        method("rgb8", "cc.colors.rgb8(r, g=None, b=None)", "Pack or unpack RGB values."),
        method("to_blit", "cc.colors.to_blit(color)", "Convert a colour bit to a blit character."),
        method("toBlit", "cc.colors.toBlit(color)", "CamelCase alias for to_blit."),
        method("from_blit", "cc.colors.from_blit(hex_value)", "Convert a blit character to a colour bit."),
        method("fromBlit", "cc.colors.fromBlit(hex_value)", "CamelCase alias for from_blit.")
    ];
}

const MODULE_SCHEMAS = {
    fs: [
        method("open", "cc.fs.open(path, mode='r')", "Open a CraftOS file and return a file handle."),
        method("join", "cc.fs.join(*parts)", "Join filesystem path parts using CraftOS path rules."),
        method("read_all", "cc.fs.read_all(path)", "Read the whole file as text."),
        method("write_all", "cc.fs.write_all(path, data)", "Overwrite a file with text data."),
        method("append_all", "cc.fs.append_all(path, data)", "Append text data to a file."),
        method("read_text", "cc.fs.read_text(path)", "Read the whole file as text."),
        method("write_text", "cc.fs.write_text(path, data)", "Overwrite a file with text data."),
        method("append_text", "cc.fs.append_text(path, data)", "Append text data to a file."),
        method("read_lines", "cc.fs.read_lines(path)", "Read a text file and return its lines."),
        method("write_lines", "cc.fs.write_lines(path, lines)", "Write an iterable of lines to a file."),
        method("read_json", "cc.fs.read_json(path)", "Read a JSON file and decode it into Python values."),
        method("write_json", "cc.fs.write_json(path, data, indent=2, sort_keys=False)", "Write Python values to a JSON file.")
    ],
    os: [
        method("sleep", "cc.os.sleep(seconds)", "Sleep for the given number of seconds."),
        method("pull_event", "cc.os.pull_event(filter_name=None)", "Wait for the next event."),
        method("pull_event_raw", "cc.os.pull_event_raw(filter_name=None)", "Wait for the next event without swallowing terminate."),
        method("clock", "cc.os.clock()", "Return the current CraftOS clock."),
        method("time", "cc.os.time(locale=None)", "Return the current time."),
        method("day", "cc.os.day(locale=None)", "Return the current day count."),
        method("epoch", "cc.os.epoch(locale=None)", "Return the current epoch time."),
        method("queue_event", "cc.os.queue_event(name, *args)", "Queue a custom event."),
        method("start_timer", "cc.os.start_timer(seconds)", "Start a timer and return its token."),
        method("cancel_timer", "cc.os.cancel_timer(timer_id)", "Cancel an active timer."),
        method("set_alarm", "cc.os.set_alarm(time)", "Schedule an alarm."),
        method("cancel_alarm", "cc.os.cancel_alarm(alarm_id)", "Cancel an active alarm."),
        method("get_computer_id", "cc.os.get_computer_id()", "Return the current computer id."),
        method("get_computer_label", "cc.os.get_computer_label()", "Return the current computer label."),
        method("set_computer_label", "cc.os.set_computer_label(label)", "Set the current computer label."),
        method("run", "cc.os.run(env, path, *args)", "Run another program with a custom environment."),
        method("shutdown", "cc.os.shutdown()", "Shut down the computer."),
        method("reboot", "cc.os.reboot()", "Reboot the computer.")
    ],
    term: [
        method("write", "cc.term.write(text)", "Write text to the terminal."),
        method("blit", "cc.term.blit(text, text_colors, background_colors)", "Write text with per-character colors."),
        method("clear", "cc.term.clear()", "Clear the terminal."),
        method("clear_line", "cc.term.clear_line()", "Clear the current line."),
        method("get_cursor_pos", "cc.term.get_cursor_pos()", "Return the current cursor position."),
        method("set_cursor_pos", "cc.term.set_cursor_pos(x, y)", "Move the cursor."),
        method("get_size", "cc.term.get_size()", "Return terminal width and height."),
        method("set_text_color", "cc.term.set_text_color(color)", "Set the text color."),
        method("set_background_color", "cc.term.set_background_color(color)", "Set the background color."),
        method("current", "cc.term.current()", "Return the current terminal object."),
        method("native", "cc.term.native()", "Return the native terminal object."),
        method("redirect", "cc.term.redirect(target)", "Redirect terminal output to another target.")
    ],
    peripheral: [
        method("names", "cc.peripheral.names(peripheral_type=None)", "List peripheral names, optionally filtered by type."),
        method("list", "cc.peripheral.list(peripheral_type=None)", "Return peripheral descriptors with name and type."),
        method("types", "cc.peripheral.types()", "Return a mapping of peripheral name to type."),
        method("wrap", "cc.peripheral.wrap(side)", "Wrap a peripheral and return a proxy object."),
        method("wrap_all", "cc.peripheral.wrap_all(peripheral_type=None)", "Wrap all matching peripherals."),
        method("find", "cc.peripheral.find(peripheral_type)", "Find the first peripheral of the given type."),
        method("find_all", "cc.peripheral.find_all(peripheral_type)", "Find all peripherals of the given type.")
    ],
    rednet: [
        constant("CHANNEL_BROADCAST", "Broadcast rednet channel."),
        constant("CHANNEL_REPEAT", "Repeat rednet channel."),
        constant("MAX_ID_CHANNELS", "Maximum computer id channel count."),
        method("open", "cc.rednet.open(modem)", "Open a modem for rednet traffic."),
        method("close", "cc.rednet.close(modem=None)", "Close one modem or all open modems."),
        method("is_open", "cc.rednet.is_open(modem=None)", "Check whether rednet is open."),
        method("send", "cc.rednet.send(recipient_id, message, protocol=None)", "Send a rednet message."),
        method("broadcast", "cc.rednet.broadcast(message, protocol=None)", "Broadcast a rednet message."),
        method("receive", "cc.rednet.receive(timeout=None, protocol=None)", "Receive a rednet message."),
        method("host", "cc.rednet.host(protocol, hostname)", "Register a hostname for a protocol."),
        method("unhost", "cc.rednet.unhost(protocol)", "Remove a hosted protocol."),
        method("lookup", "cc.rednet.lookup(protocol, hostname=None)", "Look up ids by protocol and hostname."),
        method("run", "cc.rednet.run()", "Continuously process rednet events."),
        method("send_json", "cc.rednet.send_json(recipient_id, payload, protocol=None)", "Send a JSON-encoded payload."),
        method("broadcast_json", "cc.rednet.broadcast_json(payload, protocol=None)", "Broadcast a JSON-encoded payload."),
        method("receive_json", "cc.rednet.receive_json(timeout=None, protocol=None)", "Receive and decode a JSON payload.")
    ],
    image: [
        method("open", "cc.image.open(path)", "Load an image file and return an image handle."),
        method("load_url", "cc.image.load_url(url, headers=None, timeout=10)", "Download an image and return an image handle."),
        method("loadUrl", "cc.image.loadUrl(url, headers=None, timeout=10)", "CamelCase alias for load_url.")
    ],
    monitorgfx: [
        method("size", "cc.monitorgfx.size(target)", "Return the hi-res monitor dimensions."),
        method("clear", "cc.monitorgfx.clear(target, color=None)", "Clear the hi-res monitor."),
        method("disable", "cc.monitorgfx.disable(target)", "Disable hi-res monitor rendering."),
        method("set_pixel", "cc.monitorgfx.set_pixel(target, x, y, color=None)", "Set one hi-res pixel."),
        method("setPixel", "cc.monitorgfx.setPixel(target, x, y, color=None)", "CamelCase alias for set_pixel."),
        method("draw", "cc.monitorgfx.draw(target, image, x=1, y=1, clear=False)", "Draw a cc.image handle onto a monitor."),
        method("draw_image", "cc.monitorgfx.draw_image(target, image, x=1, y=1, clear=False)", "Snake_case alias for draw."),
        method("drawImage", "cc.monitorgfx.drawImage(target, image, x=1, y=1, clear=False)", "CamelCase alias for draw.")
    ],
    midi: [
        method("open", "cc.midi.open(path)", "Parse a MIDI file and return a song handle."),
        method("load", "cc.midi.load(path)", "Alias for cc.midi.open."),
        method("list_soundfonts", "cc.midi.list_soundfonts()", "List available soundfonts."),
        method("soundfonts", "cc.midi.soundfonts()", "Alias for list_soundfonts."),
        method("play", "cc.midi.play(target, song, tempo_scale=1.0, volume=1.0, transpose=0, mode='notes', soundfont=None)", "Play a MIDI path or song handle.")
    ],
    redstone: [
        method("get_input", "cc.redstone.get_input(side)", "Read a digital redstone input."),
        method("set_output", "cc.redstone.set_output(side, value)", "Set a digital redstone output."),
        method("get_output", "cc.redstone.get_output(side)", "Read a digital redstone output."),
        method("get_analog_input", "cc.redstone.get_analog_input(side)", "Read an analog redstone input."),
        method("set_analog_output", "cc.redstone.set_analog_output(side, value)", "Set an analog redstone output."),
        method("get_analog_output", "cc.redstone.get_analog_output(side)", "Read an analog redstone output."),
        method("get_bundled_input", "cc.redstone.get_bundled_input(side)", "Read bundled redstone input."),
        method("set_bundled_output", "cc.redstone.set_bundled_output(side, colors)", "Set bundled redstone output."),
        method("get_bundled_output", "cc.redstone.get_bundled_output(side)", "Read bundled redstone output."),
        method("test_bundled_input", "cc.redstone.test_bundled_input(side, color)", "Test a bundled color bit.")
    ],
    colors: createColorSchema(false),
    colours: createColorSchema(true),
    keys: [
        ...KEY_CONSTANTS.map(name => constant(name, "Keyboard constant.")),
        method("get_name", "cc.keys.get_name(code)", "Resolve a key code to its name."),
        method("getName", "cc.keys.getName(code)", "CamelCase alias for get_name.")
    ],
    paintutils: [
        method("parse_image", "cc.paintutils.parse_image(image)", "Parse a blit image string."),
        method("parseImage", "cc.paintutils.parseImage(image)", "CamelCase alias for parse_image."),
        method("load_image", "cc.paintutils.load_image(path)", "Load a paintutils image file."),
        method("loadImage", "cc.paintutils.loadImage(path)", "CamelCase alias for load_image."),
        method("draw_pixel", "cc.paintutils.draw_pixel(x_pos, y_pos, colour=None)", "Draw one pixel on the terminal."),
        method("drawPixel", "cc.paintutils.drawPixel(x_pos, y_pos, colour=None)", "CamelCase alias for draw_pixel."),
        method("draw_line", "cc.paintutils.draw_line(start_x, start_y, end_x, end_y, colour=None)", "Draw a line on the terminal."),
        method("drawLine", "cc.paintutils.drawLine(start_x, start_y, end_x, end_y, colour=None)", "CamelCase alias for draw_line."),
        method("draw_box", "cc.paintutils.draw_box(start_x, start_y, end_x, end_y, colour=None)", "Draw a box outline."),
        method("drawBox", "cc.paintutils.drawBox(start_x, start_y, end_x, end_y, colour=None)", "CamelCase alias for draw_box."),
        method("draw_filled_box", "cc.paintutils.draw_filled_box(start_x, start_y, end_x, end_y, colour=None)", "Draw a filled box."),
        method("drawFilledBox", "cc.paintutils.drawFilledBox(start_x, start_y, end_x, end_y, colour=None)", "CamelCase alias for draw_filled_box."),
        method("draw_image", "cc.paintutils.draw_image(image, x_pos, y_pos)", "Draw a parsed image."),
        method("drawImage", "cc.paintutils.drawImage(image, x_pos, y_pos)", "CamelCase alias for draw_image.")
    ],
    parallel: [
        method("sleep", "cc.parallel.sleep(seconds)", "Create an awaitable parallel sleep request."),
        method("pull_event", "cc.parallel.pull_event(filter_name=None)", "Create an awaitable event request."),
        method("pull_event_raw", "cc.parallel.pull_event_raw(filter_name=None)", "Create an awaitable raw event request."),
        method("receive", "cc.parallel.receive(timeout=None, protocol=None)", "Create an awaitable rednet receive request."),
        method("wait_for_any", "cc.parallel.wait_for_any(*functions)", "Run functions cooperatively until one finishes."),
        method("waitForAny", "cc.parallel.waitForAny(*functions)", "CamelCase alias for wait_for_any."),
        method("wait_for_all", "cc.parallel.wait_for_all(*functions)", "Run functions cooperatively until all finish."),
        method("waitForAll", "cc.parallel.waitForAll(*functions)", "CamelCase alias for wait_for_all.")
    ],
    vector: [
        method("new", "cc.vector.new(x=0, y=0, z=0)", "Create a 3D vector.")
    ],
    textutils: [
        constant("json_null", "JSON null sentinel."),
        constant("empty_json_array", "Empty JSON array sentinel."),
        method("slow_write", "cc.textutils.slow_write(text, rate=None)", "Write text with an optional per-character delay."),
        method("slowWrite", "cc.textutils.slowWrite(text, rate=None)", "CamelCase alias for slow_write."),
        method("slow_print", "cc.textutils.slow_print(text='', rate=None)", "Print text with an optional per-character delay."),
        method("slowPrint", "cc.textutils.slowPrint(text='', rate=None)", "CamelCase alias for slow_print."),
        method("format_time", "cc.textutils.format_time(value, twenty_four_hour=False)", "Format a CraftOS time value."),
        method("formatTime", "cc.textutils.formatTime(value, twenty_four_hour=False)", "CamelCase alias for format_time."),
        method("paged_print", "cc.textutils.paged_print(text='', free_lines=0)", "Print long text with pagination."),
        method("pagedPrint", "cc.textutils.pagedPrint(text='', free_lines=0)", "CamelCase alias for paged_print."),
        method("serialise", "cc.textutils.serialise(value, opts=None)", "Serialise Python values into Lua text."),
        method("serialize", "cc.textutils.serialize(value, opts=None)", "US spelling alias for serialise."),
        method("unserialise", "cc.textutils.unserialise(text)", "Parse Lua-serialised text."),
        method("unserialize", "cc.textutils.unserialize(text)", "US spelling alias for unserialise."),
        method("serialise_json", "cc.textutils.serialise_json(value, options=None)", "Serialise Python values into JSON."),
        method("serialize_json", "cc.textutils.serialize_json(value, options=None)", "US spelling alias for serialise_json."),
        method("serialiseJSON", "cc.textutils.serialiseJSON(value, options=None)", "CamelCase alias for serialise_json."),
        method("serializeJSON", "cc.textutils.serializeJSON(value, options=None)", "US CamelCase alias for serialise_json."),
        method("unserialise_json", "cc.textutils.unserialise_json(text, options=None)", "Parse JSON into Python values."),
        method("unserialize_json", "cc.textutils.unserialize_json(text, options=None)", "US spelling alias for unserialise_json."),
        method("unserialiseJSON", "cc.textutils.unserialiseJSON(text, options=None)", "CamelCase alias for unserialise_json."),
        method("unserializeJSON", "cc.textutils.unserializeJSON(text, options=None)", "US CamelCase alias for unserialise_json."),
        method("url_encode", "cc.textutils.url_encode(text)", "URL-encode text."),
        method("urlEncode", "cc.textutils.urlEncode(text)", "CamelCase alias for url_encode."),
        method("complete", "cc.textutils.complete(search_text, search_table=None)", "Return CraftOS-style completion suffixes.")
    ],
    imports: [
        method("paths", "cc.imports.paths()", "Return the current import search path."),
        method("invalidate_caches", "cc.imports.invalidate_caches()", "Clear import loader caches."),
        method("loaded_modules", "cc.imports.loaded_modules(prefix=None)", "List loaded modules, optionally filtered by prefix.")
    ],
    turtle: [
        method("forward", "cc.turtle.forward()", "Move the turtle forward."),
        method("back", "cc.turtle.back()", "Move the turtle backward."),
        method("up", "cc.turtle.up()", "Move the turtle up."),
        method("down", "cc.turtle.down()", "Move the turtle down."),
        method("turn_left", "cc.turtle.turn_left()", "Turn the turtle left."),
        method("turn_right", "cc.turtle.turn_right()", "Turn the turtle right."),
        method("dig", "cc.turtle.dig(side=None)", "Dig the block in front."),
        method("dig_up", "cc.turtle.dig_up(side=None)", "Dig the block above."),
        method("dig_down", "cc.turtle.dig_down(side=None)", "Dig the block below."),
        method("place", "cc.turtle.place(sign_text=None)", "Place the selected item in front."),
        method("place_up", "cc.turtle.place_up(sign_text=None)", "Place the selected item above."),
        method("place_down", "cc.turtle.place_down(sign_text=None)", "Place the selected item below."),
        method("attack", "cc.turtle.attack()", "Attack in front."),
        method("attack_up", "cc.turtle.attack_up()", "Attack above."),
        method("attack_down", "cc.turtle.attack_down()", "Attack below."),
        method("detect", "cc.turtle.detect()", "Detect a block in front."),
        method("detect_up", "cc.turtle.detect_up()", "Detect a block above."),
        method("detect_down", "cc.turtle.detect_down()", "Detect a block below."),
        method("inspect", "cc.turtle.inspect()", "Inspect the block in front."),
        method("inspect_up", "cc.turtle.inspect_up()", "Inspect the block above."),
        method("inspect_down", "cc.turtle.inspect_down()", "Inspect the block below."),
        method("select", "cc.turtle.select(slot)", "Select an inventory slot."),
        method("get_selected_slot", "cc.turtle.get_selected_slot()", "Return the selected slot."),
        method("get_item_count", "cc.turtle.get_item_count(slot=None)", "Return the item count for a slot."),
        method("get_item_space", "cc.turtle.get_item_space(slot=None)", "Return free space in a slot."),
        method("get_item_detail", "cc.turtle.get_item_detail(slot=None, detailed=False)", "Return item details for a slot."),
        method("transfer_to", "cc.turtle.transfer_to(slot, count=None)", "Transfer items to another slot."),
        method("drop", "cc.turtle.drop(count=None)", "Drop items in front."),
        method("drop_up", "cc.turtle.drop_up(count=None)", "Drop items upward."),
        method("drop_down", "cc.turtle.drop_down(count=None)", "Drop items downward."),
        method("suck", "cc.turtle.suck(count=None)", "Pick up items in front."),
        method("suck_up", "cc.turtle.suck_up(count=None)", "Pick up items above."),
        method("suck_down", "cc.turtle.suck_down(count=None)", "Pick up items below."),
        method("refuel", "cc.turtle.refuel(count=None)", "Consume fuel items."),
        method("get_fuel_level", "cc.turtle.get_fuel_level()", "Return the current fuel level."),
        method("get_fuel_limit", "cc.turtle.get_fuel_limit()", "Return the maximum fuel level."),
        method("craft", "cc.turtle.craft(limit=64)", "Craft using the turtle inventory."),
        method("equip_left", "cc.turtle.equip_left()", "Swap the left tool."),
        method("equip_right", "cc.turtle.equip_right()", "Swap the right tool.")
    ]
};

const HANDLE_SCHEMAS = {
    MidiSong: [
        method("info", "song.info()", "Return metadata about the parsed MIDI file."),
        method("play", "song.play(speaker, tempo_scale=1.0, volume=1.0, transpose=0, mode='notes', soundfont=None)", "Play the MIDI song.")
    ],
    ImageHandle: [
        property("width", "image.width", "Image width."),
        property("height", "image.height", "Image height."),
        method("info", "image.info()", "Return image metadata."),
        method("size", "image.size()", "Return image width and height."),
        method("resize", "image.resize(width, height, resample='bilinear')", "Resize the image."),
        method("quantize_monitor", "image.quantize_monitor(dither=True)", "Convert the image for monitor rendering."),
        method("close", "image.close()", "Release the image handle.")
    ],
    CCFile: [
        method("read", "file.read(count=None)", "Read bytes or text from the file."),
        method("readline", "file.readline()", "Read one line from the file."),
        method("write", "file.write(value)", "Write bytes or text to the file."),
        method("writeline", "file.writeline(value='')", "Write one line to the file."),
        method("flush", "file.flush()", "Flush buffered file data."),
        method("seek", "file.seek(whence='cur', offset=0)", "Seek within the file."),
        method("close", "file.close()", "Close the file.")
    ],
    Vector: [
        property("x", "vector.x", "Vector x component."),
        property("y", "vector.y", "Vector y component."),
        property("z", "vector.z", "Vector z component."),
        method("add", "vector.add(other)", "Return the sum of two vectors."),
        method("sub", "vector.sub(other)", "Return the difference of two vectors."),
        method("mul", "vector.mul(other)", "Multiply a vector by a scalar."),
        method("div", "vector.div(other)", "Divide a vector by a scalar."),
        method("dot", "vector.dot(other)", "Return the dot product."),
        method("cross", "vector.cross(other)", "Return the cross product."),
        method("length", "vector.length()", "Return the vector length."),
        method("normalize", "vector.normalize()", "Return a unit vector."),
        method("round", "vector.round(tolerance=1.0)", "Round each component."),
        method("tostring", "vector.tostring()", "Return the vector representation.")
    ]
};

const PERIPHERAL_SCHEMAS = {
    speaker: [
        method("play_note", "speaker.play_note(instrument, volume=1.0, pitch=1.0)", "Play a speaker note event."),
        method("play_sound", "speaker.play_sound(name, volume=1.0, pitch=1.0)", "Play a Minecraft sound event."),
        method("play_audio", "speaker.play_audio(samples, volume=1.0)", "Play 8-bit speaker audio."),
        method("play_audio16", "speaker.play_audio16(samples, sample_rate=48000, volume=1.0)", "Play hi-fi 16-bit speaker audio."),
        method("play_hifi_audio", "speaker.play_hifi_audio(samples, sample_rate=48000, volume=1.0)", "Alias for play_audio16."),
        method("stop", "speaker.stop()", "Stop the current speaker playback.")
    ],
    monitor: [
        method("clear", "monitor.clear()", "Clear the monitor."),
        method("write", "monitor.write(text)", "Write text to the monitor."),
        method("blit", "monitor.blit(text, text_colors, background_colors)", "Write colored text to the monitor."),
        method("get_size", "monitor.get_size()", "Return monitor width and height."),
        method("set_cursor_pos", "monitor.set_cursor_pos(x, y)", "Move the monitor cursor."),
        method("get_cursor_pos", "monitor.get_cursor_pos()", "Return the cursor position."),
        method("set_text_scale", "monitor.set_text_scale(scale)", "Change the monitor text scale."),
        method("set_text_color", "monitor.set_text_color(color)", "Set monitor text color."),
        method("set_background_color", "monitor.set_background_color(color)", "Set monitor background color.")
    ],
    modem: [
        method("open", "modem.open(channel)", "Open a modem channel."),
        method("close", "modem.close(channel=None)", "Close one or all modem channels."),
        method("is_open", "modem.is_open(channel)", "Check whether a channel is open."),
        method("transmit", "modem.transmit(channel, reply_channel, message)", "Transmit a modem message."),
        method("is_wireless", "modem.is_wireless()", "Return whether the modem is wireless.")
    ],
    drive: [
        method("is_disk_present", "drive.is_disk_present()", "Return whether a disk is inserted."),
        method("get_disk_label", "drive.get_disk_label()", "Return the current disk label."),
        method("set_disk_label", "drive.set_disk_label(label)", "Set the current disk label."),
        method("get_mount_path", "drive.get_mount_path()", "Return the mounted filesystem path for the disk."),
        method("has_data", "drive.has_data()", "Return whether the disk contains data."),
        method("has_audio", "drive.has_audio()", "Return whether the disk contains an audio record."),
        method("get_audio_title", "drive.get_audio_title()", "Return the record title."),
        method("play_audio", "drive.play_audio()", "Start record playback."),
        method("stop_audio", "drive.stop_audio()", "Stop record playback."),
        method("eject_disk", "drive.eject_disk()", "Eject the disk."),
        method("get_disk_id", "drive.get_disk_id()", "Return the disk id, if present.")
    ]
};

const ROOT_MEMBERS = [
    moduleMember("fs", "CraftOS filesystem helpers."),
    moduleMember("os", "CraftOS event and computer helpers."),
    moduleMember("term", "Terminal proxy helpers."),
    moduleMember("peripheral", "Peripheral discovery and wrappers."),
    moduleMember("rednet", "Networking helpers."),
    moduleMember("image", "Image loading and processing helpers."),
    moduleMember("monitorgfx", "Hi-res monitor graphics helpers."),
    moduleMember("midi", "MIDI parsing and speaker playback."),
    moduleMember("redstone", "Redstone helpers."),
    moduleMember("colors", "Colour constants and helpers."),
    moduleMember("colours", "Colour constants and helpers with grey aliases."),
    moduleMember("keys", "Keyboard constants."),
    moduleMember("paintutils", "Drawing helpers."),
    moduleMember("parallel", "Cooperative task helpers."),
    moduleMember("vector", "3D vector helpers."),
    moduleMember("textutils", "Formatting and serialisation helpers."),
    moduleMember("imports", "Import debugging helpers."),
    moduleMember("turtle", "Turtle proxy helpers."),
    func("sleep", "cc.sleep(seconds)", "Sleep for the given number of seconds."),
    func("open", "cc.open(path, mode='r')", "Open a CraftOS file."),
    func("print", "cc.print(*values)", "Print to the terminal."),
    func("input", "cc.input(prompt=None)", "Read user input from the terminal."),
    func("help", "cc.help(topic=None)", "Show CCPython help."),
    func("exit", "cc.exit(code=None)", "Exit the current program."),
    func("quit", "cc.quit(code=None)", "Alias for exit.")
];

const ROOT_EXPORTS = new Set(ROOT_MEMBERS.map(member => member.label));
const MODULE_NAMES = Object.keys(MODULE_SCHEMAS);

function registerApiIntelligence(context) {
    const completionProvider = {
        provideCompletionItems(document, position) {
            const contextInfo = resolveCompletionContext(document, position);
            if (!contextInfo) return undefined;
            return buildCompletionItems(contextInfo.items, contextInfo.partial || "");
        }
    };

    const hoverProvider = {
        provideHover(document, position) {
            const definition = resolveHoverDefinition(document, position);
            if (!definition) return undefined;

            const markdown = new vscode.MarkdownString(undefined, true);
            if (definition.detail) {
                markdown.appendCodeblock(definition.detail, "python");
            }
            if (definition.documentation) {
                if (definition.detail) markdown.appendMarkdown("\n\n");
                markdown.appendMarkdown(definition.documentation);
            }

            return new vscode.Hover(markdown);
        }
    };

    const diagnostics = vscode.languages.createDiagnosticCollection("ccpython");
    const refreshDiagnostics = document => updateDiagnostics(document, diagnostics);

    context.subscriptions.push(
        vscode.languages.registerCompletionItemProvider(PYTHON_SELECTORS, completionProvider, ".")
    );
    context.subscriptions.push(
        vscode.languages.registerHoverProvider(PYTHON_SELECTORS, hoverProvider)
    );
    context.subscriptions.push(diagnostics);
    context.subscriptions.push(vscode.workspace.onDidOpenTextDocument(refreshDiagnostics));
    context.subscriptions.push(vscode.workspace.onDidChangeTextDocument(event => refreshDiagnostics(event.document)));
    context.subscriptions.push(vscode.workspace.onDidSaveTextDocument(refreshDiagnostics));
    context.subscriptions.push(vscode.workspace.onDidCloseTextDocument(document => diagnostics.delete(document.uri)));

    for (const document of vscode.workspace.textDocuments) {
        refreshDiagnostics(document);
    }
}

function resolveCompletionContext(document, position) {
    const range = new vscode.Range(new vscode.Position(0, 0), position);
    const textBefore = document.getText(range);
    const linePrefix = document.lineAt(position).text.slice(0, position.character);
    const likelyCCPython = isLikelyCCPythonDocument(document, textBefore);
    const aliases = resolveAliases(textBefore, likelyCCPython);

    const importContext = resolveImportContext(linePrefix);
    if (importContext) return importContext;

    const chainedHandleContext = resolveChainedHandleContext(linePrefix, aliases);
    if (chainedHandleContext) return chainedHandleContext;

    const attributeMatch = /([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z0-9_]*)$/.exec(linePrefix);
    if (!attributeMatch) return null;

    const objectName = attributeMatch[1];
    const partial = attributeMatch[2] || "";

    if (aliases.rootAliases.has(objectName)) {
        return { items: ROOT_MEMBERS, partial };
    }

    const moduleName = aliases.moduleAliases.get(objectName);
    if (moduleName && MODULE_SCHEMAS[moduleName]) {
        return { items: MODULE_SCHEMAS[moduleName], partial };
    }

    const variableTypes = inferVariableTypes(textBefore, aliases);
    const handleType = variableTypes.get(objectName);
    if (handleType && HANDLE_SCHEMAS[handleType]) {
        return { items: HANDLE_SCHEMAS[handleType], partial };
    }
    if (handleType && handleType.startsWith("Peripheral:")) {
        const peripheralType = handleType.slice("Peripheral:".length);
        if (PERIPHERAL_SCHEMAS[peripheralType]) {
            return { items: PERIPHERAL_SCHEMAS[peripheralType], partial };
        }
    }

    return null;
}

function resolveImportContext(linePrefix) {
    const importMatch = /\bfrom\s+cc\s+import\s+([A-Za-z0-9_,\s]*)$/.exec(linePrefix);
    if (!importMatch) return null;

    const lastSegment = importMatch[1].split(",").pop() || "";
    const cleaned = lastSegment.replace(/\bas\s+[A-Za-z_][A-Za-z0-9_]*$/, "").trim();
    return {
        items: ROOT_MEMBERS,
        partial: cleaned
    };
}

function resolveChainedHandleContext(linePrefix, aliases) {
    const compact = linePrefix.replace(/\s+/g, "");

    if (matchesHandleChain(compact, aliases, "midi", ["open", "load"])) {
        return { items: HANDLE_SCHEMAS.MidiSong, partial: chainPartial(compact) };
    }
    if (matchesHandleChain(compact, aliases, "image", ["open", "load_url", "loadUrl"])) {
        return { items: HANDLE_SCHEMAS.ImageHandle, partial: chainPartial(compact) };
    }
    if (matchesHandleChain(compact, aliases, "fs", ["open"])) {
        return { items: HANDLE_SCHEMAS.CCFile, partial: chainPartial(compact) };
    }
    if (matchesHandleChain(compact, aliases, "vector", ["new"])) {
        return { items: HANDLE_SCHEMAS.Vector, partial: chainPartial(compact) };
    }

    return null;
}

function matchesHandleChain(compact, aliases, moduleName, methods) {
    const partial = chainPartial(compact);
    if (partial === null) return false;

    for (const prefix of modulePrefixes(aliases, moduleName)) {
        for (const methodName of methods) {
            const pattern = new RegExp(`${escapeRegExp(prefix + methodName)}\\(.*\\)\\.[A-Za-z0-9_]*$`);
            if (pattern.test(compact)) return true;
        }
    }

    return false;
}

function chainPartial(compact) {
    const match = /\.([A-Za-z0-9_]*)$/.exec(compact);
    return match ? match[1] : null;
}

function inferVariableTypes(textBefore, aliases) {
    const variableTypes = new Map();
    const lines = textBefore.split(/\r?\n/);

    for (const line of lines) {
        const match = /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+)$/.exec(line);
        if (!match) continue;

        const variableName = match[1];
        const expression = match[2].replace(/\s+/g, "");

        if (matchesModuleCall(expression, aliases, "midi", ["open", "load"])) {
            variableTypes.set(variableName, "MidiSong");
            continue;
        }

        if (matchesModuleCall(expression, aliases, "image", ["open", "load_url", "loadUrl"])) {
            variableTypes.set(variableName, "ImageHandle");
            continue;
        }

        if (matchesModuleCall(expression, aliases, "fs", ["open"])) {
            variableTypes.set(variableName, "CCFile");
            continue;
        }

        if (matchesModuleCall(expression, aliases, "vector", ["new"])) {
            variableTypes.set(variableName, "Vector");
            continue;
        }

        const peripheralType = inferPeripheralType(variableName, expression, aliases);
        if (peripheralType) {
            variableTypes.set(variableName, `Peripheral:${peripheralType}`);
        }
    }

    return variableTypes;
}

function inferPeripheralType(variableName, expression, aliases) {
    const explicit = matchPeripheralFind(expression, aliases);
    if (explicit) return explicit;

    const lowered = variableName.toLowerCase();
    for (const type of Object.keys(PERIPHERAL_SCHEMAS)) {
        if (lowered.includes(type)) return type;
    }

    return null;
}

function matchPeripheralFind(expression, aliases) {
    const prefixes = modulePrefixes(aliases, "peripheral");
    for (const prefix of prefixes) {
        const escaped = escapeRegExp(prefix);
        const findPattern = new RegExp(`^${escaped}find\\((['"])(speaker|monitor|modem|drive)\\1`);
        const wrapPattern = new RegExp(`^${escaped}wrap\\(`);
        const findMatch = expression.match(findPattern);
        if (findMatch) return findMatch[2];
        if (wrapPattern.test(expression)) return null;
    }
    return null;
}

function matchesModuleCall(expression, aliases, moduleName, methods) {
    for (const prefix of modulePrefixes(aliases, moduleName)) {
        for (const methodName of methods) {
            if (expression.startsWith(`${prefix}${methodName}(`)) return true;
        }
    }
    return false;
}

function modulePrefixes(aliases, moduleName) {
    const prefixes = [];

    for (const rootAlias of aliases.rootAliases) {
        prefixes.push(`${rootAlias}.${moduleName}.`);
    }

    for (const [alias, mappedModuleName] of aliases.moduleAliases.entries()) {
        if (mappedModuleName === moduleName) {
            prefixes.push(`${alias}.`);
        }
    }

    return [...new Set(prefixes)];
}

function resolveAliases(textBefore, likelyCCPython) {
    const rootAliases = new Set();
    const moduleAliases = new Map();

    if (likelyCCPython) {
        rootAliases.add("cc");
        for (const moduleName of MODULE_NAMES) {
            moduleAliases.set(moduleName, moduleName);
        }
    }

    for (const match of textBefore.matchAll(/^\s*import\s+cc\s+as\s+([A-Za-z_][A-Za-z0-9_]*)\s*$/gm)) {
        rootAliases.add(match[1]);
    }

    for (const match of textBefore.matchAll(/^\s*import\s+cc\.([A-Za-z_][A-Za-z0-9_]*)(?:\s+as\s+([A-Za-z_][A-Za-z0-9_]*))?\s*$/gm)) {
        const moduleName = match[1];
        const alias = match[2] || moduleName;
        if (MODULE_SCHEMAS[moduleName]) {
            moduleAliases.set(alias, moduleName);
        }
    }

    for (const match of textBefore.matchAll(/^\s*from\s+cc\s+import\s+([^\n#]+)\s*$/gm)) {
        for (const spec of splitImportSpecs(match[1])) {
            if (spec.name === "*") {
                for (const moduleName of MODULE_NAMES) {
                    moduleAliases.set(moduleName, moduleName);
                }
                rootAliases.add("cc");
                continue;
            }

            if (!ROOT_EXPORTS.has(spec.name)) continue;
            if (MODULE_SCHEMAS[spec.name]) {
                moduleAliases.set(spec.alias || spec.name, spec.name);
            }
        }
    }

    return { rootAliases, moduleAliases };
}

function splitImportSpecs(raw) {
    return raw
        .split(",")
        .map(part => part.trim())
        .filter(Boolean)
        .map(part => {
            const aliasMatch = /^([A-Za-z_*][A-Za-z0-9_]*)(?:\s+as\s+([A-Za-z_][A-Za-z0-9_]*))?$/.exec(part);
            if (!aliasMatch) return { name: part, alias: null };
            return { name: aliasMatch[1], alias: aliasMatch[2] || null };
        });
}

function isLikelyCCPythonDocument(document, textBefore) {
    if (document.uri.scheme === "ccpython") return true;
    return /\b(import\s+cc\b|from\s+cc\s+import\b)/.test(textBefore);
}

function resolveHoverDefinition(document, position) {
    const wordRange = document.getWordRangeAtPosition(position);
    if (!wordRange) return null;

    const word = document.getText(wordRange);
    const linePrefix = document.lineAt(position).text.slice(0, wordRange.end.character);
    const textBefore = document.getText(new vscode.Range(new vscode.Position(0, 0), wordRange.end));
    const aliases = resolveAliases(textBefore, isLikelyCCPythonDocument(document, textBefore));
    const variableTypes = inferVariableTypes(textBefore, aliases);

    const attributeMatch = /([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z0-9_]*)$/.exec(linePrefix);
    if (attributeMatch) {
        const objectName = attributeMatch[1];
        const memberName = attributeMatch[2];
        if (memberName !== word) return null;

        if (aliases.rootAliases.has(objectName)) {
            return ROOT_MEMBERS.find(item => item.label === memberName) || null;
        }

        const moduleName = aliases.moduleAliases.get(objectName);
        if (moduleName && MODULE_SCHEMAS[moduleName]) {
            return MODULE_SCHEMAS[moduleName].find(item => item.label === memberName) || null;
        }

        const handleType = variableTypes.get(objectName);
        if (handleType && HANDLE_SCHEMAS[handleType]) {
            return HANDLE_SCHEMAS[handleType].find(item => item.label === memberName) || null;
        }
        if (handleType && handleType.startsWith("Peripheral:")) {
            const peripheralType = handleType.slice("Peripheral:".length);
            return (PERIPHERAL_SCHEMAS[peripheralType] || []).find(item => item.label === memberName) || null;
        }
    }

    if (aliases.rootAliases.has(word)) {
        return { label: word, kind: vscode.CompletionItemKind.Module, detail: word, documentation: "CCPython root module." };
    }

    if (aliases.moduleAliases.has(word)) {
        return ROOT_MEMBERS.find(item => item.label === aliases.moduleAliases.get(word)) || null;
    }

    return ROOT_MEMBERS.find(item => item.label === word) || null;
}

function updateDiagnostics(document, collection) {
    if (!PYTHON_SELECTORS.some(selector => selector.language === document.languageId) || !isLikelyCCPythonDocument(document, document.getText())) {
        collection.delete(document.uri);
        return;
    }

    const diagnostics = [];
    const text = document.getText();
    const lines = text.split(/\r?\n/);

    for (let index = 0; index < lines.length; index++) {
        const line = lines[index];
        const importMatch = /^\s*(?:from|import)\s+([A-Za-z0-9_\.]+)/.exec(line);
        if (importMatch && UNSUPPORTED_MODULES.has(importMatch[1].split(".")[0])) {
            const range = new vscode.Range(index, 0, index, line.length);
            diagnostics.push(new vscode.Diagnostic(
                range,
                `Module '${importMatch[1]}' is usually unavailable or unsafe inside the CCPython runtime.`,
                vscode.DiagnosticSeverity.Warning
            ));
        }

        if (line.length > 200) {
            diagnostics.push(new vscode.Diagnostic(
                new vscode.Range(index, 200, index, line.length),
                "Very long lines are awkward inside CC terminals and traceback views.",
                vscode.DiagnosticSeverity.Information
            ));
        }
    }

    if (Buffer.byteLength(text, "utf8") > 256 * 1024) {
        diagnostics.push(new vscode.Diagnostic(
            new vscode.Range(0, 0, 0, 1),
            "This file is larger than the typical CCPython source budget. Consider splitting it into modules.",
            vscode.DiagnosticSeverity.Warning
        ));
    }

    collection.set(document.uri, diagnostics);
}

function generateStubText() {
    const lines = [
        '"""Auto-generated CCPython stubs."""',
        "from __future__ import annotations",
        "from typing import Any, Iterable, Protocol",
        "",
        "class MidiSong(Protocol):",
        ...renderMembers(HANDLE_SCHEMAS.MidiSong),
        "",
        "class ImageHandle(Protocol):",
        ...renderMembers(HANDLE_SCHEMAS.ImageHandle),
        "",
        "class CCFile(Protocol):",
        ...renderMembers(HANDLE_SCHEMAS.CCFile),
        "",
        "class Vector(Protocol):",
        ...renderMembers(HANDLE_SCHEMAS.Vector),
        "",
        "class SpeakerPeripheral(Protocol):",
        ...renderMembers(PERIPHERAL_SCHEMAS.speaker),
        "",
        "class MonitorPeripheral(Protocol):",
        ...renderMembers(PERIPHERAL_SCHEMAS.monitor),
        "",
        "class ModemPeripheral(Protocol):",
        ...renderMembers(PERIPHERAL_SCHEMAS.modem),
        "",
        "class DrivePeripheral(Protocol):",
        ...renderMembers(PERIPHERAL_SCHEMAS.drive),
        "",
        "class _FsModule(Protocol):",
        ...renderMembers(MODULE_SCHEMAS.fs),
        "",
        "class _MidiModule(Protocol):",
        ...renderMembers(MODULE_SCHEMAS.midi),
        "",
        "class _ImageModule(Protocol):",
        ...renderMembers(MODULE_SCHEMAS.image),
        "",
        "class _PeripheralModule(Protocol):",
        ...renderMembers(MODULE_SCHEMAS.peripheral),
        "",
        "fs: _FsModule",
        "midi: _MidiModule",
        "image: _ImageModule",
        "peripheral: _PeripheralModule",
        "",
        "def sleep(seconds: float) -> None: ...",
        "def open(path: str, mode: str = 'r') -> CCFile: ...",
        "def print(*values: Any) -> None: ...",
        "def input(prompt: str | None = None) -> str: ...",
        "def help(topic: str | None = None) -> None: ...",
        "def exit(code: int | None = None) -> None: ...",
        "def quit(code: int | None = None) -> None: ..."
    ];

    return lines.join("\n") + "\n";
}

function renderMembers(members) {
    return members.map(member => {
        if (member.kind === vscode.CompletionItemKind.Property || member.kind === vscode.CompletionItemKind.Constant) {
            return `    ${member.label}: Any`;
        }
        return `    def ${member.label}(self, *args: Any, **kwargs: Any) -> Any: ...`;
    });
}

function buildCompletionItems(items, partial) {
    return items
        .filter(item => !partial || item.label.startsWith(partial))
        .map(createCompletionItem);
}

function createCompletionItem(definition) {
    const item = new vscode.CompletionItem(definition.label, definition.kind);
    item.detail = definition.detail;

    const markdown = new vscode.MarkdownString(undefined, true);
    if (definition.detail) {
        markdown.appendCodeblock(definition.detail, "python");
    }
    if (definition.documentation) {
        if (definition.detail) markdown.appendMarkdown("\n\n");
        markdown.appendMarkdown(definition.documentation);
    }
    item.documentation = markdown;
    return item;
}

function escapeRegExp(value) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

module.exports = {
    registerApiIntelligence,
    generateStubText
};
