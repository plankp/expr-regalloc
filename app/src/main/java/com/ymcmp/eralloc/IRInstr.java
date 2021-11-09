package com.ymcmp.eralloc;

import java.util.*;

public final class IRInstr {

    public final String opcode;
    public final List<String> outputs;
    public final List<String> inputs;

    public IRInstr(String opcode, String[] outputs, String[] inputs) {
        this.opcode = opcode;

        this.outputs = Collections.unmodifiableList(Arrays.asList(Arrays.copyOf(outputs, outputs.length)));
        this.inputs = Collections.unmodifiableList(Arrays.asList(Arrays.copyOf(inputs, inputs.length)));
    }

    public static IRInstr makev(String opcode, String out, String... in) {
        return new IRInstr(opcode, new String[]{ out }, in);
    }

    public static IRInstr make(String opcode, String... in) {
        return new IRInstr(opcode, new String[0], in);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        if (!this.outputs.isEmpty()) {
            final Iterator<String> it = this.outputs.iterator();
            sb.append(it.next());
            while (it.hasNext())
                sb.append(", ").append(it.next());

            sb.append(" = ");
        }

        sb.append(this.opcode);

        if (!this.inputs.isEmpty()) {
            final Iterator<String> it = this.inputs.iterator();
            sb.append(' ').append(it.next());
            while (it.hasNext())
                sb.append(", ").append(it.next());
        }

        return sb.toString();
    }
}
