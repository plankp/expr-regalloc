package com.ymcmp.eralloc;

import java.util.Collection;
import com.ymcmp.eralloc.ir.Register;

public interface RegisterType {

    // Maybe change to long? (BigInteger is probably excessive)
    public int width();

    public Collection<Register.Physical> getRegs();
}
