package at.ac.tuwien.infosys.viepep.database.entities;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@XmlRootElement(name = "WorkflowElement")
@Entity(name = "WorkflowElement")
@PrimaryKeyJoinColumn(name="id")
@Table(name="WorkflowElement")
@DiscriminatorValue(value = "workflow")
@Getter
@Setter
public class WorkflowElement extends Element {
	
    private Date arrivedAt;

    public WorkflowElement(String name, long date) {
        this.name = name;
        this.elements = new ArrayList<>();
        this.deadline = date;
    }

    public WorkflowElement() {
        elements = new ArrayList<>();
    }

//    public long calculateQoS() {
//        return elements.get(0).calculateQoS();
//    }
    
    public long calculateQoS() {
        long executionTime = 0;
        for (Element element : elements) {
            executionTime += element.calculateQoS();
        }
        return executionTime;
    }

    @Override
    public ProcessStep getLastExecutedElement() {
        List<Element> allChildren = new ArrayList<>();
        for (Element element : elements) {
            allChildren.add(element.getLastExecutedElement());
        }
        ProcessStep lastExecutedMaxElement = null;
        for (Element allChild : allChildren) {
            ProcessStep current = (ProcessStep) allChild;
            if (lastExecutedMaxElement == null && current != null) {
                if (current.hasBeenExecuted()) {
                    lastExecutedMaxElement = current;
                }
            } else if (current != null) {
                if (current.getFinishedAt().after(lastExecutedMaxElement.getFinishedAt())) {
                    lastExecutedMaxElement = current;
                }
            }
        }
        return lastExecutedMaxElement;
    }

    @Override
    public String toString() {
        return "Workflow{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", arrivedAt='" + arrivedAt + '\'' +
                ", elements=" + elements +
                ", deadline=" + deadline +
                '}';
    }

}
