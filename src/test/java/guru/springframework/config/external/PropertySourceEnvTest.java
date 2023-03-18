package guru.springframework.config.external;


import guru.springframework.config.external.props.ExternalPropsPropertySourceTestConfig;
import guru.springframework.jms.FakeJmsBroker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ExternalPropsPropertySourceTestConfig.class)
public class PropertySourceEnvTest {

    @Autowired
    FakeJmsBroker fakeJmsBroker;

    @Test
    void testPropsSet() {
        assertThat(fakeJmsBroker.getUrl())
                .isEqualTo("10.10.10.123");
        assertThat(fakeJmsBroker.getPort().intValue())
                .isEqualTo(3330);
        assertThat(fakeJmsBroker.getUser()).isEqualTo("Ron");
        assertThat(fakeJmsBroker.getPassword()).isEqualTo("Burgundy");

    }
}
