package com.ymcmp.eralloc;

public final class IRInstr {

    public final String opcode;
    public final String[] outputs;
    public final String[] inputs;

    public IRInstr(String opcode, String[] outputs, String[] inputs) {
        this.opcode = opcode;
        this.outputs = outputs;
        this.inputs = inputs;
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

        if (this.outputs.length > 0) {
            sb.append(this.outputs[0]);
            for (int i = 1; i < this.outputs.length; ++i)
                sb.append(this.outputs[i]);

            sb.append(" = ");
        }

        sb.append(this.opcode);

        if (this.inputs.length > 0) {
            sb.append(' ').append(this.inputs[0]);
            for (int i = 1; i < this.inputs.length; ++i)
                sb.append(", ").append(this.inputs[i]);
        }

        return sb.toString();
    }
}
