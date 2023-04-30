package org.servletsecurity.listeners;

import com.google.common.base.Strings;
import io.vavr.control.Either;
import io.vavr.control.Try;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.servletsecurity.HttpMethod;
import org.servletsecurity.annotaions.ComponentScan;
import org.servletsecurity.annotaions.Get;
import org.servletsecurity.annotaions.Path;
import org.servletsecurity.annotaions.Post;
import org.servletsecurity.web.ControllerHandlerDetail;
import org.servletsecurity.web.WebApplication;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RequestMatcherLoaderListener implements ServletContextListener {

    final Map<String, String> CLASS_ANNOTATED_MAP = new HashMap<>();

    private String CONTEXT_PATH;
    private final static Map<String, Map<String, ControllerHandlerDetail>> REQUEST_MAPPING = new HashMap<>();

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        Either<Throwable, String> result = Try.of(() -> {
            ServletContext servletContext = sce.getServletContext();
            String mainClass = servletContext.getInitParameter("main-class");
            CONTEXT_PATH = servletContext.getInitParameter("context-path");
            CONTEXT_PATH = Strings.isNullOrEmpty(CONTEXT_PATH) ? "/" : CONTEXT_PATH;
            if (Objects.isNull(mainClass) || mainClass.isEmpty()) {
                throw new RuntimeException("Main class is not defined");
            }

            WebApplication webApplication = (WebApplication) Class.forName(mainClass).getConstructor().newInstance();
            ComponentScan componentScan = webApplication.getClass().getAnnotation(ComponentScan.class);

            if (Objects.isNull(componentScan)) throw new RuntimeException("package scan not found");

            String scanPackage = componentScan.value().trim().isEmpty() ? this.getPackageName(mainClass) : componentScan.value();
            this.scanRequestMatcherAndSetInMap(scanPackage);
            return this.printServletStartBanner();
        }).toEither();
        if (result.isLeft()) {
            result.getLeft().printStackTrace();
            throw new RuntimeException(result.getLeft());
        }
    }

    private String printServletStartBanner() {
        return Try.of(() -> {
            ClassPathResource classPathResource = new ClassPathResource("banner.txt");
            String bannerText = new String(classPathResource.getContentAsByteArray());
            System.out.println(bannerText);
            return bannerText;
        }).get();
    }

    private void scanRequestMatcherAndSetInMap(String path) {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage(path))
                .setScanners(Scanners.TypesAnnotated, Scanners.MethodsAnnotated));
        Set<Class<?>> typeList = reflections.getTypesAnnotatedWith(Path.class);
        typeList.forEach(cons -> {
            Path value = cons.getDeclaredAnnotation(Path.class);
            CLASS_ANNOTATED_MAP.put(cons.getName(), value.value());
        });
        Set<Method> getMethodSet = reflections.getMethodsAnnotatedWith(Get.class);
        getMethodSet.forEach(getMethod -> {
            String prefixPath = CLASS_ANNOTATED_MAP.getOrDefault(getMethod.getDeclaringClass().getName(), "/");
            Get get = getMethod.getAnnotation(Get.class);
            prefixPath += get.value();
            Map<String, ControllerHandlerDetail> requestMap = REQUEST_MAPPING.getOrDefault(HttpMethod.GET.toString(), new HashMap<>());
            String pathKey = CONTEXT_PATH + prefixPath.trim();
            if (requestMap.containsKey(pathKey)) throw new RuntimeException("Already Existing api found: " + pathKey);
            requestMap.put(pathKey, this.mapToControllerHandlerDetail(getMethod));
            REQUEST_MAPPING.put(HttpMethod.GET.toString(), requestMap);
        });
        Set<Method> postMethodSet = reflections.getMethodsAnnotatedWith(Post.class);
        postMethodSet.forEach(postMethod -> {
            String prefixPath = CLASS_ANNOTATED_MAP.getOrDefault(postMethod.getDeclaringClass().getName(), "/");
            Post get = postMethod.getAnnotation(Post.class);
            prefixPath += get.value();
            Map<String, ControllerHandlerDetail> requestMap = REQUEST_MAPPING.getOrDefault(HttpMethod.POST.toString(), new HashMap<>());
            String pathKey = CONTEXT_PATH + prefixPath.trim();
            if (requestMap.containsKey(pathKey)) throw new RuntimeException("Already Existing api found: " + pathKey);
            requestMap.put(pathKey, this.mapToControllerHandlerDetail(postMethod));
            REQUEST_MAPPING.put(HttpMethod.POST.toString(), requestMap);
        });

    }


    private ControllerHandlerDetail mapToControllerHandlerDetail(Method method) {
        Parameter[] parameter = method.getParameters();
        AtomicInteger argumentPosition = new AtomicInteger(0);
        Map<Integer, String> parameterMap = Arrays.asList(parameter).stream()
                .map(param -> {
                    Map<Integer, String> paramTypeMap = new HashMap<>();
                    paramTypeMap.put(argumentPosition.getAndIncrement(), param.getType().getName());
                    return paramTypeMap;
                }).flatMap(paramTypeMap -> paramTypeMap.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return ControllerHandlerDetail.builder()
                .className(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .methodArgument(parameterMap)
                .build();
    }

    private String getPackageName(String mainClass) {
        String[] packageWithClass = mainClass.split("\\.");
        if (packageWithClass.length <= 1) {
            throw new RuntimeException("invalid main class defined");
        }
        StringBuilder sb = new StringBuilder();
        IntStream.range(0, packageWithClass.length - 1).forEach(index -> {
            if (sb.isEmpty()) {
                sb.append(packageWithClass[index]);
            } else {
                sb.append(".");
                sb.append(packageWithClass[index]);
            }
        });
        return sb.toString();
    }

    public static ControllerHandlerDetail getInvocationMethod(String method, String path) {
        Map<String, ControllerHandlerDetail> methodMap = REQUEST_MAPPING.get(method);
        return methodMap.get(path.trim());
    }
}
