/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.jca.context;

import javax.resource.spi.BootstrapContext;
import javax.resource.spi.work.WorkManager;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.Assert;

/**
 * {@link org.springframework.context.ApplicationContext} implementation
 * for a JCA ResourceAdapter. Needs to be initialized with the JCA
 * {@link javax.resource.spi.BootstrapContext}, passing it on to
 * Spring-managed beans that implement {@link BootstrapContextAware}.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see SpringContextResourceAdapter
 * @see BootstrapContextAware
 */
public class ResourceAdapterApplicationContext extends GenericApplicationContext {

	private final BootstrapContext bootstrapContext;


	/**
	 * Create a new ResourceAdapterApplicationContext for the given BootstrapContext.
	 * @param bootstrapContext the JCA BootstrapContext that the ResourceAdapter
	 * has been started with
	 */
	public ResourceAdapterApplicationContext(BootstrapContext bootstrapContext) {
		Assert.notNull(bootstrapContext, "BootstrapContext must not be null");
		this.bootstrapContext = bootstrapContext;
	}


	/**
	 * ResourceAdapterApplicationContext类为IoC容器中的bean添加了一个BootstrapContextAwareProcessor后处理器，它将BootstrapContext对象注入到容器中的特定bean中，
	 * 以便这些bean能够通过BootstrapContext接口获取到JCA服务的访问权限。
	 * 此外，这个方法还注册了BootstrapContext和WorkManager类型的解决方案，
	 * 以便在需要时可以直接通过这些类型的实例注入到其他Bean中，以满足Bean的依赖需求。
	 * @param beanFactory the bean factory used by the application context
	 * @throws BeansException
	 */
	@Override
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		// 添加BootstrapContextAwareProcessor后处理器，将BootstrapContext注入到特定的Bean中
		beanFactory.addBeanPostProcessor(new BootstrapContextAwareProcessor(this.bootstrapContext));
		// 忽略BootstrapContextAware接口的依赖
		beanFactory.ignoreDependencyInterface(BootstrapContextAware.class);
		// 注册BootstrapContext类型的解决方案，
		// 即将BootstrapContext作为一个可解决的依赖，以便于在需要时直接通过该类型的实例注入到其他Bean中
		beanFactory.registerResolvableDependency(BootstrapContext.class, this.bootstrapContext);

		// JCA WorkManager resolved lazily - may not be available.
		// 注册WorkManager类型的解决方案，WorkManager是一种可选的JavaEE Connector Architecture (JCA)服务，
		// 通过ObjectFactory将WorkManager对象注册到beanFactory中，以便在需要时可以解析为可用的依赖项
		beanFactory.registerResolvableDependency(WorkManager.class,
				(ObjectFactory<WorkManager>) this.bootstrapContext::getWorkManager);
	}

}
