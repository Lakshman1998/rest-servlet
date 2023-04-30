package org.servletsecurity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Either;
import io.vavr.control.Try;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.servletsecurity.Util.Constants;
import org.servletsecurity.Util.Util;
import org.servletsecurity.annotaions.QueryParam;
import org.servletsecurity.annotaions.RequestBody;
import org.servletsecurity.listeners.RequestMatcherLoaderListener;
import org.servletsecurity.web.ControllerHandlerDetail;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

public class DelegateServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
        try {
            ControllerHandlerDetail handlerDetail = RequestMatcherLoaderListener.getInvocationMethod(req.getMethod(), req.getRequestURI());
            Map<String, String> queryMap = this.fetchQueryParamsFromRequest(req);
            Class<?>[] invocationMethodParams = handlerDetail.getMethodArgument().entrySet().stream()
                    .sorted(this::sortMethodArgument)
                    .map(argumentMap -> this.getClassDefinition(argumentMap.getValue()))
                    .toArray(Class[]::new);
            Class<?> invocationClassDefinition = this.getClassDefinition(handlerDetail.getClassName());
            Method invocationMethod = invocationClassDefinition.getMethod(handlerDetail.getMethodName(), invocationMethodParams);
            Parameter[] param = invocationMethod.getParameters();
            long requestBodyCount = this.validateInvocationMethodBasedOnHttpMethodType(param);
            if((HttpMethod.GET.toString().equalsIgnoreCase(req.getMethod()) && requestBodyCount > 0) ||
                    (HttpMethod.POST.toString().equalsIgnoreCase(req.getMethod()) && requestBodyCount > 1)) {
                throw new RuntimeException("Request Body defined in GET method or POST method more than once");
            }
            Object[] argument = this.populateMethodArgument(invocationMethodParams, param, queryMap, req);
            this.invokeMethodAndSendResponse(argument, invocationMethod, invocationClassDefinition, resp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long validateInvocationMethodBasedOnHttpMethodType(Parameter[] parameters) {
        return Arrays.stream(parameters).filter(param -> param.getDeclaredAnnotation(RequestBody.class) != null)
                .toList().size();
    }

    private void invokeMethodAndSendResponse(Object[] argument, Method method, Class<?> handlerClass, HttpServletResponse resp) throws IOException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Object response = method.invoke(handlerClass.getConstructor().newInstance(), argument);
        sendResponse(resp, response);
    }

    private Object[] populateMethodArgument(Class<?>[] invocationArgs, Parameter[] parameters,
                                            Map<String, String> queryMap, HttpServletRequest request) {
        return  IntStream.range(0, parameters.length)
                .mapToObj(index -> {
                    Class<?> args = invocationArgs[index];
                    Parameter parameter = parameters[index];
                    QueryParam queryParam = parameter.getDeclaredAnnotation(QueryParam.class);
                    if(Objects.nonNull(queryParam)) {
                        String name = queryParam.name();
                        String value = queryMap.get(name);
                        if(queryParam.required() && Strings.isNullOrEmpty(value)) {
                            throw new RuntimeException("Query Mapping not found");
                        }
                        return args.cast(value);
                    }
                    RequestBody requestBody = parameter.getDeclaredAnnotation(RequestBody.class);
                    if(Objects.nonNull(requestBody)) {
                        ObjectMapper mapper = new ObjectMapper();
                        try {
                            return mapper.readValue(request.getInputStream(), args);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return null;
                }).toArray(Object[]::new);
    }

    private Map<String, String> fetchQueryParamsFromRequest(HttpServletRequest request) {
        String queryParam = request.getQueryString();
        if(Objects.isNull(queryParam)) return new HashMap<>();
        String[] queryArray = queryParam.split("&");
        return Arrays.asList(queryArray).stream()
                .map(query -> {
                    String[] keyValue = query.split("=");
                    Map<String, String> queryMap = new HashMap<>();
                    queryMap.put(keyValue[0], keyValue[1]);
                    return queryMap;
                })
                .reduce((a, b) -> {
                    a.putAll(b);
                    return a;
                }).orElse(new HashMap<>());
    }

    private Class<?> getClassDefinition(String argumentType) {
        ImmutableMap<String, Class<?>> primitiveMap = ImmutableMap.<String, Class<?>>builder()
                .put("long", long.class)
                .put("int", char.class)
                .put("double", double.class)
                .put("char", char.class)
                .build();
        Class<?> argumentClass = primitiveMap.get(argumentType);
        return Objects.nonNull(argumentClass) ? argumentClass : Try.of(() -> Class.forName(argumentType)).get();
    }

    private int sortMethodArgument(Map.Entry<Integer, String> a, Map.Entry<Integer, String> b) {
        return a.getKey() - b.getKey();
    }

    protected <T> void sendResponse(HttpServletResponse response, T data) throws IOException {
        response.setContentType(Constants.APPLICATION_JSON);
        PrintWriter writer = response.getWriter();
        writer.print(this.convertToJson(data));
    }

    private <T> String convertToJson(T result) {
        Either<Throwable, String> response = Try.success(result)
                .mapTry(r -> {
                    ObjectMapper mapper = new ObjectMapper();
                    return mapper.writeValueAsString(r);
                }).toEither();
        return Util.unwrapEither(response);
    }
}
