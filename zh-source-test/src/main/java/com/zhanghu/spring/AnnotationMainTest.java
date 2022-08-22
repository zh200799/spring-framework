package com.zhanghu.spring;

import com.zhanghu.spring.bean.Person;
import com.zhanghu.spring.config.AnnotationConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class AnnotationMainTest {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AnnotationConfig.class);
		Person p = context.getBean(Person.class);
		System.out.println(p.getName());
	}
}
