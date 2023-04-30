package org.servletsecurity.Util;

import io.vavr.control.Either;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Util {

    public <T> T unwrapEither(Either<Throwable, T> data) {
        if(data.isLeft()) {
            throw new RuntimeException(data.getLeft());
        }
        return data.get();
    }
}
