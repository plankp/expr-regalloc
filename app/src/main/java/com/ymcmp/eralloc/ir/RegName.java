package com.ymcmp.eralloc.ir;

import java.math.BigInteger;
import java.util.*;
import com.ymcmp.eralloc.RegisterType;

public interface RegName {

    public RegisterType getInfo();

    public interface Physical extends RegName {

        public default RegName.Physical getParent() {
            return null;
        }

        public default Set<RegName.Physical> getSubregs() {
            return Collections.emptySet();
        }
    }

    public final class Virtual implements RegName {

        private final BigInteger id;
        private final RegisterType info;

        protected Virtual(BigInteger id, RegisterType info) {
            // get this via an IRContext
            this.id = id;
            this.info = info;
        }

        @Override
        public RegisterType getInfo() {
            return this.info;
        }

        @Override
        public String toString() {
            return '%' + this.id.toString();
        }
    }
}
