package guru.springframework.config.external;

import guru.springframework.config.external.props.ExternalPropsPropertySourceTestConfig;
import guru.springframework.jms.FakeJmsBroker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ExternalPropsPropertySourceTestConfig.class)
public class PropertySourceTest {

    @Autowired
    FakeJmsBroker fakeJmsBroker;
    @Test
    public void testPropsSet() {
        assertThat("10.10.10.123")
                .isEqualTo(fakeJmsBroker.getUrl());
        assertThat(3330)
                .isEqualTo(fakeJmsBroker.getPort().intValue());
        assertThat("Ron")
                .isEqualTo(fakeJmsBroker.getUser());
        assertThat("Burgundy")
                .isEqualTo(fakeJmsBroker.getPassword());
    }
}
