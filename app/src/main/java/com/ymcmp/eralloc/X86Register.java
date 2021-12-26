package com.ymcmp.eralloc;

import java.util.*;
import com.ymcmp.eralloc.ir.RegName;

public enum X86Register implements RegName.Physical {

    EAX {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR32;
        }

        @Override
        public Set<RegName.Physical> getSubregs() {
            return Collections.unmodifiableSet(EnumSet.of(AX));
        }
    },

    EBX {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR32;
        }

        @Override
        public Set<RegName.Physical> getSubregs() {
            return Collections.unmodifiableSet(EnumSet.of(BX));
        }
    },

    ECX {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR32;
        }

        @Override
        public Set<RegName.Physical> getSubregs() {
            return Collections.unmodifiableSet(EnumSet.of(CX));
        }
    },

    EDX {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR32;
        }

        @Override
        public Set<RegName.Physical> getSubregs() {
            return Collections.unmodifiableSet(EnumSet.of(DX));
        }
    },

    EDI {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR32;
        }

        @Override
        public Set<RegName.Physical> getSubregs() {
            return Collections.unmodifiableSet(EnumSet.of(DI));
        }
    },

    ESI {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR32;
        }

        @Override
        public Set<RegName.Physical> getSubregs() {
            return Collections.unmodifiableSet(EnumSet.of(SI));
        }
    },

    AX {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR16;
        }

        @Override
        public RegName.Physical getParent() {
            return EAX;
        }

        @Override
        public Set<RegName.Physical> getSubregs() {
            return Collections.unmodifiableSet(EnumSet.of(AL));
        }
    },

    BX {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR16;
        }

        @Override
        public RegName.Physical getParent() {
            return EBX;
        }

        @Override
        public Set<RegName.Physical> getSubregs() {
            return Collections.unmodifiableSet(EnumSet.of(BL));
        }
    },

    CX {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR16;
        }

        @Override
        public RegName.Physical getParent() {
            return ECX;
        }

        @Override
        public Set<RegName.Physical> getSubregs() {
            return Collections.unmodifiableSet(EnumSet.of(CL));
        }
    },

    DX {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR16;
        }

        @Override
        public RegName.Physical getParent() {
            return EDX;
        }

        @Override
        public Set<RegName.Physical> getSubregs() {
            return Collections.unmodifiableSet(EnumSet.of(DL));
        }
    },

    DI {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR16;
        }

        @Override
        public RegName.Physical getParent() {
            return EDI;
        }
    },

    SI {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR16;
        }

        @Override
        public RegName.Physical getParent() {
            return ESI;
        }
    },

    AL {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR8;
        }

        @Override
        public RegName.Physical getParent() {
            return AX;
        }
    },

    BL {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR8;
        }

        @Override
        public RegName.Physical getParent() {
            return BX;
        }
    },

    CL {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR8;
        }

        @Override
        public RegName.Physical getParent() {
            return CX;
        }
    },

    DL {
        @Override
        public RegisterType getInfo() {
            return X86RegisterType.GR8;
        }

        @Override
        public RegName.Physical getParent() {
            return DX;
        }
    };
}