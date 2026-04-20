"use strict";

const TEMPLATES = {
    startup: {
        label: "startup.py",
        fileName: "startup.py",
        description: "Simple startup entrypoint for a computer.",
        content: [
            "import cc",
            "",
            "cc.print('CCPython startup is running.')",
            ""
        ].join("\n")
    },
    monitor: {
        label: "monitor app",
        fileName: "monitor_app.py",
        description: "Draw a simple UI on a monitor peripheral.",
        content: [
            "import cc",
            "",
            "monitor = cc.peripheral.find('monitor')",
            "if not monitor:",
            "    raise RuntimeError('monitor not found')",
            "",
            "monitor.clear()",
            "monitor.set_cursor_pos(1, 1)",
            "monitor.write('Hello from CCPython')",
            ""
        ].join("\n")
    },
    speaker: {
        label: "speaker app",
        fileName: "speaker_app.py",
        description: "Play a note on the first speaker peripheral.",
        content: [
            "import cc",
            "",
            "speaker = cc.peripheral.find('speaker')",
            "if not speaker:",
            "    raise RuntimeError('speaker not found')",
            "",
            "speaker.play_note('harp', 1.0, 12)",
            ""
        ].join("\n")
    },
    rednet: {
        label: "rednet app",
        fileName: "rednet_app.py",
        description: "Open a modem and receive one rednet message.",
        content: [
            "import cc",
            "",
            "modem = cc.peripheral.find('modem')",
            "if not modem:",
            "    raise RuntimeError('modem not found')",
            "",
            "cc.rednet.open(modem)",
            "sender, message, protocol = cc.rednet.receive()",
            "cc.print(f'from {sender}: {message!r} ({protocol})')",
            ""
        ].join("\n")
    },
    turtle: {
        label: "turtle bot",
        fileName: "turtle_bot.py",
        description: "Small turtle movement loop with fuel checks.",
        content: [
            "import cc",
            "",
            "if cc.turtle.get_fuel_level() == 0:",
            "    raise RuntimeError('out of fuel')",
            "",
            "for _ in range(4):",
            "    cc.turtle.forward()",
            "    cc.turtle.turn_right()",
            ""
        ].join("\n")
    }
};

function templateItems() {
    return Object.entries(TEMPLATES).map(([key, template]) => ({
        key,
        ...template
    }));
}

function getTemplate(key) {
    return TEMPLATES[key] || null;
}

module.exports = {
    getTemplate,
    templateItems
};
