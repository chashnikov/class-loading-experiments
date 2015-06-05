package classLoaders;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author nik
 */
public class MyClassLoader extends URLClassLoader {
  private final String name;

  public MyClassLoader(URL[] urls, ClassLoader parent, String name) {
    super(urls, parent);
    this.name = name;
  }

  @Override
  public String toString() {
    return "MyClassLoader:" + name;
  }
}
