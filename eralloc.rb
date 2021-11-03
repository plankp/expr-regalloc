#
# Based on Ershov and Sethi-Ullman (which are pretty much the same anyway)
#

def eralloc(expr)
    map = {}
    ershov(expr, map)

    registers = ['edx', 'ecx', 'eax']
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

        body += emit(lhs_expr, map, free_regs, all_regs, base_offset)
        body += emit(rhs_expr, map, free_regs[..-2], all_regs, base_offset)

        if lhs_num < rhs_num then
            # unswap the top two registers
            free_regs[-1], free_regs[-2] = free_regs[-2], free_regs[-1]
        end

        case expr[0]
        when "Add"
            body += ["add #{free_regs[-1]}, #{free_regs[-2]}"]
        when "Sub"
            body += ["sub #{free_regs[-1]}, #{free_regs[-2]}"]
        when "Mul"
            body += ["imul #{free_regs[-1]}, #{free_regs[-2]}"]
        end

        if need_spilling then
            free_regs.shift(spills.length)
            spills.each do |r, offs|
                body << "mov #{r}, [ebp-#{offs}]"
            end
        end

        return body
    when "Div", "Rem"
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

        body += emit(lhs_expr, map, free_regs, all_regs, base_offset)
        body += emit(rhs_expr, map, free_regs[..-2], all_regs, base_offset)

        if lhs_num < rhs_num then
            # unswap the top two registers
            free_regs[-1], free_regs[-2] = free_regs[-2], free_regs[-1]
        end

        divident = free_regs[-1]
        divisor = free_regs[-2]
        idiv_out = expr[0] == "Div" ? "eax" : "edx"

        # 32-bit division via idiv requires:
        # * divident in eax
        # * divisor not in edx
        #
        # the additional constraint is that the result must have the same
        # register as whatever is in the divident.

        # things that do not need emergency spilling:
        # * the divident: we will store the result into it anyway
        # * all free regs: they are not holding anything useful anyway
        emergency_spill = ['eax', 'edx', 'ecx'] - [divident] - free_regs
        emergency_spill.each do |r|
            body << "mov [ebp-#{base_offset}], #{r}"
            base_offset += 4
        end

        # our goal is to make eax the divident and ecx the divisor
        if divisor == "eax"
            if divident == "ecx" then
                body << "xchg eax, ecx"
            else
                # assuming all else is correct, divident cannot be eax
                # (because divisor is already eax...)
                raise "ILLEGAL ALLOCATION" if divident == "eax"

                body << "mov ecx, eax"
                body << "mov eax, #{divident}"
            end
        else
            body << "mov eax, #{divident}" unless "eax" == divident
            body << "mov ecx, #{divisor}" unless "ecx" == divisor
        end

        body << "cdq"
        body << "idiv ecx"
        body << "mov #{divident}, #{idiv_out}" unless divident == idiv_out
        emergency_spill.reverse_each do |r|
            base_offset -= 4
            body << "mov #{r}, [ebp-#{base_offset}]"
        end

        if need_spilling then
            free_regs.shift(spills.length)
            spills.each do |r, offs|
                body << "mov #{r}, [ebp-#{offs}]"
            end
        end

        return body
    when "Shl", "Sra", "Srl"
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

        body += emit(lhs_expr, map, free_regs, all_regs, base_offset)
        body += emit(rhs_expr, map, free_regs[..-2], all_regs, base_offset)

        if lhs_num < rhs_num then
            # unswap the top two registers
            free_regs[-1], free_regs[-2] = free_regs[-2], free_regs[-1]
        end

        shift_op = { "Shl" => "shl", "Sra" => "sar", "Srl" => "shr" }[expr[0]]
        shifted = free_regs[-1]
        shamt = free_regs[-2]

        # shifting requires the shamt to be in ecx (specific cl, which is the
        # low 16 bits)
        #
        # the additional constraint is that the result must have the same
        # register as whatever is in shifted.

        if shamt == "ecx" then
            body << "#{shift_op} #{shifted}, cl"
        elsif shifted == "ecx" then
            body << "xchg ecx, #{shamt}"
            body << "#{shift_op} #{shamt}, cl"
            body << "mov ecx, #{shamt}"
        elsif free_regs.include?("ecx")
            # we can just wipe ecx
            body << "mov ecx, #{shamt}"
            body << "#{shift_op} #{shifted}, cl"
        else
            # we need to save ecx, and since of the registers is free anyways,
            # just use xchg twice and avoid the load-store.
            body << "xchg ecx, #{shamt}"
            body << "#{shift_op} #{shifted}, cl"
            body << "xchg ecx, #{shamt}"
        end

        if need_spilling then
            free_regs.shift(spills.length)
            spills.each do |r, offs|
                body << "mov #{r}, [ebp-#{offs}]"
            end
        end

        return body
    end
end

def ershov(expr, map)
    case expr[0]
    when "Numeric", "Variable"
        return map[expr] = 1
    when "Add", "Sub", "Mul", "Div", "Rem", "Shl", "Sra", "Srl"
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
puts eralloc(["Div", ["Variable", "p"], ["Variable", "q"]])
puts
puts eralloc(["Div", ["Add", ["Numeric", 10], ["Variable", "p"]], ["Variable", "q"]])
puts
puts eralloc(["Div", ["Variable", "p"], ["Add", ["Numeric", 10], ["Variable", "q"]]])
puts
puts eralloc(["Div",
        ["Add", ["Numeric", 8], ["Variable", "p"]],
        ["Add", ["Numeric", 10], ["Variable", "q"]]])
puts
puts eralloc(["Div",
        ["Div", ["Numeric", 8], ["Variable", "p"]],
        ["Div", ["Numeric", 10], ["Variable", "q"]]])
puts
puts eralloc(["Div",
    ["Div", ["Div", ["Variable", "qq"], ["Variable", "rr"]],
            ["Div", ["Variable", "ss"], ["Variable", "tt"]]],
    ["Div", ["Div", ["Variable", "b"], ["Variable", "c"]],
            ["Div", ["Variable", "f"], ["Variable", "g"]]]])
puts
puts eralloc(["Shl", ["Variable", "x"], ["Numeric", 8]])
puts
puts eralloc(["Shl", ["Add", ["Numeric", 10], ["Variable", "p"]], ["Variable", "q"]])
puts
puts eralloc(["Shl", ["Variable", "p"], ["Add", ["Numeric", 10], ["Variable", "q"]]])
puts
puts eralloc(["Shl",
        ["Add", ["Numeric", 8], ["Variable", "p"]],
        ["Add", ["Numeric", 10], ["Variable", "q"]]])
puts
puts eralloc(["Shl",
        ["Shl", ["Numeric", 8], ["Variable", "p"]],
        ["Shl", ["Numeric", 10], ["Variable", "q"]]])
puts
puts eralloc(["Shl",
    ["Shl", ["Shl", ["Variable", "qq"], ["Variable", "rr"]],
            ["Shl", ["Variable", "ss"], ["Variable", "tt"]]],
    ["Shl", ["Shl", ["Variable", "b"], ["Variable", "c"]],
            ["Shl", ["Variable", "f"], ["Variable", "g"]]]])
puts
