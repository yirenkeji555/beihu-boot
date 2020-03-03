package ltd.beihu.core.boot.autoconfigure.mutiles;

import com.google.gson.Gson;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.elasticsearch.jest.HttpClientConfigBuilderCustomizer;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * @Author: zjz
 * @Desc:
 * @Date: 2019/11/14
 * @Version: V1.0.0
 */
@Configuration
@ConditionalOnClass(JestClient.class)
@EnableConfigurationProperties({BeihuMutilElasticsearchJestProperties.class})
@ConditionalOnProperty(value = "beihu.data.elasticsearch.mutil.jest.enable", havingValue = "true", matchIfMissing = false)
@AutoConfigureAfter(GsonAutoConfiguration.class)
@Slf4j
public class BeihuMutilElasticsearchJestAutoConfiguration {

    private final BeihuMutilElasticsearchJestProperties properties;
    private final ObjectProvider<Gson> gsonProvider;
    private final ObjectProvider<HttpClientConfigBuilderCustomizer> builderCustomizers;

    private ApplicationContext applicationContext;

    public BeihuMutilElasticsearchJestAutoConfiguration(BeihuMutilElasticsearchJestProperties properties, ObjectProvider<Gson> gson,
                                                        ObjectProvider<HttpClientConfigBuilderCustomizer> builderCustomizers,
                                                        ApplicationContext applicationContext) throws Exception {
        this.properties = properties;
        this.gsonProvider = gson;
        this.builderCustomizers = builderCustomizers;

        this.applicationContext = applicationContext;

        init();
    }

    private void init() throws Exception{
        Map<String, BeihuMutilElasticsearchJestProperties.Instances> instances = this.properties.instances;
        for(Map.Entry<String, BeihuMutilElasticsearchJestProperties.Instances> entry : instances.entrySet()) {
            String connectionName = entry.getKey();
            log.info("【connectionName】： [{}]", connectionName);
            BeihuMutilElasticsearchJestProperties.Instances instance = entry.getValue();
            // connectionName -> JestClient
            registerBean(connectionName, buildJestClient(instance));
        }
    }

    private void registerBean(String name, Object obj) {
        // 获取BeanFactory
        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) applicationContext
                .getAutowireCapableBeanFactory();
        // 动态注册bean.
        defaultListableBeanFactory.registerSingleton(name, obj);
    }

    public JestClient buildJestClient(BeihuMutilElasticsearchJestProperties.Instances instance) {
        JestClientFactory factory = new JestClientFactory();
        factory.setHttpClientConfig(createHttpClientConfig(instance));
        return factory.getObject();
    }

    protected HttpClientConfig createHttpClientConfig(BeihuMutilElasticsearchJestProperties.Instances instance) {
        HttpClientConfig.Builder builder = new HttpClientConfig.Builder(
                instance.getUris());
        PropertyMapper map = PropertyMapper.get();
        map.from(instance::getUsername).whenHasText().to((username) -> builder
                .defaultCredentials(username, instance.getPassword()));

        map.from(this.gsonProvider::getIfUnique).whenNonNull().to(builder::gson);
        map.from(instance::isMultiThreaded).to(builder::multiThreaded);
        map.from(instance::getConnectionTimeout).whenNonNull()
                .asInt(Duration::toMillis).to(builder::connTimeout);
        map.from(instance::getReadTimeout).whenNonNull().asInt(Duration::toMillis)
                .to(builder::readTimeout);
        customize(builder);
        return builder.build();
    }

    private void customize(HttpClientConfig.Builder builder) {
        this.builderCustomizers.orderedStream()
                .forEach((customizer) -> customizer.customize(builder));
    }
}
