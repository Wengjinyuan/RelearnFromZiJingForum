package org.example.rebuiltzijingforum;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:docker_profile_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.sql.init.schema-locations=classpath:schema.sql"
})
@ActiveProfiles({"mysql", "docker"})
class DockerProfileConfigurationTest {
    @Autowired Environment environment;

    @Test
    void dockerDefaultsUseContainerHostsAndKeepOptionalFeaturesOff() {
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8080);
        assertThat(environment.getProperty("forum.storage.type")).isEqualTo("local");
        assertThat(environment.getProperty("forum.storage.local-root")).isEqualTo("/app/uploads");
        assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("redis");
        assertThat(environment.getProperty("spring.rabbitmq.host")).isEqualTo("rabbitmq");
        assertThat(environment.getProperty("forum.search.elasticsearch.url")).isEqualTo("http://elasticsearch:9200");
        assertThat(environment.getProperty("forum.redis.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("forum.rabbit.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("forum.search.elasticsearch.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("forum.search.ai.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("forum.learning-ai.enabled", Boolean.class)).isFalse();
    }
}
