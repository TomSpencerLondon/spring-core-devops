package guru.springframework.bootstrap.profilesysout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NonProfileBean {

    @Autowired
    public NonProfileBean(@Value("${guru.springframework.profile.message}")
                            String msg) {
        System.out.println("**************" + msg + "****************");
    }
}
