package guru.springframework.bootstrap.profilesysout;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProdProfileSysOut {
    public ProdProfileSysOut() {
        System.out.println("####################################");
        System.out.println("####################################");
        System.out.println("##              PROD              ##");
        System.out.println("####################################");
        System.out.println("####################################");
    }
}
