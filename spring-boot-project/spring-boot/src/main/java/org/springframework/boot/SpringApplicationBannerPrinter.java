/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.boot;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;

import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

/**
 * Class used by {@link SpringApplication} to print the application banner.
 *
 * @author Phillip Webb
 */
class SpringApplicationBannerPrinter {

	static final String BANNER_LOCATION_PROPERTY = "spring.banner.location";

	static final String BANNER_IMAGE_LOCATION_PROPERTY = "spring.banner.image.location";

	static final String DEFAULT_BANNER_LOCATION = "banner.txt";

	static final String[] IMAGE_EXTENSION = { "gif", "jpg", "png" };

	private static final Banner DEFAULT_BANNER = new SpringBootBanner();

	private final ResourceLoader resourceLoader;

	private final Banner fallbackBanner;

	SpringApplicationBannerPrinter(ResourceLoader resourceLoader, Banner fallbackBanner) {
		this.resourceLoader = resourceLoader;
		this.fallbackBanner = fallbackBanner;
	}

	Banner print(Environment environment, Class<?> sourceClass, Log logger) {
		Banner banner = getBanner(environment);
		try {
			logger.info(createStringFromBanner(banner, environment, sourceClass));
		}
		catch (UnsupportedEncodingException ex) {
			logger.warn("Failed to create String for banner", ex);
		}
		return new PrintedBanner(banner, sourceClass);
	}

	// 获取 banner 内容 和 输出 banner 内容
	Banner print(Environment environment, Class<?> sourceClass, PrintStream out) {
		// 3.1 获取 banner 内容
		Banner banner = getBanner(environment);		// =>>
		// 3.2 输出 banner 内容
		// 	   根据返回的 banner 类型，输出不同的内容，这就是多态的运用！
		// 	   针对 SpringApplicationBannerPrinter.Banners 类型，遍历 banners 列表，获取 Banner 类型的对象
		// 	   依次调用 ImageBanner 类型或者 ResourceBanner 类型（这两个都是 Banner 类型、又一个多态！）的 print 方法
		// 	   针对 SpringBootBanner 类型，默认的文本输出
		banner.printBanner(environment, sourceClass, out);
		return new PrintedBanner(banner, sourceClass);
	}

	/*
	 * 获取 banner 内容（加载顺序是先图片 banner，然后文本 banner，最后兜底 banner。如果都没有，则返回默认 banner ）
	 * 		1) 针对图片 banner，要么通过 spring.banner.image.location 属性 指定加载 图片 banner 的路径，或
	 * 	       在 resources 目录下存放 banner.gif 或 banner.jpg 或 banner.png 格式的 图片 banner
	 *		2) 针对文本 banner，可以通过 spring.banner.location 属性 指定加载文本 banner 的路径，如果没有加载，
	 *	  	   Spring 会尝试从 resources 目录下的 加载名为 ''banner.txt'' 的资源，如果没有，则返回
	 *		3) 如果说 图片 banner 和 文本 banner 都没加载到，则去查看 兜底 banner 是否存在，
	 *	       (兜底 banner 在启动类中手动加载，比如 springApplication.setBanner(newResourceBanner(newClassPathResource("favorite.txt"))) 这行代码）
	 * 上面三个 banner 都不存在的话，返回 默认 banner
	 */
	private Banner getBanner(Environment environment) {
		Banners banners = new Banners();
		// 3.1.1 尝试加载图片 banner
		banners.addIfNotNull(getImageBanner(environment));
		// 3.1.2 尝试加载文本 banner
		banners.addIfNotNull(getTextBanner(environment));
		// 如果 banners 列表中存在至少一个 banner，直接返回 banners
		if (banners.hasAtLeastOneBanner()) {
			return banners;
		}
		// 尝试加载兜底 banner，如果失败，返回默认 banner
		if (this.fallbackBanner != null) {
			return this.fallbackBanner;
		}
		return DEFAULT_BANNER;
	}

	private Banner getTextBanner(Environment environment) {
		String location = environment.getProperty(BANNER_LOCATION_PROPERTY, DEFAULT_BANNER_LOCATION);
		Resource resource = this.resourceLoader.getResource(location);
		if (resource.exists()) {
			return new ResourceBanner(resource);
		}
		return null;
	}

	private Banner getImageBanner(Environment environment) {
		String location = environment.getProperty(BANNER_IMAGE_LOCATION_PROPERTY);
		if (StringUtils.hasLength(location)) {
			Resource resource = this.resourceLoader.getResource(location);
			return resource.exists() ? new ImageBanner(resource) : null;
		}
		for (String ext : IMAGE_EXTENSION) {
			Resource resource = this.resourceLoader.getResource("banner." + ext);
			if (resource.exists()) {
				return new ImageBanner(resource);
			}
		}
		return null;
	}

	private String createStringFromBanner(Banner banner, Environment environment, Class<?> mainApplicationClass)
			throws UnsupportedEncodingException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		banner.printBanner(environment, mainApplicationClass, new PrintStream(baos));
		String charset = environment.getProperty("spring.banner.charset", "UTF-8");
		return baos.toString(charset);
	}

	/**
	 * {@link Banner} comprised of other {@link Banner Banners}.
	 */
	private static class Banners implements Banner {

		private final List<Banner> banners = new ArrayList<>();

		void addIfNotNull(Banner banner) {
			if (banner != null) {
				this.banners.add(banner);
			}
		}

		boolean hasAtLeastOneBanner() {
			return !this.banners.isEmpty();
		}

		@Override
		public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
			for (Banner banner : this.banners) {
				banner.printBanner(environment, sourceClass, out);
			}
		}

	}

	/**
	 * Decorator that allows a {@link Banner} to be printed again without needing to
	 * specify the source class.
	 */
	private static class PrintedBanner implements Banner {

		private final Banner banner;

		private final Class<?> sourceClass;

		PrintedBanner(Banner banner, Class<?> sourceClass) {
			this.banner = banner;
			this.sourceClass = sourceClass;
		}

		@Override
		public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
			sourceClass = (sourceClass != null) ? sourceClass : this.sourceClass;
			this.banner.printBanner(environment, sourceClass, out);
		}

	}

}
