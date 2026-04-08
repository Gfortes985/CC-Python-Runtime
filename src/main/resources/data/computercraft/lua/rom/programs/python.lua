local driver = require("ccpython.driver")

local args = { ... }

if #args == 0 or (args[1] == "-i" and #args == 1) then
    return driver.run({
        interactive = true,
        cwd = shell.dir(),
        args = {},
    })
end

local resolved_program = shell.resolve(args[1])
if not fs.exists(resolved_program) or fs.isDir(resolved_program) then
    error("No such Python file: " .. tostring(args[1]), 0)
end

local python_args = {}
for i = 2, #args do
    python_args[#python_args + 1] = args[i]
end

return driver.run({
    program = args[1],
    cwd = shell.dir(),
    args = python_args,
    interactive = false,
})
