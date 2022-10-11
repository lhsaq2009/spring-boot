/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.boot.context.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.RandomValuePropertySource;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * {@link EnvironmentPostProcessor} that configures the context environment by loading
 * properties from well known file locations. By default properties will be loaded from
 * 'application.properties' and/or 'application.yml' files in the following locations:
 * <ul>
 * <li>file:./config/</li>
 * <li>file:./config/{@literal *}/</li>
 * <li>file:./</li>
 * <li>classpath:config/</li>
 * <li>classpath:</li>
 * </ul>
 * The list is ordered by precedence (properties defined in locations higher in the list
 * override those defined in lower locations).
 * <p>
 * Alternative search locations and names can be specified using
 * {@link #setSearchLocations(String)} and {@link #setSearchNames(String)}.
 * <p>
 * Additional files will also be loaded based on active profiles. For example if a 'web'
 * profile is active 'application-web.properties' and 'application-web.yml' will be
 * considered.
 * <p>
 * The 'spring.config.name' property can be used to specify an alternative name to load
 * and the 'spring.config.location' property can be used to specify alternative search
 * locations or specific files.
 * <p>
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @author Eddú Meléndez
 * @author Madhura Bhave
 * @author Scott Frederick
 * @since 1.0.0
 */
public class ConfigFileApplicationListener implements EnvironmentPostProcessor, SmartApplicationListener, Ordered {		//

	private static final String DEFAULT_PROPERTIES = "defaultProperties";

	// Note the order is from least to most specific (last one wins)
	private static final String DEFAULT_SEARCH_LOCATIONS = "classpath:/,classpath:/config/,file:./,file:./config/*/,file:./config/";

	private static final String DEFAULT_NAMES = "application";

	private static final Set<String> NO_SEARCH_NAMES = Collections.singleton(null);

	private static final Bindable<String[]> STRING_ARRAY = Bindable.of(String[].class);

	private static final Bindable<List<String>> STRING_LIST = Bindable.listOf(String.class);

	private static final Set<String> LOAD_FILTERED_PROPERTY;

	static {
		Set<String> filteredProperties = new HashSet<>();
		filteredProperties.add("spring.profiles.active");
		filteredProperties.add("spring.profiles.include");
		LOAD_FILTERED_PROPERTY = Collections.unmodifiableSet(filteredProperties);
	}

	/**
	 * The "active profiles" property name.
	 */
	public static final String ACTIVE_PROFILES_PROPERTY = "spring.profiles.active";

	/**
	 * The "includes profiles" property name.
	 */
	public static final String INCLUDE_PROFILES_PROPERTY = "spring.profiles.include";

	/**
	 * The "config name" property name.
	 */
	public static final String CONFIG_NAME_PROPERTY = "spring.config.name";

	/**
	 * The "config location" property name.
	 */
	public static final String CONFIG_LOCATION_PROPERTY = "spring.config.location";

	/**
	 * The "config additional location" property name.
	 */
	public static final String CONFIG_ADDITIONAL_LOCATION_PROPERTY = "spring.config.additional-location";

	/**
	 * The default order for the processor.
	 */
	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

	private final DeferredLog logger = new DeferredLog();

	private static final Resource[] EMPTY_RESOURCES = {};

	private static final Comparator<File> FILE_COMPARATOR = Comparator.comparing(File::getAbsolutePath);

	private String searchLocations;

	private String names;

	private int order = DEFAULT_ORDER;

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return ApplicationEnvironmentPreparedEvent.class.isAssignableFrom(eventType)
				|| ApplicationPreparedEvent.class.isAssignableFrom(eventType);
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ApplicationEnvironmentPreparedEvent) {
			onApplicationEnvironmentPreparedEvent((ApplicationEnvironmentPreparedEvent) event);		// =>>
		}
		if (event instanceof ApplicationPreparedEvent) {
			onApplicationPreparedEvent(event);														// =>>
		}
	}

	// 遍历所有的后处理器，依次调用后处理器，执行相应的方法
	private void onApplicationEnvironmentPreparedEvent(ApplicationEnvironmentPreparedEvent event) {
		/*
		 * 使用 SpringFactoriesLoader 加载所有 jar 包的 META-INF/spring.factories 定义的 EnvironmentPostProcessor 类型的后处理器，
		 * 已经在 SpringApplication 实例化的时候加载，这时候从缓存中获取就可以
		 */
		List<EnvironmentPostProcessor> postProcessors = loadPostProcessors();
		// 将 ConfigFileApplicationListener 类对象本身 添加到 postProcessors 列表中
		postProcessors.add(this);
		// 按照 Oreder 值 进行排序
		AnnotationAwareOrderComparator.sort(postProcessors);
		/*
		 * postProcessors = {ArrayList@1531}  size = 5
		 * 		0 = {SystemEnvironmentPropertySourceEnvironmentPostProcessor@1542}
		 * 		1 = {SpringApplicationJsonEnvironmentPostProcessor@1543}
		 * 		2 = {CloudFoundryVcapEnvironmentPostProcessor@1544}
		 * 		3 = {ConfigFileApplicationListener@1529}
		 * 		4 = {DebugAgentEnvironmentPostProcessor@1545}
		 */
		/** {@link ConfigFileApplicationListener#postProcessEnvironment} */
		for (EnvironmentPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessEnvironment(event.getEnvironment(), event.getSpringApplication());
		}
	}

	List<EnvironmentPostProcessor> loadPostProcessors() {
		return SpringFactoriesLoader.loadFactories(EnvironmentPostProcessor.class, getClass().getClassLoader());
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		// 向环境中添加属性源
		addPropertySources(environment, application.getResourceLoader());	// =>>
	}

	private void onApplicationPreparedEvent(ApplicationEvent event) {
		this.logger.switchTo(ConfigFileApplicationListener.class);
		addPostProcessors(((ApplicationPreparedEvent) event).getApplicationContext());
	}

	/**
	 * Add config file property sources to the specified environment.
	 * @param environment the environment to add source to
	 * @param resourceLoader the resource loader
	 * @see #addPostProcessors(ConfigurableApplicationContext)
	 */
	// 向环境中添加属性源，主要是 RandomValuePropertySource 和 application-{profile}.(properties|yml) 配置文件中的属性源
	protected void addPropertySources(ConfigurableEnvironment environment, ResourceLoader resourceLoader) {
		// 添加 RandomValuePropertySource(随机类型) 属性源
		RandomValuePropertySource.addToEnvironment(environment);
		// 从 application-{profile}.(properties|yml) 配置文件中加载属性源
		new Loader(environment, resourceLoader).load();		// =>>
	}

	/**
	 * Add appropriate post-processors to post-configure the property-sources.
	 * @param context the context to configure
	 */
	protected void addPostProcessors(ConfigurableApplicationContext context) {
		context.addBeanFactoryPostProcessor(new PropertySourceOrderingPostProcessor(context));
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * Set the search locations that will be considered as a comma-separated list. Each
	 * search location should be a directory path (ending in "/") and it will be prefixed
	 * by the file names constructed from {@link #setSearchNames(String) search names} and
	 * profiles (if any) plus file extensions supported by the properties loaders.
	 * Locations are considered in the order specified, with later items taking precedence
	 * (like a map merge).
	 * @param locations the search locations
	 */
	public void setSearchLocations(String locations) {
		Assert.hasLength(locations, "Locations must not be empty");
		this.searchLocations = locations;
	}

	/**
	 * Sets the names of the files that should be loaded (excluding file extension) as a
	 * comma-separated list.
	 * @param names the names to load
	 */
	public void setSearchNames(String names) {
		Assert.hasLength(names, "Names must not be empty");
		this.names = names;
	}

	/**
	 * {@link BeanFactoryPostProcessor} to re-order our property sources below any
	 * {@code @PropertySource} items added by the {@link ConfigurationClassPostProcessor}.
	 */
	private static class PropertySourceOrderingPostProcessor implements BeanFactoryPostProcessor, Ordered {

		private ConfigurableApplicationContext context;

		PropertySourceOrderingPostProcessor(ConfigurableApplicationContext context) {
			this.context = context;
		}

		@Override
		public int getOrder() {
			return Ordered.HIGHEST_PRECEDENCE;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
			reorderSources(this.context.getEnvironment());
		}

		private void reorderSources(ConfigurableEnvironment environment) {
			PropertySource<?> defaultProperties = environment.getPropertySources().remove(DEFAULT_PROPERTIES);
			if (defaultProperties != null) {
				environment.getPropertySources().addLast(defaultProperties);
			}
		}

	}

	/**
	 * Loads candidate property sources and configures the active profiles.
	 */
	private class Loader {

		private final Log logger = ConfigFileApplicationListener.this.logger;

		private final ConfigurableEnvironment environment;

		private final PropertySourcesPlaceholdersResolver placeholdersResolver;

		private final ResourceLoader resourceLoader;

		private final List<PropertySourceLoader> propertySourceLoaders;

		private Deque<Profile> profiles;

		private List<Profile> processedProfiles;		// 项目中的配置文件：application-dev.yml、application-mq.yml

		private boolean activatedProfiles;

		private Map<Profile, MutablePropertySources> loaded;

		private Map<DocumentsCacheKey, List<Document>> loadDocumentsCache = new HashMap<>();

		Loader(ConfigurableEnvironment environment, ResourceLoader resourceLoader) {
			this.environment = environment;
			// 初始化占位符解析器
			this.placeholdersResolver = new PropertySourcesPlaceholdersResolver(this.environment);
			// 一般情况下，使用默认的类加载器
			this.resourceLoader = (resourceLoader != null) ? resourceLoader : new DefaultResourceLoader(null);
			// 使用 SpringFactoriesLoader 类加载所有 jar 包下的 META-INF/spring.factories 文件的 PropertySourceLoader 类数组
			// 最终会得到两个实现类，一个是 PropertiesPropertySourceLoader 类，一个是 YamlPropertySourceLoader 类
			// PropertiesPropertySourceLoader 类 支持 properties 和 xml 文件，解析成 Properties，然后封装成 PropertiesPropertySource
			// YamlPropertySourceLoader 类 支持 yml 和 yaml 文件，解析成 Map，然后封装成 MapPropertySource
			this.propertySourceLoaders = SpringFactoriesLoader.loadFactories(PropertySourceLoader.class, getClass().getClassLoader());
		}

		// 加载所有可能的 profile
		void load() {
			FilteredPropertySource.apply(this.environment, DEFAULT_PROPERTIES, LOAD_FILTERED_PROPERTY,
					(defaultProperties) -> {
						// 初始化集合，未处理的数据集合
						this.profiles = new LinkedList<>();
						// 已处理的数据集合
						this.processedProfiles = new LinkedList<>();
						// 被 spring.profiles.active 指定的集合
						this.activatedProfiles = false;
						this.loaded = new LinkedHashMap<>();
						// 4.1 加载存在已经激活的 profiles
						initializeProfiles();		// =>>
						// 遍历 profiles
						while (!this.profiles.isEmpty()) {
							Profile profile = this.profiles.poll();
							// 如果 profile 不是默认指定的 profile，且不为 null
							// 其中，isDefaultProfile 方法体定义 profile != null && !profile.isDefaultProfile()
							if (isDefaultProfile(profile)) {
								addProfileToEnvironment(profile.getName());
							}
							// 4.2 确定搜索范围，获取对应的配置文件名，并使用相应加载器加载
							load(profile, this::getPositiveProfileFilter, addToLoaded(MutablePropertySources::addLast, false));	// =>>
							// 将处理完的 profile 添加到 processedProfiles 列表当中，表示已经处理完成
							this.processedProfiles.add(profile);
						}
						// 采用消极策略的 DocumentFilterFactory 对象进行处理
						load(null, this::getNegativeProfileFilter, addToLoaded(MutablePropertySources::addFirst, true));
						// 顺序颠倒下，保证了优先 add 的是带 profile 的，而默认的 profile 是优先级最低
						addLoadedPropertySources();
						// 更新 activeProfiles 列表
						applyActiveProfiles(defaultProperties);
					});
		}

		/**
		 * Initialize profile information from both the {@link Environment} active
		 * profiles and any {@code spring.profiles.active}/{@code spring.profiles.include}
		 * properties that are already set.
		 */
		// 加载存在已经激活的 profiles
		private void initializeProfiles() {
			// The default profile for these purposes is represented as null. We add it
			// first so that it is processed first and has lowest priority.
			// 默认配置文件为 null，以便优先被处理，具有最小优先级
			this.profiles.add(null);
			Binder binder = Binder.get(this.environment);
			// 4.1.1 判断当前环境是否配置 spring.profiles.active 属性
			// 		 也就是遍历环境中所有的属性源集合，查看是否有名称为 spring.profiles.active 的属性源
			// 		 比如说，在命令行参数当中添加 --spring.profiles.active=dev，
			//       或配置系统属性 System.setProperty("spring.profiles.active","dev") ，那么就会去创建一个 dev 的 profile
			// 		 如果没有，就返回空集；如果有，就添加到 activatedViaProperty 集中
			Set<Profile> activatedViaProperty = getProfiles(binder, ACTIVE_PROFILES_PROPERTY);
			// 4.1.1 判断当前环境是否配置 spring.profiles.include 属性
			// 		 也就是遍历环境中所有的属性源集合，查看是否有名称为 spring.profiles.include 的属性源
			// 		 如果没有，就返回空集；如果有，就添加到 includedViaProperty 集中
			Set<Profile> includedViaProperty = getProfiles(binder, INCLUDE_PROFILES_PROPERTY);
			// 4.1.2 如果没有特别指定的话，就是 application.properties 和 application-default.properties 配置
			// 		 如果特别指定的话，就是 application.properties 和 已经激活的 profile
			// 		 返回该环境其他显式激活的配置文件集，已经添加过了，就不会再重复添加（除了 spring.profiles.active 和 spring.profiles.include 指定的配置以外 ）
			List<Profile> otherActiveProfiles = getOtherActiveProfiles(activatedViaProperty, includedViaProperty);	// =>>
			// 将 otherActiveProfiles 集添加到 profiles 集当中
			this.profiles.addAll(otherActiveProfiles);
			// Any pre-existing active profiles set via property sources (e.g.
			// System properties) take precedence over those added in config files.
			// 将 includedViaProperty 集添加到 profiles 集当中
			this.profiles.addAll(includedViaProperty);
			// 4.1.3 将 activatedViaProperty 集添加到 profiles 集中，以确保 spring.profiles.active 指定的值生效
			// 	     同时移除默认配置
			addActiveProfiles(activatedViaProperty);		// =>>
			// 如果 profiles 集仍然为 null，即没有指定，就会创建默认的 profile
			// 这就说明了为什么 spring.profiles.active 指定的配置文件会和 default 配置文件互斥的原因
			if (this.profiles.size() == 1) { // only has null profile
				for (String defaultProfileName : this.environment.getDefaultProfiles()) {
					Profile defaultProfile = new Profile(defaultProfileName, true);
					this.profiles.add(defaultProfile);
				}
			}
		}

		// 是获取其他显式配置激活的 profile，之前添加的，不再重复添加
		private List<Profile> getOtherActiveProfiles(Set<Profile> activatedViaProperty,
				Set<Profile> includedViaProperty) {
			// 从 activeProfiles 集获取之前注册的激活 profile
			return Arrays.stream(this.environment.getActiveProfiles()).map(Profile::new).filter(
					(profile) -> !activatedViaProperty.contains(profile) && !includedViaProperty.contains(profile))
					.collect(Collectors.toList());
		}

		// 将 activatedViaProperty 集添加到 profiles 队列中
		// 确保 spring.profiles.active 指定的 profile 会生效，也就是迭代的激活的 profiles 会覆写默认的配置 (队列)
		void addActiveProfiles(Set<Profile> profiles) {
			if (profiles.isEmpty()) {
				return;
			}
			// 之前已经将 spring.profiles.active 指定的 profile 添加进去，就不会再次添加
			// 判断激活标志是否为 true
			// 也就是判断 spring.profiles.active 指定的 profile 是否添加到 profiles 队列当中
			if (this.activatedProfiles) {
				if (this.logger.isDebugEnabled()) {
					this.logger.debug("Profiles already activated, '" + profiles + "' will not be applied");
				}
				// 如果之前没有添加，就将 spring.profiles.active 指定的 profile 添加到 profiles 队列当中
				// spring.profiles.active 指定的 profile 也就是 activatedViaProperty 集
				return;
			}
			this.profiles.addAll(profiles);
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Activated activeProfiles " + StringUtils.collectionToCommaDelimitedString(profiles));
			}
			this.activatedProfiles = true;	// 将激活标志置为 false
			// 移除默认指定的 profile
			// 如果此 profile 不为 null，并且是 spring.profiles.default 指定的 profile
			removeUnprocessedDefaultProfiles();
		}

		private void removeUnprocessedDefaultProfiles() {
			this.profiles.removeIf((profile) -> (profile != null && profile.isDefaultProfile()));
		}

		private DocumentFilter getPositiveProfileFilter(Profile profile) {
			return (Document document) -> {
				if (profile == null) {
					return ObjectUtils.isEmpty(document.getProfiles());
				}
				return ObjectUtils.containsElement(document.getProfiles(), profile.getName())
						&& this.environment.acceptsProfiles(Profiles.of(document.getProfiles()));
			};
		}

		private DocumentFilter getNegativeProfileFilter(Profile profile) {
			return (Document document) -> (profile == null && !ObjectUtils.isEmpty(document.getProfiles())
					&& this.environment.acceptsProfiles(Profiles.of(document.getProfiles())));
		}

		private DocumentConsumer addToLoaded(BiConsumer<MutablePropertySources, PropertySource<?>> addMethod,
				boolean checkForExisting) {
			return (profile, document) -> {
				if (checkForExisting) {
					for (MutablePropertySources merged : this.loaded.values()) {
						if (merged.contains(document.getPropertySource().getName())) {
							return;
						}
					}
				}
				MutablePropertySources merged = this.loaded.computeIfAbsent(profile,
						(k) -> new MutablePropertySources());
				addMethod.accept(merged, document.getPropertySource());
			};
		}

		// 确定搜索范围，获取对应的配置文件名，并使用相应的加载器加载
		private void load(Profile profile, DocumentFilterFactory filterFactory, DocumentConsumer consumer) {
			// 获取加载配置文件的路径
			getSearchLocations().forEach((location) -> {
				// 判断指定的搜索范围是否是文件夹
				// 如果是文件夹，需要进一步搜索，找到相应的配置文件
				// 如果不是文件夹，说明有可能一次性提供配置文件，直接去加载即可
				boolean isDirectory = location.endsWith("/");
				// 4.2.2 如果是文件夹，需要调用 getSearchNames() 方法进一步找到配置文件
				// 如果没有用 spring.config.name 指定配置文件的前缀，默认是返回 "application"
				Set<String> names = isDirectory ? getSearchNames() : NO_SEARCH_NAMES;	// =>>
				names.forEach((name) ->
						// 4.2.3 加载相应路径下的配置文件，一般是 {name}-{profile}.(properties|yml)
						load(location, name, profile, filterFactory, consumer));
			});
		}

		// 4.2.3：是针对配置文件的不同前缀，使用不同的方式进行相应的处理
		//  	  此时，前缀等于 location + name

		private void load(String location, String name, Profile profile, DocumentFilterFactory filterFactory,
				DocumentConsumer consumer) {
			// StringUtils.hasText(String str) 方法是用来检查给定字符串是否包含实际文本
			// 也就是判断 name 值是否为""，" "，null ；一般默认值是"application"，所以不会进入 if 语句块当中
			if (!StringUtils.hasText(name)) {
				for (PropertySourceLoader loader : this.propertySourceLoaders) {
					if (canLoadFileExtension(loader, location)) {
						load(loader, location, profile, filterFactory.getDocumentFilter(profile), consumer);
						return;
					}
				}
				throw new IllegalStateException("File extension of config file location '" + location
						+ "' is not known to any PropertySourceLoader. If the location is meant to reference "
						+ "a directory, it must end in '/'");
			}
			// 针对"application"值，使用相应的属性资源加载器 (前面构造 Loader 的时候已经初始化) 进行处理
			// 其中，属性资源加载器有两种
			// 1. PropertiesPropertySourceLoader 类 支持 properties 和 xml 文件，解析成 Properties，然后封装成 PropertiesPropertySource
			// 2. YamlPropertySourceLoader 类 支持 yml 和 yaml 文件，解析成 Map，然后封装成 MapPropertySource
			Set<String> processed = new HashSet<>();
			for (PropertySourceLoader loader : this.propertySourceLoaders) {
				// 返回属性资源加载器可以支持的扩展名
				// PropertiesPropertySourceLoader 加载器支持以"properties"、"xml"为后缀的配置文件
				// YamlPropertySourceLoader 加载器支持以"yml"、"ymal"为后缀的配置文件
				for (String fileExtension : loader.getFileExtensions()) {
					if (processed.add(fileExtension)) {
						loadForFileExtension(loader, location + name, "." + fileExtension, profile, filterFactory,	// ==>
								consumer);
					}
				}
			}
		}

		private boolean canLoadFileExtension(PropertySourceLoader loader, String name) {
			return Arrays.stream(loader.getFileExtensions())
					.anyMatch((fileExtension) -> StringUtils.endsWithIgnoreCase(name, fileExtension));
		}

		// 根据配置文件的绝对路径名（上面已经添加文件扩展名），来加载配置文件
		private void loadForFileExtension(PropertySourceLoader loader, String prefix, String fileExtension,
				Profile profile, DocumentFilterFactory filterFactory, DocumentConsumer consumer) {
			DocumentFilter defaultFilter = filterFactory.getDocumentFilter(null);
			DocumentFilter profileFilter = filterFactory.getDocumentFilter(profile);
			// 如果 profile 不为 null 的话，配置文件名是 {name}-{profile}.fileExtension
			// 比如遍历到第一个 location，使用 PropertiesPropertySourceLoader 加载器加载时
			//     默认情况是 location：file:./config/，name：application，profile：default，fileExtension：properties
			//     此时的 prefix=file:./config/application，profile=default，fileExtension=properties
			//           profileSpecificFile=file:./config/application-default.properties
			if (profile != null) {
				// 确定配置文件的具体文件名 (包括路径和完整的文件名)
				// Try profile-specific file & profile section in profile file (gh-340)
				String profileSpecificFile = prefix + "-" + profile + fileExtension;
				load(loader, profileSpecificFile, profile, defaultFilter, consumer);
				load(loader, profileSpecificFile, profile, profileFilter, consumer);
				// Try profile specific sections in files we've already processed
				for (Profile processedProfile : this.processedProfiles) {
					if (processedProfile != null) {
						String previouslyLoaded = prefix + "-" + processedProfile + fileExtension;
						load(loader, previouslyLoaded, profile, profileFilter, consumer);
					}
				}
			}
			// Also try the profile-specific section (if any) of the normal file
			load(loader, prefix + fileExtension, profile, profileFilter, consumer);
		}

		// 将获取配置文件的 Resouce 对象，解析后生成 PropertySource 对象，封装到 Document 对象中
		private void load(PropertySourceLoader loader, String location, Profile profile, DocumentFilter filter,
				DocumentConsumer consumer) {
			// 获取指定路径匹配的 Resource 实例
			Resource[] resources = getResources(location);
			for (Resource resource : resources) {
				try {
					// 如果存在 Resource 实例，并且不为 null
					if (resource == null || !resource.exists()) {
						if (this.logger.isTraceEnabled()) {
							StringBuilder description = getDescription("Skipped missing config ", location, resource,
									profile);
							this.logger.trace(description);
						}
						continue;
					}
					// getFilename() 方法返回资源的文件名
					// getFilenameExtension(String path) 方法从给定的资源路径获取扩展文件名
					// 一般就是属性资源加载器所支持的 fileExtension，比如"properties"、"xml"、"yml"、"yaml"
					// 判断配置文件的后缀是否存在，如果不存在，会打印日志堆栈信息，方便追踪调试
					if (!StringUtils.hasText(StringUtils.getFilenameExtension(resource.getFilename()))) {
						if (this.logger.isTraceEnabled()) {
							StringBuilder description = getDescription("Skipped empty config extension ", location,
									resource, profile);
							this.logger.trace(description);
						}
						continue;
					}
					if (resource.isFile() && isPatternLocation(location) && hasHiddenPathElement(resource)) {
						if (this.logger.isTraceEnabled()) {
							StringBuilder description = getDescription("Skipped location with hidden path element ",
									location, resource, profile);
							this.logger.trace(description);
						}
						continue;
					}
					// 此时 name：applicationConfig:[profileSpecificFile]
					// 比如 applicationConfig[file:./config/application-default.properties]
					String name = "applicationConfig: [" + getLocationName(location, resource) + "]";
					List<Document> documents = loadDocuments(loader, name, resource);
					if (CollectionUtils.isEmpty(documents)) {
						if (this.logger.isTraceEnabled()) {
							StringBuilder description = getDescription("Skipped unloaded config ", location, resource,
									profile);
							this.logger.trace(description);
						}
						continue;
					}
					List<Document> loaded = new ArrayList<>();
					for (Document document : documents) {
						if (filter.match(document)) {
							addActiveProfiles(document.getActiveProfiles());
							addIncludedProfiles(document.getIncludeProfiles());
							loaded.add(document);
						}
					}
					Collections.reverse(loaded);
					if (!loaded.isEmpty()) {
						loaded.forEach((document) -> consumer.accept(profile, document));
						if (this.logger.isDebugEnabled()) {
							StringBuilder description = getDescription("Loaded config file ", location, resource,
									profile);
							this.logger.debug(description);
						}
					}
				}
				catch (Exception ex) {
					StringBuilder description = getDescription("Failed to load property source from ", location,
							resource, profile);
					throw new IllegalStateException(description.toString(), ex);
				}
			}
		}

		private boolean hasHiddenPathElement(Resource resource) throws IOException {
			String cleanPath = StringUtils.cleanPath(resource.getFile().getAbsolutePath());
			for (Path value : Paths.get(cleanPath)) {
				if (value.toString().startsWith("..")) {
					return true;
				}
			}
			return false;
		}

		private String getLocationName(String location, Resource resource) {
			if (!location.contains("*")) {
				return location;
			}
			if (resource instanceof FileSystemResource) {
				return ((FileSystemResource) resource).getPath();
			}
			return resource.getDescription();
		}

		private Resource[] getResources(String location) {
			try {
				if (isPatternLocation(location)) {
					return getResourcesFromPatternLocation(location);
				}
				return new Resource[] { this.resourceLoader.getResource(location) };
			}
			catch (Exception ex) {
				return EMPTY_RESOURCES;
			}
		}

		private boolean isPatternLocation(String location) {
			return location.contains("*");
		}

		private Resource[] getResourcesFromPatternLocation(String location) throws IOException {
			String directoryPath = location.substring(0, location.indexOf("*/"));
			Resource resource = this.resourceLoader.getResource(directoryPath);
			File[] files = resource.getFile().listFiles(File::isDirectory);
			if (files != null) {
				String fileName = location.substring(location.lastIndexOf("/") + 1);
				Arrays.sort(files, FILE_COMPARATOR);
				return Arrays.stream(files).map((file) -> file.listFiles((dir, name) -> name.equals(fileName)))
						.filter(Objects::nonNull).flatMap((Function<File[], Stream<File>>) Arrays::stream)
						.map(FileSystemResource::new).toArray(Resource[]::new);
			}
			return EMPTY_RESOURCES;
		}

		private void addIncludedProfiles(Set<Profile> includeProfiles) {
			LinkedList<Profile> existingProfiles = new LinkedList<>(this.profiles);
			this.profiles.clear();
			this.profiles.addAll(includeProfiles);
			this.profiles.removeAll(this.processedProfiles);
			this.profiles.addAll(existingProfiles);
		}

		private List<Document> loadDocuments(PropertySourceLoader loader, String name, Resource resource)
				throws IOException {
			DocumentsCacheKey cacheKey = new DocumentsCacheKey(loader, resource);
			List<Document> documents = this.loadDocumentsCache.get(cacheKey);
			if (documents == null) {
				List<PropertySource<?>> loaded = loader.load(name, resource);
				documents = asDocuments(loaded);
				this.loadDocumentsCache.put(cacheKey, documents);
			}
			return documents;
		}

		private List<Document> asDocuments(List<PropertySource<?>> loaded) {
			if (loaded == null) {
				return Collections.emptyList();
			}
			return loaded.stream().map((propertySource) -> {
				Binder binder = new Binder(ConfigurationPropertySources.from(propertySource),
						this.placeholdersResolver);
				String[] profiles = binder.bind("spring.profiles", STRING_ARRAY).orElse(null);
				Set<Profile> activeProfiles = getProfiles(binder, ACTIVE_PROFILES_PROPERTY);
				Set<Profile> includeProfiles = getProfiles(binder, INCLUDE_PROFILES_PROPERTY);
				return new Document(propertySource, profiles, activeProfiles, includeProfiles);
			}).collect(Collectors.toList());
		}

		private StringBuilder getDescription(String prefix, String location, Resource resource, Profile profile) {
			StringBuilder result = new StringBuilder(prefix);
			try {
				if (resource != null) {
					String uri = resource.getURI().toASCIIString();
					result.append("'");
					result.append(uri);
					result.append("' (");
					result.append(location);
					result.append(")");
				}
			}
			catch (IOException ex) {
				result.append(location);
			}
			if (profile != null) {
				result.append(" for profile ");
				result.append(profile);
			}
			return result;
		}

		private Set<Profile> getProfiles(Binder binder, String name) {
			return binder.bind(name, STRING_ARRAY).map(this::asProfileSet).orElse(Collections.emptySet());
		}

		private Set<Profile> asProfileSet(String[] profileNames) {
			List<Profile> profiles = new ArrayList<>();
			for (String profileName : profileNames) {
				profiles.add(new Profile(profileName));
			}
			return new LinkedHashSet<>(profiles);
		}

		private void addProfileToEnvironment(String profile) {
			for (String activeProfile : this.environment.getActiveProfiles()) {
				if (activeProfile.equals(profile)) {
					return;
				}
			}
			this.environment.addActiveProfile(profile);
		}

		/*
		 * 该方法的作用是获取搜索范围，从以下三个角度
		 *    1. spring.config.location 指定的路径
		 *    2. spring.config.addition-location 指定的路径
		 *    3. 默认路径（file:./config/，file:./，classpath:/config/，classpath:/）
		 */
		private Set<String> getSearchLocations() {
			Set<String> locations = getSearchLocations(CONFIG_ADDITIONAL_LOCATION_PROPERTY);
			// 如果环境中有名为 spring.config.location 的属性源
			// spring.config.location 配置指定了搜索范围，则以它指定的为准
			if (this.environment.containsProperty(CONFIG_LOCATION_PROPERTY)) {
				// 获取 spring.config.location 指定的搜索范围
				locations.addAll(getSearchLocations(CONFIG_LOCATION_PROPERTY));
			}
			else {
				// 获取 spring.config.additional-location 指定的搜索范围
				// 将默认的搜索范围添加进去
				// 1. file:./config/
				// 2. file:./
				// 3. classpath:/config/
				// 4. classpath:/
				locations.addAll(asResolvedSet(ConfigFileApplicationListener.this.searchLocations, DEFAULT_SEARCH_LOCATIONS));
			}
			return locations;
		}

		// 从指定的值获取搜索范围
		private Set<String> getSearchLocations(String propertyName) {
			Set<String> locations = new LinkedHashSet<>();
			// 判断环境当中是否存在该属性
			if (this.environment.containsProperty(propertyName)) {
				// 如果存在该属性，则开始遍历解析后的值
				for (String path : asResolvedSet(this.environment.getProperty(propertyName), null)) {
					// 如果路径不存在 "$"，一般是占位符
					if (!path.contains("$")) {
						path = StringUtils.cleanPath(path);
						// 如果路径不是以 url 形式表示，即只有提供相对路径
						// 则添加前缀 "file:"，变成绝对路径，默认从文件系统加载
						Assert.state(!path.startsWith(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX),
								"Classpath wildcard patterns cannot be used as a search location");
						validateWildcardLocation(path);
						if (!ResourceUtils.isUrl(path)) {
							path = ResourceUtils.FILE_URL_PREFIX + path;
						}
					}
					locations.add(path);
				}
			}
			return locations;
		}

		private void validateWildcardLocation(String path) {
			if (path.contains("*")) {
				Assert.state(StringUtils.countOccurrencesOf(path, "*") == 1,
						() -> "Search location '" + path + "' cannot contain multiple wildcards");
				String directoryPath = path.substring(0, path.lastIndexOf("/") + 1);
				Assert.state(directoryPath.endsWith("*/"), () -> "Search location '" + path + "' must end with '*/'");
			}
		}

		// 获取配置文件的前缀中 name 对应的值，即 {name}-{profile}.(properties|yml) 前面的 name 值，一般默认是 application
		private Set<String> getSearchNames() {
			// 如果环境中包含 spring.config.name 指定的属性值
			if (this.environment.containsProperty(CONFIG_NAME_PROPERTY)) {
				// 获取 spirng.config.name 指定的属性值
				String property = this.environment.getProperty(CONFIG_NAME_PROPERTY);
				// 返回 spirng.config.name 指定的配置文件前缀
				Set<String> names = asResolvedSet(property, null);
				names.forEach(this::assertValidConfigName);
				return names;
			}
			// 返回默认的配置文件前缀 "application"
			return asResolvedSet(ConfigFileApplicationListener.this.names, DEFAULT_NAMES);
		}

		private Set<String> asResolvedSet(String value, String fallback) {
			List<String> list = Arrays.asList(StringUtils.trimArrayElements(StringUtils.commaDelimitedListToStringArray(
					(value != null) ? this.environment.resolvePlaceholders(value) : fallback)));
			Collections.reverse(list);
			return new LinkedHashSet<>(list);
		}

		private void assertValidConfigName(String name) {
			Assert.state(!name.contains("*"), () -> "Config name '" + name + "' cannot contain wildcards");
		}

		private void addLoadedPropertySources() {
			MutablePropertySources destination = this.environment.getPropertySources();
			List<MutablePropertySources> loaded = new ArrayList<>(this.loaded.values());
			Collections.reverse(loaded);
			String lastAdded = null;
			Set<String> added = new HashSet<>();
			for (MutablePropertySources sources : loaded) {
				for (PropertySource<?> source : sources) {
					if (added.add(source.getName())) {
						addLoadedPropertySource(destination, lastAdded, source);
						lastAdded = source.getName();
					}
				}
			}
		}

		private void addLoadedPropertySource(MutablePropertySources destination, String lastAdded,
				PropertySource<?> source) {
			if (lastAdded == null) {
				if (destination.contains(DEFAULT_PROPERTIES)) {
					destination.addBefore(DEFAULT_PROPERTIES, source);
				}
				else {
					destination.addLast(source);
				}
			}
			else {
				destination.addAfter(lastAdded, source);
			}
		}

		private void applyActiveProfiles(PropertySource<?> defaultProperties) {
			List<String> activeProfiles = new ArrayList<>();
			if (defaultProperties != null) {
				Binder binder = new Binder(ConfigurationPropertySources.from(defaultProperties),
						new PropertySourcesPlaceholdersResolver(this.environment));
				activeProfiles.addAll(getDefaultProfiles(binder, "spring.profiles.include"));
				if (!this.activatedProfiles) {
					activeProfiles.addAll(getDefaultProfiles(binder, "spring.profiles.active"));
				}
			}
			this.processedProfiles.stream().filter(this::isDefaultProfile).map(Profile::getName)
					.forEach(activeProfiles::add);
			this.environment.setActiveProfiles(activeProfiles.toArray(new String[0]));
		}

		private boolean isDefaultProfile(Profile profile) {
			return profile != null && !profile.isDefaultProfile();
		}

		private List<String> getDefaultProfiles(Binder binder, String property) {
			return binder.bind(property, STRING_LIST).orElse(Collections.emptyList());
		}

	}

	/**
	 * A Spring Profile that can be loaded.
	 */
	private static class Profile {

		private final String name;

		private final boolean defaultProfile;

		Profile(String name) {
			this(name, false);
		}

		Profile(String name, boolean defaultProfile) {
			Assert.notNull(name, "Name must not be null");
			this.name = name;
			this.defaultProfile = defaultProfile;
		}

		String getName() {
			return this.name;
		}

		boolean isDefaultProfile() {
			return this.defaultProfile;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (obj == null || obj.getClass() != getClass()) {
				return false;
			}
			return ((Profile) obj).name.equals(this.name);
		}

		@Override
		public int hashCode() {
			return this.name.hashCode();
		}

		@Override
		public String toString() {
			return this.name;
		}

	}

	/**
	 * Cache key used to save loading the same document multiple times.
	 */
	private static class DocumentsCacheKey {

		private final PropertySourceLoader loader;

		private final Resource resource;

		DocumentsCacheKey(PropertySourceLoader loader, Resource resource) {
			this.loader = loader;
			this.resource = resource;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			DocumentsCacheKey other = (DocumentsCacheKey) obj;
			return this.loader.equals(other.loader) && this.resource.equals(other.resource);
		}

		@Override
		public int hashCode() {
			return this.loader.hashCode() * 31 + this.resource.hashCode();
		}

	}

	/**
	 * A single document loaded by a {@link PropertySourceLoader}.
	 */
	private static class Document {

		private final PropertySource<?> propertySource;

		private String[] profiles;

		private final Set<Profile> activeProfiles;

		private final Set<Profile> includeProfiles;

		Document(PropertySource<?> propertySource, String[] profiles, Set<Profile> activeProfiles,
				Set<Profile> includeProfiles) {
			this.propertySource = propertySource;
			this.profiles = profiles;
			this.activeProfiles = activeProfiles;
			this.includeProfiles = includeProfiles;
		}

		PropertySource<?> getPropertySource() {
			return this.propertySource;
		}

		String[] getProfiles() {
			return this.profiles;
		}

		Set<Profile> getActiveProfiles() {
			return this.activeProfiles;
		}

		Set<Profile> getIncludeProfiles() {
			return this.includeProfiles;
		}

		@Override
		public String toString() {
			return this.propertySource.toString();
		}

	}

	/**
	 * Factory used to create a {@link DocumentFilter}.
	 */
	@FunctionalInterface
	private interface DocumentFilterFactory {

		/**
		 * Create a filter for the given profile.
		 * @param profile the profile or {@code null}
		 * @return the filter
		 */
		DocumentFilter getDocumentFilter(Profile profile);

	}

	/**
	 * Filter used to restrict when a {@link Document} is loaded.
	 */
	@FunctionalInterface
	private interface DocumentFilter {

		boolean match(Document document);

	}

	/**
	 * Consumer used to handle a loaded {@link Document}.
	 */
	@FunctionalInterface
	private interface DocumentConsumer {

		void accept(Profile profile, Document document);

	}

}
