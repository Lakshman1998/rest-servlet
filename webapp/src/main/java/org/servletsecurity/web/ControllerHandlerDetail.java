package org.servletsecurity.web;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import java.util.Map;

@Setter
@Getter
@Builder
@ToString
public class ControllerHandlerDetail {
    private String className;
    private String methodName;
    private Map<Integer, String> methodArgument;
}
