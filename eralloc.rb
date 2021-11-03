#
# Based on Ershov and Sethi-Ullman (which are pretty much the same anyway)
#

class ERAlloc
    attr_reader :max_offset

    def alloc(expr)
        @ershov_labels = {}
        @registers = ['edx', 'ecx', 'eax']
        @max_offset = 0

        self.ershov(expr)
        self.emit(expr, Array.new(@registers), 4)
    end

    def update_offset(offset)
        @max_offset = offset if offset > @max_offset
        offset
    end

    def ershov(expr)
        case expr[0]
        when "Numeric", "RefVar", "RefExt", "Load"
            return @ershov_labels[expr] = 1
        when "Add", "Sub", "Mul", "Div", "Rem", "Shl", "Sra", "Srl", "Store"
            lhs = self.ershov(expr[1])
            rhs = self.ershov(expr[2])

            return @ershov_labels[expr] = lhs == rhs ? lhs + 1 : [lhs, rhs].max
        when "Call"
            # function calls are special because the amount of register (and what
            # registers they use) are dictated by the calling convention.

            # Regardless of calling convention, we must compute the call site as
            # well as the arguments to the call.
            expr[2..].each {|arg| self.ershov(arg)}
            return @ershov_labels[expr] = self.ershov(expr[1])
        end
    end

    def emit(expr, free_regs, base_offset)
        case expr[0]
        when "Numeric"
            return ["mov #{free_regs.last}, #{expr[1]}"]
        when "RefVar"
            return ["lea #{free_regs.last}, [ebp-#{expr[1]}]"]
        when "RefExt"
            return [
                "extern #{expr[1]}",
                "mov #{free_regs.last}, #{expr[1]}" ]
        when "Load"
            body = self.emit(expr[1], free_regs, base_offset)
            body << "mov #{free_regs.last}, [#{free_regs.last}]"
            return body
        when "Add", "Sub", "Mul", "Store"
            lhs_num = @ershov_labels[expr[1]]
            rhs_num = @ershov_labels[expr[2]]

            body = []
            need_spilling = free_regs.length < 2
            spills = {}
            if need_spilling then
                spilled_regs = @registers - free_regs
                free_regs.unshift(*spilled_regs)
                spilled_regs.each do |r|
                    body << "mov [ebp-#{base_offset}], #{r}";
                    spills[r] = base_offset
                    self.update_offset(base_offset += 4)
                end
            end

            lhs_expr = expr[1]
            rhs_expr = expr[2]

            if lhs_num < rhs_num then
                # swap the left and right expressions and the top two registers
                lhs_expr, rhs_expr = rhs_expr, lhs_expr
                free_regs[-1], free_regs[-2] = free_regs[-2], free_regs[-1]
            end

            body += self.emit(lhs_expr, free_regs, base_offset)
            body += self.emit(rhs_expr, free_regs[..-2], base_offset)

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
            when "Store"
                # It's reversed because it's ["Store", value, ptr], note that
                # the value being stored is propagated as the result, not the
                # pointer.
                body += ["mov [#{free_regs[-2]}], #{free_regs[-1]}"]
            end

            if need_spilling then
                free_regs.shift(spills.length)
                spills.each do |r, offs|
                    body << "mov #{r}, [ebp-#{offs}]"
                end
            end

            return body
        when "Div", "Rem"
            lhs_num = @ershov_labels[expr[1]]
            rhs_num = @ershov_labels[expr[2]]

            body = []
            need_spilling = free_regs.length < 2
            spills = {}
            if need_spilling then
                spilled_regs = @registers - free_regs
                free_regs.unshift(*spilled_regs)
                spilled_regs.each do |r|
                    body << "mov [ebp-#{base_offset}], #{r}";
                    spills[r] = base_offset
                    self.update_offset(base_offset += 4)
                end
            end

            lhs_expr = expr[1]
            rhs_expr = expr[2]

            if lhs_num < rhs_num then
                # swap the left and right expressions and the top two registers
                lhs_expr, rhs_expr = rhs_expr, lhs_expr
                free_regs[-1], free_regs[-2] = free_regs[-2], free_regs[-1]
            end

            body += emit(lhs_expr, free_regs, base_offset)
            body += emit(rhs_expr, free_regs[..-2], base_offset)

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
                self.update_offset(base_offset += 4)
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
            lhs_num = @ershov_labels[expr[1]]
            rhs_num = @ershov_labels[expr[2]]

            body = []
            need_spilling = free_regs.length < 2
            spills = {}
            if need_spilling then
                spilled_regs = @registers - free_regs
                free_regs.unshift(*spilled_regs)
                spilled_regs.each do |r|
                    body << "mov [ebp-#{base_offset}], #{r}";
                    spills[r] = base_offset
                    self.update_offset(base_offset += 4)
                end
            end

            lhs_expr = expr[1]
            rhs_expr = expr[2]

            if lhs_num < rhs_num then
                # swap the left and right expressions and the top two registers
                lhs_expr, rhs_expr = rhs_expr, lhs_expr
                free_regs[-1], free_regs[-2] = free_regs[-2], free_regs[-1]
            end

            body += emit(lhs_expr, free_regs, base_offset)
            body += emit(rhs_expr, free_regs[..-2], base_offset)

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
        when "Call"
            # the simplest way to do this is the following:
            # 1. evaluate the arguments from last to first (also push them in that
            #    order)
            # 2. evaluate the callsite
            # 3. issue the call instruction
            # 4. move the result from eax
            # 5. pop-off all the arguments

            raise "OUT OF REGS" if free_regs.length < 1

            body = []
            expr[2..].reverse_each do |arg|
                body += emit(arg, free_regs, base_offset)
                body << "push #{free_regs.last}"
            end

            body += emit(expr[1], free_regs, base_offset)
            body << "call eax"
            body << "mov #{free_regs.last}, eax" unless free_regs.last == "eax"
            body << "add esp, #{4 * (expr.length - 2)}" unless expr.length < 3

            return body
        end
    end
end

def eralloc(expr)
    v = ERAlloc.new
    body = v.alloc(expr)

    prologue = []
    prologue << "push ebp"
    prologue << "mov ebp, esp"
    prologue << "sub esp, #{v.max_offset}" if v.max_offset > 0

    epilogue = [
        "mov esp, ebp",
        "pop ebp",
        "ret"
    ]

    return prologue + body + epilogue
end

puts eralloc(["Numeric", 123])
puts
puts eralloc(["Load", ["RefVar", "foo"]])
puts
puts eralloc(["Add", ["Numeric", 123], ["Numeric", 456]])
puts
puts eralloc(["Add", ["Load", ["RefVar", "foo"]], ["Numeric", 456]])
puts
puts eralloc(["Add", ["Numeric", 456], ["Load", ["RefVar", "foo"]]])
puts
puts eralloc(["Add", ["Load", ["RefVar", "foo"]], ["Numeric", 456]])
puts
puts eralloc(["Add", ["Load", ["RefVar", "foo"]], ["Add", ["Numeric", 456], ["Numeric", 789]]])
puts
puts eralloc(["Add",
        ["Add", ["Load", ["RefVar", "foo"]], ["Numeric", 456]],
        ["Add", ["Numeric", 123], ["Numeric", 789]]])
puts
puts eralloc(["Add",
        ["Add", ["Add", ["Load", ["RefVar", "foo"]], ["Numeric", 222]],
                ["Add", ["Load", ["RefVar", "bar"]], ["Numeric", 111]]],
        ["Add", ["Numeric", 123], ["Numeric", 789]]])
puts
puts eralloc(["Mul", ["Load", ["RefVar", "foo"]], ["Numeric", 456]])
puts
puts eralloc(["Mul", ["Load", ["RefVar", "foo"]], ["Mul", ["Numeric", 456], ["Numeric", 789]]])
puts
puts eralloc(["Sub", ["Numeric", 123], ["Numeric", 456]])
puts
puts eralloc(["Sub", ["Load", ["RefVar", "foo"]], ["Numeric", 456]])
puts
puts eralloc(["Sub", ["Numeric", 456], ["Load", ["RefVar", "foo"]]])
puts
puts eralloc(["Sub", ["Load", ["RefVar", "foo"]], ["Numeric", 456]])
puts
puts eralloc(["Mul",
    ["Add", ["Add", ["Load", ["RefVar", "b"]], ["Load", ["RefVar", "c"]]],
            ["Mul", ["Load", ["RefVar", "f"]], ["Load", ["RefVar", "g"]]]],
    ["Add", ["Load", ["RefVar", "d"]], ["Numeric", 3]]])
puts
puts eralloc(["Mul",
    ["Add", ["Load", ["RefVar", "d"]], ["Numeric", 3]],
    ["Add", ["Add", ["Load", ["RefVar", "b"]], ["Load", ["RefVar", "c"]]],
            ["Mul", ["Load", ["RefVar", "f"]], ["Load", ["RefVar", "g"]]]]])
puts
puts eralloc(["Sub",
    ["Add", ["Add", ["Load", ["RefVar", "b"]], ["Load", ["RefVar", "c"]]],
            ["Mul", ["Load", ["RefVar", "f"]], ["Load", ["RefVar", "g"]]]],
    ["Add", ["Load", ["RefVar", "d"]], ["Numeric", 3]]])
puts
puts eralloc(["Sub",
    ["Add", ["Load", ["RefVar", "d"]], ["Numeric", 3]],
    ["Add", ["Add", ["Load", ["RefVar", "b"]], ["Load", ["RefVar", "c"]]],
            ["Mul", ["Load", ["RefVar", "f"]], ["Load", ["RefVar", "g"]]]]])
puts
puts eralloc(["Div", ["Load", ["RefVar", "p"]], ["Load", ["RefVar", "q"]]])
puts
puts eralloc(["Div", ["Add", ["Numeric", 10], ["Load", ["RefVar", "p"]]], ["Load", ["RefVar", "q"]]])
puts
puts eralloc(["Div", ["Load", ["RefVar", "p"]], ["Add", ["Numeric", 10], ["Load", ["RefVar", "q"]]]])
puts
puts eralloc(["Div",
        ["Add", ["Numeric", 8], ["Load", ["RefVar", "p"]]],
        ["Add", ["Numeric", 10], ["Load", ["RefVar", "q"]]]])
puts
puts eralloc(["Div",
        ["Div", ["Numeric", 8], ["Load", ["RefVar", "p"]]],
        ["Div", ["Numeric", 10], ["Load", ["RefVar", "q"]]]])
puts
puts eralloc(["Div",
    ["Div", ["Div", ["Load", ["RefVar", "qq"]], ["Load", ["RefVar", "rr"]]],
            ["Div", ["Load", ["RefVar", "ss"]], ["Load", ["RefVar", "tt"]]]],
    ["Div", ["Div", ["Load", ["RefVar", "b"]], ["Load", ["RefVar", "c"]]],
            ["Div", ["Load", ["RefVar", "f"]], ["Load", ["RefVar", "g"]]]]])
puts
puts eralloc(["Shl", ["Load", ["RefVar", "x"]], ["Numeric", 8]])
puts
puts eralloc(["Shl", ["Add", ["Numeric", 10], ["Load", ["RefVar", "p"]]], ["Load", ["RefVar", "q"]]])
puts
puts eralloc(["Shl", ["Load", ["RefVar", "p"]], ["Add", ["Numeric", 10], ["Load", ["RefVar", "q"]]]])
puts
puts eralloc(["Shl",
        ["Add", ["Numeric", 8], ["Load", ["RefVar", "p"]]],
        ["Add", ["Numeric", 10], ["Load", ["RefVar", "q"]]]])
puts
puts eralloc(["Shl",
        ["Shl", ["Numeric", 8], ["Load", ["RefVar", "p"]]],
        ["Shl", ["Numeric", 10], ["Load", ["RefVar", "q"]]]])
puts
puts eralloc(["Shl",
    ["Shl", ["Shl", ["Load", ["RefVar", "qq"]], ["Load", ["RefVar", "rr"]]],
            ["Shl", ["Load", ["RefVar", "ss"]], ["Load", ["RefVar", "tt"]]]],
    ["Shl", ["Shl", ["Load", ["RefVar", "b"]], ["Load", ["RefVar", "c"]]],
            ["Shl", ["Load", ["RefVar", "f"]], ["Load", ["RefVar", "g"]]]]])
puts
puts eralloc(["Call", ["Load", ["RefVar", "foo"]]])
puts
puts eralloc(["Call", ["RefVar", "foo"]])
puts
puts eralloc(["Call", ["RefVar", "foo"], ["Load", ["RefVar", "p"]]])
puts
puts eralloc(["Call", ["RefVar", "foo"], ["Load", ["RefVar", "p"]]])
puts
puts eralloc(["Call", ["RefExt", "foo"], ["Load", ["RefVar", "p"]]])
puts
puts eralloc(["Call", ["Numeric", 7779], ["Load", ["RefVar", "p"]]])
puts
puts eralloc(["Call", ["RefVar", "fn"],
        ["Shl", ["Numeric", 8], ["Load", ["RefVar", "p"]]],
        ["Shl", ["Numeric", 10], ["Load", ["RefVar", "q"]]]])
puts
puts eralloc(["Store", ["Numeric", 7779], ["RefVar", "p"]])
puts
puts eralloc(["Store", ["Store", ["Numeric", 7779], ["RefVar", "q"]], ["RefVar", "p"]])
puts