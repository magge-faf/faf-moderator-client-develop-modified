package com.faforever.moderatorclient.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.jasminb.jsonapi.ResourceConverter;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.SneakyThrows;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Class.forName;

@Configuration
public class JsonApiConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new Jdk8Module())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean(name = "defaultResourceConverter")
    public ResourceConverter defaultResourceConverter(ObjectMapper objectMapper) {
        return new ResourceConverter(objectMapper, findJsonApiTypes("com.faforever.moderatorclient.api.dto.get", "com.faforever.commons.api.dto", "com.faforever.moderatorclient.common"));
    }

    @Bean(name = "updateResourceConverter")
    public ResourceConverter updateResourceConverter(ObjectMapper objectMapper) {
        return new ResourceConverter(objectMapper, findJsonApiTypes("com.faforever.moderatorclient.api.dto.update"));
    }

    private Class<?>[] findJsonApiTypes(String... scanPackages) {
        List<Class<?>> classes = new ArrayList<>();
        for (String packageName : scanPackages) {
            ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
            provider.addIncludeFilter(new AnnotationTypeFilter(Type.class));
            provider.findCandidateComponents(packageName).stream()
                    .map(this::resolveClass)
                    .forEach(classes::add);
        }
        return classes.toArray(new Class<?>[classes.size()]);
    }

    @SneakyThrows
    private Class<?> resolveClass(BeanDefinition beanDefinition) {
        return forName(beanDefinition.getBeanClassName());
    }
}
