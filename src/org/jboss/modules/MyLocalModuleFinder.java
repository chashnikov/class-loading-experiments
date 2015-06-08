package org.jboss.modules;

import org.jboss.modules.*;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import static java.security.AccessController.doPrivileged;
import static java.security.AccessController.getContext;

/**
 * @author nik
 */
public final class MyLocalModuleFinder implements ModuleFinder {

  private static final File[] NO_FILES = new File[0];

  private final File[] repoRoots;
  private final AccessControlContext accessControlContext;

  public MyLocalModuleFinder(final File[] repoRoots) {
    this.repoRoots = repoRoots;
    accessControlContext = getContext();
  }

  private static File[] getFiles(final String modulePath, final int stringIdx, final int arrayIdx) {
    if (modulePath == null) return NO_FILES;
    final int i = modulePath.indexOf(File.pathSeparatorChar, stringIdx);
    final File[] files;
    if (i == -1) {
      files = new File[arrayIdx + 1];
      files[arrayIdx] = new File(modulePath.substring(stringIdx)).getAbsoluteFile();
    }
    else {
      files = getFiles(modulePath, i + 1, arrayIdx + 1);
      files[arrayIdx] = new File(modulePath.substring(stringIdx, i)).getAbsoluteFile();
    }
    return files;
  }

  private static String toPathString(ModuleIdentifier moduleIdentifier) {
    return moduleIdentifier.getName().replace('.', File.separatorChar) + File.separatorChar;
  }

  public ModuleSpec findModule(final ModuleIdentifier identifier, final ModuleLoader delegateLoader) throws ModuleLoadException {
    final String child = toPathString(identifier);
    try {
      return doPrivileged(new PrivilegedExceptionAction<ModuleSpec>() {
        public ModuleSpec run() throws Exception {
          for (File root : repoRoots) {
            final File file = new File(root, child);
            final File moduleXml = new File(file, "module.xml");
            if (moduleXml.exists()) {
              final ModuleSpec spec = ModuleXmlParser.parseModuleXml(delegateLoader, identifier, file, moduleXml, accessControlContext);
              if (spec == null) break;
              return spec;
            }
          }
          return null;
        }
      }, accessControlContext);
    } catch (PrivilegedActionException e) {
      try {
        throw e.getException();
      } catch (RuntimeException e1) {
        throw e1;
      } catch (ModuleLoadException e1) {
        throw e1;
      } catch (Error e1) {
        throw e1;
      } catch (Exception e1) {
        throw new UndeclaredThrowableException(e1);
      }
    }
  }

  public String toString() {
    final StringBuilder b = new StringBuilder();
    b.append("my local module finder @").append(Integer.toHexString(hashCode())).append(" (roots: ");
    final int repoRootsLength = repoRoots.length;
    for (int i = 0; i < repoRootsLength; i++) {
      final File root = repoRoots[i];
      b.append(root);
      if (i != repoRootsLength - 1) {
        b.append(',');
      }
    }
    b.append(')');
    return b.toString();
  }
}
