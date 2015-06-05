package xxx;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author nik
 */
public class Dep {
  public void showClassLoader() {
    System.out.println("ClassLoader inside Dep: " + getClass().getClassLoader());
    Object o = new CopyOnWriteArraySet<String>();
    System.out.println("Class from java.util loaded by: " + o.getClass().getClassLoader());
  }
}
