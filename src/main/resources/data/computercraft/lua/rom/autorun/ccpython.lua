local completion = require("cc.shell.completion")

shell.setAlias("py", "python")
shell.setCompletionFunction("rom/programs/python.lua", completion.build({ completion.file }))
