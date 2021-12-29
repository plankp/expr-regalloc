package com.ymcmp.eralloc.ir;

import java.math.BigInteger;
import java.util.*;
import com.ymcmp.eralloc.RegisterType;

public interface Register {

    public RegisterType getInfo();

    public interface Physical extends Register {

        public default Register.Physical getParent() {
            return null;
        }

        public default Set<Register.Physical> getSubregs() {
            return Collections.emptySet();
        }
    }

    public final class Virtual implements Register {

        private final BigInteger id;
        private final RegisterType info;

        private String name;

        protected Virtual(BigInteger id, RegisterType info) {
            // get this via an IRContext
            this.id = id;
            this.info = info;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public RegisterType getInfo() {
            return this.info;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append('%');
            if (this.name != null)
                sb.append(this.name);
            sb.append(this.id);
            return sb.toString();
        }
    }
}
