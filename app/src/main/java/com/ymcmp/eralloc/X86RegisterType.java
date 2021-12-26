package com.ymcmp.eralloc;

import java.util.*;
import com.ymcmp.eralloc.ir.RegName;
import static com.ymcmp.eralloc.X86Register.*;

public enum X86RegisterType implements RegisterType {

    GR8 {
        @Override
        public int width() {
            return 8;
        }

        @Override
        public Collection<RegName.Physical> getRegs() {
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
        public Collection<RegName.Physical> getRegs() {
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
        public Collection<RegName.Physical> getRegs() {
            return Collections.unmodifiableList(Arrays.asList(
                // EAX, ECX, EDX, ESI, EDI, EBX));
                EAX, ECX, EDX/*, ESI, EDI, EBX*/));
        }
    };
}