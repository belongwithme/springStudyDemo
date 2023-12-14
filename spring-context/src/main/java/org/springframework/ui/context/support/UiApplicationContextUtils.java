/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.ui.context.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationContext;
import org.springframework.ui.context.HierarchicalThemeSource;
import org.springframework.ui.context.ThemeSource;

/**
 * Utility class for UI application context implementations.
 * Provides support for a special bean named "themeSource",
 * of type {@link org.springframework.ui.context.ThemeSource}.
 *
 * @author Jean-Pierre Pawlak
 * @author Juergen Hoeller
 * @since 17.06.2003
 */
public abstract class UiApplicationContextUtils {

	/**
	 * Name of the ThemeSource bean in the factory.
	 * If none is supplied, theme resolution is delegated to the parent.
	 * @see org.springframework.ui.context.ThemeSource
	 */
	public static final String THEME_SOURCE_BEAN_NAME = "themeSource";


	private static final Log logger = LogFactory.getLog(UiApplicationContextUtils.class);


	/**
	 * Initialize the ThemeSource for the given application context,
	 * autodetecting a bean with the name "themeSource". If no such
	 * bean is found, a default (empty) ThemeSource will be used.
	 * @param context current application context
	 * @return the initialized theme source (will never be {@code null})
	 * @see #THEME_SOURCE_BEAN_NAME
	 */
	/**
	 * 这段代码的主要作用是初始化 ThemeSource 对象。
	 * ThemeSource 是 Spring 中的一个接口，用于管理应用程序中使用的主题（主题是指一组资源，如图像和样式表，可用于控制应用程序的外观）。
	 * @param context
	 * @return
	 */
	public static ThemeSource initThemeSource(ApplicationContext context) {
		//首先检查给定的 ApplicationContext 是否包含名为 THEME_SOURCE_BEAN_NAME 的 bean。
		if (context.containsLocalBean(THEME_SOURCE_BEAN_NAME)) {
			//如果有，我们就从 ApplicationContext 中获取这个 bean
			ThemeSource themeSource = context.getBean(THEME_SOURCE_BEAN_NAME, ThemeSource.class);
			// 然后使其能够感知父 ThemeSource,如果 ApplicationContext 拥有父容器且其父容器是 ThemeSource 类型
			if (context.getParent() instanceof ThemeSource && themeSource instanceof HierarchicalThemeSource) {
				//就将当前获取的 ThemeSource 对象转换成 HierarchicalThemeSource 类型，
				HierarchicalThemeSource hts = (HierarchicalThemeSource) themeSource;
				if (hts.getParentThemeSource() == null) {
					//将父 ThemeSource 设置为当前 ThemeSource 对象的父 ThemeSource。
					hts.setParentThemeSource((ThemeSource) context.getParent());
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Using ThemeSource [" + themeSource + "]");
			}
			return themeSource;
		}
		//如果 ApplicationContext 中不存在名为 THEME_SOURCE_BEAN_NAME 的 bean，
		else {
            //我们就创建一个 HierarchicalThemeSource 对象作为默认的 ThemeSource。
			HierarchicalThemeSource themeSource = null;
			//如果 ApplicationContext 拥有父容器且其父容器是 ThemeSource 类型的话，
			if (context.getParent() instanceof ThemeSource) {
				//创建一个 DelegatingThemeSource 对象作为 HierarchicalThemeSource 对象的代理
				themeSource = new DelegatingThemeSource();
				//并将父 ThemeSource 设置为 DelegatingThemeSource 对象的父 ThemeSource
				themeSource.setParentThemeSource((ThemeSource) context.getParent());
			}
			else {
				//如果 ApplicationContext 没有父容器或其父容器不是 ThemeSource 类型的话，
				//创建一个 ResourceBundleThemeSource 对象作为 HierarchicalThemeSource 对象的默认 ThemeSource。
				themeSource = new ResourceBundleThemeSource();
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate ThemeSource with name '" + THEME_SOURCE_BEAN_NAME +
						"': using default [" + themeSource + "]");
			}
			return themeSource;
		}
	}

}
