local function pack(...)
    return table.pack(...)
end

local function unpack_from(values, start)
    local first = start or 1
    return table.unpack(values, first, values.n or #values)
end

local function assert_callable(value, description)
    if type(value) ~= "function" then
        error(description .. " is not callable", 0)
    end
    return value
end

local function store_lua_ref(state, value)
    local token = tostring(state.next_lua_ref)
    state.next_lua_ref = state.next_lua_ref + 1
    state.lua_refs[token] = value
    return token
end

local function is_plain_table(value, seen)
    if type(value) ~= "table" then
        return false
    end

    if getmetatable(value) ~= nil then
        return false
    end

    if seen[value] then
        return false
    end

    seen[value] = true
    for key, entry in pairs(value) do
        local key_type = type(key)
        if key_type ~= "string" and key_type ~= "number" and key_type ~= "boolean" then
            seen[value] = nil
            return false
        end

        local entry_type = type(entry)
        if entry_type == "table" then
            if not is_plain_table(entry, seen) then
                seen[value] = nil
                return false
            end
        elseif entry_type ~= "nil" and entry_type ~= "boolean" and entry_type ~= "number" and entry_type ~= "string" then
            seen[value] = nil
            return false
        end
    end

    seen[value] = nil
    return true
end

local function clone_plain_table(value, seen)
    if type(value) ~= "table" then
        return value
    end

    if seen[value] ~= nil then
        return seen[value]
    end

    local copy = {}
    seen[value] = copy
    for key, entry in pairs(value) do
        copy[clone_plain_table(key, seen)] = clone_plain_table(entry, seen)
    end
    return copy
end

local function encode_lua_value(state, value)
    local kind = type(value)
    if kind == "nil" or kind == "boolean" or kind == "number" or kind == "string" then
        return value
    end

    if kind == "table" and is_plain_table(value, {}) then
        return clone_plain_table(value, {})
    end

    return {
        __ccpython_lua_proxy = true,
        token = store_lua_ref(state, value),
        kind = kind,
        label = tostring(value),
    }
end

local function encode_lua_results(state, ...)
    local results = table.pack(...)
    for index = 1, results.n do
        results[index] = encode_lua_value(state, results[index])
    end
    return results
end

local function get_lua_ref(state, token)
    local value = state.lua_refs[tostring(token)]
    if value == nil then
        error("Unknown Lua proxy reference '" .. tostring(token) .. "'", 0)
    end
    return value
end

local function decode_python_value(state, value)
    if type(value) ~= "table" then
        return value
    end

    if value.__ccpython_lua_ref == true then
        return get_lua_ref(state, value.token)
    end

    local decoded = {}
    for key, entry in pairs(value) do
        decoded[decode_python_value(state, key)] = decode_python_value(state, entry)
    end
    return decoded
end

local function decode_python_args(state, args, start)
    local decoded = table.pack()
    local begin = start or 1
    local finish = args.n or #args
    for index = begin, finish do
        decoded[#decoded + 1] = decode_python_value(state, args[index])
    end
    decoded.n = #decoded
    return decoded
end

local function resolve_lua_module(name)
    if type(name) ~= "string" or name == "" then
        return nil
    end

    local global_value = rawget(_G, name)
    if global_value ~= nil then
        return global_value
    end

    local ok, required = pcall(require, name)
    if ok then
        return required
    end

    return nil
end

local function get_lua_member(target, primary, fallback)
    if target == nil then
        return nil
    end

    local value = target[primary]
    if value == nil and fallback ~= nil and fallback ~= primary then
        value = target[fallback]
    end
    return value
end

local function enforce_source_limit(state, path)
    local max_source_bytes = state.limits.max_source_bytes
    if type(max_source_bytes) ~= "number" or max_source_bytes <= 0 then
        return
    end

    local size = fs.getSize(path)
    if size ~= nil and size > max_source_bytes then
        error("Python source '" .. tostring(path) .. "' exceeds the configured size limit (" .. tostring(size) .. " > " .. tostring(max_source_bytes) .. " bytes)", 0)
    end
end

local function read_all(state, path)
    local handle = fs.open(path, "r")
    if handle == nil then
        error("Cannot open file '" .. tostring(path) .. "'", 0)
    end

    enforce_source_limit(state, path)
    local content = handle.readAll()
    handle.close()
    return content
end

local function write_all(path, content, mode)
    local handle = fs.open(path, mode)
    if handle == nil then
        error("Cannot open file '" .. tostring(path) .. "'", 0)
    end

    handle.write(content)
    handle.close()
end

local function resolve_import(fullname, search_paths)
    local relative = fullname:gsub("%.", "/")

    for _, base in ipairs(search_paths or {}) do
        local module_path = fs.combine(base, relative .. ".py")
        if fs.exists(module_path) and not fs.isDir(module_path) then
            return {
                path = module_path,
                package = false,
                package_path = fs.getDir(module_path),
            }
        end

        local package_path = fs.combine(base, relative)
        local init_path = fs.combine(package_path, "__init__.py")
        if fs.exists(init_path) and not fs.isDir(init_path) then
            return {
                path = init_path,
                package = true,
                namespace = false,
                package_path = package_path,
            }
        end

        if fs.exists(package_path) and fs.isDir(package_path) then
            return {
                path = package_path,
                package = true,
                namespace = true,
                package_path = package_path,
            }
        end
    end

    return nil
end

local function get_handle(state, token)
    local handle = state.file_handles[token]
    if handle == nil then
        error("Unknown Python file handle '" .. tostring(token) .. "'", 0)
    end
    return handle
end

local function close_all_handles(state)
    for token, handle in pairs(state.file_handles) do
        pcall(function()
            handle.close()
        end)
        state.file_handles[token] = nil
    end
end

local function dispatch_special_fs(state, method, args)
    if method == "readAll" then
        return pack(read_all(state, args[1]))
    elseif method == "writeAll" then
        write_all(args[1], args[2], "w")
        return pack()
    elseif method == "appendAll" then
        write_all(args[1], args[2], "a")
        return pack()
    elseif method == "resolveImport" then
        return pack(resolve_import(args[1], args[2]))
    end

    error("Unsupported __fs host method '" .. tostring(method) .. "'", 0)
end

local function dispatch_lua_runtime(state, method, args)
    if method == "resolveModule" then
        return pack(encode_lua_value(state, resolve_lua_module(args[1])))
    elseif method == "getGlobalAttr" then
        local target = args[1] == "_G" and _G or resolve_lua_module(args[1])
        return pack(encode_lua_value(state, get_lua_member(target, args[2], args[3])))
    elseif method == "keysGlobal" then
        local target = args[1] == "_G" and _G or resolve_lua_module(args[1])
        if type(target) ~= "table" then
            return pack({})
        end

        local keys = {}
        for key in pairs(target) do
            if type(key) == "string" then
                keys[#keys + 1] = key
            end
        end
        table.sort(keys)
        return pack(keys)
    elseif method == "getAttr" then
        local target = get_lua_ref(state, args[1])
        return pack(encode_lua_value(state, get_lua_member(target, args[2], args[3])))
    elseif method == "keys" then
        local target = get_lua_ref(state, args[1])
        if type(target) ~= "table" then
            return pack({})
        end

        local keys = {}
        for key in pairs(target) do
            if type(key) == "string" then
                keys[#keys + 1] = key
            end
        end
        table.sort(keys)
        return pack(keys)
    elseif method == "call" then
        local callable = assert_callable(get_lua_ref(state, args[1]), "Lua proxy reference '" .. tostring(args[1]) .. "'")
        local decoded_args = decode_python_args(state, args, 2)
        return encode_lua_results(state, callable(unpack_from(decoded_args)))
    elseif method == "callAttr" then
        local target = get_lua_ref(state, args[1])
        local callable = assert_callable(get_lua_member(target, args[2], args[3]), "Lua proxy member '" .. tostring(args[2]) .. "'")
        local decoded_args = decode_python_args(state, args, 4)
        return encode_lua_results(state, callable(unpack_from(decoded_args)))
    end

    error("Unsupported __lua host method '" .. tostring(method) .. "'", 0)
end

local function dispatch_file_handle(state, method, args)
    if method == "open" then
        local max_handles = tonumber(state.limits.max_open_file_handles_per_process) or 32
        if state.open_file_handle_count >= max_handles then
            error("Python process exceeded the configured open file handle limit (" .. tostring(max_handles) .. ")", 0)
        end
        local token = tostring(state.next_file_handle)
        state.next_file_handle = state.next_file_handle + 1
        state.file_handles[token] = fs.open(args[1], args[2] or "r")
        if state.file_handles[token] == nil then
            error("Cannot open file '" .. tostring(args[1]) .. "'", 0)
        end
        state.open_file_handle_count = state.open_file_handle_count + 1
        return pack(token)
    end

    local handle = get_handle(state, args[1])
    if method == "close" then
        handle.close()
        state.file_handles[args[1]] = nil
        if state.open_file_handle_count > 0 then
            state.open_file_handle_count = state.open_file_handle_count - 1
        end
        return pack()
    elseif method == "seek" then
        return pack(handle.seek(args[2], args[3]))
    end

    return pack(assert_callable(handle[method], "File handle method '" .. tostring(method) .. "'")(table.unpack(args, 2, #args)))
end

local function dispatch_host_call(state, action)
    local module_name = action.module
    local method = action.method
    local args = decode_python_args(state, action.args or {}, 1)

    if module_name == "__fs" then
        return dispatch_special_fs(state, method, args)
    elseif module_name == "__fs_handle" then
        return dispatch_file_handle(state, method, args)
    elseif module_name == "__lua" then
        return dispatch_lua_runtime(state, method, args)
    elseif module_name == "__global" then
        local fn = assert_callable(_G[method], "Global host function '" .. tostring(method) .. "'")
        return encode_lua_results(state, fn(unpack_from(args)))
    else
        local module = _G[module_name]
        if type(module) ~= "table" then
            error("Host module '" .. tostring(module_name) .. "' is unavailable", 0)
        end
        local fn = assert_callable(module[method], "Host function '" .. tostring(module_name) .. "." .. tostring(method) .. "'")
        return encode_lua_results(state, fn(unpack_from(args)))
    end
end

local M = {}

function M.run(spec)
    local state = {
        limits = ccpython.limits(),
        file_handles = {},
        next_file_handle = 1,
        open_file_handle_count = 0,
        lua_refs = {},
        next_lua_ref = 1,
    }

    local handle = ccpython.start(spec)
    local results = table.pack(pcall(function()
        while true do
            local action = handle.await()

            if action.kind == "host_call" then
                local ok, results_or_error = pcall(function()
                    return dispatch_host_call(state, action)
                end)

                if ok then
                    handle.respond(true, table.unpack(results_or_error, 1, results_or_error.n))
                else
                    handle.respond(false, tostring(results_or_error))
                end
            elseif action.kind == "done" then
                local done_results = action.results or {}
                return table.unpack(done_results, 1, action.result_count or #done_results)
            elseif action.kind == "error" then
                error(action.traceback or action.message or "Python runtime error", 0)
            else
                error("Unknown Python action kind '" .. tostring(action.kind) .. "'", 0)
            end
        end
    end))

    close_all_handles(state)
    if not results[1] then
        error(results[2], 0)
    end

    return table.unpack(results, 2, results.n)
end

return M
