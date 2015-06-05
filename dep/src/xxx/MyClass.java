package xxx;

import xxx.Dep;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author nik
 */
public class MyClass {
  public void method() throws ClassNotFoundException {
    System.out.println("Context classloader: " + Thread.currentThread().getContextClassLoader());
    System.out.println("ClassLoader of Dep.class: " + Dep.class.getClassLoader());
    System.out.println("ClassLoader of Dep via forName: " + Class.forName("xxx.Dep").getClassLoader());
    new Dep().showClassLoader();
  }
}
