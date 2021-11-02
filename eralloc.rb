#
# Based on Ershov and Sethi-Ullman (which are pretty much the same anyway)
#

def eralloc(expr)
    map = {}
    ershov(expr, map)

    registers = ['edx', 'eax']
    emit(expr, map, Array.new(registers), Array.new(registers), 4)
end

def emit(expr, map, free_regs, all_regs, base_offset)
    case expr[0]
    when "Numeric"
        return ["mov #{free_regs.last}, #{expr[1]}"]
    when "Variable"
        return ["mov #{free_regs.last}, [ebp-#{expr[1]}]"]
    when "Add", "Sub", "Mul"
        lhs_num = map[expr[1]]
        rhs_num = map[expr[2]]

        body = []
        need_spilling = free_regs.length < 2
        spills = {}
        if need_spilling then
            spilled_regs = all_regs - free_regs
            free_regs.unshift(*spilled_regs)
            spilled_regs.each do |r|
                body << "mov [ebp-#{base_offset}], #{r}";
                spills[r] = base_offset
                base_offset += 4
            end
        end

        lhs_expr = expr[1]
        rhs_expr = expr[2]
        if lhs_num < rhs_num then
            # swap the left and right expressions and the top two registers
            lhs_expr, rhs_expr = rhs_expr, lhs_expr
            free_regs[-1], free_regs[-2] = free_regs[-2], free_regs[-1]
        end
        
        lc = emit(lhs_expr, map, free_regs, all_regs, base_offset)
        rc = emit(rhs_expr, map, free_regs[..-2], all_regs, base_offset)
        
        if lhs_num < rhs_num then
            # unswap the top two registers
            free_regs[-1], free_regs[-2] = free_regs[-2], free_regs[-1]
        end

        opc = { "Add" => "add", "Sub" => "sub", "Mul" => "imul" }[expr[0]]
        body += lc + rc + ["#{opc} #{free_regs[-1]}, #{free_regs[-2]}"]

        if need_spilling then
            free_regs.shift(spills.length)
            all_regs.each do |r|
                body << "mov #{r}, [ebp-#{spills[r]}]" if spills[r]
            end
        end

        return body
    end
end

def ershov(expr, map)
    case expr[0]
    when "Numeric", "Variable"
        return map[expr] = 1
    when "Add", "Sub", "Mul"
        lhs = ershov(expr[1], map)
        rhs = ershov(expr[2], map)

        return map[expr] = lhs == rhs ? lhs + 1 : [lhs, rhs].max
    end
end

puts eralloc(["Numeric", 123])
puts
puts eralloc(["Variable", "foo"])
puts
puts eralloc(["Add", ["Numeric", 123], ["Numeric", 456]])
puts
puts eralloc(["Add", ["Variable", "foo"], ["Numeric", 456]])
puts
puts eralloc(["Add", ["Numeric", 456], ["Variable", "foo"]])
puts
puts eralloc(["Add", ["Variable", "foo"], ["Numeric", 456]])
puts
puts eralloc(["Add", ["Variable", "foo"], ["Add", ["Numeric", 456], ["Numeric", 789]]])
puts
puts eralloc(["Add",
        ["Add", ["Variable", "foo"], ["Numeric", 456]],
        ["Add", ["Numeric", 123], ["Numeric", 789]]])
puts
puts eralloc(["Add",
        ["Add", ["Add", ["Variable", "foo"], ["Numeric", 222]],
                ["Add", ["Variable", "bar"], ["Numeric", 111]]],
        ["Add", ["Numeric", 123], ["Numeric", 789]]])
puts
puts eralloc(["Mul", ["Variable", "foo"], ["Numeric", 456]])
puts
puts eralloc(["Mul", ["Variable", "foo"], ["Mul", ["Numeric", 456], ["Numeric", 789]]])
puts
puts eralloc(["Sub", ["Numeric", 123], ["Numeric", 456]])
puts
puts eralloc(["Sub", ["Variable", "foo"], ["Numeric", 456]])
puts
puts eralloc(["Sub", ["Numeric", 456], ["Variable", "foo"]])
puts
puts eralloc(["Sub", ["Variable", "foo"], ["Numeric", 456]])
puts
puts eralloc(["Mul",
    ["Add", ["Add", ["Variable", "b"], ["Variable", "c"]],
            ["Mul", ["Variable", "f"], ["Variable", "g"]]],
    ["Add", ["Variable", "d"], ["Numeric", 3]]])
puts
puts eralloc(["Mul",
    ["Add", ["Variable", "d"], ["Numeric", 3]],
    ["Add", ["Add", ["Variable", "b"], ["Variable", "c"]],
            ["Mul", ["Variable", "f"], ["Variable", "g"]]]])
puts
puts eralloc(["Sub",
    ["Add", ["Add", ["Variable", "b"], ["Variable", "c"]],
            ["Mul", ["Variable", "f"], ["Variable", "g"]]],
    ["Add", ["Variable", "d"], ["Numeric", 3]]])
puts
puts eralloc(["Sub",
    ["Add", ["Variable", "d"], ["Numeric", 3]],
    ["Add", ["Add", ["Variable", "b"], ["Variable", "c"]],
            ["Mul", ["Variable", "f"], ["Variable", "g"]]]])
puts
