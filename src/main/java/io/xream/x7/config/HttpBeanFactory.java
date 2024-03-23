package io.xream.x7.config;


import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.support.RestTemplateAdapter;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;



public class HttpBeanFactory<T>  {

    private final Class<T> interfaceType;

    public HttpBeanFactory(Class<T> interfaceType) {
        this.interfaceType = interfaceType;
    }

    public T getObject() {
        RestTemplate restTemplate = new RestTemplate();
        List<ClientHttpRequestInterceptor> interceptors = restTemplate.getInterceptors();

        interceptors.add((request, body, execution) -> {
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null) {
                ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) requestAttributes;
                HttpServletRequest requestA = servletRequestAttributes.getRequest();
                Enumeration<String> headerNames = requestA.getHeaderNames();
                Iterator<String> stringIterator = headerNames.asIterator();
                while (stringIterator.hasNext()) {
                    String headerName = stringIterator.next();
                    if ("content-length".equalsIgnoreCase(headerName)) {
                        continue;
                    }
                    if ("Authorization".equals(headerName))
                        continue;
                    String value = requestA.getHeader(headerName);
                    if (StringUtils.isBlank(value))
                        continue;
                    if ("content-type".equalsIgnoreCase(headerName)){
                        request.getHeaders().set(headerName, "application/json;charset=UTF-8");
                    }else {
                        request.getHeaders().set(headerName, requestA.getHeader(headerName));
                    }
                }
            }

            return execution.execute(request, body);
        });

        RestTemplateAdapter adapter = RestTemplateAdapter.create(restTemplate);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(interfaceType);
    }

}
