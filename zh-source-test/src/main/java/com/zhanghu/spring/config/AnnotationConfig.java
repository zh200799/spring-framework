package com.zhanghu.spring.config;

import com.zhanghu.spring.bean.Person;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotationMetadata;

//@Import(Person.class)
//@ImportResource("classpath:beans.xml")
@Import(AnnotationConfig.MyImportRegistrar.class)
@Configuration
public class AnnotationConfig {

//	@Bean
//	public Person person(){
//		Person person = new Person();
//		person.setName("lisi");
//		return person;
//	}

	static class MyImportRegistrar implements ImportBeanDefinitionRegistrar{
		@Override
		public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry, BeanNameGenerator importBeanNameGenerator) {
			RootBeanDefinition rootBeanDefinition = new RootBeanDefinition();
			rootBeanDefinition.setBeanClass(Person.class);
			MutablePropertyValues propertyValues = new MutablePropertyValues();
			PropertyValue pv = new PropertyValue("name", "registrarVal");
			propertyValues.addPropertyValue(pv);
			rootBeanDefinition.setPropertyValues(propertyValues);
			registry.registerBeanDefinition("personBean", rootBeanDefinition);
			ImportBeanDefinitionRegistrar.super.registerBeanDefinitions(importingClassMetadata, registry, importBeanNameGenerator);
		}
	}
}
