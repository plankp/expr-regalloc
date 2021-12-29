package com.ymcmp.eralloc.target.x86;

import java.util.*;
import com.ymcmp.eralloc.*;
import com.ymcmp.eralloc.ir.Register;
import static com.ymcmp.eralloc.target.x86.X86Register.*;

public enum X86RegisterType implements RegisterType {

    FLAGS {
        @Override
        public int width() {
            // uhh let's say...
            // XXX: Maybe the way to go is to remove width from here...
            return 1;
        }

        @Override
        public Collection<Register.Physical> getRegs() {
            return Collections.singleton(EFLAGS);
        }
    },

    GR8 {
        @Override
        public int width() {
            return 8;
        }

        @Override
        public Collection<Register.Physical> getRegs() {
            return Collections.unmodifiableList(Arrays.asList(
                AL, CL, DL, BL));
        }
    },

    GR16 {
        @Override
        public int width() {
            return 16;
        }

        @Override
        public Collection<Register.Physical> getRegs() {
            return Collections.unmodifiableList(Arrays.asList(
                AX, CX, DX, SI, DI, BX));
        }
    },

    GR32 {
        @Override
        public int width() {
            return 32;
        }

        @Override
        public Collection<Register.Physical> getRegs() {
            return Collections.unmodifiableList(Arrays.asList(
                // EAX, ECX, EDX, ESI, EDI, EBX));
                EAX, ECX, EDX/*, ESI, EDI, EBX*/));
        }
    };
}
