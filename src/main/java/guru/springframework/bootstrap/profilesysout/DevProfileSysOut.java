package guru.springframework.bootstrap.profilesysout;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevProfileSysOut {
    public DevProfileSysOut() {
        System.out.println("####################################");
        System.out.println("####################################");
        System.out.println("##              DEV               ##");
        System.out.println("####################################");
        System.out.println("####################################");
    }
}
