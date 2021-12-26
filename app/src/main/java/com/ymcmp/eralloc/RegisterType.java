package com.ymcmp.eralloc;

import java.util.Collection;
import com.ymcmp.eralloc.ir.RegName;

public interface RegisterType {

    public int width();

    public Collection<RegName.Physical> getRegs();
}