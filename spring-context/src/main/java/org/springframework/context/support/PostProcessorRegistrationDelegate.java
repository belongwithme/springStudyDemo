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
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
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


	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		//如果有BeanDefinitionRegistryPostProcessor的话优先执行
		//存放执行过的beanFactoryPostProcessors。
		Set<String> processedBeans = new HashSet<>();
		//如果是BeanDefinitionRegistry类型的话
		if (beanFactory instanceof BeanDefinitionRegistry) {
            //既然是BeanDefinitionRegistry了，做一个强转。
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			//用于记录常规BeanFactoryPostProcessor——爷爷类型的
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			//用于记录常规BeanDefinitionRegistryPostProcessor——爸爸类型的，优先级高
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();
			//上面两个不同类型的ArrayList分别存放不同类型的BeanFactoryPostProcessor。


            //遍历所有参数传递进来的BeanFactoryPostProcessor（它们并没有作为bean注册在容器中）
			//将所有参数传入的BeanFactoryPostProcessor分为两组：
			//1.如果是BeanDefinitionRegistryPostProcessor——父亲类型的,先强转为BeanDefinitionRegistryPostProcessor（父亲类型）
			// 放入到上面的registryProcessors（父亲类型）
			//2.否则放入为一个常规BeanFactoryPostProcessor（爷爷类型）。
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {

				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					registryProcessors.add(registryProcessor);
				}
				else {
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			//存放当前需要执行，但是还没有执行的。
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			//找父亲类型的BeanFactoryPostProcessor。
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				//父亲类型里面也分优先级
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					//把找到的加到currentRegistryProcessor里面
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					//顺便也给processedBeans里面加上了。（真的是顺便，提前添加上，但是不影响。
					processedBeans.add(ppName);
				}
			}
			//同类别的可能不止一个，这里需要排序一下
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			//把优先级高的放进去（全部父亲类型的）
			registryProcessors.addAll(currentRegistryProcessors);
			//执行，将父亲类型的方法进行循环执行
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			//执行后，把当前执行完的清空。
			currentRegistryProcessors.clear();


			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			//又找一遍父亲类型的，但是找的是order优先级的。
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			registryProcessors.addAll(currentRegistryProcessors);
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			currentRegistryProcessors.clear();

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			//能不能找到生门。
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				//这次要拿的是最低优先级的。
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					//只要没执行过的就行。
					if (!processedBeans.contains(ppName)) {
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						reiterate = true;
					}
				}
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				registryProcessors.addAll(currentRegistryProcessors);
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				currentRegistryProcessors.clear();
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			//爷爷类型的目前还没有。
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}

		else {
			// Invoke factory processors registered with the context instance.
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		//爷爷类型的。
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		//三个优先级。
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
			}
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		//做个排序
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		//执行
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		//order优先级的
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		//排序
		sortPostProcessors(orderedPostProcessors, beanFactory);
		//执行
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		//没有任何优先级的。
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		//没有排序，直接执行。
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		//清除缓存
		//为什么要清除缓存？
		//因为我们调用了getBean，getbean里调用了createbean，可能会导致definition的变化。
		beanFactory.clearMetadataCache();
	}

	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {
		//获取所有实现了BeanPostProcessor接口的Bean的名称
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		// // 注册一个BeanPostProcessorChecker，用于检测是否有Bean在BeanPostProcessor实例化期间就被创建了
		//？？？？
		/**
		 * 当容器中存在 BeanPostProcessor 时，容器在初始化 bean 的过程中会调用 BeanPostProcessor 的方法，
		 * 用于对 bean 进行后置处理。而这个方法会被所有的 BeanPostProcessor 调用，
		 * 包括在初始化 BeanPostProcessor 本身时也会被调用。这个时候就需要一个机制来确保所有 BeanPostProcessor 都可以得到正确的处理，
		 * 即确保所有 BeanPostProcessor 都在所有 bean 实例化前被注册到容器中。
		 *
		 * beanProcessorTargetCount 的值就是为了满足这个需求而计算出来的。
		 * 它的值等于已经注册的 BeanPostProcessor 数量加上 1（为了确保 BeanPostProcessorChecker 也被处理），
		 * 再加上 postProcessorNames 数组的长度（即还未注册到容器的 BeanPostProcessor 数量）。
		 */
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		/**
		 * 接着，beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));
		 * 将一个新的 BeanPostProcessorChecker 实例注册到容器中，
		 * 该实例的主要作用是在初始化 BeanPostProcessor 时检查是否所有的 BeanPostProcessor 都已经被注册到容器中。
		 * 如果未被正确注册，会打印一条警告日志，提醒我们需要在 BeanPostProcessor 的实现中更加注意。
		 */
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		//存放实现了PriorityOrdered接口的BeanPostProcessor，其优先级最高。
		//PriorityOrdered接口作用：用于对BeanPostProcessor进行优先级排序。
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		//存放内部实现的BeanPostProcessor。
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		//存放实现了Ordered接口的BeanPostProcessor，其优先级低于PriorityOrdered接口
		//Ordered接口作用：用于排序功能。
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// 存放除去内部和有优先级的BeanPostProcessor后，剩余的BeanPostProcessor
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		//遍历BeanPostProcessor对应的名字，进行分堆和创建Bean实例操作。
		for (String ppName : postProcessorNames) {
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		//对PriorityOrdered相关的BeanPostProcessor进行排序操作，
		// 排序的依据是重写了PriorityOrdered的getOrder的返回值。
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		//排序后的BeanPostProcessor一次注册到BeanFactory里面。
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
	    //遍历实现了Ordered接口的BeanPostProcessor
		//如果该processor实现了MergedBeanDefinitionPostProcessor接口，
		// 将其加入到内部的BeanPostProcessor集合里面。
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		//对Ordered优先级的PostProcessors进行排序。
		sortPostProcessors(orderedPostProcessors, beanFactory);
		//注册
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		//对无优先级的BeanPostProcessor进行遍历，
		// 将实现了MergedBeanDefinitionPostProcessor接口的加入到内部BeanPostProcessor的集合中
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		//没有先后顺序，一次注册这些Bean实例到BeanFactory里面
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		//最后对所有内部BeanPostProcessor实例进行排序操作，
		//并将其注册到BeanFactory里面
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		//对BeanFactory添加一个全局的监听器。
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
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {
       //遍历 postProcessors 集合的循环，用于遍历所有注册的 BeanFactoryPostProcessor 实例。
		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		for (BeanPostProcessor postProcessor : postProcessors) {
			beanFactory.addBeanPostProcessor(postProcessor);
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
