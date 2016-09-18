package at.ac.tuwien.infosys.viepep.database.services;

import at.ac.tuwien.infosys.viepep.database.entities.Element;
import at.ac.tuwien.infosys.viepep.database.entities.ProcessStep;
import at.ac.tuwien.infosys.viepep.database.entities.VirtualMachine;
import at.ac.tuwien.infosys.viepep.database.entities.WorkflowElement;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerContainer;
import at.ac.tuwien.infosys.viepep.database.entities.docker.DockerImage;
import at.ac.tuwien.infosys.viepep.database.repositories.WorkflowElementRepository;
import at.ac.tuwien.infosys.viepep.reasoning.optimisation.PlacementHelper;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by philippwaibel on 17/05/16. edited by Gerta Sheganaku
 */
@Component
@Slf4j
public class WorkflowDaoService {

	@Autowired
	private WorkflowElementRepository workflowElementRepository;
	@Autowired
	private PlacementHelper placementHelperImpl;
	@Autowired
	private VirtualMachineDaoService virtualMachineDaoService;
	@Autowired
	private DockerContainerDaoService dockerContainerDaoService;
	@Autowired
	private DockerImageDaoService dockerImageDaoService;

	@Value("${use.docker}")
	private boolean useDocker;

    @Transactional(propagation=Propagation.REQUIRES_NEW)
	public WorkflowElement finishWorkflow(WorkflowElement workflow) {
		log.info("-- Update workflowElement: " + workflow.toString());

		List<Element> flattedWorkflow = placementHelperImpl.getFlattenWorkflow(new ArrayList<>(), workflow);
		Date finishedDate = getFinishedDate(flattedWorkflow);

		workflow.setFinishedAt(finishedDate);
		for (Element element : flattedWorkflow) {
			if (element.getFinishedAt() == null) {
				element.setFinishedAt(workflow.getFinishedAt()); // TODO can be deleted?
			}
			if (element instanceof ProcessStep) {
				VirtualMachine vm = ((ProcessStep) element).getScheduledAtVM();
				if (vm != null) { // if the process step is after an XOR the process steps on one side of the XOR are not executed
					if (vm.getId() != null) {
						vm = virtualMachineDaoService.getVm(vm);
						((ProcessStep) element).setScheduledAtVM(vm);
						virtualMachineDaoService.update(vm);
					} else {
						vm = virtualMachineDaoService.update(vm);
						((ProcessStep) element).setScheduledAtVM(vm);
					}
				}
				
				if (useDocker) {
					DockerContainer dockerContainer = ((ProcessStep) element).getScheduledAtContainer();
					if (dockerContainer != null) { // if the process step is after an XOR the process steps on one side of the XOR are not executed
						// make sure we save the DockerImage first, to avoid org.hibernate.TransientPropertyValueException:
						DockerImage img = dockerContainer.getDockerImage();
						if(img != null) {
							DockerImage dockerImgInDB = dockerImageDaoService.getDockerImage(img);
							if(dockerImgInDB == null) {
								img = dockerImageDaoService.save(img);
								dockerContainer.setDockerImage(img);
							} else {
								dockerContainer.setDockerImage(dockerImgInDB);
							}
						}
						if (dockerContainer.getId() != null) {
							dockerContainer = dockerContainerDaoService.getDockerContainer(dockerContainer);
							((ProcessStep) element).setScheduledAtContainer(dockerContainer);
							dockerContainerDaoService.update(dockerContainer);
						} else {
							dockerContainer = dockerContainerDaoService.update(dockerContainer);
							((ProcessStep) element).setScheduledAtContainer(dockerContainer);
						}
					}
				}
			}
		}
		return workflowElementRepository.save(workflow);
	}

	private Date getFinishedDate(List<Element> flattedWorkflow) {
		Date finishedDate = null;
		for (Element element : flattedWorkflow) {
			if (element instanceof ProcessStep && element.isLastElement()) {
				if (element.getFinishedAt() != null) {
					if (finishedDate == null) {
						finishedDate = element.getFinishedAt();
					} else if (element.getFinishedAt().after(finishedDate)) {
						finishedDate = element.getFinishedAt();
					}
				}
			}
		}
		return finishedDate;
	}

}
