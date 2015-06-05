package classLoaders;

import xxx.MyClass;

import java.io.File;
import java.net.URL;

/**
 * @author nik
 */
public class ContextClassLoaderTest {
  public static void main(String[] args) throws Exception {
    File utilJar = new File("lib/dep.jar").getAbsoluteFile();
    URL[] urls = {utilJar.toURI().toURL()};
    ClassLoader parent = null;
    Thread.currentThread().setContextClassLoader(new MyClassLoader(urls, parent, "context"));
    Class<?> aClass = Class.forName("xxx.MyClass", false, new MyClassLoader(urls, parent, "defining"));
    Object instance = aClass.newInstance();
    aClass.getMethod("method").invoke(instance);
  }
}
