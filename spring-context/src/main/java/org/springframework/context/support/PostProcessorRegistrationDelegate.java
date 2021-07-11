/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	private PostProcessorRegistrationDelegate() {
	}

	/**
	 * 1. ApplicationContext 扩展类可以调用 AbstractApplicationContext.addBeanFactoryPostProcessor 方法,将自定义的 BeanFactoryPostProcessor 实现类保存在 ApplicationContext 中
	 * 2. Spring 容器初始化时,1 中杯加入到 ApplicationContext 的 bean 会被有限调用其 postProcessBeanFactory 方法
	 * 3. 自定义 BeanFactoryPostProcessor 接口实现类,也会被找出来,然后调用其 postProcessorBeanFactory 方法
	 * 4. postProcessorBeanFactory 方法调用时,beanFactory 会被作为参数传入,自定义类中可以使用该参数来处理 bean 的定义,达到业务要求
	 * 5. 此时容器还未进行实例化 bean,因此定义的 BeanFactoryProcessor 实现类不要做与 bean 实例有关的操作,而做一些操作 bean 定义相关的操作
	 */
	public static void invokeBeanFactoryPostProcessors( ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {
		// 存储已处理的后置处理器
		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		Set<String> processedBeans = new HashSet<>();

		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			// 存储BeanFactoryPostProcessor的后置处理器
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			// 存储BeanDefinitionRegistryPostProcessor的后置处理器
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			// 根据是否实现了 BeanDefinitionRegistryPostProcessor,判断加入的后置处理器
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor = (BeanDefinitionRegistryPostProcessor) postProcessor;
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					registryProcessors.add(registryProcessor);
				} else {
					// 没有实现 BeanDefinitionRegistryPostProcessor 加入到
					regularPostProcessors.add(postProcessor);
				}
			}

			// 当前要执行的 BeanDefinitionRegistryPostProcessor
			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// Step.1 查询出BeanFactory 中所有实现了 BeanDefinitionRegistryPostProcessors 的接口和 PriorityOrdered 接口的 bean
			// 1. 符合要求的 bean 放到 processedBeans 已处理的后置处理器集合中
			String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			// 根据 PriorityOrdered 接口来排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 排序后的结果 addAll 到 registryProcessors中(实现了BeanDefinitionRegistryPostProcessors的后置处理器)
			registryProcessors.addAll(currentRegistryProcessors);

			// 使用 currentRegistryProcessors 处理所有的 bean
			// 1. 扫描配置类, 2. 解析配置类为BeanDefinition, 3. 将解析后的BeanDefinition放入Map中缓存起来
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());

			// 此时Step.1中  BeanDefinition 解析完成, 清空 currentRegistryProcessors
			currentRegistryProcessors.clear();

			// Step.2 查询出 BeanFactory 中所有实现了 BeanDefinitionRegistryPostProcessors 的接口且未存在于 processedBeans(已处理的后置处理器中) 且实现了 Ordered 接口的 bean
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}

			// 根据 Ordered 接口来排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 排序后的结果 addAll 到 registryProcessors中(实现了BeanDefinitionRegistryPostProcessors的后置处理器)
			registryProcessors.addAll(currentRegistryProcessors);

			// 使用 currentRegistryProcessors 处理所有的 bean
			// 1. 扫描配置类, 2. 解析配置类为BeanDefinition, 3. 将解析后的BeanDefinition放入Map中缓存起来
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
			// 此时Step.2中  BeanDefinition 解析完成, 清空 currentRegistryProcessors
			currentRegistryProcessors.clear();

			// Step.3 查询出 BeanFactory 中所有实现了 BeanDefinitionRegistryPostProcessors 的接口且未存在于 processedBeans(已处理的后置处理器中) 的 bean
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					if (!processedBeans.contains(ppName)) {
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						reiterate = true;
					}
				}
				// 根据排序
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				// 排序后的结果 addAll 到 registryProcessors中(实现了BeanDefinitionRegistryPostProcessors的后置处理器)
				registryProcessors.addAll(currentRegistryProcessors);
				// 使用 currentRegistryProcessors 处理所有的 bean
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
				// 此时Step.3中  BeanDefinition 解析完成, 清空 currentRegistryProcessors
				currentRegistryProcessors.clear();
			}

			// 再次执行BeanFactory后置处理器,完成cglib的代理
			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		} else {
			// Invoke factory processors registered with the context instance.
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		// 找出 BeanFactory 中所有实现了 BeanFactoryPostProcessor接口的 bean
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// 实现了 PriorityOrdered 接口的
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// 实现了 Ordered 接口的
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// 没有实现排序接口的
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			if (processedBeans.contains(ppName)) {
				// 跳过已处理的 bean
			} else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			} else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			} else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		beanFactory.clearMetadataCache();
	}

	public static void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {
		// 获取 beanFactory 中所有实现 BeanPostProcessor 接口的bean名称
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		// 此时虽然注册操作没开始,但是之前已经有部分特殊 bean 已经注册进来,
		// AbstractApplicationContext.prepareBeanFactory注册进来了 ApplicationContextAwareProcessor,ApplicationListenerDetector
		// 因此beanFactory.getBeanPostProcessorCount()返回值不等于 0
		// +1 操作是因为最后会注册一个 ApplicationListenerDetector
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		// BeanPostProcessorChecker 为 BeanPostProcessor 的一个实现类, 用于每个 bean 的初始化完成后做一些简单的检查
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// 存放实现了 PriorityOrdered 接口的 BeanPostProcessor 集合(在意执行顺序)
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// 存放同时实现了 PriorityOrdered 和 MergedBeanDefinitionPostProcessor 接口的 BeanPostProcessor 集合
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		// 存放实现了 Ordered 接口的 bean 名的集合
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// 存放没有实现 PriorityOrdered 和 Ordered 接口的 bean 名的集合(不在意执行顺序)
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				// 实现了 PriorityOrdered 接口的 BeanPostProcessor 的处理
				priorityOrderedPostProcessors.add(pp);
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					// 同时实现了 PriorityOrdered 接口和 MergedBeanDefinitionPostProcessor 接口的 BeanPostProcessor 的处理
					internalPostProcessors.add(pp);
				}
			} else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				// 实现了 Ordered 接口的处理
				orderedPostProcessorNames.add(ppName);
			} else {
				// 没有实现 PriorityOrdered 接口和 Ordered 接口的处理
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// 对实现了 PriorityOrdered 接口的 bean 进行排序
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 将排序后的 priorityOrderedPostProcessors 注册到容器中
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// 存放所有实现 Ordered 接口的 bean
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			// 之前已将同时实现了 PriorityOrdered 接口和 MergedBeanDefinitionPostProcessor 接口的 BeanPostProcessor 加入到 internalPostProcessors
			// 这里将所有实现了 Ordered 接口和 MergedBeanDefinitionPostProcessor 接口的 BeanPostProcessor 加入到 internalPostProcessors
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		// 实现了 Ordered 接口的 bean 进行排序
		sortPostProcessors(orderedPostProcessors, beanFactory);
		// 将排序后的 orderedPostProcessors 注册到容器中
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// 存放所有常规的 BeanPostProcessor, 即没有实现 PriorityOrdered 接口和 Ordered 接口
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			// 将剩余的 BeanPostProcessor 中实现了 MergedBeanDefinitionPostProcessor 接口的,加入到 internalPostProcessors
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		// nonOrderedPostProcessors 注册到容器中
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// 对所有 internalPostProcessors 进行排序
		// Finally, re-register all internal BeanPostProcessors.
		sortPostProcessors(internalPostProcessors, beanFactory);
		// 将所有实现了 MergedBeanDefinitionPostProcessor 接口的 bean 也注册到容器中
		// 不会将之前已经注册过的 bean 再次注册
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// 创建一个 ApplicationListenerDetector 对象并注册到容器中, 也是开始时 +1 的原因
		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		// Nothing to sort?
		if (postProcessors.size() <= 1) {
			return;
		}
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry, ApplicationStartup applicationStartup) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			StartupStep postProcessBeanDefRegistry = applicationStartup.start("spring.context.beandef-registry.post-process")
					.tag("postProcessor", postProcessor::toString);
			// 完成beanDefinition的扫描, 解析, 缓存工作
			postProcessor.postProcessBeanDefinitionRegistry(registry);
			postProcessBeanDefRegistry.end();
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			StartupStep postProcessBeanFactory = beanFactory.getApplicationStartup().start("spring.context.bean-factory.post-process")
					.tag("postProcessor", postProcessor::toString);
			postProcessor.postProcessBeanFactory(beanFactory);
			postProcessBeanFactory.end();
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		if (beanFactory instanceof AbstractBeanFactory) {
			// Bulk addition is more efficient against our CopyOnWriteArrayList there
			((AbstractBeanFactory) beanFactory).addBeanPostProcessors(postProcessors);
		} else {
			for (BeanPostProcessor postProcessor : postProcessors) {
				beanFactory.addBeanPostProcessor(postProcessor);
			}
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
