# rest-servlet
java servlet with annotations

# java version
supported java version 17

# add it your web.xml

    <context-param>
        <param-name>main-class</param-name>
        <param-value>com.practice.servletsercurityservice.Main</param-value>
    </context-param>

    <context-param>
        <param-name>context-path</param-name>
        <param-value>/api</param-value>
    </context-param>

    <servlet>
        <servlet-name>main-controller</servlet-name>
        <servlet-class>org.servletsecurity.DelegateServlet</servlet-class>
    </servlet>

    <servlet-mapping>
        <servlet-name>main-controller</servlet-name>
        <url-pattern>/api/*</url-pattern>
    </servlet-mapping>

    <listener>
        <listener-class>org.servletsecurity.listeners.RequestMatcherLoaderListener</listener-class>
    </listener>
    
# build
./gradlew clean build -x test


