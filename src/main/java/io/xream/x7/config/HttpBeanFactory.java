/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.xream.x7.config;


import io.micrometer.common.util.StringUtils;
import io.xream.x7.base.exception.Remote1xxException;
import io.xream.x7.base.exception.Remote3xxException;
import io.xream.x7.base.exception.Remote4xxException;
import io.xream.x7.base.exception.Remote5xxException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.support.RestTemplateAdapter;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;



public class HttpBeanFactory<T>  {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpBeanFactory.class);
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

            ClientHttpResponse rep = execution.execute(request,body);
            if (rep.getStatusCode().is2xxSuccessful() ) {
                return rep;
            }
            String uri = request.getURI().getHost()+":"+request.getURI().getPort() + request.getURI().getPath();
            if (rep.getStatusCode().is4xxClientError()){
                String bodyStr = toBodyString(rep.getBody());
                LOGGER.error("Request uri:{} failed,statusCode:{},info:{}",uri,rep.getStatusCode().value(),bodyStr);
                throw new Remote4xxException(rep.getStatusCode().value(),uri,bodyStr);
            }else if (rep.getStatusCode().is5xxServerError()){
                String bodyStr = toBodyString(rep.getBody());
                LOGGER.error("Request uri:{} failed,statusCode:{},info:{}",uri,rep.getStatusCode().value(),bodyStr);
                throw new Remote5xxException(rep.getStatusCode().value(),uri,bodyStr);
            }else if (rep.getStatusCode().is3xxRedirection()){
                String bodyStr = toBodyString(rep.getBody());
                LOGGER.error("Request uri:{} failed,statusCode:{},info:{}",uri,rep.getStatusCode().value(),bodyStr);
                throw new Remote3xxException(rep.getStatusCode().value(),uri,bodyStr);
            }else if (rep.getStatusCode().is1xxInformational()){
                String bodyStr = toBodyString(rep.getBody());
                LOGGER.error("Request uri:{} failed,statusCode:{},info:{}",uri,rep.getStatusCode().value(),bodyStr);
                throw new Remote1xxException(rep.getStatusCode().value(),uri,bodyStr);
            }
            return rep;
        });

        RestTemplateAdapter adapter = RestTemplateAdapter.create(restTemplate);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(interfaceType);
    }

    public static String toBodyString(InputStream input) {
        ByteArrayOutputStream output = null;
        try {
            output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024 * 4];
            int n = 0;
            while (-1 != (n = input.read(buffer))) {
                output.write(buffer, 0, n);
            }
            String str = new String(output.toByteArray(),"UTF-8");
            return str;
        }catch (Exception e) {
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new RuntimeException(e.getMessage());
        }finally {
            if (output != null) {
                try {
                    output.close();
                }catch (IOException ioe){
                    throw new RuntimeException(ioe.getMessage());
                }
            }
            if (input !=null) {
                try {
                    input.close();
                }catch (IOException ioe){
                    throw new RuntimeException(ioe.getMessage());
                }
            }
        }
    }


}
