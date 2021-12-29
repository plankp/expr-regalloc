package com.ymcmp.eralloc.target.generic;

import java.util.*;
import com.ymcmp.eralloc.*;
import com.ymcmp.eralloc.ir.*;

public abstract class DataType implements RegisterType {

    protected DataType() {
        // Disallow anonymous inner classes
    }

    @Override
    public final Collection<Register.Physical> getRegs() {
        return Collections.emptySet();
    }

    public boolean isInt() {
        return false;
    }

    public boolean isInt(int bits) {
        return false;
    }
}
