package smoketest.tomcat;

/**
 * <p>〈功能概述〉.
 *
 * @author haisen /20228/21
 */
public class Student {
    private Integer id;
    private String name;

    public Student() {
    }

    public Student(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void destroyMethod() {
        System.out.println("Student destroyMethod() ...");
    }

    public void initMethod() {
        System.out.println("Student initMethod() ...");
    }
}
