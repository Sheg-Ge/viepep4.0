package at.ac.tuwien.infosys.viepep.database.entities;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import javax.xml.bind.annotation.*;

import java.io.Serializable;
import java.util.Date;
import java.util.List;


/**
 * this class represents a complex element of the model complex elements are:
 * <ul>
 * <li>ANDConstruct
 * <li>Sequence
 * <li>XORConstruct
 * <li>Loop
 * </ul>
 */
@XmlSeeAlso({WorkflowElement.class, XORConstruct.class, ANDConstruct.class, LoopConstruct.class, ProcessStep.class,
        Sequence.class})
@Entity
@Table(name = "Element")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "TYPE")
@XmlAccessorType(XmlAccessType.FIELD)
@Getter
@Setter
public abstract class Element implements Serializable {

    /**
     * primary id in db
     */
    @Id
    @GeneratedValue(strategy = GenerationType.TABLE)
    protected long id;

    // name of the element
    @XmlElement(name = "name")
    protected String name;

    @XmlTransient
    @ManyToOne
    private Element parent;

    @XmlElementWrapper(name = "elementsList")
    @XmlElement(name = "elements")
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "parent", orphanRemoval = true)
    @OrderColumn
    protected List<Element> elements;


    // execution probability of this element
    // in a loop-pattern it's the number of executions      TODO is this true? If yes can it be an own property?
    protected double probability;

    @XmlElement(name = "deadline")
    protected long deadline;

    @XmlTransient
    @ManyToOne
    private Element nextXOR;

    protected Date finishedAt;

    @XmlElement(name = "lastElement")
    private boolean lastElement;

    /**
     * adds an element to the list of subelements
     *
     * @param elem Element
     */
    public void addElement(Element elem) {
        elements.add(elem);
        elem.setParent(this);
    }

    public abstract ProcessStep getLastExecutedElement();

    public abstract long calculateQoS();
    
    public abstract int getNumberOfExecutions();

    // TODO testing only
    public String getJavaObjectId() {
    	return super.toString().split("@")[1];
    }

    @Override
    public String toString() {
        return "Element{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", elements=" + elements +
                ", deadline=" + deadline +
                ", javaObjectId=" + getJavaObjectId() +
                '}';
    }

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (deadline ^ (deadline >>> 32));
		result = prime * result + (int) (id ^ (id >>> 32));
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		long temp;
		temp = Double.doubleToLongBits(probability);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Element other = (Element) obj;
		if (deadline != other.deadline)
			return false;
		if (id != other.id)
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (Double.doubleToLongBits(probability) != Double
				.doubleToLongBits(other.probability))
			return false;
		return true;
	}

}