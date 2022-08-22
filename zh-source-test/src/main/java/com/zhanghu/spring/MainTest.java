package com.zhanghu.spring;

import com.zhanghu.spring.bean.Person;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MainTest {

	public static void main(String[] args) {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("beans.xml");
		Person p = context.getBean(Person.class);
		System.out.println(p.getName());
	}
}
