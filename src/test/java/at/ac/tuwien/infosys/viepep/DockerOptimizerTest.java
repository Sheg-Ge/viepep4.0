package at.ac.tuwien.infosys.viepep;

import lombok.extern.slf4j.Slf4j;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.inmemory.services.CacheVirtualMachineService;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ViePepApplication.class)
@WebAppConfiguration
@IntegrationTest("server.port:0")
@TestPropertySource(properties = {"simulate = true", "autostart = false"})
@Slf4j
@ActiveProfiles({"test", "docker"})
public class DockerOptimizerTest {
	
	@Autowired
	CacheVirtualMachineService vmService;

	@Ignore
	@Test
	public void testOptimization() {
		/* define VM types */
		vmService.initializeVMs();
		
		/* define container types */
		

		/* define process type */

		/* request enactment */

		/* ... */

		/* start optimization */

		/* ... (wait) */

		/* assertions, metrics.. */
	}

}
