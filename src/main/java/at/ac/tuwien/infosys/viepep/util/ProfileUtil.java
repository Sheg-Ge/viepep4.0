package at.ac.tuwien.infosys.viepep.util;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProfileUtil {

    @Value("${spring.profiles.active}")
    private String profile;

    public boolean isProfile(String profileName) {
    	return Arrays.asList(profile.split("\\s*,\\s*")).contains(profileName);
    }

}
